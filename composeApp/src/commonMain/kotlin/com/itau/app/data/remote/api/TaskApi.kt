package com.itau.app.data.remote.api

import com.itau.app.data.remote.dto.CreateTaskRequest
import com.itau.app.data.remote.dto.PaginatedResponse
import com.itau.app.data.remote.dto.TaskDto
import com.itau.app.data.remote.dto.UpdateTaskRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TaskApi(private val client: HttpClient) {

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    suspend fun getTasks(limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): PaginatedResponse {
        return client.get("todos") {
            url {
                parameters.append("_limit", limit.toString())
                parameters.append("_offset", offset.toString())
            }
        }.body()
    }

    suspend fun getTask(id: String): TaskDto {
        return client.get("todos/$id").body()
    }

    suspend fun createTask(request: CreateTaskRequest): TaskDto {
        return client.post("todos") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun updateTask(id: String, request: UpdateTaskRequest): TaskDto {
        return client.put("todos/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun deleteTask(id: String) {
        client.delete("todos/$id")
    }
}
