package me.ttdrp.tagwerk.ui

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.str
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.StepEdits
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.shownEmoji

private val DeleteTint = Color(0xFFC9848E)
private val RowGap = 8.dp

/** Schritte im Formular: Das Emoji folgt dem Titel, bis man selbst eines wählt. */
@Composable
fun StepsEditor(
    steps: SnapshotStateList<Subtask>,
    busy: Boolean,
    onBreakDown: () -> Unit,
    onPickVisuals: (Subtask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalFeedback.current
    val timers = StepEdits.timersOn(steps)
    var focusId by remember { mutableStateOf<String?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(1) }
    val gapPx = with(LocalDensity.current) { RowGap.roundToPx() }

    val moveDragged = { amount: Float ->
        dragOffset += amount
        val index = steps.indexOfFirst { it.id == draggedId }
        val target = when {
            dragOffset > rowHeight / 2f && index < steps.lastIndex -> index + 1
            dragOffset < -rowHeight / 2f && index > 0 -> index - 1
            else -> index
        }
        if (target != index) {
            val next = StepEdits.move(steps.toList(), index, target)
            next.forEachIndexed { i, step -> steps[i] = step }
            dragOffset -= (target - index) * rowHeight
            feedback.tick()
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(RowGap)) {
            Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    str(R.string.steps_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PillLabel(
                    str(if (busy) R.string.thinking else R.string.steps_suggest),
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    leading = Icons.Outlined.AutoAwesome,
                    tight = true,
                    onClick = onBreakDown,
                )
            }
            if (busy) PulsingRows(3, Modifier.fillMaxWidth().padding(vertical = 4.dp))
            steps.toList().forEach { step ->
                key(step.id) {
                    val lifted = draggedId == step.id
                    StepRow(
                        step = step,
                        timers = timers,
                        autoFocus = focusId == step.id,
                        onTitle = { steps[steps.indexOfFirst { s -> s.id == step.id }] = step.copy(title = it) },
                        onMinutes = { steps[steps.indexOfFirst { s -> s.id == step.id }] = step.copy(minutes = it) },
                        onDelete = { steps.removeAll { s -> s.id == step.id } },
                        onPickVisuals = { onPickVisuals(step) },
                        grip = Modifier.pointerInput(step.id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggedId = step.id
                                    dragOffset = 0f
                                    feedback.lift()
                                },
                                onDragEnd = { draggedId = null },
                                onDragCancel = { draggedId = null },
                            ) { change, amount ->
                                change.consume()
                                moveDragged(amount.y)
                            }
                        },
                        modifier = Modifier
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (lifted) dragOffset else 0f
                                shadowElevation = if (lifted) 8.dp.toPx() else 0f
                                shape = RoundedCornerShape(23.dp)
                            }
                            .onSizeChanged { rowHeight = it.height + gapPx },
                    )
                }
            }
            AddStepButton {
                val next = StepEdits.add(steps.toList())
                steps += next.last()
                focusId = next.last().id
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { applyTimers(steps, !timers) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(str(R.string.steps_timer), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    str(R.string.steps_timer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = timers,
                enabled = steps.isNotEmpty(),
                onCheckedChange = { applyTimers(steps, it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

private fun applyTimers(steps: SnapshotStateList<Subtask>, on: Boolean) {
    StepEdits.setTimers(steps.toList(), on).forEachIndexed { i, step -> steps[i] = step }
}

@Composable
private fun StepRow(
    step: Subtask,
    timers: Boolean,
    autoFocus: Boolean,
    onTitle: (String) -> Unit,
    onMinutes: (Int) -> Unit,
    onDelete: () -> Unit,
    onPickVisuals: () -> Unit,
    grip: Modifier,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val titleStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface)
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(colors.surface)
            .border(1.dp, colors.surfaceContainerHigh, RoundedCornerShape(23.dp))
            .padding(start = 11.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onPickVisuals)
                .semantics { contentDescription = str(R.string.cd_step_visuals) },
        ) {
            val showEmoji = step.title.isNotBlank() || step.emoji != null
            EmojiBadge(if (showEmoji) step.shownEmoji else "", EmojiGuess.tintFor(step.title), color = step.color, neutral = true)
        }
        BasicTextField(
            value = step.title,
            onValueChange = onTitle,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 9.dp)
                .focusRequester(focus)
                .semantics { contentDescription = str(R.string.cd_step_title) },
            singleLine = true,
            textStyle = titleStyle,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { field ->
                Box {
                    if (step.title.isEmpty()) Text(str(R.string.step_placeholder), style = titleStyle.copy(color = colors.onSurfaceVariant))
                    field()
                }
            },
        )
        if (timers) MinutesPill(step, onMinutes)
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = str(R.string.cd_remove_step), tint = DeleteTint, modifier = Modifier.size(22.dp))
        }
        Box(
            grip
                .size(32.dp)
                .semantics { contentDescription = str(R.string.cd_move_step) },
            contentAlignment = Alignment.Center,
        ) {
            GripDots(colors.onSurface)
        }
    }
}

@Composable
private fun MinutesPill(step: Subtask, onMinutes: (Int) -> Unit) {
    var text by remember(step.id) { mutableStateOf(step.minutes?.toString().orEmpty()) }
    LaunchedEffect(step.minutes) {
        if (text.isNotEmpty() && text.toIntOrNull() != step.minutes) text = step.minutes?.toString().orEmpty()
    }
    val style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    BasicTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(3)
            if (text.isNotEmpty()) onMinutes(StepEdits.minutesFrom(text))
        },
        modifier = Modifier
            .width(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = str(R.string.cd_minutes) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun GripDots(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val radius = 1.9.dp.toPx()
        listOf(0.2f, 0.8f).forEach { y ->
            listOf(0.2f, 0.8f).forEach { x -> drawCircle(color, radius, Offset(size.width * x, size.height * y)) }
        }
    }
}

@Composable
private fun AddStepButton(onClick: () -> Unit) {
    val line = MaterialTheme.colorScheme.surfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    line,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(23.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            str(R.string.add_step).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        SmallPlus(MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
