/*
 * UploadPathsTest.kt — created 2026-09-01, version 0.1.0.
 * Purpose: verify confined SFTP upload and repository mutation paths and decisions.
 * Algorithm: exercise root/nested destinations, validation, collisions, and safe deletion.
 */

package org.mdview.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun buildsNewFolderDestinationInCurrentDirectory() {
        assertEquals(
            "/repo/linux/network",
            RepositoryMutationPaths.newFolderPath("/repo", "linux", " network "),
        )
        assertEquals(
            "/repo/hardware",
            RepositoryMutationPaths.newFolderPath("/repo", "", "hardware"),
        )
    }

    @Test
    fun rejectsUnsafeFolderNamesAndUnsupportedDepth() {
        listOf("", ".", "..", "../test", "test/sub", "/test", "test\\sub").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                RepositoryMutationPaths.newFolderPath("/repo", "linux", name)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryMutationPaths.newFolderPath("/repo", "linux/network", "third")
        }
    }

    @Test
    fun renamePathsRemainInCurrentParent() {
        assertEquals(
            "/repo/hardware/test/router.md",
            RepositoryMutationPaths.objectPath(
                "/repo",
                "hardware/test",
                RepositoryMutationPaths.safeDocumentName("router.md"),
            ),
        )
        assertEquals(
            "/repo/linux/archive",
            RepositoryMutationPaths.objectPath(
                "/repo",
                "linux",
                RepositoryMutationPaths.safeFolderName("archive"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryMutationPaths.safeDocumentName("../router.md")
        }
    }

    @Test
    fun mutationRulesPreventCollisionAndNonEmptyFolderDeletion() {
        assertEquals(
            RepositoryMutationResult.AlreadyExists,
            RepositoryMutationRules.collisionResult(targetExists = true),
        )
        assertNull(RepositoryMutationRules.collisionResult(targetExists = false))
        assertEquals(
            RepositoryMutationResult.Completed,
            RepositoryMutationRules.folderDeleteResult(hasChildren = false),
        )
        assertEquals(
            RepositoryMutationResult.FolderNotEmpty,
            RepositoryMutationRules.folderDeleteResult(hasChildren = true),
        )
    }

    @Test
    fun deletePathTargetsOnlySelectedObjectAndCannotEscapeRoot() {
        assertEquals(
            "/repo/hardware/test/notes.md",
            RepositoryMutationPaths.objectPath("/repo", "hardware/test", "notes.md"),
        )
        listOf("../notes.md", "folder/notes.md", "/notes.md").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                RepositoryMutationPaths.objectPath("/repo", "hardware", name)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryMutationPaths.objectPath("/repo/../outside", "hardware", "notes.md")
        }
    }

    @Test
    fun mutationOutcomeDistinguishesRefreshFailure() {
        assertEquals(
            "File renamed",
            RepositoryMutationOutcomeText.afterRefresh("File renamed", true),
        )
        assertEquals(
            "File renamed, repository refresh failed",
            RepositoryMutationOutcomeText.afterRefresh("File renamed", false),
        )
    }
}
