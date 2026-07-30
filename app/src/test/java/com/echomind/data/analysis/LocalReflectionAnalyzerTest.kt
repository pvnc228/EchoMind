package com.echomind.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReflectionAnalyzerTest {

    private val analyzer = LocalReflectionAnalyzer()

    @Test
    fun `structures only material present in the original text`() {
        val proposal = analyzer.analyze(
            "Yesterday I saw the architect reject my draft. " +
                "I think that means I am not suited for this work because I failed once. " +
                "What am I missing?"
        )

        assertEquals(
            "I think that means I am not suited for this work because I failed once.",
            proposal.draft.tentativeThesis
        )
        assertEquals(
            listOf("Yesterday I saw the architect reject my draft."),
            proposal.draft.observations
        )
        assertEquals(1, proposal.draft.interpretations.size)
        assertEquals(listOf("What am I missing?"), proposal.draft.openQuestions)
        assertTrue(proposal.counterargument.contains("more than one cause"))
    }

    @Test
    fun `does not invent an observation when none is explicit`() {
        val proposal = analyzer.analyze("I think I should always get it right.")

        assertTrue(proposal.draft.observations.isEmpty())
        assertEquals(1, proposal.draft.assumptions.size)
        assertTrue(proposal.counterargument.contains("absolute wording"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank reflections`() {
        analyzer.analyze("   ")
    }
}
