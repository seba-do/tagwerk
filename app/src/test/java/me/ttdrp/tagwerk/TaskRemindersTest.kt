package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.AlarmText
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.FocusSession
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.ReminderKind
import me.ttdrp.tagwerk.model.ReminderSettings
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TaskReminder
import me.ttdrp.tagwerk.model.TaskReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TaskRemindersTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 6)
    private val both = ReminderSettings()

    /** Zeitpunkt an unserem Testtag, in Minuten seit Mitternacht. */
    private fun at(minute: Int) = day.atStartOfDay(utc).toInstant().toEpochMilli() + minute * 60_000L

    private fun task(id: String = "a", minute: Int? = 600, title: String = "Steuer") = Task(
        id = id,
        title = title,
        emoji = "🧾",
        kind = Kind.PLAN,
        epochDay = day.toEpochDay(),
        startMinute = minute,
        minutes = 30,
    )

    private fun state(vararg tasks: Task) = AppState(tasks = tasks.toList())

    private fun next(s: AppState, now: Int, settings: ReminderSettings = both) =
        TaskReminders.nextAtMs(s, settings, utc, at(now))

    private fun due(s: AppState, since: Int, now: Int, settings: ReminderSettings = both) =
        TaskReminders.due(s, settings, utc, at(since), at(now))

    @Test
    fun `meldet sich zehn Minuten vorher und dann zum Start`() {
        val s = state(task())
        assertEquals(at(590), next(s, 500))
        assertEquals(at(600), next(s, 590))
        assertNull(next(s, 600))
    }

    @Test
    fun `der naechste Zeitpunkt ist der frueheste aller Aufgaben`() {
        val s = state(task("spaet", 900), task("frueh", 600))
        assertEquals(at(590), next(s, 0))
    }

    @Test
    fun `ohne Uhrzeit gibt es keine Meldung`() {
        assertNull(next(state(task(minute = null)), 0))
    }

    @Test
    fun `eine Listenaufgabe meldet sich nicht`() {
        assertNull(next(state(task().copy(kind = Kind.TODO, epochDay = null)), 0))
    }

    @Test
    fun `eine erledigte Aufgabe meldet sich nicht`() {
        assertNull(next(state(task().copy(doneAtMs = at(1))), 0))
    }

    @Test
    fun `abgeschaltete Meldungen werden nicht geplant`() {
        val s = state(task())
        assertEquals(at(600), next(s, 0, ReminderSettings(before = false)))
        assertEquals(at(590), next(s, 0, ReminderSettings(atStart = false)))
        assertNull(next(s, 595, ReminderSettings(atStart = false)))
        assertNull(next(s, 0, ReminderSettings(before = false, atStart = false)))
    }

    @Test
    fun `eine Aufgabe in weniger als zehn Minuten meldet sich nur noch zum Start`() {
        assertEquals(at(600), next(state(task()), 595))
    }

    @Test
    fun `zur Zeit gehoert die Zeitzone des Geraets`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val expected = day.atTime(10, 0).atZone(berlin).toInstant().toEpochMilli()
        assertEquals(expected, TaskReminders.startMs(task(), berlin))
    }

    @Test
    fun `faellig ist was im Fenster liegt`() {
        val s = state(task())
        assertEquals(listOf(TaskReminder("a", ReminderKind.BEFORE, at(590))), due(s, 590, 590))
        assertEquals(listOf(TaskReminder("a", ReminderKind.START, at(600))), due(s, 600, 600))
        assertEquals(emptyList<TaskReminder>(), due(s, 591, 599))
    }

    @Test
    fun `zwei Aufgaben zur selben Zeit melden sich beide`() {
        val s = state(task("a"), task("b"))
        assertEquals(listOf("a", "b"), due(s, 600, 600).map { it.taskId })
    }

    @Test
    fun `nach einer Verspaetung kommt pro Aufgabe nur die letzte Meldung`() {
        assertEquals(listOf(TaskReminder("a", ReminderKind.START, at(600))), due(state(task()), 590, 605))
    }

    @Test
    fun `eine verspaetete Vorwarnung entfaellt wenn die Aufgabe schon begonnen hat`() {
        val onlyBefore = ReminderSettings(atStart = false)
        assertEquals(emptyList<TaskReminder>(), due(state(task()), 590, 601, onlyBefore))
    }

    @Test
    fun `eine alte Startmeldung wird nicht nachgeholt`() {
        val s = state(task())
        assertEquals(1, due(s, 590, 660).size)
        assertEquals(0, due(s, 590, 661).size)
    }

    @Test
    fun `die Aufgabe im laufenden Fokus meldet sich nicht`() {
        val focus = FocusSession(taskId = "a", startedAtMs = at(580), plannedMs = 1_800_000)
        val s = state(task("a"), task("b")).copy(focus = focus)
        assertEquals(listOf("b"), due(s, 600, 600).map { it.taskId })
    }

    @Test
    fun `eine sichtbare Meldung bleibt solange sie noch gilt`() {
        val s = state(task())
        assertTrue(TaskReminders.stillRelevant(s, "a", utc, at(590)))
        assertTrue(TaskReminders.stillRelevant(s, "a", utc, at(700)))
    }

    @Test
    fun `eine sichtbare Meldung verschwindet wenn sie nicht mehr gilt`() {
        val gone = state()
        val done = state(task().copy(doneAtMs = at(595)))
        val moved = state(task(minute = 800))
        val noTime = state(task(minute = null))
        val inFocus = state(task()).copy(focus = FocusSession("a", at(595), 1_800_000))
        listOf(gone, done, moved, noTime, inFocus).forEach {
            assertFalse(TaskReminders.stillRelevant(it, "a", utc, at(595)))
        }
    }

    @Test
    fun `die Vorwarnung nennt die verbleibende Zeit und die Uhrzeit`() {
        val reminder = TaskReminder("a", ReminderKind.BEFORE, at(590))
        assertEquals(
            AlarmText("🧾 Steuer", "Startet in 10 Minuten, um 10:00."),
            TaskReminders.message(reminder, task(), utc, at(590)),
        )
        assertEquals(
            "Startet in 1 Minute, um 10:00.",
            TaskReminders.message(reminder, task(), utc, at(599)).text,
        )
    }

    @Test
    fun `die Startmeldung nennt das geplante Ende`() {
        val reminder = TaskReminder("a", ReminderKind.START, at(600))
        assertEquals(
            AlarmText("🧾 Steuer", "Geht jetzt los. Geplant bis 10:30."),
            TaskReminders.message(reminder, task(), utc, at(600)),
        )
    }
}
