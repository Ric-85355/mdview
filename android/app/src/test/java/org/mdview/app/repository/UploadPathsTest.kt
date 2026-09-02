/*
 * UploadPathsTest.kt — created 2026-09-01, version 0.1.0.
 * Purpose: verify Markdown selection and confined SFTP destination construction.
 * Algorithm: exercise root/nested destinations and reject traversal or separators.
 */

package org.mdview.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadPathsTest {
    @Test
    fun acceptsMarkdownExtensionCaseInsensitively() {
        assertTrue(UploadPaths.isMarkdownFileName("file.md"))
        assertTrue(UploadPaths.isMarkdownFileName("FILE.MD"))
        assertTrue(UploadPaths.isMarkdownFileName("Readme.Md"))
        assertFalse(UploadPaths.isMarkdownFileName("notes.txt"))
    }

    @Test
    fun buildsRootAndNestedDestinations() {
        assertEquals("/repo/root.md", UploadPaths.destination("/repo", "", "root.md"))
        assertEquals(
            "/repo/linux/network/new.MD",
            UploadPaths.destination("/repo/", "linux/network", "new.MD"),
        )
    }

    @Test
    fun rejectsTraversalAndProviderPaths() {
        listOf("../bad.md", "folder/bad.md", "folder\\bad.md").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                UploadPaths.destination("/repo", "linux", name)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            UploadPaths.destination("/repo", "../linux", "safe.md")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UploadPaths.destination("/repo/../other", "linux", "safe.md")
        }
    }

    @Test
    fun distinguishesUploadSuccessFromRefreshFailure() {
        assertEquals("Upload completed", UploadOutcomeText.afterRefresh(true))
        assertEquals(
            "Upload completed, repository refresh failed",
            UploadOutcomeText.afterRefresh(false),
        )
    }
}
