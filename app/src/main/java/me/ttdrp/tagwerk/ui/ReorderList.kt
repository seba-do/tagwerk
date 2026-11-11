package me.ttdrp.tagwerk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.ttdrp.tagwerk.model.Reorder
import kotlin.math.roundToInt

const val DONE_ZONE = "done-zone"

/** Alle Positionen sind Listen-Koordinaten: 0 ist die Oberkante des Inhalts unter dem oberen Innenabstand. */
@Stable
class ReorderController(
    private val listState: LazyListState,
    private val rowFor: (key: Any) -> Reorder.Row?,
) {
    var draggedId by mutableStateOf<String?>(null)
        private set
    var slot by mutableStateOf<Reorder.Slot?>(null)
        private set
    private var pointerY by mutableFloatStateOf(0f)
    private var grabY by mutableFloatStateOf(0f)
    private var layerOrigin by mutableStateOf(Offset.Zero)
    private var layerWidth by mutableFloatStateOf(0f)

    private val topPadding: Int get() = listState.layoutInfo.beforeContentPadding

    val overlayY: Float get() = pointerY - grabY + topPadding
    val lineY: Float? get() = slot?.takeIf { it.zoneKey == null }?.let { (it.lineY + topPadding).toFloat() }

    fun start(id: String, localY: Float) {
        val top = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: return
        grabY = localY
        pointerY = top + localY
        draggedId = id
        slot = currentSlot()
    }

    fun drag(deltaY: Float) {
        pointerY += deltaY
        slot = currentSlot()
    }

    fun finish(): Reorder.Slot? = slot.also { clear() }

    fun clear() {
        draggedId = null
        slot = null
    }

    fun placeLayer(origin: Offset, width: Float) {
        layerOrigin = origin
        layerWidth = width
    }

    /** Wo der Finger gerade liegt, in Fensterkoordinaten: Dort startet das Feuerwerk beim Abhaken. */
    fun fingerInRoot(): Offset = layerOrigin + Offset(layerWidth / 2, pointerY + topPadding)

    /** Am oberen und unteren Rand scrollt die Liste mit, damit auch entfernte Abschnitte erreichbar sind. */
    suspend fun autoScroll(edgePx: Float, maxPx: Float) {
        while (true) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val bottom = (info.viewportEndOffset - info.afterContentPadding).toFloat()
            val speed = Reorder.autoScrollSpeed(pointerY, 0f, bottom, edgePx, maxPx)
            if (speed != 0f) {
                listState.scrollBy(speed)
                slot = currentSlot()
            }
        }
    }

    private fun currentSlot(): Reorder.Slot? {
        val id = draggedId ?: return null
        val rows = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            rowFor(item.key)?.copy(top = item.offset, bottom = item.offset + item.size)
        }
        return Reorder.slotAt(rows, pointerY.toInt(), id)
    }
}

/**
 * Die Karte bleibt beim Ziehen als Lücke stehen, sichtbar bewegt sich nur die Kopie in [DragLayer].
 * Sie wird angeheftet: Sonst verwürfe die Liste sie beim Mitscrollen und die Geste bräche ab.
 */
@Composable
fun reorderHandle(
    controller: ReorderController,
    taskId: String,
    enabled: Boolean,
    onDrop: (Reorder.Slot, Offset) -> Unit,
): Modifier {
    if (!enabled) return Modifier
    val feedback = LocalFeedback.current
    val drop by rememberUpdatedState(onDrop)
    val pinnable = LocalPinnableContainer.current
    val pin = remember { arrayOfNulls<PinnableContainer.PinnedHandle>(1) }
    val release = {
        pin[0]?.release()
        pin[0] = null
    }
    DisposableEffect(Unit) { onDispose { release() } }
    return Modifier
        .graphicsLayer { alpha = if (controller.draggedId == taskId) 0f else 1f }
        .pointerInput(taskId) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    pin[0] = pinnable?.pin()
                    controller.start(taskId, it.y)
                    feedback.lift()
                },
                onDrag = { change, amount ->
                    change.consume()
                    controller.drag(amount.y)
                },
                onDragEnd = {
                    val finger = controller.fingerInRoot()
                    controller.finish()?.let { drop(it, finger) }
                    release()
                },
                onDragCancel = {
                    controller.clear()
                    release()
                },
            )
        }
}

/** Die angehobene Karte, etwas eingerückt, dazu die gestrichelte Linie an der Einfügestelle. */
@Composable
fun DragLayer(controller: ReorderController, card: @Composable (String) -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val feedback = LocalFeedback.current
    val density = LocalDensity.current
    val id = controller.draggedId ?: return
    LaunchedEffect(id) {
        controller.autoScroll(with(density) { 72.dp.toPx() }, with(density) { 9.dp.toPx() })
    }
    val zone = controller.slot?.zoneKey
    LaunchedEffect(zone) { if (zone != null) feedback.tick() }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { controller.placeLayer(it.positionInRoot(), it.size.width.toFloat()) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = controller.lineY ?: return@Canvas
            drawLine(
                color,
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, controller.overlayY.roundToInt()) }
                .padding(horizontal = 26.dp)
                .shadow(12.dp, CardShape),
        ) { card(id) }
    }
}

@Composable
fun rememberReorderController(
    listState: LazyListState,
    rowFor: (key: Any) -> Reorder.Row?,
): ReorderController {
    val latest by rememberUpdatedState(rowFor)
    return remember(listState) { ReorderController(listState) { latest(it) } }
}
