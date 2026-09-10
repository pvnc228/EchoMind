package com.echomind.data.remote

data class RemoteTranscriptionPreview(
    val requestId: String,
    val purpose: String,
    val destination: String,
    val audioFileName: String,
    val audioDurationMs: Long,
    val audioFileSizeBytes: Long,
    val audioSha256: String = ""
) {
    init {
        require(requestId.isNotBlank()) { "A transcription preview requires a request ID." }
        require(destination.isNotBlank()) { "A transcription preview requires a destination URL." }
    }
}
