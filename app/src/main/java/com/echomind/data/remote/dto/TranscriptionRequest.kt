package com.echomind.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranscriptionRequest(
    @SerialName("model")
    val model: String = "whisper-1",
    @SerialName("language")
    val language: String? = null,
    @SerialName("response_format")
    val responseFormat: String = "json"
)
