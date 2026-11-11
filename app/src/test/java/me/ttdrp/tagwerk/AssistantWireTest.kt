package me.ttdrp.tagwerk

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.ttdrp.tagwerk.ai.Assistant
import me.ttdrp.tagwerk.ai.AssistantException
import me.ttdrp.tagwerk.ai.Conversation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/** Prüft, was tatsächlich über die Leitung geht, ohne echte API. */
class AssistantWireTest {
    private val api = FakeApi()

    @After
    fun stop() = api.close()

    private fun assistant(conversation: Conversation, model: String = "model-a") =
        Assistant("test-key", model, api.url, conversation)

    @Test
    fun `Verlauf ueberlebt den Wechsel des Modells`() = runBlocking {
        val conversation = Conversation()
        api.reply(text("Erste Antwort"))
        api.reply(text("Zweite Antwort"))

        assistant(conversation, "model-a").chat("erste Frage", "ctx")
        assistant(conversation, "model-b").chat("zweite Frage", "ctx")

        val second = api.requests[1]
        assertEquals("model-b", second.field("model"))
        assertTrue(second.raw.contains("erste Frage"))
        assertTrue(second.raw.contains("Erste Antwort"))
    }

    @Test
    fun `spaete Entscheidung erreicht das Modell genau einmal`() = runBlocking {
        val conversation = Conversation()
        api.reply(proposal("t1"))
        api.reply(text("ok"))
        api.reply(text("ok"))
        api.reply(text("ok"))
        val ai = assistant(conversation)

        ai.chat("eins", "ctx")
        ai.chat("zwei", "ctx")
        conversation.recordDecision("t1", "Der Nutzer hat Aufräumen übernommen.")
        ai.chat("drei", "ctx")
        ai.chat("vier", "ctx")

        assertFalse(api.requests[1].raw.contains("Aufräumen übernommen"))
        assertTrue(api.requests[2].lastMessage().contains("Aufräumen übernommen"))
        assertEquals(1, api.requests[3].raw.split("Aufräumen übernommen").size - 1)
    }

    @Test
    fun `Entscheidung vor der naechsten Nachricht wird zum tool_result`() = runBlocking {
        val conversation = Conversation()
        api.reply(proposal("t1"))
        api.reply(text("ok"))
        val ai = assistant(conversation)

        ai.chat("eins", "ctx")
        conversation.recordDecision("t1", "Der Nutzer hat verworfen.")
        ai.chat("zwei", "ctx")

        val blocks = api.requests[1].messages().last().jsonObject["content"]!!.jsonArray
        val first = blocks.first().jsonObject
        assertEquals("tool_result", first["type"]!!.jsonPrimitive.content)
        assertEquals("t1", first["tool_use_id"]!!.jsonPrimitive.content)
        assertTrue(first.toString().contains("Der Nutzer hat verworfen."))
    }

    @Test
    fun `fehlgeschlagene Anfrage verliert keinen Hinweis`() = runBlocking {
        val conversation = Conversation()
        api.reply(proposal("t1"))
        api.reply(text("ok"))
        api.reply(ERROR, status = 400)
        api.reply(text("ok"))
        val ai = assistant(conversation)

        ai.chat("eins", "ctx")
        ai.chat("zwei", "ctx")
        conversation.recordDecision("t1", "Der Nutzer hat Lesen übernommen.")
        assertThrows(AssistantException::class.java) { runBlocking { ai.chat("drei", "ctx") } }
        ai.chat("drei nochmal", "ctx")

        assertTrue(api.requests[3].lastMessage().contains("Lesen übernommen"))
        assertFalse(api.requests[3].raw.contains("\"drei\""))
    }

    @Test
    fun `Vorschlags-Tool verlangt ein Datum`() = runBlocking {
        api.reply(text("ok"))
        assistant(Conversation()).chat("eins", "ctx")
        val task = api.requests[0].json.jsonObject["tools"]!!.jsonArray[0].jsonObject["input_schema"]!!
            .jsonObject["properties"]!!.jsonObject["tasks"]!!.jsonObject["items"]!!.jsonObject
        assertTrue(task["required"]!!.jsonArray.any { it.jsonPrimitive.content == "date" })
        assertTrue(task["properties"]!!.jsonObject.containsKey("date"))
    }

    private class Request(val raw: String) {
        val json: JsonElement = Json.parseToJsonElement(raw)
        fun field(name: String) = json.jsonObject[name]!!.jsonPrimitive.content
        fun messages() = json.jsonObject["messages"]!!.jsonArray
        fun lastMessage() = messages().last().toString()
    }

    private class FakeApi : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val replies = LinkedBlockingQueue<Pair<Int, String>>()
        val requests = CopyOnWriteArrayList<Request>()
        val url = "http://127.0.0.1:${socket.localPort}"

        fun reply(body: String, status: Int = 200) = replies.put(status to body)

        init {
            thread(isDaemon = true) {
                try {
                    while (true) socket.accept().use { client ->
                        val input = BufferedInputStream(client.getInputStream())
                        var length = 0
                        while (true) {
                            val line = readLine(input)
                            if (line.isEmpty()) break
                            if (line.startsWith("content-length:", ignoreCase = true)) {
                                length = line.substringAfter(':').trim().toInt()
                            }
                        }
                        requests += Request(String(input.readNBytes(length), Charsets.UTF_8))
                        val (status, body) = replies.take()
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val head = "HTTP/1.1 $status X\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        client.getOutputStream().apply {
                            write(head.toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: java.net.SocketException) {
                    // Server wurde geschlossen.
                }
            }
        }

        private fun readLine(input: BufferedInputStream): String {
            val out = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1 || c == '\n'.code) return out.toString().trimEnd('\r')
                out.append(c.toChar())
            }
        }

        override fun close() = socket.close()
    }

    private companion object {
        const val ERROR = """{"type":"error","error":{"type":"invalid_request_error","message":"kaputt"}}"""

        fun message(content: String, stop: String) =
            """{"id":"m","type":"message","role":"assistant","model":"x","content":$content,""" +
                """"stop_reason":"$stop","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}"""

        fun text(value: String) = message("""[{"type":"text","text":"$value"}]""", "end_turn")

        fun proposal(id: String) = message(
            """[{"type":"tool_use","id":"$id","name":"propose_tasks","input":{"tasks":[]}}]""",
            "tool_use",
        )
    }
}
