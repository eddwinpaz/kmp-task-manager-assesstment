package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.repository.PaginationState
import com.itau.app.domain.repository.TaskRepository

class LoadMoreTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(offset: Int): Result<PaginationState> = repository.loadMoreTasks(offset)
}
