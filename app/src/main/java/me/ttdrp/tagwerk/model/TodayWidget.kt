package me.ttdrp.tagwerk.model

import java.time.Instant
import java.time.ZoneId

/** Was das Homescreen-Widget zeigt. Ohne Android, damit es sich testen lässt. */
object TodayWidget {
    const val MAX_STEPS = 3
    private const val RING_TICK_MS = 60_000L

    data class Current(val task: Task, val focus: FocusSession?) {
        val running: Boolean get() = focus != null
    }

    /** Ein laufender Timer geht vor, sonst die erste offene Aufgabe in der Reihenfolge des Plans. */
    fun current(state: AppState, today: java.time.LocalDate, zone: ZoneId): Current? {
        state.focus?.let { session ->
            Recurrence.find(state.tasks, session.taskId)
                ?.takeUnless { it.isDone }
                ?.let { return Current(it, session) }
        }
        val open = Recurrence.forDay(state.tasks, today).filter { it.kind == Kind.PLAN && !it.isDone }
        return groupDayEntries(open, emptyList(), today, zone)
            .flatMap { it.entries }
            .filterIsInstance<TaskEntry>()
            .firstOrNull()
            ?.let { Current(it.task, focus = null) }
    }

    /** Passt alles hinein, bleibt die Reihenfolge. Sonst gehen die offenen Schritte vor. */
    fun visibleSteps(steps: List<Subtask>): List<Subtask> =
        if (steps.size <= MAX_STEPS) steps else (steps.filterNot { it.done } + steps.filter { it.done }).take(MAX_STEPS)

    fun toggleStep(tasks: List<Task>, taskId: String, stepId: String): List<Task> {
        val task = Recurrence.find(tasks, taskId) ?: return tasks
        if (task.subtasks.none { it.id == stepId }) return tasks
        return Recurrence.upsert(
            tasks,
            task.copy(subtasks = task.subtasks.map { if (it.id == stepId) it.copy(done = !it.done) else it }),
        )
    }

    fun togglePause(session: FocusSession, nowMs: Long): FocusSession =
        if (session.pausedAtMs != null) FocusClock.resume(session, nowMs) else FocusClock.pause(session, nowMs)

    /**
     * Der Countdown läuft im Launcher von selbst, der Ring ist aber ein Bild und braucht einen Takt.
     * Am Ende des Timers wird einmal neu aufgebaut, weil die Anzeige dort aufs Überziehen umspringt.
     */
    fun nextRefreshAtMs(nowMs: Long, zone: ZoneId, focus: FocusSession? = null): Long {
        // Eine Sekunde Abstand, damit der neue Tag sicher begonnen hat.
        val newDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 1_000
        val remaining = focus?.takeIf { it.pausedAtMs == null }?.let { FocusClock.remainingMs(it, nowMs) } ?: return newDay
        if (remaining <= 0) return newDay
        return minOf(newDay, nowMs + RING_TICK_MS, nowMs + remaining + 1_000)
    }
}
