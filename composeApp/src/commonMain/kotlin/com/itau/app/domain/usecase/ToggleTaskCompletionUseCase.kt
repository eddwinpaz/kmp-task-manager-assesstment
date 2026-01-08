package com.itau.app.domain.usecase

import com.itau.app.core.util.currentTimeMillis
import com.itau.app.domain.model.Result
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task
import com.itau.app.domain.repository.TaskRepository

class ToggleTaskCompletionUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task): Result<Task> {
        val updatedTask = task.copy(
            isCompleted = !task.isCompleted,
            updatedAt = currentTimeMillis(),
            syncStatus = if (task.syncStatus == SyncStatus.PENDING_CREATE) {
                SyncStatus.PENDING_CREATE
            } else {
                SyncStatus.PENDING_UPDATE
            }
        )
        return repository.updateTask(updatedTask)
    }
}
