package com.echomind.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showExportWarning by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::restoreData) }

    LaunchedEffect(uiState.exportState) {
        when (val state = uiState.exportState) {
            is ExportState.Success -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share export"))
                snackbarHostState.showSnackbar("Export ready - this file is NOT encrypted, handle with care")
                viewModel.clearExportState()
            }
            is ExportState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearExportState()
            }
            else -> {}
        }
    }

    LaunchedEffect(uiState.restoreState) {
        when (val state = uiState.restoreState) {
            RestoreState.Success -> {
                snackbarHostState.showSnackbar("Restore completed. Restart the app to refresh all screens.")
                viewModel.clearRestoreState()
            }
            is RestoreState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearRestoreState()
            }
            else -> Unit
        }
    }

    if (uiState.showEndpointWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEndpointWarning() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Non-local endpoint") },
            text = {
                Text(
                    "Changing the endpoint does not transmit data. EchoMind blocks raw diary " +
                    "and audio requests. Future remote assistance must show the minimized " +
                    "outgoing context and ask for approval each time. Provider retention " +
                    "remains outside EchoMind's control."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissEndpointWarning() }) {
                    Text("I understand")
                }
            }
        )
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Unencrypted export") },
            text = {
                Text(
                    "The exported zip file contains your full diary entries and " +
                    "decrypted audio recordings in PLAINTEXT. This file is NOT " +
                    "password-protected. Only share via trusted channels and " +
                    "delete after use."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    viewModel.exportData()
                }) {
                    Text("Export anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            Text("API Configuration", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.apiEndpoint,
                onValueChange = { viewModel.updateApiEndpoint(it) },
                label = { Text("API Endpoint") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Local mode", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.localMode,
                    onCheckedChange = { viewModel.toggleLocalMode(it) }
                )
            }
            Text(
                text = if (uiState.localMode) {
                    "AI network calls are blocked; entry analysis uses the on-device fallback."
                } else {
                    "Remote endpoint configured, but raw content remains blocked. No request " +
                        "is sent without a minimized preview and separate approval."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text("Data", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showExportWarning = true },
                enabled = uiState.exportState !is ExportState.InProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.exportState is ExportState.InProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Export all entries")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = uiState.restoreState !is RestoreState.InProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore backup into empty profile")
            }
            if (uiState.pendingAudioCleanupCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Some attached audio files still need cleanup " +
                        "(${uiState.pendingAudioCleanupCount}). EchoMind will retry in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (uiState.dismissedCards.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Dismissed Home cards", style = MaterialTheme.typography.titleMedium)
                uiState.dismissedCards.forEach { card ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${card.cardType} · ${card.scopeType} ${card.scopeId}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { viewModel.restoreCard(card.cardKey) }) {
                            Text("Restore")
                        }
                    }
                }
            }
        }
    }
}
