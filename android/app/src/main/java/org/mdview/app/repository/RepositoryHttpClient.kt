/*
 * RepositoryHttpClient.kt — created 2026-09-01, version 0.1.0.
 * Purpose: retrieve repository indexes and Markdown documents over HTTP(S).
 * Algorithm: normalize URLs, apply optional Basic authentication, enforce
 * timeouts/status handling, and strictly decode UTF-8 off the main thread.
 */

package org.mdview.app.repository

import android.util.Base64
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mdview.app.settings.RepositoryCredentials
import org.mdview.app.settings.RepositorySettings

class RepositoryHttpClient {
    suspend fun loadIndex(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
    ): MarkdownRepository = withContext(Dispatchers.IO) {
        val source = request(
            RepositoryPaths.normalizeBaseUrl(settings.repositoryUrl) + "repository.json",
            settings.httpUser,
            credentials.httpPassword,
        )
        RepositoryParser.parse(source)
    }

    suspend fun loadDocument(
        settings: RepositorySettings,
        credentials: RepositoryCredentials,
        document: RepositoryDocument,
    ): String = withContext(Dispatchers.IO) {
        request(
            RepositoryPaths.documentUrl(settings.repositoryUrl, document.path),
            settings.httpUser,
            credentials.httpPassword,
        )
    }

    private fun request(url: String, user: String, password: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json, text/markdown, text/plain")
            if (user.isNotEmpty() || password.isNotEmpty()) {
                val token = Base64.encodeToString(
                    "$user:$password".toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                connection.setRequestProperty("Authorization", "Basic $token")
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                error("HTTP authentication failed")
            }
            if (status !in 200..299) error("Server returned HTTP $status")
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            return connection.inputStream.use { InputStreamReader(it, decoder).readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
