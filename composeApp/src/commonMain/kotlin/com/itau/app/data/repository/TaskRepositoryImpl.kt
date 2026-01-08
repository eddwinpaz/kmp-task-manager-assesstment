package com.itau.app.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.itau.app.core.util.currentTimeMillis
import com.itau.app.data.local.ItauDatabase
import com.itau.app.data.mapper.toDomain
import com.itau.app.data.mapper.toEntity
import com.itau.app.data.network.ConnectivityMonitor
import com.itau.app.data.remote.api.TaskApi
import com.itau.app.data.remote.dto.CreateTaskRequest
import com.itau.app.data.remote.dto.UpdateTaskRequest
import com.itau.app.domain.model.Result
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task
import com.itau.app.domain.model.resultOf
import com.itau.app.domain.repository.PaginationState
import com.itau.app.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TaskRepositoryImpl(
    private val database: ItauDatabase,
    private val api: TaskApi,
    private val connectivityMonitor: ConnectivityMonitor
) : TaskRepository {

    private val queries = database.taskQueries

    override fun observeTasks(): Flow<List<Task>> {
        return queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getTaskById(id: String): Result<Task> = withContext(Dispatchers.IO) {
        resultOf {
            queries.selectById(id).executeAsOne().toDomain()
        }
    }

    override suspend fun createTask(title: String, description: String): Result<Task> =
        withContext(Dispatchers.IO) {
            resultOf {
                val now = currentTimeMillis()
                val id = generateLocalId()

                val syncStatus = if (connectivityMonitor.isConnected()) {
                    try {
                        val response = api.createTask(CreateTaskRequest(title, description))
                        queries.insert(
                            id = response.id.toString(),
                            title = response.title,
                            description = response.description,
                            is_completed = if (response.completed) 1L else 0L,
                            sync_status = SyncStatus.SYNCED.name,
                            created_at = now,
                            updated_at = now
                        )
                        return@resultOf queries.selectById(response.id.toString()).executeAsOne().toDomain()
                    } catch (e: Exception) {
                        SyncStatus.PENDING_CREATE
                    }
                } else {
                    SyncStatus.PENDING_CREATE
                }

                queries.insert(
                    id = id,
                    title = title,
                    description = description,
                    is_completed = 0L,
                    sync_status = syncStatus.name,
                    created_at = now,
                    updated_at = now
                )
                queries.selectById(id).executeAsOne().toDomain()
            }
        }

    override suspend fun updateTask(task: Task): Result<Task> = withContext(Dispatchers.IO) {
        resultOf {
            // Check the current sync status in the database to determine if we should sync
            val existingTask = queries.selectById(task.id).executeAsOneOrNull()
            val wasAlreadySynced = existingTask?.sync_status == SyncStatus.SYNCED.name
            val isPendingCreate = existingTask?.sync_status == SyncStatus.PENDING_CREATE.name ||
                    task.syncStatus == SyncStatus.PENDING_CREATE

            val syncStatus = when {
                // Keep PENDING_CREATE for tasks that haven't been created on server yet
                isPendingCreate -> SyncStatus.PENDING_CREATE

                // Try to sync if connected and task was previously synced
                connectivityMonitor.isConnected() && wasAlreadySynced -> {
                    try {
                        api.updateTask(
                            task.id,
                            UpdateTaskRequest(task.title, task.description, task.isCompleted)
                        )
                        SyncStatus.SYNCED
                    } catch (e: Exception) {
                        SyncStatus.PENDING_UPDATE
                    }
                }

                // Otherwise mark as pending update
                else -> SyncStatus.PENDING_UPDATE
            }

            queries.insert(
                id = task.id,
                title = task.title,
                description = task.description,
                is_completed = if (task.isCompleted) 1L else 0L,
                sync_status = syncStatus.name,
                created_at = task.createdAt,
                updated_at = task.updatedAt
            )
            task.copy(syncStatus = syncStatus)
        }
    }

    override suspend fun deleteTask(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        resultOf {
            val task = queries.selectById(id).executeAsOneOrNull()

            if (task?.sync_status == SyncStatus.PENDING_CREATE.name) {
                queries.delete(id)
            } else if (connectivityMonitor.isConnected()) {
                try {
                    api.deleteTask(id)
                    queries.delete(id)
                } catch (e: Exception) {
                    queries.updateSyncStatus(SyncStatus.PENDING_DELETE.name, id)
                }
            } else {
                queries.updateSyncStatus(SyncStatus.PENDING_DELETE.name, id)
            }
        }
    }

    override suspend fun refreshTasks(): Result<PaginationState> = withContext(Dispatchers.IO) {
        resultOf {
            if (!connectivityMonitor.isConnected()) {
                throw Exception("No internet connection")
            }

            val response = api.getTasks()
            val remoteTasks = response.data
            val remoteIds = remoteTasks.map { it.id.toString() }.toSet()

            // Get all local synced tasks
            val localSyncedTasks = queries.selectAll().executeAsList()
                .filter { it.sync_status == SyncStatus.SYNCED.name }

            // Delete local synced tasks that no longer exist on server (only for first page)
            localSyncedTasks.forEach { localTask ->
                if (localTask.id !in remoteIds) {
                    queries.delete(localTask.id)
                }
            }

            // Update or insert remote tasks
            remoteTasks.forEach { dto ->
                val existing = queries.selectById(dto.id.toString()).executeAsOneOrNull()
                if (existing == null || existing.sync_status == SyncStatus.SYNCED.name) {
                    val entity = dto.toEntity(SyncStatus.SYNCED)
                    queries.insert(
                        id = entity.id,
                        title = entity.title,
                        description = entity.description,
                        is_completed = entity.is_completed,
                        sync_status = entity.sync_status,
                        created_at = entity.created_at,
                        updated_at = entity.updated_at
                    )
                }
            }

            PaginationState(
                hasMore = response.pagination.hasMore,
                offset = response.pagination.offset + remoteTasks.size,
                total = response.pagination.total
            )
        }
    }

    override suspend fun loadMoreTasks(offset: Int): Result<PaginationState> = withContext(Dispatchers.IO) {
        resultOf {
            if (!connectivityMonitor.isConnected()) {
                throw Exception("No internet connection")
            }

            val response = api.getTasks(offset = offset)
            val remoteTasks = response.data

            // Insert new tasks (don't delete existing ones when loading more)
            remoteTasks.forEach { dto ->
                val existing = queries.selectById(dto.id.toString()).executeAsOneOrNull()
                if (existing == null || existing.sync_status == SyncStatus.SYNCED.name) {
                    val entity = dto.toEntity(SyncStatus.SYNCED)
                    queries.insert(
                        id = entity.id,
                        title = entity.title,
                        description = entity.description,
                        is_completed = entity.is_completed,
                        sync_status = entity.sync_status,
                        created_at = entity.created_at,
                        updated_at = entity.updated_at
                    )
                }
            }

            PaginationState(
                hasMore = response.pagination.hasMore,
                offset = response.pagination.offset + remoteTasks.size,
                total = response.pagination.total
            )
        }
    }

    override suspend fun syncPendingTasks(): Result<Int> = withContext(Dispatchers.IO) {
        resultOf {
            if (!connectivityMonitor.isConnected()) {
                throw Exception("No internet connection")
            }

            val pendingTasks = queries.selectPendingSync().executeAsList()
            var syncedCount = 0

            pendingTasks.forEach { entity ->
                try {
                    when (SyncStatus.valueOf(entity.sync_status)) {
                        SyncStatus.PENDING_CREATE -> {
                            val response = api.createTask(
                                CreateTaskRequest(entity.title, entity.description)
                            )
                            queries.delete(entity.id)
                            val now = currentTimeMillis()
                            queries.insert(
                                id = response.id.toString(),
                                title = response.title,
                                description = response.description,
                                is_completed = if (response.completed) 1L else 0L,
                                sync_status = SyncStatus.SYNCED.name,
                                created_at = now,
                                updated_at = now
                            )
                        }
                        SyncStatus.PENDING_UPDATE -> {
                            api.updateTask(
                                entity.id,
                                UpdateTaskRequest(entity.title, entity.description, entity.is_completed == 1L)
                            )
                            queries.updateSyncStatus(SyncStatus.SYNCED.name, entity.id)
                        }
                        SyncStatus.PENDING_DELETE -> {
                            api.deleteTask(entity.id)
                            queries.delete(entity.id)
                        }
                        SyncStatus.SYNCED -> { /* Already synced */ }
                    }
                    syncedCount++
                } catch (e: Exception) {
                    // Keep as pending, will retry later
                }
            }
            syncedCount
        }
    }

    override fun observePendingSyncCount(): Flow<Int> {
        return queries.countPendingSync()
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.toInt() }
    }

    private fun generateLocalId(): String {
        return "local-${currentTimeMillis()}-${(0..999999).random()}"
    }
}
