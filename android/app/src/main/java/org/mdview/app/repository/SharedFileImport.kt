/*
 * SharedFileImport.kt — created 2026-09-02, version 0.1.0.
 * Purpose: model one pending Android Share file without retaining a filesystem path.
 * Algorithm: accept ACTION_SEND metadata, validate the Markdown basename through the
 * existing upload rules, and serialize only URI text and display name for saved state.
 */

package org.mdview.app.repository

import android.content.Intent
import android.net.Uri

/** URI grant metadata retained only while one shared file awaits an explicit upload. */
data class PendingSharedFile(val contentUri: String, val displayName: String) {
    /** Reuses the same upload value object as the system file picker. */
    fun uploadSelection(): UploadSelection = UploadSelection(Uri.parse(contentUri), displayName)
}

/** Pure validation and state transitions shared by Activity input, ViewModel, and tests. */
object SharedFileImport {
    /** Keeps toolbar visibility derived from the single pending-state source of truth. */
    fun hasPending(pending: PendingSharedFile?): Boolean = pending != null

    /** Validates one ACTION_SEND selection without opening or uploading its content. */
    fun create(action: String?, contentUri: String?, displayName: String): PendingSharedFile {
        require(action == Intent.ACTION_SEND) { "Only one shared file is supported" }
        require(!contentUri.isNullOrBlank()) { "The shared file is unavailable" }
        return PendingSharedFile(
            contentUri = contentUri,
            displayName = UploadPaths.safeFileName(displayName),
        )
    }

    /** Rebuilds pending metadata after Activity recreation without triggering side effects. */
    fun restore(contentUri: String?, displayName: String?): PendingSharedFile? =
        if (contentUri.isNullOrBlank() || displayName.isNullOrBlank()) null
        else PendingSharedFile(contentUri, UploadPaths.safeFileName(displayName))

    /** Clears a pending Share only after an explicit user cancellation. */
    fun cancel(): PendingSharedFile? = null

    /** Keeps failed uploads retryable and clears metadata once SFTP has completed. */
    fun afterUpload(pending: PendingSharedFile?, completed: Boolean): PendingSharedFile? =
        if (completed) null else pending
}
