package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.AlarmText
import me.ttdrp.tagwerk.model.FocusAlarms
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAlarmsTest {
    init {
        TestStrings.install()
    }

    private val min = 60_000L
    private val start = 1_000_000L
    private val steps = listOf(
        Subtask(title = "Belege", minutes = 5),
        Subtask(title = "ohne Zeit"),
        Subtask(title = "Formular", minutes = 10),
    )

    private fun at(minutes: Long) = start + minutes * min

    @Test
    fun `ohne Schritte klingelt nur das Ende`() {
        val s = FocusClock.start("t", 30, start)
        assertEquals(at(30), FocusAlarms.nextAtMs(s, emptyList(), start))
        assertEquals(at(30), FocusAlarms.nextAtMs(s, emptyList(), at(29)))
        assertNull(FocusAlarms.nextAtMs(s, emptyList(), at(30)))
    }

    @Test
    fun `jeder Schritt klingelt und das Ende nur einmal`() {
        val s = FocusClock.start("t", 15, start)
        assertEquals(at(5), FocusAlarms.nextAtMs(s, steps, start))
        assertEquals(at(15), FocusAlarms.nextAtMs(s, steps, at(5)))
        assertNull(FocusAlarms.nextAtMs(s, steps, at(15)))
    }

    @Test
    fun `pausiert klingelt nichts und danach verschiebt sich der Alarm`() {
        val paused = FocusClock.pause(FocusClock.start("t", 30, start), at(10))
        assertNull(FocusAlarms.nextAtMs(paused, emptyList(), at(11)))
        val resumed = FocusClock.resume(paused, at(20))
        assertEquals(at(40), FocusAlarms.nextAtMs(resumed, emptyList(), at(20)))
    }

    @Test
    fun `verlaengern schiebt das Ende und klingelt auch nach Ablauf wieder`() {
        val s = FocusClock.extend(FocusClock.start("t", 30, start), 5)
        assertEquals(at(35), FocusAlarms.nextAtMs(s, emptyList(), at(31)))
    }

    @Test
    fun `Schritte hinter dem geplanten Ende zaehlen nicht`() {
        val s = FocusClock.start("t", 7, start)
        assertEquals(at(7), FocusAlarms.nextAtMs(s, steps, at(5)))
    }

    @Test
    fun `Meldung nennt den naechsten Schritt`() {
        val task = Task(title = "Steuer", subtasks = steps)
        val s = FocusClock.start("t", 15, start)
        assertEquals(AlarmText("Steuer", "Up next: Formular"), FocusAlarms.message(s, task, at(5)))
    }

    @Test
    fun `Meldung am Ende nennt die Aufgabe`() {
        val task = Task(title = "Steuer", subtasks = steps)
        val s = FocusClock.start("t", 15, start)
        assertEquals(AlarmText("Steuer", "The planned time is up."), FocusAlarms.message(s, task, at(15)))
    }

    @Test
    fun `verspaeteter Alarm meldet den aktuellen Stand`() {
        val task = Task(title = "Steuer", subtasks = steps)
        val s = FocusClock.start("t", 15, start)
        assertEquals("The planned time is up.", FocusAlarms.message(s, task, at(40))?.text)
    }

    @Test
    fun `nach allen Schritten laeuft die Zusatzzeit weiter`() {
        val task = Task(title = "Steuer", subtasks = steps)
        val s = FocusClock.extend(FocusClock.start("t", 15, start), 5)
        assertEquals(
            AlarmText("Steuer", "All steps are done. The extra time is still running."),
            FocusAlarms.message(s, task, at(15)),
        )
    }

    @Test
    fun `veralteter Alarm bleibt stumm`() {
        val task = Task(title = "Steuer", subtasks = steps)
        val s = FocusClock.start("t", 15, start)
        assertNull(FocusAlarms.message(s, task, at(4)))
        assertNull(FocusAlarms.message(FocusClock.pause(s, at(5)), task, at(6)))
    }

    @Test
    fun `verpasstes Ende wird nur kurz danach nachgeholt`() {
        val s = FocusClock.start("t", 15, start)
        assertFalse(FocusAlarms.missedFinish(s, at(14)))
        assertTrue(FocusAlarms.missedFinish(s, at(20)))
        assertFalse(FocusAlarms.missedFinish(s, at(15 + 61)))
        assertFalse(FocusAlarms.missedFinish(FocusClock.pause(s, at(5)), at(20)))
    }
}
