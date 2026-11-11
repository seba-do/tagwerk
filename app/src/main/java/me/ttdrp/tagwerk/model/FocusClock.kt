package me.ttdrp.tagwerk.model

/**
 * Der Timer zählt nie selbst mit: Der Stand wird immer aus Zeitstempeln berechnet.
 * So überlebt er Neustarts der App, und alle Anzeigen sehen denselben Wert.
 */
object FocusClock {
    private const val MINUTE_MS = 60_000L

    fun start(taskId: String, minutes: Int, nowMs: Long) =
        FocusSession(taskId = taskId, startedAtMs = nowMs, plannedMs = minutes * MINUTE_MS)

    fun elapsedMs(s: FocusSession, nowMs: Long): Long =
        (s.pausedAtMs ?: nowMs) - s.startedAtMs - s.pausedTotalMs

    /** Kann negativ werden: Dann läuft die Aufgabe über die geplante Zeit hinaus. */
    fun remainingMs(s: FocusSession, nowMs: Long): Long = s.plannedMs - elapsedMs(s, nowMs)

    fun progress(s: FocusSession, nowMs: Long): Float =
        if (s.plannedMs <= 0) 1f else (elapsedMs(s, nowMs).toFloat() / s.plannedMs).coerceIn(0f, 1f)

    fun pause(s: FocusSession, nowMs: Long): FocusSession =
        if (s.pausedAtMs != null) s else s.copy(pausedAtMs = nowMs)

    fun resume(s: FocusSession, nowMs: Long): FocusSession {
        val pausedAt = s.pausedAtMs ?: return s
        return s.copy(pausedAtMs = null, pausedTotalMs = s.pausedTotalMs + (nowMs - pausedAt))
    }

    fun extend(s: FocusSession, minutes: Int): FocusSession =
        s.copy(plannedMs = s.plannedMs + minutes * MINUTE_MS)

    fun finish(state: AppState, nowMs: Long): AppState {
        val id = state.focus?.taskId ?: return state
        return state.copy(focus = null, tasks = state.tasks.map { if (it.id == id) it.copy(doneAtMs = nowMs) else it })
    }

    /** Index der gerade laufenden Teilaufgabe und ihre Restzeit, oder null ohne Teil-Timer. */
    fun currentStage(subtasks: List<Subtask>, elapsedMs: Long): Stage? {
        var boundary = 0L
        subtasks.forEachIndexed { index, sub ->
            val minutes = sub.minutes ?: return@forEachIndexed
            boundary += minutes * MINUTE_MS
            if (elapsedMs < boundary) return Stage(index, boundary - elapsedMs)
        }
        return null
    }

    data class Stage(val index: Int, val remainingMs: Long)

    fun format(ms: Long): String {
        val total = kotlin.math.abs(ms) / 1000
        val text = "%02d:%02d".format(total / 60, total % 60)
        return if (ms < 0) "+$text" else text
    }
}
