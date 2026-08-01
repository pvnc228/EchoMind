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
        val language = languageOf(originalText)
        val classified = sentences.map { sentence -> sentence to roleOf(sentence) }
        val observations = classified.filter { it.second == SentenceRole.OBSERVATION }
            .map { it.first }
            .take(MAX_ITEMS)
        val interpretations = classified.filter { it.second == SentenceRole.INTERPRETATION }
            .map { it.first }
            .take(MAX_ITEMS)
        val assumptions = classified.filter { it.second == SentenceRole.ASSUMPTION }
            .map { it.first }
            .take(MAX_ITEMS)
        val sourceQuestions = classified.filter { it.second == SentenceRole.QUESTION }
            .map { it.first }
            .take(MAX_ITEMS)
        val counterEvidence = classified.filter { it.second == SentenceRole.COUNTER_EVIDENCE }
            .map { it.first }
            .take(MAX_ITEMS)
        val relation = relationOf(originalText, observations, interpretations, assumptions, counterEvidence)
        val displayedAssumptions = assumptions.ifEmpty { impliedAssumptionFor(relation, language) }
        val openQuestions = when (relation) {
            Relation.OBSERVATIONAL, Relation.CAUTIOUS -> sourceQuestions
            else -> questionFor(relation, language, observations, counterEvidence)
        }

        return LocalReflectionProposal(
            draft = ReflectionDraft(
                tentativeThesis = thesisFor(relation, language),
                observations = observations,
                interpretations = interpretations,
                assumptions = displayedAssumptions,
                openQuestions = openQuestions
            ),
            counterargument = alternativeFor(relation, language, observations, counterEvidence)
        )
    }

    private fun impliedAssumptionFor(relation: Relation, language: Language): List<String> = when (relation) {
        Relation.AMBIGUOUS_COMMUNICATION -> listOf(
            when (language) {
                Language.RUSSIAN -> "Если ответ короче обычного, его длина надёжно показывает качество моей работы."
                Language.ENGLISH -> "If a reply is shorter than usual, its length reliably shows the quality of my work."
            }
        )

        else -> emptyList()
    }

    private fun roleOf(sentence: String): SentenceRole = when {
        sentence.endsWith("?") -> SentenceRole.QUESTION
        looksLikeAssumption(sentence) && !sentence.startsWithAny(
            "я думаю", "я считаю", "мне кажется", "i think", "i believe", "it seems"
        ) -> SentenceRole.ASSUMPTION
        looksLikeInterpretation(sentence) -> SentenceRole.INTERPRETATION
        looksLikeAssumption(sentence) -> SentenceRole.ASSUMPTION
        looksLikeCounterEvidence(sentence) -> SentenceRole.COUNTER_EVIDENCE
        else -> SentenceRole.OBSERVATION
    }

    private fun relationOf(
        text: String,
        observations: List<String>,
        interpretations: List<String>,
        assumptions: List<String>,
        counterEvidence: List<String>
    ): Relation = when {
        assumptions.isEmpty() && interpretations.isEmpty() -> Relation.OBSERVATIONAL
        text.containsAny("похвал", "одобр", "praised", "compliment") -> Relation.PRAISE_TO_IDENTITY
        text.containsAny("сообщени", "ответил", "ответила", "reply", "message") &&
            text.containsAny("одним словом", "кратк", "короч", "short", "brief") &&
            text.containsAny("недовол", "ошиб", "оценк", "dissatisf", "mistake", "evaluat") ->
            Relation.AMBIGUOUS_COMMUNICATION
        assumptions.isNotEmpty() && observations.any { it.containsAny("не интерес", "не дожд", "неизвест", "not interested", "not received", "waiting") } -> Relation.FORCED_CHOICE
        interpretations.any { it.containsAny("из-за", "потому что", "причиной", "because", "cause", "therefore", "поэтому") } && counterEvidence.isNotEmpty() -> Relation.CAUSAL_WITH_COUNTER_EVIDENCE
        assumptions.isNotEmpty() && observations.isNotEmpty() -> Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM
        assumptions.isEmpty() && (counterEvidence.isNotEmpty() || text.containsAny("пока не понимаю", "недостаточно", "может быть", "not enough", "may be")) -> Relation.CAUTIOUS
        else -> Relation.GENERIC
    }

    private fun thesisFor(relation: Relation, language: Language): String = when (language) {
        Language.RUSSIAN -> when (relation) {
            Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM -> "Описанный единичный исход пока недостаточен для общего вывода о моих способностях."
            Relation.AMBIGUOUS_COMMUNICATION -> "Короткий ответ — повод проверить контекст, а не подтверждение оценки моей работы."
            Relation.FORCED_CHOICE -> "Решение стоит сравнить по срочности, интересу и доступной информации, прежде чем считать его обязательным."
            Relation.CAUSAL_WITH_COUNTER_EVIDENCE -> "Связь между событиями можно проверить, не объявляя одну причину единственной."
            Relation.PRAISE_TO_IDENTITY -> "Одна похвала — сигнал для проверки интереса, а не окончательный выбор специализации."
            Relation.OBSERVATIONAL -> "Это фактическая запись без достаточного основания для вывода о результате."
            Relation.CAUTIOUS -> "Исходная гипотеза уже сформулирована осторожно и остаётся проверяемой."
            Relation.GENERIC -> "Это предварительное толкование, которое стоит проверить по наблюдаемым деталям."
        }

        Language.ENGLISH -> when (relation) {
            Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM -> "One described outcome is not yet enough for a general conclusion about ability."
            Relation.AMBIGUOUS_COMMUNICATION -> "A short reply is a reason to check context, not confirmation of an evaluation."
            Relation.FORCED_CHOICE -> "The decision is worth comparing against urgency, preference, and available information before treating it as required."
            Relation.CAUSAL_WITH_COUNTER_EVIDENCE -> "The link between events can be checked without treating one cause as the only cause."
            Relation.PRAISE_TO_IDENTITY -> "One compliment is a signal to explore an interest, not a final identity choice."
            Relation.OBSERVATIONAL -> "This is a factual record without enough basis for a conclusion about the outcome."
            Relation.CAUTIOUS -> "The original hypothesis is already cautious and remains testable."
            Relation.GENERIC -> "This is a tentative interpretation worth checking against observable details."
        }
    }

    private fun alternativeFor(
        relation: Relation,
        language: Language,
        observations: List<String>,
        counterEvidence: List<String>
    ): String = when (language) {
        Language.RUSSIAN -> when (relation) {
            Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM -> "Единичный исход «${detailOf(observations.first())}» сам по себе не устанавливает общий уровень способностей."
            Relation.AMBIGUOUS_COMMUNICATION -> "Краткость сообщения сама по себе не показывает оценку работы."
            Relation.FORCED_CHOICE -> "Срочность решения конкурирует с указанным интересом к работе и недостающей информацией."
            Relation.CAUSAL_WITH_COUNTER_EVIDENCE -> "К единственной причине добавляется указанный контекст: ${detailOf(counterEvidence.first())}."
            Relation.PRAISE_TO_IDENTITY -> "Похвала за отдельную работу не обязательно определяет подходящую специализацию в целом."
            Relation.OBSERVATIONAL, Relation.CAUTIOUS -> ""
            Relation.GENERIC -> "Та же запись может поддерживать другое предварительное объяснение."
        }

        Language.ENGLISH -> when (relation) {
            Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM -> "The single outcome \"${detailOf(observations.first())}\" does not by itself establish overall ability."
            Relation.AMBIGUOUS_COMMUNICATION -> "A brief message alone does not show how the work was evaluated."
            Relation.FORCED_CHOICE -> "Urgency competes with the stated preference and missing information."
            Relation.CAUSAL_WITH_COUNTER_EVIDENCE -> "The stated context also competes with a single-cause explanation: ${detailOf(counterEvidence.first())}."
            Relation.PRAISE_TO_IDENTITY -> "Praise for one piece of work does not necessarily determine a suitable specialization."
            Relation.OBSERVATIONAL, Relation.CAUTIOUS -> ""
            Relation.GENERIC -> "The same record may support another tentative explanation."
        }
    }

    private fun questionFor(
        relation: Relation,
        language: Language,
        observations: List<String>,
        counterEvidence: List<String>
    ): List<String> = when (language) {
        Language.RUSSIAN -> listOfNotNull(when (relation) {
            Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM -> "Какая конкретная обратная связь о ситуации «${detailOf(observations.first())}» отличила бы один исход от общего вывода о способностях?"
            Relation.AMBIGUOUS_COMMUNICATION -> "Встречается ли такая краткость в других сообщениях, не связанных с этой работой?"
            Relation.FORCED_CHOICE -> "Какая недостающая информация могла бы изменить выбор до срочного решения?"
            Relation.CAUSAL_WITH_COUNTER_EVIDENCE -> "Как изменится объяснение, если учесть: ${detailOf(counterEvidence.first())}?"
            Relation.PRAISE_TO_IDENTITY -> "Повторяется ли интерес к таким задачам и без внешней похвалы?"
            Relation.GENERIC -> "Какое наблюдаемое обстоятельство сделало бы это толкование более или менее вероятным?"
            Relation.OBSERVATIONAL, Relation.CAUTIOUS -> null
        })

        Language.ENGLISH -> listOfNotNull(when (relation) {
            Relation.SINGLE_EVENT_TO_GLOBAL_CLAIM -> "What specific feedback about \"${detailOf(observations.first())}\" would distinguish one outcome from a general conclusion about ability?"
            Relation.AMBIGUOUS_COMMUNICATION -> "Does the same brevity appear in other messages unrelated to this work?"
            Relation.FORCED_CHOICE -> "What missing information could change the choice before an urgent decision?"
            Relation.CAUSAL_WITH_COUNTER_EVIDENCE -> "How would the explanation change after considering: ${detailOf(counterEvidence.first())}?"
            Relation.PRAISE_TO_IDENTITY -> "Does the interest in this kind of work recur without outside praise?"
            Relation.GENERIC -> "What observable detail would make this interpretation more or less likely?"
            Relation.OBSERVATIONAL, Relation.CAUTIOUS -> null
        })
    }

    private fun looksLikeInterpretation(sentence: String): Boolean = sentence.containsAny(
        "i think", "i believe", "it seems", "this means", "i feel",
        "я думаю", "я считаю", "я начал думать", "я начала думать", "мне кажется",
        "кажется мне", "это значит", "я чувствую", "i started to think"
    )

    private fun looksLikeAssumption(sentence: String): Boolean = sentence.containsAny(
        "always", "never", "must", "should", "obviously", "everyone", "no one",
        "всегда", "никогда", "должен", "должна", "нужно", "обязан", "обязана", "нельзя", "очевидно", "никто"
    )

    private fun looksLikeCounterEvidence(sentence: String): Boolean = sentence.containsAny(
        "но ", "однако", "хотя", "недостаточно", "but ", "however", "although", "not enough"
    )

    private fun detailOf(sentence: String): String = sentence
        .removeSuffix(".")
        .removeSuffix("?")
        .take(120)

    private fun String.containsAny(vararg terms: String): Boolean {
        val normalized = lowercase()
        return terms.any(normalized::contains)
    }

    private fun String.startsWithAny(vararg terms: String): Boolean {
        val normalized = trimStart().lowercase()
        return terms.any(normalized::startsWith)
    }

    private fun isCyrillic(character: Char): Boolean =
        character in 'А'..'я' || character == 'Ё' || character == 'ё'

    private fun languageOf(text: String): Language {
        val cyrillicCount = text.count(::isCyrillic)
        val latinCount = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        return if (cyrillicCount > latinCount) Language.RUSSIAN else Language.ENGLISH
    }

    private enum class Language { RUSSIAN, ENGLISH }

    private enum class SentenceRole { OBSERVATION, INTERPRETATION, ASSUMPTION, COUNTER_EVIDENCE, QUESTION }

    private enum class Relation {
        SINGLE_EVENT_TO_GLOBAL_CLAIM,
        AMBIGUOUS_COMMUNICATION,
        FORCED_CHOICE,
        CAUSAL_WITH_COUNTER_EVIDENCE,
        PRAISE_TO_IDENTITY,
        OBSERVATIONAL,
        CAUTIOUS,
        GENERIC
    }

    private companion object {
        const val MAX_ITEMS = 4
    }
}
