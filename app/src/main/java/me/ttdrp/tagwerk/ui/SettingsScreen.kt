package me.ttdrp.tagwerk.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ttdrp.tagwerk.BuildConfig
import me.ttdrp.tagwerk.ai.MODEL_OPTIONS
import me.ttdrp.tagwerk.model.TaskReminders

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier, onClose: () -> Unit) {
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val baseUrl by vm.baseUrl.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    var keyInput by remember { mutableStateOf("") }
    var urlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
    val uriHandler = LocalUriHandler.current
    val reminders by vm.reminderSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendar by vm.calendarUi.collectAsStateWithLifecycle()
    var calendarDenied by remember { mutableStateOf(false) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarDenied = !granted
        if (granted) vm.connectCalendar()
    }
    var notificationsOn by remember { mutableStateOf(true) }
    // Auch nach der Rückkehr aus den Android-Einstellungen neu prüfen.
    LifecycleResumeEffect(Unit) {
        notificationsOn = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        onPauseOrDispose {}
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(start = 25.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onClose)
            Text("Einstellungen", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 25.dp, end = 25.dp, bottom = 24.dp),
        ) {
            Label("Claude-API-Key")
            GroupCard {
                Text(
                    if (hasKey) "Ein Schlüssel ist verschlüsselt auf diesem Gerät gespeichert."
                    else "Noch kein Schlüssel hinterlegt. Ohne ihn funktioniert alles außer den KI-Funktionen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 17.dp, end = 17.dp, top = 16.dp, bottom = 6.dp),
                )
                TextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(if (hasKey) "Neuen Schlüssel eingeben" else "API-Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = plainFieldColors(),
                )
                GroupDivider()
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InversePill(
                        "Speichern",
                        null,
                        enabled = keyInput.isNotBlank(),
                        onClick = {
                            vm.saveApiKey(keyInput)
                            keyInput = ""
                            vm.notify("Schlüssel gespeichert")
                        },
                    )
                    if (hasKey) {
                        PillLabel(
                            if (busy) "Prüft…" else "Testen",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { if (!busy) vm.testKey() },
                        )
                        PillLabel(
                            "Löschen",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = {
                                vm.deleteApiKey()
                                vm.notify("Schlüssel gelöscht")
                            },
                        )
                    }
                }
                GroupDivider()
                SettingRow("Schlüssel bei Anthropic erstellen", { uriHandler.openUri("https://console.anthropic.com/settings/keys") }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Label("Modell")
            GroupCard {
                MODEL_OPTIONS.forEachIndexed { index, option ->
                    if (index > 0) GroupDivider()
                    SettingRow(option.label, { vm.setModel(option.id) }) {
                        if (model == option.id) {
                            Icon(Icons.Filled.Check, contentDescription = "Gewählt", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Text(
                "Die Kosten laufen über dein Anthropic-Konto und hängen vom Modell ab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )

            Label("Benachrichtigungen")
            if (!notificationsOn) {
                Text(
                    "Android blockiert die Meldungen von Tagwerk gerade.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
            GroupCard {
                SwitchRow("${TaskReminders.BEFORE_MINUTES} Minuten vor dem Start", reminders.before) {
                    vm.setReminderSettings(reminders.copy(before = it))
                }
                GroupDivider()
                SwitchRow("Zum Start", reminders.atStart) { vm.setReminderSettings(reminders.copy(atStart = it)) }
                GroupDivider()
                SettingRow(
                    if (notificationsOn) "Ton und Anzeige in Android" else "In Android einschalten",
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(
                "Gilt für Aufgaben mit fester Uhrzeit. Der Fokus-Timer meldet sich immer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )

            Label("Kalender")
            GroupCard {
                if (calendar.connected && calendar.permitted) {
                    if (calendar.calendars.isEmpty()) {
                        Text(
                            "Auf diesem Gerät ist noch kein Kalender eingerichtet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(17.dp),
                        )
                    }
                    calendar.calendars.forEach { item ->
                        SwitchRow(
                            item.name,
                            item.id !in calendar.hidden,
                            detail = item.account.takeIf { it != item.name },
                            dot = Color(item.color or 0xFF000000.toInt()),
                        ) { vm.setCalendarVisible(item.id, it) }
                        GroupDivider()
                    }
                    SettingRow("Kalender trennen", { vm.disconnectCalendar() }) {}
                } else {
                    SettingRow(
                        "Kalender verbinden",
                        {
                            if (calendar.permitted) vm.connectCalendar() else askCalendar.launch(Manifest.permission.READ_CALENDAR)
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                    if (calendarDenied) {
                        GroupDivider()
                        SettingRow(
                            "In Android erlauben",
                            {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                )
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Text(
                "Zeigt Termine aus den Kalendern dieses Geräts im Plan, zum Beispiel aus deinem Google-Konto. " +
                    "Termine werden nur gelesen, nicht gespeichert und nicht an die KI geschickt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            )

            Label("App-Einstellungen")
            GroupCard {
                SwitchRow("Töne", feedback.sound) { vm.setFeedback(feedback.copy(sound = it)) }
                GroupDivider()
                SwitchRow("Feuerwerk", feedback.motion) { vm.setFeedback(feedback.copy(motion = it)) }
                GroupDivider()
                SwitchRow("Haptisches Feedback", feedback.haptics) { vm.setFeedback(feedback.copy(haptics = it)) }
            }

            Label("Deine Daten")
            GroupCard {
                Text(
                    "Aufgaben bleiben auf diesem Gerät. Es gibt kein Konto, keine Werbung und kein Tracking. " +
                        "Nur wenn du eine KI-Funktion nutzt, gehen deine Nachricht und die dafür nötigen Aufgabentitel " +
                        "direkt von diesem Gerät an Anthropic.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(17.dp),
                )
            }

            if (BuildConfig.DEBUG) {
                Label("Nur für Entwicklung")
                GroupCard {
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("API-Basis-URL (leer = Anthropic)") },
                        colors = plainFieldColors(),
                    )
                    GroupDivider()
                    SettingRow("URL übernehmen", { vm.setBaseUrl(urlInput) }) {}
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    SectionTitle(text, Modifier.padding(start = 12.dp, top = 6.dp))
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        trailing()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    detail: String? = null,
    dot: Color? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 17.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.padding(end = 12.dp).size(14.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
