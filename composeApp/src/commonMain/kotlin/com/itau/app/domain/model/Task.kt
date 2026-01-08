package com.itau.app.domain.model

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long
)

enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE
}
