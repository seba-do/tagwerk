package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.FocusSession
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.Repeat
import me.ttdrp.tagwerk.model.RepeatRule
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TodayWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayWidgetTest {
    init {
        TestStrings.install()
    }

    private val day = LocalDate.of(2026, 11, 9)
    private val zone = ZoneId.of("Europe/Berlin")

    private fun plan(id: String, part: Daypart = Daypart.ANYTIME, start: Int? = null, done: Boolean = false) = Task(
        id = id,
        title = id,
        kind = Kind.PLAN,
        epochDay = day.toEpochDay(),
        daypart = part,
        startMinute = start,
        doneAtMs = if (done) 1 else null,
    )

    private fun pick(state: AppState) = TodayWidget.current(state, day, zone)

    @Test
    fun `zeigt die erste offene Aufgabe in der Reihenfolge des Plans`() {
        val state = AppState(
            tasks = listOf(
                plan("abend", Daypart.EVENING),
                plan("erledigt", Daypart.ANYTIME, done = true),
                plan("spaeter", start = 10 * 60),
                plan("frueher", start = 8 * 60),
            ),
        )
        assertEquals("frueher", pick(state)?.task?.id)
        assertFalse(pick(state)!!.running)
    }

    @Test
    fun `ohne offene Aufgabe gibt es nichts zu zeigen`() {
        assertNull(pick(AppState(tasks = listOf(plan("a", done = true)))))
    }

    @Test
    fun `Listeneintraege und andere Tage zaehlen nicht`() {
        val todo = Task(id = "todo", title = "todo", kind = Kind.TODO)
        val tomorrow = plan("morgen").copy(epochDay = day.plusDays(1).toEpochDay())
        assertNull(pick(AppState(tasks = listOf(todo, tomorrow))))
    }

    @Test
    fun `ein laufender Timer hat Vorrang`() {
        val session = FocusSession("laeuft", startedAtMs = 0, plannedMs = 60_000)
        val state = AppState(tasks = listOf(plan("zuerst"), plan("laeuft", Daypart.EVENING)), focus = session)
        assertEquals("laeuft", pick(state)?.task?.id)
        assertEquals(session, pick(state)!!.focus)
        assertTrue(pick(state)!!.running)
    }

    @Test
    fun `ohne Timer gibt es keine Sitzung fuer den Countdown`() {
        assertNull(pick(AppState(tasks = listOf(plan("a"))))!!.focus)
    }

    @Test
    fun `eine erledigte Timer-Aufgabe wird nicht mehr gezeigt`() {
        val state = AppState(
            tasks = listOf(plan("offen"), plan("fertig", done = true)),
            focus = FocusSession("fertig", startedAtMs = 0, plannedMs = 60_000),
        )
        assertEquals("offen", pick(state)?.task?.id)
    }

    @Test
    fun `bis zu drei Schritte bleiben in ihrer Reihenfolge stehen`() {
        val steps = listOf(Subtask(id = "1", title = "a", done = true), Subtask(id = "2", title = "b"))
        assertEquals(listOf("1", "2"), TodayWidget.visibleSteps(steps).map { it.id })
    }

    @Test
    fun `bei mehr als drei Schritten kommen die offenen zuerst`() {
        val steps = listOf(
            Subtask(id = "1", title = "a", done = true),
            Subtask(id = "2", title = "b"),
            Subtask(id = "3", title = "c", done = true),
            Subtask(id = "4", title = "d"),
        )
        assertEquals(listOf("2", "4", "1"), TodayWidget.visibleSteps(steps).map { it.id })
    }

    @Test
    fun `Abhaken schaltet genau einen Schritt um`() {
        val task = plan("a").copy(subtasks = listOf(Subtask(id = "1", title = "x"), Subtask(id = "2", title = "y")))
        val once = TodayWidget.toggleStep(listOf(task), "a", "2")
        assertEquals(listOf(false, true), once.single().subtasks.map { it.done })
        val twice = TodayWidget.toggleStep(once, "a", "2")
        assertEquals(listOf(false, false), twice.single().subtasks.map { it.done })
    }

    @Test
    fun `Abhaken an einem Serientag laesst die Serie unberuehrt`() {
        val master = plan("s").copy(
            repeat = Repeat(RepeatRule.DAILY),
            subtasks = listOf(Subtask(id = "1", title = "x")),
        )
        val id = Recurrence.occurrenceId("s", day.toEpochDay())
        val result = TodayWidget.toggleStep(listOf(master), id, "1")
        assertFalse(result.first { it.id == "s" }.subtasks.single().done)
        assertTrue(result.first { it.id == id }.subtasks.single().done)
    }

    @Test
    fun `unbekannte Aufgaben und Schritte aendern nichts`() {
        val tasks = listOf(plan("a").copy(subtasks = listOf(Subtask(id = "1", title = "x"))))
        assertEquals(tasks, TodayWidget.toggleStep(tasks, "fehlt", "1"))
        assertEquals(tasks, TodayWidget.toggleStep(tasks, "a", "fehlt"))
    }

    @Test
    fun `Pause schaltet zwischen Anhalten und Weiterlaufen um`() {
        val session = FocusSession("a", startedAtMs = 0, plannedMs = 60_000)
        val paused = TodayWidget.togglePause(session, 1_000)
        assertEquals(1_000L, paused.pausedAtMs)
        val resumed = TodayWidget.togglePause(paused, 4_000)
        assertNull(resumed.pausedAtMs)
        assertEquals(3_000L, resumed.pausedTotalMs)
    }

    @Test
    fun `waehrend der Timer laeuft wird regelmaessig neu aufgebaut`() {
        val now = day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val session = FocusSession("a", startedAtMs = now, plannedMs = 30 * 60_000)
        assertEquals(now + 60_000, TodayWidget.nextRefreshAtMs(now, zone, session))
    }

    @Test
    fun `zum Ende des Timers wird neu aufgebaut`() {
        val now = day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val session = FocusSession("a", startedAtMs = now - 50_000, plannedMs = 60_000)
        assertEquals(now + 11_000, TodayWidget.nextRefreshAtMs(now, zone, session))
    }

    @Test
    fun `angehaltene und ueberzogene Timer brauchen keinen Takt`() {
        val now = day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val midnight = TodayWidget.nextRefreshAtMs(now, zone)
        val paused = FocusSession("a", startedAtMs = now, plannedMs = 60_000, pausedAtMs = now)
        val overrun = FocusSession("a", startedAtMs = now - 120_000, plannedMs = 60_000)
        assertEquals(midnight, TodayWidget.nextRefreshAtMs(now, zone, paused))
        assertEquals(midnight, TodayWidget.nextRefreshAtMs(now, zone, overrun))
    }

    @Test
    fun `kurz vor Mitternacht geht der neue Tag vor`() {
        val now = day.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        val session = FocusSession("a", startedAtMs = now, plannedMs = 30 * 60_000)
        assertEquals(TodayWidget.nextRefreshAtMs(now, zone), TodayWidget.nextRefreshAtMs(now, zone, session))
    }

    @Test
    fun `Erledigen beendet den Timer und hakt die Aufgabe ab`() {
        val state = AppState(tasks = listOf(plan("a"), plan("b")), focus = FocusSession("a", startedAtMs = 0, plannedMs = 60_000))
        val result = FocusClock.finish(state, 5)
        assertNull(result.focus)
        assertEquals(5L, result.tasks.first { it.id == "a" }.doneAtMs)
        assertNull(result.tasks.first { it.id == "b" }.doneAtMs)
    }

    @Test
    fun `Erledigen ohne Timer aendert nichts`() {
        val state = AppState(tasks = listOf(plan("a")))
        assertEquals(state, FocusClock.finish(state, 5))
    }

    @Test
    fun `neu aufgebaut wird kurz nach Mitternacht`() {
        val now = day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val at = TodayWidget.nextRefreshAtMs(now, zone)
        assertTrue(at >= midnight && at < midnight + 60_000)
    }
}
