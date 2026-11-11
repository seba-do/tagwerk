package me.ttdrp.tagwerk.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.ttdrp.tagwerk.data.Store
import me.ttdrp.tagwerk.model.FocusAlarms

/**
 * Läuft auch bei geschlossener App. Liest den gespeicherten Zustand und schreibt nichts:
 * Ein Alarm ist nur der Anlass, ob er noch gilt, entscheidet der gespeicherte Timer.
 * Der Alarm selbst kommt ohne Action; mit Action kommt der Neustart des Geräts oder ein App-Update,
 * nach denen das System geplante Alarme vergessen haben kann.
 */
class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = Store.shared(context).state.value
        val scheduler = FocusAlarmScheduler(context.applicationContext)
        val now = System.currentTimeMillis()
        val session = state.focus
        val task = state.tasks.firstOrNull { it.id == session?.taskId }
        if (session != null && task != null && (intent.action == null || FocusAlarms.missedFinish(session, now))) {
            FocusAlarms.message(session, task, now)?.let(scheduler::show)
        }
        scheduler.scheduleNext(state, now)
    }
}
