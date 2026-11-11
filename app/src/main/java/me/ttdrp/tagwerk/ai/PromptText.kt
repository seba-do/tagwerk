package me.ttdrp.tagwerk.ai

import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.label
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object PromptText {
    fun breakDownInstruction(title: String, targetMinutes: Int?): String {
        val base = "Zerlege diese Aufgabe in kleine Schritte: $title"
        return if (targetMinutes == null) base else "$base\nDie Schritte sollen zusammen $targetMinutes Minuten dauern."
    }

    fun chatContext(tasks: List<Task>, today: LocalDate): String {
        val todayDay = today.toEpochDay()
        val lines = tasks
            .filter { !it.isDone && (it.kind == Kind.TODO || it.repeat != null || (it.epochDay ?: Long.MIN_VALUE) >= todayDay) }
            .joinToString("\n") { "- ${it.title} (${it.effectiveMinutes} Min., ${placeOf(it, todayDay)})" }
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN)
        return "Heute ist $weekday, $today.\nOffene Aufgaben des Nutzers:\n${lines.ifEmpty { "(keine)" }}"
    }

    private fun placeOf(task: Task, todayDay: Long): String = when {
        task.kind == Kind.TODO -> "auf der Liste"
        task.repeat != null -> "wiederholt sich: ${task.repeat.label}"
        task.epochDay == todayDay -> "heute geplant"
        else -> "geplant für ${LocalDate.ofEpochDay(task.epochDay ?: todayDay)}"
    }
}
