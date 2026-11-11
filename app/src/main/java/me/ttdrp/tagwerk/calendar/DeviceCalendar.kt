package me.ttdrp.tagwerk.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.ttdrp.tagwerk.model.CalendarEvent
import me.ttdrp.tagwerk.model.CalendarEvents
import me.ttdrp.tagwerk.model.DeviceCalendarInfo
import java.time.LocalDate
import java.time.ZoneId

/**
 * Liest die Kalender, die auf dem Gerät eingerichtet sind: Google-Konten, Exchange oder Sync-Apps wie DAVx5.
 * So braucht Tagwerk weder Server noch eigene Anmeldung. Es wird nur gelesen, nie geschrieben.
 */
class DeviceCalendar(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver

    fun hasPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(): List<DeviceCalendarInfo> {
        if (!hasPermission()) return emptyList()
        val found = mutableListOf<DeviceCalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
            ),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
        )?.use { c ->
            while (c.moveToNext()) {
                found += DeviceCalendarInfo(
                    id = c.getLong(0),
                    name = c.getString(1).orEmpty().ifBlank { "Kalender" },
                    account = c.getString(2).orEmpty(),
                    color = c.getInt(3),
                )
            }
        }
        return found
    }

    /** Liest bewusst einen Tag zu viel auf beiden Seiten: Die Zuordnung zum Tag trifft [CalendarEvents]. */
    fun events(day: LocalDate, zone: ZoneId, hidden: Set<Long>): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val from = day.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = day.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val found = mutableListOf<CalendarEvent>()
        CalendarContract.Instances.query(resolver, PROJECTION, from, to)?.use { c ->
            while (c.moveToNext()) {
                val calendarId = c.getLong(1)
                if (calendarId in hidden) continue
                if (c.getInt(COL_ATTENDEE) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (c.getInt(COL_STATUS) == CalendarContract.Instances.STATUS_CANCELED) continue
                val event = CalendarEvent(
                    eventId = c.getLong(0),
                    calendarId = calendarId,
                    title = c.getString(2).orEmpty().ifBlank { "Ohne Titel" },
                    beginMs = c.getLong(3),
                    endMs = c.getLong(4),
                    allDay = c.getInt(5) != 0,
                    color = c.getInt(6),
                    calendarName = c.getString(7).orEmpty(),
                )
                if (CalendarEvents.occursOn(event, day, zone)) found += event
            }
        }
        return found.sortedBy { it.beginMs }
    }

    /** Meldet Änderungen, zum Beispiel wenn eine Synchronisation neue Termine bringt. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private companion object {
        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )
        const val COL_ATTENDEE = 8
        const val COL_STATUS = 9
    }
}
