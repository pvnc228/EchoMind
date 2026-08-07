package com.echomind.domain.model

data class Revision(
    val version: Int,
    val text: String,
    val author: String,
    val createdAt: Long,
    val isCurrent: Boolean
)
