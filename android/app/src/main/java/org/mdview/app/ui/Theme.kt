/*
 * Theme.kt — created 2026-08-26, version 0.1.0.
 * Purpose: provide accessible system-controlled light and dark Compose themes.
 * Algorithm: choose Material 3 light/dark schemes from current night mode.
 */

package org.mdview.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MdviewTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
