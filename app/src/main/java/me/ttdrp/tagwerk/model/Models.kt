package me.ttdrp.tagwerk.model

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import me.ttdrp.tagwerk.R
import java.util.UUID

@Serializable
enum class Kind { PLAN, TODO }

@Serializable
enum class Daypart(@StringRes private val labelRes: Int) {
    ANYTIME(R.string.daypart_anytime),
    MORNING(R.string.daypart_morning),
    AFTERNOON(R.string.daypart_afternoon),
    EVENING(R.string.daypart_evening),
    ;

    val label: String get() = str(labelRes)
}

@Serializable
enum class Priority(@StringRes private val labelRes: Int) {
    HIGH(R.string.priority_high),
    MEDIUM(R.string.priority_medium),
    LOW(R.string.priority_low),
    ;

    val label: String get() = str(labelRes)
}

@Serializable
enum class Quadrant(@StringRes private val labelRes: Int) {
    NOW(R.string.quadrant_now),
    SCHEDULE(R.string.quadrant_schedule),
    DELEGATE(R.string.quadrant_delegate),
    DROP(R.string.quadrant_drop),
    ;

    val label: String get() = str(labelRes)
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

    /** Timed steps determine the duration, otherwise the task's own value applies. */
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
