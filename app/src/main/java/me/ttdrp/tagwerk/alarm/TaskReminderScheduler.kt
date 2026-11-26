package me.ttdrp.tagwerk.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import me.ttdrp.tagwerk.MainActivity
import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.ReminderSettings
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TaskReminder
import me.ttdrp.tagwerk.model.TaskReminders
import java.time.ZoneId

/**
 * Wie beim Fokus-Timer gibt es immer höchstens einen geplanten Alarm: den nächsten. Beim Auslösen entscheidet
 * der aktuelle Zustand, was fällig ist. Gelöschte oder verschobene Aufgaben brauchen so keine eigene Aufräumarbeit.
 */
class TaskReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun settings() = ReminderSettings(
        before = prefs.getBoolean(KEY_BEFORE, true),
        atStart = prefs.getBoolean(KEY_START, true),
    )

    fun saveSettings(value: ReminderSettings) {
        prefs.edit().putBoolean(KEY_BEFORE, value.before).putBoolean(KEY_START, value.atStart).apply()
    }

    /**
     * Für jeden Anlass derselbe Weg: Alarm, Neustart des Geräts oder geänderte Aufgabe.
     * Was seit dem letzten geplanten Zeitpunkt fällig wurde, wird gemeldet, danach kommt der nächste an die Reihe.
     */
    fun sync(state: AppState, nowMs: Long) {
        val zone = ZoneId.systemDefault()
        val settings = settings()
        clearObsolete(state, zone, nowMs)

        val plannedAt = prefs.getLong(KEY_PLANNED_AT, NOTHING_PLANNED)
        if (plannedAt != NOTHING_PLANNED && plannedAt <= nowMs) {
            TaskReminders.due(state, settings, zone, plannedAt, nowMs).forEach { reminder ->
                Recurrence.find(state.tasks, reminder.taskId)?.let { show(reminder, it, zone, nowMs) }
            }
        }

        val next = TaskReminders.nextAtMs(state, settings, zone, nowMs)
        val pending = alarmIntent()
        if (next == null) {
            prefs.edit().remove(KEY_PLANNED_AT).apply()
            alarms.cancel(pending)
        } else {
            prefs.edit().putLong(KEY_PLANNED_AT, next).apply()
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                // Exakte Alarme können in den Systemeinstellungen entzogen sein: lieber ungenau als gar nicht.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            }
        }
    }

    private fun clearObsolete(state: AppState, zone: ZoneId, nowMs: Long) {
        notifications.activeNotifications
            .filter { it.id == NOTIFICATION_ID && it.tag != null }
            .filterNot { TaskReminders.stillRelevant(state, it.tag, zone, nowMs) }
            .forEach { notifications.cancel(it.tag, it.id) }
    }

    private fun show(reminder: TaskReminder, task: Task, zone: ZoneId, nowMs: Long) {
        notifications.createNotificationChannel(channel())
        val message = TaskReminders.message(reminder, task, zone, nowMs)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NOTIFICATION_COLOR)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(activityIntent(task.id, EXTRA_OPEN_TASK))
            .addAction(action(context.getString(R.string.action_start_focus), activityIntent(task.id, EXTRA_START_FOCUS)))
            .addAction(action(context.getString(R.string.action_done), doneIntent(task.id)))
            .build()
        // Pro Aufgabe eine Meldung: Die Startmeldung ersetzt die Vorwarnung.
        notifications.notify(task.id, NOTIFICATION_ID, notification)
    }

    private fun action(label: String, intent: PendingIntent) =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), label, intent).build()

    /** Die Uri macht die Intents je Aufgabe und Zweck verschieden, sonst teilten sie sich einen PendingIntent. */
    private fun activityIntent(taskId: String, extra: String) = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setData(Uri.fromParts("tagwerk", taskId, extra))
            .putExtra(extra, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PENDING_FLAGS,
    )

    private fun doneIntent(taskId: String) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TaskReminderReceiver::class.java)
            .setAction(ACTION_DONE)
            .setData(Uri.fromParts("tagwerk", taskId, ACTION_DONE))
            .putExtra(EXTRA_TASK_ID, taskId),
        PENDING_FLAGS,
    )

    private fun alarmIntent() = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TaskReminderReceiver::class.java),
        PENDING_FLAGS,
    )

    private fun canScheduleExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun channel() = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_HIGH).apply {
        description = context.getString(R.string.channel_tasks_description)
        enableVibration(true)
    }

    companion object {
        const val ACTION_DONE = "me.ttdrp.tagwerk.REMINDER_DONE"
        const val EXTRA_TASK_ID = "me.ttdrp.tagwerk.TASK_ID"
        const val EXTRA_OPEN_TASK = "me.ttdrp.tagwerk.OPEN_TASK"
        const val EXTRA_START_FOCUS = "me.ttdrp.tagwerk.START_FOCUS"
        const val NOTIFICATION_COLOR = 0xFFAF8DFF.toInt()
        private const val CHANNEL_ID = "task_reminders"
        private const val NOTIFICATION_ID = 2
        private const val KEY_BEFORE = "reminder_before"
        private const val KEY_START = "reminder_start"
        private const val KEY_PLANNED_AT = "reminder_planned_at"
        private const val NOTHING_PLANNED = -1L
        private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
