package com.echomind.ui.guidance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echomind.data.remote.GuidanceGrounds
import com.echomind.data.remote.GuidancePreview
import com.echomind.data.remote.dto.Message
import com.echomind.ui.theme.EchoMindTheme
import org.junit.Rule
import org.junit.Test

class GuidanceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun guidancePreviewShowsExactEvidenceAndOneShotActions() {
        val preview = GuidancePreview(
            requestId = "guidance-1",
            purpose = "guidance_from_confirmed_conclusions",
            destination = "https://provider.example",
            question = "planning",
            grounds = listOf(
                GuidanceGrounds(
                    conclusionId = 8L,
                    revisionId = 9L,
                    version = 2,
                    entryId = 7L,
                    text = "confirmed planning conclusion",
                    supports = listOf("a supporting planning record"),
                    contradictions = listOf("an opposing planning record"),
                    outcomes = listOf("the plan worked")
                )
            ),
            messages = listOf(
                Message("system", "grounded evidence"),
                Message("user", "planning")
            ),
            sourceEntryIds = listOf(7L)
        )

        composeRule.setContent {
            EchoMindTheme {
                GuidanceScreenContent(
                    uiState = GuidanceUiState(pendingPreview = preview),
                    onNavigateBack = {},
                    onApproveRemoteRequest = {},
                    onCancelRemoteRequest = {}
                )
            }
        }

        composeRule.onNodeWithText("Review guidance request").assertIsDisplayed()
        composeRule.onNodeWithText("https://provider.example").assertIsDisplayed()
        composeRule.onNodeWithText("  supports: a supporting planning record").assertIsDisplayed()
        composeRule.onNodeWithText("  contradicts: an opposing planning record").assertIsDisplayed()
        composeRule.onNodeWithText("  reported outcome: the plan worked").assertIsDisplayed()
        composeRule.onNode(hasClickAction() and hasText("Allow once")).assertIsDisplayed()
    }

    @Test
    fun refusalIsShownAsNonErrorNotice() {
        composeRule.setContent {
            EchoMindTheme {
                GuidanceScreenContent(
                    uiState = GuidanceUiState(
                        refusal = "EchoMind cannot diagnose or infer hidden motives."
                    ),
                    onNavigateBack = {}
                )
            }
        }

        composeRule.onNodeWithText("EchoMind cannot diagnose or infer hidden motives.").assertIsDisplayed()
    }
}
