package me.ttdrp.tagwerk.model

import androidx.annotation.StringRes
import me.ttdrp.tagwerk.R

/** Eigene Auswahl gängiger Emojis. Angezeigt werden sie mit der Emoji-Schrift des Geräts. */
object EmojiCatalog {
    data class Category(@StringRes private val nameRes: Int, val emojis: List<String>) {
        val name: String get() = str(nameRes)
    }

    private fun set(text: String) = text.trim().split(Regex("\\s+"))

    val categories: List<Category> = listOf(
        Category(
            R.string.emoji_cat_smileys,
            set(
                """😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄
                😬 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭
                😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 👻 👽 🤖 💩 ❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 💕 💖 ✨""",
            ),
        ),
        Category(
            R.string.emoji_cat_people,
            set(
                """👋 🤚 ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 👍 👎 ✊ 👊 👏 🙌 👐 🤲 🤝 🙏 💪 🧠 👀 👂 👃 👄 🦷 🦶 🦵
                👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 💁 🙋 🙇 🤦 🤷 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 🧖 🧘 👪 👫 👬 👭 🧑‍💻 🧑‍🍳 🧑‍🎓 🧑‍🏫""",
            ),
        ),
        Category(
            R.string.emoji_cat_nature,
            set(
                """🐶 🐕 🐩 🐱 🐈 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦉 🦇 🐺 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🐙
                🐠 🐟 🐬 🐳 🦈 🐘 🦒 🐄 🐖 🐑 🐐 🌵 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍃 🍂 🍁 🌾 🌷 🌹 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌙 ⭐ 🌟 ⚡ 🔥
                🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 💨 💧 🌊""",
            ),
        ),
        Category(
            R.string.emoji_cat_food,
            set(
                """🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🧀 🥚 🍳 🥞
                🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍚 🍙 🍦 🍩 🍪 🎂 🍰 🧁 🍫 🍬 🍭 🍯 🥛 ☕ 🍵 🧃 🥤 🍺
                🍷 🥂 🍽️ 🍴 🥄""",
            ),
        ),
        Category(
            R.string.emoji_cat_travel,
            set(
                """🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🚜 🚲 🛴 🛵 🏍️ 🚨 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤 🚢 ⚓ ⛽ 🚧 🚦 🗺️ 🧭 🏔️ ⛰️ 🏕️ 🏖️ 🏝️
                🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 ⛪ 🕌 🏰 🗼 🗽 ⛲ 🌅 🌄 🌃 🌆 🌇 🌉""",
            ),
        ),
        Category(
            R.string.emoji_cat_activities,
            set(
                """⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🏂 🏋️ 🤸 🚴 🏊 🧗 🏆 🥇 🥈 🥉 🏅 🎖️ 🎫 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎵 🎶 🎹
                🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎮 🕹️ 🧩 🎉 🎊 🎈 🎁 🎀""",
            ),
        ),
        Category(
            R.string.emoji_cat_objects,
            set(
                """🛒 ⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⏱️ ⏳ 🔋 🔌 💡 🔦 🕯️ 🧯 💸 💵 💳 💰 💎 🧾 🔧 🔨 🛠️ ⛏️ 🔩 ⚙️ 🧰
                🧲 🔬 🔭 🩺 💉 💊 🩹 🧬 🧹 🧺 🧻 🧼 🧽 🧴 🪒 🚿 🛁 🚽 🛏️ 🛋️ 🪑 🚪 🖌️ 🖍️ ✏️ ✒️ 🖊️ 📝 📄 📃 📑 📊 📈 📉 🗒️ 📅 📆 📇
                📋 📁 📂 🗂️ 📎 📌 📍 ✂️ 📏 📐 📕 📗 📘 📙 📖 📚 🔖 📧 ✉️ 📨 📦 📮 🗑️ 🔒 🔓 🔑 🗝️ 👕 👖 👗 👟 👠 🧢 👜 🎒 👓 🌂""",
            ),
        ),
        Category(
            R.string.emoji_cat_symbols,
            set(
                """✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ ❓ ❔ ❕ ❗ ‼️ ⁉️ 💯 🔅 🔆 ⚠️ 🚫 ⛔ 📵 🔞 ♻️ ⚜️ 🔱 ⭕ 🔴 🟠 🟡 🟢 🔵 🟣 🟤 ⚫ ⚪ 🟥 🟧 🟨 🟩 🟦 🟪
                🔶 🔷 🔸 🔹 🔺 🔻 💠 🔘 🔳 🔲 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁 🔂 ➡️ ⬅️ ⬆️ ⬇️ 🔄 🔃 🔔 🔕 📣 📢 💤 💬 💭 🆕 🆗 🆒 🆓 🆙""",
            ),
        ),
        Category(
            R.string.emoji_cat_flags,
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
