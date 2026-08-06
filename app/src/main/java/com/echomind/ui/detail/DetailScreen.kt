package com.echomind.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomind.domain.model.ReflectionSession
import com.echomind.domain.model.ReflectionStatus
import com.echomind.domain.model.RelatedRecord
import com.echomind.domain.model.Relationship
import com.echomind.domain.model.Theme
import com.echomind.ui.theme.DetailSkeleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    entryId: Long,
    onNavigateBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        viewModel.loadEntry(entryId)
    }

    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onNavigateBack()
    }

    if (showDeleteDialog) {
        val hasConfirmedConclusion = uiState.reflection?.confirmedConclusion != null
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    if (hasConfirmedConclusion) {
                        "Delete conclusion and source?"
                    } else {
                        "Delete reflection?"
                    }
                )
            },
            text = {
                Text(
                    if (hasConfirmedConclusion) {
                        "This source supports a confirmed conclusion. Deleting both also " +
                            "removes revision history, the saved proposal, and any attached audio."
                    } else {
                        "This removes the raw record, saved proposal, archive entry, and any " +
                            "attached audio."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !uiState.isDeleting,
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteEntry(
                            includeConfirmedConclusion = hasConfirmedConclusion
                        )
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !uiState.isDeleting,
                        onClick = { showDeleteDialog = true }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                DetailSkeleton()
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.entry != null -> {
                val entry = uiState.entry!!
                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.category.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dateFormat.format(Date(entry.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = entry.transcript,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    uiState.reflection?.let { reflection ->
                        Spacer(modifier = Modifier.height(20.dp))
                        SavedReflectionProvenance(reflection)

                        if (reflection.confirmedConclusion != null && reflection.revisionId != null) {
                            Spacer(modifier = Modifier.height(20.dp))
                            ConnectionsSection(
                                themes = uiState.themes,
                                availableThemes = uiState.availableThemes,
                                relatedRecords = uiState.relatedRecords,
                                otherEntries = uiState.otherEntries,
                                revisionId = reflection.revisionId,
                                onLinkToTheme = { themeId, revisionId ->
                                    viewModel.linkToTheme(themeId, revisionId)
                                },
                                onUnlinkFromTheme = { themeId, revisionId ->
                                    viewModel.unlinkFromTheme(themeId, revisionId)
                                },
                                onLinkRelated = { candidate, relationship, revisionId ->
                                    viewModel.linkRelatedRecord(revisionId, candidate, relationship)
                                },
                                onUnlinkRelated = { revisionId, sourceId ->
                                    viewModel.unlinkRelatedRecord(revisionId, sourceId)
                                }
                            )
                        }
                    }

                    if (entry.audioPath != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.togglePlayback() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isPlaying) "Pause" else "Play Recording")
                        }
                    }

                    if (entry.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader("Summary")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (entry.tasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader("Tasks")
                        entry.tasks.forEach { task ->
                            Text("• $task", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (entry.ideas.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader("Ideas")
                        entry.ideas.forEach { idea ->
                            Text("• $idea", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (entry.emotions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader("Emotions")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            entry.emotions.forEach { emotion ->
                                Text(
                                    text = emotion,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    if (entry.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        SectionHeader("Tags")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            entry.tags.forEach { tag ->
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ConnectionsSection(
    themes: List<Theme>,
    availableThemes: List<Theme>,
    relatedRecords: List<RelatedRecord>,
    otherEntries: List<RelatedRecord>,
    revisionId: Long,
    onLinkToTheme: (Long, Long) -> Unit,
    onUnlinkFromTheme: (Long, Long) -> Unit,
    onLinkRelated: (RelatedRecord, String, Long) -> Unit,
    onUnlinkRelated: (Long, Long) -> Unit
) {
    var showThemePicker by remember { mutableStateOf(false) }
    var relateTarget by remember { mutableStateOf<RelatedRecord?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Connections")

        Text(
            "Themes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (themes.isEmpty()) {
            Text(
                "Not in any theme yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            themes.forEach { theme ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(theme.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onUnlinkFromTheme(theme.id, revisionId) }) {
                        Text("Remove")
                    }
                }
            }
        }
        TextButton(onClick = { showThemePicker = true }) {
            Text("Add to theme...")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Related records",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (relatedRecords.isEmpty()) {
            Text(
                "No linked supporting or contradicting records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            relatedRecords.forEach { record ->
                RecordRelationshipRow(
                    record = record,
                    onSelect = { onUnlinkRelated(revisionId, record.rawRecordId) },
                    removeLabel = "Remove"
                )
            }
        }
        if (otherEntries.isNotEmpty()) {
            TextButton(onClick = { relateTarget = otherEntries.first() }) {
                Text("Link a record...")
            }
        }
    }

    if (showThemePicker) {
        val linkedIds = themes.map { it.id }.toSet()
        ThemePickerDialog(
            themes = availableThemes.filter { it.id !in linkedIds },
            onDismiss = { showThemePicker = false },
            onSelected = { onLinkToTheme(it, revisionId) }
        )
    }

    relateTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { relateTarget = null },
            title = { Text("Link \"${target.sourceText.take(60)}\"?") },
            text = {
                Column {
                    Text(
                        "Does this record support or contradict the current conclusion?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        onLinkRelated(target, Relationship.SUPPORTS, revisionId)
                        relateTarget = null
                    }) { Text("Supports") }
                    TextButton(onClick = {
                        onLinkRelated(target, Relationship.CONTRADICTS, revisionId)
                        relateTarget = null
                    }) { Text("Contradicts") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { relateTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecordRelationshipRow(
    record: RelatedRecord,
    onSelect: () -> Unit,
    removeLabel: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = record.relationship,
                style = MaterialTheme.typography.labelMedium,
                color = if (record.relationship == Relationship.CONTRADICTS) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(record.sourceText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onSelect) { Text(removeLabel) }
        }
    }
}

@Composable
private fun ThemePickerDialog(
    themes: List<Theme>,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to theme") },
        text = {
            if (themes.isEmpty()) {
                Text(
                    "No themes yet. Create one in the Themes screen, then link it here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column {
                    themes.forEach { theme ->
                        TextButton(onClick = { onSelected(theme.id) }) {
                            Text(theme.name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SavedReflectionProvenance(reflection: ReflectionSession) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Saved reflection provenance")
        ProvenanceCard("Your words · raw source", reflection.originalText)
        ProvenanceCard(
            "EchoMind · ${reflection.status} proposal",
            reflection.draft.tentativeThesis
        )
        if (reflection.counterargument.isNotBlank()) {
            ProvenanceCard("EchoMind · alternative", reflection.counterargument)
        }
        reflection.confirmedConclusion?.let { conclusion ->
            ProvenanceCard(
                "Your conclusion · revision ${reflection.revisionVersion ?: 1}",
                conclusion,
                emphasized = true
            )
            Text(
                text = "Source link · ${reflection.sourceRelationship ?: "supports"} · " +
                    (reflection.sourceLinkStatus ?: ReflectionStatus.CONFIRMED),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ProvenanceCard(
    label: String,
    text: String,
    emphasized: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}
