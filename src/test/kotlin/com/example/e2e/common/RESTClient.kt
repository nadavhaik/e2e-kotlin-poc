package com.example.e2e.common

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.takeFrom
import java.lang.reflect.Type
import kotlin.reflect.typeOf

val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
private val anyAdapter: JsonAdapter<Any> = moshi.adapter(Any::class.java)

class RESTClient : AutoCloseable {
    var clientHeaders: Map<String, String> = emptyMap()

    private val baseUrl: String
    private val client: HttpClient

    constructor(
        baseUrl: String,
        timeout: Long = 5000,
        headers: Map<String, String> = emptyMap()
    ) {
        this.clientHeaders = headers
        this.baseUrl = baseUrl.trimEnd('/')
        this.client = HttpClient(CIO) {
            expectSuccess = true
            install(HttpTimeout) {
                requestTimeoutMillis = timeout
                connectTimeoutMillis = timeout
                socketTimeoutMillis = timeout
            }
            defaultRequest {
                url.takeFrom(this@RESTClient.baseUrl)
                val defaultHeaders = this@RESTClient.clientHeaders
                defaultHeaders.forEach { (name, value) ->
                    header(name, value)
                }
            }
        }
    }

    suspend inline fun <reified R> post(endpoint: String, body: Any): R =
        post(endpoint, body, responseTypeAdapter = moshi.adapter(getJavaType(typeOf<R>())))

    suspend fun <R> post(endpoint: String, body: Any, responseTypeAdapter: JsonAdapter<R>): R {
        val response = this.client.post(endpoint) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(anyAdapter.toJson(body))
        }

        return responseTypeAdapter.fromJson(String(response.readRawBytes()))!!
    }


    override fun close() {
        client.close()
    }
}

fun getJavaType(kType: kotlin.reflect.KType): Type {
    val rawClass = (kType.classifier as? kotlin.reflect.KClass<*>)?.javaObjectType ?: Any::class.java
    val arguments = kType.arguments
    if (arguments.isEmpty()) return rawClass

    val typeArgs = arguments.map { arg ->
        arg.type?.let { getJavaType(it) } ?: Any::class.java
    }.toTypedArray()

    return Types.newParameterizedType(rawClass, *typeArgs)
}