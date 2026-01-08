package com.itau.app.domain.usecase

import com.itau.app.domain.model.Task
import com.itau.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class GetTasksUseCase(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.observeTasks()
}
