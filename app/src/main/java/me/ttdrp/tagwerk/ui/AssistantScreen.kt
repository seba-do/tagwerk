package me.ttdrp.tagwerk.ui

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.str
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.ttdrp.tagwerk.ai.Proposals
import me.ttdrp.tagwerk.model.EmojiGuess
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(vm: AppViewModel, modifier: Modifier, onOpenSettings: () -> Unit) {
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it }
    }

    LaunchedEffect(chat.size, busy) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size)
    }

    val keyboardOpen = WindowInsets.isImeVisible
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(start = 16.dp, top = 22.dp, bottom = 16.dp),
        ) {
            AssistantMenu(canClear = chat.isNotEmpty(), onClear = { vm.clearChat() }, onOpenSettings = onOpenSettings)
        }

        if (chat.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                AiOrb(150.dp, glow = true)
                Text(
                    str(if (hasKey) R.string.assistant_intro else R.string.assistant_no_key_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp, bottom = 20.dp),
                )
                if (!hasKey) {
                    Text(
                        str(R.string.assistant_no_key_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    InversePill(str(R.string.assistant_add_key), null, onOpenSettings, Modifier.padding(top = 14.dp, bottom = 20.dp))
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(chat, key = { it.id }) { entry -> ChatBubble(entry, vm) }
                item {
                    if (busy) {
                        PulsingRows(2, Modifier.fillMaxWidth(0.7f).padding(vertical = 10.dp))
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(26.dp))
                .padding(end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = hasKey,
                placeholder = { Text(str(R.string.assistant_placeholder)) },
                maxLines = 4,
                colors = plainFieldColors(),
            )
            if (input.isBlank()) {
                InversePill(
                    text = str(R.string.assistant_speak),
                    icon = Icons.Outlined.Mic,
                    enabled = hasKey,
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                        try {
                            speech.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            vm.notify(str(R.string.assistant_no_voice))
                        }
                    },
                )
            } else {
                InversePill(
                    text = str(R.string.assistant_send),
                    icon = Icons.AutoMirrored.Filled.Send,
                    enabled = hasKey && !busy,
                    onClick = {
                        vm.sendChat(input)
                        input = ""
                    },
                )
            }
        }
        Spacer(Modifier.size(if (keyboardOpen) 0.dp else navClearance()))
    }
}

@Composable
private fun AssistantMenu(canClear: Boolean, onClear: () -> Unit, onOpenSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RoundIconButton(Icons.Filled.Menu, str(R.string.menu), onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (canClear) {
                DropdownMenuItem(
                    text = { Text(str(R.string.assistant_clear), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                    onClick = {
                        onClear()
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(str(R.string.settings), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                onClick = {
                    onOpenSettings()
                    open = false
                },
            )
        }
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry, vm: AppViewModel) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (entry.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(horizontalAlignment = if (entry.fromUser) Alignment.End else Alignment.Start) {
            if (entry.text.isNotEmpty()) {
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = if (entry.fromUser) {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    },
                )
            }
            if (entry.proposals.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CardShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entry.proposals.forEach { p ->
                        val where = Proposals.placeLabel(p, LocalDate.now())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EmojiBadge(EmojiGuess.forTitle(p.title), EmojiGuess.tintFor(p.title))
                            Column(Modifier.padding(start = 9.dp)) {
                                Text(p.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${formatMinutes(p.minutes)} · $where",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        p.subtasks.forEach { sub ->
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 41.dp),
                            )
                        }
                    }
                    when (entry.decision) {
                        null -> Row(
                            Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InversePill(str(R.string.assistant_accept), null, { vm.decide(entry, true) })
                            PillLabel(str(R.string.assistant_reject), container = MaterialTheme.colorScheme.surfaceVariant, onClick = { vm.decide(entry, false) })
                        }
                        true -> Text(str(R.string.assistant_accepted), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        false -> Text(str(R.string.assistant_rejected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
