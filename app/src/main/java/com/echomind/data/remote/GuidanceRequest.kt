package com.echomind.data.remote

import com.echomind.data.remote.dto.Message

/** Confirmed evidence for one conclusion, assembled for a single guidance request. */
data class GuidanceGrounds(
    val conclusionId: Long,
    val revisionId: Long,
    val version: Int,
    val entryId: Long?,
    val text: String,
    val supports: List<String>,
    val contradictions: List<String>,
    val outcomes: List<String>
)

data class GuidancePreview(
    val requestId: String,
    val purpose: String,
    val destination: String,
    val question: String,
    val grounds: List<GuidanceGrounds>,
    val messages: List<Message>,
    val sourceEntryIds: List<Long>
)

data class GuidanceAnswer(
    val answer: String,
    val sourceEntryIds: List<Long>
)
