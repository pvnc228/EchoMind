package com.echomind.data.analysis

import com.echomind.domain.model.ReflectionDraft
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class StructuredReflectionResult(
    val draft: ReflectionDraft,
    val counterargument: String
)

@Serializable
internal data class StructuredReflectionPayload(
    @SerialName("tentativeThesis")
    val tentativeThesis: String? = null,
    @SerialName("observations")
    val observations: List<String>? = null,
    @SerialName("interpretations")
    val interpretations: List<String>? = null,
    @SerialName("assumptions")
    val assumptions: List<String>? = null,
    @SerialName("openQuestions")
    val openQuestions: List<String>? = null,
    @SerialName("counterargument")
    val counterargument: String? = null
)

@Singleton
class ReflectionDraftParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parse(rawText: String): StructuredReflectionResult? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val jsonString = extractJson(trimmed) ?: return null

        return try {
            val payload = json.decodeFromString<StructuredReflectionPayload>(jsonString)
            val observations = payload.observations?.filter { it.isNotBlank() } ?: emptyList()
            val interpretations = payload.interpretations?.filter { it.isNotBlank() } ?: emptyList()
            val assumptions = payload.assumptions?.filter { it.isNotBlank() } ?: emptyList()
            val openQuestions = payload.openQuestions?.filter { it.isNotBlank() } ?: emptyList()
            val tentativeThesis = payload.tentativeThesis?.trim().orEmpty()
            val counterargument = payload.counterargument?.trim().orEmpty()

            StructuredReflectionResult(
                draft = ReflectionDraft(
                    tentativeThesis = tentativeThesis,
                    observations = observations,
                    interpretations = interpretations,
                    assumptions = assumptions,
                    openQuestions = openQuestions
                ),
                counterargument = counterargument
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJson(text: String): String? {
        val fencePattern = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val match = fencePattern.find(text)
        val candidate = if (match != null) {
            match.groupValues[1].trim()
        } else {
            val firstBrace = text.indexOf('{')
            val lastBrace = text.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                text.substring(firstBrace, lastBrace + 1).trim()
            } else {
                null
            }
        }
        return candidate?.takeIf { it.startsWith("{") && it.endsWith("}") }
    }
}
