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
