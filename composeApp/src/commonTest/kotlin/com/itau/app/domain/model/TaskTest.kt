package com.itau.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskTest {

    @Test
    fun `Task should be created with correct properties`() {
        val task = Task(
            id = "1",
            title = "Test Task",
            description = "Test Description",
            isCompleted = false,
            syncStatus = SyncStatus.SYNCED,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        assertEquals("1", task.id)
        assertEquals("Test Task", task.title)
        assertEquals("Test Description", task.description)
        assertFalse(task.isCompleted)
        assertEquals(SyncStatus.SYNCED, task.syncStatus)
        assertEquals(1000L, task.createdAt)
        assertEquals(2000L, task.updatedAt)
    }

    @Test
    fun `Task copy should update specified fields`() {
        val task = Task(
            id = "1",
            title = "Original",
            description = "Original Description",
            isCompleted = false,
            syncStatus = SyncStatus.SYNCED,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val updatedTask = task.copy(
            title = "Updated",
            isCompleted = true,
            syncStatus = SyncStatus.PENDING_UPDATE
        )

        assertEquals("1", updatedTask.id)
        assertEquals("Updated", updatedTask.title)
        assertEquals("Original Description", updatedTask.description)
        assertTrue(updatedTask.isCompleted)
        assertEquals(SyncStatus.PENDING_UPDATE, updatedTask.syncStatus)
    }

    @Test
    fun `SyncStatus should have correct values`() {
        assertEquals("SYNCED", SyncStatus.SYNCED.name)
        assertEquals("PENDING_CREATE", SyncStatus.PENDING_CREATE.name)
        assertEquals("PENDING_UPDATE", SyncStatus.PENDING_UPDATE.name)
        assertEquals("PENDING_DELETE", SyncStatus.PENDING_DELETE.name)
    }
}
