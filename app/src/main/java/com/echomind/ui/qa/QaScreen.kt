package com.echomind.ui.qa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomind.data.remote.RemoteQuestionPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    viewModel: QaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    QaScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToDetail = onNavigateToDetail,
        onInputChanged = viewModel::onInputChanged,
        onSend = viewModel::sendMessage,
        onApproveRemoteRequest = viewModel::approveRemoteRequest,
        onCancelRemoteRequest = viewModel::cancelRemoteRequest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaScreenContent(
    uiState: QaUiState,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit = {},
    onInputChanged: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onApproveRemoteRequest: () -> Unit = {},
    onCancelRemoteRequest: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask EchoMind") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.currentInput,
                    onValueChange = onInputChanged,
                    label = { Text("Question") },
                    placeholder = { Text("Ask about confirmed conclusions...") },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading && uiState.pendingPreview == null,
                    singleLine = false
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = uiState.currentInput.isNotBlank() &&
                        !uiState.isLoading && uiState.pendingPreview == null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Review remote request")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            uiState.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ask about a confirmed conclusion.\nThe exact remote request will be shown first.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = listState
                ) {
                    items(uiState.messages, key = { message -> message.hashCode() }) { message ->
                        MessageBubble(message = message, onNavigateToDetail = onNavigateToDetail)
                    }
                    if (uiState.isLoading) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomEnd = 16.dp
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Preparing request...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.pendingPreview?.let { preview ->
        RemoteRequestPreviewDialog(
            preview = preview,
            isSending = uiState.isLoading,
            onApprove = onApproveRemoteRequest,
            onCancel = onCancelRemoteRequest
        )
    }
}

@Composable
private fun RemoteRequestPreviewDialog(
    preview: RemoteQuestionPreview,
    isSending: Boolean,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSending) onCancel() },
        title = { Text("Review remote request") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Purpose", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(preview.purpose)
                Text("Destination", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(preview.destination)
                Text("Exact content to send", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                preview.messages.forEach { message ->
                    Text(
                        text = "${message.role}: ${message.content}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "This approval applies once to this exact request. The provider may retain submitted data under its own policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onApprove, enabled = !isSending) {
                Text("Allow once")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !isSending) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onNavigateToDetail: (Long) -> Unit
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val colors = if (message.isUser) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(colors = colors, shape = shape) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
