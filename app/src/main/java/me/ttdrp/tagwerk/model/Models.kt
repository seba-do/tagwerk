package me.ttdrp.tagwerk.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Kind { PLAN, TODO }

@Serializable
enum class Daypart(val label: String) {
    ANYTIME("Jederzeit"),
    MORNING("Morgen"),
    AFTERNOON("Nachmittag"),
    EVENING("Abend"),
}

@Serializable
enum class Priority(val label: String) { HIGH("Wichtig"), MEDIUM("Normal"), LOW("Kann warten") }

@Serializable
enum class Quadrant(val label: String) {
    NOW("Sofort erledigen"),
    SCHEDULE("Fest einplanen"),
    DELEGATE("Abgeben"),
    DROP("Streichen"),
}

fun newId(): String = UUID.randomUUID().toString()

@Serializable
data class Subtask(
    val id: String = newId(),
    val title: String,
    val minutes: Int? = null,
    val done: Boolean = false,
    val emoji: String? = null,
    val color: Int? = null,
)

@Serializable
data class Task(
    val id: String = newId(),
    val title: String,
    val emoji: String = "✅",
    val tint: Int = 0,
    val kind: Kind = Kind.PLAN,
    val listId: String? = null,
    val epochDay: Long? = null,
    val startMinute: Int? = null,
    val daypart: Daypart = Daypart.ANYTIME,
    val minutes: Int = 30,
    val priority: Priority = Priority.MEDIUM,
    val quadrant: Quadrant = Quadrant.SCHEDULE,
    val subtasks: List<Subtask> = emptyList(),
    val doneAtMs: Long? = null,
    val notes: String = "",
    val color: Int? = null,
    val repeat: Repeat? = null,
    val seriesId: String? = null,
    val exceptions: List<Long> = emptyList(),
) {
    val isDone: Boolean get() = doneAtMs != null

    /** Zeitgesteuerte Teilaufgaben bestimmen die Dauer, sonst gilt der eigene Wert. */
    val effectiveMinutes: Int
        get() = subtasks.sumOf { it.minutes ?: 0 }.takeIf { it > 0 } ?: minutes
}

@Serializable
data class TodoList(val id: String = newId(), val name: String)

@Serializable
data class FocusSession(
    val taskId: String,
    val startedAtMs: Long,
    val plannedMs: Long,
    val pausedAtMs: Long? = null,
    val pausedTotalMs: Long = 0,
)

@Serializable
data class AppState(
    val tasks: List<Task> = emptyList(),
    val lists: List<TodoList> = emptyList(),
    val focus: FocusSession? = null,
    val seeded: Boolean = false,
)
