package com.itau.app.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itau.app.data.network.ConnectivityMonitor
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task
import com.itau.app.domain.usecase.CreateTaskUseCase
import com.itau.app.domain.usecase.DeleteTaskUseCase
import com.itau.app.domain.usecase.GetTasksUseCase
import com.itau.app.domain.usecase.LoadMoreTasksUseCase
import com.itau.app.domain.usecase.RefreshTasksUseCase
import com.itau.app.domain.usecase.SyncTasksUseCase
import com.itau.app.domain.usecase.ToggleTaskCompletionUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TaskListViewModel(
    private val getTasksUseCase: GetTasksUseCase,
    private val refreshTasksUseCase: RefreshTasksUseCase,
    private val loadMoreTasksUseCase: LoadMoreTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val toggleTaskCompletionUseCase: ToggleTaskCompletionUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val syncTasksUseCase: SyncTasksUseCase,
    private val connectivityMonitor: ConnectivityMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(TaskListState())
    val state: StateFlow<TaskListState> = _state.asStateFlow()

    companion object {
        private const val BACKGROUND_SYNC_INTERVAL_MS = 30_000L // 30 seconds
    }

    init {
        observeTasks()
        observeConnectivity()
        startBackgroundSync()
    }

    private fun startBackgroundSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(BACKGROUND_SYNC_INTERVAL_MS)
                if (!_state.value.isOffline && _state.value.pendingSyncCount > 0 && !_state.value.isSyncing) {
                    syncPendingTasksQuietly()
                }
            }
        }
    }

    private fun syncPendingTasksQuietly() {
        viewModelScope.launch {
            if (_state.value.isSyncing) return@launch
            _state.update { it.copy(isSyncing = true) }
            syncTasksUseCase()
                .onSuccess { _state.update { it.copy(isSyncing = false) } }
                .onError { _state.update { it.copy(isSyncing = false) } }
        }
    }

    fun processIntent(intent: TaskListIntent) {
        when (intent) {
            is TaskListIntent.LoadTasks -> loadTasks()
            is TaskListIntent.RefreshTasks -> refreshTasks()
            is TaskListIntent.LoadMoreTasks -> loadMoreTasks()
            is TaskListIntent.CreateTask -> createTask(intent.title, intent.description)
            is TaskListIntent.ToggleTaskComplete -> toggleTaskComplete(intent.task)
            is TaskListIntent.DeleteTask -> deleteTask(intent.task)
            is TaskListIntent.RetrySync -> syncPendingTasks()
            is TaskListIntent.DismissError -> dismissError()
            is TaskListIntent.ShowCreateDialog -> showCreateDialog()
            is TaskListIntent.HideCreateDialog -> hideCreateDialog()
        }
    }

    private fun observeTasks() {
        getTasksUseCase()
            .onStart { _state.update { it.copy(isLoading = true) } }
            .onEach { tasks ->
                _state.update {
                    it.copy(
                        tasks = tasks,
                        isLoading = false,
                        pendingSyncCount = tasks.count { task ->
                            task.syncStatus != SyncStatus.SYNCED
                        }
                    )
                }
            }
            .catch { e ->
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectivity() {
        connectivityMonitor.observeConnectivity()
            .distinctUntilChanged()
            .onEach { isConnected ->
                _state.update { it.copy(isOffline = !isConnected) }
                if (isConnected && _state.value.pendingSyncCount > 0) {
                    syncPendingTasks()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun showCreateDialog() {
        _state.update { it.copy(showCreateDialog = true) }
    }

    private fun hideCreateDialog() {
        _state.update { it.copy(showCreateDialog = false) }
    }

    private fun createTask(title: String, description: String) {
        if (title.isBlank()) {
            _state.update { it.copy(error = "Title cannot be empty") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isCreatingTask = true, showCreateDialog = false) }
            createTaskUseCase(title.trim(), description.trim())
                .onSuccess {
                    _state.update { it.copy(isCreatingTask = false) }
                    // Trigger background sync if connected
                    if (!_state.value.isOffline) {
                        syncPendingTasksQuietly()
                    }
                }
                .onError { e ->
                    _state.update { it.copy(error = e.message, isCreatingTask = false) }
                }
        }
    }

    private fun loadTasks() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            refreshTasksUseCase()
                .onSuccess { paginationState ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasMoreTasks = paginationState.hasMore,
                            currentOffset = paginationState.offset
                        )
                    }
                }
                .onError { e -> _state.update { it.copy(error = e.message, isLoading = false) } }
        }
    }

    private fun refreshTasks() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null, currentOffset = 0) }
            refreshTasksUseCase()
                .onSuccess { paginationState ->
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            hasMoreTasks = paginationState.hasMore,
                            currentOffset = paginationState.offset
                        )
                    }
                }
                .onError { e ->
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            error = if (it.isOffline) null else e.message
                        )
                    }
                }
        }
    }

    private fun loadMoreTasks() {
        val currentState = _state.value
        if (currentState.isLoadingMore || !currentState.hasMoreTasks || currentState.isOffline) {
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            loadMoreTasksUseCase(currentState.currentOffset)
                .onSuccess { paginationState ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMoreTasks = paginationState.hasMore,
                            currentOffset = paginationState.offset
                        )
                    }
                }
                .onError { e ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            error = e.message
                        )
                    }
                }
        }
    }

    private fun toggleTaskComplete(task: Task) {
        viewModelScope.launch {
            toggleTaskCompletionUseCase(task)
                .onSuccess {
                    // Trigger background sync if connected
                    if (!_state.value.isOffline) {
                        syncPendingTasksQuietly()
                    }
                }
                .onError { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    private fun deleteTask(task: Task) {
        viewModelScope.launch {
            deleteTaskUseCase(task.id)
                .onSuccess {
                    // Trigger background sync if connected
                    if (!_state.value.isOffline) {
                        syncPendingTasksQuietly()
                    }
                }
                .onError { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    private fun syncPendingTasks() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true) }
            syncTasksUseCase()
                .onSuccess { _state.update { it.copy(isSyncing = false) } }
                .onError { e -> _state.update { it.copy(error = e.message, isSyncing = false) } }
        }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
