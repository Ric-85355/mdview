/*
 * SettingsScreen.kt — created 2026-09-01, version 0.1.0.
 * Purpose: edit one repository's HTTP and SFTP connection settings.
 * Algorithm: retain form state in composition, never preload passwords, and
 * submit password changes separately from ordinary settings fields.
 */

package org.mdview.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.mdview.app.repository.RepositoryViewModel
import org.mdview.app.settings.RepositorySettings

@Composable
fun SettingsScreen(viewModel: RepositoryViewModel, onCancel: () -> Unit) {
    MdviewTheme {
        val initial = viewModel.settings
        var name by remember(initial) { mutableStateOf(initial.name) }
        var url by remember(initial) { mutableStateOf(initial.repositoryUrl) }
        var httpUser by remember(initial) { mutableStateOf(initial.httpUser) }
        var httpPassword by remember { mutableStateOf("") }
        var sftpEnabled by remember(initial) { mutableStateOf(initial.sftpEnabled) }
        var sftpHost by remember(initial) { mutableStateOf(initial.sftpHost) }
        var sftpPort by remember(initial) { mutableStateOf(initial.sftpPort.toString()) }
        var sftpUser by remember(initial) { mutableStateOf(initial.sftpUser) }
        var sftpRoot by remember(initial) { mutableStateOf(initial.sftpRoot) }
        var sftpPassword by remember { mutableStateOf("") }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text("Repository settings", style = MaterialTheme.typography.headlineSmall)
            SettingsField("Name", name, { name = it })
            SettingsField("Repository URL", url, { url = it })
            Text("HTTP access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            SettingsField("HTTP user", httpUser, { httpUser = it })
            PasswordField("HTTP password", httpPassword, { httpPassword = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sftpEnabled, onCheckedChange = { sftpEnabled = it })
                Text("Enable SFTP upload")
            }
            SettingsField("SFTP host", sftpHost, { sftpHost = it }, enabled = sftpEnabled)
            OutlinedTextField(
                value = sftpPort,
                onValueChange = { sftpPort = it.filter(Char::isDigit) },
                label = { Text("SFTP port") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = sftpEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SettingsField("SFTP user", sftpUser, { sftpUser = it }, enabled = sftpEnabled)
            SettingsField("SFTP root", sftpRoot, { sftpRoot = it }, enabled = sftpEnabled)
            PasswordField("SFTP password", sftpPassword, { sftpPassword = it }, enabled = sftpEnabled)
            Text(
                "Blank password fields keep the stored values. Passwords are never displayed after saving.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            viewModel.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    viewModel.saveSettings(
                        RepositorySettings(
                            name = name.trim(),
                            repositoryUrl = url.trim(),
                            httpUser = httpUser.trim(),
                            sftpEnabled = sftpEnabled,
                            sftpHost = sftpHost.trim(),
                            sftpPort = sftpPort.toIntOrNull() ?: 0,
                            sftpUser = sftpUser.trim(),
                            sftpRoot = sftpRoot.trim(),
                        ),
                        httpPassword.takeIf { it.isNotEmpty() },
                        sftpPassword.takeIf { it.isNotEmpty() },
                    )
                }) { Text("Save") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            TextButton(onClick = viewModel::clearPasswords) { Text("Clear stored passwords") }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("Leave blank to keep stored password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}
