package com.echomind.ui.qa

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echomind.data.remote.RemoteQuestionPreview
import com.echomind.data.remote.dto.Message
import com.echomind.ui.theme.EchoMindTheme
import org.junit.Rule
import org.junit.Test

class QaScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun remotePreviewShowsExactPayloadAndSeparateOneShotActions() {
        val preview = RemoteQuestionPreview(
            requestId = "request-1",
            purpose = "answer_question_from_confirmed_conclusions",
            destination = "https://provider.example",
            question = "What changed?",
            context = emptyList(),
            messages = listOf(
                Message("system", "confirmed context"),
                Message("user", "What changed?")
            ),
            sourceEntryIds = listOf(7L)
        )

        composeRule.setContent {
            EchoMindTheme {
                QaScreenContent(
                    uiState = QaUiState(pendingPreview = preview),
                    onNavigateBack = {},
                    onApproveRemoteRequest = {},
                    onCancelRemoteRequest = {}
                )
            }
        }

        composeRule.onNodeWithText("Review remote request").assertIsDisplayed()
        composeRule.onNodeWithText("https://provider.example").assertIsDisplayed()
        composeRule.onNodeWithText("system: confirmed context").assertIsDisplayed()
        composeRule.onNodeWithText("user: What changed?").assertIsDisplayed()
        composeRule.onNode(hasClickAction() and androidx.compose.ui.test.hasText("Allow once"))
            .assertIsDisplayed()
    }
}
