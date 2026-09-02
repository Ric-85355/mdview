/*
 * RepositoryScreen.kt — created 2026-09-01, version 0.1.0.
 * Purpose: render the single-repository browser and operation controls.
 * Algorithm: show one file-manager-style list for the current folder, with a
 * parent entry first, then child folders and documents, delegating actions.
 */

package org.mdview.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mdview.app.repository.RepositoryBrowserEntry
import org.mdview.app.repository.RepositoryDocument
import org.mdview.app.repository.RepositoryOperation
import org.mdview.app.repository.RepositoryViewModel
import org.mdview.app.repository.browserEntries

@Composable
fun RepositoryScreen(
    viewModel: RepositoryViewModel,
    onOpenSettings: () -> Unit,
    onChooseUpload: () -> Unit,
    onOpenDocument: (RepositoryDocument) -> Unit,
) {
    MdviewTheme {
        val directory = viewModel.currentDirectory
        Scaffold(
            topBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            viewModel.settings.name.ifBlank { "Repository" },
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(
                            onClick = viewModel::refresh,
                            enabled = viewModel.operation == RepositoryOperation.Idle,
                        ) { Text("Refresh") }
                        TextButton(
                            onClick = onChooseUpload,
                            enabled = viewModel.canUpload,
                        ) { Text("Upload") }
                        TextButton(onClick = onOpenSettings) { Text("Settings") }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                viewModel.message?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        color = if ("failed" in message.lowercase()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        viewModel.repository == null && viewModel.operation != RepositoryOperation.Idle ->
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        directory == null -> Text(
                            "Configure a repository, then refresh it.",
                            Modifier.align(Alignment.Center).padding(24.dp),
                        )
                        else -> RepositoryBrowser(
                            entries = directory.browserEntries(),
                            onParent = viewModel::goToParentDirectory,
                            onDirectory = viewModel::openDirectory,
                            onDocument = onOpenDocument,
                        )
                    }
                    if (viewModel.operation == RepositoryOperation.Uploading ||
                        viewModel.operation == RepositoryOperation.Refreshing
                    ) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator()
                                Spacer(Modifier.width(14.dp))
                                Text(if (viewModel.operation == RepositoryOperation.Uploading) "Uploading…" else "Refreshing…")
                            }
                        }
                    }
                }
                AdBannerPlaceholder()
            }
        }
        viewModel.overwriteSelection?.let { selection ->
            AlertDialog(
                onDismissRequest = viewModel::cancelOverwrite,
                title = { Text("File already exists") },
                text = { Text("Replace ${selection.fileName}?") },
                confirmButton = {
                    Button(onClick = viewModel::confirmOverwrite) { Text("Replace") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelOverwrite) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun RepositoryBrowser(
    entries: List<RepositoryBrowserEntry>,
    onParent: () -> Unit,
    onDirectory: (String) -> Unit,
    onDocument: (RepositoryDocument) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(
            items = entries,
            key = { entry ->
                when (entry) {
                    RepositoryBrowserEntry.Parent -> "parent"
                    is RepositoryBrowserEntry.Directory -> "directory:${entry.value.path}"
                    is RepositoryBrowserEntry.Document -> "document:${entry.value.path}"
                }
            },
        ) { entry ->
            when (entry) {
                RepositoryBrowserEntry.Parent -> RepositoryRow(
                    label = "..",
                    icon = Icons.Filled.ArrowUpward,
                    iconDescription = "Parent folder",
                    onClick = onParent,
                )
                is RepositoryBrowserEntry.Directory -> RepositoryRow(
                    label = entry.value.name,
                    icon = Icons.Filled.Folder,
                    iconDescription = "Folder",
                ) {
                    onDirectory(entry.value.path)
                }
                is RepositoryBrowserEntry.Document -> RepositoryRow(
                    label = entry.value.name,
                    icon = Icons.Filled.Description,
                    iconDescription = "Markdown document",
                ) {
                    onDocument(entry.value)
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "No files or folders",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else if (entries.singleOrNull() == RepositoryBrowserEntry.Parent) {
            item {
                Text("No files", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    label: String,
    icon: ImageVector,
    iconDescription: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            modifier = Modifier.width(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
    HorizontalDivider()
}
