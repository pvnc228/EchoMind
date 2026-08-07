package com.echomind.domain.model

data class ThemeCoverage(
    val themeId: Long,
    val name: String,
    val conclusionCount: Int,
    val evidenceCount: Int
)

data class ThemeCandidate(
    val themeId: Long,
    val name: String,
    val conclusionCount: Int,
    val evidenceCount: Int,
    val contradictionCount: Int
)

enum class HomeCardType { CONTRADICTION, THIN_THEME, THEME }

data class HomeCard(
    val type: HomeCardType,
    val themeId: Long,
    val themeName: String,
    val title: String,
    val detail: String,
    val reason: String
)

data class HomeRelevance(
    val card: HomeCard? = null,
    val coverage: List<ThemeCoverage> = emptyList(),
    val hasKnowledge: Boolean = false
)

object HomeRelevanceBuilder {
    fun build(candidates: List<ThemeCandidate>): HomeRelevance {
        val coverage = candidates.map {
            ThemeCoverage(it.themeId, it.name, it.conclusionCount, it.evidenceCount)
        }
        val contradiction = candidates.firstOrNull { it.contradictionCount > 0 }
        if (contradiction != null) {
            return HomeRelevance(
                card = HomeCard(
                    type = HomeCardType.CONTRADICTION,
                    themeId = contradiction.themeId,
                    themeName = contradiction.name,
                    title = "Contradicting evidence in \"${contradiction.name}\"",
                    detail = "${contradiction.conclusionCount} conclusion(s) vs " +
                        "${contradiction.contradictionCount} contradicting record(s).",
                    reason = "Shown because opposing records challenge this conclusion."
                ),
                coverage = coverage,
                hasKnowledge = true
            )
        }
        val thin = candidates.firstOrNull { it.conclusionCount > 0 && it.evidenceCount == 0 }
        if (thin != null) {
            return HomeRelevance(
                card = HomeCard(
                    type = HomeCardType.THIN_THEME,
                    themeId = thin.themeId,
                    themeName = thin.name,
                    title = "\"${thin.name}\" has a conclusion with no evidence yet",
                    detail = "A conclusion exists but no records support it yet.",
                    reason = "Shown because this conclusion needs supporting records."
                ),
                coverage = coverage,
                hasKnowledge = true
            )
        }
        val theme = candidates.maxByOrNull { it.evidenceCount }
        if (theme != null) {
            return HomeRelevance(
                card = HomeCard(
                    type = HomeCardType.THEME,
                    themeId = theme.themeId,
                    themeName = theme.name,
                    title = "\"${theme.name}\" is the most supported theme",
                    detail = "${theme.conclusionCount} confirmed conclusion(s) backed by " +
                        "${theme.evidenceCount} record(s).",
                    reason = "Shown as the theme with the most evidence you can continue."
                ),
                coverage = coverage,
                hasKnowledge = true
            )
        }
        return HomeRelevance(card = null, coverage = coverage, hasKnowledge = false)
    }
}
