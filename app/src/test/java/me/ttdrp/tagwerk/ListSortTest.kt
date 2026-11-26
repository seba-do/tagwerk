package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.ListSort
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.sortTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class ListSortTest {
    init {
        TestStrings.install()
    }

    private fun task(title: String, minutes: Int = 30) = Task(title = title, minutes = minutes)
    private val tasks = listOf(task("Zebra", 10), task("äpfel", 60), task("Birne", 5))

    private fun titles(sort: ListSort, input: List<Task> = tasks) = sortTasks(input, sort).map { it.title }

    @Test
    fun `manuell bleibt die gespeicherte Reihenfolge`() {
        assertEquals(listOf("Zebra", "äpfel", "Birne"), titles(ListSort.MANUAL))
    }

    @Test
    fun `alphabetisch ordnet Umlaute deutsch und ignoriert Gross- und Kleinschreibung`() {
        assertEquals(listOf("äpfel", "Birne", "Zebra"), titles(ListSort.ALPHABETICAL))
    }

    @Test
    fun `nach Dauer kommen die kurzen zuerst`() {
        assertEquals(listOf("Birne", "Zebra", "äpfel"), titles(ListSort.DURATION))
    }

    @Test
    fun `die Dauer zaehlt auch die Timer der Schritte`() {
        val timed = task("Mit Schritten", 90).copy(subtasks = listOf(Subtask(title = "x", minutes = 1)))
        assertEquals(listOf("Mit Schritten", "Birne", "Zebra", "äpfel"), titles(ListSort.DURATION, tasks + timed))
    }

    @Test
    fun `gleich lange behalten ihre Reihenfolge`() {
        val same = listOf(task("B", 10), task("A", 10))
        assertEquals(listOf("B", "A"), titles(ListSort.DURATION, same))
    }
}
