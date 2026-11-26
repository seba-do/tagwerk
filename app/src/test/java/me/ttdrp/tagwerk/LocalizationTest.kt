package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.Daypart
import me.ttdrp.tagwerk.model.Repeat
import me.ttdrp.tagwerk.model.RepeatRule
import me.ttdrp.tagwerk.model.label
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {
    @After
    fun englishAgain() = TestStrings.install()

    @Test
    fun `German stays available`() {
        TestStrings.install("values-de")
        assertEquals("Abend", Daypart.EVENING.label)
        assertEquals("Fr · alle 2 Wochen", Repeat(RepeatRule.CUSTOM, weekdays = listOf(5), everyWeeks = 2).label)
    }

    @Test
    fun `both languages have the same strings with the same arguments`() {
        val en = read("values")
        val de = read("values-de")
        assertEquals(en.keys, de.keys)
        val args = Regex("%\\d\\$[sd]")
        en.forEach { (name, text) ->
            assertEquals(name, args.findAll(text).map { it.value }.toSet(), args.findAll(de.getValue(name)).map { it.value }.toSet())
        }
    }

    private fun read(folder: String): Map<String, String> {
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml").map(::File).first { it.exists() }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
    }
}
