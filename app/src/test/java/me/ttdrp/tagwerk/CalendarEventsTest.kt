package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.CalendarEvent
import me.ttdrp.tagwerk.model.CalendarEvents
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TaskEntry
import me.ttdrp.tagwerk.model.EventEntry
import me.ttdrp.tagwerk.model.groupDayEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarEventsTest {
    init {
        TestStrings.install()
    }

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val day = LocalDate.of(2026, 11, 6)

    private fun local(date: LocalDate, h: Int, m: Int = 0) = date.atTime(h, m).atZone(berlin).toInstant().toEpochMilli()
    private fun utc(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun event(begin: Long, end: Long, allDay: Boolean = false, title: String = "Termin", id: Long = 1) = CalendarEvent(
        eventId = id,
        calendarId = 7,
        title = title,
        beginMs = begin,
        endMs = end,
        allDay = allDay,
        color = 0,
        calendarName = "Privat",
    )

    private fun timed(h: Int, duration: Int = 1, id: Long = 1) = event(local(day, h), local(day, h + duration), id = id)

    @Test
    fun `ein Termin liegt an seinem Tag`() {
        assertTrue(CalendarEvents.occursOn(timed(9), day, berlin))
        assertFalse(CalendarEvents.occursOn(timed(9), day.plusDays(1), berlin))
    }

    @Test
    fun `ein Termin bis Mitternacht zaehlt nicht fuer den Folgetag`() {
        val e = event(local(day, 22), local(day.plusDays(1), 0))
        assertTrue(CalendarEvents.occursOn(e, day, berlin))
        assertFalse(CalendarEvents.occursOn(e, day.plusDays(1), berlin))
    }

    @Test
    fun `ein Termin ohne Dauer liegt trotzdem an seinem Tag`() {
        val e = event(local(day, 9), local(day, 9))
        assertTrue(CalendarEvents.occursOn(e, day, berlin))
    }

    @Test
    fun `ein ganztaegiger Termin rutscht durch die Zeitzone nicht auf den Nachbartag`() {
        val e = event(utc(day), utc(day.plusDays(1)), allDay = true)
        assertFalse(CalendarEvents.occursOn(e, day.minusDays(1), berlin))
        assertTrue(CalendarEvents.occursOn(e, day, berlin))
        assertFalse(CalendarEvents.occursOn(e, day.plusDays(1), berlin))
    }

    @Test
    fun `mehrtaegig ganztaegig liegt an jedem seiner Tage`() {
        val e = event(utc(day), utc(day.plusDays(3)), allDay = true)
        assertTrue(CalendarEvents.occursOn(e, day.plusDays(2), berlin))
        assertFalse(CalendarEvents.occursOn(e, day.plusDays(3), berlin))
    }

    @Test
    fun `Startzeit nur am ersten Tag`() {
        val e = event(local(day, 22), local(day.plusDays(1), 10))
        assertEquals(22 * 60, CalendarEvents.minuteOn(e, day, berlin))
        assertNull(CalendarEvents.minuteOn(e, day.plusDays(1), berlin))
    }

    @Test
    fun `ganztaegig hat keine Startzeit`() {
        assertNull(CalendarEvents.minuteOn(event(utc(day), utc(day.plusDays(1)), allDay = true), day, berlin))
    }

    @Test
    fun `Beschriftung der Zeit`() {
        fun label(e: CalendarEvent, on: LocalDate = day) = CalendarEvents.timeLabel(e, on, berlin)
        assertEquals("09:30 – 10:00", label(event(local(day, 9, 30), local(day, 10))))
        assertEquals("09:30", label(event(local(day, 9, 30), local(day, 9, 30))))
        assertEquals("All day", label(event(utc(day), utc(day.plusDays(1)), allDay = true)))
        val overnight = event(local(day, 22), local(day.plusDays(1), 10))
        assertEquals("From 22:00", label(overnight))
        assertEquals("Until 10:00", label(overnight, day.plusDays(1)))
        val long = event(local(day.minusDays(1), 22), local(day.plusDays(1), 10))
        assertEquals("All day", label(long))
    }

    @Test
    fun `alle vier Abschnitte sind immer da und Jederzeit steht oben`() {
        val sections = groupDayEntries(emptyList(), emptyList(), day, berlin)
        assertEquals(listOf(Daypart.ANYTIME, Daypart.MORNING, Daypart.AFTERNOON, Daypart.EVENING), sections.map { it.part })
        assertTrue(sections.all { it.entries.isEmpty() })
    }

    @Test
    fun `Termine kommen nach ihrer Uhrzeit in den Abschnitt`() {
        val sections = groupDayEntries(emptyList(), listOf(timed(9), timed(15), timed(20)), day, berlin)
        fun count(part: Daypart) = sections.first { it.part == part }.entries.size
        assertEquals(1, count(Daypart.MORNING))
        assertEquals(1, count(Daypart.AFTERNOON))
        assertEquals(1, count(Daypart.EVENING))
        assertEquals(0, count(Daypart.ANYTIME))
    }

    @Test
    fun `ganztaegige Termine stehen bei Irgendwann`() {
        val allDay = event(utc(day), utc(day.plusDays(1)), allDay = true)
        val sections = groupDayEntries(emptyList(), listOf(allDay), day, berlin)
        assertEquals(1, sections.first { it.part == Daypart.ANYTIME }.entries.size)
    }

    @Test
    fun `Termine fremder Tage werden aussortiert`() {
        val sections = groupDayEntries(emptyList(), listOf(event(local(day.plusDays(1), 9), local(day.plusDays(1), 10))), day, berlin)
        assertTrue(sections.all { it.entries.isEmpty() })
    }

    @Test
    fun `Termine und Aufgaben mit Zeit mischen sich nach der Uhr, freie Aufgaben folgen`() {
        val free = Task(id = "frei", title = "frei", kind = Kind.PLAN, daypart = Daypart.MORNING)
        val at8 = Task(id = "acht", title = "acht", kind = Kind.PLAN, startMinute = 8 * 60)
        val at10 = Task(id = "zehn", title = "zehn", kind = Kind.PLAN, startMinute = 10 * 60)
        val sections = groupDayEntries(listOf(free, at10, at8), listOf(timed(9, id = 42)), day, berlin)
        val keys = sections.first { it.part == Daypart.MORNING }.entries.map {
            when (it) {
                is TaskEntry -> it.task.id
                is EventEntry -> it.event.eventId.toString()
            }
        }
        assertEquals(listOf("acht", "42", "zehn", "frei"), keys)
    }
}
