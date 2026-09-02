/*
 * RepositoryScreen.kt — created 2026-09-01, version 0.1.0.
 * Purpose: render the single-repository browser and operation controls.
 * Algorithm: show one file-manager-style list for the current folder, with a
 * parent entry first, then child folders and documents, delegating actions.
 */

package org.mdview.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        var menuExpanded by remember { mutableStateOf(false) }
        var nameAction by remember { mutableStateOf<RepositoryNameAction?>(null) }
        var deleteTarget by remember { mutableStateOf<RepositoryBrowserEntry?>(null) }
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
                        Box {
                            TextButton(onClick = { menuExpanded = true }) { Text("Menu") }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    enabled = viewModel.operation == RepositoryOperation.Idle,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.refresh()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Upload") },
                                    enabled = viewModel.canUpload,
                                    onClick = {
                                        menuExpanded = false
                                        onChooseUpload()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("New folder") },
                                    enabled = viewModel.canCreateFolder,
                                    onClick = {
                                        menuExpanded = false
                                        nameAction = RepositoryNameAction.NewFolder
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
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
                            onRename = { nameAction = RepositoryNameAction.Rename(it) },
                            onDelete = { deleteTarget = it },
                            mutationsEnabled = viewModel.canManage,
                        )
                    }
                    if (viewModel.operation in setOf(
                            RepositoryOperation.Uploading,
                            RepositoryOperation.CreatingFolder,
                            RepositoryOperation.Renaming,
                            RepositoryOperation.Deleting,
                            RepositoryOperation.Refreshing,
                        )
                    ) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp,
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator()
                                Spacer(Modifier.width(14.dp))
                                Text(viewModel.operation.progressText.orEmpty())
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
        nameAction?.let { action ->
            RepositoryNameDialog(
                title = if (action == RepositoryNameAction.NewFolder) "New folder" else "Rename",
                initialValue = action.initialValue,
                onDismiss = { nameAction = null },
                onConfirm = { name ->
                    when (action) {
                        RepositoryNameAction.NewFolder -> viewModel.createFolder(name)
                        is RepositoryNameAction.Rename -> when (val entry = action.entry) {
                            is RepositoryBrowserEntry.Directory -> viewModel.renameFolder(entry.value, name)
                            is RepositoryBrowserEntry.Document -> viewModel.renameDocument(entry.value, name)
                            RepositoryBrowserEntry.Parent -> Unit
                        }
                    }
                    nameAction = null
                },
            )
        }
        deleteTarget?.let { target ->
            val label = target.objectName
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(if (target is RepositoryBrowserEntry.Directory) "Delete folder?" else "Delete file?") },
                text = { Text("Delete \"$label\"?") },
                confirmButton = {
                    Button(onClick = {
                        when (target) {
                            is RepositoryBrowserEntry.Directory -> viewModel.deleteFolder(target.value)
                            is RepositoryBrowserEntry.Document -> viewModel.deleteDocument(target.value)
                            RepositoryBrowserEntry.Parent -> Unit
                        }
                        deleteTarget = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
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
    onRename: (RepositoryBrowserEntry) -> Unit,
    onDelete: (RepositoryBrowserEntry) -> Unit,
    mutationsEnabled: Boolean,
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
                    onRename = if (mutationsEnabled) {
                        { onRename(entry) }
                    } else null,
                    onDelete = if (mutationsEnabled) {
                        { onDelete(entry) }
                    } else null,
                ) {
                    onDirectory(entry.value.path)
                }
                is RepositoryBrowserEntry.Document -> RepositoryRow(
                    label = entry.value.name,
                    icon = Icons.Filled.Description,
                    iconDescription = "Markdown document",
                    onRename = if (mutationsEnabled) {
                        { onRename(entry) }
                    } else null,
                    onDelete = if (mutationsEnabled) {
                        { onDelete(entry) }
                    } else null,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RepositoryRow(
    label: String,
    icon: ImageVector,
    iconDescription: String,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var contextMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onRename != null && onDelete != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = { contextMenuExpanded = true },
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(14.dp),
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
        if (onRename != null && onDelete != null) {
            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        contextMenuExpanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        contextMenuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
    HorizontalDivider()
}

private sealed interface RepositoryNameAction {
    val initialValue: String

    data object NewFolder : RepositoryNameAction {
        override val initialValue: String = ""
    }

    data class Rename(val entry: RepositoryBrowserEntry) : RepositoryNameAction {
        override val initialValue: String = entry.objectName
    }
}

private val RepositoryBrowserEntry.objectName: String
    get() = when (this) {
        RepositoryBrowserEntry.Parent -> ".."
        is RepositoryBrowserEntry.Directory -> value.name
        is RepositoryBrowserEntry.Document -> value.path.substringAfterLast('/')
    }

@Composable
private fun RepositoryNameDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }) { Text(if (initialValue.isEmpty()) "Create" else "Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
