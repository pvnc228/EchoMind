package com.echomind

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.echomind.domain.model.Capability
import com.echomind.domain.model.HomeCard
import com.echomind.domain.model.HomeCardType
import com.echomind.domain.model.HomeNavigationTarget
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

    @Test
    fun relevantCardActionsRemainReachableAtCompactWidthAndLargeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                    HomeScreenContent(
                        uiState = HomeUiState(
                            card = HomeCard(
                                type = HomeCardType.CONTRADICTION,
                                themeId = 1L,
                                themeName = "Work",
                                title = "Contradicting evidence in Work",
                                detail = "2 current conclusions, 1 contradiction.",
                                reason = "The evidence changed.",
                                capability = Capability.CONNECTION,
                                navigationTarget = HomeNavigationTarget.Theme(1L)
                            ),
                            hasKnowledge = true,
                            isLoading = false
                        ),
                        onNavigateToRecord = {},
                        onNavigateToSearch = {},
                        onNavigateToSettings = {}
                    )
                }
            }
        }

        listOf("Inspect", "Continue", "Dismiss", "Later").forEach { action ->
            composeTestRule.onNodeWithText(action)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
    }
}
