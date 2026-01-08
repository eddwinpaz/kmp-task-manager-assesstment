package com.itau.app.presentation.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel,
    onLogout: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.processIntent(TaskListIntent.LoadTasks)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tareas")
                        if (state.pendingSyncCount > 0) {
                            Text(
                                text = "${state.pendingSyncCount} pendientes de sincronizar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Sync indicator
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (state.pendingSyncCount > 0 && !state.isOffline) {
                        IconButton(
                            onClick = { viewModel.processIntent(TaskListIntent.RetrySync) }
                        ) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) {
                                Text("${state.pendingSyncCount}")
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.processIntent(TaskListIntent.RefreshTasks) },
                        enabled = !state.isRefreshing && !state.isSyncing
                    ) {
                        Text("↻", style = MaterialTheme.typography.titleLarge)
                    }
                    // Logout button
                    if (onLogout != null) {
                        IconButton(onClick = onLogout) {
                            Text("⏻", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Offline Banner
                AnimatedVisibility(
                    visible = state.isOffline,
                    enter = slideInVertically(),
                    exit = slideOutVertically()
                ) {
                    OfflineBanner(
                        pendingCount = state.pendingSyncCount,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                when {
                    state.isLoading && state.tasks.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    state.tasks.isEmpty() && !state.isLoading -> {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    }
                    else -> {
                        val listState = rememberLazyListState()

                        // Detect when we need to load more
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                totalItems > 0 && lastVisibleItem >= totalItems - 3
                            }
                        }

                        // Trigger load more when conditions are met
                        LaunchedEffect(shouldLoadMore, state.hasMoreTasks, state.isLoadingMore) {
                            if (shouldLoadMore && state.hasMoreTasks && !state.isLoadingMore && !state.isOffline) {
                                viewModel.processIntent(TaskListIntent.LoadMoreTasks)
                            }
                        }

                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.processIntent(TaskListIntent.RefreshTasks) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White)
                            ) {
                                items(
                                    items = state.tasks,
                                    key = { it.id }
                                ) { task ->
                                    TaskItemWithSwipe(
                                        task = task,
                                        onToggleComplete = {
                                            viewModel.processIntent(TaskListIntent.ToggleTaskComplete(task))
                                        },
                                        onDelete = {
                                            viewModel.processIntent(TaskListIntent.DeleteTask(task))
                                        }
                                    )
                                }

                                // Loading indicator at bottom
                                if (state.isLoadingMore) {
                                    item(key = "loading_more") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error Snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.processIntent(TaskListIntent.DismissError) }) {
                            Text("Cerrar")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // FAB for creating new task
            FloatingActionButton(
                onClick = { viewModel.processIntent(TaskListIntent.ShowCreateDialog) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    // Create Task Dialog
    if (state.showCreateDialog) {
        CreateTaskDialog(
            onDismiss = { viewModel.processIntent(TaskListIntent.HideCreateDialog) },
            onConfirm = { title, description ->
                viewModel.processIntent(TaskListIntent.CreateTask(title, description))
            }
        )
    }
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nueva Tarea") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (opcional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, description) },
                enabled = title.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun OfflineBanner(pendingCount: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚠",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (pendingCount > 0) {
                    "Modo sin conexión - $pendingCount cambios pendientes"
                } else {
                    "Modo sin conexión - Los cambios se sincronizarán al conectar"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskItemWithSwipe(
    task: Task,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    // Reset dismiss state when task changes
    LaunchedEffect(task.id) {
        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "🗑",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        TaskItem(task = task, onToggleComplete = onToggleComplete)
    }
}

@Composable
private fun TaskItem(task: Task, onToggleComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (task.isCompleted) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                } else {
                    Color.White
                }
            )
            .clickable { onToggleComplete() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (task.isCompleted) 0.5f else 0.7f
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Sync status indicator
            if (task.syncStatus != SyncStatus.SYNCED) {
                Spacer(modifier = Modifier.width(8.dp))
                SyncStatusIndicator(task.syncStatus)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SyncStatusIndicator(status: SyncStatus) {
    val color = when (status) {
        SyncStatus.PENDING_CREATE -> Color(0xFF4CAF50) // Green
        SyncStatus.PENDING_UPDATE -> Color(0xFFFFC107) // Amber
        SyncStatus.PENDING_DELETE -> Color(0xFFF44336) // Red
        SyncStatus.SYNCED -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, shape = CircleShape)
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No hay tareas",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Desliza hacia abajo para actualizar o agrega una nueva tarea",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
