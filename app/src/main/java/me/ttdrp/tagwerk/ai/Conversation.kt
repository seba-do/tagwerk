package me.ttdrp.tagwerk.ai

import com.anthropic.models.messages.MessageParam

/**
 * Der Gesprächsstand gehört dem Gespräch, nicht dem Client: Er übersteht einen Wechsel von Modell oder Key.
 * Die Entscheidungen kommen vom UI-Thread, die Antworten vom IO-Thread, daher synchronisiert.
 */
class Conversation {
    data class Outgoing(val toolResults: List<Pair<String, String>>, val notes: List<String>)

    private val history = mutableListOf<MessageParam>()
    private val openToolUses = mutableListOf<String>()
    private val decisions = mutableMapOf<String, String>()
    private val notes = mutableListOf<String>()

    /** Zu spät für ein tool_result: Dann erfährt das Modell es als Hinweis mit der nächsten Nachricht. */
    @Synchronized
    fun recordDecision(toolUseId: String, decision: String) {
        if (toolUseId in openToolUses) decisions[toolUseId] = decision else addNote(decision)
    }

    /** Die API verlangt zu jedem tool_use ein Ergebnis, auch ohne Entscheidung des Nutzers. */
    @Synchronized
    fun outgoing(): Outgoing = Outgoing(
        toolResults = openToolUses.map { it to (decisions[it] ?: UNDECIDED) },
        notes = notes.toList(),
    )

    @Synchronized
    fun history(): List<MessageParam> = history.toList()

    /** Erst nach einer Antwort aufrufen: Ein Fehler lässt den Stand unverändert. */
    @Synchronized
    fun commit(sent: Outgoing, exchange: List<MessageParam>, newToolUses: List<String>) {
        history += exchange
        notes.removeAll(sent.notes)
        // Entschieden, während die Anfrage lief: Das tool_result ist schon ohne diese Entscheidung unterwegs.
        val sentResults = sent.toolResults.toMap()
        decisions.filter { (id, decision) -> sentResults[id] != decision }.values.forEach(::addNote)
        decisions.clear()
        openToolUses.clear()
        openToolUses += newToolUses
    }

    @Synchronized
    fun reset() {
        history.clear()
        openToolUses.clear()
        decisions.clear()
        notes.clear()
    }

    private fun addNote(note: String) {
        if (note !in notes) notes += note
    }

    private companion object {
        const val UNDECIDED = "Der Nutzer hat noch nicht entschieden."
    }
}
