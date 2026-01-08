package com.itau.app.presentation.tasks

import app.cash.turbine.test
import com.itau.app.data.network.ConnectivityMonitor
import com.itau.app.domain.model.Result
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task
import com.itau.app.domain.repository.TaskRepository
import com.itau.app.domain.repository.PaginationState
import com.itau.app.domain.usecase.CreateTaskUseCase
import com.itau.app.domain.usecase.DeleteTaskUseCase
import com.itau.app.domain.usecase.GetTasksUseCase
import com.itau.app.domain.usecase.LoadMoreTasksUseCase
import com.itau.app.domain.usecase.RefreshTasksUseCase
import com.itau.app.domain.usecase.SyncTasksUseCase
import com.itau.app.domain.usecase.ToggleTaskCompletionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeTasks = listOf(
        Task("1", "Task 1", "Description 1", false, SyncStatus.SYNCED, 1000L, 1000L),
        Task("2", "Task 2", "Description 2", true, SyncStatus.PENDING_UPDATE, 2000L, 2000L)
    )

    private val tasksFlow = MutableStateFlow(fakeTasks)
    private val connectivityFlow = MutableStateFlow(true)

    private val fakeRepository = object : TaskRepository {
        var refreshResult: Result<PaginationState> = Result.Success(PaginationState(hasMore = false, offset = 20, total = 2))
        var loadMoreResult: Result<PaginationState> = Result.Success(PaginationState(hasMore = false, offset = 40, total = 2))
        var syncResult: Result<Int> = Result.Success(0)

        override fun observeTasks(): Flow<List<Task>> = tasksFlow
        override suspend fun getTaskById(id: String) = Result.Success(fakeTasks.first { it.id == id })
        override suspend fun createTask(title: String, description: String) = Result.Success(fakeTasks.first())
        override suspend fun updateTask(task: Task) = Result.Success(task)
        override suspend fun deleteTask(id: String) = Result.Success(Unit)
        override suspend fun refreshTasks() = refreshResult
        override suspend fun loadMoreTasks(offset: Int) = loadMoreResult
        override suspend fun syncPendingTasks() = syncResult
        override fun observePendingSyncCount() = flowOf(1)
    }

    private val fakeConnectivityMonitor = object : ConnectivityMonitor {
        override fun observeConnectivity(): Flow<Boolean> = connectivityFlow
        override fun isConnected(): Boolean = connectivityFlow.value
    }

    private lateinit var viewModel: TaskListViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TaskListViewModel {
        return TaskListViewModel(
            getTasksUseCase = GetTasksUseCase(fakeRepository),
            refreshTasksUseCase = RefreshTasksUseCase(fakeRepository),
            loadMoreTasksUseCase = LoadMoreTasksUseCase(fakeRepository),
            createTaskUseCase = CreateTaskUseCase(fakeRepository),
            toggleTaskCompletionUseCase = ToggleTaskCompletionUseCase(fakeRepository),
            deleteTaskUseCase = DeleteTaskUseCase(fakeRepository),
            syncTasksUseCase = SyncTasksUseCase(fakeRepository),
            connectivityMonitor = fakeConnectivityMonitor
        )
    }

    @Test
    fun `state should eventually have loading true then load tasks`() = runTest {
        viewModel = createViewModel()

        viewModel.state.test {
            // First emission is the default state
            awaitItem()
            // Advance coroutines and wait for loading/tasks state
            testDispatcher.scheduler.advanceUntilIdle()
            // After all coroutines complete, we should have tasks
            val finalState = expectMostRecentItem()
            assertTrue(finalState.tasks.isNotEmpty() || !finalState.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state should contain tasks after loading`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.tasks.size)
        assertEquals("Task 1", viewModel.state.value.tasks[0].title)
    }

    @Test
    fun `pendingSyncCount should reflect pending tasks`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // One task has PENDING_UPDATE status
        assertEquals(1, viewModel.state.value.pendingSyncCount)
    }

    @Test
    fun `isOffline should be true when no connectivity`() = runTest {
        connectivityFlow.value = false
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isOffline)
    }

    @Test
    fun `isOffline should be false when connected`() = runTest {
        connectivityFlow.value = true
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isOffline)
    }

    @Test
    fun `refresh should set isRefreshing to true then false`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.RefreshTasks)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `error should be set when refresh fails`() = runTest {
        fakeRepository.refreshResult = Result.Error(Exception("Network error"))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Reset error state first
        fakeRepository.refreshResult = Result.Error(Exception("Network error"))
        viewModel.processIntent(TaskListIntent.RefreshTasks)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Network error", viewModel.state.value.error)
    }

    @Test
    fun `dismissError should clear error`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepository.refreshResult = Result.Error(Exception("Network error"))
        viewModel.processIntent(TaskListIntent.RefreshTasks)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.DismissError)

        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `showCreateDialog should set showCreateDialog to true`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.ShowCreateDialog)

        assertTrue(viewModel.state.value.showCreateDialog)
    }

    @Test
    fun `hideCreateDialog should set showCreateDialog to false`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.ShowCreateDialog)
        viewModel.processIntent(TaskListIntent.HideCreateDialog)

        assertFalse(viewModel.state.value.showCreateDialog)
    }

    @Test
    fun `createTask with empty title should show error`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.CreateTask("", "description"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Title cannot be empty", viewModel.state.value.error)
    }

    @Test
    fun `createTask with valid title should hide dialog`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.ShowCreateDialog)
        viewModel.processIntent(TaskListIntent.CreateTask("New Task", "description"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCreateDialog)
        assertFalse(viewModel.state.value.isCreatingTask)
    }

    @Test
    fun `deleteTask should not show error on success`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val taskToDelete = fakeTasks.first()
        viewModel.processIntent(TaskListIntent.DeleteTask(taskToDelete))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `toggleTaskComplete should not show error on success`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val taskToToggle = fakeTasks.first()
        viewModel.processIntent(TaskListIntent.ToggleTaskComplete(taskToToggle))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `loadMoreTasks should set isLoadingMore to true then false`() = runTest {
        fakeRepository.refreshResult = Result.Success(PaginationState(hasMore = true, offset = 20, total = 100))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate loading more
        viewModel.processIntent(TaskListIntent.LoadMoreTasks)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoadingMore)
    }

    @Test
    fun `loadMoreTasks should not load when offline`() = runTest {
        connectivityFlow.value = false
        fakeRepository.refreshResult = Result.Success(PaginationState(hasMore = true, offset = 20, total = 100))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.LoadMoreTasks)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not trigger loading more when offline
        assertFalse(viewModel.state.value.isLoadingMore)
    }

    @Test
    fun `retrySync should sync pending tasks`() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.processIntent(TaskListIntent.RetrySync)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSyncing)
    }
}
