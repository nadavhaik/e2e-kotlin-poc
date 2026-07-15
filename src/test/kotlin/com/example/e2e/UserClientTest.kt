package com.example.e2e

import com.example.e2e.users.UserClient
import com.example.e2e.users.UsersError
import com.example.users.UserProfile
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Exercises [UserClient] (JSON Schema-generated [UserProfile] request body) against a stubbed
 * HTTP server that returns the UserResponse envelope.
 */
class UserClientTest {

    companion object {
        private lateinit var mockWebServer: MockWebServer

        @JvmStatic
        @BeforeAll
        fun setUpServer() {
            mockWebServer = MockWebServer()
            mockWebServer.start()
            // Must be set before UserClient's object initializer runs (first reference in a test).
            System.setProperty("user.api.baseUrl", mockWebServer.url("/").toString().trimEnd('/'))
        }

        @JvmStatic
        @AfterAll
        fun tearDownServer() {
            UserClient.close()
            mockWebServer.shutdown()
        }
    }

    @Test
    fun `createUser posts UserProfile and returns the new id from UserResponse`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"OK","data":42}""")
        )

        val id = UserClient.createUser(
            UserProfile(
                username = "alice",
                email = "alice@example.com",
                role = UserProfile.Role.user,
            )
        )

        assertEquals(42, id)

        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/users", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("alice"))
        assertTrue(body.contains("alice@example.com"))
    }

    @Test
    fun `createUser throws UsersError when status is FAILURE`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"FAILURE","error":"username taken"}""")
        )

        val error = assertThrows<UsersError> {
            UserClient.createUser(
                UserProfile(
                    username = "alice",
                    email = "alice@example.com",
                    role = UserProfile.Role.user,
                )
            )
        }
        assertEquals("username taken", error.message)
    }
}
