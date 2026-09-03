package com.echomind.domain.model

enum class TranscriptionEngine(val displayName: String) {
    ON_DEVICE("Android Speech"),
    WHISPER("Whisper"),
    GEMINI("Gemini")
}
