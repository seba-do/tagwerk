package me.ttdrp.tagwerk

import android.app.Application
import me.ttdrp.tagwerk.model.Text

class TagwerkApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Always read through the current resources, so a language change applies without a restart.
        Text.resolver = Text.Resolver { id, args -> if (args.isEmpty()) getString(id) else getString(id, *args) }
    }
}
