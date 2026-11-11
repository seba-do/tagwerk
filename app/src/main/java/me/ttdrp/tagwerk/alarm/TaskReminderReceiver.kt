package me.ttdrp.tagwerk.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.ttdrp.tagwerk.data.Store
import me.ttdrp.tagwerk.model.Recurrence

/**
 * Läuft auch bei geschlossener App: der geplante Alarm, "Erledigt" in der Meldung, ein Neustart des Geräts,
 * ein App-Update oder eine geänderte Uhrzeit. Bei allen genügt es, den Zustand neu abzugleichen.
 */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val store = Store.shared(app)
        val now = System.currentTimeMillis()
        if (intent.action == TaskReminderScheduler.ACTION_DONE) {
            val id = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_ID)
            store.update { s ->
                val task = id?.let { Recurrence.find(s.tasks, it) }?.takeUnless { it.isDone }
                if (task == null) s else s.copy(tasks = Recurrence.upsert(s.tasks, task.copy(doneAtMs = now)))
            }
        }
        TaskReminderScheduler(app).sync(store.state.value, now)
    }
}
