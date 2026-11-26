package me.ttdrp.tagwerk.ui

import me.ttdrp.tagwerk.R
import me.ttdrp.tagwerk.model.str
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import me.ttdrp.tagwerk.model.ListSort
import me.ttdrp.tagwerk.model.TodoList
import me.ttdrp.tagwerk.model.sortTasks
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import me.ttdrp.tagwerk.ai.SuggestedItem
import me.ttdrp.tagwerk.model.AppState
import me.ttdrp.tagwerk.model.EmojiGuess
import me.ttdrp.tagwerk.model.Grouping
import me.ttdrp.tagwerk.model.Kind
import me.ttdrp.tagwerk.model.Priority
import me.ttdrp.tagwerk.model.Quadrant
import me.ttdrp.tagwerk.model.Reorder
import me.ttdrp.tagwerk.model.isDraggableGrouping
import me.ttdrp.tagwerk.model.Task
import me.ttdrp.tagwerk.model.groupTasks

@Composable
fun TodoScreen(
    state: AppState,
    vm: AppViewModel,
    modifier: Modifier,
    onEdit: (Task) -> Unit,
    onBreakDown: (Task) -> Unit,
    onNew: (Task) -> Unit,
    onStart: (Task) -> Unit,
) {
    var menuFor by remember { mutableStateOf<Task?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var groupingName by rememberSaveable { mutableStateOf(Grouping.NONE.name) }
    var askNewList by remember { mutableStateOf(false) }
    var askEditLists by remember { mutableStateOf(false) }
    var listToDelete by remember { mutableStateOf<TodoList?>(null) }
    var sortName by rememberSaveable { mutableStateOf(ListSort.MANUAL.name) }
    var showCompleted by rememberSaveable { mutableStateOf(true) }
    var showIdeas by rememberSaveable { mutableStateOf(true) }
    var suggestions by remember { mutableStateOf<List<SuggestedItem>>(emptyList()) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val grouping = Grouping.valueOf(groupingName)
    val sort = ListSort.valueOf(sortName)
    val list = state.lists.firstOrNull { it.id == selectedId } ?: state.lists.firstOrNull()
    val listTasks = state.tasks.filter { it.kind == Kind.TODO && it.listId == list?.id }
    val open = listTasks.filterNot { it.isDone }
    val done = listTasks.filter { it.isDone }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val feedback = LocalFeedback.current
    val listState = rememberLazyListState()
    val sections = groupTasks(sortTasks(open, sort), grouping)
    val collapsed = rememberCollapsedSections()
    // In einer berechneten Sortierung hätte der Platz, an den man zieht, keine Bedeutung.
    val droppable = isDraggableGrouping(grouping) && sort == ListSort.MANUAL
    val reorder = rememberReorderController(listState) { key ->
        val id = key as? String ?: return@rememberReorderController null
        val headerOf = { title: String ->
            val section = sections.firstOrNull { it.title == title }
            val empty = droppable && grouping != Grouping.NONE && (section?.tasks?.isEmpty() == true || title in collapsed)
            Reorder.Row(0, 0, null, null, zoneKey = if (empty) "empty-$title" else null, group = title)
        }
        when {
            id == DONE_ZONE -> Reorder.Row(0, 0, null, null, zoneKey = "done", completes = true)
            id.startsWith("h-") || id.startsWith("e-") -> if (grouping == Grouping.NONE) null else headerOf(id.drop(2))
            else -> sections.firstOrNull { s -> s.tasks.any { it.id == id } }?.let { section ->
                Reorder.Row(0, 0, id, null, group = section.title.ifEmpty { null })
            }
        }
    }
    val fetchIdeas = {
        list?.let { current -> vm.suggestForList(current.name, open.map { it.title }) { suggestions = it } }
        Unit
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 16.dp, top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            ListMenu(
                hasLists = state.lists.isNotEmpty(),
                grouping = grouping,
                sort = sort,
                showCompleted = showCompleted,
                showIdeas = showIdeas,
                onNewList = { askNewList = true },
                onEditLists = { askEditLists = true },
                onGrouping = { groupingName = it.name },
                onSort = { sortName = it.name },
                onShowCompleted = { showCompleted = it },
                onShowIdeas = { showIdeas = it },
            )
            RoundIconButton(
                Icons.Filled.Add,
                str(R.string.new_todo),
                onClick = { onNew(Task(title = "", kind = Kind.TODO, listId = list?.id)) },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.lists.forEach { item ->
                val selected = item.id == list?.id
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(150),
                    label = "list",
                )
                Text(
                    item.name,
                    style = if (selected) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    color = color,
                    modifier = Modifier
                        .alignByBaseline()
                        .clickable {
                            selectedId = item.id
                            suggestions = emptyList()
                        },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = listState,
                contentPadding = PaddingValues(bottom = navClearance()),
            ) {
                sections.forEach { section ->
                    val isCollapsed = section.title in collapsed
                    val newInGroup = {
                        onNew(
                            Task(
                                title = "",
                                kind = Kind.TODO,
                                listId = list?.id,
                                priority = Priority.entries.firstOrNull { it.label == section.title } ?: Priority.MEDIUM,
                                quadrant = Quadrant.entries.firstOrNull { it.label == section.title } ?: Task(title = "").quadrant,
                            ),
                        )
                    }
                    item(key = "h-${section.title}") {
                        GroupHeader(
                            title = section.title,
                            count = section.tasks.size,
                            expanded = !isCollapsed,
                            onToggle = { collapsed.toggle(section.title) },
                            highlighted = isCollapsed && reorder.slot?.zoneKey == "empty-${section.title}",
                            onAdd = newInGroup.takeIf { section.tasks.isNotEmpty() },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (isCollapsed) return@forEach
                    if (section.tasks.isEmpty()) {
                        item(key = "e-${section.title}") {
                            DashedCard(
                                emptyTextFor(section.title),
                                onClick = newInGroup,
                                modifier = Modifier.animateItem(),
                                highlighted = reorder.slot?.zoneKey == "empty-${section.title}",
                            )
                        }
                    }
                    items(section.tasks, key = { it.id }) { task ->
                        SwipeToDelete(
                            task = task,
                            onDelete = { vm.deleteWithUndo(task) },
                            modifier = Modifier
                                .animateItem()
                                .then(
                                    reorderHandle(reorder, task.id, enabled = droppable) { slot, finger ->
                                        if (slot.completes) {
                                            feedback.taskDone(finger)
                                            vm.toggleDone(task)
                                        } else {
                                            vm.moveTaskToGroup(task.id, slot.beforeTaskId, grouping, slot.group)
                                        }
                                    },
                                ),
                        ) {
                            TaskCard(
                                task = task,
                                onClick = { menuFor = task },
                                onToggle = { vm.toggleDone(task) },
                                onToggleStep = { vm.toggleSubtask(task, it) },
                            )
                        }
                    }
                }

                if (reorder.draggedId != null) {
                    item(key = DONE_ZONE) {
                        DoneDropZone(highlighted = reorder.slot?.completes == true, modifier = Modifier.animateItem())
                    }
                } else if (showIdeas) {
                    item(key = "ideas-head") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 26.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    str(R.string.ideas_title),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.titleSmall.fontFamily),
                                    fontWeight = MaterialTheme.typography.titleSmall.fontWeight,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    str(R.string.ideas_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !busy && list != null, role = Role.Button, onClick = fetchIdeas)
                                    .semantics { contentDescription = str(R.string.cd_new_ideas) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Replay, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                    if (busy) {
                        item(key = "ideas-busy") { PulsingRows(3, Modifier.fillMaxWidth().padding(12.dp)) }
                    } else if (suggestions.isEmpty()) {
                        item(key = "ideas-empty") { DashedCard(str(R.string.get_ideas), onClick = fetchIdeas) }
                    }
                    items(suggestions, key = { "idea-${it.title}" }) { idea ->
                        SuggestionRow(
                            idea = idea,
                            modifier = Modifier.animateItem(),
                            onAdd = {
                                vm.saveTask(
                                    Task(
                                        title = idea.title,
                                        emoji = EmojiGuess.forTitle(idea.title),
                                        tint = EmojiGuess.tintFor(idea.title),
                                        kind = Kind.TODO,
                                        listId = list?.id,
                                        minutes = idea.minutes ?: 30,
                                    ),
                                )
                                suggestions = suggestions - idea
                            },
                        )
                    }
                }

                if (showCompleted && done.isNotEmpty() && reorder.draggedId == null) {
                    item(key = "done-h") {
                        DoneHeader(done.size, showDone, onToggle = { showDone = !showDone }, modifier = Modifier.animateItem())
                    }
                    if (showDone) {
                        items(done, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                onClick = { menuFor = task },
                                onToggle = { vm.toggleDone(task) },
                                onToggleStep = { vm.toggleSubtask(task, it) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
            DragLayer(reorder) { id ->
                open.firstOrNull { it.id == id }?.let { TaskCard(task = it, onClick = {}, onToggle = {}) }
            }
        }
    }

    menuFor?.let { task ->
        TaskMenu(
            task = task,
            state = state,
            vm = vm,
            onDismiss = { menuFor = null },
            onEdit = onEdit,
            onBreakDown = onBreakDown,
            onStart = onStart,
        )
    }

    if (askNewList) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askNewList = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(str(R.string.new_list)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(str(R.string.name)) })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.addList(name)
                    askNewList = false
                }) { Text(str(R.string.action_create)) }
            },
            dismissButton = { TextButton(onClick = { askNewList = false }) { Text(str(R.string.action_cancel)) } },
        )
    }
    if (askEditLists) {
        EditListsDialog(
            lists = state.lists,
            onDismiss = { askEditLists = false },
            onRename = vm::renameList,
            onDelete = { listToDelete = it },
        )
    }
    listToDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(str(R.string.delete_list_title, doomed.name)) },
            text = { Text(str(R.string.delete_list_text)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteList(doomed.id)
                    if (selectedId == doomed.id) selectedId = null
                    listToDelete = null
                }) { Text(str(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { listToDelete = null }) { Text(str(R.string.keep)) } },
        )
    }
}

/** Umbenennen wird erst mit "Fertig" übernommen, Löschen fragt sofort nach. */
@Composable
private fun EditListsDialog(
    lists: List<TodoList>,
    onDismiss: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (TodoList) -> Unit,
) {
    val names = remember { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(str(R.string.edit_lists)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lists.forEach { list ->
                    OutlinedTextField(
                        value = names[list.id] ?: list.name,
                        onValueChange = { names[list.id] = it },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { onDelete(list) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = str(R.string.cd_delete_named, list.name), tint = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = lists.all { (names[it.id] ?: it.name).isNotBlank() },
                onClick = {
                    lists.forEach { list ->
                        names[list.id]?.takeIf { it.trim() != list.name }?.let { onRename(list.id, it) }
                    }
                    onDismiss()
                },
            ) { Text(str(R.string.focus_finish)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.action_cancel)) } },
    )
}

private fun emptyTextFor(section: String): String = when (section) {
    "" -> str(R.string.empty_list)
    Priority.LOW.label -> str(R.string.empty_low)
    Priority.HIGH.label -> str(R.string.empty_high)
    else -> str(R.string.empty_group)
}

/** Wichtigkeit ist auch ohne Lesen zu erkennen: an Farbe und Pfeil. */
@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    highlighted: Boolean,
    onAdd: (() -> Unit)?,
    modifier: Modifier,
) {
    val (container, leading) = when (title) {
        Priority.HIGH.label -> Color(0xFF7A3B47) to Icons.Filled.ArrowDropUp
        Priority.LOW.label -> Color(0xFF47585E) to Icons.Filled.ArrowDropDown
        else -> MaterialTheme.colorScheme.surfaceContainer to null
    }
    SectionHeader(
        label = title.ifEmpty { str(R.string.todo) },
        count = count,
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
        leading = leading,
        container = container,
        content = if (leading != null) Color.White else MaterialTheme.colorScheme.onBackground,
        highlighted = highlighted,
        onAdd = onAdd,
    )
}

@Composable
private fun SuggestionRow(idea: SuggestedItem, modifier: Modifier, onAdd: () -> Unit) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.surfaceContainer, CardShape)
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiBadge(EmojiGuess.forTitle(idea.title), EmojiGuess.tintFor(idea.title))
            Text(
                idea.title,
                modifier = Modifier.padding(start = 9.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onAdd)
                .semantics { contentDescription = str(R.string.cd_accept_idea, idea.title) },
            contentAlignment = Alignment.Center,
        ) {
            SmallPlus(MaterialTheme.colorScheme.primary)
        }
    }
}

private enum class MenuSection { GROUP, SORT, VIEW }

private fun groupingIcon(grouping: Grouping): ImageVector = when (grouping) {
    Grouping.NONE -> Icons.Outlined.Menu
    Grouping.PRIORITY -> Icons.Outlined.Flag
    Grouping.SIZE -> Icons.Outlined.Schedule
    Grouping.QUADRANT -> Icons.Outlined.GridView
}

private fun sortIcon(sort: ListSort): ImageVector = when (sort) {
    ListSort.MANUAL -> Icons.AutoMirrored.Outlined.Sort
    ListSort.ALPHABETICAL -> Icons.Outlined.SortByAlpha
    ListSort.DURATION -> Icons.Outlined.Schedule
}

@Composable
private fun ListMenu(
    hasLists: Boolean,
    grouping: Grouping,
    sort: ListSort,
    showCompleted: Boolean,
    showIdeas: Boolean,
    onNewList: () -> Unit,
    onEditLists: () -> Unit,
    onGrouping: (Grouping) -> Unit,
    onSort: (ListSort) -> Unit,
    onShowCompleted: (Boolean) -> Unit,
    onShowIdeas: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<MenuSection?>(null) }
    val close = {
        open = false
        section = null
    }
    val toggle = { target: MenuSection -> section = target.takeUnless { it == section } }
    val divider = @Composable { HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline) }
    Box {
        RoundIconButton(Icons.Filled.MoreVert, str(R.string.cd_list_menu), onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = close,
            modifier = Modifier.width(220.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            DropdownMenuItem(
                text = { Text(str(R.string.create_list), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(Icons.Outlined.AddCircleOutline, contentDescription = null) },
                onClick = {
                    onNewList()
                    close()
                },
            )
            if (hasLists) {
                DropdownMenuItem(
                    text = { Text(str(R.string.edit_lists), style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        onEditLists()
                        close()
                    },
                )
            }
            divider()
            SectionItem(str(R.string.group_by), section == MenuSection.GROUP) { toggle(MenuSection.GROUP) }
            if (section == MenuSection.GROUP) {
                Grouping.entries.forEach { option ->
                    OptionItem(option.label, groupingIcon(option), option == grouping) {
                        onGrouping(option)
                        close()
                    }
                }
            }
            divider()
            SectionItem(str(R.string.sort_by), section == MenuSection.SORT) { toggle(MenuSection.SORT) }
            if (section == MenuSection.SORT) {
                ListSort.entries.forEach { option ->
                    OptionItem(option.label, sortIcon(option), option == sort) {
                        onSort(option)
                        close()
                    }
                }
            }
            divider()
            SectionItem(str(R.string.view), section == MenuSection.VIEW) { toggle(MenuSection.VIEW) }
            if (section == MenuSection.VIEW) {
                OptionItem(str(R.string.show_done_todos), null, showCompleted) { onShowCompleted(!showCompleted) }
                OptionItem(str(R.string.show_ideas), null, showIdeas) { onShowIdeas(!showIdeas) }
            }
        }
    }
}

@Composable
private fun SectionItem(label: String, expanded: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingIcon = {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun OptionItem(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp)) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = str(R.string.cd_selected))
                }
                if (icon != null) Icon(icon, contentDescription = null)
            }
        },
        onClick = onClick,
    )
}

/** Nach links wischen legt das Löschen frei, an der Schwelle gibt es Haptik, und "Rückgängig" holt die Aufgabe zurück. */
@Composable
private fun SwipeToDelete(task: Task, onDelete: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val feedback = LocalFeedback.current
    // Bewusst nicht gespeichert: Sonst käme eine zurückgeholte Aufgabe als "gelöscht" wieder und verschwände sofort.
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }
            .drop(1)
            .collect { if (it == SwipeToDismissBoxValue.EndToStart) feedback.threshold() }
    }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                if (armed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                tween(120),
                label = "swipe",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(color, CardShape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = str(R.string.cd_delete_task, task.title))
            }
        },
    ) { content() }
}
