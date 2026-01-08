package com.itau.app.data.mapper

import com.itau.app.core.util.currentTimeMillis
import com.itau.app.data.local.TaskEntity
import com.itau.app.data.remote.dto.TaskDto
import com.itau.app.domain.model.SyncStatus
import com.itau.app.domain.model.Task

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = is_completed == 1L,
    syncStatus = SyncStatus.valueOf(sync_status),
    createdAt = created_at,
    updatedAt = updated_at
)

fun TaskDto.toEntity(syncStatus: SyncStatus = SyncStatus.SYNCED): TaskEntity {
    val now = currentTimeMillis()
    return TaskEntity(
        id = id.toString(),
        title = title,
        description = description,
        is_completed = if (completed) 1L else 0L,
        sync_status = syncStatus.name,
        created_at = now,
        updated_at = now
    )
}

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    is_completed = if (isCompleted) 1L else 0L,
    sync_status = syncStatus.name,
    created_at = createdAt,
    updated_at = updatedAt
)
