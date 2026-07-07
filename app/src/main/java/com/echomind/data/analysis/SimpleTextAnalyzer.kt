package com.echomind.data.analysis

import com.echomind.domain.model.Entry
import com.echomind.domain.model.EntryCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleTextAnalyzer @Inject constructor() {

    fun analyze(entry: Entry): Entry {
        val text = entry.transcript

        val summary = extractSummary(text)
        val tasks = extractTasks(text)
        val ideas = extractIdeas(text)
        val emotions = extractEmotions(text)
        val tags = extractTags(text)
        val category = classifyCategory(text, tasks, ideas, emotions)

        return entry.copy(
            summary = summary,
            tasks = tasks,
            ideas = ideas,
            emotions = emotions,
            category = category,
            tags = tags
        )
    }

    private fun extractSummary(text: String): String {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        return sentences.firstOrNull()?.take(200) ?: text.take(200)
    }

    private fun extractTasks(text: String): List<String> {
        val taskPatterns = listOf(
            Regex("(?i)(?:^|\\n)\\s*[-*]\\s*\\[.?\\]\\s*(.+)"),
            Regex("(?i)(?:^|\\n)\\s*(?:todo|task|to-do|action item)[:\\s]+(.+)"),
            Regex("(?i)(?:^|\\n)\\s*(?:need to|must|should|have to)\\s+(.+)"),
            Regex("(?i)(?:^|\\n)\\s*(?:remind me to|remember to|don't forget to)\\s+(.+)")
        )
        return taskPatterns.flatMap { pattern ->
            pattern.findAll(text).map { it.groupValues[1].trim() }.toList()
        }.distinct().take(10)
    }

    private fun extractIdeas(text: String): List<String> {
        val ideaPatterns = listOf(
            Regex("(?i)(?:^|\\n)\\s*(?:idea|maybe|what if|how about|wouldn't it|imagine)[:\\s]+(.+)"),
            Regex("(?i)(?:^|\\n)\\s*(?:I think|I wonder|perhaps)(?:\\s+that)?\\s+(.+)"),
            Regex("(?i)\\b(?:could|might)\\s+(?:be|have|make|create|try)\\b.*?(?=[.!])")
        )
        return ideaPatterns.flatMap { pattern ->
            pattern.findAll(text).map { it.groupValues[1].trim() }.toList()
        }.distinct().take(10)
    }

    private fun extractEmotions(text: String): List<String> {
        val emotionKeywords = mapOf(
            "happy" to listOf("happy", "glad", "joy", "wonderful", "great", "excellent", "delighted", "thrilled", "amazing"),
            "sad" to listOf("sad", "unhappy", "depressed", "down", "miserable", "heartbroken", "disappointed", "gloomy"),
            "angry" to listOf("angry", "mad", "furious", "irritated", "annoyed", "frustrated", "outraged", "livid"),
            "anxious" to listOf("anxious", "worried", "nervous", "stressed", "uneasy", "concerned", "fearful", "panicked"),
            "grateful" to listOf("grateful", "thankful", "appreciative", "blessed", "fortunate"),
            "hopeful" to listOf("hopeful", "optimistic", "encouraged", "positive", "promising", "bright"),
            "tired" to listOf("tired", "exhausted", "fatigued", "drained", "weary", "burned out", "overwhelmed"),
            "loved" to listOf("love", "loved", "cherished", "appreciated", "valued", "cared for"),
            "lonely" to listOf("lonely", "alone", "isolated", "disconnected", "miss", "longing"),
            "excited" to listOf("excited", "eager", "enthusiastic", "pumped", "energized", "motivated"),
            "confused" to listOf("confused", "uncertain", "unsure", "puzzled", "perplexed", "lost", "ambivalent"),
            "calm" to listOf("calm", "peaceful", "relaxed", "serene", "content", "at ease", "tranquil")
        )

        val textLower = text.lowercase()
        return emotionKeywords.filter { (_, keywords) ->
            keywords.any { textLower.contains(it) }
        }.keys.toList().take(5)
    }

    private fun extractTags(text: String): List<String> {
        val hashtags = Regex("#(\\w+)").findAll(text).map { it.groupValues[1] }.toList()
        val capitalized = Regex("\\b([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,})?)\\b")
            .findAll(text)
            .map { it.groupValues[1] }
            .filter { word ->
                word.length > 3 &&
                word.lowercase() !in stopWords &&
                !word.all { it.isUpperCase() }
            }
            .toList()

        return (hashtags + capitalized).distinct().take(8)
    }

    private fun classifyCategory(
        text: String,
        tasks: List<String>,
        ideas: List<String>,
        emotions: List<String>
    ): EntryCategory {
        val textLower = text.lowercase()

        if (tasks.isNotEmpty() || textLower.containsAny(listOf(
                "need to", "must", "should", "have to", "deadline", "due ",
                "remind", "schedule", "appointment", "meeting", "plan to"
            ))
        ) return EntryCategory.TASK

        if (ideas.isNotEmpty() || textLower.containsAny(listOf(
                "maybe", "what if", "imagine", "idea", "how about",
                "wouldn't it", "could be", "might be", "create", "invent"
            ))
        ) return EntryCategory.IDEA

        if (emotions.isNotEmpty() || textLower.containsAny(listOf(
                "feel", "feeling", "emotion", "angry", "sad", "happy",
                "worried", "anxious", "depressed", "grateful", "lonely"
            ))
        ) return EntryCategory.FEELING

        if (textLower.containsAny(listOf(
                "plan", "planning", "prepare", "preparing", "tomorrow",
                "next week", "goal", "objective", "strategy", "roadmap"
            ))
        ) return EntryCategory.PLAN

        return EntryCategory.GENERAL
    }

    private fun String.containsAny(terms: List<String>): Boolean =
        terms.any { contains(it) }

    companion object {
        private val stopWords = setOf(
            "this", "that", "these", "those", "with", "from", "have", "been",
            "were", "was", "being", "would", "could", "should", "about",
            "there", "their", "which", "when", "where", "what", "they",
            "them", "said", "just", "also", "very", "well", "then",
            "than", "into", "over", "after", "other", "still", "more",
            "some", "such", "only", "even", "much", "each", "many",
            "will", "your", "make", "like", "know", "think", "people",
            "back", "take", "good", "time", "year", "life", "work"
        )
    }
}
