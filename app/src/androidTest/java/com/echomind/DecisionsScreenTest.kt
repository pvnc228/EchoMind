package com.echomind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.echomind.domain.model.OutcomeImpactReview
import com.echomind.ui.decisions.OutcomeImpactReviewCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DecisionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun impactReviewKeepsGroundsOutcomeAndProposalDistinctUntilConfirm() {
        var confirmedText: String? = null
        composeTestRule.setContent {
            OutcomeImpactReviewCard(
                review = OutcomeImpactReview(
                    decisionId = 1L,
                    sourceRevisionId = 2L,
                    originalText = "The original conclusion",
                    choice = "Continue",
                    outcomes = listOf("The result was better than expected."),
                    proposedText = "The original conclusion\nOutcome: better than expected."
                ),
                isSaving = false,
                onDismiss = {},
                onConfirm = { confirmedText = it }
            )
        }

        composeTestRule.onNodeWithText("Review impact").assertIsDisplayed()
        composeTestRule.onNodeWithText("Original grounds").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your choice").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reported outcome").assertIsDisplayed()
        composeTestRule.onNodeWithText("Proposed revision (diff)").assertIsDisplayed()
        composeTestRule.onNodeWithText("The original conclusion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm new revision").performClick()

        assertEquals(
            "The original conclusion\nOutcome: better than expected.",
            confirmedText
        )
    }
}
