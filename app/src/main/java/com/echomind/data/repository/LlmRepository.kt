package com.echomind.data.repository

import com.echomind.data.remote.LlmApi
import com.echomind.data.remote.dto.AnalysisRequest
import com.echomind.data.remote.dto.Message
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository @Inject constructor(
    private val llmApi: LlmApi
) {
    suspend fun transcribeAudio(audioFile: java.io.File): Result<String> = runCatching {
        val requestBody = audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", audioFile.name, requestBody)
        val modelPart = "whisper-1".toRequestBody()
        val formatPart = "json".toRequestBody()
        val response = llmApi.transcribeAudio(part, modelPart, formatPart)
        response.text
    }

    suspend fun analyzeEntry(entry: Entry): Result<Entry> = runCatching {
        val prompt = buildString {
            appendLine("Analyze the following voice diary entry.")
            appendLine("Extract: summary, tasks, ideas, emotions, category (general/task/idea/feeling/plan), tags.")
            appendLine("Respond in JSON format:")
            appendLine("""{"summary":"...","tasks":["..."],"ideas":["..."],"emotions":["..."],"category":"...","tags":["..."]}""")
            appendLine()
            appendLine("Entry:")
            appendLine(entry.transcript)
        }

        val request = AnalysisRequest(
            messages = listOf(
                Message("system", "You are a diary analysis assistant. Extract structured data from personal voice journal entries."),
                Message("user", prompt)
            )
        )
        val response = llmApi.analyzeText(request)
        val content = response.choices.firstOrNull()?.message?.content ?: return@runCatching entry

        val json = Json { ignoreUnknownKeys = true }
        val analysis = json.decodeFromString<AnalysisResult>(content.extractJson())

        entry.copy(
            summary = analysis.summary,
            tasks = analysis.tasks,
            ideas = analysis.ideas,
            emotions = analysis.emotions,
            category = EntryCategory.fromString(analysis.category),
            tags = analysis.tags
        )
    }

    private fun String.extractJson(): String {
        val start = indexOf('{')
        val end = lastIndexOf('}')
        return if (start != -1 && end != -1) substring(start..end) else this
    }
}

@Serializable
data class AnalysisResult(
    val summary: String = "",
    val tasks: List<String> = emptyList(),
    val ideas: List<String> = emptyList(),
    val emotions: List<String> = emptyList(),
    val category: String = "general",
    val tags: List<String> = emptyList()
)
