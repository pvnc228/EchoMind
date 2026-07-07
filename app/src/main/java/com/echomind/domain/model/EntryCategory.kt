package com.echomind.domain.model

enum class EntryCategory(val displayName: String) {
    GENERAL("General"),
    TASK("Task"),
    IDEA("Idea"),
    FEELING("Feeling"),
    PLAN("Plan");

    companion object {
        fun fromString(value: String): EntryCategory =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: GENERAL
    }
}
