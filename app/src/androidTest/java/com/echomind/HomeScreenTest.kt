package com.echomind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echomind.ui.home.HomeScreenContent
import com.echomind.ui.home.HomeUiState
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_showsEmptyState() {
        composeTestRule.setContent {
            HomeScreenContent(
                uiState = HomeUiState(isLoading = false),
                onNavigateToRecord = {},
                onNavigateToSearch = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("Start with one thought").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap + to write your first reflection").assertIsDisplayed()
    }
}
