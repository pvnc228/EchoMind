package com.echomind.ui.themes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomind.domain.model.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemesScreen(
    onNavigateBack: () -> Unit,
    onOpenTheme: (Long) -> Unit,
    viewModel: ThemesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Theme?>(null) }
    var archiveTarget by remember { mutableStateOf<Theme?>(null) }

    if (showCreate) {
        ThemeNameDialog(
            title = "New theme",
            confirmLabel = "Create",
            initial = "",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                viewModel.createTheme(name)
                showCreate = false
            }
        )
    }

    renameTarget?.let { theme ->
        ThemeNameDialog(
            title = "Rename theme",
            confirmLabel = "Save",
            initial = theme.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameTheme(theme.id, name)
                renameTarget = null
            }
        )
    }

    archiveTarget?.let { theme ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Archive \"${theme.name}\"?") },
            text = {
                Text(
                    "Archiving hides the theme from the list but keeps its conclusions " +
                        "and links. You can restore or delete it in a later version."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveTheme(theme.id)
                    archiveTarget = null
                }) { Text("Archive") }
            },
            dismissButton = {
                TextButton(onClick = { archiveTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Themes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, contentDescription = "New theme")
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) { Text("Loading themes...") }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
            }
            uiState.themes.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No themes yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Group related conclusions under a theme you own.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showCreate = true }) { Text("Create your first theme") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.themes, key = { it.id }) { theme ->
                        ThemeCard(
                            theme = theme,
                            onOpen = { onOpenTheme(theme.id) },
                            onRename = { renameTarget = theme },
                            onArchive = { archiveTarget = theme }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: Theme,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(theme.name, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${theme.conclusionCount} conclusions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename ${theme.name}")
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Default.Archive, contentDescription = "Archive ${theme.name}")
            }
        }
    }
}

@Composable
private fun ThemeNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Theme name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name) }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
