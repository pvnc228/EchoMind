package com.echomind.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptionResponse(
    @SerialName("text")
    val text: String
)

@Serializable
data class AnalysisRequest(
    @SerialName("model")
    val model: String = "local-model",
    @SerialName("messages")
    val messages: List<Message>,
    @SerialName("temperature")
    val temperature: Double = 0.3
)

@Serializable
data class Message(
    @SerialName("role")
    val role: String,
    @SerialName("content")
    val content: String
)

@Serializable
data class AnalysisResponse(
    @SerialName("choices")
    val choices: List<Choice>
)

@Serializable
data class Choice(
    @SerialName("message")
    val message: Message
)
