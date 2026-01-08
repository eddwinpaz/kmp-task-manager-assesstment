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

class ToggleTaskCompletionUseCaseTest {

    private val testTask = Task(
        id = "1",
        title = "Test Task",
        description = "Test Description",
        isCompleted = false,
        syncStatus = SyncStatus.SYNCED,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private var updatedTask: Task? = null

    private val fakeRepository = object : TaskRepository {
        override fun observeTasks() = flowOf(listOf(testTask))
        override suspend fun getTaskById(id: String) = Result.Success(testTask)
        override suspend fun createTask(title: String, description: String) = Result.Success(testTask)
        override suspend fun updateTask(task: Task): Result<Task> {
            updatedTask = task
            return Result.Success(task)
        }
        override suspend fun deleteTask(id: String) = Result.Success(Unit)
        override suspend fun refreshTasks() = Result.Success(PaginationState(hasMore = false, offset = 20, total = 1))
        override suspend fun loadMoreTasks(offset: Int) = Result.Success(PaginationState(hasMore = false, offset = 40, total = 1))
        override suspend fun syncPendingTasks() = Result.Success(0)
        override fun observePendingSyncCount() = flowOf(0)
    }

    @Test
    fun `invoke should toggle task completion from false to true`() = runTest {
        val useCase = ToggleTaskCompletionUseCase(fakeRepository)

        val result = useCase(testTask)

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull()?.isCompleted)
    }

    @Test
    fun `invoke should toggle task completion from true to false`() = runTest {
        val completedTask = testTask.copy(isCompleted = true)
        val useCase = ToggleTaskCompletionUseCase(fakeRepository)

        val result = useCase(completedTask)

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull()?.isCompleted)
    }

    @Test
    fun `invoke should set sync status to PENDING_UPDATE for synced tasks`() = runTest {
        val useCase = ToggleTaskCompletionUseCase(fakeRepository)

        useCase(testTask)

        assertEquals(SyncStatus.PENDING_UPDATE, updatedTask?.syncStatus)
    }

    @Test
    fun `invoke should keep PENDING_CREATE status for new tasks`() = runTest {
        val pendingCreateTask = testTask.copy(syncStatus = SyncStatus.PENDING_CREATE)
        val useCase = ToggleTaskCompletionUseCase(fakeRepository)

        useCase(pendingCreateTask)

        assertEquals(SyncStatus.PENDING_CREATE, updatedTask?.syncStatus)
    }

    @Test
    fun `invoke should update the updatedAt timestamp`() = runTest {
        val useCase = ToggleTaskCompletionUseCase(fakeRepository)

        useCase(testTask)

        assertTrue(updatedTask!!.updatedAt > testTask.updatedAt)
    }
}
