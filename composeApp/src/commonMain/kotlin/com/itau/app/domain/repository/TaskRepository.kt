package com.itau.app.domain.repository

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.Task
import kotlinx.coroutines.flow.Flow

data class PaginationState(
    val hasMore: Boolean = true,
    val offset: Int = 0,
    val total: Int = 0
)

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    suspend fun getTaskById(id: String): Result<Task>
    suspend fun createTask(title: String, description: String): Result<Task>
    suspend fun updateTask(task: Task): Result<Task>
    suspend fun deleteTask(id: String): Result<Unit>
    suspend fun refreshTasks(): Result<PaginationState>
    suspend fun loadMoreTasks(offset: Int): Result<PaginationState>
    suspend fun syncPendingTasks(): Result<Int>
    fun observePendingSyncCount(): Flow<Int>
}
