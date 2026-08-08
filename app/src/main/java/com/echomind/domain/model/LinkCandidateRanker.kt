package com.echomind.domain.model

import java.text.Normalizer
import java.util.Locale

data class LinkCandidateInput(
    val rawRecordId: Long,
    val text: String,
    val recordedAt: Long
)

object LinkCandidateRanker {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "at",
        "i", "you", "it", "is", "are", "was", "were", "be", "been", "have",
        "has", "had", "that", "this", "these", "those", "my", "we", "our",
        "with", "for", "not", "so", "if", "can", "could", "would", "should",
        "я", "и", "в", "о", "не", "на", "что", "это", "мой", "моя", "мои",
        "мы", "нам", "для", "с", "по", "как", "но", "или", "если", "то",
        "быть", "был", "была", "были", "есть"
    )

    fun rank(
        currentText: String,
        themeText: String,
        candidates: List<LinkCandidateInput>,
        currentRawRecordId: Long?,
        linkedRawRecordIds: Set<Long>,
        limit: Int = 5
    ): List<RelatedRecord> {
        require(limit >= 0) { "Candidate limit cannot be negative." }

        val currentTokens = tokenize(currentText)
        val themeTokens = tokenize(themeText)
        return candidates
            .asSequence()
            .filter { it.rawRecordId != currentRawRecordId }
            .filter { it.rawRecordId !in linkedRawRecordIds }
            .mapNotNull { candidate ->
                val candidateTokens = tokenize(candidate.text)
                val sharedWithConclusion = currentTokens.intersect(candidateTokens)
                val sharedWithThemes = themeTokens.intersect(candidateTokens)
                val score = sharedWithConclusion.size + sharedWithThemes.size
                if (score == 0) {
                    null
                } else {
                    RelatedRecord(
                        rawRecordId = candidate.rawRecordId,
                        relationship = "",
                        sourceText = candidate.text,
                        recordedAt = candidate.recordedAt,
                        suggestedReason = suggestReason(sharedWithConclusion, sharedWithThemes),
                        score = score
                    )
                }
            }
            .sortedWith(
                compareByDescending<RelatedRecord> { it.score }
                    .thenByDescending { it.recordedAt }
                    .thenBy { it.rawRecordId }
            )
            .take(limit)
            .toList()
    }

    fun tokenize(text: String): Set<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
        return tokenPattern.findAll(normalized)
            .map { it.value }
            .filter { it.codePointCount(0, it.length) >= 3 }
            .filter { it !in stopWords }
            .toSet()
    }

    private fun suggestReason(
        sharedWithConclusion: Set<String>,
        sharedWithThemes: Set<String>
    ): String? = when {
        sharedWithConclusion.isNotEmpty() ->
            "Shares a term with your conclusion: ${sharedWithConclusion.sorted().joinToString(", ")}"
        sharedWithThemes.isNotEmpty() ->
            "Mentions a theme: ${sharedWithThemes.sorted().joinToString(", ")}"
        else -> null
    }
}
