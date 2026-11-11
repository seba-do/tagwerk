package me.ttdrp.tagwerk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.CalendarEvent
import me.ttdrp.tagwerk.model.CalendarEvents
import me.ttdrp.tagwerk.model.DayEntry
import me.ttdrp.tagwerk.model.EventEntry
import me.ttdrp.tagwerk.model.TaskEntry
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.Reorder
import me.ttdrp.tagwerk.model.Swipes
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.daypartOf
import me.ttdrp.tagwerk.model.groupDayEntries
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    state: AppState,
    vm: AppViewModel,
    modifier: Modifier,
    onOpenSettings: () -> Unit,
    onEdit: (Task) -> Unit,
    onBreakDown: (Task) -> Unit,
    onNew: (Task) -> Unit,
    onStart: (Task) -> Unit,
) {
    var menuFor by remember { mutableStateOf<Task?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var dayValue by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val collapsed = rememberCollapsedSections()
    val day = LocalDate.ofEpochDay(dayValue)
    val today = LocalDate.now()
    val dayTasks = Recurrence.forDay(state.tasks, day).filter { it.kind == Kind.PLAN }
    val open = dayTasks.filterNot { it.isDone }
    val done = dayTasks.filter { it.isDone }
    val feedback = LocalFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val swipe = remember { Animatable(0f) }
    LaunchedEffect(dayValue) { listState.scrollToItem(0) }

    val zone = ZoneId.systemDefault()
    val calendarVersion by vm.calendarVersion.collectAsStateWithLifecycle()
    val events by produceState(emptyList<CalendarEvent>(), dayValue, calendarVersion) {
        value = vm.eventsFor(LocalDate.ofEpochDay(dayValue))
    }
    val sections = groupDayEntries(open, events, day, zone)
    val reorder = rememberReorderController(listState) { key ->
        val id = key as? String ?: return@rememberReorderController null
        when {
            id == DONE_ZONE -> Reorder.Row(0, 0, null, null, zoneKey = "done", completes = true)
            id.startsWith("h-") || id.startsWith("e-") -> {
                val section = sections.firstOrNull { it.part.name == id.drop(2) } ?: return@rememberReorderController null
                val takesDrop = section.entries.isEmpty() || section.part.name in collapsed
                Reorder.Row(0, 0, null, section.part, zoneKey = if (takesDrop) emptyKey(section.part) else null)
            }
            id.startsWith("ev-") -> {
                val section = sections.firstOrNull { s -> s.entries.any { it is EventEntry && eventKey(it.event) == id } }
                section?.let { Reorder.Row(0, 0, null, it.part) }
            }
            else -> open.firstOrNull { it.id == id }?.let { task ->
                Reorder.Row(0, 0, task.id, task.startMinute?.let(::daypartOf) ?: task.daypart)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = day != today,
                enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
                exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f),
            ) {
                PillLabel("Heute", onClick = { dayValue = today.toEpochDay() })
            }
            RoundIconButton(Icons.Outlined.Settings, "Einstellungen", onOpenSettings)
            RoundIconButton(
                Icons.Filled.Add,
                "Neue Aufgabe",
                onClick = { onNew(Task(title = "", kind = Kind.PLAN, epochDay = dayValue)) },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = day,
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                label = "day-title",
            ) { shown ->
                Text(
                    shown.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            MonthButton(day, onPick = { dayValue = it.toEpochDay() })
        }

        WeekStrip(day, today, onSelect = { dayValue = it.toEpochDay() })

        Box(
            Modifier
                .weight(1f)
                .graphicsLayer { translationX = swipe.value }
                .pointerInput(Unit) {
                    // Die Liste folgt dem Finger nur gedämpft: Sie deutet den Wechsel an, statt ihn vorwegzunehmen.
                    var raw = 0f
                    val threshold = 80.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { raw = 0f },
                        onDragEnd = {
                            val delta = Swipes.dayDelta(raw, threshold)
                            if (delta != 0) {
                                dayValue += delta
                                feedback.tick()
                            }
                            scope.launch { swipe.animateTo(0f, tween(200)) }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f, tween(200)) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            raw += amount
                            scope.launch { swipe.snapTo(Swipes.damped(raw)) }
                        },
                    )
                },
        ) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        vm.refreshCalendars().join()
                        // Ohne die kurze Pause wäre das Neuladen vorbei, bevor man es sieht.
                        delay(MIN_REFRESH_MS)
                        refreshing = false
                    }
                },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = navClearance()),
            ) {
                sections.forEach { section ->
                    val isCollapsed = section.part.name in collapsed
                    val newInPart = { onNew(Task(title = "", kind = Kind.PLAN, epochDay = dayValue, daypart = section.part)) }
                    item(key = "h-${section.part.name}") {
                        SectionHeader(
                            label = section.part.label,
                            count = section.entries.size,
                            expanded = !isCollapsed,
                            onToggle = { collapsed.toggle(section.part.name) },
                            modifier = Modifier.animateItem(),
                            leading = daypartIcon(section.part),
                            container = daypartColor(section.part) ?: MaterialTheme.colorScheme.surfaceContainer,
                            content = if (daypartColor(section.part) != null) Color.White else MaterialTheme.colorScheme.onBackground,
                            highlighted = isCollapsed && reorder.slot?.zoneKey == emptyKey(section.part),
                            onAdd = newInPart.takeIf { section.entries.isNotEmpty() },
                        )
                    }
                    if (isCollapsed) return@forEach
                    if (section.entries.isEmpty()) {
                        item(key = "e-${section.part.name}") {
                            DashedCard(
                                emptyPlanText(section.part),
                                onClick = newInPart,
                                modifier = Modifier.animateItem(),
                                highlighted = reorder.slot?.zoneKey == emptyKey(section.part),
                            )
                        }
                    }
                    items(section.entries, key = ::entryKey) { entry ->
                        when (entry) {
                            is EventEntry -> EventCard(
                                event = entry.event,
                                timeLabel = CalendarEvents.timeLabel(entry.event, day, zone),
                                onClick = { openEvent(context, entry.event) { vm.notify(it) } },
                                modifier = Modifier.animateItem(),
                            )
                            is TaskEntry -> TaskCard(
                                task = entry.task,
                                onClick = { menuFor = entry.task },
                                onToggle = { vm.toggleDone(entry.task) },
                                onToggleStep = { vm.toggleSubtask(entry.task, it) },
                                modifier = Modifier
                                    .animateItem()
                                    .then(
                                        reorderHandle(reorder, entry.task.id, enabled = entry.task.startMinute == null) { slot, finger ->
                                            if (slot.completes) {
                                                feedback.taskDone(finger)
                                                vm.toggleDone(entry.task)
                                            } else {
                                                vm.moveTask(entry.task.id, slot.beforeTaskId, slot.daypart)
                                            }
                                        },
                                    ),
                            )
                        }
                    }
                }
                if (reorder.draggedId != null) {
                    item(key = DONE_ZONE) {
                        DoneDropZone(highlighted = reorder.slot?.completes == true, modifier = Modifier.animateItem())
                    }
                } else if (done.isNotEmpty()) {
                    item(key = "done-toggle") {
                        DoneHeader(done.size, showDone, onToggle = { showDone = !showDone }, modifier = Modifier.animateItem())
                    }
                }
                if (done.isNotEmpty() && showDone) {
                    items(done, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onClick = { menuFor = task },
                            onToggle = { vm.toggleDone(task) },
                            onToggleStep = { vm.toggleSubtask(task, it) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            }
            DragLayer(reorder) { id ->
                open.firstOrNull { it.id == id }?.let { TaskCard(task = it, onClick = {}, onToggle = {}) }
            }
        }
    }

    menuFor?.let { task ->
        TaskMenu(
            task = task,
            state = state,
            vm = vm,
            onDismiss = { menuFor = null },
            onEdit = onEdit,
            onBreakDown = onBreakDown,
            onStart = onStart,
        )
    }
}

private const val MIN_REFRESH_MS = 500L

private fun emptyKey(part: Daypart) = "empty-${part.name}"
private fun eventKey(event: CalendarEvent) = "ev-${event.eventId}-${event.beginMs}"
private fun entryKey(entry: DayEntry): Any = when (entry) {
    is TaskEntry -> entry.task.id
    is EventEntry -> eventKey(entry.event)
}

private fun emptyPlanText(part: Daypart): String = when (part) {
    Daypart.ANYTIME -> "Platz für alles ohne feste Zeit"
    Daypart.MORNING -> "Der Morgen ist noch frei"
    Daypart.AFTERNOON -> "Der Nachmittag ist noch frei"
    Daypart.EVENING -> "Der Abend ist noch frei"
}

private fun openEvent(context: Context, event: CalendarEvent, onMissing: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMs)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onMissing("Keine Kalender-App gefunden")
    }
}

/** Drei Wochen liegen nebeneinander: So sieht man beim Wischen schon die nächste kommen. */
@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun settle(delta: Int) {
        scope.launch {
            if (delta == 0) {
                offset.animateTo(0f, tween(250))
            } else {
                offset.animateTo(-delta * width, tween(250))
                select(current.plusWeeks(delta.toLong()))
                offset.snapTo(0f)
                feedback.tick()
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
            .clipToBounds()
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        settle(Swipes.weekDelta(offset.value, tracker.calculateVelocity().x, width / 4f))
                    },
                    onDragCancel = { settle(0) },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { offset.snapTo(offset.value + amount) }
                    },
                )
            },
    ) {
        (-1..1).forEach { page ->
            WeekDays(
                monday = monday.plusWeeks(page.toLong()),
                selected = selected,
                today = today,
                onSelect = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset((offset.value + page * width).roundToInt(), 0) },
            )
        }
    }
}

/** Monat und Jahr, tippen springt über den Datumswähler zu einem beliebigen Tag. */
@Composable
private fun MonthButton(day: LocalDate, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .clip(CircleShape)
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth -> onPick(LocalDate.of(year, month + 1, dayOfMonth)) },
                    day.year,
                    day.monthValue - 1,
                    day.dayOfMonth,
                ).show()
            }
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            day.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.GERMAN)).uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun WeekDays(
    monday: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier) {
        (0L..6L).forEach { offset ->
            val date = monday.plusDays(offset)
            val isSelected = date == selected
            val background by animateColorAsState(
                if (isSelected) colors.surfaceContainer else colors.background,
                tween(150),
                label = "day",
            )
            val letter = when {
                isSelected -> colors.primary
                else -> colors.onSurfaceVariant
            }
            val number = when {
                isSelected -> colors.primary
                date == today -> colors.onBackground
                else -> colors.onSurfaceVariant
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(15.dp))
                    .background(background)
                    .clickable { onSelect(date) }
                    .padding(top = 7.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.GERMAN),
                    style = MaterialTheme.typography.labelSmall,
                    color = letter,
                    textAlign = TextAlign.Center,
                )
                Text(
                    date.dayOfMonth.toString(),
                    fontFamily = SerifFamily,
                    fontSize = 24.sp,
                    lineHeight = 31.sp,
                    color = number,
                )
            }
        }
    }
}
