package me.ttdrp.tagwerk.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ttdrp.tagwerk.BuildConfig
import me.ttdrp.tagwerk.alarm.FocusAlarmScheduler
import me.ttdrp.tagwerk.alarm.TaskReminderScheduler
import me.ttdrp.tagwerk.ai.Assistant
import me.ttdrp.tagwerk.ai.AssistantException
import me.ttdrp.tagwerk.ai.Conversation
import me.ttdrp.tagwerk.ai.DEFAULT_MODEL
import me.ttdrp.tagwerk.ai.PromptText
import me.ttdrp.tagwerk.ai.ProposedTask
import me.ttdrp.tagwerk.ai.Proposals
import me.ttdrp.tagwerk.ai.SecretStore
import me.ttdrp.tagwerk.ai.SuggestedItem
import me.ttdrp.tagwerk.calendar.DeviceCalendar
import me.ttdrp.tagwerk.data.Store
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.CalendarEvent
import me.ttdrp.tagwerk.model.DeviceCalendarInfo
import me.ttdrp.tagwerk.model.Grouping
import me.ttdrp.tagwerk.model.applyGroup
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.ReminderSettings
import me.ttdrp.tagwerk.model.Repeat
import me.ttdrp.tagwerk.model.Reorder
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TodoList
import me.ttdrp.tagwerk.model.newId
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalendarUi(
    val connected: Boolean = false,
    val permitted: Boolean = false,
    val calendars: List<DeviceCalendarInfo> = emptyList(),
    val hidden: Set<Long> = emptySet(),
)

data class ChatEntry(
    val id: String = newId(),
    val fromUser: Boolean,
    val text: String,
    val proposals: List<ProposedTask> = emptyList(),
    val toolUseIds: List<String> = emptyList(),
    val decision: Boolean? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store.shared(app)
    private val focusAlarms = FocusAlarmScheduler(app)
    private val taskReminders = TaskReminderScheduler(app)
    private val secrets = SecretStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val state: StateFlow<AppState> = store.state

    private val _hasKey = MutableStateFlow(secrets.apiKey() != null)
    val hasKey: StateFlow<Boolean> = _hasKey
    private val _model = MutableStateFlow(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val model: StateFlow<String> = _model
    private val _baseUrl = MutableStateFlow(prefs.getString("base_url", "").orEmpty())
    val baseUrl: StateFlow<String> = _baseUrl

    private val _chat = MutableStateFlow<List<ChatEntry>>(emptyList())
    val chat: StateFlow<List<ChatEntry>> = _chat
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    private var assistant: Assistant? = null
    private val conversation = Conversation()

    private val _feedback = MutableStateFlow(
        FeedbackSettings(
            sound = prefs.getBoolean("feedback_sound", true),
            motion = prefs.getBoolean("feedback_motion", true),
            haptics = prefs.getBoolean("feedback_haptics", true),
        ),
    )
    val feedback: StateFlow<FeedbackSettings> = _feedback

    /** Gelöschte Aufgabe samt Platz, damit "Rückgängig" sie genau dorthin zurücklegt. */
    data class Undo(val task: Task, val index: Int)
    private val _undo = MutableStateFlow<Undo?>(null)
    val undo: StateFlow<Undo?> = _undo

    private val _openFocus = MutableStateFlow(false)
    val openFocus: StateFlow<Boolean> = _openFocus

    private val _openTask = MutableStateFlow<String?>(null)
    val openTask: StateFlow<String?> = _openTask

    private val deviceCalendar = DeviceCalendar(app)
    private val _calendarUi = MutableStateFlow(CalendarUi())
    val calendarUi: StateFlow<CalendarUi> = _calendarUi
    private val _calendarVersion = MutableStateFlow(0)
    val calendarVersion: StateFlow<Int> = _calendarVersion
    private var calendarChanges: Job? = null

    private val _reminderSettings = MutableStateFlow(taskReminders.settings())
    val reminderSettings: StateFlow<ReminderSettings> = _reminderSettings

    init {
        refreshCalendars()
        viewModelScope.launch {
            state.collect { taskReminders.sync(it, System.currentTimeMillis()) }
        }
        // Alle Wege zum Timer laufen über den Speicher, also hängt auch der Alarm nur an ihm.
        viewModelScope.launch {
            state.map { s -> s.focus to s.tasks.firstOrNull { it.id == s.focus?.taskId }?.subtasks }
                .distinctUntilChanged()
                .collect { focusAlarms.sync(state.value, System.currentTimeMillis()) }
        }
    }

    fun requestOpenFocus() { _openFocus.value = true }
    fun focusOpened() { _openFocus.value = false }

    fun requestOpenTask(id: String) { _openTask.value = id }
    fun taskOpened() { _openTask.value = null }

    /** "Fokus starten" aus einer Erinnerung. Die Aufgabe kann inzwischen erledigt oder gelöscht sein. */
    fun startFocusFromReminder(id: String) {
        val task = Recurrence.find(state.value.tasks, id)?.takeUnless { it.isDone } ?: return
        startFocus(task)
        requestOpenFocus()
    }

    fun setReminderSettings(value: ReminderSettings) {
        taskReminders.saveSettings(value)
        _reminderSettings.value = value
        taskReminders.sync(state.value, System.currentTimeMillis())
    }

    fun clearNotice() { _notice.value = null }
    fun notify(text: String) { _notice.value = text }

    // ---- Aufgaben ----

    fun saveTask(task: Task) = store.update { s -> s.copy(tasks = Recurrence.upsert(s.tasks, task)) }

    /** Änderung an einem Termin einer Serie, für [scope] Termine. */
    fun editOccurrence(edited: Task, repeat: Repeat?, scope: Recurrence.EditScope) = store.update { s ->
        s.copy(tasks = Recurrence.applyEdit(s.tasks, edited, repeat, scope))
    }

    fun deleteOccurrence(id: String, scope: Recurrence.EditScope) = store.update { s ->
        val tasks = Recurrence.delete(s.tasks, id, scope)
        s.copy(tasks = tasks, focus = s.focus?.takeIf { f -> tasks.any { it.id == f.taskId } })
    }

    fun deleteTask(id: String) = store.update { s ->
        s.copy(tasks = s.tasks.filterNot { it.id == id }, focus = s.focus?.takeUnless { it.taskId == id })
    }

    fun deleteWithUndo(task: Task) {
        val index = state.value.tasks.indexOfFirst { it.id == task.id }
        if (index < 0) return
        deleteTask(task.id)
        _undo.value = Undo(task, index)
    }

    fun undoDelete(undo: Undo) {
        store.update { s ->
            val tasks = s.tasks.toMutableList()
            tasks.add(undo.index.coerceAtMost(tasks.size), undo.task)
            s.copy(tasks = tasks)
        }
        _undo.value = null
    }

    fun clearUndo() { _undo.value = null }

    fun moveTask(id: String, beforeId: String?, daypart: Daypart?) = store.update { s ->
        val ready = Recurrence.materialize(Recurrence.materialize(s.tasks, id), beforeId)
        s.copy(tasks = Reorder.move(ready, id, beforeId, daypart))
    }

    /** Mit Gruppen übernimmt die Aufgabe den Wert der Gruppe, in die sie gezogen wurde. */
    fun moveTaskToGroup(id: String, beforeId: String?, grouping: Grouping, group: String?) = store.update { s ->
        val moved = Reorder.move(s.tasks, id, beforeId, null)
        s.copy(tasks = if (group == null) moved else moved.map { if (it.id == id) applyGroup(it, grouping, group) else it })
    }

    fun setFeedback(value: FeedbackSettings) {
        prefs.edit()
            .putBoolean("feedback_sound", value.sound)
            .putBoolean("feedback_motion", value.motion)
            .putBoolean("feedback_haptics", value.haptics)
            .apply()
        _feedback.value = value
    }

    fun toggleDone(task: Task) = saveTask(
        task.copy(doneAtMs = if (task.isDone) null else System.currentTimeMillis()),
    )

    fun toggleSubtask(task: Task, sub: Subtask) = saveTask(
        task.copy(subtasks = task.subtasks.map { if (it.id == sub.id) it.copy(done = !it.done) else it }),
    )

    fun duplicateTask(task: Task) {
        store.update { s -> s.copy(tasks = Recurrence.duplicate(s.tasks, task)) }
        notify("„${task.title}“ kopiert")
    }

    /** Bei einem Termin einer Serie wandert nur dieser eine Tag, ein To-do landet damit im Plan. */
    fun rescheduleTask(task: Task, day: LocalDate) {
        if (task.kind == Kind.PLAN && task.epochDay == day.toEpochDay()) return
        saveTask(task.copy(kind = Kind.PLAN, epochDay = day.toEpochDay()))
        notify("„${task.title}“ geplant für ${day.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))}")
    }

    fun scheduleToday(task: Task) {
        saveTask(task.copy(kind = Kind.PLAN, epochDay = LocalDate.now().toEpochDay()))
        notify("„${task.title}“ steht jetzt im heutigen Plan")
    }

    fun moveToList(task: Task, list: TodoList) {
        saveTask(task.copy(kind = Kind.TODO, epochDay = null, startMinute = null, listId = list.id))
        notify("„${task.title}“ liegt jetzt auf „${list.name}“")
    }

    fun addList(name: String) = store.update { s ->
        s.copy(lists = s.lists + TodoList(name = name.trim()))
    }

    fun renameList(id: String, name: String) = store.update { s ->
        s.copy(lists = s.lists.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteList(id: String) = store.update { s ->
        s.copy(lists = s.lists.filterNot { it.id == id }, tasks = s.tasks.filterNot { it.listId == id && it.kind == Kind.TODO })
    }

    // ---- Fokus ----

    fun startFocus(task: Task) = store.update { s ->
        // Eine Instanz muss gespeichert sein, damit der Timer sie auch nach einem Neustart wiederfindet.
        s.copy(
            tasks = Recurrence.upsert(s.tasks, task),
            focus = FocusClock.start(task.id, task.effectiveMinutes, System.currentTimeMillis()),
        )
    }

    fun startQuickFocus(minutes: Int) {
        val task = Task(
            title = "Fokuszeit",
            emoji = "🎯",
            tint = 2,
            epochDay = LocalDate.now().toEpochDay(),
            minutes = minutes,
        )
        store.update { s ->
            s.copy(tasks = s.tasks + task, focus = FocusClock.start(task.id, minutes, System.currentTimeMillis()))
        }
    }

    fun pauseFocus() = store.update { s ->
        s.copy(focus = s.focus?.let { FocusClock.pause(it, System.currentTimeMillis()) })
    }

    fun resumeFocus() = store.update { s ->
        s.copy(focus = s.focus?.let { FocusClock.resume(it, System.currentTimeMillis()) })
    }

    fun extendFocus(minutes: Int = 5) = store.update { s ->
        s.copy(focus = s.focus?.let { FocusClock.extend(it, minutes) })
    }

    fun stopFocus() = store.update { it.copy(focus = null) }

    fun finishFocus() = store.update { FocusClock.finish(it, System.currentTimeMillis()) }

    // ---- Kalender ----

    /** Liest Kalender neu ein, auch nach der Rückkehr aus den Android-Einstellungen. */
    fun refreshCalendars(): Job = viewModelScope.launch {
        val permitted = deviceCalendar.hasPermission()
        val connected = prefs.getBoolean("calendar_connected", false)
        val calendars = if (permitted && connected) withContext(Dispatchers.IO) { deviceCalendar.calendars() } else emptyList()
        _calendarUi.value = CalendarUi(connected, permitted, calendars, hiddenCalendars())
        _calendarVersion.value += 1
        if (permitted && calendarChanges == null) {
            calendarChanges = viewModelScope.launch { deviceCalendar.changes().conflate().collect { refreshCalendars() } }
        }
    }

    fun connectCalendar() {
        prefs.edit().putBoolean("calendar_connected", true).apply()
        refreshCalendars()
    }

    fun disconnectCalendar() {
        prefs.edit().putBoolean("calendar_connected", false).apply()
        refreshCalendars()
    }

    fun setCalendarVisible(id: Long, visible: Boolean) {
        val hidden = hiddenCalendars().let { if (visible) it - id else it + id }
        prefs.edit().putStringSet("calendar_hidden", hidden.map(Long::toString).toSet()).apply()
        refreshCalendars()
    }

    /** Termine bleiben auf dem Gerät: Sie werden hier nur gelesen und nicht an die KI weitergegeben. */
    suspend fun eventsFor(day: LocalDate): List<CalendarEvent> {
        val ui = _calendarUi.value
        if (!ui.connected || !ui.permitted) return emptyList()
        return withContext(Dispatchers.IO) { deviceCalendar.events(day, ZoneId.systemDefault(), ui.hidden) }
    }

    private fun hiddenCalendars(): Set<Long> =
        prefs.getStringSet("calendar_hidden", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    // ---- Einstellungen ----

    fun saveApiKey(value: String) {
        secrets.saveApiKey(value.trim())
        _hasKey.value = true
        assistant = null
    }

    fun deleteApiKey() {
        secrets.clearApiKey()
        _hasKey.value = false
        assistant = null
        clearChat()
    }

    fun setModel(id: String) {
        prefs.edit().putString("model", id).apply()
        _model.value = id
        assistant = null
    }

    fun setBaseUrl(url: String) {
        prefs.edit().putString("base_url", url.trim()).apply()
        _baseUrl.value = url.trim()
        assistant = null
    }

    private fun assistantOrNull(): Assistant? {
        assistant?.let { return it }
        val key = secrets.apiKey() ?: return null
        val url = _baseUrl.value.takeIf { BuildConfig.DEBUG }
        return Assistant(key, _model.value, url, conversation).also { assistant = it }
    }

    fun testKey() = aiCall("Der Schlüssel funktioniert.") { it.testConnection() }

    // ---- KI ----

    private fun aiCall(successNotice: String? = null, block: suspend (Assistant) -> Unit) {
        val ai = assistantOrNull()
        if (ai == null) {
            notify("Hinterlege zuerst deinen API-Key in den Einstellungen.")
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                block(ai)
                successNotice?.let(::notify)
            } catch (e: AssistantException) {
                notify(e.message ?: "Die Anfrage ist fehlgeschlagen.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun sendChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return
        _chat.value += ChatEntry(fromUser = true, text = trimmed)
        aiCall { ai ->
            val reply = ai.chat(trimmed, PromptText.chatContext(state.value.tasks, LocalDate.now()))
            _chat.value += ChatEntry(
                fromUser = false,
                text = reply.text,
                proposals = reply.proposals,
                toolUseIds = reply.toolUseIds,
            )
        }
    }

    fun decide(entry: ChatEntry, accept: Boolean) {
        if (accept) {
            val today = LocalDate.now()
            val firstList = state.value.lists.firstOrNull()?.id
            entry.proposals.forEach { p ->
                saveTask(
                    Task(
                        title = p.title,
                        emoji = EmojiGuess.forTitle(p.title),
                        tint = EmojiGuess.tintFor(p.title),
                        kind = p.kind,
                        listId = if (p.kind == Kind.TODO) firstList else null,
                        epochDay = Proposals.planDay(p, today),
                        daypart = p.daypart,
                        minutes = p.minutes,
                        subtasks = p.subtasks.map { Subtask(title = it) },
                    ),
                )
            }
            notify("${entry.proposals.size} Aufgabe(n) übernommen")
        }
        val titles = entry.proposals.joinToString(", ") { it.title }
        val answer = "Der Nutzer hat diese Vorschläge ${if (accept) "übernommen" else "verworfen"}: $titles"
        entry.toolUseIds.forEach { conversation.recordDecision(it, answer) }
        _chat.value = _chat.value.map { if (it.id == entry.id) it.copy(decision = accept) else it }
    }

    fun clearChat() {
        conversation.reset()
        _chat.value = emptyList()
    }

    /** Zerlegt eine Aufgabe in Schritte. Das Ergebnis übernimmt erst der Nutzer im Formular. */
    fun breakDown(title: String, targetMinutes: Int?, onResult: (List<SuggestedItem>) -> Unit) = aiCall { ai ->
        val items = ai.suggestItems(PromptText.breakDownInstruction(title, targetMinutes))
        onResult(if (targetMinutes == null) items else Proposals.fitToDuration(items, targetMinutes))
    }

    /** Schlägt passende Einträge für eine Liste vor. */
    fun suggestForList(listName: String, existing: List<String>, onResult: (List<SuggestedItem>) -> Unit) = aiCall { ai ->
        val known = existing.joinToString(", ").ifEmpty { "noch keine" }
        onResult(ai.suggestItems("Schlage weitere Aufgaben für die Liste „$listName“ vor. Vorhanden sind: $known."))
    }
}
