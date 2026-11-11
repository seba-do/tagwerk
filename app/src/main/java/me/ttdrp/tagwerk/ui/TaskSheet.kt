package me.ttdrp.tagwerk.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import java.time.ZoneId
import me.ttdrp.tagwerk.model.label
import me.ttdrp.tagwerk.model.RepeatRule
import me.ttdrp.tagwerk.model.Repeat
import me.ttdrp.tagwerk.model.Recurrence
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Repeat
import java.util.Locale
import java.time.format.DateTimeFormatter
import me.ttdrp.tagwerk.model.shownEmoji
import me.ttdrp.tagwerk.model.StepEdits
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Priority
import me.ttdrp.tagwerk.model.Quadrant
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import java.time.LocalDate
import java.time.LocalTime

private const val TASK_VISUALS = "task"

/**
 * Neu anlegen beginnt schnell: Es braucht nur einen Titel, der Rest liegt in Chips.
 * Über die drei Punkte, und beim Bearbeiten immer, öffnet sich das ganze Formular.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    request: SheetRequest,
    state: AppState,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onStart: (Task) -> Unit,
) {
    val original = when (request) {
        is SheetRequest.Edit -> request.task
        is SheetRequest.New -> request.template
    }
    val isNew = request is SheetRequest.New
    val busy by vm.busy.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf(original.title) }
    var emoji by remember { mutableStateOf(original.emoji) }
    var emojiTouched by remember { mutableStateOf(!isNew) }
    var color by remember { mutableStateOf(original.color) }
    var kind by remember { mutableStateOf(original.kind) }
    var listId by remember { mutableStateOf(original.listId ?: state.lists.firstOrNull()?.id) }
    var daypart by remember { mutableStateOf(original.daypart) }
    var startMinute by remember { mutableStateOf(original.startMinute) }
    var epochDay by remember { mutableStateOf(original.epochDay ?: LocalDate.now().toEpochDay()) }
    var minutes by remember { mutableStateOf(original.minutes) }
    // Bei einer neuen Aufgabe ist die Vorauswahl keine Planung, also auch keine Vorgabe fürs Zerlegen.
    var durationChosen by remember { mutableStateOf(!isNew) }
    var notes by remember { mutableStateOf(original.notes) }
    var full by remember { mutableStateOf(!isNew) }
    var titleMissing by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var visualsFor by remember { mutableStateOf<String?>(null) }
    val steps = remember { mutableStateListOf(*original.subtasks.toTypedArray()) }
    val master = remember { Recurrence.masterOf(state.tasks, original) }
    val originalRepeat = master?.repeat ?: original.repeat
    var repeat by remember { mutableStateOf(originalRepeat) }
    var askCustomRepeat by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf(false) }
    var askDeleteScope by remember { mutableStateOf(false) }

    val edited = {
        original.copy(
            title = title.trim(),
            emoji = emoji.ifBlank { EmojiGuess.forTitle(title) },
            tint = if (isNew) EmojiGuess.tintFor(title) else original.tint,
            color = color,
            kind = kind,
            listId = if (kind == Kind.TODO) listId else original.listId,
            epochDay = if (kind == Kind.PLAN) epochDay else null,
            startMinute = if (kind == Kind.PLAN) startMinute else null,
            daypart = daypart,
            minutes = minutes,
            notes = notes.trim(),
            subtasks = StepEdits.forSaving(steps.toList()),
            repeat = if (kind == Kind.PLAN && master == null) repeat else null,
        )
    }
    val dirty = {
        if (isNew) title.isNotBlank() || steps.isNotEmpty() || notes.isNotBlank() else edited() != original || repeat != originalRepeat
    }
    val save = {
        when {
            title.isBlank() -> titleMissing = true
            // Bei einem Termin einer Serie entscheidet erst die Abfrage, was gespeichert wird.
            master != null && dirty() -> askScope = true
            master != null -> onDismiss()
            else -> {
                vm.saveTask(edited())
                onDismiss()
            }
        }
    }
    val close = { if (full && dirty()) askDiscard = true else onDismiss() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val breakDown = {
        if (title.isNotBlank() && !busy) {
            val alreadyTimed = steps.sumOf { it.minutes ?: 0 }
            val target = (minutes - alreadyTimed).takeIf { durationChosen && it > 0 }
            vm.breakDown(title, target) { items ->
                items.forEach { steps += Subtask(title = it.title, minutes = it.minutes) }
            }
        }
    }
    LaunchedEffect(request) {
        if (request is SheetRequest.Edit && request.breakDown) breakDown()
    }

    ModalBottomSheet(
        // Zurück-Taste und Tipp daneben landen hier: Das Blatt ist dann schon weg, "Weiter bearbeiten" holt es zurück.
        onDismissRequest = { if (full && dirty()) askDiscard = true else onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null,
        sheetGesturesEnabled = !full,
    ) {
        val pickDaypart: (Daypart) -> Unit = {
            daypart = it
            startMinute = null
        }
        if (!full) {
            QuickAdd(
                title = title,
                onTitle = {
                    title = it
                    if (!emojiTouched) emoji = EmojiGuess.forTitle(it)
                },
                kind = kind,
                daypart = daypart,
                startMinute = startMinute,
                minutes = minutes,
                listName = state.lists.firstOrNull { it.id == listId }?.name,
                lists = state.lists.map { list -> list.name to { listId = list.id } },
                onDaypart = pickDaypart,
                onStartMinute = { startMinute = it },
                onMinutes = {
                    minutes = it
                    durationChosen = true
                },
                repeat = repeat,
                repeatOptions = repeatOptions({ repeat = it }, { askCustomRepeat = true }),
                onSave = save,
                onMore = { full = true },
            )
        } else {
            FullForm(
                isNew = isNew,
                showPlace = original.kind == Kind.PLAN,
                title = title,
                titleMissing = titleMissing && title.isBlank(),
                onTitle = {
                    title = it
                    if (!emojiTouched) emoji = EmojiGuess.forTitle(it)
                },
                emoji = emoji.takeIf { title.isNotBlank() || emojiTouched },
                tint = original.tint,
                color = color,
                onEmojiClick = { visualsFor = TASK_VISUALS },
                onClose = close,
                onSave = save,
                kind = kind,
                onKind = { kind = it },
                listName = state.lists.firstOrNull { it.id == listId }?.name,
                lists = state.lists.map { list -> list.name to { listId = list.id } },
                daypart = daypart,
                startMinute = startMinute,
                onDaypart = pickDaypart,
                onStartMinute = { startMinute = it },
                epochDay = epochDay,
                onEpochDay = { epochDay = it },
                minutes = minutes,
                onMinutes = {
                    minutes = it
                    durationChosen = true
                },
                repeat = repeat,
                repeatOptions = repeatOptions({ repeat = it }, { askCustomRepeat = true }),
                onRepeatUntil = { until -> repeat = repeat?.copy(untilEpochDay = until) },
                steps = steps,
                busy = busy,
                onBreakDown = breakDown,
                onPickStepVisuals = { visualsFor = it.id },
                notes = notes,
                onNotes = { notes = it },
                footer = {
                    val startFocus = {
                        val task = edited()
                        vm.saveTask(task)
                        onStart(task)
                        onDismiss()
                    }
                    val delete = {
                        if (master != null) {
                            askDeleteScope = true
                        } else {
                            vm.deleteTask(original.id)
                            onDismiss()
                        }
                    }
                    val colors = MaterialTheme.colorScheme
                    when {
                        isNew -> Unit
                        kind == Kind.TODO -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RoundFormButton(Icons.Outlined.Delete, "Löschen", colors.surfaceContainerHigh, colors.error, delete)
                            if (!original.isDone) {
                                RoundFormButton(Icons.Filled.PlayArrow, "Fokus starten", colors.surfaceContainerHigh, colors.onSurface) {
                                    if (title.isNotBlank()) startFocus()
                                }
                            }
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!original.isDone) {
                                InversePill("Fokus starten", Icons.Filled.PlayArrow, enabled = title.isNotBlank(), onClick = startFocus)
                            }
                            PillLabel("Löschen", container = colors.surfaceVariant, onClick = delete)
                        }
                    }
                },
            )
        }
    }

    visualsFor?.let { target ->
        val step = steps.firstOrNull { it.id == target }
        VisualsSheet(
            emoji = step?.shownEmoji ?: emoji.ifBlank { EmojiGuess.DEFAULT },
            color = if (step != null) step.color else color,
            fallbackTint = step?.let { EmojiGuess.tintFor(it.title) } ?: original.tint,
            onDismiss = { visualsFor = null },
            onSave = { pickedEmoji, pickedColor ->
                if (step != null) {
                    steps[steps.indexOfFirst { it.id == target }] = step.copy(emoji = pickedEmoji, color = pickedColor)
                } else {
                    emoji = pickedEmoji
                    emojiTouched = true
                    color = pickedColor
                }
                visualsFor = null
            },
        )
    }

    if (askCustomRepeat) {
        CustomRepeatDialog(
            initial = repeat,
            startDay = LocalDate.ofEpochDay(epochDay),
            onDismiss = { askCustomRepeat = false },
            onConfirm = {
                repeat = it.copy(untilEpochDay = repeat?.untilEpochDay)
                askCustomRepeat = false
            },
        )
    }

    if (askScope) {
        // Eine neue Regel kann nicht für einen einzelnen Termin gelten.
        ScopeDialog(
            title = "Änderung speichern für",
            allowThis = repeat == originalRepeat,
            onDismiss = { askScope = false },
            onPick = { scope ->
                vm.editOccurrence(edited(), repeat.takeIf { kind == Kind.PLAN }, scope)
                askScope = false
                onDismiss()
            },
        )
    }

    if (askDeleteScope) {
        ScopeDialog(
            title = "Löschen",
            allowThis = true,
            onDismiss = { askDeleteScope = false },
            onPick = { scope ->
                vm.deleteOccurrence(original.id, scope)
                askDeleteScope = false
                onDismiss()
            },
        )
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = {
                askDiscard = false
                scope.launch { sheetState.show() }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Änderungen verwerfen?") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Verwerfen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askDiscard = false
                    scope.launch { sheetState.show() }
                }) { Text("Weiter bearbeiten") }
            },
        )
    }
}

@Composable
private fun QuickAdd(
    title: String,
    onTitle: (String) -> Unit,
    kind: Kind,
    daypart: Daypart,
    startMinute: Int?,
    minutes: Int,
    listName: String?,
    lists: List<Pair<String, () -> Unit>>,
    onDaypart: (Daypart) -> Unit,
    onStartMinute: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    repeat: Repeat?,
    repeatOptions: List<Pair<String, () -> Unit>>,
    onSave: () -> Unit,
    onMore: () -> Unit,
) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val pickTime = { current: Int? ->
        val initial = current?.let { LocalTime.of(it / 60, it % 60) } ?: LocalTime.now()
        TimePickerDialog(context, { _, h, m -> onStartMinute(h * 60 + m) }, initial.hour, initial.minute, true).show()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 21.dp, end = 21.dp, top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val titleStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface)
            BasicTextField(
                value = title,
                onValueChange = onTitle,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .semantics { contentDescription = "Titel" },
                singleLine = true,
                textStyle = titleStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                decorationBox = { field ->
                    Box {
                        if (title.isEmpty()) RotatingPlaceholder(animate = true, titleStyle)
                        field()
                    }
                },
            )
            if (title.isNotBlank()) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClick = onSave)
                        .semantics { contentDescription = "Anlegen" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind == Kind.PLAN) {
                MenuChip(
                    label = if (startMinute != null) AT_TIME else daypart.label,
                    icon = if (startMinute != null) Icons.Outlined.EventAvailable else daypartIcon(daypart),
                    options = Daypart.entries.map { part -> part.label to { onDaypart(part) } } +
                        (AT_TIME to { pickTime(startMinute) }),
                )
                if (startMinute != null) {
                    PillLabel(
                        formatClock(startMinute),
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        tight = true,
                        onClick = { pickTime(startMinute) },
                    )
                }
            } else {
                MenuChip(label = listName ?: "Liste", icon = Icons.Outlined.Checklist, options = lists)
            }
            MenuChip(
                label = durationLabel(minutes),
                options = DURATIONS.map { value -> formatMinutes(value) to { onMinutes(value) } },
            )
            if (kind == Kind.PLAN) {
                MenuChip(label = repeat?.label ?: NO_REPEAT, icon = Icons.Outlined.Repeat, options = repeatOptions)
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.Button, onClick = onMore)
                    .semantics { contentDescription = "Mehr Optionen" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun FullForm(
    isNew: Boolean,
    showPlace: Boolean,
    title: String,
    titleMissing: Boolean,
    onTitle: (String) -> Unit,
    emoji: String?,
    tint: Int,
    color: Int?,
    onEmojiClick: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    kind: Kind,
    onKind: (Kind) -> Unit,
    listName: String?,
    lists: List<Pair<String, () -> Unit>>,
    daypart: Daypart,
    startMinute: Int?,
    onDaypart: (Daypart) -> Unit,
    onStartMinute: (Int) -> Unit,
    epochDay: Long,
    onEpochDay: (Long) -> Unit,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    repeat: Repeat?,
    repeatOptions: List<Pair<String, () -> Unit>>,
    onRepeatUntil: (Long?) -> Unit,
    steps: SnapshotStateList<Subtask>,
    busy: Boolean,
    onBreakDown: () -> Unit,
    onPickStepVisuals: (Subtask) -> Unit,
    notes: String,
    onNotes: (String) -> Unit,
    footer: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val date = LocalDate.ofEpochDay(epochDay)
    val pickTime = { current: Int? ->
        val initial = current?.let { LocalTime.of(it / 60, it % 60) } ?: LocalTime.now()
        TimePickerDialog(context, { _, h, m -> onStartMinute(h * 60 + m) }, initial.hour, initial.minute, true).show()
    }
    val timers = StepEdits.timersOn(steps)
    val isTodo = kind == Kind.TODO

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
            .imePadding(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundFormButton(Icons.Filled.Close, "Schließen", colors.surface, colors.onSurface, onClose)
            Text(
                when {
                    !isNew -> "${kindNoun(kind)} bearbeiten"
                    kind == Kind.TODO -> "Neues To-do"
                    else -> "Neue Aufgabe"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            val valid = title.isNotBlank()
            RoundFormButton(
                Icons.Filled.Check,
                if (isNew) "Anlegen" else "Speichern",
                if (valid) colors.primary else colors.surfaceContainerHigh,
                if (valid) colors.onPrimary else colors.onSurfaceVariant,
                onSave,
            )
        }
        HorizontalDivider(color = colors.surfaceContainerHigh)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
                    if (isTodo) {
                        FormField(label = "Aufgabenname", icon = null, modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = title,
                                onValueChange = onTitle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Titel" },
                                singleLine = true,
                                textStyle = style.copy(fontFamily = SerifFamily, fontSize = 20.sp),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            )
                        }
                    } else {
                        BasicTextField(
                            value = title,
                            onValueChange = onTitle,
                            modifier = Modifier
                                .weight(1f)
                                .clip(FieldShape)
                                .background(colors.surface)
                                .border(1.dp, colors.surfaceContainerHigh, FieldShape)
                                .padding(horizontal = 16.dp, vertical = 15.dp)
                                .semantics { contentDescription = "Titel" },
                            singleLine = true,
                            textStyle = style,
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            decorationBox = { field ->
                                Box {
                                    if (title.isEmpty()) Text("Name der Aufgabe", style = style)
                                    field()
                                }
                            },
                        )
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onEmojiClick)
                            .semantics { contentDescription = "Emoji und Farbe" },
                    ) {
                        EmojiBadge(emoji.orEmpty(), tint, size = 40.dp, color = color, neutral = color == null && !isTodo)
                    }
                }
                if (titleMissing) {
                    Text(
                        "Bitte gib einen Namen ein",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.padding(start = 14.dp, top = 5.dp),
                    )
                }
            }

            if (showPlace) {
                FormDropdown(
                    label = "Ort",
                    value = if (kind == Kind.PLAN) "Im Plan" else "Auf der Liste",
                    icon = if (kind == Kind.PLAN) Icons.Outlined.EventAvailable else Icons.Outlined.Checklist,
                    options = listOf("Im Plan" to { onKind(Kind.PLAN) }, "Auf der Liste" to { onKind(Kind.TODO) }),
                )
            }

            if (kind == Kind.PLAN) {
                FormDropdown(
                    label = "Tageszeit",
                    value = if (startMinute != null) AT_TIME else daypart.label,
                    icon = if (startMinute != null) Icons.Outlined.Schedule else daypartIcon(daypart),
                    options = Daypart.entries.map { part -> part.label to { onDaypart(part) } } +
                        (AT_TIME to { pickTime(startMinute) }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormField(
                        label = "Datum",
                        icon = Icons.Outlined.CalendarMonth,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> onEpochDay(LocalDate.of(y, m + 1, d).toEpochDay()) },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth,
                            ).show()
                        },
                    ) {
                        FieldText(date.format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)))
                    }
                    if (startMinute != null) {
                        FormField(
                            label = "Uhrzeit",
                            icon = Icons.Outlined.Schedule,
                            modifier = Modifier.weight(0.7f),
                            onClick = { pickTime(startMinute) },
                        ) {
                            FieldText(formatClock(startMinute))
                        }
                    } else {
                        DurationField(minutes, steps, timers, onMinutes, Modifier.weight(0.7f))
                    }
                }
                if (startMinute != null) DurationField(minutes, steps, timers, onMinutes, Modifier.fillMaxWidth())
                FormDropdown(
                    label = "Wiederholen",
                    value = repeat?.label ?: NO_REPEAT,
                    icon = Icons.Outlined.Repeat,
                    options = repeatOptions,
                )
                if (repeat != null) {
                    val until = repeat.untilEpochDay?.let(LocalDate::ofEpochDay)
                    FormField(
                        label = "Letzter Tag",
                        icon = Icons.Outlined.EventBusy,
                        onClick = {
                            val initial = until ?: date.plusMonths(1)
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> onRepeatUntil(LocalDate.of(y, m + 1, d).toEpochDay()) },
                                initial.year,
                                initial.monthValue - 1,
                                initial.dayOfMonth,
                            ).apply { datePicker.minDate = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()
                        },
                        trailing = if (until == null) null else {
                            {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Ende entfernen",
                                    tint = colors.onSurface,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable(role = Role.Button) { onRepeatUntil(null) },
                                )
                            }
                        },
                    ) {
                        FieldText(until?.format(DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)) ?: "Ohne Ende")
                    }
                }
            } else {
                FormDropdown(label = "Liste", value = listName ?: "Liste", icon = null, options = lists)
                DurationField(minutes, steps, timers, onMinutes, Modifier.fillMaxWidth())
            }

            StepsEditor(steps, busy, onBreakDown, onPickStepVisuals)

            FormCard {
                val style = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface)
                BasicTextField(
                    value = notes,
                    onValueChange = onNotes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                        .padding(16.dp)
                        .semantics { contentDescription = "Notizen" },
                    textStyle = style,
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { field ->
                        Box {
                            if (notes.isEmpty()) Text("Schreibe deine Notizen...", style = style)
                            field()
                        }
                    },
                )
            }

            if (!isTodo) footer()
        }
        // Bei To-dos bleiben Löschen und Starten als runde Knöpfe unten stehen.
        if (isTodo && !isNew) {
            Box(Modifier.padding(16.dp)) { footer() }
        }
    }
}

private val FieldShape = RoundedCornerShape(24.dp)
private const val AT_TIME = "Zu einer Uhrzeit"
private val DURATIONS = listOf(5, 15, 30, 45, 60, 90)
private const val NO_REPEAT = "Nicht wiederholen"
private val WEEKDAY_LETTERS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

private fun repeatOptions(onPick: (Repeat?) -> Unit, onCustom: () -> Unit): List<Pair<String, () -> Unit>> =
    listOf<Pair<String, () -> Unit>>(NO_REPEAT to { onPick(null) }) +
        RepeatRule.entries.filter { it != RepeatRule.CUSTOM }.map { rule -> rule.label to { onPick(Repeat(rule)) } } +
        ("${RepeatRule.CUSTOM.label}…" to onCustom)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRepeatDialog(
    initial: Repeat?,
    startDay: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Repeat) -> Unit,
) {
    var days by remember {
        mutableStateOf(initial?.takeIf { it.rule == RepeatRule.CUSTOM }?.weekdays?.toSet() ?: setOf(startDay.dayOfWeek.value))
    }
    var weeks by remember { mutableStateOf(initial?.takeIf { it.rule == RepeatRule.CUSTOM }?.everyWeeks ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(RepeatRule.CUSTOM.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Alle sieben Tage passen nebeneinander: Der heute gewählte Tag darf nicht aus dem Bild rutschen.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    WEEKDAY_LETTERS.forEachIndexed { index, name ->
                        val value = index + 1
                        WeekdayToggle(name, value in days) { days = if (value in days) days - value else days + value }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { n ->
                        ChoicePill(if (n == 1) "Jede Woche" else "Alle $n", weeks == n, { weeks = n })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = days.isNotEmpty(), onClick = {
                onConfirm(Repeat(RepeatRule.CUSTOM, weekdays = days.sorted(), everyWeeks = weeks))
            }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun WeekdayToggle(name: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
internal fun ScopeDialog(
    title: String,
    allowThis: Boolean,
    onDismiss: () -> Unit,
    onPick: (Recurrence.EditScope) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(title) },
        text = {
            Column {
                if (allowThis) ScopeOption("Nur dieser Termin") { onPick(Recurrence.EditScope.THIS) }
                ScopeOption("Dieser und alle folgenden") { onPick(Recurrence.EditScope.FOLLOWING) }
                ScopeOption("Alle Termine") { onPick(Recurrence.EditScope.ALL) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ScopeOption(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 4.dp),
    )
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun RoundFormButton(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content)
    }
}

@Composable
private fun FieldText(text: String, enabled: Boolean = true) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
    )
}

/** Umrandetes Feld mit Beschriftung auf der Linie. */
@Composable
private fun FormField(
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = colors.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
    Box(modifier.padding(top = 7.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(FieldShape)
                .background(colors.surface)
                .border(1.dp, colors.surfaceContainerHigh, FieldShape)
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f)) { content() }
            trailing?.invoke()
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
            modifier = Modifier
                .offset(x = 12.dp, y = (-8).dp)
                .background(colors.surfaceContainer)
                .padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun FormDropdown(
    label: String,
    value: String,
    icon: ImageVector?,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FormField(
            label = label,
            icon = icon,
            onClick = { open = true },
            trailing = {
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            },
        ) { FieldText(value) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            options.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        action()
                        open = false
                    },
                )
            }
        }
    }
}

/** Mit Timern pro Schritt ergibt sich die Dauer aus der Summe und ist nur noch Anzeige. */
@Composable
private fun DurationField(
    minutes: Int,
    steps: List<Subtask>,
    timers: Boolean,
    onMinutes: (Int) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    FormField(label = "Dauer (Min)", icon = Icons.Outlined.Schedule, modifier = modifier, enabled = !timers) {
        if (timers) {
            FieldText(steps.sumOf { it.minutes ?: 0 }.toString(), enabled = false)
        } else {
            var text by remember(minutes) { mutableStateOf(minutes.toString()) }
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter(Char::isDigit).take(3)
                    text.toIntOrNull()?.takeIf { it > 0 }?.let(onMinutes)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Dauer in Minuten" },
            )
        }
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}M"
    minutes % 60 == 0 -> "${minutes / 60}Std"
    else -> "${minutes / 60}Std ${minutes % 60}M"
}

@Composable
private fun MenuChip(label: String, icon: ImageVector? = null, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillLabel(label, container = MaterialTheme.colorScheme.surfaceVariant, leading = icon, tight = true, onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            options.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        action()
                        open = false
                    },
                )
            }
        }
    }
}

/** Blendet zwischen Beispielen über, damit das leere Feld zeigt, was alles geht. */
@Composable
private fun RotatingPlaceholder(animate: Boolean, style: TextStyle) {
    val examples = listOf("Was möchtest du tun?", "Wäsche aufhängen", "Zahnarzt anrufen", "Kurz durchatmen")
    var index by remember { mutableStateOf(0) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(animate) {
        while (animate) {
            delay(2400)
            alpha.animateTo(0f, tween(200))
            index = (index + 1) % examples.size
            alpha.animateTo(1f, tween(200))
        }
    }
    Text(
        examples[index],
        Modifier.alpha(alpha.value),
        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

@Composable
private fun PillRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}
