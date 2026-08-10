package com.echomind

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.echomind.domain.model.RelatedRecord
import com.echomind.ui.detail.ConnectionsSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun manualPickerKeepsFilterAndLoadMoreReachableAtCompactWidthAndLargeText() {
        var query = ""
        var loadMoreCount = 0
        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                var renderedQuery by remember { mutableStateOf("") }
                Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                    ConnectionsSection(
                        themes = emptyList(),
                        availableThemes = emptyList(),
                        pendingThemes = emptyList(),
                        relatedRecords = emptyList(),
                        pendingRelatedRecords = emptyList(),
                        otherEntries = listOf(candidate()),
                        manualCandidates = listOf(candidate()),
                        manualCandidatesHasMore = true,
                        isManualLoading = false,
                        manualQuery = renderedQuery,
                        revisionId = 1L,
                        onLinkToTheme = { _, _ -> },
                        onUnlinkFromTheme = { _, _ -> },
                        onLinkRelated = { _, _, _ -> },
                        onUnlinkRelated = { _, _ -> },
                        onReviewPendingTheme = { _, _ -> },
                        onReviewPendingRelated = { _, _ -> },
                        onSearchManual = {
                            query = it
                            renderedQuery = it
                        },
                        onLoadMoreManual = { loadMoreCount++ }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Browse or search records...").performClick()
        composeTestRule.onNodeWithText("Filter records")
            .assertIsDisplayed()
            .performTextInput("карьера")
        composeTestRule.onNodeWithText("Load more records")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        assertEquals("карьера", query)
        assertEquals(1, loadMoreCount)
    }

    @Test
    fun manualPickerKeepsRecordActionReachableInLandscape() {
        composeTestRule.setContent {
            Box(modifier = Modifier.width(640.dp).height(320.dp)) {
                ConnectionsSection(
                    themes = emptyList(),
                    availableThemes = emptyList(),
                    pendingThemes = emptyList(),
                    relatedRecords = emptyList(),
                    pendingRelatedRecords = emptyList(),
                    otherEntries = listOf(candidate()),
                    manualCandidates = listOf(candidate()),
                    manualCandidatesHasMore = false,
                    isManualLoading = false,
                    manualQuery = "",
                    revisionId = 1L,
                    onLinkToTheme = { _, _ -> },
                    onUnlinkFromTheme = { _, _ -> },
                    onLinkRelated = { _, _, _ -> },
                    onUnlinkRelated = { _, _ -> },
                    onReviewPendingTheme = { _, _ -> },
                    onReviewPendingRelated = { _, _ -> },
                    onSearchManual = {},
                    onLoadMoreManual = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Browse or search records...").performClick()
        composeTestRule.onNodeWithText("Landscape archive record")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun candidate() = RelatedRecord(
        rawRecordId = 2L,
        relationship = "",
        sourceText = "Landscape archive record",
        recordedAt = 1L
    )
}
