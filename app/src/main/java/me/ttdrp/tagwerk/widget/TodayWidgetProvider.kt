package me.ttdrp.tagwerk.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import me.ttdrp.tagwerk.alarm.FocusAlarmScheduler
import me.ttdrp.tagwerk.alarm.TaskReminderScheduler
import me.ttdrp.tagwerk.data.Store
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.TodayWidget

/** Das kleine Widget. Abhaken und der Wechsel des Tages laufen auch bei geschlossener App hier an. */
open class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onDisabled(context: Context) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            TodayWidgetRenderer.ACTION_TOGGLE_STEP -> toggleStep(context, intent)
            TodayWidgetRenderer.ACTION_EXTEND -> changeTimer(context) { s, _ -> s.copy(focus = s.focus?.let { FocusClock.extend(it, EXTEND_MINUTES) }) }
            TodayWidgetRenderer.ACTION_TOGGLE_PAUSE -> changeTimer(context) { s, now -> s.copy(focus = s.focus?.let { TodayWidget.togglePause(it, now) }) }
            TodayWidgetRenderer.ACTION_FINISH -> changeTimer(context) { s, now -> FocusClock.finish(s, now) }
            TodayWidgetRenderer.ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> refresh(context)
        }
    }

    private fun refresh(context: Context) = TodayWidgetRenderer.updateAll(context, Store.shared(context).state.value)

    /** Die App läuft womöglich gerade nicht, also stellt das Widget die Alarme selbst nach. */
    private fun changeTimer(context: Context, change: (AppState, Long) -> AppState) {
        val app = context.applicationContext
        val store = Store.shared(app)
        val now = System.currentTimeMillis()
        store.update { change(it, now) }
        FocusAlarmScheduler(app).sync(store.state.value, now)
        TaskReminderScheduler(app).sync(store.state.value, now)
    }

    private fun toggleStep(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TodayWidgetRenderer.EXTRA_TASK_ID) ?: return
        val stepId = intent.getStringExtra(TodayWidgetRenderer.EXTRA_STEP_ID) ?: return
        val store = Store.shared(context)
        store.update { it.copy(tasks = TodayWidget.toggleStep(it.tasks, taskId, stepId)) }
        // Der Alarm je Schritt hängt an den Schritten, und die App läuft womöglich gerade nicht.
        FocusAlarmScheduler(context.applicationContext).sync(store.state.value, System.currentTimeMillis())
    }
}

private const val EXTEND_MINUTES = 5

/** Das breite Widget mit den Schritten der Aufgabe. */
class TodayWidgetWideProvider : TodayWidgetProvider()
