package com.echomind.data.followup

enum class FollowUpStatus {
    PENDING,
    SCHEDULED,
    POSTPONED,
    CANCELED,
    FIRED,
    FAILED
}

data class FollowUpRecord(
    val decisionId: Long,
    val triggerAtMillis: Long,
    val status: FollowUpStatus
)
