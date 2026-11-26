package me.ttdrp.tagwerk.ai

import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.label
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object PromptText {
    fun breakDownInstruction(title: String, targetMinutes: Int?): String {
        val base = "Break this task down into small steps: $title"
        return if (targetMinutes == null) base else "$base\nTogether the steps should take $targetMinutes minutes."
    }

    fun chatContext(tasks: List<Task>, today: LocalDate): String {
        val todayDay = today.toEpochDay()
        val lines = tasks
            .filter { !it.isDone && (it.kind == Kind.TODO || it.repeat != null || (it.epochDay ?: Long.MIN_VALUE) >= todayDay) }
            .joinToString("\n") { "- ${it.title} (${it.effectiveMinutes} min, ${placeOf(it, todayDay)})" }
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "Today is $weekday, $today.\nThe user's open tasks:\n${lines.ifEmpty { "(none)" }}"
    }

    private fun placeOf(task: Task, todayDay: Long): String = when {
        task.kind == Kind.TODO -> "on the list"
        task.repeat != null -> "repeats: ${task.repeat.label}"
        task.epochDay == todayDay -> "planned for today"
        else -> "planned for ${LocalDate.ofEpochDay(task.epochDay ?: todayDay)}"
    }
}
