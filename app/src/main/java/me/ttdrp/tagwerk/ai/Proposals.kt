package me.ttdrp.tagwerk.ai

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.str
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.floor

data class ProposedTask(
    val title: String,
    val kind: Kind,
    val daypart: Daypart,
    val minutes: Int,
    val subtasks: List<String>,
    val date: LocalDate? = null,
)

data class SuggestedItem(val title: String, val minutes: Int?)

/** Liest die Tool-Eingaben des Modells. Unbrauchbare Einträge fallen weg, statt abzustürzen. */
object Proposals {
    fun parseTasks(input: Map<*, *>): List<ProposedTask> =
        (input["tasks"] as? List<*>).orEmpty().mapNotNull { raw ->
            val task = raw as? Map<*, *> ?: return@mapNotNull null
            val title = (task["title"] as? String)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            ProposedTask(
                title = title,
                kind = if (task["kind"] == "todo") Kind.TODO else Kind.PLAN,
                daypart = when (task["daypart"]) {
                    "morning" -> Daypart.MORNING
                    "afternoon" -> Daypart.AFTERNOON
                    "evening" -> Daypart.EVENING
                    else -> Daypart.ANYTIME
                },
                minutes = (task["minutes"] as? Number)?.toInt()?.coerceIn(1, 480) ?: 30,
                subtasks = (task["subtasks"] as? List<*>).orEmpty()
                    .mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) },
                date = (task["date"] as? String)?.let(::parseDate),
            )
        }

    fun parseItems(input: Map<*, *>): List<SuggestedItem> =
        (input["items"] as? List<*>).orEmpty().mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            val title = (item["title"] as? String)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            SuggestedItem(title, (item["minutes"] as? Number)?.toInt()?.takeIf { it > 0 })
        }

    private fun parseDate(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim())
        } catch (e: DateTimeParseException) {
            null
        }

    /** Tag für den Plan. Listeneinträge haben keinen, und in die Vergangenheit wird nichts geplant. */
    fun planDay(task: ProposedTask, today: LocalDate): Long? {
        if (task.kind == Kind.TODO) return null
        return (task.date?.takeUnless { it.isBefore(today) } ?: today).toEpochDay()
    }

    fun placeLabel(task: ProposedTask, today: LocalDate): String {
        val epochDay = planDay(task, today) ?: return str(R.string.place_list)
        val day = when (val date = LocalDate.ofEpochDay(epochDay)) {
            today -> null
            today.plusDays(1) -> str(R.string.place_tomorrow)
            else -> date.format(DateTimeFormatter.ofPattern(str(R.string.place_date_pattern), Locale.getDefault()))
        }
        return listOfNotNull(str(R.string.place_plan), day, task.daypart.label).joinToString(", ")
    }

    /**
     * Passt die Schritte an die geplante Dauer an, denn ihre Summe wird zur Dauer der Aufgabe.
     * Das Modell trifft die Vorgabe nicht verlässlich. Jeder Schritt behält mindestens eine Minute.
     */
    fun fitToDuration(items: List<SuggestedItem>, targetMinutes: Int): List<SuggestedItem> {
        val sum = items.sumOf { it.minutes ?: 0 }
        if (sum == 0 || targetMinutes <= 0 || sum == targetMinutes) return items
        val exact = items.map { (it.minutes ?: 0) * targetMinutes.toDouble() / sum }
        val timed = items.indices.filter { items[it].minutes != null }
        val fitted = IntArray(items.size)
        timed.forEach { fitted[it] = floor(exact[it]).toInt().coerceAtLeast(1) }
        var rest = targetMinutes - fitted.sum()
        // Zu wenig: Die größten abgeschnittenen Reste bekommen je eine Minute dazu.
        timed.sortedByDescending { exact[it] - fitted[it] }.take(rest.coerceAtLeast(0)).forEach { fitted[it]++ }
        // Zu viel, weil Mindestminuten dazukamen: Die längsten Schritte geben ab.
        while (rest < 0) {
            val longest = timed.maxBy { fitted[it] }
            if (fitted[longest] <= 1) break
            fitted[longest]--
            rest++
        }
        return items.mapIndexed { i, item -> if (item.minutes == null) item else item.copy(minutes = fitted[i]) }
    }
}
