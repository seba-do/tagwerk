package me.ttdrp.tagwerk.model

import java.text.Collator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class Grouping(val label: String) {
    NONE("Keine Gruppierung"),
    PRIORITY("Priorität"),
    SIZE("Aufgabengröße"),
    QUADRANT("Eisenhower"),
}

enum class SizeBucket(val label: String) {
    TINY("Unter 15 Minuten"),
    SHORT("15 bis 30 Minuten"),
    MEDIUM("30 bis 60 Minuten"),
    LONG("Über eine Stunde"),
    ;

    companion object {
        fun of(minutes: Int) = when {
            minutes < 15 -> TINY
            minutes <= 30 -> SHORT
            minutes <= 60 -> MEDIUM
            else -> LONG
        }
    }
}

enum class ListSort(val label: String) {
    MANUAL("Manuelle Reihenfolge"),
    ALPHABETICAL("Alphabetisch"),
    DURATION("Dauer"),
}

/** Nur die Sicht: Die gespeicherte Reihenfolge bleibt, damit "Manuell" sie jederzeit zurückbringt. */
fun sortTasks(tasks: List<Task>, sort: ListSort): List<Task> = when (sort) {
    ListSort.MANUAL -> tasks
    ListSort.ALPHABETICAL -> tasks.sortedWith(compareBy(Collator.getInstance(Locale.GERMAN)) { it.title })
    ListSort.DURATION -> tasks.sortedBy { it.effectiveMinutes }
}

data class Section(val title: String, val tasks: List<Task>)

/** Dieselben Aufgaben, nur anders sortiert: Die Sicht ändert nie die Daten. */
fun groupTasks(tasks: List<Task>, grouping: Grouping): List<Section> = when (grouping) {
    Grouping.NONE -> listOf(Section("", tasks))
    Grouping.PRIORITY -> Priority.entries.map { p -> Section(p.label, tasks.filter { it.priority == p }) }
    Grouping.QUADRANT -> Quadrant.entries.map { q -> Section(q.label, tasks.filter { it.quadrant == q }) }
    Grouping.SIZE -> SizeBucket.entries.map { b ->
        Section(b.label, tasks.filter { SizeBucket.of(it.effectiveMinutes) == b })
    }
}

fun daypartOf(startMinute: Int): Daypart = when {
    startMinute < 12 * 60 -> Daypart.MORNING
    startMinute < 18 * 60 -> Daypart.AFTERNOON
    else -> Daypart.EVENING
}

/** Nach Dauer lässt sich nicht einsortieren: Die Dauer gehört zur Aufgabe und soll sich nicht beim Ziehen ändern. */
fun isDraggableGrouping(grouping: Grouping): Boolean = grouping != Grouping.SIZE

/** Eine auf eine Gruppe gezogene Aufgabe bekommt deren Wert. */
fun applyGroup(task: Task, grouping: Grouping, title: String): Task = when (grouping) {
    Grouping.PRIORITY -> Priority.entries.firstOrNull { it.label == title }?.let { task.copy(priority = it) } ?: task
    Grouping.QUADRANT -> Quadrant.entries.firstOrNull { it.label == title }?.let { task.copy(quadrant = it) } ?: task
    Grouping.NONE, Grouping.SIZE -> task
}

sealed interface DayEntry
data class TaskEntry(val task: Task) : DayEntry
data class EventEntry(val event: CalendarEvent) : DayEntry

data class DaySection(val part: Daypart, val entries: List<DayEntry>)

/** Tagesansicht mit Terminen. Alle Abschnitte bleiben stehen: Auch ein leerer nimmt Karten an. */
fun groupDayEntries(tasks: List<Task>, events: List<CalendarEvent>, day: LocalDate, zone: ZoneId): List<DaySection> {
    val todays = events.filter { CalendarEvents.occursOn(it, day, zone) }
    return Daypart.entries.map { part ->
        val timedEvents = todays
            .filter { CalendarEvents.daypartOn(it, day, zone) == part }
            .map { (CalendarEvents.minuteOn(it, day, zone) ?: Int.MAX_VALUE) to EventEntry(it) }
        val timedTasks = tasks
            .filter { (it.startMinute?.let(::daypartOf) ?: it.daypart) == part }
            .map { (it.startMinute ?: Int.MAX_VALUE) to TaskEntry(it) }
        DaySection(part, (timedEvents + timedTasks).sortedBy { it.first }.map { it.second })
    }
}
