package com.itau.app.presentation.tasks

import com.itau.app.domain.model.Task

sealed interface TaskListIntent {
    data object LoadTasks : TaskListIntent
    data object RefreshTasks : TaskListIntent
    data object LoadMoreTasks : TaskListIntent
    data class CreateTask(val title: String, val description: String) : TaskListIntent
    data class ToggleTaskComplete(val task: Task) : TaskListIntent
    data class DeleteTask(val task: Task) : TaskListIntent
    data object RetrySync : TaskListIntent
    data object DismissError : TaskListIntent
    data object ShowCreateDialog : TaskListIntent
    data object HideCreateDialog : TaskListIntent
}
