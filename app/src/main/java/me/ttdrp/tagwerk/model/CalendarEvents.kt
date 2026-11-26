package me.ttdrp.tagwerk.model

import me.ttdrp.tagwerk.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class DeviceCalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

/** Ein Termin aus einem Kalender des Geräts. Wird nur angezeigt und nie gespeichert. */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarName: String,
)

object CalendarEvents {
    /**
     * Android speichert ganztägige Termine in UTC-Mitternacht. In Berlin begänne ein Termin vom 6. sonst
     * schon am Abend des 5. Deshalb zählt bei ihnen das UTC-Datum und nicht die lokale Zeit.
     */
    fun occursOn(event: CalendarEvent, day: LocalDate, zone: ZoneId): Boolean {
        if (event.allDay) {
            val first = Instant.ofEpochMilli(event.beginMs).atZone(ZoneOffset.UTC).toLocalDate()
            val last = Instant.ofEpochMilli(maxOf(event.beginMs, event.endMs - 1)).atZone(ZoneOffset.UTC).toLocalDate()
            return !day.isBefore(first) && !day.isAfter(last)
        }
        val start = startOf(day, zone)
        val end = startOf(day.plusDays(1), zone)
        if (event.beginMs == event.endMs) return event.beginMs in start until end
        return event.beginMs < end && event.endMs > start
    }

    /** Startzeit an diesem Tag in Minuten. Null, wenn der Termin ganztägig ist oder schon vorher begann. */
    fun minuteOn(event: CalendarEvent, day: LocalDate, zone: ZoneId): Int? {
        if (event.allDay || event.beginMs !in startOf(day, zone) until startOf(day.plusDays(1), zone)) return null
        val time = Instant.ofEpochMilli(event.beginMs).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    fun daypartOn(event: CalendarEvent, day: LocalDate, zone: ZoneId): Daypart =
        minuteOn(event, day, zone)?.let(::daypartOf) ?: Daypart.ANYTIME

    fun timeLabel(event: CalendarEvent, day: LocalDate, zone: ZoneId): String {
        if (event.allDay) return str(R.string.event_all_day)
        val dayEnd = startOf(day.plusDays(1), zone)
        val startsToday = event.beginMs >= startOf(day, zone)
        val endsToday = event.endMs <= dayEnd
        return when {
            startsToday && event.beginMs == event.endMs -> clockText(event.beginMs, zone)
            startsToday && endsToday -> "${clockText(event.beginMs, zone)} – ${clockText(event.endMs, zone)}"
            startsToday -> str(R.string.event_from, clockText(event.beginMs, zone))
            endsToday -> str(R.string.event_until, clockText(event.endMs, zone))
            else -> str(R.string.event_all_day)
        }
    }

    private fun startOf(day: LocalDate, zone: ZoneId): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
}
