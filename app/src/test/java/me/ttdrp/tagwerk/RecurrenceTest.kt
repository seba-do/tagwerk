package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.ai.PromptText
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.ReminderKind
import me.ttdrp.tagwerk.model.ReminderSettings
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.Recurrence.EditScope
import me.ttdrp.tagwerk.model.Repeat
import me.ttdrp.tagwerk.model.RepeatRule
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TaskReminders
import me.ttdrp.tagwerk.model.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class RecurrenceTest {
    init {
        TestStrings.install()
    }

    // Der 2. November 2026 ist ein Montag.
    private val monday = LocalDate.of(2026, 11, 2)
    private fun day(offset: Long) = monday.plusDays(offset)
    private fun ep(offset: Long) = day(offset).toEpochDay()

    private fun series(rule: RepeatRule, start: LocalDate = monday, build: Repeat.() -> Repeat = { this }) = Task(
        id = "s",
        title = "Serie",
        kind = Kind.PLAN,
        epochDay = start.toEpochDay(),
        repeat = Repeat(rule).build(),
    )

    private fun occurs(task: Task, date: LocalDate) = Recurrence.occursOn(task, date)
    private fun occId(offset: Long, id: String = "s") = Recurrence.occurrenceId(id, ep(offset))

    @Test
    fun `taeglich ab dem Start und nie davor`() {
        val t = series(RepeatRule.DAILY)
        assertFalse(occurs(t, day(-1)))
        assertTrue(occurs(t, day(0)))
        assertTrue(occurs(t, day(100)))
    }

    @Test
    fun `werktags und am Wochenende`() {
        val work = series(RepeatRule.WEEKDAYS)
        val weekend = series(RepeatRule.WEEKENDS)
        (0L..4L).forEach { assertTrue(occurs(work, day(it))); assertFalse(occurs(weekend, day(it))) }
        (5L..6L).forEach { assertFalse(occurs(work, day(it))); assertTrue(occurs(weekend, day(it))) }
    }

    @Test
    fun `woechentlich und alle zwei Wochen`() {
        val weekly = series(RepeatRule.WEEKLY)
        val biweekly = series(RepeatRule.BIWEEKLY)
        assertTrue(occurs(weekly, day(7)))
        assertFalse(occurs(weekly, day(8)))
        assertFalse(occurs(biweekly, day(7)))
        assertTrue(occurs(biweekly, day(14)))
    }

    @Test
    fun `monatlich am selben Tag und in kurzen Monaten am letzten`() {
        val t = series(RepeatRule.MONTHLY, LocalDate.of(2026, 1, 31))
        assertTrue(occurs(t, LocalDate.of(2026, 2, 28)))
        assertFalse(occurs(t, LocalDate.of(2026, 2, 27)))
        assertTrue(occurs(t, LocalDate.of(2026, 3, 31)))
        assertFalse(occurs(t, LocalDate.of(2026, 3, 30)))
    }

    @Test
    fun `jaehrlich auch vom Schalttag aus`() {
        val t = series(RepeatRule.YEARLY, LocalDate.of(2028, 2, 29))
        assertTrue(occurs(t, LocalDate.of(2029, 2, 28)))
        assertTrue(occurs(t, LocalDate.of(2032, 2, 29)))
        assertFalse(occurs(t, LocalDate.of(2032, 2, 28)))
    }

    @Test
    fun `eigener Rhythmus an gewaehlten Tagen alle zwei Wochen`() {
        val t = series(RepeatRule.CUSTOM) { copy(weekdays = listOf(DayOfWeek.WEDNESDAY.value, DayOfWeek.FRIDAY.value), everyWeeks = 2) }
        assertFalse(occurs(t, day(0)))
        assertTrue(occurs(t, day(2)))
        assertTrue(occurs(t, day(4)))
        assertFalse(occurs(t, day(9)))
        assertTrue(occurs(t, day(16)))
    }

    @Test
    fun `das Ende und Ausnahmen gelten`() {
        val t = series(RepeatRule.DAILY) { copy(untilEpochDay = ep(3)) }.copy(exceptions = listOf(ep(1)))
        assertTrue(occurs(t, day(0)))
        assertFalse(occurs(t, day(1)))
        assertTrue(occurs(t, day(3)))
        assertFalse(occurs(t, day(4)))
    }

    @Test
    fun `der Tag zeigt die Serie und die Vorlage selbst nie`() {
        val plain = Task(id = "p", title = "einmal", kind = Kind.PLAN, epochDay = ep(0))
        val list = listOf(series(RepeatRule.DAILY), plain)
        assertEquals(listOf(occId(0), "p"), Recurrence.forDay(list, day(0)).map { it.id })
        assertEquals(listOf(occId(1)), Recurrence.forDay(list, day(1)).map { it.id })
    }

    @Test
    fun `eine Instanz kennt Serie und Tag und hat offene Schritte`() {
        val master = series(RepeatRule.DAILY).copy(subtasks = listOf(Subtask(id = "a", title = "x", done = true)))
        val instance = Recurrence.forDay(listOf(master), day(2)).single()
        assertEquals("s", instance.seriesId)
        assertEquals(ep(2), instance.epochDay)
        assertNull(instance.repeat)
        assertFalse(instance.subtasks.single().done)
    }

    @Test
    fun `eine Instanz laesst sich ueber ihre Kennung finden`() {
        val list = listOf(series(RepeatRule.WEEKLY))
        assertEquals(ep(7), Recurrence.find(list, occId(7))?.epochDay)
        assertNull(Recurrence.find(list, occId(8)))
        assertNull(Recurrence.find(list, "unbekannt"))
    }

    @Test
    fun `Abhaken betrifft nur diesen Tag und behaelt die Stelle`() {
        val other = Task(id = "p", title = "x", kind = Kind.PLAN, epochDay = ep(0))
        val list = listOf(series(RepeatRule.DAILY), other)
        val done = Recurrence.find(list, occId(0))!!.copy(doneAtMs = 5)
        val saved = Recurrence.upsert(list, done)
        assertEquals(listOf(occId(0), "p"), Recurrence.forDay(saved, day(0)).map { it.id })
        assertTrue(Recurrence.forDay(saved, day(0)).first().isDone)
        assertFalse(Recurrence.forDay(saved, day(1)).single().isDone)
    }

    @Test
    fun `zweimal speichern erzeugt keine Doppelten`() {
        val list = listOf(series(RepeatRule.DAILY))
        val a = Recurrence.upsert(list, Recurrence.find(list, occId(0))!!.copy(title = "eins"))
        val b = Recurrence.upsert(a, Recurrence.find(a, occId(0))!!.copy(title = "zwei"))
        assertEquals(listOf("zwei"), Recurrence.forDay(b, day(0)).map { it.title })
    }

    @Test
    fun `eine Aenderung nur fuer diesen Tag`() {
        val list = listOf(series(RepeatRule.DAILY))
        val edited = Recurrence.find(list, occId(1))!!.copy(title = "Neu")
        val out = Recurrence.applyEdit(list, edited, Repeat(RepeatRule.DAILY), EditScope.THIS)
        assertEquals("Neu", Recurrence.forDay(out, day(1)).single().title)
        assertEquals("Serie", Recurrence.forDay(out, day(2)).single().title)
    }

    @Test
    fun `Verschieben auf einen anderen Tag nur fuer diesen Termin`() {
        val list = listOf(series(RepeatRule.WEEKLY))
        val edited = Recurrence.find(list, occId(7))!!.copy(epochDay = ep(8))
        val out = Recurrence.applyEdit(list, edited, Repeat(RepeatRule.WEEKLY), EditScope.THIS)
        assertTrue(Recurrence.forDay(out, day(7)).isEmpty())
        assertEquals(1, Recurrence.forDay(out, day(8)).size)
    }

    @Test
    fun `dieser und alle folgenden teilt die Serie`() {
        val list = listOf(series(RepeatRule.DAILY))
        val edited = Recurrence.find(list, occId(3))!!.copy(title = "Neu")
        val out = Recurrence.applyEdit(list, edited, Repeat(RepeatRule.DAILY), EditScope.FOLLOWING) { "neu" }
        assertEquals("Serie", Recurrence.forDay(out, day(2)).single().title)
        assertEquals("Neu", Recurrence.forDay(out, day(3)).single().title)
        assertEquals("Neu", Recurrence.forDay(out, day(9)).single().title)
    }

    @Test
    fun `erledigte Tage bleiben beim Teilen stehen und erscheinen nicht doppelt`() {
        val list = listOf(series(RepeatRule.DAILY))
        val done = Recurrence.upsert(list, Recurrence.find(list, occId(5))!!.copy(doneAtMs = 9))
        val edited = Recurrence.find(done, occId(3))!!.copy(title = "Neu")
        val out = Recurrence.applyEdit(done, edited, Repeat(RepeatRule.DAILY), EditScope.FOLLOWING) { "neu" }
        val fifth = Recurrence.forDay(out, day(5))
        assertEquals(1, fifth.size)
        assertTrue(fifth.single().isDone)
    }

    @Test
    fun `alle aendert die ganze Serie und offene Einzelstaende`() {
        val list = listOf(series(RepeatRule.DAILY))
        val single = Recurrence.upsert(list, Recurrence.find(list, occId(4))!!.copy(title = "Einzeln"))
        val edited = Recurrence.find(single, occId(1))!!.copy(title = "Alle")
        val out = Recurrence.applyEdit(single, edited, Repeat(RepeatRule.DAILY), EditScope.ALL)
        assertEquals("Alle", Recurrence.forDay(out, day(0)).single().title)
        assertEquals("Alle", Recurrence.forDay(out, day(4)).single().title)
    }

    @Test
    fun `alle laesst Erledigtes in Ruhe`() {
        val list = listOf(series(RepeatRule.DAILY))
        val done = Recurrence.upsert(list, Recurrence.find(list, occId(0))!!.copy(doneAtMs = 9))
        val edited = Recurrence.find(done, occId(1))!!.copy(title = "Alle")
        val out = Recurrence.applyEdit(done, edited, Repeat(RepeatRule.DAILY), EditScope.ALL)
        assertEquals("Serie", Recurrence.forDay(out, day(0)).single().title)
        assertTrue(Recurrence.forDay(out, day(0)).single().isDone)
    }

    @Test
    fun `ohne Wiederholung bleibt nur der eine Termin`() {
        val list = listOf(series(RepeatRule.DAILY))
        val edited = Recurrence.find(list, occId(2))!!
        val out = Recurrence.applyEdit(list, edited, null, EditScope.ALL) { "einzeln" }
        assertEquals(1, Recurrence.forDay(out, day(2)).size)
        assertTrue(Recurrence.forDay(out, day(3)).isEmpty())
        assertTrue(Recurrence.forDay(out, day(1)).isEmpty())
    }

    @Test
    fun `einen Termin loeschen`() {
        val list = listOf(series(RepeatRule.DAILY))
        val out = Recurrence.delete(list, occId(2), EditScope.THIS)
        assertTrue(Recurrence.forDay(out, day(2)).isEmpty())
        assertEquals(1, Recurrence.forDay(out, day(3)).size)
    }

    @Test
    fun `einen schon bearbeiteten Termin loeschen`() {
        val list = listOf(series(RepeatRule.DAILY))
        val saved = Recurrence.upsert(list, Recurrence.find(list, occId(2))!!.copy(title = "x"))
        assertTrue(Recurrence.forDay(Recurrence.delete(saved, occId(2), EditScope.THIS), day(2)).isEmpty())
    }

    @Test
    fun `diesen und alle folgenden loeschen`() {
        val out = Recurrence.delete(listOf(series(RepeatRule.DAILY)), occId(2), EditScope.FOLLOWING)
        assertEquals(1, Recurrence.forDay(out, day(1)).size)
        assertTrue(Recurrence.forDay(out, day(2)).isEmpty())
        assertTrue(Recurrence.forDay(out, day(20)).isEmpty())
    }

    @Test
    fun `alle loeschen`() {
        val out = Recurrence.delete(listOf(series(RepeatRule.DAILY)), occId(2), EditScope.ALL)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `Verschieben macht aus einer Instanz einen festen Eintrag`() {
        val list = listOf(series(RepeatRule.DAILY))
        val out = Recurrence.materialize(list, occId(1))
        assertEquals(occId(1), out[1].id)
        assertEquals(listOf(ep(1)), out[0].exceptions)
        assertEquals(out, Recurrence.materialize(out, occId(1)))
        assertEquals(list, Recurrence.materialize(list, "gibt es nicht"))
        assertEquals(list, Recurrence.materialize(list, null))
    }

    @Test
    fun `Beschriftungen`() {
        assertEquals("Daily", Repeat(RepeatRule.DAILY).label)
        assertEquals("Mon, Wed", Repeat(RepeatRule.CUSTOM, weekdays = listOf(3, 1)).label)
        assertEquals("Fri · every 2 weeks", Repeat(RepeatRule.CUSTOM, weekdays = listOf(5), everyWeeks = 2).label)
    }

    @Test
    fun `eine Serie erinnert am naechsten passenden Tag`() {
        val zone = ZoneOffset.UTC
        val t = series(RepeatRule.DAILY).copy(startMinute = 600)
        fun at(offset: Long, minute: Int) = day(offset).atStartOfDay(zone).toInstant().toEpochMilli() + minute * 60_000L
        val state = AppState(tasks = listOf(t))
        assertEquals(at(30, 590), TaskReminders.nextAtMs(state, ReminderSettings(), zone, at(30, 0)))
        assertEquals(at(31, 590), TaskReminders.nextAtMs(state, ReminderSettings(), zone, at(30, 600)))
        val due = TaskReminders.due(state, ReminderSettings(), zone, at(30, 600), at(30, 600)).single()
        assertEquals(ReminderKind.START, due.kind)
        assertEquals(Recurrence.occurrenceId("s", ep(30)), due.taskId)
        assertTrue(TaskReminders.stillRelevant(state, due.taskId, zone, at(30, 600)))
    }

    @Test
    fun `ein erledigter Serientag erinnert nicht mehr`() {
        val zone = ZoneOffset.UTC
        val t = series(RepeatRule.DAILY).copy(startMinute = 600)
        val list = Recurrence.upsert(listOf(t), Recurrence.find(listOf(t), occId(30))!!.copy(doneAtMs = 1))
        fun at(offset: Long, minute: Int) = day(offset).atStartOfDay(zone).toInstant().toEpochMilli() + minute * 60_000L
        assertEquals(at(31, 590), TaskReminders.nextAtMs(AppState(tasks = list), ReminderSettings(), zone, at(30, 0)))
    }

    @Test
    fun `der Assistent kennt laufende Serien`() {
        val text = PromptText.chatContext(listOf(series(RepeatRule.DAILY)), day(30))
        assertTrue(text, "Serie" in text && "Daily" in text)
        assertNotNull(text)
    }

    @Test
    fun `die Kopie steht offen direkt hinter dem Original`() {
        val a = Task(id = "a", title = "A", doneAtMs = 5, subtasks = listOf(Subtask(id = "x", title = "Schritt", done = true)))
        val b = Task(id = "b", title = "B")
        val ids = mutableListOf("kopie", "schritt").iterator()

        val result = Recurrence.duplicate(listOf(a, b), a) { ids.next() }

        assertEquals(listOf("a", "kopie", "b"), result.map { it.id })
        val copy = result[1]
        assertNull(copy.doneAtMs)
        assertEquals(listOf("schritt"), copy.subtasks.map { it.id })
        assertFalse(copy.subtasks.single().done)
    }

    @Test
    fun `die Kopie eines Serientags ist eigenstaendig und laesst die Serie unberuehrt`() {
        val other = Task(id = "b", title = "B")
        val master = series(RepeatRule.DAILY)
        val occurrence = Recurrence.find(listOf(master, other), occId(3))!!
        val ids = generateSequence(0) { it + 1 }.map { "neu$it" }.iterator()

        val result = Recurrence.duplicate(listOf(master, other), occurrence) { ids.next() }

        assertEquals(listOf("s", "neu0", "b"), result.map { it.id })
        assertEquals(master, result[0])
        assertNull(result[1].repeat)
        assertNull(result[1].seriesId)
        assertEquals(ep(3), result[1].epochDay)
        assertEquals(2, Recurrence.forDay(result, day(3)).size)
    }
}
