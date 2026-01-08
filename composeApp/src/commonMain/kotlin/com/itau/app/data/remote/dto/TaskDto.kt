package com.itau.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    val id: Long,
    val title: String,
    @SerialName("body") val description: String = "",
    val completed: Boolean = false,
    @SerialName("userId") val userId: Int = 1
)

@Serializable
data class CreateTaskRequest(
    val title: String,
    @SerialName("body") val description: String,
    @SerialName("userId") val userId: Int = 1
)

@Serializable
data class UpdateTaskRequest(
    val title: String,
    @SerialName("body") val description: String,
    val completed: Boolean
)

@Serializable
data class PaginatedResponse(
    val data: List<TaskDto>,
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean
)
