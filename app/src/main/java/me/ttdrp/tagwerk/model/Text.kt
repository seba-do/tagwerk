package me.ttdrp.tagwerk.model

import androidx.annotation.StringRes

/**
 * Texts for code outside of Compose, such as the model, alarms and widgets. The app points [resolver] at its
 * resources on start, so the device language picks English or German. Unit tests install a resolver that reads
 * the strings.xml files directly.
 */
object Text {
    fun interface Resolver {
        fun get(@StringRes id: Int, args: Array<out Any>): String
    }

    @Volatile
    var resolver: Resolver = Resolver { id, _ -> "#$id" }
}

fun str(@StringRes id: Int, vararg args: Any): String = Text.resolver.get(id, args)
