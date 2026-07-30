package com.echomind.data.analysis

import com.echomind.domain.model.ReflectionDraft
import javax.inject.Inject
import javax.inject.Singleton

data class LocalReflectionProposal(
    val draft: ReflectionDraft,
    val counterargument: String
)

@Singleton
class LocalReflectionAnalyzer @Inject constructor() {

    fun analyze(originalText: String): LocalReflectionProposal {
        require(originalText.isNotBlank()) { "A reflection cannot be blank." }

        val sentences = originalText
            .trim()
            .split(Regex("(?<=[.!?])\\s+|[\\r\\n]+"))
            .map(String::trim)
            .filter(String::isNotBlank)

        val thesis = sentences.firstOrNull(::looksLikeInterpretation)
            ?: sentences.first()
        val observations = sentences.filter(::looksLikeObservation).take(MAX_ITEMS)
        val interpretations = sentences.filter(::looksLikeInterpretation).take(MAX_ITEMS)
        val assumptions = sentences.filter(::looksLikeAssumption).take(MAX_ITEMS)
        val questions = sentences.filter { it.endsWith("?") }.take(MAX_ITEMS)
            .ifEmpty {
                listOf("What evidence would strengthen or weaken this tentative thesis?")
            }

        return LocalReflectionProposal(
            draft = ReflectionDraft(
                tentativeThesis = thesis,
                observations = observations,
                interpretations = interpretations,
                assumptions = assumptions,
                openQuestions = questions
            ),
            counterargument = buildCounterargument(originalText)
        )
    }

    private fun looksLikeObservation(sentence: String): Boolean =
        sentence.containsAny(
            "i noticed", "i saw", "i heard", "happened", "when ", "after ",
            "today", "yesterday", "я заметил", "я заметила", "я увидел",
            "я увидела", "произошло", "когда ", "после ", "сегодня", "вчера"
        )

    private fun looksLikeInterpretation(sentence: String): Boolean =
        sentence.containsAny(
            "i think", "i believe", "it seems", "this means", "i feel",
            "я думаю", "я считаю", "мне кажется", "это значит", "я чувствую"
        )

    private fun looksLikeAssumption(sentence: String): Boolean =
        sentence.containsAny(
            " always ", " never ", " must ", " should ", "obviously",
            " everyone ", " no one ", "всегда", "никогда", "должен",
            "должна", "нужно", "очевидно", "все ", "никто"
        )

    private fun buildCounterargument(text: String): String = when {
        text.containsAny(
            " always ", " never ", "obviously", " everyone ", " no one ",
            "всегда", "никогда", "очевидно", "все ", "никто"
        ) -> "An exception may weaken the absolute wording. Which concrete cases support it, and which do not?"

        text.containsAny(
            "because", "therefore", "that is why", "потому что", "поэтому",
            "из-за"
        ) -> "The sequence may fit more than one cause. What alternative explanation would the same observations also support?"

        text.containsAny(
            " must ", " should ", "have to", "должен", "должна", "нужно",
            "обязан"
        ) -> "This may be a preference or constraint rather than a requirement. What changes if it is treated as a choice?"

        else -> "The same material may support another interpretation. What evidence would distinguish it from the tentative thesis?"
    }

    private fun String.containsAny(vararg terms: String): Boolean {
        val normalized = " ${lowercase()} "
        return terms.any { normalized.contains(it) }
    }

    private companion object {
        const val MAX_ITEMS = 3
    }
}
