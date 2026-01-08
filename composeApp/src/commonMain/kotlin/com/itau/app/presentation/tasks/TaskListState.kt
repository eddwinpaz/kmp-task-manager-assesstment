package com.itau.app.presentation.tasks

import com.itau.app.domain.model.Task

data class TaskListState(
    val tasks: List<Task> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isCreatingTask: Boolean = false,
    val isSyncing: Boolean = false,
    val isOffline: Boolean = false,
    val pendingSyncCount: Int = 0,
    val showCreateDialog: Boolean = false,
    val hasMoreTasks: Boolean = true,
    val currentOffset: Int = 0,
    val error: String? = null
)
