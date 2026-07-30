package com.echomind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echomind.domain.model.ReflectionDraft
import com.echomind.ui.record.RecordScreenContent
import com.echomind.ui.record.RecordUiState
import com.echomind.ui.record.ReflectionStage
import org.junit.Rule
import org.junit.Test

class ReflectionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun reviewDistinguishesSourceProposalAndUserConfirmation() {
        composeTestRule.setContent {
            RecordScreenContent(
                uiState = RecordUiState(
                    stage = ReflectionStage.REVIEW,
                    thoughtText = "My original words",
                    rawRecordId = 1,
                    hypothesisId = 2,
                    draft = ReflectionDraft(
                        tentativeThesis = "Tentative thesis",
                        observations = listOf("Observed event"),
                        interpretations = listOf("Current interpretation"),
                        assumptions = emptyList(),
                        openQuestions = listOf("What would change my mind?")
                    ),
                    counterargument = "Alternative interpretation",
                    confirmationText = "My edited conclusion"
                ),
                onThoughtChange = {},
                onSubmit = {},
                onStartRecording = {},
                onStopRecording = {},
                onConfirmationChange = {},
                onConfirm = {},
                onReject = {},
                onRetry = {},
                onDone = {}
            )
        }

        composeTestRule.onNodeWithText("Your words · immutable source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local proposal · structured draft").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your confirmed wording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm conclusion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reject proposal").assertIsDisplayed()
    }
}
