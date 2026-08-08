package com.echomind.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomind.domain.model.Entry
import com.echomind.domain.model.EvidenceState
import com.echomind.domain.model.HomeCard
import com.echomind.domain.model.HomeCardType
import com.echomind.domain.model.ThemeCoverage
import com.echomind.ui.theme.HomeSkeleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToRecord: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToQa: () -> Unit = {},
    onNavigateToThemes: () -> Unit = {},
    onNavigateToTheme: (Long) -> Unit = {},
    onNavigateToDecisions: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onNavigateToRecord = onNavigateToRecord,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToQa = onNavigateToQa,
        onNavigateToThemes = onNavigateToThemes,
        onNavigateToTheme = onNavigateToTheme,
        onNavigateToDecisions = onNavigateToDecisions,
        onDismissCard = viewModel::dismissCard,
        onPostponeCard = {
            viewModel.postponeCard(System.currentTimeMillis() + 24L * 60 * 60 * 1000)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToRecord: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToQa: () -> Unit = {},
    onNavigateToThemes: () -> Unit = {},
    onNavigateToTheme: (Long) -> Unit = {},
    onNavigateToDecisions: () -> Unit = {},
    onDismissCard: () -> Unit = {},
    onPostponeCard: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EchoMind") },
                actions = {
                    IconButton(onClick = onNavigateToQa) {
                        Icon(Icons.Default.QuestionAnswer, contentDescription = "Ask AI")
                    }
                    IconButton(onClick = onNavigateToDecisions) {
                        Icon(Icons.Default.Checklist, contentDescription = "Decisions")
                    }
                    IconButton(onClick = onNavigateToThemes) {
                        Icon(Icons.Default.Label, contentDescription = "Themes")
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToRecord) {
                Icon(Icons.Default.Add, contentDescription = "New reflection")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            HomeSkeleton()
        } else if (!uiState.hasKnowledge && uiState.recent.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Start with one thought",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap + to write your first reflection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.legacySuppressionReset) {
                    item {
                        Text(
                            "Dismissed-card preferences were reset after the relevance update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item { PromptHeader(onNavigateToRecord = onNavigateToRecord) }

                uiState.card?.let { card ->
                    item {
                        RelevantCard(
                            card = card,
                            onContinue = { onNavigateToRecord() },
                            onInspect = {
                                if (card.scopeType == com.echomind.domain.model.CoverageScopeType.THEME) {
                                    onNavigateToTheme(card.scopeId)
                                } else {
                                    card.sourceRawRecordIds.firstOrNull()?.let(onNavigateToDetail)
                                }
                            },
                            onDismiss = onDismissCard,
                            onPostpone = onPostponeCard
                        )
                    }
                }

                if (uiState.coverage.isNotEmpty()) {
                    item {
                        EvidenceCoverageSection(
                            coverage = uiState.coverage,
                            onOpenTheme = onNavigateToTheme
                        )
                    }
                }

                if (uiState.recent.isNotEmpty()) {
                    item {
                        Text(
                            "Recent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(uiState.recent.take(5), key = { it.id }) { entry ->
                        EntryCard(entry = entry, onClick = { onNavigateToDetail(entry.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptHeader(onNavigateToRecord: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "What are you thinking about?",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Capture a thought, or revisit one something reminds you of.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onNavigateToRecord) {
                Text("Write a reflection")
            }
        }
    }
}

@Composable
private fun RelevantCard(
    card: HomeCard,
    onContinue: () -> Unit,
    onInspect: () -> Unit,
    onDismiss: () -> Unit,
    onPostpone: () -> Unit
) {
    val accent = when (card.type) {
        HomeCardType.CONTRADICTION -> MaterialTheme.colorScheme.error
        HomeCardType.UNFINISHED -> MaterialTheme.colorScheme.secondary
        HomeCardType.THIN_EVIDENCE -> MaterialTheme.colorScheme.tertiary
        HomeCardType.SUPPORTED_THEME -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "For you",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "· ${card.capability.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(card.detail, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                card.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (card.currentRevisionIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Revision IDs: ${card.currentRevisionIds.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onInspect) { Text("Inspect") }
                OutlinedButton(onClick = onContinue) { Text("Continue") }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                OutlinedButton(onClick = onPostpone) { Text("Later") }
            }
        }
    }
}

@Composable
private fun EvidenceCoverageSection(
    coverage: List<ThemeCoverage>,
    onOpenTheme: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Evidence by theme",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        coverage.forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTheme(theme.themeId) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(theme.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (theme.evidenceState == EvidenceState.EMPTY_THEME) {
                    Text(
                        "No confirmed conclusions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (theme.evidenceState == EvidenceState.NO_EXTERNAL_EVIDENCE) {
                    Text(
                        "No external evidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "${theme.evidenceCount} record(s) · ${theme.conclusionCount} conclusion(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry, onClick: () -> Unit = {}) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = dateFormat.format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.transcript,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.emotions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.emotions.take(3).forEach { emotion ->
                        Text(
                            text = emotion,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
