package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task
import com.itau.app.domain.repository.PaginationState
import com.itau.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncTasksUseCaseTest {

    @Test
    fun `invoke should return synced count on success`() = runTest {
        val fakeRepository = object : TaskRepository {
            override fun observeTasks() = flowOf(emptyList<Task>())
            override suspend fun getTaskById(id: String) = Result.Error(Exception("Not found"))
            override suspend fun createTask(title: String, description: String) = Result.Error(Exception("Not implemented"))
            override suspend fun updateTask(task: Task) = Result.Success(task)
            override suspend fun deleteTask(id: String) = Result.Success(Unit)
            override suspend fun refreshTasks() = Result.Success(PaginationState(hasMore = false, offset = 20, total = 0))
            override suspend fun loadMoreTasks(offset: Int) = Result.Success(PaginationState(hasMore = false, offset = 40, total = 0))
            override suspend fun syncPendingTasks() = Result.Success(5)
            override fun observePendingSyncCount() = flowOf(0)
        }

        val useCase = SyncTasksUseCase(fakeRepository)
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun `invoke should return error when sync fails`() = runTest {
        val fakeRepository = object : TaskRepository {
            override fun observeTasks() = flowOf(emptyList<Task>())
            override suspend fun getTaskById(id: String) = Result.Error(Exception("Not found"))
            override suspend fun createTask(title: String, description: String) = Result.Error(Exception("Not implemented"))
            override suspend fun updateTask(task: Task) = Result.Success(task)
            override suspend fun deleteTask(id: String) = Result.Success(Unit)
            override suspend fun refreshTasks() = Result.Success(PaginationState(hasMore = false, offset = 20, total = 0))
            override suspend fun loadMoreTasks(offset: Int) = Result.Success(PaginationState(hasMore = false, offset = 40, total = 0))
            override suspend fun syncPendingTasks() = Result.Error(Exception("No internet connection"))
            override fun observePendingSyncCount() = flowOf(3)
        }

        val useCase = SyncTasksUseCase(fakeRepository)
        val result = useCase()

        assertTrue(result.isError)
        assertEquals("No internet connection", result.exceptionOrNull()?.message)
    }
}
