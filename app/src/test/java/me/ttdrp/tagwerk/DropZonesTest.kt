package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Grouping
import me.ttdrp.tagwerk.model.Priority
import me.ttdrp.tagwerk.model.Quadrant
import me.ttdrp.tagwerk.model.Reorder
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.applyGroup
import me.ttdrp.tagwerk.model.isDraggableGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DropZonesTest {
    init {
        TestStrings.install()
    }

    private val rows = listOf(
        Reorder.Row(0, 40, null, Daypart.MORNING),
        Reorder.Row(40, 100, "a", Daypart.MORNING),
        Reorder.Row(100, 140, null, Daypart.AFTERNOON, zoneKey = "empty-AFTERNOON"),
        Reorder.Row(140, 200, null, Daypart.AFTERNOON, zoneKey = "empty-AFTERNOON"),
        Reorder.Row(200, 240, null, Daypart.EVENING),
        Reorder.Row(240, 300, "c", Daypart.EVENING),
        Reorder.Row(300, 360, null, null, zoneKey = "done", completes = true),
    )

    @Test
    fun `ueber einer leeren Flaeche gilt die Flaeche statt einer Linie`() {
        val slot = Reorder.slotAt(rows, 170, "c")!!
        assertEquals("empty-AFTERNOON", slot.zoneKey)
        assertEquals(Daypart.AFTERNOON, slot.daypart)
        assertNull(slot.beforeTaskId)
        assertFalse(slot.completes)
    }

    @Test
    fun `auch die Ueberschrift eines leeren Abschnitts nimmt die Karte an`() {
        assertEquals("empty-AFTERNOON", Reorder.slotAt(rows, 110, "c")!!.zoneKey)
    }

    @Test
    fun `ausserhalb der Flaechen bleibt es bei der Einfuegelinie`() {
        val slot = Reorder.slotAt(rows, 260, "a")!!
        assertNull(slot.zoneKey)
        assertEquals("c", slot.beforeTaskId)
        assertEquals(Daypart.EVENING, slot.daypart)
    }

    @Test
    fun `ueber Erledigt wird abgehakt`() {
        val slot = Reorder.slotAt(rows, 330, "a")!!
        assertTrue(slot.completes)
        assertEquals("done", slot.zoneKey)
    }

    @Test
    fun `unterhalb von Erledigt zaehlt die Flaeche weiter`() {
        assertTrue(Reorder.slotAt(rows, 900, "a")!!.completes)
    }

    @Test
    fun `die Einfuegelinie landet nie hinter einer Flaeche`() {
        // Knapp über "Erledigt": Die Karte kommt ans Ende des Abends, nicht in die Fläche.
        val slot = Reorder.slotAt(rows, 299, "a")!!
        assertNull(slot.zoneKey)
        assertEquals(Daypart.EVENING, slot.daypart)
        assertNull(slot.beforeTaskId)
    }

    @Test
    fun `eine Gruppe wird wie ein Abschnitt uebernommen`() {
        val groups = listOf(
            Reorder.Row(0, 40, null, null, group = "Wichtig"),
            Reorder.Row(40, 100, "a", null, group = "Wichtig"),
            Reorder.Row(100, 140, null, null, group = "Normal"),
            Reorder.Row(140, 200, "b", null, group = "Normal"),
        )
        val slot = Reorder.slotAt(groups, 150, "a")!!
        assertEquals("Normal", slot.group)
        assertEquals("b", slot.beforeTaskId)
    }

    @Test
    fun `an den Raendern scrollt die Liste mit und sonst nicht`() {
        val speed = { y: Float -> Reorder.autoScrollSpeed(y, 0f, 1000f, 100f, 20f) }
        assertEquals(0f, speed(500f), 0.01f)
        assertEquals(0f, speed(100f), 0.01f)
        assertEquals(0f, speed(900f), 0.01f)
        assertEquals(-20f, speed(0f), 0.01f)
        assertEquals(20f, speed(1000f), 0.01f)
        assertEquals(-10f, speed(50f), 0.01f)
        assertEquals(10f, speed(950f), 0.01f)
    }

    @Test
    fun `jenseits des Rands wird nicht schneller gescrollt`() {
        assertEquals(20f, Reorder.autoScrollSpeed(1400f, 0f, 1000f, 100f, 20f), 0.01f)
        assertEquals(-20f, Reorder.autoScrollSpeed(-300f, 0f, 1000f, 100f, 20f), 0.01f)
    }

    @Test
    fun `eine Gruppe setzt Wichtigkeit oder Quadrant`() {
        val task = Task(title = "x")
        assertEquals(Priority.HIGH, applyGroup(task, Grouping.PRIORITY, Priority.HIGH.label).priority)
        assertEquals(Quadrant.NOW, applyGroup(task, Grouping.QUADRANT, Quadrant.NOW.label).quadrant)
    }

    @Test
    fun `eine unbekannte Gruppe aendert nichts`() {
        val task = Task(title = "x")
        assertEquals(task, applyGroup(task, Grouping.PRIORITY, "Gibt es nicht"))
        assertEquals(task, applyGroup(task, Grouping.NONE, Priority.HIGH.label))
    }

    @Test
    fun `nach Dauer laesst sich nicht ziehen`() {
        assertFalse(isDraggableGrouping(Grouping.SIZE))
        assertTrue(isDraggableGrouping(Grouping.NONE))
        assertTrue(isDraggableGrouping(Grouping.PRIORITY))
        assertTrue(isDraggableGrouping(Grouping.QUADRANT))
    }
}
