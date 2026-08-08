package com.echomind.domain.model

import java.security.MessageDigest

enum class CoverageScopeType { THEME, UNTHEMED }

sealed interface HomeNavigationTarget {
    data class Theme(val themeId: Long) : HomeNavigationTarget
    data class Entry(val entryId: Long) : HomeNavigationTarget
    data class ReflectionProposal(val hypothesisId: Long) : HomeNavigationTarget
}

enum class EvidenceState {
    EMPTY_THEME,
    NO_EXTERNAL_EVIDENCE,
    SUPPORTED,
    CONTRADICTED
}

data class CoverageItem(
    val scopeType: CoverageScopeType,
    val scopeId: Long,
    val name: String,
    val currentRevisionIds: List<Long>,
    val evidenceState: EvidenceState,
    val hasOutcome: Boolean,
    val externalEvidenceCount: Int,
    val contradictionCount: Int,
    val lastGraphChangeAt: Long,
    val navigationTarget: HomeNavigationTarget? = null
) {
    // Compatibility projections for existing callers while the UI migrates to typed state.
    val themeId: Long get() = if (scopeType == CoverageScopeType.THEME) scopeId else 0L
    val conclusionCount: Int get() = currentRevisionIds.size
    val evidenceCount: Int get() = externalEvidenceCount
}

typealias ThemeCoverage = CoverageItem

/**
 * Input to the deterministic Home policy. The first five fields retain the old constructor
 * shape so existing fixtures remain readable; the remaining fields carry provenance and time.
 */
data class ThemeCandidate(
    val themeId: Long,
    val name: String,
    val conclusionCount: Int,
    val evidenceCount: Int,
    val contradictionCount: Int,
    val scopeType: CoverageScopeType = CoverageScopeType.THEME,
    val scopeId: Long = themeId,
    val currentRevisionIds: List<Long> = emptyList(),
    val evidenceState: EvidenceState? = null,
    val hasOutcome: Boolean = false,
    val lastGraphChangeAt: Long = 0L,
    val relevantLinkIds: List<Long> = emptyList(),
    val relevantSourceRevisionKeys: List<String> = emptyList(),
    val sourceRawRecordIds: List<Long> = emptyList(),
    val relevantOutcomeIds: List<Long> = emptyList(),
    val unfinishedSince: Long? = null,
    val navigationTarget: HomeNavigationTarget? = null
)

enum class HomeCardType {
    CONTRADICTION,
    UNFINISHED,
    THIN_EVIDENCE,
    SUPPORTED_THEME
}

enum class Capability(val label: String) {
    REFLECTION("Reflection"),
    CONNECTION("Connection"),
    CHANGE_TRACKING("Change tracking"),
    GUIDANCE("Guidance")
}

data class HomeCard(
    val type: HomeCardType,
    val themeId: Long,
    val themeName: String,
    val title: String,
    val detail: String,
    val reason: String,
    val capability: Capability,
    val cardKey: String = "",
    val scopeType: CoverageScopeType = CoverageScopeType.THEME,
    val scopeId: Long = themeId,
    val currentRevisionIds: List<Long> = emptyList(),
    val sourceRawRecordIds: List<Long> = emptyList(),
    val evidenceState: EvidenceState? = null,
    val hasOutcome: Boolean = false,
    val lastGraphChangeAt: Long = 0L,
    val navigationTarget: HomeNavigationTarget? = null
)

data class HomeRelevance(
    val card: HomeCard? = null,
    val coverage: List<ThemeCoverage> = emptyList(),
    val hasKnowledge: Boolean = false,
    val legacySuppressionReset: Boolean = false
)

object HomeRelevanceBuilder {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val WEEK_MS = 7L * DAY_MS

    fun build(
        candidates: List<ThemeCandidate>,
        now: Long,
        suppressedCardKeys: Set<String> = emptySet()
    ): HomeRelevance {
        val coverage = candidates
            .filter { it.unfinishedSince == null }
            .sortedWith(compareBy<ThemeCandidate> { it.scopeType.ordinal }.thenBy { it.scopeId })
            .map { it.toCoverageItem() }
        val eligible = candidates.mapNotNull { candidate ->
            val state = candidate.resolvedEvidenceState()
            val type = when {
                state == EvidenceState.CONTRADICTED -> HomeCardType.CONTRADICTION
                candidate.unfinishedSince != null &&
                    now >= candidate.unfinishedSince + DAY_MS -> HomeCardType.UNFINISHED
                state == EvidenceState.NO_EXTERNAL_EVIDENCE &&
                    candidate.currentRevisionIds.isNotEmpty() &&
                    now >= candidate.lastGraphChangeAt + DAY_MS -> HomeCardType.THIN_EVIDENCE
                state == EvidenceState.SUPPORTED &&
                    now >= candidate.lastGraphChangeAt + WEEK_MS -> HomeCardType.SUPPORTED_THEME
                else -> return@mapNotNull null
            }
            type to candidate
        }.sortedWith(
            compareBy<Pair<HomeCardType, ThemeCandidate>> { it.first.tier() }
                .thenByDescending { it.second.lastGraphChangeAt }
                .thenByDescending {
                    if (it.first == HomeCardType.SUPPORTED_THEME) it.second.evidenceCount else 0
                }
                .thenBy { it.second.scopeId }
                .thenBy { it.second.currentRevisionIds.minOrNull() ?: Long.MAX_VALUE }
        )

        val card = eligible.asSequence()
            .map { (type, candidate) -> candidate.toCard(type) }
            .firstOrNull { it.cardKey !in suppressedCardKeys }
        return HomeRelevance(card = card, coverage = coverage, hasKnowledge = candidates.isNotEmpty())
    }

    private fun ThemeCandidate.toCoverageItem(): CoverageItem = CoverageItem(
        scopeType = scopeType,
        scopeId = scopeId,
        name = name,
        currentRevisionIds = currentRevisionIds,
        evidenceState = resolvedEvidenceState(),
        hasOutcome = hasOutcome,
        externalEvidenceCount = evidenceCount,
        contradictionCount = contradictionCount,
        lastGraphChangeAt = lastGraphChangeAt,
        navigationTarget = navigationTarget ?: when (scopeType) {
            CoverageScopeType.THEME -> HomeNavigationTarget.Theme(scopeId)
            CoverageScopeType.UNTHEMED -> null
        }
    )

    private fun ThemeCandidate.toCard(type: HomeCardType): HomeCard {
        val state = resolvedEvidenceState()
        val displayName = name.ifBlank { "Unthemed reflection" }
        val title = when (type) {
            HomeCardType.CONTRADICTION -> "Contradicting evidence in \"$displayName\""
            HomeCardType.UNFINISHED -> "An unfinished reflection needs a review"
            HomeCardType.THIN_EVIDENCE -> "\"$displayName\" has no external evidence yet"
            HomeCardType.SUPPORTED_THEME -> "\"$displayName\" is ready for a new check-in"
        }
        val detail = when (type) {
            HomeCardType.CONTRADICTION ->
                "${currentRevisionIds.size} current conclusion(s), $contradictionCount contradiction(s)."
            HomeCardType.UNFINISHED -> "A local proposal has been waiting for your review."
            HomeCardType.THIN_EVIDENCE -> "Current conclusions exist, but no confirmed external records support them."
            HomeCardType.SUPPORTED_THEME ->
                "${currentRevisionIds.size} current conclusion(s) backed by $evidenceCount external record(s)."
        }
        val reason = when (type) {
            HomeCardType.CONTRADICTION -> "Shown because opposing records challenge this conclusion."
            HomeCardType.UNFINISHED -> "Shown because a proposal has had no action for at least 24 hours."
            HomeCardType.THIN_EVIDENCE -> "Shown because the last confirmed graph change was at least 24 hours ago."
            HomeCardType.SUPPORTED_THEME -> "Shown because the last supported graph change was at least 7 days ago."
        }
        val capability = when (type) {
            HomeCardType.CONTRADICTION -> Capability.CHANGE_TRACKING
            HomeCardType.UNFINISHED, HomeCardType.THIN_EVIDENCE -> Capability.REFLECTION
            HomeCardType.SUPPORTED_THEME -> Capability.CONNECTION
        }
        return HomeCard(
            type = type,
            themeId = themeId,
            themeName = displayName,
            title = title,
            detail = detail,
            reason = reason,
            capability = capability,
            cardKey = fingerprint(type, scopeId, currentRevisionIds, relevantLinkIds,
                relevantSourceRevisionKeys, relevantOutcomeIds),
            scopeType = scopeType,
            scopeId = scopeId,
            currentRevisionIds = currentRevisionIds,
            sourceRawRecordIds = sourceRawRecordIds.ifEmpty {
                relevantSourceRevisionKeys.mapNotNull { it.substringBefore(':').toLongOrNull() }
            },
            evidenceState = state,
            hasOutcome = hasOutcome,
            lastGraphChangeAt = lastGraphChangeAt,
            navigationTarget = navigationTarget ?: when (scopeType) {
                CoverageScopeType.THEME -> HomeNavigationTarget.Theme(scopeId)
                CoverageScopeType.UNTHEMED -> null
            }
        )
    }

    private fun ThemeCandidate.resolvedEvidenceState(): EvidenceState = evidenceState ?: when {
        scopeType == CoverageScopeType.THEME && conclusionCount == 0 -> EvidenceState.EMPTY_THEME
        contradictionCount > 0 -> EvidenceState.CONTRADICTED
        evidenceCount > 0 -> EvidenceState.SUPPORTED
        else -> EvidenceState.NO_EXTERNAL_EVIDENCE
    }

    private fun HomeCardType.tier(): Int = when (this) {
        HomeCardType.CONTRADICTION -> 0
        HomeCardType.UNFINISHED -> 1
        HomeCardType.THIN_EVIDENCE -> 2
        HomeCardType.SUPPORTED_THEME -> 3
    }

    private fun fingerprint(
        type: HomeCardType,
        scopeId: Long,
        revisionIds: List<Long>,
        linkIds: List<Long>,
        sourceRevisionKeys: List<String>,
        outcomeIds: List<Long>
    ): String {
        val canonical = buildString {
            append(type.name).append('|').append(scopeId).append('|')
            append(revisionIds.sorted().joinToString(",")).append('|')
            append(linkIds.sorted().joinToString(",")).append('|')
            append(sourceRevisionKeys.sorted().joinToString(",")).append('|')
            append(outcomeIds.sorted().joinToString(","))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
