package com.echomind.ui.decisions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
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
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionSourceOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DecisionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decisions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New decision")
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { Text("Loading...") }
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
            uiState.decisions.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No decisions recorded", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Turn a question into an explicit decision, then report what happened.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.decisions, key = { it.id }) { decision ->
                        DecisionCard(
                            decision = decision,
                            sources = uiState.sources,
                            onChoose = { choice -> viewModel.choose(decision.id, choice) },
                            onReplaceChoice = { choice -> viewModel.replaceChoice(decision.id, choice) },
                            onReplaceGrounds = { revisionId -> viewModel.replaceGrounds(decision.id, revisionId) },
                            onReportOutcome = { report -> viewModel.reportOutcome(decision.id, report) },
                            onDeleteOutcome = { outcomeId -> viewModel.deleteOutcome(decision.id, outcomeId) },
                            onDelete = { viewModel.delete(decision.id) }
                        )
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        NewDecisionDialog(
            sources = uiState.sources,
            onDismiss = { showNewDialog = false },
            onConfirm = { question, sourceRevisionId ->
                viewModel.add(question, null, sourceRevisionId)
                showNewDialog = false
            }
        )
    }
}

@Composable
private fun DecisionCard(
    decision: Decision,
    sources: List<DecisionSourceOption>,
    onChoose: (String) -> Unit,
    onReplaceChoice: (String) -> Unit,
    onReplaceGrounds: (Long) -> Unit,
    onReportOutcome: (String) -> Unit,
    onDeleteOutcome: (Long) -> Unit,
    onDelete: () -> Unit
) {
    var showChoiceDialog by remember { mutableStateOf(false) }
    var showGroundsDialog by remember { mutableStateOf(false) }
    var showOutcomeDialog by remember { mutableStateOf(false) }
    var choiceToReplace by remember { mutableStateOf<String?>(null) }
    var groundsToReplace by remember { mutableStateOf<Long?>(null) }
    var outcomeToDelete by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    decision.question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    dateFormat.format(Date(decision.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "State: ${decision.state.name}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            decision.sourceConclusionText?.let { grounds ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Grounds: $grounds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!decision.isDecided) {
                TextButton(onClick = { showGroundsDialog = true }) { Text("Change grounds") }
            }

            if (
                decision.suggestion != null &&
                decision.suggestionAuthor == "echomind" &&
                !decision.suggestionSource.isNullOrBlank()
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "EchoMind proposal (source ${decision.suggestionSource}): ${decision.suggestion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (decision.isDecided) {
                Text(
                    "Your choice: ${decision.choice}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "No choice recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (decision.outcomes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                decision.outcomes.forEach { outcome ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Outcome: ${outcome.report}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { outcomeToDelete = outcome.id }) { Text("Remove") }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No outcome reported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!decision.isDecided) {
                    OutlinedTextButtonSmall("Choose...") { showChoiceDialog = true }
                } else if (!decision.hasOutcome) {
                    OutlinedTextButtonSmall("Change choice") { showChoiceDialog = true }
                }
                if (decision.isDecided && decision.hasOutcome) {
                    OutlinedTextButtonSmall("Add outcome") { showOutcomeDialog = true }
                } else if (decision.isDecided) {
                    TextButton(onClick = { showOutcomeDialog = true }) {
                        Text("Report outcome")
                    }
                } else {
                    Text(
                        "Choose before reporting an outcome",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete")
                }
            }
        }
    }

    if (showChoiceDialog) {
        SimpleTextDialog(
            title = if (decision.isDecided) "Change your choice" else "Record your choice",
            placeholder = "What did you decide?",
            onDismiss = { showChoiceDialog = false },
            onConfirm = {
                if (decision.isDecided) choiceToReplace = it else onChoose(it)
                showChoiceDialog = false
            }
        )
    }
    if (showGroundsDialog) {
        AlertDialog(
            onDismissRequest = { showGroundsDialog = false },
            title = { Text("Replace decision grounds") },
            text = {
                Column {
                    Text("Choose a current conclusion revision:")
                    sources.forEach { source ->
                        TextButton(onClick = {
                            showGroundsDialog = false
                            groundsToReplace = source.revisionId
                        }) {
                            Text("v${source.version}: ${source.text}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroundsDialog = false }) { Text("Cancel") }
            }
        )
    }
    choiceToReplace?.let { replacement ->
        AlertDialog(
            onDismissRequest = { choiceToReplace = null },
            title = { Text("Replace choice?") },
            text = { Text("Replace the recorded choice with \"$replacement\"?") },
            confirmButton = {
                TextButton(onClick = {
                    choiceToReplace = null
                    onReplaceChoice(replacement)
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { choiceToReplace = null }) { Text("Cancel") }
            }
        )
    }
    groundsToReplace?.let { revisionId ->
        val source = sources.firstOrNull { it.revisionId == revisionId }
        AlertDialog(
            onDismissRequest = { groundsToReplace = null },
            title = { Text("Replace decision grounds?") },
            text = {
                Text(
                    source?.let { "Use current revision v${it.version} as the new grounds?" }
                        ?: "Use this current revision as the new grounds?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    groundsToReplace = null
                    onReplaceGrounds(revisionId)
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { groundsToReplace = null }) { Text("Cancel") }
            }
        )
    }
    if (showOutcomeDialog) {
        SimpleTextDialog(
            title = "Report what happened",
            placeholder = "How did it turn out?",
            onDismiss = { showOutcomeDialog = false },
            onConfirm = {
                onReportOutcome(it)
                showOutcomeDialog = false
            }
        )
    }
    outcomeToDelete?.let { outcomeId ->
        AlertDialog(
            onDismissRequest = { outcomeToDelete = null },
            title = { Text("Remove outcome?") },
            text = { Text("This removes only the selected outcome report.") },
            confirmButton = {
                TextButton(onClick = {
                    outcomeToDelete = null
                    onDeleteOutcome(outcomeId)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { outcomeToDelete = null }) { Text("Cancel") }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete decision?") },
            text = {
                Text(
                    "This removes the decision and its reported outcomes. It never " +
                        "deletes the records or conclusions it references.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun OutlinedTextButtonSmall(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SimpleTextDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NewDecisionDialog(
    sources: List<DecisionSourceOption>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<DecisionSourceOption?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New decision") },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("The question you are deciding") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text("Ground this decision in a current conclusion")
                    TextButton(onClick = { sourceMenuExpanded = true }) {
                        Text(selectedSource?.let { "Revision ${it.revisionId}: ${it.text}" } ?: "Choose grounds")
                    }
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false }
                    ) {
                        sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text("v${source.version}: ${source.text}") },
                                onClick = {
                                    selectedSource = source
                                    sourceMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && selectedSource != null,
                onClick = {
                    onConfirm(question.trim(), requireNotNull(selectedSource).revisionId)
                }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
