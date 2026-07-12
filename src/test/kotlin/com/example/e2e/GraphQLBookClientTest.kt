package com.example.e2e

import com.apollographql.apollo.ApolloClient
import com.example.graphql.GetBookQuery
import com.example.graphql.GetBooksQuery
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises the Apollo-generated GraphQL client (`GetBookQuery` / `GetBooksQuery`,
 * both built from the shared `BookDetails` fragment - see
 * src/main/graphql/com/example/graphql/BookDetails.graphql) against a stubbed
 * HTTP server, instead of a real GraphQL server.
 */
class GraphQLBookClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apolloClient: ApolloClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        apolloClient = ApolloClient.Builder()
            .serverUrl(mockWebServer.url("/graphql").toString())
            .build()
    }

    @AfterEach
    fun tearDown() {
        apolloClient.close()
        mockWebServer.shutdown()
    }

    @Test
    fun `GetBook returns a book resolved through the BookDetails fragment`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "data": {
                        "book": {
                          "__typename": "Book",
                          "id": "1",
                          "title": "Kotlin in Action",
                          "author": "Dmitry Jemerov",
                          "publishedYear": 2011
                        }
                      }
                    }
                    """.trimIndent()
                )
        )

        val response = apolloClient.query(GetBookQuery(id = "1")).execute()

        assertFalse(response.hasErrors())
        // Fields come from the reused BookDetails fragment, exposed as `.bookDetails`.
        val book = requireNotNull(response.data?.book)
        assertEquals("1", book.bookDetails.id)
        assertEquals("Kotlin in Action", book.bookDetails.title)
        assertEquals("Dmitry Jemerov", book.bookDetails.author)
        assertEquals(2011, book.bookDetails.publishedYear)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.body.readUtf8().contains("GetBook"))
    }

    @Test
    fun `GetBooks returns every book via the shared fragment`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "data": {
                        "books": [
                          {
                            "__typename": "Book",
                            "id": "1",
                            "title": "Kotlin in Action",
                            "author": "Dmitry Jemerov",
                            "publishedYear": 2011
                          },
                          {
                            "__typename": "Book",
                            "id": "2",
                            "title": "Effective Kotlin",
                            "author": "Marcin Moskala",
                            "publishedYear": 2019
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
        )

        val response = apolloClient.query(GetBooksQuery()).execute()

        assertFalse(response.hasErrors())
        val books = requireNotNull(response.data?.books)
        assertEquals(2, books.size)
        assertEquals(listOf("Kotlin in Action", "Effective Kotlin"), books.map { it.bookDetails.title })
    }
}
