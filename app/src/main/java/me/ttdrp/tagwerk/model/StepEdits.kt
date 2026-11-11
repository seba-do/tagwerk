package me.ttdrp.tagwerk.model

/** Bearbeiten der Schritte im Formular, ohne Oberfläche und damit testbar. */
object StepEdits {
    const val DEFAULT_MINUTES = 10
    private const val MAX_MINUTES = 999

    fun timersOn(steps: List<Subtask>): Boolean = steps.any { it.minutes != null }

    fun add(steps: List<Subtask>): List<Subtask> =
        steps + Subtask(title = "", minutes = if (timersOn(steps)) DEFAULT_MINUTES else null)

    fun setTimers(steps: List<Subtask>, on: Boolean): List<Subtask> =
        steps.map { it.copy(minutes = if (on) it.minutes ?: DEFAULT_MINUTES else null) }

    /** Im Timer-Betrieb hat jeder Schritt mindestens eine Minute, sonst gäbe es nichts herunterzuzählen. */
    fun minutesFrom(text: String): Int {
        val digits = text.filter(Char::isDigit).take(6)
        return (digits.toIntOrNull() ?: 1).coerceIn(1, MAX_MINUTES)
    }

    fun move(steps: List<Subtask>, from: Int, to: Int): List<Subtask> {
        if (from !in steps.indices || to !in steps.indices || from == to) return steps
        return steps.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun forSaving(steps: List<Subtask>): List<Subtask> =
        steps.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() }
}

/** Ohne eigene Wahl folgt das Emoji dem Titel. */
val Subtask.shownEmoji: String get() = emoji ?: EmojiGuess.forTitle(title)
