package com.echomind

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.echomind.domain.model.ThemeConclusion
import com.echomind.ui.themes.ThemeDetailScreenContent
import com.echomind.ui.themes.ThemeDetailUiState
import org.junit.Rule
import org.junit.Test

class ThemeDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun differentConclusionsWithRevisionOneRenderWithoutDuplicateKeys() {
        composeTestRule.setContent {
            ThemeDetailScreenContent(
                uiState = ThemeDetailUiState(
                    themeName = "Work",
                    conclusions = listOf(
                        ThemeConclusion(1L, "First conclusion", 1, 101L),
                        ThemeConclusion(1L, "Second conclusion", 1, 202L)
                    ),
                    conclusionsWithOutcome = setOf(202L),
                    isLoading = false
                ),
                onNavigateBack = {}
            )
        }

        composeTestRule.onAllNodesWithText("Revision 1").assertCountEquals(2)
        composeTestRule.onNodeWithText("First conclusion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Second conclusion").assertIsDisplayed()
        composeTestRule.onNodeWithText("· has outcome evidence").assertIsDisplayed()
        composeTestRule.onNodeWithText("· no outcome evidence").assertIsDisplayed()
    }
}
