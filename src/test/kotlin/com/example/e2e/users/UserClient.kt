package com.example.e2e.users

import com.example.e2e.common.RESTClient
import com.example.users.UserProfile

enum class ResponseStatus {
    FAILURE,
    OK
}

data class UserResponse<T>(
    val status: ResponseStatus,
    val error: String? = null,
    val data: T? = null
)

class UsersError(error: String) : Exception(error)

object UserClient : AutoCloseable {
    val restClient = RESTClient(
        baseUrl = System.getProperty("user.api.baseUrl", "http://localhost:8080"),
    )

    suspend fun createUser(user: UserProfile): Int = post("/users", user)!!

    suspend inline fun <reified R> post(endpoint: String, body: Any): R? {
        val response = restClient.post<UserResponse<R>>(endpoint, body)
        if (response.status == ResponseStatus.FAILURE) {
            throw UsersError(response.error ?: "Unknown error")
        }
        return response.data
    }

    override fun close() {
        restClient.close()
    }
}
