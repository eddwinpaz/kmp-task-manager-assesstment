package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.repository.PaginationState
import com.itau.app.domain.repository.TaskRepository

class RefreshTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): Result<PaginationState> = repository.refreshTasks()
}
