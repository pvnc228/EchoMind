package com.echomind.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomind.domain.model.ReflectionDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDraftExitDialog by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else viewModel.permissionDenied()
    }

    val requestRecording = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val requestExit = {
        if (uiState.stage == ReflectionStage.RECORDING) viewModel.stopRecording()
        if (uiState.stage == ReflectionStage.CAPTURE || uiState.stage == ReflectionStage.RECORDING) {
            showDraftExitDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler(
        enabled = uiState.stage == ReflectionStage.CAPTURE || uiState.stage == ReflectionStage.RECORDING,
        onBack = requestExit
    )

    if (showDraftExitDialog) {
        AlertDialog(
            onDismissRequest = { showDraftExitDialog = false },
            title = { Text("Keep this draft?") },
            text = {
                Text(
                    "Text and completed encrypted audio are saved locally. " +
                        "An unfinished recording is reported as interrupted after process death."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDraftExitDialog = false
                    viewModel.keepDraft(onNavigateBack)
                }) { Text("Keep draft") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDraftExitDialog = false }) { Text("Cancel") }
                     TextButton(onClick = {
                         showDraftExitDialog = false
                         viewModel.discardDraft(onNavigateBack)
                     }) { Text("Discard") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New reflection") },
                navigationIcon = {
                    IconButton(onClick = requestExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        RecordScreenContent(
            uiState = uiState,
            onThoughtChange = viewModel::updateThought,
            onSubmit = viewModel::submitThought,
            onStartRecording = requestRecording,
            onStopRecording = viewModel::stopRecording,
            onConfirmationChange = viewModel::updateConfirmation,
            onFollowUpQuestionChange = viewModel::updateFollowUpQuestion,
            onConfirm = viewModel::confirmProposal,
            onReject = viewModel::rejectProposal,
            onContinueDiscussion = viewModel::continueDiscussion,
            onRetry = viewModel::retry,
            onDone = onNavigateBack,
            onStartNew = viewModel::startNewReflection,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun RecordScreenContent(
    uiState: RecordUiState,
    onThoughtChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onConfirmationChange: (String) -> Unit,
    onFollowUpQuestionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onContinueDiscussion: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onStartNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        when (uiState.stage) {
            ReflectionStage.LOADING -> ProcessingContent("Restoring your reflection…")
            ReflectionStage.CAPTURE -> CaptureContent(
                text = uiState.thoughtText,
                hasAudio = uiState.audioPath != null,
                permissionDenied = uiState.permissionDenied,
                onTextChange = onThoughtChange,
                onSubmit = onSubmit,
                onStartRecording = onStartRecording
            )
            ReflectionStage.RECORDING -> RecordingContent(
                amplitudes = uiState.amplitudes,
                onStopRecording = onStopRecording
            )
            ReflectionStage.PROCESSING -> ProcessingContent(
                if (uiState.rawRecordId == null) {
                    "Saving your original words…"
                } else {
                    "Original saved. Structuring locally…"
                }
            )
            ReflectionStage.REVIEW -> ReviewContent(
                originalText = uiState.thoughtText,
                draft = requireNotNull(uiState.draft),
                counterargument = uiState.counterargument,
                confirmationText = uiState.confirmationText,
                followUpQuestion = uiState.followUpQuestion,
                followUpQuestionDraft = uiState.followUpQuestionDraft,
                onConfirmationChange = onConfirmationChange,
                onFollowUpQuestionChange = onFollowUpQuestionChange,
                onConfirm = onConfirm,
                onReject = onReject,
                onContinueDiscussion = onContinueDiscussion
            )
            ReflectionStage.CONFIRMED -> ConfirmedContent(
                originalText = uiState.thoughtText,
                draft = requireNotNull(uiState.draft),
                counterargument = uiState.counterargument,
                conclusion = uiState.confirmedConclusion.orEmpty(),
                onDone = onDone,
                onStartNew = onStartNew
            )
            ReflectionStage.REJECTED -> RejectedContent(
                originalText = uiState.thoughtText,
                draft = requireNotNull(uiState.draft),
                onDone = onDone,
                onStartNew = onStartNew
            )
            ReflectionStage.ERROR -> ErrorContent(
                message = uiState.error ?: "Unknown error",
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun CaptureContent(
    text: String,
    hasAudio: Boolean,
    permissionDenied: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStartRecording: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "What are you trying to understand?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Describe the situation and your current interpretation. Your original words stay separate from EchoMind's proposal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Your reflection") },
            placeholder = { Text("I noticed… I think… What I may be assuming is…") },
            minLines = 7,
            modifier = Modifier.fillMaxWidth()
        )
        if (hasAudio) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Encrypted voice note attached. Add or edit its transcript above.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (permissionDenied) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Microphone permission was declined. You can still write this reflection with text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create local reflection")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onStartRecording,
            enabled = !hasAudio,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (hasAudio) "Voice note attached" else "Record instead")
        }
    }
}

@Composable
private fun RecordingContent(
    amplitudes: List<Float>,
    onStopRecording: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WaveformVisualizer(
            amplitudes = amplitudes,
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text("Recording…", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStopRecording) {
            Text("Stop and add transcript")
        }
    }
}

@Composable
private fun ProcessingContent(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
private fun ReviewContent(
    originalText: String,
    draft: ReflectionDraft,
    counterargument: String,
    confirmationText: String,
    followUpQuestion: String?,
    followUpQuestionDraft: String,
    onConfirmationChange: (String) -> Unit,
    onFollowUpQuestionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onContinueDiscussion: () -> Unit
) {
    var showAnalysis by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LabeledCard("Your words · immutable source", originalText)
        Text(
            if (followUpQuestion == null) {
                "EchoMind's proposal"
            } else {
                "EchoMind's focused follow-up proposal"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "A local draft. It is not your belief until you confirm it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LabeledCard(
            "Proposed thesis",
            draft.tentativeThesis,
            container = MaterialTheme.colorScheme.secondaryContainer
        )
        LabeledCard("Local alternative", counterargument)
        if (followUpQuestion != null) {
            LabeledCard(
                "Your focused question",
                followUpQuestion,
                container = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            Text(
                "Continue once with a focused question. The new local response remains a proposal until you review it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = followUpQuestionDraft,
                onValueChange = onFollowUpQuestionChange,
                label = { Text("Focused follow-up question") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = onContinueDiscussion,
                enabled = followUpQuestionDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ask one focused follow-up")
            }
        }
        TextButton(
            onClick = { showAnalysis = !showAnalysis },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (showAnalysis) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
            Spacer(Modifier.width(6.dp))
            Text("Show full analysis")
        }
        AnimatedVisibility(
            visible = showAnalysis,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AnalysisCard(draft)
        }
        HorizontalDivider()
        Text(
            "My wording",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Edit freely. Only this field becomes your conclusion when you confirm it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = confirmationText,
            onValueChange = onConfirmationChange,
            label = { Text("My wording") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        ActionDock(
            onConfirm = onConfirm,
            onReject = onReject,
            confirmEnabled = confirmationText.isNotBlank()
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionDock(
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    confirmEnabled: Boolean
) {
    // ponytail: bounded glass action dock. Static functional layer only; API 26-30 falls
    // back to an opaque tonal surface (no blur on scrolling content). Upgrade to measured
    // blur/compositing only if a validated visual pass requires it.
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm my conclusion")
        }
        TextButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) {
            Text("Reject EchoMind's proposal")
        }
    }
}

@Composable
private fun AnalysisCard(draft: ReflectionDraft) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Full analysis · local draft",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            DraftField("Observations", draft.observations)
            DraftField("Interpretations", draft.interpretations)
            DraftField("Assumptions", draft.assumptions)
            DraftField("Open questions", draft.openQuestions)
        }
    }
}

@Composable
private fun ConfirmedContent(
    originalText: String,
    draft: ReflectionDraft,
    counterargument: String,
    conclusion: String,
    onDone: () -> Unit,
    onStartNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Conclusion confirmed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        LabeledCard("Your words · source", originalText)
        LabeledCard("Proposed thesis", draft.tentativeThesis)
        LabeledCard("Local alternative", counterargument)
        LabeledCard("Your conclusion · revision 1", conclusion, emphasized = true)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
        TextButton(onClick = onStartNew, modifier = Modifier.fillMaxWidth()) {
            Text("Start another reflection")
        }
    }
}

@Composable
private fun RejectedContent(
    originalText: String,
    draft: ReflectionDraft,
    onDone: () -> Unit,
    onStartNew: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Proposal rejected", style = MaterialTheme.typography.headlineSmall)
        Text(
            "No confirmed conclusion was created. Your original record stays in your archive, and you can review the proposal again.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LabeledCard("Your words · source", originalText)
        LabeledCard("Rejected local proposal", draft.tentativeThesis)
        Button(onClick = onStartNew, modifier = Modifier.fillMaxWidth()) {
            Text("Start another reflection")
        }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Back to archive")
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun DraftField(label: String, values: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) {
            Text(
                "None identified in the original text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            values.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun LabeledCard(
    label: String,
    text: String,
    emphasized: Boolean = false,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                container
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
