package com.example.e2e

import com.example.tasks.apis.TasksApi
import com.example.tasks.models.NewTask
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises the OpenAPI Generator-generated REST client (`TasksApi`, from
 * schemas/openapi/tasks-api.yaml) against a stubbed HTTP server, instead of a
 * real Tasks service.
 */
class TaskApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tasksApi: TasksApi

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        tasksApi = TasksApi(basePath = mockWebServer.url("/").toString())
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getTasks lists tasks from GET tasks`() {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    [
                      {"id": "1", "title": "Write POC", "completed": false},
                      {"id": "2", "title": "Review PR", "completed": true}
                    ]
                    """.trimIndent()
                )
        )

        val tasks = tasksApi.getTasks()

        assertEquals(2, tasks.size)
        assertEquals("Write POC", tasks[0].title)
        assertTrue(tasks[1].completed)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/tasks", recordedRequest.path)
    }

    @Test
    fun `getTaskById fetches a single task from GET tasks id`() {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id": "1", "title": "Write POC", "completed": false}""")
        )

        val task = tasksApi.getTaskById("1")

        assertEquals("1", task.id)
        assertEquals("Write POC", task.title)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/tasks/1", recordedRequest.path)
    }

    @Test
    fun `createTask sends a POST tasks request and returns the created task`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id": "3", "title": "Ship feature", "completed": false}""")
        )

        val created = tasksApi.createTask(NewTask(title = "Ship feature", completed = false))

        assertEquals("3", created.id)
        assertEquals("Ship feature", created.title)
        assertTrue(!created.completed)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/tasks", recordedRequest.path)
        val requestBody = recordedRequest.body.readUtf8()
        assertTrue(requestBody.contains("Ship feature"))
    }
}
