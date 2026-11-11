package me.ttdrp.tagwerk.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class ReminderKind { BEFORE, START }

data class ReminderSettings(val before: Boolean = true, val atStart: Boolean = true)

data class TaskReminder(val taskId: String, val kind: ReminderKind, val atMs: Long)

/**
 * Wann geplante Aufgaben sich melden und was sie dann sagen. Es wird nichts gespeichert außer der Aufgabe selbst:
 * Alle Zeitpunkte ergeben sich aus Tag und Uhrzeit, Änderungen an der Aufgabe wirken also sofort.
 */
object TaskReminders {
    const val BEFORE_MINUTES = 10
    private const val MINUTE_MS = 60_000L
    private const val BEFORE_MS = BEFORE_MINUTES * MINUTE_MS
    private const val MISSED_LIMIT_MS = 60 * MINUTE_MS
    private const val HORIZON_DAYS = 366L

    /** Startzeit einer offenen, fest eingeplanten Aufgabe. Grobe Tageszeiten haben keine. */
    fun startMs(task: Task, zone: ZoneId): Long? {
        if (task.kind != Kind.PLAN || task.isDone || task.repeat != null) return null
        val day = task.epochDay ?: return null
        val minute = task.startMinute ?: return null
        return LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
    }

    fun nextAtMs(state: AppState, settings: ReminderSettings, zone: ZoneId, nowMs: Long): Long? =
        all(state, settings, zone, nowMs).map { it.atMs }.filter { it > nowMs }.minOrNull()

    /** Was seit [sinceMs] fällig wurde. Nach einer Verspätung kommt pro Aufgabe nur die letzte Meldung. */
    fun due(state: AppState, settings: ReminderSettings, zone: ZoneId, sinceMs: Long, nowMs: Long): List<TaskReminder> =
        all(state, settings, zone, nowMs)
            .filter { it.atMs in sinceMs..nowMs && it.taskId != state.focus?.taskId && isFresh(it, nowMs) }
            .groupBy { it.taskId }
            .map { (_, reminders) -> reminders.maxBy { it.atMs } }

    /** Darf eine schon sichtbare Meldung stehen bleiben, oder wurde die Aufgabe inzwischen geändert? */
    fun stillRelevant(state: AppState, taskId: String, zone: ZoneId, nowMs: Long): Boolean {
        val task = Recurrence.find(state.tasks, taskId) ?: return false
        if (state.focus?.taskId == taskId) return false
        val start = startMs(task, zone) ?: return false
        return start - BEFORE_MS <= nowMs
    }

    fun message(reminder: TaskReminder, task: Task, zone: ZoneId, nowMs: Long): AlarmText {
        val title = "${task.emoji} ${task.title}"
        val start = startMs(task, zone) ?: reminder.atMs
        return when (reminder.kind) {
            ReminderKind.BEFORE -> {
                val minutes = ((start - nowMs + MINUTE_MS - 1) / MINUTE_MS).coerceAtLeast(1)
                val span = if (minutes == 1L) "1 Minute" else "$minutes Minuten"
                AlarmText(title, "Startet in $span, um ${clock(start, zone)}.")
            }
            ReminderKind.START -> {
                val end = start + task.effectiveMinutes * MINUTE_MS
                AlarmText(title, "Geht jetzt los. Geplant bis ${clock(end, zone)}.")
            }
        }
    }

    private fun all(state: AppState, settings: ReminderSettings, zone: ZoneId, nowMs: Long): List<TaskReminder> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        // Ein Jahr voraus reicht: Auch eine jährliche Serie kommt darin vor, und jeder Alarm plant den nächsten.
        return Recurrence.expand(state.tasks, today.minusDays(1), today.plusDays(HORIZON_DAYS)).flatMap { task ->
            val start = startMs(task, zone) ?: return@flatMap emptyList()
            buildList {
                if (settings.before) add(TaskReminder(task.id, ReminderKind.BEFORE, start - BEFORE_MS))
                if (settings.atStart) add(TaskReminder(task.id, ReminderKind.START, start))
            }
        }
    }

    /** Eine Vorwarnung nach dem Start wäre falsch, eine sehr alte Startmeldung nur noch störend. */
    private fun isFresh(reminder: TaskReminder, nowMs: Long): Boolean = when (reminder.kind) {
        ReminderKind.BEFORE -> nowMs < reminder.atMs + BEFORE_MS
        ReminderKind.START -> nowMs - reminder.atMs <= MISSED_LIMIT_MS
    }

    private fun clock(ms: Long, zone: ZoneId): String = clockText(ms, zone)
}

internal fun clockText(ms: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
    return String.format(Locale.GERMANY, "%02d:%02d", time.hour, time.minute)
}
