package me.ttdrp.tagwerk.model

/** Eigene Auswahl gängiger Emojis. Angezeigt werden sie mit der Emoji-Schrift des Geräts. */
object EmojiCatalog {
    data class Category(val name: String, val emojis: List<String>)

    private fun set(text: String) = text.trim().split(Regex("\\s+"))

    val categories: List<Category> = listOf(
        Category(
            "Smileys und Gefühle",
            set(
                """😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄
                😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭
                😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 👻 👽 🤖 💩 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💖 ✨""",
            ),
        ),
        Category(
            "Menschen",
            set(
                """👋 🤚 ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 👏 🙌 👐 🤲 🤝 🙏 💪 🧠 👀 👂 👃 👄 🦷 🦶 🦵
                👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 💁 🙋 🙇 🤦 🤷 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 🧖 🧘 👪 👫 👬 👭 🧑‍💻 🧑‍🍳 🧑‍🎓 🧑‍🏫""",
            ),
        ),
        Category(
            "Tiere und Natur",
            set(
                """🐶 🐕 🐩 🐱 🐈 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦉 🦇 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🐙
                🐠 🐟 🐬 🐳 🦈 🐘 🦒 🐄 🐖 🐑 🐐 🌵 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍃 🍂 🍁 🌾 🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌙 ⭐ 🌟 ⚡ 🔥
                🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 💨 💧 🌊""",
            ),
        ),
        Category(
            "Essen und Trinken",
            set(
                """🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🥞
                🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍚 🍙 🍦 🍩 🍪 🎂 🍰 🧁 🍫 🍬 🍭 🍯 🥛 ☕ 🍵 🧃 🥤 🍺
                🍷 🥂 🍽️ 🍴 🥄""",
            ),
        ),
        Category(
            "Reisen und Orte",
            set(
                """🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚜 🚲 🛴 🛵 🏍️ 🚨 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ ⛽ 🚧 🚦 🗺️ 🧭 🏔️ ⛰️ 🏕️ 🏖️ 🏝️
                🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 ⛪ 🕌 🏰 🗼 🗽 ⛲ 🌅 🌄 🌃 🌆 🌇 🌉""",
            ),
        ),
        Category(
            "Aktivitäten",
            set(
                """⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🏂 🏋️ 🤸 🚴 🏊 🧗 🏆 🥇 🥈 🥉 🏅 🎖️ 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎵 🎶 🎹
                🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎮 🕹️ 🧩 🎉 🎊 🎈 🎁 🎀""",
            ),
        ),
        Category(
            "Gegenstände",
            set(
                """🛒 ⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⏱️ ⏳ 🔋 🔌 💡 🔦 🕯️ 🧯 💸 💵 💳 💰 💎 🧾 🔧 🔨 🛠️ ⛏️ 🔩 ⚙️ 🧰
                🧲 🔬 🔭 🩺 💉 💊 🩹 🧬 🧹 🧺 🧻 🧼 🧽 🧴 🪒 🚿 🛁 🚽 🛏️ 🛋️ 🪑 🚪 🖌️ 🖍️ ✏️ ✒️ 🖊️ 📝 📄 📃 📑 📊 📈 📉 🗒️ 📅 📆 📇
                📋 📁 📂 🗂️ 📎 📌 📍 ✂️ 📏 📐 📕 📗 📘 📙 📖 📚 🔖 📧 ✉️ 📨 📦 📮 🗑️ 🔒 🔓 🔑 🗝️ 👕 👖 👗 👟 👠 🧢 👜 🎒 👓 🌂""",
            ),
        ),
        Category(
            "Symbole",
            set(
                """✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ ❓ ❔ ❕ ❗ ‼️ ⁉️ 💯 🔅 🔆 ⚠️ 🚫 ⛔ 📵 🔞 ♻️ ⚜️ 🔱 ⭕ 🔴 🟠 🟡 🟢 🔵 🟣 🟤 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪
                🔶 🔷 🔸 🔹 🔺 🔻 💠 🔘 🔳 🔲 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁 🔂 ➡️ ⬅️ ⬆️ ⬇️ 🔄 🔃 🔔 🔕 📣 📢 💤 💬 💭 🆕 🆗 🆒 🆓 🆙""",
            ),
        ),
        Category(
            "Flaggen",
            set(
                """🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🇩🇪 🇦🇹 🇨🇭 🇪🇺 🇬🇧 🇺🇸 🇫🇷 🇪🇸 🇮🇹 🇳🇱 🇧🇪 🇵🇱 🇨🇿 🇩🇰 🇸🇪 🇳🇴 🇫🇮 🇵🇹 🇬🇷 🇹🇷 🇺🇦 🇯🇵 🇨🇳 🇰🇷 🇮🇳 🇧🇷 🇨🇦 🇦🇺""",
            ),
        ),
    )

    /** [nameOf] liefert den englischen Unicode-Namen. Deutsche Stichwörter kommen aus [EmojiGuess]. */
    fun search(query: String, nameOf: (String) -> String): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return categories.flatMap { it.emojis }.filter { emoji ->
            needle in nameOf(emoji).lowercase() || EmojiGuess.keywordsFor(emoji).any { needle in it }
        }
    }
}
