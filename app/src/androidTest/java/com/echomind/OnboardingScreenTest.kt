package com.echomind

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.echomind.ui.onboarding.OnboardingScreen
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun firstPage_isTextFirstAndDoesNotPromiseAutomaticTranscription() {
        composeTestRule.setContent { OnboardingScreen(onComplete = {}) }

        composeTestRule.onNodeWithText("Text-first reflection").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Write a short thought in your own words. Voice is optional when it is more convenient.")
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Voice Diary").assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText("Every entry is automatically transcribed.")
            .assertCountEquals(0)
    }
}
