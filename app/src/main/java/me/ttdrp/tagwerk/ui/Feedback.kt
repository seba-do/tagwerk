package me.ttdrp.tagwerk.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.Firework
import kotlin.random.Random

data class FeedbackSettings(val sound: Boolean = true, val motion: Boolean = true, val haptics: Boolean = true)


/** Eine Stelle für Feuerwerk, Ton und Haptik. Jeder Teil lässt sich in den Einstellungen abschalten. */
@Stable
class FeedbackController internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val sounds: DoneSounds,
    private val settings: () -> FeedbackSettings,
) {
    internal val bursts = mutableStateListOf<BurstEntry>()
    private var nextId = 0L

    fun taskDone(center: Offset?) {
        haptic(HapticFeedbackType.Confirm)
        if (settings().sound) sounds.playTask()
        burst(center, small = false)
    }

    fun stepDone(center: Offset?) {
        haptic(HapticFeedbackType.ToggleOn)
        if (settings().sound) sounds.playStep()
        burst(center, small = true)
    }

    fun tick() = haptic(HapticFeedbackType.SegmentTick)
    fun threshold() = haptic(HapticFeedbackType.GestureThresholdActivate)
    fun lift() = haptic(HapticFeedbackType.LongPress)

    /** Läuft auch weiter, wenn die auslösende Karte in der Zwischenzeit verschwindet. */
    fun later(delayMs: Long, block: () -> Unit) {
        scope.launch {
            delay(delayMs)
            block()
        }
    }

    private fun haptic(type: HapticFeedbackType) {
        if (settings().haptics) haptics.performHapticFeedback(type)
    }

    private fun burst(center: Offset?, small: Boolean) {
        if (center == null || !settings().motion || animationsDisabled()) return
        bursts += BurstEntry(nextId++, center, small, Random.nextInt())
    }

    /** Wer Animationen im System abgeschaltet hat, bekommt auch kein Feuerwerk. */
    private fun animationsDisabled(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

internal data class BurstEntry(val id: Long, val center: Offset, val small: Boolean, val seed: Int)

internal class DoneSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val task = pool.load(context, R.raw.tagwerk_task_done, 1)
    private val step = pool.load(context, R.raw.tagwerk_step_done, 1)

    fun playTask() { pool.play(task, 0.8f, 0.8f, 1, 0, 1f) }
    fun playStep() { pool.play(step, 0.6f, 0.6f, 1, 0, 1f) }
    fun release() = pool.release()
}

val LocalFeedback = staticCompositionLocalOf<FeedbackController> { error("FeedbackHost fehlt") }

@Composable
fun FeedbackHost(vm: AppViewModel, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val current by vm.feedback.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(current)
    val sounds = remember { DoneSounds(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    val controller = remember { FeedbackController(context.applicationContext, scope, haptics, sounds) { latest } }

    CompositionLocalProvider(LocalFeedback provides controller) {
        Box(Modifier.fillMaxSize()) {
            content()
            controller.bursts.toList().forEach { burst ->
                key(burst.id) {
                    FireworkOverlay(burst) { controller.bursts.remove(burst) }
                }
            }
        }
    }
}

/** Zeichnet nur und fängt keine Berührungen ab. */
@Composable
private fun FireworkOverlay(burst: BurstEntry, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val duration = if (burst.small) Firework.STEP_MS else Firework.TASK_MS
        progress.animateTo(1f, tween(duration, easing = LinearEasing))
        onFinished()
    }
    val first = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        fun at(x: Float, y: Float) = burst.center + Offset(x.dp.toPx(), y.dp.toPx())
        if (!burst.small) {
            val line = Firework.trail(t)
            if (line.size > 1) {
                val path = Path()
                line.forEachIndexed { i, p ->
                    val o = at(p.x, p.y)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, first, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Firework.rays(t).forEach { ray ->
                drawLine(
                    first.copy(alpha = ray.alpha),
                    at(ray.from.x, ray.from.y),
                    at(ray.to.x, ray.to.y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Firework.sparks(burst.seed, t, burst.small).forEachIndexed { i, spark ->
            val color: Color = if (i % 3 == 2) second else first
            drawCircle(color.copy(alpha = spark.alpha.coerceIn(0f, 1f)), spark.radius.dp.toPx(), at(spark.x, spark.y))
        }
    }
}
