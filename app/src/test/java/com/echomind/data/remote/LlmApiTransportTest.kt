package com.echomind.data.remote

import com.echomind.data.remote.dto.AnalysisRequest
import com.echomind.data.remote.dto.Message
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
class LlmApiTransportTest {

    @Test
    fun productionJsonSerializesModelAndTemperatureDefaults() {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
        val body = json.encodeToString(
            com.echomind.data.remote.dto.AnalysisRequest.serializer(),
            AnalysisRequest(messages = listOf(Message("user", "hi")))
        )
        assertTrue(body.contains("\"model\":\"local-model\""))
        assertTrue(body.contains("\"temperature\":0.3"))
    }

    @Test
    fun realRetrofitApprovedRequestReachesExactDestinationPath() = runBlocking {
        val server = RecordingHttpServer()
        try {
            val policy = RemoteAccessPolicy()
            val provider = BaseUrlProvider(policy)
            policy.updateLocalMode(false)
            provider.updateUrl("http://127.0.0.1:${server.port}/api")
            val client = OkHttpClient.Builder()
                .addInterceptor(EndpointInterceptor(provider, policy))
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("http://localhost:1234/")
                .client(client)
                .addConverterFactory(
                    Json { ignoreUnknownKeys = true }
                        .asConverterFactory("application/json".toMediaType())
                )
                .build()
            val api = retrofit.create(LlmApi::class.java)
            val destination = provider.effectiveUrl(QUESTION_API_PATH)

            val response = api.analyzeText(
                url = destination,
                approvedDestination = destination,
                request = AnalysisRequest(
                    messages = listOf(Message(role = "user", content = "synthetic transport probe"))
                )
            )

            assertEquals("synthetic transport answer", response.choices.single().message.content)
            assertTrue(server.requestSeen.await(2, TimeUnit.SECONDS))
            assertEquals("/api/v1/chat/completions", server.requestPath.get())
        } finally {
            server.close()
        }
    }

    @Test
    fun realRetrofitApprovedTranscriptionReachesExactDestinationPath() = runBlocking {
        val server = RecordingHttpServer()
        try {
            val policy = RemoteAccessPolicy()
            val provider = BaseUrlProvider(policy)
            policy.updateLocalMode(false)
            provider.updateUrl("http://127.0.0.1:${server.port}/api")
            val client = OkHttpClient.Builder()
                .addInterceptor(EndpointInterceptor(provider, policy))
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("http://localhost:1234/")
                .client(client)
                .addConverterFactory(
                    Json { ignoreUnknownKeys = true }
                        .asConverterFactory("application/json".toMediaType())
                )
                .build()
            val api = retrofit.create(LlmApi::class.java)
            val destination = provider.effectiveUrl(TRANSCRIPTION_API_PATH)

            val audioBody = "synthetic audio bytes".toByteArray().toRequestBody("audio/m4a".toMediaType())
            val audioPart = okhttp3.MultipartBody.Part.createFormData("file", "sample.m4a", audioBody)
            val modelBody = "whisper-1".toRequestBody("text/plain".toMediaType())
            val responseFormatBody = "json".toRequestBody("text/plain".toMediaType())

            val response = api.transcribeAudio(
                url = destination,
                approvedDestination = destination,
                audio = audioPart,
                model = modelBody,
                responseFormat = responseFormatBody
            )

            assertEquals("synthetic transport answer", response.text)
            assertTrue(server.requestSeen.await(2, TimeUnit.SECONDS))
            assertEquals("/api/v1/audio/transcriptions", server.requestPath.get())
        } finally {
            server.close()
        }
    }
}

private class RecordingHttpServer : AutoCloseable {
    private val server = ServerSocket(0)
    val port: Int = server.localPort
    val requestSeen = CountDownLatch(1)
    val requestPath = AtomicReference<String?>(null)
    private val thread = Thread {
        server.use { listener ->
            listener.accept().use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val headers = readHeaders(input)
                requestPath.set(headers.lineSequence().first().split(' ').getOrNull(1))
                val length = headers.lineSequence()
                    .firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.toIntOrNull()
                    ?: 0
                repeat(length) { input.read() }
                requestSeen.countDown()
                val body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"synthetic transport answer\"}}],\"text\":\"synthetic transport answer\"}"
                BufferedOutputStream(socket.getOutputStream()).use { output ->
                    output.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" + body).toByteArray()
                    )
                    output.flush()
                }
            }
        }
    }.also { it.start() }

    override fun close() {
        server.close()
        thread.join(2_000)
    }

    private fun readHeaders(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>()
        while (bytes.takeLast(4) != listOf(13, 10, 13, 10).map(Int::toByte)) {
            val value = input.read()
            if (value < 0) break
            bytes += value.toByte()
        }
        return bytes.toByteArray().toString(Charsets.ISO_8859_1)
    }
}
