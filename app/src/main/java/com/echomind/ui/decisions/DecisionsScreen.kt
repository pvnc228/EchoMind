package com.echomind.ui.decisions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomind.data.followup.FollowUpRecord
import com.echomind.data.followup.FollowUpStatus
import com.echomind.domain.model.Decision
import com.echomind.domain.model.DecisionSourceOption
import com.echomind.domain.model.OutcomeImpactReview
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
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

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
        DecisionsScreenContent(
            uiState = uiState,
            modifier = Modifier.fillMaxSize().padding(padding),
            onReviewImpact = viewModel::reviewImpact,
            onDismissImpact = viewModel::dismissImpactReview,
            onApplyImpact = { decisionId, wording -> viewModel.applyImpact(decisionId, wording) },
            onChoose = { decisionId, choice -> viewModel.choose(decisionId, choice) },
            onReplaceChoice = { decisionId, choice -> viewModel.replaceChoice(decisionId, choice) },
            onReplaceGrounds = { decisionId, revisionId ->
                viewModel.replaceGrounds(decisionId, revisionId)
            },
            onReportOutcome = { decisionId, report -> viewModel.reportOutcome(decisionId, report) },
            onDeleteOutcome = { decisionId, outcomeId ->
                viewModel.deleteOutcome(decisionId, outcomeId)
            },
            onScheduleFollowUp = { decisionId, days ->
                viewModel.scheduleFollowUp(decisionId, days)
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onPostponeFollowUp = viewModel::postponeFollowUp,
            onCancelFollowUp = viewModel::cancelFollowUp,
            onDelete = viewModel::delete
        )
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
fun DecisionsScreenContent(
    uiState: DecisionsUiState,
    modifier: Modifier = Modifier,
    onReviewImpact: (Long) -> Unit = {},
    onDismissImpact: () -> Unit = {},
    onApplyImpact: (Long, String) -> Unit = { _, _ -> },
    onChoose: (Long, String) -> Unit = { _, _ -> },
    onReplaceChoice: (Long, String) -> Unit = { _, _ -> },
    onReplaceGrounds: (Long, Long) -> Unit = { _, _ -> },
    onReportOutcome: (Long, String) -> Unit = { _, _ -> },
    onDeleteOutcome: (Long, Long) -> Unit = { _, _ -> },
    onScheduleFollowUp: (Long, Int) -> Unit = { _, _ -> },
    onPostponeFollowUp: (Long) -> Unit = {},
    onCancelFollowUp: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {}
) {
    when {
        uiState.isLoading -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { Text("Loading...") }
        }
        uiState.error != null -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        }
        uiState.decisions.isEmpty() -> {
            Column(
                modifier = modifier,
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
                modifier = modifier.imePadding(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.decisions, key = { it.id }) { decision ->
                    DecisionCard(
                        decision = decision,
                        sources = uiState.sources,
                        followUp = uiState.followUps[decision.id],
                        followUpLoading = uiState.followUpLoadingDecisionId == decision.id,
                        followUpError = uiState.followUpError.takeIf {
                            uiState.followUpErrorDecisionId == decision.id
                        },
                        impactReview = uiState.impactReview?.takeIf {
                            it.decisionId == decision.id
                        },
                        impactLoading = uiState.impactDecisionId == decision.id &&
                            uiState.impactLoading,
                        impactError = uiState.impactError.takeIf {
                            uiState.impactDecisionId == decision.id
                        },
                        onReviewImpact = { onReviewImpact(decision.id) },
                        onDismissImpact = onDismissImpact,
                        onApplyImpact = { wording -> onApplyImpact(decision.id, wording) },
                        onChoose = { choice -> onChoose(decision.id, choice) },
                        onReplaceChoice = { choice -> onReplaceChoice(decision.id, choice) },
                        onReplaceGrounds = { revisionId ->
                            onReplaceGrounds(decision.id, revisionId)
                        },
                        onReportOutcome = { report -> onReportOutcome(decision.id, report) },
                        onDeleteOutcome = { outcomeId ->
                            onDeleteOutcome(decision.id, outcomeId)
                        },
                        onScheduleFollowUp = { days -> onScheduleFollowUp(decision.id, days) },
                        onPostponeFollowUp = { onPostponeFollowUp(decision.id) },
                        onCancelFollowUp = { onCancelFollowUp(decision.id) },
                        onDelete = { onDelete(decision.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecisionCard(
    decision: Decision,
    sources: List<DecisionSourceOption>,
    followUp: FollowUpRecord?,
    followUpLoading: Boolean,
    followUpError: String?,
    impactReview: OutcomeImpactReview?,
    impactLoading: Boolean,
    impactError: String?,
    onReviewImpact: () -> Unit,
    onDismissImpact: () -> Unit,
    onApplyImpact: (String) -> Unit,
    onChoose: (String) -> Unit,
    onReplaceChoice: (String) -> Unit,
    onReplaceGrounds: (Long) -> Unit,
    onReportOutcome: (String) -> Unit,
    onDeleteOutcome: (Long) -> Unit,
    onScheduleFollowUp: (Int) -> Unit,
    onPostponeFollowUp: () -> Unit,
    onCancelFollowUp: () -> Unit,
    onDelete: () -> Unit
) {
    var showChoiceDialog by remember { mutableStateOf(false) }
    var showGroundsDialog by remember { mutableStateOf(false) }
    var showOutcomeDialog by remember { mutableStateOf(false) }
    var choiceToReplace by remember { mutableStateOf<String?>(null) }
    var groundsToReplace by remember { mutableStateOf<Long?>(null) }
    var outcomeToDelete by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
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

            FollowUpSection(
                decision = decision,
                followUp = followUp,
                isLoading = followUpLoading,
                error = followUpError,
                onOpenSchedule = { showFollowUpDialog = true },
                onPostpone = onPostponeFollowUp,
                onCancel = onCancelFollowUp,
                onRetry = { showFollowUpDialog = true }
            )

            if (decision.isDecided && decision.hasOutcome) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextButtonSmall("Review impact", onClick = onReviewImpact)
            }
            if (impactLoading && impactReview == null) {
                Text(
                    "Preparing a local review proposal...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (impactError != null && impactReview == null) {
                Text(
                    impactError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            impactReview?.let { review ->
                OutcomeImpactReviewCard(
                    review = review,
                    isSaving = impactLoading,
                    onDismiss = onDismissImpact,
                    onConfirm = onApplyImpact
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
    if (showFollowUpDialog) {
        FollowUpScheduleDialog(
            isLoading = followUpLoading,
            onDismiss = { if (!followUpLoading) showFollowUpDialog = false },
            onSchedule = { days ->
                showFollowUpDialog = false
                onScheduleFollowUp(days)
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
@OptIn(ExperimentalLayoutApi::class)
private fun FollowUpSection(
    decision: Decision,
    followUp: FollowUpRecord?,
    isLoading: Boolean,
    error: String?,
    onOpenSchedule: () -> Unit,
    onPostpone: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    if (!decision.isDecided) return

    Spacer(modifier = Modifier.height(8.dp))
    when {
        isLoading -> {
            Text(
                "Scheduling follow-up...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        followUp?.status == FollowUpStatus.FIRED -> {
            Text(
                "Follow-up is ready",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "The reminder is available here even when notifications are unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onPostpone) { Text("Postpone 1 day") }
                TextButton(onClick = onCancel) { Text("Dismiss follow-up") }
            }
        }
        followUp?.status == FollowUpStatus.SCHEDULED ||
            followUp?.status == FollowUpStatus.POSTPONED -> {
            Text(
                "Follow-up scheduled for ${formatFollowUpDate(followUp.triggerAtMillis)}",
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onPostpone) { Text("Postpone 1 day") }
                TextButton(onClick = onCancel) { Text("Cancel follow-up") }
            }
        }
        followUp?.status == FollowUpStatus.FAILED -> {
            Text(
                error ?: "Follow-up could not be scheduled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) { Text("Try again") }
        }
        followUp?.status == FollowUpStatus.CANCELED -> Unit
        else -> {
            Text("Optional follow-up", style = MaterialTheme.typography.labelLarge)
            Text(
                "Choose a local reminder for one to three days. It does not change your conclusion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenSchedule) { Text("Set optional follow-up") }
        }
    }
    if (error != null && followUp?.status != FollowUpStatus.FAILED) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FollowUpScheduleDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSchedule: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set optional follow-up") },
        text = {
            Text("Choose a local reminder. It will not confirm a proposal or revise a conclusion.")
        },
        confirmButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 2, 3).forEach { days ->
                    TextButton(enabled = !isLoading, onClick = { onSchedule(days) }) {
                        Text("$days day${if (days == 1) "" else "s"}")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatFollowUpDate(triggerAtMillis: Long): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(triggerAtMillis))

@Composable
fun OutcomeImpactReviewCard(
    review: OutcomeImpactReview,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var proposedText by remember(review.proposedText) {
        mutableStateOf(review.proposedText)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Review impact", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Original grounds", style = MaterialTheme.typography.labelLarge)
            Text(review.originalText, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Your choice", style = MaterialTheme.typography.labelLarge)
            Text(review.choice, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Reported outcome", style = MaterialTheme.typography.labelLarge)
            review.outcomes.forEach { outcome ->
                Text(outcome, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Proposed revision (diff)", style = MaterialTheme.typography.labelLarge)
            Text(
                "The original grounds stay unchanged until you confirm a new revision.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            OutlinedTextField(
                value = proposedText,
                onValueChange = { proposedText = it },
                label = { Text("Your revised conclusion") },
                supportingText = { Text("Edit EchoMind's proposal in your own words.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !isSaving && proposedText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onConfirm(proposedText.trim()) }
                ) {
                    Text(if (isSaving) "Saving revision..." else "Confirm new revision")
                }
                TextButton(
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text("Keep current conclusion")
                }
            }
        }
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
