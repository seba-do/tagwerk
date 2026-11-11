package me.ttdrp.tagwerk.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import me.ttdrp.tagwerk.MainActivity
import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.AlarmText
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.FocusAlarms
import me.ttdrp.tagwerk.model.FocusClock

/** Es gibt immer höchstens einen geplanten Alarm: den nächsten. Der Receiver plant danach den folgenden. */
class FocusAlarmScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Nach jeder Änderung am Timer: stellt den Alarm neu und räumt eine überholte Meldung weg. */
    fun sync(state: AppState, nowMs: Long) {
        scheduleNext(state, nowMs)
        val session = state.focus
        if (session == null || FocusClock.remainingMs(session, nowMs) > 0) {
            notifications.cancel(NOTIFICATION_ID)
        }
    }

    fun scheduleNext(state: AppState, nowMs: Long) {
        val session = state.focus
        val subtasks = state.tasks.firstOrNull { it.id == session?.taskId }?.subtasks.orEmpty()
        val atMs = session?.let { FocusAlarms.nextAtMs(it, subtasks, nowMs) }
        val pending = alarmIntent()
        when {
            atMs == null -> alarms.cancel(pending)
            canScheduleExact() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
            // Exakte Alarme können in den Systemeinstellungen entzogen sein: lieber ungenau als gar nicht.
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }
    }

    fun show(message: AlarmText) {
        notifications.createNotificationChannel(channel())
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_FOCUS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(TaskReminderScheduler.NOTIFICATION_COLOR)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PENDING_FLAGS))
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }

    private fun canScheduleExact() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun alarmIntent() = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, FocusAlarmReceiver::class.java),
        PENDING_FLAGS,
    )

    /** Der Alarm-Stream ist auch bei stummgeschaltetem Klingelton hörbar. Den Ton gibt das System vor. */
    private fun channel() = NotificationChannel(CHANNEL_ID, "Fokus-Timer", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Meldet das Ende des Timers und den Wechsel zum nächsten Schritt"
        enableVibration(true)
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
    }

    companion object {
        const val EXTRA_OPEN_FOCUS = "me.ttdrp.tagwerk.OPEN_FOCUS"
        private const val CHANNEL_ID = "focus_timer"
        private const val NOTIFICATION_ID = 1
        private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
