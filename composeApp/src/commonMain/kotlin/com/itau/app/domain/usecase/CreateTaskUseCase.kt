package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.Task
import com.itau.app.domain.repository.TaskRepository

class CreateTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(title: String, description: String): Result<Task> {
        return repository.createTask(title, description)
    }
}
