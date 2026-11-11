package me.ttdrp.tagwerk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.ttdrp.tagwerk.model.shownEmoji
import me.ttdrp.tagwerk.model.Daypart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.outlined.Event
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.model.CalendarEvent
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import java.util.Locale

fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}min"
    minutes % 60 == 0 -> "${minutes / 60}std"
    else -> "${minutes / 60}std ${minutes % 60}min"
}

fun formatClock(minuteOfDay: Int): String =
    String.format(Locale.GERMANY, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

@Composable
fun EmojiBadge(emoji: String, tint: Int, size: Dp = 32.dp, color: Int? = null, neutral: Boolean = false) {
    val background = when {
        color != null -> Color(color or 0xFF000000.toInt())
        neutral -> MaterialTheme.colorScheme.surfaceVariant
        else -> tintColor(tint)
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.52f).sp, color = Color.Black)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun SectionPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onBackground,
) {
    Box(modifier.padding(top = 14.dp, bottom = 5.dp)) { PillLabel(text, container = container, content = content) }
}

/** Abschnittskopf: Antippen klappt auf und zu, das Plus erscheint erst, wenn der Abschnitt schon etwas enthält. */
@Composable
fun SectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onBackground,
    highlighted: Boolean = false,
    onAdd: (() -> Unit)? = null,
) {
    val outline by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
        tween(120),
        label = "header-zone",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            PillLabel(
                "$label ($count)",
                modifier = Modifier.border(1.5.dp, outline, CircleShape),
                container = container,
                content = content,
                leading = leading,
                expanded = expanded,
                onClick = onToggle,
            )
        }
        if (onAdd != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Neu in $label" },
                contentAlignment = Alignment.Center,
            ) {
                SmallPlus(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Welche Abschnitte zugeklappt sind. Als Text gespeichert, damit es Drehen und Tab-Wechsel übersteht. */
@Stable
class CollapsedSections(private val read: () -> String, private val write: (String) -> Unit) {
    private fun names() = read().split(SEPARATOR).filter { it.isNotEmpty() }.toSet()
    operator fun contains(name: String): Boolean = name in names()
    fun toggle(name: String) {
        val current = names()
        write((if (name in current) current - name else current + name).joinToString(SEPARATOR))
    }

    private companion object {
        const val SEPARATOR = "|"
    }
}

@Composable
fun rememberCollapsedSections(): CollapsedSections {
    var raw by rememberSaveable { mutableStateOf("") }
    return remember { CollapsedSections({ raw }, { raw = it }) }
}

fun daypartIcon(part: Daypart): ImageVector = when (part) {
    Daypart.ANYTIME -> Icons.Outlined.Schedule
    Daypart.MORNING -> Icons.Outlined.WbTwilight
    Daypart.AFTERNOON -> Icons.Outlined.WbSunny
    Daypart.EVENING -> Icons.Outlined.Bedtime
}

/** Morgen, Nachmittag und Abend sind auch ohne Lesen zu unterscheiden. "Jederzeit" bleibt neutral. */
fun daypartColor(part: Daypart): Color? = when (part) {
    Daypart.ANYTIME -> null
    Daypart.MORNING -> Color(0xFF999264)
    Daypart.AFTERNOON -> Color(0xFF4A3745)
    Daypart.EVENING -> Color(0xFF2A2637)
}

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onToggleStep: ((Subtask) -> Unit)? = null,
    extraMeta: String? = null,
    modifier: Modifier = Modifier,
) {
    val done = task.isDone
    val dim = if (done) 0.5f else 1f
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CardShape)
            .background(if (task.subtasks.isEmpty()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, CardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 14.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.alpha(dim)) { EmojiBadge(task.emoji, task.tint, color = task.color) }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .alpha(dim),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                val meta = listOfNotNull(
                    task.startMinute?.let(::formatClock),
                    formatMinutes(task.effectiveMinutes),
                    extraMeta,
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
            CheckButton(done = done, onToggle = onToggle, taskCenterLabel = task.title)
        }
        if (task.subtasks.isNotEmpty()) StepsPanel(task, onToggleStep)
    }
}

/** Termin aus dem Kalender: Sieht aus wie eine Aufgabe, lässt sich aber weder abhaken noch verschieben. */
@Composable
fun EventCard(event: CalendarEvent, timeLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val badge = Color(event.color or 0xFF000000.toInt())
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, CardShape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(badge, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Event,
                contentDescription = "Kalendertermin",
                tint = if (badge.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 5.dp)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                listOf(timeLabel, event.calendarName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

private val StepTrack = Color(0xFFE7DDFF)

/** Fortschritt in der Karte; aufgeklappt lassen sich die Schritte direkt abhaken. */
@Composable
private fun StepsPanel(task: Task, onToggleStep: ((Subtask) -> Unit)?) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "steps")
    val finished = task.subtasks.count { it.done }
    val fill = remember { Animatable(0f) }
    val target = finished.toFloat() / task.subtasks.size
    LaunchedEffect(target) { fill.animateTo(target, tween(250)) }

    Column(Modifier.alpha(if (task.isDone) 0.6f else 1f)) {
        AnimatedVisibility(expanded) {
            Column {
                task.subtasks.forEach { sub ->
                    Row(
                        Modifier.padding(start = 14.dp, end = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EmojiBadge(sub.shownEmoji, EmojiGuess.tintFor(sub.title), color = sub.color)
                        Text(
                            sub.title,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 9.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (sub.done) 0.5f else 1f),
                            textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                        )
                        CheckButton(
                            done = sub.done,
                            onToggle = { onToggleStep?.invoke(sub) },
                            taskCenterLabel = sub.title,
                            step = true,
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 14.dp, end = 15.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 32.dp, height = 9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(StepTrack),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(32.dp * fill.value)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
            Text(
                "$finished / ${task.subtasks.size}",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Schritte ausblenden" else "Schritte anzeigen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation),
            )
        }
    }
}

/**
 * Der Kreis füllt sich sofort und federt, dann erst verlässt die Karte die Liste.
 * Ohne die kurze Pause sähe man den Haken nie.
 */
@Composable
fun CheckButton(done: Boolean, onToggle: () -> Unit, taskCenterLabel: String, step: Boolean = false) {
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    var center by remember { mutableStateOf<Offset?>(null) }
    var justDone by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) justDone = false }
    val checked = done || justDone
    val scale = remember { Animatable(1f) }
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (done) colors.onSurfaceVariant else colors.primary, tween(120), label = "check")
    Box(
        Modifier
            .size(44.dp)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clip(CircleShape)
            .clickable(role = Role.Checkbox) {
                when {
                    done -> onToggle()
                    step -> {
                        feedback.stepDone(center)
                        onToggle()
                    }
                    !justDone -> {
                        justDone = true
                        scope.launch {
                            scale.snapTo(0.6f)
                            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                        }
                        feedback.taskDone(center)
                        feedback.later(CHECK_VISIBLE_MS, onToggle)
                    }
                }
            }
            .semantics { contentDescription = (if (done) "Wieder öffnen: " else "Abhaken: ") + taskCenterLabel },
        contentAlignment = Alignment.Center,
    ) {
        val ring = colors.onSurface
        val mark = colors.surface
        Canvas(
            Modifier
                .size(21.dp)
                .scale(scale.value),
        ) {
            val radius = size.minDimension / 2f
            if (checked) {
                drawCircle(fill, radius)
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.67f)
                    lineTo(size.width * 0.73f, size.height * 0.35f)
                }
                drawPath(path, mark, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                val width = 1.6.dp.toPx()
                drawCircle(ring, radius - width / 2, style = Stroke(width))
            }
        }
    }
}

private const val CHECK_VISIBLE_MS = 220L

/** Kopf des Erledigt-Bereichs, der Pfeil dreht sich beim Auf- und Zuklappen. */
@Composable
fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(top = 18.dp, bottom = 6.dp)) {
        PillLabel(
            "Erledigt ($count)",
            expanded = expanded,
            onClick = onToggle,
            modifier = Modifier.semantics {
                contentDescription = if (expanded) "Erledigte ausblenden" else "Erledigte anzeigen"
            },
        )
    }
}

/** Solange eine Karte gezogen wird, nimmt diese Fläche sie an und hakt sie ab. */
@Composable
fun DoneDropZone(highlighted: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 14.dp)) {
        DashedCard("Zum Abhaken hier ablegen", onClick = {}, highlighted = highlighted, showPlus = false)
    }
}

/** Pulsierende Platzhalter, solange die KI nachdenkt. */
@Composable
fun PulsingRows(count: Int, modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Column(modifier.alpha(alpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index % 2 == 0) 0.85f else 0.6f)
                    .size(height = 14.dp, width = 0.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp)),
            )
        }
        Spacer(Modifier.size(1.dp))
    }
}
