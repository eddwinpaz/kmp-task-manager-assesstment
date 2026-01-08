package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.repository.TaskRepository

class DeleteTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: String): Result<Unit> {
        return repository.deleteTask(taskId)
    }
}
