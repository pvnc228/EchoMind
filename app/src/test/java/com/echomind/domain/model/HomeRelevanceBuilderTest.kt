package com.echomind.domain.model

import com.echomind.domain.model.HomeCardType.CONTRADICTION
import com.echomind.domain.model.HomeCardType.THEME
import com.echomind.domain.model.HomeCardType.THIN_THEME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRelevanceBuilderTest {

    @Test
    fun `empty candidates build empty relevance`() {
        val result = HomeRelevanceBuilder.build(emptyList())
        assertNull(result.card)
        assertEquals(false, result.hasKnowledge)
    }

    @Test
    fun `contradiction takes priority`() {
        val result = HomeRelevanceBuilder.build(
            listOf(
                ThemeCandidate(1, "A", 2, 3, contradictionCount = 1),
                ThemeCandidate(2, "B", 5, 8, contradictionCount = 0)
            )
        )
        assertEquals(CONTRADICTION, result.card?.type)
        assertEquals("A", result.card?.themeName)
    }

    @Test
    fun `conclusion without evidence is flagged`() {
        val result = HomeRelevanceBuilder.build(
            listOf(
                ThemeCandidate(1, "A", conclusionCount = 1, evidenceCount = 0, contradictionCount = 0)
            )
        )
        assertEquals(THIN_THEME, result.card?.type)
        assertEquals("A", result.card?.themeName)
    }

    @Test
    fun `most supported theme is picked by evidenceCount`() {
        val result = HomeRelevanceBuilder.build(
            listOf(
                ThemeCandidate(1, "A", 1, 2, contradictionCount = 0),
                ThemeCandidate(2, "B", 1, 5, contradictionCount = 0)
            )
        )
        assertEquals(THEME, result.card?.type)
        assertEquals("B", result.card?.themeName)
        assertEquals(2, result.coverage.size)
    }
}
