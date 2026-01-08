package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.repository.TaskRepository

class SyncTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): Result<Int> = repository.syncPendingTasks()
}
