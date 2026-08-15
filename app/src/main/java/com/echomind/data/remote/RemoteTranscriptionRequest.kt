package com.echomind.data.remote

data class RemoteTranscriptionPreview(
    val requestId: String,
    val purpose: String,
    val destination: String,
    val audioFileName: String,
    val audioDurationMs: Long,
    val audioFileSizeBytes: Long
) {
    init {
        require(requestId.isNotBlank()) { "A transcription preview requires a request ID." }
        require(destination.isNotBlank()) { "A transcription preview requires a destination URL." }
    }
}
