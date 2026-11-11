package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.EmojiCatalog
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.StepEdits
import me.ttdrp.tagwerk.model.Subtask
import me.ttdrp.tagwerk.model.shownEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsTest {
    private fun step(title: String, minutes: Int? = null, id: String = title) = Subtask(id = id, title = title, minutes = minutes)

    @Test
    fun `neue Schritte sind leer und ohne Timer ohne Minuten`() {
        val added = StepEdits.add(emptyList())
        assertEquals("", added.single().title)
        assertNull(added.single().minutes)
    }

    @Test
    fun `mit laufenden Timern bekommt ein neuer Schritt zehn Minuten`() {
        val added = StepEdits.add(listOf(step("a", 5)))
        assertEquals(10, added.last().minutes)
    }

    @Test
    fun `Timer gelten als an sobald ein Schritt Minuten hat`() {
        assertFalse(StepEdits.timersOn(listOf(step("a"), step("b"))))
        assertTrue(StepEdits.timersOn(listOf(step("a"), step("b", 5))))
    }

    @Test
    fun `Timer einschalten vergibt zehn Minuten und laesst vorhandene stehen`() {
        val on = StepEdits.setTimers(listOf(step("a"), step("b", 25)), true)
        assertEquals(listOf(10, 25), on.map { it.minutes })
    }

    @Test
    fun `Timer ausschalten entfernt alle Minuten`() {
        val off = StepEdits.setTimers(listOf(step("a", 5), step("b", 25)), false)
        assertEquals(listOf<Int?>(null, null), off.map { it.minutes })
    }

    @Test
    fun `Minuten kommen aus der Eingabe und bleiben im Timer-Betrieb gueltig`() {
        assertEquals(45, StepEdits.minutesFrom("45"))
        assertEquals(45, StepEdits.minutesFrom("4x5"))
        assertEquals(999, StepEdits.minutesFrom("99999"))
        assertEquals(1, StepEdits.minutesFrom(""))
        assertEquals(1, StepEdits.minutesFrom("0"))
    }

    @Test
    fun `Schritte lassen sich umsortieren`() {
        val list = listOf(step("a"), step("b"), step("c"))
        assertEquals(listOf("b", "c", "a"), StepEdits.move(list, 0, 2).map { it.title })
        assertEquals(listOf("c", "a", "b"), StepEdits.move(list, 2, 0).map { it.title })
        assertEquals(list, StepEdits.move(list, 1, 1))
        assertEquals(list, StepEdits.move(list, 0, 9))
    }

    @Test
    fun `leere Schritte werden beim Speichern verworfen und Titel getrimmt`() {
        val saved = StepEdits.forSaving(listOf(step("  a  "), step("   ", id = "x"), step("")))
        assertEquals(listOf("a"), saved.map { it.title })
    }

    @Test
    fun `ohne eigene Wahl folgt das Emoji dem Titel`() {
        assertEquals("☕", Subtask(title = "Kaffee").shownEmoji)
    }

    @Test
    fun `ein gewaehltes Emoji bleibt auch bei neuem Titel`() {
        assertEquals("🦄", Subtask(title = "Kaffee", emoji = "🦄").copy(title = "Tee").shownEmoji)
    }

    private fun check(vararg cases: Pair<String, String>) = cases.forEach { (title, emoji) ->
        assertEquals(title, emoji, EmojiGuess.forTitle(title))
    }

    @Test
    fun `Woerter im Wort loesen kein falsches Emoji aus`() {
        check(
            "Blumen giessen" to "🌱",
            "Blumen gießen" to "🌱",
            "Termin nicht vergessen" to "📅",
            "Kommunikation" to EmojiGuess.DEFAULT,
            "Steuer machen" to "💰",
        )
    }

    @Test
    fun `zusammengesetzte Woerter werden erkannt`() {
        check(
            "Abendessen" to "🍽️",
            "Zahnarzt anrufen" to "🦷",
            "Hundefutter kaufen" to "🐕",
            "Wäschekorb leeren" to "🧺",
        )
    }

    @Test
    fun `alltaegliche Aufgaben in Deutsch und Englisch`() {
        check(
            "Kaffee" to "☕",
            "Medis geben" to "💊",
            "Füttern" to "🍖",
            "Walk the dog" to "🐕",
            "Morning routine" to "🌅",
            "Read book" to "📖",
            "Möbel aufbauen" to "🪑",
            "Wände streichen" to "🖌️",
            "Müll rausbringen" to "🗑️",
            "Haare waschen" to "💇",
            "Auto tanken" to "🚗",
            "Zug nehmen" to "🚆",
            "Geschenk besorgen" to "🎁",
            "Arzttermin" to "🩺",
            "Duschen" to "🚿",
            "Morgens in Gang kommen" to "🌅",
        )
    }

    @Test
    fun `die Suche findet ueber Namen und deutsche Stichwoerter`() {
        val names = mapOf("🐕" to "dog", "☕" to "hot beverage")
        val search = { q: String -> EmojiCatalog.search(q) { names[it].orEmpty() } }
        assertTrue("🐕" in search("dog"))
        assertTrue("🐕" in search("HUND"))
        assertTrue("☕" in search("kaffee"))
        assertFalse("🐕" in search("kaffee"))
    }

    @Test
    fun `die Sammlung hat alle Kategorien und keine Doppelten`() {
        assertEquals(9, EmojiCatalog.categories.size)
        assertTrue(EmojiCatalog.categories.all { it.emojis.isNotEmpty() })
        val all = EmojiCatalog.categories.flatMap { it.emojis }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `jedes erkannte Emoji ist auch auswaehlbar`() {
        val all = EmojiCatalog.categories.flatMap { it.emojis }.toSet()
        EmojiGuess.allEmojis.forEach { assertTrue(it, it in all) }
    }
}
