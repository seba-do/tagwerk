package me.ttdrp.tagwerk

import me.ttdrp.tagwerk.model.Text
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Lets JVM tests resolve string resources by reading strings.xml, English by default. */
object TestStrings {
    fun install(folder: String = "values") {
        val file = listOf("src/main/res/$folder/strings.xml", "app/src/main/res/$folder/strings.xml").map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val byName = (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
        }
        val byId = R.string::class.java.fields.associate { it.getInt(null) to byName[it.name] }
        Text.resolver = Text.Resolver { id, args ->
            val pattern = byId[id] ?: error("unknown string id $id")
            if (args.isEmpty()) pattern else String.format(pattern, *args)
        }
    }

    private fun unescape(s: String) = s.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n").replace("\\@", "@")
}
