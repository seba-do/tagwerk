package me.ttdrp.tagwerk.model

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable
import me.ttdrp.tagwerk.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Serializable
enum class RepeatRule(@StringRes private val labelRes: Int) {
    DAILY(R.string.repeat_daily),
    WEEKDAYS(R.string.repeat_weekdays),
    WEEKENDS(R.string.repeat_weekends),
    WEEKLY(R.string.repeat_weekly),
    BIWEEKLY(R.string.repeat_biweekly),
    MONTHLY(R.string.repeat_monthly),
    YEARLY(R.string.repeat_yearly),
    CUSTOM(R.string.repeat_custom),
    ;

    val label: String get() = str(labelRes)
}

/** [weekdays] und [everyWeeks] gelten nur für [RepeatRule.CUSTOM]; Wochentage von 1 (Montag) bis 7. */
@Serializable
data class Repeat(
    val rule: RepeatRule,
    val weekdays: List<Int> = emptyList(),
    val everyWeeks: Int = 1,
    val untilEpochDay: Long? = null,
)

/** Short weekday names from Monday (1) to Sunday (7). */
fun shortDayName(isoDay: Int): String = str(SHORT_DAYS[isoDay - 1])

private val SHORT_DAYS = listOf(
    R.string.day_short_mon, R.string.day_short_tue, R.string.day_short_wed, R.string.day_short_thu,
    R.string.day_short_fri, R.string.day_short_sat, R.string.day_short_sun,
)

val Repeat.label: String
    get() {
        if (rule != RepeatRule.CUSTOM) return rule.label
        val days = weekdays.sorted().joinToString(", ") { shortDayName(it) }
        return if (everyWeeks > 1) str(R.string.repeat_every_weeks, days, everyWeeks) else days
    }

/**
 * Eine Serie ist eine Aufgabe mit [Task.repeat]: die Vorlage. Sie wird selbst nie angezeigt, jeder Tag bekommt
 * daraus eine berechnete Instanz mit der Kennung "Serie@Tag". Erst wenn sich an einem Tag etwas ändert,
 * wird daraus ein gespeicherter Eintrag, und die Vorlage merkt sich den Tag als Ausnahme.
 */
object Recurrence {
    enum class EditScope { THIS, FOLLOWING, ALL }

    private const val SEPARATOR = '@'

    fun occurrenceId(seriesId: String, epochDay: Long): String = "$seriesId$SEPARATOR$epochDay"

    fun occursOn(master: Task, date: LocalDate): Boolean {
        val repeat = master.repeat ?: return false
        val startDay = master.epochDay ?: return false
        val epoch = date.toEpochDay()
        if (epoch < startDay || epoch in master.exceptions) return false
        if (repeat.untilEpochDay != null && epoch > repeat.untilEpochDay) return false
        val start = LocalDate.ofEpochDay(startDay)
        return when (repeat.rule) {
            RepeatRule.DAILY -> true
            RepeatRule.WEEKDAYS -> date.dayOfWeek !in WEEKEND
            RepeatRule.WEEKENDS -> date.dayOfWeek in WEEKEND
            RepeatRule.WEEKLY -> date.dayOfWeek == start.dayOfWeek
            RepeatRule.BIWEEKLY -> (epoch - startDay) % 14 == 0L
            // Ein Termin am 31. fällt in kürzeren Monaten auf deren letzten Tag, statt auszufallen.
            RepeatRule.MONTHLY -> date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
            RepeatRule.YEARLY -> date.monthValue == start.monthValue && date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
            RepeatRule.CUSTOM -> date.dayOfWeek.value in repeat.weekdays &&
                ChronoUnit.WEEKS.between(mondayOf(start), mondayOf(date)) % repeat.everyWeeks.coerceAtLeast(1) == 0L
        }
    }

    /** Die Einträge eines Tages in gespeicherter Reihenfolge. Eine Serie steht dort, wo ihre Vorlage steht. */
    fun forDay(tasks: List<Task>, date: LocalDate): List<Task> {
        val epoch = date.toEpochDay()
        return tasks.mapNotNull { task ->
            when {
                task.repeat != null -> if (occursOn(task, date)) instance(task, epoch) else null
                task.epochDay == epoch -> task
                else -> null
            }
        }
    }

    fun expand(tasks: List<Task>, from: LocalDate, to: LocalDate): List<Task> {
        val range = from.toEpochDay()..to.toEpochDay()
        return tasks.flatMap { task ->
            if (task.repeat == null) {
                listOf(task).filter { (it.epochDay ?: Long.MIN_VALUE) in range }
            } else {
                range.mapNotNull { day -> if (occursOn(task, LocalDate.ofEpochDay(day))) instance(task, day) else null }
            }
        }
    }

    /** Sucht gespeicherte Einträge und berechnete Instanzen. */
    fun find(tasks: List<Task>, id: String): Task? {
        tasks.firstOrNull { it.id == id }?.let { return it }
        val master = tasks.firstOrNull { it.id == id.substringBefore(SEPARATOR, "") } ?: return null
        val day = dayOf(id) ?: return null
        return if (occursOn(master, LocalDate.ofEpochDay(day))) instance(master, day) else null
    }

    fun masterOf(tasks: List<Task>, task: Task): Task? =
        task.seriesId?.let { series -> tasks.firstOrNull { it.id == series && it.repeat != null } }

    fun upsert(tasks: List<Task>, task: Task): List<Task> {
        if (tasks.any { it.id == task.id }) return tasks.map { if (it.id == task.id) task else it }
        val master = masterOf(tasks, task) ?: return tasks + task
        val day = dayOf(task.id) ?: return tasks + task
        val index = tasks.indexOf(master)
        val marked = master.copy(exceptions = master.exceptions + day)
        return tasks.take(index) + marked + task + tasks.drop(index + 1)
    }

    /** Vor dem Verschieben: Eine Instanz braucht einen echten Platz in der Liste. */
    fun materialize(tasks: List<Task>, id: String?): List<Task> {
        if (id == null || tasks.any { it.id == id }) return tasks
        return find(tasks, id)?.let { upsert(tasks, it) } ?: tasks
    }

    /** Eine eigenständige, offene Kopie direkt hinter dem Original. Eine Instanz steht dort, wo ihre Serie steht. */
    fun duplicate(tasks: List<Task>, task: Task, makeId: () -> String = ::newId): List<Task> {
        val copy = task.copy(
            id = makeId(),
            seriesId = null,
            repeat = null,
            exceptions = emptyList(),
            doneAtMs = null,
            subtasks = task.subtasks.map { it.copy(id = makeId(), done = false) },
        )
        val index = tasks.indexOfFirst { it.id == task.id }.takeIf { it >= 0 } ?: tasks.indexOfFirst { it.id == task.seriesId }
        return if (index < 0) tasks + copy else tasks.take(index + 1) + copy + tasks.drop(index + 1)
    }

    fun applyEdit(
        tasks: List<Task>,
        edited: Task,
        repeat: Repeat?,
        scope: EditScope,
        makeId: () -> String = ::newId,
    ): List<Task> {
        val master = masterOf(tasks, edited) ?: return upsert(tasks, edited.copy(repeat = repeat))
        val day = dayOf(edited.id) ?: return upsert(tasks, edited)
        return when {
            scope == EditScope.THIS -> upsert(tasks, edited.copy(repeat = null))
            scope == EditScope.FOLLOWING && day > (master.epochDay ?: day) -> editFollowing(tasks, master, edited, repeat, day, makeId)
            else -> editAll(tasks, master, edited, repeat, day, makeId)
        }
    }

    fun delete(tasks: List<Task>, id: String, scope: EditScope): List<Task> {
        val task = find(tasks, id) ?: return tasks
        val master = masterOf(tasks, task) ?: return tasks.filterNot { it.id == id }
        val day = dayOf(id) ?: return tasks.filterNot { it.id == id }
        return when {
            scope == EditScope.THIS -> tasks
                .filterNot { it.id == id }
                .map { if (it.id == master.id && day !in it.exceptions) it.copy(exceptions = it.exceptions + day) else it }
            scope == EditScope.FOLLOWING && day > (master.epochDay ?: day) -> cutOff(tasks, master, day)
            else -> tasks.filterNot { it.id == master.id || isOpenInstanceOf(it, master) }
        }
    }

    private fun instance(master: Task, day: Long): Task = master.copy(
        id = occurrenceId(master.id, day),
        epochDay = day,
        seriesId = master.id,
        repeat = null,
        exceptions = emptyList(),
        doneAtMs = null,
        subtasks = master.subtasks.map { it.copy(done = false) },
    )

    private fun dayOf(id: String): Long? = id.substringAfterLast(SEPARATOR, "").toLongOrNull()

    private fun mondayOf(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun isOpenInstanceOf(task: Task, master: Task) = task.seriesId == master.id && !task.isDone

    private fun cutOff(tasks: List<Task>, master: Task, day: Long): List<Task> = tasks
        .filterNot { isOpenInstanceOf(it, master) && (dayOf(it.id) ?: Long.MIN_VALUE) >= day }
        .map { if (it.id == master.id) it.copy(repeat = it.repeat?.copy(untilEpochDay = day - 1)) else it }

    private fun asMaster(edited: Task, id: String, start: Long?, repeat: Repeat, exceptions: List<Long>) = edited.copy(
        id = id,
        epochDay = start,
        seriesId = null,
        repeat = repeat,
        exceptions = exceptions,
        doneAtMs = null,
        subtasks = edited.subtasks.map { it.copy(done = false) },
    )

    private fun editFollowing(
        tasks: List<Task>,
        master: Task,
        edited: Task,
        repeat: Repeat?,
        day: Long,
        makeId: () -> String,
    ): List<Task> {
        // Erledigte Tage bleiben stehen, die neue Serie darf sie nicht noch einmal erzeugen.
        val keptDays = tasks.filter { it.seriesId == master.id && it.isDone }.mapNotNull { dayOf(it.id) }.filter { it >= day }
        val skipped = master.exceptions.filter { it >= day }
        val follower = if (repeat != null) {
            asMaster(edited, makeId(), edited.epochDay, repeat, (keptDays + skipped).distinct())
        } else {
            edited.copy(id = makeId(), seriesId = null, repeat = null)
        }
        val cut = cutOff(tasks, master, day)
        val index = cut.indexOfFirst { it.id == master.id }
        return cut.take(index + 1) + follower + cut.drop(index + 1)
    }

    private fun editAll(
        tasks: List<Task>,
        master: Task,
        edited: Task,
        repeat: Repeat?,
        day: Long,
        makeId: () -> String,
    ): List<Task> {
        val open = tasks.filter { isOpenInstanceOf(it, master) }
        val rest = tasks.filterNot { it in open }
        if (repeat == null) {
            return rest.map { if (it.id == master.id) edited.copy(id = makeId(), seriesId = null, repeat = null) else it }
        }
        val shift = (edited.epochDay ?: day) - day
        val freed = open.mapNotNull { dayOf(it.id) }.toSet()
        val updated = asMaster(
            edited,
            master.id,
            master.epochDay?.plus(shift),
            repeat,
            master.exceptions.filterNot { it in freed },
        )
        return rest.map { if (it.id == master.id) updated else it }
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
}
