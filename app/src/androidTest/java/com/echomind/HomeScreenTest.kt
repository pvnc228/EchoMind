package com.echomind

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echomind.ui.home.HomeScreen
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_showsEmptyState() {
        composeTestRule.setContent {
            HomeScreen(
                onNavigateToRecord = {},
                onNavigateToSearch = {},
                onNavigateToSettings = {}
            )
        }

        composeTestRule.onNodeWithText("No entries yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap + to record your first entry").assertIsDisplayed()
    }
}
