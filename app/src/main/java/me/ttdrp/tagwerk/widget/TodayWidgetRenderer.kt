package me.ttdrp.tagwerk.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import me.ttdrp.tagwerk.MainActivity
import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.alarm.FocusAlarmScheduler
import me.ttdrp.tagwerk.alarm.TaskReminderScheduler
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.FocusClock
import me.ttdrp.tagwerk.model.FocusSession
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.TodayWidget
import me.ttdrp.tagwerk.model.shownEmoji
import me.ttdrp.tagwerk.ui.formatClock
import me.ttdrp.tagwerk.ui.formatMinutes
import me.ttdrp.tagwerk.ui.tintColor
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Baut beide Widgets aus dem aktuellen Zustand. Läuft nach jeder Änderung am Speicher. */
object TodayWidgetRenderer {
    const val ACTION_TOGGLE_STEP = "me.ttdrp.tagwerk.widget.TOGGLE_STEP"
    const val ACTION_REFRESH = "me.ttdrp.tagwerk.widget.REFRESH"
    const val ACTION_EXTEND = "me.ttdrp.tagwerk.widget.EXTEND"
    const val ACTION_TOGGLE_PAUSE = "me.ttdrp.tagwerk.widget.TOGGLE_PAUSE"
    const val ACTION_FINISH = "me.ttdrp.tagwerk.widget.FINISH"
    const val EXTRA_TASK_ID = "me.ttdrp.tagwerk.widget.TASK_ID"
    const val EXTRA_STEP_ID = "me.ttdrp.tagwerk.widget.STEP_ID"

    private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private const val NOTHING_LEFT_EMOJI = "🌿"
    private const val NOTHING_LEFT_TINT = 2
    private const val TITLE_SIZE_SP = 18f
    private const val DONE_STEP_COLOR = 0x80FFFFFF.toInt()
    private const val TITLE_MAX_WIDTH_DP = 160f
    private const val RING_SIZE_DP = 84f
    private const val RING_STROKE_DP = 10f
    private const val RING_TRACK_COLOR = 0xFF2E2842.toInt()
    private const val RING_COLOR = 0xFFA98AF7.toInt()

    private class StepRow(val root: Int, val circle: Int, val emoji: Int, val title: Int, val check: Int)

    private val STEP_ROWS = listOf(
        StepRow(R.id.widget_step1, R.id.widget_step1_circle, R.id.widget_step1_emoji, R.id.widget_step1_title, R.id.widget_step1_check),
        StepRow(R.id.widget_step2, R.id.widget_step2_circle, R.id.widget_step2_emoji, R.id.widget_step2_title, R.id.widget_step2_check),
        StepRow(R.id.widget_step3, R.id.widget_step3_circle, R.id.widget_step3_emoji, R.id.widget_step3_title, R.id.widget_step3_check),
    )

    fun updateAll(context: Context, state: AppState) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val small = manager.getAppWidgetIds(ComponentName(app, TodayWidgetProvider::class.java))
        val wide = manager.getAppWidgetIds(ComponentName(app, TodayWidgetWideProvider::class.java))
        val zone = ZoneId.systemDefault()
        val current = TodayWidget.current(state, LocalDate.now(zone), zone)
        scheduleRefresh(app, active = small.isNotEmpty() || wide.isNotEmpty(), zone, current?.focus)
        if (small.isNotEmpty()) manager.updateAppWidget(small, build(app, R.layout.widget_today_small, current, wide = false))
        if (wide.isNotEmpty()) manager.updateAppWidget(wide, build(app, R.layout.widget_today_wide, current, wide = true))
    }

    private fun build(context: Context, layout: Int, current: TodayWidget.Current?, wide: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(R.id.widget_root, if (current?.running == true) openFocus(context) else openApp(context))
        if (current == null) {
            showNothingLeft(context, views, wide)
        } else {
            showTask(context, views, current)
            if (wide) showSteps(context, views, current.task)
        }
        return views
    }

    private fun showNothingLeft(context: Context, views: RemoteViews, wide: Boolean) {
        views.setInt(R.id.widget_circle, "setColorFilter", tintColor(NOTHING_LEFT_TINT).toArgb())
        views.setTextViewText(R.id.widget_emoji, NOTHING_LEFT_EMOJI)
        views.setViewVisibility(R.id.widget_meta, View.GONE)
        showTitle(context, views, "Alles geschafft")
        views.setViewVisibility(R.id.widget_start, View.GONE)
        if (wide) views.setViewVisibility(R.id.widget_steps, View.GONE)
    }

    private fun showTask(context: Context, views: RemoteViews, current: TodayWidget.Current) {
        val task = current.task
        val color = task.color?.let { it or 0xFF000000.toInt() } ?: tintColor(task.tint).toArgb()
        views.setTextViewText(R.id.widget_emoji, task.emoji)
        val session = current.focus
        if (session == null) {
            views.setInt(R.id.widget_circle, "setColorFilter", color)
            views.setViewVisibility(R.id.widget_meta, View.VISIBLE)
            views.setTextViewText(R.id.widget_meta, meta(task))
            showTitle(context, views, task.title)
            views.setTextViewText(R.id.widget_start, "Start ▶︎")
            views.setOnClickPendingIntent(R.id.widget_start, startFocus(context, task.id))
        } else {
            views.setInt(R.id.widget_circle_running, "setColorFilter", color)
            showTimer(context, views, task, session)
        }
    }

    private fun showTimer(context: Context, views: RemoteViews, task: Task, session: FocusSession) {
        val now = System.currentTimeMillis()
        val paused = session.pausedAtMs != null
        views.setViewVisibility(R.id.widget_circle, View.GONE)
        views.setViewVisibility(R.id.widget_circle_running, View.VISIBLE)
        views.setViewVisibility(R.id.widget_ring, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_ring, ringBitmap(context, FocusClock.progress(session, now)))
        views.setViewVisibility(R.id.widget_title_image, View.GONE)
        views.setViewVisibility(R.id.widget_start, View.GONE)
        views.setViewVisibility(R.id.widget_meta, View.GONE)
        views.setViewVisibility(R.id.widget_running_title, View.VISIBLE)
        views.setTextViewText(R.id.widget_running_title, task.title)
        val remaining = FocusClock.remainingMs(session, now)
        if (paused) {
            // Ein angehaltener Chronometer liefe beim nächsten Neuaufbau im Launcher von selbst weg.
            views.setViewVisibility(R.id.widget_timer_paused, View.VISIBLE)
            views.setTextViewText(R.id.widget_timer_paused, FocusClock.format(remaining))
        } else {
            // Der Launcher zählt selbst, das Widget braucht für die Sekunden keine Updates.
            views.setViewVisibility(R.id.widget_timer, View.VISIBLE)
            views.setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() + remaining, if (remaining < 0) "+%s" else null, true)
            views.setChronometerCountDown(R.id.widget_timer, remaining >= 0)
        }
        views.setViewVisibility(R.id.widget_controls, View.VISIBLE)
        views.setImageViewResource(R.id.widget_pause, if (paused) R.drawable.widget_play else R.drawable.widget_pause)
        views.setContentDescription(R.id.widget_pause, if (paused) "Weiter" else "Pause")
        views.setOnClickPendingIntent(R.id.widget_extend, timerAction(context, ACTION_EXTEND))
        views.setOnClickPendingIntent(R.id.widget_pause, timerAction(context, ACTION_TOGGLE_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_finish, timerAction(context, ACTION_FINISH))
    }

    private fun ringBitmap(context: Context, progress: Float): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (RING_SIZE_DP * density).toInt()
        val stroke = RING_STROKE_DP * density
        val inset = stroke / 2
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = center - inset
        paint.color = RING_TRACK_COLOR
        canvas.drawCircle(center, center, radius, paint)
        paint.color = RING_COLOR
        canvas.drawArc(RectF(inset, inset, size - inset, size - inset), -90f, 360f * progress, false, paint)
        paint.style = Paint.Style.FILL
        val angle = progress * 2f * PI.toFloat() - PI.toFloat() / 2f
        canvas.drawCircle(center + radius * cos(angle), center + radius * sin(angle), stroke / 2, paint)
        return bitmap
    }

    /** Widgets dürfen keine eigenen Schriften laden, deshalb kommt der Titel in Lora als Bild. */
    private fun showTitle(context: Context, views: RemoteViews, title: String) {
        views.setViewVisibility(R.id.widget_title, View.GONE)
        views.setViewVisibility(R.id.widget_title_image, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_title_image, titleBitmap(context, title))
        views.setContentDescription(R.id.widget_title_image, title)
    }

    private fun titleBitmap(context: Context, title: String): Bitmap {
        val metrics = context.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = context.resources.getFont(R.font.lora_regular)
            color = Color.WHITE
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_SIZE_SP, metrics)
        }
        val shown = TextUtils.ellipsize(title, paint, TITLE_MAX_WIDTH_DP * metrics.density, TextUtils.TruncateAt.END).toString()
        val font = paint.fontMetrics
        val bitmap = Bitmap.createBitmap(
            ceil(paint.measureText(shown)).toInt().coerceAtLeast(1),
            ceil(font.descent - font.ascent).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(bitmap).drawText(shown, 0f, -font.ascent, paint)
        return bitmap
    }

    private fun meta(task: Task): String =
        (task.startMinute?.let(::formatClock) ?: task.daypart.label) + " · " + formatMinutes(task.effectiveMinutes)

    private fun showSteps(context: Context, views: RemoteViews, task: Task) {
        views.setViewVisibility(R.id.widget_steps, View.VISIBLE)
        val steps = TodayWidget.visibleSteps(task.subtasks)
        STEP_ROWS.forEachIndexed { index, row ->
            val step = steps.getOrNull(index)
            views.setViewVisibility(row.root, if (step == null) View.GONE else View.VISIBLE)
            if (step != null) showStep(context, views, row, task.id, step)
        }
        views.setOnClickPendingIntent(R.id.widget_add_step, openTask(context, task.id))
    }

    private fun showStep(context: Context, views: RemoteViews, row: StepRow, taskId: String, step: Subtask) {
        views.setInt(row.circle, "setColorFilter", step.color?.let { it or 0xFF000000.toInt() } ?: tintColor(EmojiGuess.tintFor(step.title)).toArgb())
        views.setTextViewText(row.emoji, step.shownEmoji)
        views.setTextViewText(row.title, if (step.done) struckThrough(step.title) else step.title)
        views.setTextColor(row.title, if (step.done) DONE_STEP_COLOR else Color.WHITE)
        views.setImageViewResource(row.check, if (step.done) R.drawable.widget_check_done else R.drawable.widget_check_open)
        views.setOnClickPendingIntent(row.check, toggleStep(context, taskId, step.id))
    }

    private fun struckThrough(text: String) = SpannableString(text).apply {
        setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun scheduleRefresh(context: Context, active: Boolean, zone: ZoneId, focus: FocusSession?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TodayWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PENDING_FLAGS,
        )
        if (active) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, TodayWidget.nextRefreshAtMs(System.currentTimeMillis(), zone, focus), pending)
        } else {
            alarms.cancel(pending)
        }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PENDING_FLAGS,
    )

    private fun openFocus(context: Context) = activity(context, "focus") { it.putExtra(FocusAlarmScheduler.EXTRA_OPEN_FOCUS, true) }

    private fun startFocus(context: Context, taskId: String) =
        activity(context, "start/${Uri.encode(taskId)}") { it.putExtra(TaskReminderScheduler.EXTRA_START_FOCUS, taskId) }

    private fun openTask(context: Context, taskId: String) =
        activity(context, "task/${Uri.encode(taskId)}") { it.putExtra(TaskReminderScheduler.EXTRA_OPEN_TASK, taskId) }

    /** Die Uri macht die Intents je Aufgabe und Zweck verschieden, sonst teilten sie sich einen PendingIntent. */
    private fun activity(context: Context, path: String, extras: (Intent) -> Unit) = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("tagwerk://widget/$path"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .also(extras),
        PENDING_FLAGS,
    )

    private fun timerAction(context: Context, action: String) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TodayWidgetProvider::class.java).setAction(action),
        PENDING_FLAGS,
    )

    private fun toggleStep(context: Context, taskId: String, stepId: String) = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TodayWidgetProvider::class.java)
            .setAction(ACTION_TOGGLE_STEP)
            .setData(Uri.parse("tagwerk://widget/toggle/${Uri.encode(taskId)}/${Uri.encode(stepId)}"))
            .putExtra(EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_STEP_ID, stepId),
        PENDING_FLAGS,
    )
}
