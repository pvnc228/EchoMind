package com.echomind

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionOutcome
import com.echomind.domain.model.OutcomeImpactReview
import com.echomind.ui.decisions.OutcomeImpactReviewCard
import com.echomind.ui.decisions.DecisionsScreenContent
import com.echomind.ui.decisions.DecisionsUiState
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

    @Test
    fun decisionActionsRemainReachableAtCompactWidthAndLargeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(modifier = androidx.compose.ui.Modifier.width(320.dp).height(640.dp)) {
                    DecisionsScreenContent(
                        uiState = DecisionsUiState(
                            isLoading = false,
                            decisions = listOf(
                                Decision(
                                    id = 1L,
                                    question = "Which option should I choose for this important decision?",
                                    choice = "Continue with the selected option",
                                    createdAt = 1L
                                )
                            )
                        ),
                        modifier = androidx.compose.ui.Modifier.fillMaxSize()
                    )
                }
            }
        }

        listOf("Change choice", "Report outcome", "Delete").forEach { action ->
            composeTestRule.onNodeWithText(action)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }

    @Test
    fun decisionActionsRemainReachableInLandscape() {
        composeTestRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.width(640.dp).height(320.dp)) {
                DecisionsScreenContent(
                    uiState = DecisionsUiState(
                        isLoading = false,
                        decisions = listOf(
                            Decision(
                                id = 1L,
                                question = "Landscape decision",
                                choice = "Continue",
                                createdAt = 1L
                            )
                        )
                    ),
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.onNodeWithText("Change choice")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun impactReviewKeepsImeFieldAndTalkBackActionsReachable() {
        composeTestRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.width(320.dp).height(640.dp)) {
                DecisionsScreenContent(
                    uiState = DecisionsUiState(
                        isLoading = false,
                        decisions = listOf(
                            Decision(
                                id = 1L,
                                question = "Decision with a reported outcome",
                                choice = "Continue",
                                createdAt = 1L,
                                outcomes = listOf(
                                    DecisionOutcome(
                                        id = 2L,
                                        decisionId = 1L,
                                        report = "The outcome was different than expected.",
                                        createdAt = 2L
                                    )
                                )
                            )
                        ),
                        impactDecisionId = 1L,
                        impactReview = OutcomeImpactReview(
                            decisionId = 1L,
                            sourceRevisionId = 3L,
                            originalText = "Original grounds",
                            choice = "Continue",
                            outcomes = listOf("The outcome was different than expected."),
                            proposedText = "A revised conclusion"
                        )
                    ),
                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule
            .onNodeWithText("Your revised conclusion")
            .performTextInput(" with new evidence")
        composeTestRule.onNodeWithText("Confirm new revision")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithText("Keep current conclusion")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
