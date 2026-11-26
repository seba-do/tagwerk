package me.ttdrp.tagwerk.ui

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.str
import android.icu.lang.UCharacter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.model.EmojiCatalog
import android.graphics.Color as AndroidColor

private val CATEGORY_ICONS: List<ImageVector> = listOf(
    Icons.Outlined.SentimentSatisfied,
    Icons.Outlined.People,
    Icons.Outlined.Park,
    Icons.Outlined.LocalPizza,
    Icons.Outlined.Flight,
    Icons.Outlined.SportsBasketball,
    Icons.Outlined.Lightbulb,
    Icons.Outlined.Block,
    Icons.Outlined.Flag,
)

private val HUES = List(7) { i -> Color(AndroidColor.HSVToColor(floatArrayOf(i * 60f, 1f, 1f))) }

/** Unicode-Name für die Suche. Zusammengesetzte Emojis haben keinen, dann zählt das erste Zeichen. */
private fun emojiName(emoji: String): String = UCharacter.getName(emoji.codePointAt(0)).orEmpty()

/** Emoji und freie Farbe wählen. Die Farbe zählt erst, wenn sie wirklich angefasst wurde. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualsSheet(
    emoji: String,
    color: Int?,
    fallbackTint: Int,
    onDismiss: () -> Unit,
    onSave: (emoji: String, color: Int?) -> Unit,
) {
    var chosen by remember { mutableStateOf(emoji) }
    val hsv = remember {
        FloatArray(3).also { out -> if (color != null) AndroidColor.colorToHSV(color, out) else { out[1] = 0f; out[2] = 1f } }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var colorTouched by remember { mutableStateOf(color != null) }
    val currentColor = if (colorTouched) AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)) else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        sheetGesturesEnabled = false,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmojiGrid(chosen, onPick = { chosen = it })
            ColorPlane(hue, saturation, value) { s, v ->
                saturation = s
                value = v
                colorTouched = true
            }
            HueSlider(hue) {
                hue = it
                // Weiß hat keinen Farbton: Wer am Regler zieht, will eine sichtbare Farbe sehen.
                if (saturation == 0f) saturation = 1f
                colorTouched = true
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(chosen, fallbackTint, size = 40.dp, color = currentColor, neutral = currentColor == null)
                Spacer(Modifier.weight(1f))
                Text(
                    str(R.string.action_cancel),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Text(
                    str(R.string.action_save),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                        .clickable(role = Role.Button) { onSave(chosen, currentColor) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun EmojiGrid(selected: String, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { EmojiCatalog.search(query, ::emojiName) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Jede Kategorie beginnt mit ihrer Überschrift: Deren Position ergibt sich aus den Längen davor.
    val starts = remember {
        var position = 0
        EmojiCatalog.categories.map { category -> position.also { position += category.emojis.size + 1 } }
    }
    val active by remember { derivedStateOf { starts.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0) } }

    Box(
        Modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 11.dp)
                    .semantics { contentDescription = str(R.string.cd_search_emoji) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) {
                            Text(str(R.string.search), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                        field()
                    }
                },
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                state = grid,
                contentPadding = PaddingValues(bottom = 60.dp),
            ) {
                if (query.isNotBlank()) {
                    if (results.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { GridLabel(str(R.string.nothing_found)) }
                    }
                    items(results, key = { "r-$it" }) { EmojiCell(it, it == selected, onPick) }
                } else {
                    EmojiCatalog.categories.forEach { category ->
                        item(key = category.name, span = { GridItemSpan(maxLineSpan) }) { GridLabel(category.name) }
                        items(category.emojis, key = { it }) { EmojiCell(it, it == selected, onPick) }
                    }
                }
            }
        }
        if (query.isBlank()) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CATEGORY_ICONS.forEachIndexed { index, icon ->
                    val isActive = index == active
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable(role = Role.Button) { scope.launch { grid.scrollToItem(starts[index]) } }
                            .semantics { contentDescription = EmojiCatalog.categories[index].name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmojiCell(emoji: String, selected: Boolean, onPick: (String) -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(role = Role.Button) { onPick(emoji) },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 27.sp, color = Color.Black)
    }
}

@Composable
private fun ColorPlane(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(130.dp),
    ) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val report = { position: Offset ->
            onChange((position.x / width).coerceIn(0f, 1f), 1f - (position.y / height).coerceIn(0f, 1f))
        }
        val pure = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        Canvas(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = str(R.string.cd_pick_color) }
                .pointerInput(width, height) { detectTapGestures { report(it) } }
                .pointerInput(width, height) {
                    detectDragGestures(onDragStart = { report(it) }) { change, _ ->
                        change.consume()
                        report(change.position)
                    }
                },
        ) {
            val corner = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
            drawRoundRect(Brush.horizontalGradient(listOf(Color.White, pure)), cornerRadius = corner)
            drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = corner)
            handle(Offset(saturation * size.width, (1f - value) * size.height))
        }
    }
}

@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(26.dp),
    ) {
        val width = with(density) { maxWidth.toPx() }
        val report = { x: Float -> onChange((x / width).coerceIn(0f, 1f) * 360f) }
        Canvas(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = str(R.string.cd_pick_hue) }
                .pointerInput(width) { detectTapGestures { report(it.x) } }
                .pointerInput(width) {
                    detectDragGestures(onDragStart = { report(it.x) }) { change, _ ->
                        change.consume()
                        report(change.position.x)
                    }
                },
        ) {
            val barHeight = 16.dp.toPx()
            drawRoundRect(
                Brush.horizontalGradient(HUES),
                topLeft = Offset(0f, (size.height - barHeight) / 2),
                size = androidx.compose.ui.geometry.Size(size.width, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
            )
            handle(Offset(hue / 360f * size.width, size.height / 2))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.handle(center: Offset) {
    val radius = 11.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.4f), radius + 1.5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
    drawCircle(Color.White, radius, center)
}
