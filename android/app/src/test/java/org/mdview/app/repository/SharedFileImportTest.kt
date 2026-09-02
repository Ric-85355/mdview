/*
 * SharedFileImportTest.kt — created 2026-09-02, version 0.1.0.
 * Purpose: verify single-file Share validation, lifecycle restoration, and completion policy.
 * Algorithm: exercise the pure metadata model without Android providers or a network server.
 */

package org.mdview.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedFileImportTest {
    private val pending = PendingSharedFile("content://provider/notes", "notes.md")

    @Test
    fun toolbarIndicatorFollowsPendingState() {
        assertFalse(SharedFileImport.hasPending(null))
        assertTrue(SharedFileImport.hasPending(pending))
    }

    @Test
    fun actionSendMarkdownCreatesPendingFile() {
        assertEquals(
            pending,
            SharedFileImport.create(
                action = "android.intent.action.SEND",
                contentUri = "content://provider/notes",
                displayName = "notes.md",
            ),
        )
    }

    @Test
    fun sharedNonMarkdownFileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedFileImport.create(
                action = "android.intent.action.SEND",
                contentUri = "content://provider/image",
                displayName = "image.jpg",
            )
        }
    }

    @Test
    fun shareWithoutUriIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SharedFileImport.create(
                action = "android.intent.action.SEND",
                contentUri = null,
                displayName = "notes.md",
            )
        }
    }

    @Test
    fun pendingFileUsesCurrentRepositoryDirectory() {
        assertEquals(
            "/repo/linux/network/notes.md",
            UploadPaths.destination("/repo", "linux/network", pending.displayName),
        )
    }

    @Test
    fun cancelClearsPendingFile() {
        val cancelled = SharedFileImport.cancel()
        assertNull(cancelled)
        assertFalse(SharedFileImport.hasPending(cancelled))
    }

    @Test
    fun completedUploadClearsPendingEvenWhenRefreshFails() {
        val completed = SharedFileImport.afterUpload(pending, completed = true)
        assertNull(completed)
        assertFalse(SharedFileImport.hasPending(completed))
        assertEquals(
            "Upload completed, repository refresh failed",
            UploadOutcomeText.afterRefresh(refreshSucceeded = false),
        )
    }

    @Test
    fun failedUploadKeepsPendingFile() {
        assertSame(pending, SharedFileImport.afterUpload(pending, completed = false))
    }

    @Test
    fun savedMetadataRestoresPendingWithoutStartingUpload() {
        assertEquals(
            pending,
            SharedFileImport.restore(pending.contentUri, pending.displayName),
        )
    }
}
