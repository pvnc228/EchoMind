package com.echomind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.echomind.domain.model.ReflectionDraft
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.ReflectionStatus
import com.echomind.ui.detail.SavedReflectionProvenance
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
                onFollowUpQuestionChange = {},
                onConfirm = {},
                onReject = {},
                onContinueDiscussion = {},
                onRetry = {},
                onDone = {},
                onStartNew = {}
            )
        }

        composeTestRule.onNodeWithText("Your words · immutable source").assertIsDisplayed()
        composeTestRule.onNodeWithText("Proposed thesis").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local alternative").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("My edited conclusion")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Confirm my conclusion")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Reject EchoMind's proposal")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun savedConclusionShowsItsSourceProposalRevisionAndLink() {
        composeTestRule.setContent {
            SavedReflectionProvenance(
                ReflectionSession(
                    rawRecordId = 1,
                    hypothesisId = 2,
                    originalText = "My original words",
                    draft = ReflectionDraft(
                        tentativeThesis = "Tentative thesis",
                        observations = emptyList(),
                        interpretations = emptyList(),
                        assumptions = emptyList(),
                        openQuestions = emptyList()
                    ),
                    counterargument = "Alternative interpretation",
                    status = ReflectionStatus.CONFIRMED,
                    confirmedConclusion = "My edited conclusion",
                    revisionVersion = 1,
                    sourceRelationship = "supports",
                    sourceLinkStatus = ReflectionStatus.CONFIRMED
                )
            )
        }

        composeTestRule.onNodeWithText("Your words · raw source").assertIsDisplayed()
        composeTestRule.onNodeWithText("EchoMind · confirmed proposal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your conclusion · revision 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Source link · supports · confirmed").assertIsDisplayed()
    }

    @Test
    fun focusedFollowUpReviewKeepsQuestionAndProposalDistinct() {
        composeTestRule.setContent {
            RecordScreenContent(
                uiState = RecordUiState(
                    stage = ReflectionStage.REVIEW,
                    thoughtText = "Исходный текст",
                    rawRecordId = 1,
                    hypothesisId = 3,
                    draft = ReflectionDraft(
                        tentativeThesis = "Локальное предложение",
                        observations = emptyList(),
                        interpretations = emptyList(),
                        assumptions = emptyList(),
                        openQuestions = emptyList()
                    ),
                    counterargument = "Другая проверяемая трактовка",
                    confirmationText = "Моя формулировка",
                    followUpQuestion = "Какие данные изменят вывод?"
                ),
                onThoughtChange = {},
                onSubmit = {},
                onStartRecording = {},
                onStopRecording = {},
                onConfirmationChange = {},
                onFollowUpQuestionChange = {},
                onConfirm = {},
                onReject = {},
                onContinueDiscussion = {},
                onRetry = {},
                onDone = {},
                onStartNew = {}
            )
        }

        composeTestRule.onNodeWithText("Your focused question").assertIsDisplayed()
        composeTestRule.onNodeWithText("Какие данные изменят вывод?").assertIsDisplayed()
        composeTestRule.onNodeWithText("EchoMind's focused follow-up proposal").assertIsDisplayed()
    }
}
