/*
 * MainActivity.kt — created 2026-08-26, version 0.1.0.
 * Purpose: host Compose, the system file picker, and Android Open-with URIs.
 * Algorithm: forward launcher/new intents to a retained ViewerViewModel and
 * request persistable read access when Storage Access Framework permits it.
 */

package org.mdview.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mdview.app.ui.MdviewApp
import org.mdview.app.ui.ViewerViewModel

class MainActivity : ComponentActivity() {
    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data
        setContent {
            val viewer: ViewerViewModel = viewModel()
            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                uri?.let {
                    persistReadPermission(it)
                    viewer.openDocument(it)
                }
            }
            LaunchedEffect(incomingUri) {
                incomingUri?.let {
                    viewer.openDocument(it)
                    incomingUri = null
                }
            }
            MdviewApp(
                viewModel = viewer,
                onOpenDocument = { picker.launch(arrayOf("text/markdown", "text/plain")) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) incomingUri = intent.data
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}
