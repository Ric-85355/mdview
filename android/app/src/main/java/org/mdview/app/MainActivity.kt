/*
 * MainActivity.kt — created 2026-08-26, version 0.1.0.
 * Purpose: host Repository/Reader/Settings, file pickers, Open-with, and Share.
 * Algorithm: route retained state through ViewModels, forward ACTION_VIEW to Reader,
 * and turn one ACTION_SEND URI into a pending repository upload without auto-uploading.
 */

package org.mdview.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mdview.app.ui.MdviewApp
import org.mdview.app.ui.RepositoryScreen
import org.mdview.app.ui.SettingsScreen
import org.mdview.app.ui.ViewerViewModel
import org.mdview.app.repository.AppScreen
import org.mdview.app.repository.RepositoryViewModel

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)
    private var incomingShare by mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            val viewer: ViewerViewModel = viewModel()
            val repository: RepositoryViewModel = viewModel()
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let {
                    persistReadPermission(it)
                    viewer.openDocument(it)
                    repository.showReader()
                }
            }
            val uploadPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri -> repository.onUploadUri(uri) }
            LaunchedEffect(incomingUri) {
                incomingUri?.let {
                    viewer.openDocument(it)
                    repository.showReader()
                    incomingUri = null
                }
            }
            LaunchedEffect(incomingShare) {
                incomingShare?.let {
                    repository.onSharedUri(it.action, it.uri)
                    incomingShare = null
                }
            }
            BackHandler(enabled = repository.screen != AppScreen.Repository) {
                repository.showRepository()
            }
            when (repository.screen) {
                AppScreen.Repository -> RepositoryScreen(
                    viewModel = repository,
                    onOpenSettings = repository::showSettings,
                    onChooseUpload = {
                        uploadPicker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream"))
                    },
                    onOpenDocument = { document ->
                        repository.openDocument(document, viewer::openSource)
                    },
                )
                AppScreen.Reader -> MdviewApp(
                    viewModel = viewer,
                    onOpenDocument = { picker.launch(arrayOf("text/markdown", "text/plain")) },
                    onBack = repository::showRepository,
                )
                AppScreen.Settings -> SettingsScreen(
                    viewModel = repository,
                    onCancel = repository::showRepository,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> incomingUri = intent.data
            Intent.ACTION_SEND -> incomingShare = IncomingShare(Intent.ACTION_SEND, sharedUri(intent))
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUri(intent: Intent): Uri? =
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

private data class IncomingShare(val action: String, val uri: Uri?)
