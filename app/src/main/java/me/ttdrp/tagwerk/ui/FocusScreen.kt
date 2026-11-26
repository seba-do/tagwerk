package me.ttdrp.tagwerk.ui

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.str
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.FocusSession
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.RingGesture
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.EmojiGuess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun FocusScreen(state: AppState, vm: AppViewModel, modifier: Modifier, onStart: (Task) -> Unit) {
    val session = state.focus
    val task = session?.let { s -> state.tasks.firstOrNull { it.id == s.taskId } }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val active = session != null && task != null
        AnimatedContent(
            targetState = active,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f)) togetherWith fadeOut(tween(120))
            },
            label = "focus",
        ) { showActive ->
            if (showActive && session != null && task != null) {
                ActiveFocus(session, task, vm)
            } else {
                PickFocus(state, vm, onStart)
            }
        }
    }
}

/** Leerzustand: ein ruhiger, heller Kreis, und darunter die Wahl, womit es losgeht. */
@Composable
private fun PickFocus(state: AppState, vm: AppViewModel, onStart: (Task) -> Unit) {
    val candidates = Recurrence.forDay(state.tasks, LocalDate.now()).filter { !it.isDone && it.kind == Kind.PLAN }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 48.dp, bottom = navClearance()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            str(R.string.focus_pick_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            str(R.string.focus_pick_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(24.dp))
        Box(contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.primaryContainer
            Canvas(Modifier.size(RING_SIZE)) {
                val stroke = RING_STROKE.toPx()
                drawCircle(track, size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            }
            Box(
                Modifier
                    .size(RING_SIZE - RING_STROKE * 2)
                    .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "00:00",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
        Spacer(Modifier.size(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 25, 45).forEach { minutes ->
                PillLabel(str(R.string.focus_quick_minutes, minutes), container = MaterialTheme.colorScheme.surfaceVariant, onClick = { vm.startQuickFocus(minutes) })
            }
        }
        if (candidates.isNotEmpty()) {
            Box(Modifier.fillMaxWidth()) { SectionPill(str(R.string.focus_from_today)) }
            candidates.forEach { item ->
                TaskCard(task = item, onClick = { onStart(item) }, onToggle = { vm.toggleDone(item) })
            }
        }
    }
}

@Composable
private fun ActiveFocus(session: FocusSession, task: Task, vm: AppViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val feedback = LocalFeedback.current
    var ringCenter by remember { mutableStateOf<Offset?>(null) }
    val remaining = FocusClock.remainingMs(session, now)
    val progress = FocusClock.progress(session, now)
    val elapsed = FocusClock.elapsedMs(session, now)
    val paused = session.pausedAtMs != null
    val stage = FocusClock.currentStage(task.subtasks, elapsed)
    val complete = {
        feedback.taskDone(ringCenter)
        vm.finishFocus()
        vm.notify(str(R.string.notice_finished, task.title))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = if (task.subtasks.isEmpty()) 130.dp else 24.dp, bottom = navClearance()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            task.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            clockAt(now - elapsed) + " → " + clockAt(now + remaining),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.size(14.dp))
        FocusRing(
            timerProgress = progress,
            overtime = remaining < 0,
            onCenter = { ringCenter = it },
            onComplete = complete,
        ) {
            val pop = remember { Animatable(0.5f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
            Box(
                Modifier
                    .size(RING_SIZE - RING_STROKE * 2)
                    .background(tintColor(task.tint), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(task.emoji, fontSize = 70.sp, modifier = Modifier.scale(pop.value))
            }
        }
        val pausedAlpha by rememberInfiniteTransition(label = "paused").animateFloat(
            initialValue = 1f,
            targetValue = if (paused) 0.35f else 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "pausedAlpha",
        )
        Text(
            FocusClock.format(remaining),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(top = 12.dp, bottom = 6.dp)
                .alpha(pausedAlpha),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (paused) {
                Text(str(R.string.focus_paused), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            }
            InversePill(
                text = null,
                icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                onClick = { if (paused) vm.resumeFocus() else vm.pauseFocus() },
                description = str(if (paused) R.string.action_resume else R.string.action_pause),
                modifier = Modifier.size(width = 91.dp, height = 41.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillLabel(str(R.string.focus_plus_five), onClick = { vm.extendFocus(5) })
            PillLabel(str(R.string.focus_finish), onClick = complete)
            PillLabel(str(R.string.action_cancel), onClick = { vm.stopFocus() })
        }

        // Wird mit dem Timer laufend neu geprüft, also auch nach der Rückkehr aus den Einstellungen.
        val context = LocalContext.current
        if (!context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            Text(
                str(R.string.focus_notifications_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            )
            PillLabel(str(R.string.focus_enable_notifications), onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            })
        }

        if (task.subtasks.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, CardShape),
            ) {
                task.subtasks.forEachIndexed { index, sub ->
                    StepRow(
                        title = sub.title,
                        minutes = sub.minutes,
                        done = sub.done,
                        currentRemainingMs = stage?.takeIf { it.index == index }?.remainingMs,
                        onToggle = { vm.toggleSubtask(task, sub) },
                    )
                }
            }
        }
    }
}

private fun clockAt(epochMs: Long): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return formatClock(time.hour * 60 + time.minute)
}

@Composable
private fun StepRow(title: String, minutes: Int?, done: Boolean, currentRemainingMs: Long?, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(EmojiGuess.forTitle(title), EmojiGuess.tintFor(title))
        Text(
            title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 9.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (done) 0.5f else 1f),
            textDecoration = if (done) TextDecoration.LineThrough else null,
        )
        when {
            currentRemainingMs != null -> Text(FocusClock.format(currentRemainingMs), color = MaterialTheme.colorScheme.primary)
            minutes != null -> Text(formatMinutes(minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CheckButton(done = done, onToggle = onToggle, taskCenterLabel = title, step = true)
    }
}

/**
 * Zeigt den Timer und lässt sich mit dem Finger vorwärts drehen: alle zehn Prozent ein Tick,
 * bei voll ist die Aufgabe geschafft, beim Loslassen federt der Ring zurück auf den Timer.
 */
@Composable
private fun FocusRing(
    timerProgress: Float,
    overtime: Boolean,
    onCenter: (Offset) -> Unit,
    onComplete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val ring = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val floor by rememberUpdatedState(timerProgress)
    val complete by rememberUpdatedState(onComplete)
    LaunchedEffect(timerProgress, dragging) {
        if (!dragging) ring.animateTo(timerProgress, spring(stiffness = Spring.StiffnessLow))
    }
    val overtimeAlpha by rememberInfiniteTransition(label = "overtime").animateFloat(
        initialValue = 1f,
        targetValue = if (overtime) 0.55f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "overtimeAlpha",
    )
    val track = MaterialTheme.colorScheme.primaryContainer
    val color = MaterialTheme.colorScheme.secondary
    val handle = MaterialTheme.colorScheme.primary
    val arrow = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RING_SIZE)
            .onGloballyPositioned { onCenter(it.boundsInRoot().center) }
            .semantics { contentDescription = str(R.string.cd_focus_ring) }
            .pointerInput(Unit) {
                var last = 0.0
                var current = 0f
                var onRing = false
                var finished = false
                val band = RING_STROKE.toPx() + 16.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        val dx = start.x - size.width / 2f
                        val dy = start.y - size.height / 2f
                        onRing = hypot(dx, dy) >= size.width / 2f - band
                        if (onRing) {
                            dragging = true
                            finished = false
                            current = ring.value
                            last = RingGesture.angle(dx, dy)
                        }
                    },
                    onDrag = { change, _ ->
                        if (onRing && !finished) {
                            change.consume()
                            val angle = RingGesture.angle(
                                change.position.x - size.width / 2f,
                                change.position.y - size.height / 2f,
                            )
                            val next = RingGesture.next(current, floor, RingGesture.delta(last, angle))
                            if (RingGesture.crossedTick(current, next)) feedback.tick()
                            current = next
                            last = angle
                            scope.launch { ring.snapTo(next) }
                            if (next >= 1f) {
                                finished = true
                                complete()
                            }
                        }
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val value = ring.value
            drawArc(
                color.copy(alpha = overtimeAlpha),
                -90f,
                360f * value,
                false,
                Offset(inset, inset),
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Griff an der Spitze: zeigt, dass sich hier etwas bewegen lässt.
            val angle = value * 2f * PI.toFloat() - PI.toFloat() / 2f
            val radius = size.width / 2f - inset
            val head = Offset(size.width / 2f + radius * cos(angle), size.height / 2f + radius * sin(angle))
            drawCircle(handle, stroke / 2f, head)
            rotate(degrees = value * 360f, pivot = head) {
                val reach = stroke * 0.22f
                val line = 1.8.dp.toPx()
                drawLine(arrow, head - Offset(reach, 0f), head + Offset(reach, 0f), line, StrokeCap.Round)
                drawLine(arrow, head + Offset(reach, 0f), head + Offset(0f, -reach * 0.8f), line, StrokeCap.Round)
                drawLine(arrow, head + Offset(reach, 0f), head + Offset(0f, reach * 0.8f), line, StrokeCap.Round)
            }
        }
        content()
    }
}

private val RING_SIZE = 252.dp
private val RING_STROKE = 35.dp
