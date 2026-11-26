package me.ttdrp.tagwerk.ui

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.str
import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.TaskReminders
import java.time.ZoneId
import java.time.LocalDate

private enum class Tab(private val labelRes: Int) {
    TODOS(R.string.tab_lists),
    PLAN(R.string.tab_plan),
    FOCUS(R.string.tab_focus),
    ASSISTANT(R.string.tab_assistant),
    ;

    val label: String get() = str(labelRes)
}

/** Formular-Zustand: entweder eine bestehende Aufgabe oder eine neue Vorlage. */
sealed interface SheetRequest {
    data class Edit(val task: Task, val breakDown: Boolean = false) : SheetRequest
    data class New(val template: Task) : SheetRequest
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.PLAN) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    val state by vm.state.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            vm.clearNotice()
        }
    }

    val openFocus by vm.openFocus.collectAsStateWithLifecycle()
    LaunchedEffect(openFocus) {
        if (openFocus) {
            tab = Tab.FOCUS
            showSettings = false
            vm.focusOpened()
        }
    }

    val openTask by vm.openTask.collectAsStateWithLifecycle()
    LaunchedEffect(openTask) {
        val id = openTask ?: return@LaunchedEffect
        Recurrence.find(state.tasks, id)?.let { task ->
            tab = Tab.PLAN
            showSettings = false
            sheet = SheetRequest.Edit(task)
        }
        vm.taskOpened()
    }

    // Gefragt wird erst, wenn eine Meldung ansteht: beim ersten Timer oder der ersten Aufgabe mit Uhrzeit.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val reminderSettings by vm.reminderSettings.collectAsStateWithLifecycle()
    val needsNotifications = remember(state, reminderSettings) {
        state.focus != null ||
            TaskReminders.nextAtMs(state, reminderSettings, ZoneId.systemDefault(), System.currentTimeMillis()) != null
    }
    LaunchedEffect(needsNotifications) {
        val notificationsOn = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        if (needsNotifications && !notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Neue Termine und eine in Android geänderte Berechtigung sollen ohne Neustart ankommen.
    LifecycleResumeEffect(Unit) {
        vm.refreshCalendars()
        onPauseOrDispose {}
    }

    BackHandler(enabled = showSettings) { showSettings = false }

    val undo by vm.undo.collectAsStateWithLifecycle()
    LaunchedEffect(undo) {
        val deleted = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            str(R.string.notice_deleted, deleted.task.title),
            actionLabel = str(R.string.action_undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete(deleted) else vm.clearUndo()
    }

    // Behält beim Tab-Wechsel den gewählten Tag und die Scroll-Position.
    val tabStates = rememberSaveableStateHolder()
    val startFocus: (Task) -> Unit = { task ->
        vm.startFocus(task)
        tab = Tab.FOCUS
    }

    FeedbackHost(vm) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (showSettings) {
                SettingsScreen(vm, Modifier, onClose = { showSettings = false })
            } else {
                Crossfade(tab, animationSpec = tween(160), label = "tab") { shown ->
                    tabStates.SaveableStateProvider(shown.name) {
                        when (shown) {
                            Tab.PLAN -> PlanScreen(
                                state = state,
                                vm = vm,
                                modifier = Modifier,
                                onOpenSettings = { showSettings = true },
                                onEdit = { sheet = SheetRequest.Edit(it) },
                                onBreakDown = { sheet = SheetRequest.Edit(it, breakDown = true) },
                                onNew = { sheet = SheetRequest.New(it) },
                                onStart = startFocus,
                            )
                            Tab.TODOS -> TodoScreen(
                                state = state,
                                vm = vm,
                                modifier = Modifier,
                                onEdit = { sheet = SheetRequest.Edit(it) },
                                onBreakDown = { sheet = SheetRequest.Edit(it, breakDown = true) },
                                onNew = { sheet = SheetRequest.New(it) },
                                onStart = startFocus,
                            )
                            Tab.FOCUS -> FocusScreen(state, vm, Modifier, onStart = startFocus)
                            Tab.ASSISTANT -> AssistantScreen(vm, Modifier, onOpenSettings = { showSettings = true })
                        }
                    }
                }
                if (!WindowInsets.isImeVisible) {
                    FloatingBar(
                        tab = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showSettings) 16.dp else navClearance()),
            ) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
            }
        }

        sheet?.let { request ->
            TaskSheet(
                request = request,
                state = state,
                vm = vm,
                onDismiss = { sheet = null },
                onStart = startFocus,
            )
        }
    }
}

private val TAB_WIDTH = 78.dp

/** Schwebende Leiste für die drei Ansichten, der Assistent sitzt als eigene Kugel daneben. */
@Composable
private fun FloatingBar(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val tabs = listOf(Tab.TODOS, Tab.PLAN, Tab.FOCUS)
    val index = tabs.indexOf(tab)
    val slide by animateDpAsState(TAB_WIDTH * index.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = 500f), label = "slide")
    val indicatorAlpha by animateFloatAsState(if (index >= 0) 1f else 0f, tween(120), label = "indicator")
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .offset(x = slide)
                    .size(width = TAB_WIDTH, height = 47.dp)
                    .alpha(indicatorAlpha)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            )
            Row {
                tabs.forEach { item ->
                    val selected = item == tab
                    Box(
                        Modifier
                            .size(width = TAB_WIDTH, height = 47.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Tab) { onSelect(item) }
                            .semantics {
                                contentDescription = item.label
                                this.selected = selected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (item) {
                            Tab.TODOS -> CheckSquareIcon(selected)
                            Tab.PLAN -> CalendarIcon(selected)
                            else -> RingIcon(selected)
                        }
                    }
                }
            }
        }
        val orbRing by animateColorAsState(
            if (tab == Tab.ASSISTANT) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceContainerHigh,
            tween(150),
            label = "orb",
        )
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(orbRing)
                .clickable(role = Role.Tab) { onSelect(Tab.ASSISTANT) }
                .semantics {
                    contentDescription = Tab.ASSISTANT.label
                    selected = tab == Tab.ASSISTANT
                },
            contentAlignment = Alignment.Center,
        ) {
            AiOrb(47.dp)
        }
    }
}

@Composable
private fun tabIconColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun CheckSquareIcon(selected: Boolean) {
    val color = tabIconColor(selected)
    val mark = if (selected) MaterialTheme.colorScheme.surfaceVariant else color
    Canvas(Modifier.size(26.dp)) {
        val stroke = 2.dp.toPx()
        val corner = CornerRadius(5.dp.toPx())
        if (selected) {
            drawRoundRect(color, cornerRadius = corner)
        } else {
            drawRoundRect(
                color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = corner,
                style = Stroke(stroke),
            )
        }
        val path = Path().apply {
            moveTo(size.width * 0.26f, size.height * 0.52f)
            lineTo(size.width * 0.43f, size.height * 0.69f)
            lineTo(size.width * 0.75f, size.height * 0.33f)
        }
        drawPath(path, mark, style = Stroke(2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Zeigt das heutige Datum, wie ein Abreißkalender. */
@Composable
private fun CalendarIcon(selected: Boolean) {
    val color = tabIconColor(selected)
    val mark = if (selected) MaterialTheme.colorScheme.surfaceVariant else color
    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val corner = CornerRadius(5.dp.toPx())
            if (selected) {
                drawRoundRect(color, cornerRadius = corner)
            } else {
                drawRoundRect(
                    color,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = corner,
                    style = Stroke(stroke),
                )
            }
            val y = size.height * 0.27f
            drawLine(mark, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
        }
        Text(
            LocalDate.now().dayOfMonth.toString(),
            modifier = Modifier.offset(y = 3.dp),
            color = mark,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RingIcon(selected: Boolean) {
    val track = MaterialTheme.colorScheme.outline
    val arc = tabIconColor(selected)
    Canvas(Modifier.size(26.dp)) {
        val stroke = 3.5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(arc, -70f, 110f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
