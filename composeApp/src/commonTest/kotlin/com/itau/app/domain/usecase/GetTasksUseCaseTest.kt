package com.itau.app.domain.usecase

import com.itau.app.domain.model.Result
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task
import com.itau.app.domain.repository.PaginationState
import com.itau.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetTasksUseCaseTest {

    private val fakeTasks = listOf(
        Task(
            id = "1",
            title = "Task 1",
            description = "Description 1",
            isCompleted = false,
            syncStatus = SyncStatus.SYNCED,
            createdAt = 1000L,
            updatedAt = 1000L
        ),
        Task(
            id = "2",
            title = "Task 2",
            description = "Description 2",
            isCompleted = true,
            syncStatus = SyncStatus.PENDING_UPDATE,
            createdAt = 2000L,
            updatedAt = 2000L
        )
    )

    private val fakeRepository = object : TaskRepository {
        override fun observeTasks() = flowOf(fakeTasks)
        override suspend fun getTaskById(id: String) = Result.Success(fakeTasks.first { it.id == id })
        override suspend fun createTask(title: String, description: String) = Result.Success(fakeTasks.first())
        override suspend fun updateTask(task: Task) = Result.Success(task)
        override suspend fun deleteTask(id: String) = Result.Success(Unit)
        override suspend fun refreshTasks() = Result.Success(PaginationState(hasMore = false, offset = 20, total = 2))
        override suspend fun loadMoreTasks(offset: Int) = Result.Success(PaginationState(hasMore = false, offset = 40, total = 2))
        override suspend fun syncPendingTasks() = Result.Success(0)
        override fun observePendingSyncCount() = flowOf(1)
    }

    @Test
    fun `invoke should return tasks from repository`() = runTest {
        val useCase = GetTasksUseCase(fakeRepository)

        val tasks = useCase().first()

        assertEquals(2, tasks.size)
        assertEquals("Task 1", tasks[0].title)
        assertEquals("Task 2", tasks[1].title)
    }

    @Test
    fun `tasks should include both synced and pending tasks`() = runTest {
        val useCase = GetTasksUseCase(fakeRepository)

        val tasks = useCase().first()

        assertEquals(SyncStatus.SYNCED, tasks[0].syncStatus)
        assertEquals(SyncStatus.PENDING_UPDATE, tasks[1].syncStatus)
    }
}
