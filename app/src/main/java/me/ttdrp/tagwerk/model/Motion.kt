package me.ttdrp.tagwerk.model

import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Der Fokus-Ring lässt sich vorwärts drehen. Winkel laufen von oben im Uhrzeigersinn. */
object RingGesture {
    private const val FULL = 2 * PI

    fun angle(dx: Float, dy: Float): Double {
        val raw = atan2(dx.toDouble(), -dy.toDouble())
        return if (raw < 0) raw + FULL else raw
    }

    /** Kürzester Weg zwischen zwei Winkeln: Über die Zwölf hinweg gibt es keinen Sprung. */
    fun delta(previous: Double, current: Double): Double {
        val d = current - previous
        return when {
            d < -PI -> d + FULL
            d > PI -> d - FULL
            else -> d
        }
    }

    /** Nie hinter den Stand des Timers zurück und nie über voll hinaus. */
    fun next(progress: Float, floor: Float, deltaAngle: Double): Float =
        min(1f, max(floor, progress + (deltaAngle / FULL).toFloat()))

    fun crossedTick(old: Float, new: Float): Boolean =
        new < 1f && floor(new * 10f) > floor(old * 10f)
}

object Swipes {
    private const val DAY_DAMPING = 0.3f
    private const val FLING_VELOCITY = 500f

    /** Nach rechts wischen führt zum Vortag, nach links zum Folgetag. */
    fun dayDelta(translationPx: Float, thresholdPx: Float): Int = when {
        translationPx > thresholdPx -> -1
        translationPx < -thresholdPx -> 1
        else -> 0
    }

    fun weekDelta(translationPx: Float, velocityPx: Float, thresholdPx: Float): Int = when {
        translationPx < -thresholdPx || velocityPx < -FLING_VELOCITY -> 1
        translationPx > thresholdPx || velocityPx > FLING_VELOCITY -> -1
        else -> 0
    }

    fun damped(translationPx: Float): Float = translationPx * DAY_DAMPING
}

/**
 * Eigenes kleines Feuerwerk, als reine Rechnung. Eine Linie steigt auf, platzt und zerfällt in Funken.
 * [t] läuft von 0 bis 1, alle Koordinaten sind dp relativ zum Startpunkt, negative y liegen oben.
 */
object Firework {
    const val TASK_MS = 1800
    const val STEP_MS = 700

    data class Point(val x: Float, val y: Float)
    data class Ray(val from: Point, val to: Point, val alpha: Float)
    data class Spark(val x: Float, val y: Float, val radius: Float, val alpha: Float)

    private const val RISE_END = 0.28f
    private const val RISE_HEIGHT = 58f
    private const val SWAY = 7f
    private const val TRAIL_SAMPLES = 14
    private const val RAYS_START = 0.26f
    private const val RAYS_END = 0.46f
    private const val RAY_COUNT = 9
    private const val SPARKS_START = 0.30f
    private const val SPARK_COUNT = 20
    private const val SMALL_SPARK_COUNT = 8

    /** Der sichtbare Teil der aufsteigenden Schlangenlinie. */
    fun trail(t: Float): List<Point> {
        if (t !in 0f..<RISE_END) return emptyList()
        val p = t / RISE_END
        val head = 1f - (1f - p) * (1f - p)
        val tail = max(0f, head - 0.55f) * (1f + p)
        return List(TRAIL_SAMPLES) { i ->
            val along = tail + (head - tail) * i / (TRAIL_SAMPLES - 1)
            Point(SWAY * sin(along * 3f * PI.toFloat()), -RISE_HEIGHT * along)
        }
    }

    /** Die Strahlen des Moments, in dem die Linie platzt. */
    fun rays(t: Float): List<Ray> {
        if (t !in RAYS_START..<RAYS_END) return emptyList()
        val p = (t - RAYS_START) / (RAYS_END - RAYS_START)
        val outer = 6f + 24f * p
        val inner = outer * p
        return List(RAY_COUNT) { i ->
            val a = i * 2f * PI.toFloat() / RAY_COUNT
            Ray(
                from = Point(inner * cos(a), -RISE_HEIGHT + inner * sin(a)),
                to = Point(outer * cos(a), -RISE_HEIGHT + outer * sin(a)),
                alpha = 1f - p,
            )
        }
    }

    /** Funken fliegen auseinander, sinken leicht und verblassen. Das Muster hängt nur am [seed]. */
    fun sparks(seed: Int, t: Float, small: Boolean): List<Spark> {
        val start = if (small) 0f else SPARKS_START
        if (t < start) return emptyList()
        val p = ((t - start) / (1f - start)).coerceIn(0f, 1f)
        val eased = 1f - (1f - p) * (1f - p)
        val random = Random(seed.toLong())
        val count = if (small) SMALL_SPARK_COUNT else SPARK_COUNT
        val originY = if (small) 0f else -RISE_HEIGHT
        return List(count) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val reach = if (small) 12f + random.nextFloat() * 12f else 30f + random.nextFloat() * 40f
            val size = if (small) 1.2f + random.nextFloat() else 1.5f + random.nextFloat() * 1.8f
            val fall = if (small) 3f else 16f
            Spark(
                x = reach * eased * cos(angle),
                y = originY + reach * eased * sin(angle) + fall * p * p,
                radius = size * (1f - 0.4f * p),
                alpha = (1f - p) * (1f - p * 0.3f),
            )
        }
    }
}

/** Verschieben per Long-Press: Die Reihenfolge steckt in der Aufgabenliste selbst. */
object Reorder {
    /** [zoneKey] macht aus der Zeile eine Ablagefläche, [completes] hakt die abgelegte Aufgabe ab. */
    data class Row(
        val top: Int,
        val bottom: Int,
        val taskId: String?,
        val daypart: Daypart?,
        val zoneKey: String? = null,
        val group: String? = null,
        val completes: Boolean = false,
    )

    data class Slot(
        val lineY: Int,
        val beforeTaskId: String?,
        val daypart: Daypart?,
        val zoneKey: String? = null,
        val group: String? = null,
        val completes: Boolean = false,
    )

    /** Einfügestelle für die Fingerposition [y]. Die gezogene Karte selbst zählt nicht als Ziel. */
    fun slotAt(rows: List<Row>, y: Int, draggedId: String): Slot? {
        val targets = rows.filterNot { it.taskId == draggedId }
        if (targets.isEmpty()) return null
        zoneAt(targets, y)?.let { return Slot(it.top, null, it.daypart, it.zoneKey, it.group, it.completes) }

        val lines = targets.filter { it.zoneKey == null }
        if (lines.isEmpty()) return null
        val index = lines.indexOfFirst { y < (it.top + it.bottom) / 2 }.let { if (it < 0) lines.size else it }
        val above = lines.getOrNull(index - 1)
        val below = lines.getOrNull(index)
        val daypart = above?.daypart ?: below?.daypart
        val group = above?.group ?: below?.group
        // Liegt zwischen den beiden Zeilen eine Fläche, gehört die Linie unter die obere und nicht hinter die Fläche.
        val lineY = if (above != null && below != null && below.top > above.bottom) above.bottom else below?.top ?: above?.bottom ?: 0
        return Slot(
            lineY = lineY,
            beforeTaskId = below?.takeIf { it.daypart == daypart && it.group == group }?.taskId,
            daypart = daypart,
            group = group,
        )
    }

    /** "Erledigt" steht zuletzt: Auch alles darunter zählt noch, sonst müsste man die Fläche genau treffen. */
    private fun zoneAt(targets: List<Row>, y: Int): Row? =
        targets.firstOrNull { it.zoneKey != null && y >= it.top && y < it.bottom }
            ?: targets.lastOrNull()?.takeIf { it.completes && y >= it.bottom }

    /** Pixel pro Bild: 0 in der Mitte, zum Rand hin gleichmäßig schneller, bis [maxPx]. */
    fun autoScrollSpeed(y: Float, top: Float, bottom: Float, edge: Float, maxPx: Float): Float = when {
        y < top + edge -> -maxPx * ((top + edge - y) / edge).coerceAtMost(1f)
        y > bottom - edge -> maxPx * ((y - bottom + edge) / edge).coerceAtMost(1f)
        else -> 0f
    }

    fun move(tasks: List<Task>, id: String, beforeId: String?, daypart: Daypart?): List<Task> {
        val moving = tasks.firstOrNull { it.id == id } ?: return tasks
        val rest = tasks.filterNot { it.id == id }
        val updated = if (daypart == null) moving else moving.copy(daypart = daypart)
        val index = rest.indexOfFirst { it.id == beforeId }
        return if (index < 0) rest + updated else rest.take(index) + updated + rest.drop(index)
    }
}
