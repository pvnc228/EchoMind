package com.echomind.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRelevanceBuilderTest {

    private val now = 10_000_000L

    @Test
    fun `coverage retains empty theme and unthemed current conclusion`() {
        val result = HomeRelevanceBuilder.build(
            listOf(
                ThemeCandidate(
                    themeId = 1,
                    name = "Empty",
                    conclusionCount = 0,
                    evidenceCount = 0,
                    contradictionCount = 0,
                    evidenceState = EvidenceState.EMPTY_THEME,
                    lastGraphChangeAt = now
                ),
                ThemeCandidate(
                    themeId = 0,
                    name = "",
                    conclusionCount = 1,
                    evidenceCount = 0,
                    contradictionCount = 0,
                    scopeType = CoverageScopeType.UNTHEMED,
                    scopeId = 42,
                    currentRevisionIds = listOf(7),
                    evidenceState = EvidenceState.NO_EXTERNAL_EVIDENCE,
                    lastGraphChangeAt = now
                )
            ),
            now
        )

        assertEquals(2, result.coverage.size)
        assertEquals(EvidenceState.EMPTY_THEME, result.coverage[0].evidenceState)
        assertEquals(CoverageScopeType.UNTHEMED, result.coverage[1].scopeType)
        assertTrue(result.hasKnowledge)
    }

    @Test
    fun `contradiction is immediate and outranks thin evidence`() {
        val result = HomeRelevanceBuilder.build(
            listOf(
                candidate(1, "Thin", EvidenceState.NO_EXTERNAL_EVIDENCE, now - HomeRelevanceBuilder.DAY_MS),
                candidate(2, "Conflict", EvidenceState.CONTRADICTED, now)
            ),
            now
        )

        assertEquals(HomeCardType.CONTRADICTION, result.card?.type)
        assertEquals(2L, result.card?.scopeId)
        assertTrue(result.card?.currentRevisionIds!!.isNotEmpty())
    }

    @Test
    fun `thin and supported cards wait for exact thresholds`() {
        val thin = candidate(1, "Thin", EvidenceState.NO_EXTERNAL_EVIDENCE, now - HomeRelevanceBuilder.DAY_MS + 1)
        val supported = candidate(2, "Supported", EvidenceState.SUPPORTED, now - HomeRelevanceBuilder.WEEK_MS + 1, evidence = 5)

        assertNull(HomeRelevanceBuilder.build(listOf(thin), now).card)
        assertNull(HomeRelevanceBuilder.build(listOf(supported), now).card)
        assertEquals(
            HomeCardType.THIN_EVIDENCE,
            HomeRelevanceBuilder.build(
                listOf(thin.copy(lastGraphChangeAt = now - HomeRelevanceBuilder.DAY_MS)), now
            ).card?.type
        )
        assertEquals(
            HomeCardType.SUPPORTED_THEME,
            HomeRelevanceBuilder.build(
                listOf(supported.copy(lastGraphChangeAt = now - HomeRelevanceBuilder.WEEK_MS)), now
            ).card?.type
        )
    }

    @Test
    fun `tie breaks use newest graph change then scope and revision`() {
        val result = HomeRelevanceBuilder.build(
            listOf(
                candidate(7, "B", EvidenceState.CONTRADICTED, now - 100, revision = 20),
                candidate(3, "A", EvidenceState.CONTRADICTED, now - 100, revision = 30),
                candidate(3, "A newer", EvidenceState.CONTRADICTED, now - 50, revision = 40)
            ),
            now
        )

        assertEquals(3L, result.card?.scopeId)
        assertEquals(listOf(40L), result.card?.currentRevisionIds)
    }

    @Test
    fun `fingerprint changes when graph identity changes but not when theme name changes`() {
        val first = candidate(1, "A", EvidenceState.CONTRADICTED, now, linkIds = listOf(4))
        val renamed = first.copy(name = "Renamed")
        val changed = first.copy(relevantLinkIds = listOf(5))

        val firstKey = HomeRelevanceBuilder.build(listOf(first), now).card!!.cardKey
        assertEquals(firstKey, HomeRelevanceBuilder.build(listOf(renamed), now).card!!.cardKey)
        assertNotEquals(firstKey, HomeRelevanceBuilder.build(listOf(changed), now).card!!.cardKey)
    }

    private fun candidate(
        scopeId: Long,
        name: String,
        state: EvidenceState,
        changedAt: Long,
        evidence: Int = 0,
        revision: Long = scopeId * 10,
        linkIds: List<Long> = emptyList()
    ) = ThemeCandidate(
        themeId = scopeId,
        name = name,
        conclusionCount = 1,
        evidenceCount = evidence,
        contradictionCount = if (state == EvidenceState.CONTRADICTED) 1 else 0,
        currentRevisionIds = listOf(revision),
        evidenceState = state,
        lastGraphChangeAt = changedAt,
        relevantLinkIds = linkIds
    )
}
