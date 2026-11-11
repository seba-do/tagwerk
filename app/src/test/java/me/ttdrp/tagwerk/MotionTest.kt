package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Firework
import me.ttdrp.tagwerk.model.Reorder
import me.ttdrp.tagwerk.model.RingGesture
import me.ttdrp.tagwerk.model.Swipes
import me.ttdrp.tagwerk.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class MotionTest {
    @Test
    fun `Winkel laeuft von oben im Uhrzeigersinn`() {
        assertEquals(0.0, RingGesture.angle(0f, -10f), 1e-6)
        assertEquals(PI / 2, RingGesture.angle(10f, 0f), 1e-6)
        assertEquals(PI, RingGesture.angle(0f, 10f), 1e-6)
        assertEquals(3 * PI / 2, RingGesture.angle(-10f, 0f), 1e-6)
    }

    @Test
    fun `Fingerweg ueber die Zwoelf springt nicht`() {
        assertEquals(0.2, RingGesture.delta(2 * PI - 0.1, 0.1), 1e-6)
        assertEquals(-0.2, RingGesture.delta(0.1, 2 * PI - 0.1), 1e-6)
    }

    @Test
    fun `Ring geht nur vorwaerts und hoechstens bis voll`() {
        assertEquals(0.75f, RingGesture.next(0.5f, 0.2f, PI / 2), 1e-4f)
        assertEquals(0.5f, RingGesture.next(0.5f, 0.5f, -PI / 2), 1e-4f)
        assertEquals(1f, RingGesture.next(0.9f, 0.2f, PI), 1e-4f)
    }

    @Test
    fun `Haptik tickt alle zehn Prozent aber nicht bei voll`() {
        assertTrue(RingGesture.crossedTick(0.19f, 0.21f))
        assertFalse(RingGesture.crossedTick(0.21f, 0.29f))
        assertFalse(RingGesture.crossedTick(0.21f, 0.19f))
        assertFalse(RingGesture.crossedTick(0.99f, 1f))
    }

    @Test
    fun `Wischen wechselt den Tag erst ab der Schwelle`() {
        assertEquals(0, Swipes.dayDelta(50f, 100f))
        assertEquals(-1, Swipes.dayDelta(120f, 100f))
        assertEquals(1, Swipes.dayDelta(-120f, 100f))
        assertEquals(30f, Swipes.damped(100f), 1e-4f)
    }

    @Test
    fun `Woche wechselt auch bei schnellem kurzem Wischen`() {
        assertEquals(0, Swipes.weekDelta(-20f, -100f, 100f))
        assertEquals(1, Swipes.weekDelta(-20f, -800f, 100f))
        assertEquals(-1, Swipes.weekDelta(20f, 800f, 100f))
        assertEquals(1, Swipes.weekDelta(-150f, 0f, 100f))
    }

    @Test
    fun `Feuerwerk steigt erst auf und platzt dann`() {
        val early = Firework.trail(0.1f)
        val later = Firework.trail(0.25f)
        assertTrue(early.isNotEmpty())
        assertTrue(later.minOf { it.y } < early.minOf { it.y })
        assertTrue(later.minOf { it.y } < 0f)
        assertTrue(Firework.sparks(1, 0.1f, small = false).isEmpty())
        assertTrue(Firework.rays(0.1f).isEmpty())
        assertTrue(Firework.rays(0.35f).isNotEmpty())
        assertTrue(Firework.sparks(1, 0.6f, small = false).isNotEmpty())
    }

    @Test
    fun `Feuerwerk ist am Ende vollstaendig weg`() {
        assertTrue(Firework.trail(1f).isEmpty())
        assertTrue(Firework.rays(1f).isEmpty())
        assertTrue(Firework.sparks(1, 1f, small = false).all { it.alpha <= 0.001f })
    }

    @Test
    fun `gleicher Seed ergibt dasselbe Bild und anderer Seed ein anderes`() {
        val a = Firework.sparks(7, 0.6f, small = false)
        assertEquals(a, Firework.sparks(7, 0.6f, small = false))
        assertTrue(a != Firework.sparks(8, 0.6f, small = false))
    }

    @Test
    fun `kleines Feuerwerk fuer Schritte braucht keinen Aufstieg und bleibt nah`() {
        val big = Firework.sparks(1, 0.6f, small = false)
        val small = Firework.sparks(1, 0.6f, small = true)
        assertTrue(Firework.sparks(1, 0.05f, small = true).isNotEmpty())
        assertTrue(small.size < big.size)
        assertTrue(small.maxOf { abs(it.x) } < big.maxOf { abs(it.x) })
    }

    private val rows = listOf(
        Reorder.Row(0, 40, null, Daypart.MORNING),
        Reorder.Row(40, 100, "a", Daypart.MORNING),
        Reorder.Row(100, 160, "b", Daypart.MORNING),
        Reorder.Row(160, 200, null, Daypart.EVENING),
        Reorder.Row(200, 260, "c", Daypart.EVENING),
    )

    @Test
    fun `Einfuegestelle haengt an der Kartenmitte`() {
        assertEquals(Reorder.Slot(100, "b", Daypart.MORNING), Reorder.slotAt(rows, 110, "c"))
        assertEquals(Reorder.Slot(160, null, Daypart.MORNING), Reorder.slotAt(rows, 150, "c"))
    }

    @Test
    fun `direkt unter der Ueberschrift landet man am Anfang des Abschnitts`() {
        assertEquals(Reorder.Slot(200, "c", Daypart.EVENING), Reorder.slotAt(rows, 190, "a"))
    }

    @Test
    fun `ganz unten landet man am Ende des letzten Abschnitts`() {
        assertEquals(Reorder.Slot(260, null, Daypart.EVENING), Reorder.slotAt(rows, 999, "a"))
    }

    @Test
    fun `die gezogene Karte selbst zaehlt nicht als Ziel`() {
        val slot = Reorder.slotAt(rows, 60, "a")
        assertEquals("b", slot?.beforeTaskId)
        assertNull(Reorder.slotAt(listOf(Reorder.Row(0, 10, "a", null)), 5, "a"))
    }

    private fun titles(tasks: List<Task>) = tasks.map { it.id }

    @Test
    fun `Verschieben aendert Reihenfolge und Abschnitt`() {
        val tasks = listOf(Task(id = "a", title = "a"), Task(id = "b", title = "b"), Task(id = "c", title = "c"))
        assertEquals(listOf("c", "a", "b"), titles(Reorder.move(tasks, "c", "a", null)))
        assertEquals(listOf("b", "c", "a"), titles(Reorder.move(tasks, "a", null, null)))
        val moved = Reorder.move(tasks, "a", "c", Daypart.EVENING)
        assertEquals(listOf("b", "a", "c"), titles(moved))
        assertEquals(Daypart.EVENING, moved[1].daypart)
        assertEquals(Daypart.ANYTIME, moved[0].daypart)
        assertEquals(tasks, Reorder.move(tasks, "x", "a", null))
    }
}
