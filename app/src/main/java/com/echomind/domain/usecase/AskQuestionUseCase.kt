package com.echomind.domain.usecase

import com.echomind.data.remote.dto.Message
import com.echomind.data.repository.EntryRepository
import com.echomind.data.repository.LlmRepository
import com.echomind.domain.model.Entry
import javax.inject.Inject

data class QaResult(
    val answer: String,
    val sourceEntryIds: List<Long>
)

class AskQuestionUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
    private val llmRepository: LlmRepository
) {
    suspend operator fun invoke(question: String): Result<QaResult> = runCatching {
        val recentEntries = entryRepository.getRecentEntries(20)

        val contextBanner = buildString {
            appendLine("You are EchoMind's AI assistant. You help users explore their personal voice diary entries.")
            appendLine("Answer the user's question based ONLY on the diary entries provided below.")
            appendLine("If the answer cannot be found in the entries, say so clearly.")
            appendLine("When referencing specific entries, cite them by ID number in brackets, e.g. [Entry 3].")
            appendLine()
            if (recentEntries.isEmpty()) {
                appendLine("No diary entries available yet.")
            } else {
                appendLine("Here are the user's recent diary entries:")
                appendLine()
                recentEntries.forEachIndexed { idx, entry ->
                    appendLine("--- Entry ${idx + 1} (ID: ${entry.id}) ---")
                    appendLine("Date: ${formatTimestamp(entry.createdAt)}")
                    appendLine("Category: ${entry.category.displayName}")
                    appendLine(entry.transcript)
                    if (entry.summary.isNotBlank()) {
                        appendLine("Summary: ${entry.summary}")
                    }
                    if (entry.tasks.isNotEmpty()) {
                        appendLine("Tasks: ${entry.tasks.joinToString(", ")}")
                    }
                    if (entry.ideas.isNotEmpty()) {
                        appendLine("Ideas: ${entry.ideas.joinToString(", ")}")
                    }
                    if (entry.emotions.isNotEmpty()) {
                        appendLine("Emotions: ${entry.emotions.joinToString(", ")}")
                    }
                    if (entry.tags.isNotEmpty()) {
                        appendLine("Tags: ${entry.tags.joinToString(", ")}")
                    }
                    appendLine()
                }
            }
        }

        val messages = listOf(
            Message("system", contextBanner),
            Message("user", question)
        )

        val answer = llmRepository.askQuestion(messages).getOrThrow()
        val sourceIds = recentEntries.map { it.id }

        QaResult(answer = answer, sourceEntryIds = sourceIds)
    }

    private fun formatTimestamp(millis: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(millis))
    }
}
