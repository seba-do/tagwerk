package me.ttdrp.tagwerk.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Priority
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TodoList
import me.ttdrp.tagwerk.model.str
import me.ttdrp.tagwerk.widget.TodayWidgetRenderer
import java.io.File
import java.time.LocalDate

/** Prototyp-Speicher: der ganze Zustand als JSON-Datei, atomar ersetzt. */
class Store(private val file: File, private val onChange: (AppState) -> Unit = {}) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(load())
    val state: StateFlow<AppState> = mutable

    private fun load(): AppState {
        val loaded = runCatching { json.decodeFromString<AppState>(file.readText()) }.getOrNull()
        return if (loaded == null || !loaded.seeded) seeded() else loaded
    }

    @Synchronized
    fun update(change: (AppState) -> AppState) {
        val next = change(mutable.value)
        mutable.value = next
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), next))
        tmp.renameTo(file)
        onChange(next)
    }

    companion object {
        const val FILE_NAME = "tagwerk.json"

        @Volatile
        private var shared: Store? = null

        /**
         * Eine Instanz je Prozess: App und Receiver schreiben beide. Mit getrennten Instanzen sähe die offene App
         * ein "Erledigt" aus der Meldung nicht und überschriebe es beim nächsten Speichern.
         */
        fun shared(context: Context): Store = shared ?: synchronized(this) {
            val app = context.applicationContext
            shared ?: Store(File(app.filesDir, FILE_NAME)) { TodayWidgetRenderer.updateAll(app, it) }.also { shared = it }
        }
    }

    private fun seeded(): AppState {
        val inbox = TodoList(name = str(R.string.seed_inbox))
        val home = TodoList(name = str(R.string.seed_home))
        val today = LocalDate.now().toEpochDay()
        fun plan(title: String, part: Daypart, minutes: Int, subs: List<Subtask> = emptyList()) = Task(
            title = title,
            emoji = EmojiGuess.forTitle(title),
            tint = EmojiGuess.tintFor(title),
            kind = Kind.PLAN,
            epochDay = today,
            daypart = part,
            minutes = minutes,
            subtasks = subs,
        )
        fun todo(title: String, list: TodoList, minutes: Int, priority: Priority) = Task(
            title = title,
            emoji = EmojiGuess.forTitle(title),
            tint = EmojiGuess.tintFor(title),
            kind = Kind.TODO,
            listId = list.id,
            minutes = minutes,
            priority = priority,
        )
        return AppState(
            seeded = true,
            lists = listOf(inbox, home),
            tasks = listOf(
                plan(
                    str(R.string.seed_morning), Daypart.MORNING, 20,
                    listOf(
                        Subtask(title = str(R.string.seed_brush), minutes = 5),
                        Subtask(title = str(R.string.seed_coffee), minutes = 5),
                        Subtask(title = str(R.string.seed_review), minutes = 10),
                    ),
                ),
                plan(str(R.string.seed_deep_work), Daypart.AFTERNOON, 45),
                plan(str(R.string.seed_run), Daypart.EVENING, 30),
                todo(str(R.string.seed_bill), inbox, 10, Priority.HIGH),
                todo(str(R.string.seed_laundry), home, 15, Priority.MEDIUM),
                todo(str(R.string.seed_basement), home, 90, Priority.LOW),
            ),
        )
    }
}
