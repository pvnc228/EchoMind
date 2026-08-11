package com.echomind.data.remote

import com.echomind.data.remote.dto.Message

/** A bounded, confirmed conclusion selected for one remote request. */
data class ConfirmedContextItem(
    val entryId: Long?,
    val revisionId: Long,
    val version: Int,
    val text: String
) {
    init {
        require(revisionId > 0) { "A confirmed context item needs a revision ID." }
        require(version > 0) { "A confirmed context item needs a revision version." }
        require(text.isNotBlank()) { "A confirmed context item needs text." }
    }
}

data class RemoteQuestionPreview(
    val requestId: String,
    val purpose: String,
    val destination: String,
    val question: String,
    val context: List<ConfirmedContextItem>,
    val messages: List<Message>,
    val sourceEntryIds: List<Long>
)

data class RemoteQuestionAnswer(
    val answer: String,
    val sourceEntryIds: List<Long>
)

class RemoteDestinationChangedException : IllegalStateException(
    "The configured remote destination changed after approval. Review the request again."
)

class RemoteLocalModeChangedException : IllegalStateException(
    "Local mode was enabled before the approved remote request crossed the network boundary."
)
