package me.ttdrp.tagwerk.model

data class AlarmText(val title: String, val text: String)

/** Wann der Timer sich meldet und was er dann sagt. Wie der Timer selbst nur aus Zeitstempeln berechnet. */
object FocusAlarms {
    private const val MINUTE_MS = 60_000L
    private const val MISSED_LIMIT_MS = 60 * MINUTE_MS

    /** Nächster Zeitpunkt, an dem sich der Timer meldet, oder null, wenn pausiert oder nichts mehr aussteht. */
    fun nextAtMs(s: FocusSession, subtasks: List<Subtask>, nowMs: Long): Long? {
        if (s.pausedAtMs != null) return null
        val elapsed = FocusClock.elapsedMs(s, nowMs)
        val offset = offsets(s, subtasks).firstOrNull { it > elapsed } ?: return null
        return s.startedAtMs + s.pausedTotalMs + offset
    }

    /** Was ein ausgelöster Alarm meldet. Null, wenn er überholt ist: Dann bleibt er stumm. */
    fun message(s: FocusSession, task: Task, nowMs: Long): AlarmText? {
        if (s.pausedAtMs != null) return null
        val elapsed = FocusClock.elapsedMs(s, nowMs)
        if (elapsed >= s.plannedMs) return AlarmText(task.title, "Die geplante Zeit ist um.")
        if (offsets(s, task.subtasks).none { it <= elapsed }) return null
        val stage = FocusClock.currentStage(task.subtasks, elapsed)
        val text = if (stage == null) {
            "Alle Schritte sind durch. Die Zusatzzeit läuft noch."
        } else {
            "Weiter mit: ${task.subtasks[stage.index].title}"
        }
        return AlarmText(task.title, text)
    }

    /** Nach einem Neustart des Geräts: Ist das Ende gerade erst verpasst worden? Alte Timer klingeln nicht mehr. */
    fun missedFinish(s: FocusSession, nowMs: Long): Boolean {
        if (s.pausedAtMs != null) return false
        val overtime = FocusClock.elapsedMs(s, nowMs) - s.plannedMs
        return overtime in 0 until MISSED_LIMIT_MS
    }

    private fun offsets(s: FocusSession, subtasks: List<Subtask>): List<Long> {
        var boundary = 0L
        val stepEnds = subtasks.mapNotNull { sub ->
            sub.minutes?.let {
                boundary += it * MINUTE_MS
                boundary
            }
        }
        return (stepEnds.filter { it < s.plannedMs } + s.plannedMs).distinct()
    }
}
