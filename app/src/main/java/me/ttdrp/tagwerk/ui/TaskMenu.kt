package me.ttdrp.tagwerk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Recurrence
import me.ttdrp.tagwerk.model.Task
import java.time.LocalDate

/** Was sich mit einer Aufgabe oder einem To-do anfangen lässt. Öffnet sich beim Tippen auf die Karte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMenu(
    task: Task,
    state: AppState,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onEdit: (Task) -> Unit,
    onBreakDown: (Task) -> Unit,
    onStart: (Task) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var acting by remember { mutableStateOf(false) }
    var askDeleteScope by remember { mutableStateOf(false) }
    var pickList by remember { mutableStateOf(false) }
    val isTodo = task.kind == Kind.TODO
    // Ein To-do liegt schon auf einer Liste, dorthin zu verschieben ergäbe nichts.
    val targets = state.lists.filterNot { isTodo && it.id == task.listId }

    // Erst zuschieben, dann handeln. Ein zweiter Tipp während der Animation darf nichts doppelt auslösen.
    val closeThen: (() -> Unit) -> Unit = { action ->
        if (!acting) {
            acting = true
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                onDismiss()
                action()
            }
        }
    }

    if (askDeleteScope) {
        ScopeDialog(
            title = "Löschen",
            allowThis = true,
            onDismiss = onDismiss,
            onPick = { deleteScope ->
                vm.deleteOccurrence(task.id, deleteScope)
                onDismiss()
            },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null,
    ) {
        Spacer(Modifier.height(20.dp))
        if (pickList) {
            targets.forEach { list ->
                MenuRow(Icons.Outlined.Checklist, list.name) { closeThen { vm.moveToList(task, list) } }
            }
        } else {
            MenuRow(Icons.Outlined.ContentCopy, "Kopie erstellen") { closeThen { vm.duplicateTask(task) } }
            if (targets.isNotEmpty()) {
                MenuRow(Icons.Outlined.Upload, "Zur Liste verschieben") { pickList = true }
            }
            MenuRow(Icons.Outlined.Event, if (isTodo) "Aufgabe planen" else "Aufgabe neu terminieren") {
                closeThen {
                    val current = task.epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth -> vm.rescheduleTask(task, LocalDate.of(year, month + 1, dayOfMonth)) },
                        current.year,
                        current.monthValue - 1,
                        current.dayOfMonth,
                    ).show()
                }
            }
            if (!isTodo) {
                MenuRow(Icons.Outlined.Today, "Für morgen neu planen") { closeThen { vm.rescheduleTask(task, LocalDate.now().plusDays(1)) } }
            }
            MenuRow(Icons.Outlined.AutoAwesome, "Aufschlüsselung vorschlagen") { closeThen { onBreakDown(task) } }
            if (!task.isDone) {
                MenuRow(Icons.Filled.PlayArrow, "Aufgabe starten") { closeThen { onStart(task) } }
            }
            MenuRow(Icons.Outlined.Edit, "Aufgabe bearbeiten") { closeThen { onEdit(task) } }
            MenuRow(Icons.Outlined.Delete, "Aufgabe löschen", MaterialTheme.colorScheme.error) {
                if (Recurrence.masterOf(state.tasks, task) != null) {
                    askDeleteScope = true
                } else {
                    closeThen { vm.deleteWithUndo(task) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Die App nennt Einträge im Plan "Aufgabe" und Einträge auf einer Liste "To-do". */
internal fun kindNoun(kind: Kind): String = if (kind == Kind.TODO) "To-do" else "Aufgabe"

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
