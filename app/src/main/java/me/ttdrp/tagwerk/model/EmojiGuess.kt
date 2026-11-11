package me.ttdrp.tagwerk.model

/**
 * Schätzt ein Emoji aus dem Titel. Läuft offline, ohne API-Key und schon während des Tippens.
 * Verglichen wird wortweise: Ein Stichwort zählt am Wortanfang oder, ab vier Buchstaben, am Wortende,
 * damit "Abendessen" trifft, "Kommunikation" aber nicht wegen "uni". Mit "=" zählt nur das ganze Wort.
 * Die Reihenfolge ist Absicht: Das Speziellere steht vorn, "Zahnarzt" soll der Zahn sein und nicht der Arzt.
 */
object EmojiGuess {
    const val DEFAULT = "✨"

    private val ignored = setOf("vergessen", "vergesse", "vergiss", "messen", "interessen", "gemessen")

    private val rules: List<Pair<String, List<String>>> = listOf(
        "🦷" to listOf("zähne", "zahn", "teeth", "tooth", "dentist"),
        "💇" to listOf("haare", "haar", "friseur", "frisör", "hair"),
        "🩺" to listOf("arzt", "ärztin", "doctor", "praxis", "hausarzt"),
        "💊" to listOf("medis", "medikament", "tablette", "pille", "meds", "=medizin", "medicine"),
        "🐕" to listOf("hund", "gassi", "=dog", "dogs"),
        "🐈" to listOf("katze", "kater", "=cat", "cats"),
        "🍖" to listOf("füttern", "futter", "feed"),
        "🌱" to listOf("giess", "gieß", "pflanz", "blume", "garten", "water the", "plant", "garden"),
        "☕" to listOf("kaffee", "coffee", "espresso", "=tee", "=tea"),
        "🍳" to listOf("frühstück", "breakfast", "koch", "=cook", "cooking", "backen"),
        "🍽️" to listOf("essen", "mittag", "abendbrot", "dinner", "lunch", "mahlzeit", "=eat"),
        "🎁" to listOf("geschenk", "gift", "=present"),
        "🛒" to listOf("einkauf", "kaufen", "supermarkt", "groceries", "grocery", "shopping"),
        "🧺" to listOf("wäsche", "laundry", "waschen", "bügeln"),
        "🗑️" to listOf("müll", "abfall", "trash", "garbage"),
        "🖌️" to listOf("streich", "=malen", "=paint", "lackier"),
        "🪑" to listOf("möbel", "stuhl", "furniture", "=chair"),
        "💡" to listOf("beleuchtung", "lampe", "licht", "=idee", "=ideen", "idea"),
        "🧹" to listOf("putzen", "aufräumen", "ausräumen", "saugen", "=clean", "cleaning", "tidy", "wischen", "boden"),
        "🚿" to listOf("dusch", "shower"),
        "🛁" to listOf("=bad", "baden", "badezimmer", "bath"),
        "🏃" to listOf("laufen", "joggen", "=run", "running", "sport", "training", "workout", "fitness", "gym"),
        "🚶" to listOf("spazier", "=walk", "walking"),
        "🚲" to listOf("fahrrad", "=rad", "=bike", "cycling"),
        "🧘" to listOf("yoga", "meditat", "atmen", "breath", "=ruhe", "pause"),
        "🚗" to listOf("=auto", "tanken", "autowerkstatt", "=car"),
        "🚆" to listOf("=zug", "=bahn", "bahnhof", "=train"),
        "✈️" to listOf("flug", "flight", "urlaub", "vacation", "reise", "=trip"),
        "📖" to listOf("lesen", "=buch", "bücher", "=book", "books", "=read", "reading"),
        "📚" to listOf("lernen", "hausaufgabe", "=uni", "study", "studieren", "=schule", "school"),
        "📧" to listOf("mail", "e-mail", "nachricht", "=email", "=message"),
        "📞" to listOf("anruf", "telefon", "=call", "=phone"),
        "💰" to listOf("rechnung", "steuer", "=bank", "überweis", "=invoice", "=tax", "taxes", "budget"),
        "📅" to listOf("termin", "planen", "=woche", "=plan", "kalender", "calendar", "appointment"),
        "💻" to listOf("arbeit", "projekt", "=code", "=work", "meeting", "büro", "office", "=job"),
        "🌅" to listOf("morgen", "morning", "routine", "aufstehen", "=wake"),
        "🛏️" to listOf("schlafen", "=bett", "=sleep", "=bed", "nacht"),
        "🎂" to listOf("geburtstag", "birthday"),
        "🎉" to listOf("party", "feier", "=fest"),
        "🎵" to listOf("musik", "music", "=lied", "song", "=gitarre", "klavier"),
        "🎮" to listOf("=spiel", "=spielen", "=game", "=gaming", "zocken"),
        "👶" to listOf("=baby", "=kind", "=kinder", "kita", "=windel"),
        "💧" to listOf("=wasser", "=water", "trinken", "=drink"),
        "🔧" to listOf("reparier", "werkzeug", "=fix", "=repair", "aufbauen", "montier", "installier"),
        "📦" to listOf("paket", "=post", "=package", "=parcel", "=umzug"),
        "📝" to listOf("notiz", "schreiben", "=note", "=notes", "=write", "=test", "=liste", "=todo"),
    )

    val allEmojis: Set<String> = rules.map { it.first }.toSet() + DEFAULT

    fun forTitle(title: String): String {
        val tokens = tokensOf(title)
        return rules.firstOrNull { (_, words) -> words.any { word -> tokens.any { matches(it, word) } || isPhrase(word, title) } }?.first
            ?: DEFAULT
    }

    /** Für die Suche in der Emoji-Auswahl. */
    fun keywordsFor(emoji: String): List<String> =
        rules.firstOrNull { it.first == emoji }?.second.orEmpty().map { it.removePrefix("=") }

    /** Stabile Farbe je Titel, damit eine Aufgabe nicht bei jeder Änderung die Farbe wechselt. */
    fun tintFor(title: String): Int = Math.floorMod(title.trim().lowercase().hashCode(), 6)

    private fun tokensOf(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}-]+")).filter { it.isNotEmpty() && it !in ignored }

    private fun isPhrase(word: String, title: String) = ' ' in word && word in title.lowercase()

    private fun matches(token: String, word: String): Boolean = when {
        word.startsWith("=") -> token == word.drop(1)
        ' ' in word -> false
        token.startsWith(word) -> true
        else -> word.length >= 4 && token.endsWith(word)
    }
}
