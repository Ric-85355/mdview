/*
 * RepositoryModelTest.kt — created 2026-09-01, version 0.1.0.
 * Purpose: verify repository parsing, navigation, URL normalization, and refresh restoration.
 * Algorithm: parse representative format-1 fixtures and assert pure model resolution.
 */

package org.mdview.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryModelTest {
    private val source = """
        {
          "format": 1,
          "name": "Test",
          "items": [
            {"type":"document","name":"root","path":"root.md"},
            {"type":"directory","name":"linux","items":[
              {"type":"document","name":"samba","path":"linux/samba.md"},
              {"type":"directory","name":"network","items":[
                {"type":"document","name":"wireguard","path":"linux/network/wireguard.md"}
              ]}
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesRootFoldersDocumentsAndTwoLevels() {
        val repository = RepositoryParser.parse(source)

        assertEquals(listOf("root"), repository.root.documents.map { it.name })
        assertEquals(listOf("linux"), repository.root.directories.map { it.name })
        assertEquals(listOf("samba"), repository.findDirectory("linux")!!.documents.map { it.name })
        assertEquals(
            "wireguard",
            repository.findDirectory("linux/network")!!.documents.single().name,
        )
    }

    @Test
    fun browserShowsDocumentsAndSubfoldersFromSameDirectory() {
        val repository = RepositoryParser.parse(source)
        val entries = repository.findDirectory("linux")!!.browserEntries()

        assertEquals(RepositoryBrowserEntry.Parent, entries.first())
        assertTrue(entries.any {
            it is RepositoryBrowserEntry.Directory && it.value.path == "linux/network"
        })
        assertTrue(entries.any {
            it is RepositoryBrowserEntry.Document && it.value.path == "linux/samba.md"
        })
    }

    @Test
    fun browserNavigationUsesParentThenDirectoriesThenDocuments() {
        val repository = RepositoryParser.parse(source)
        val rootEntries = repository.root.browserEntries()
        assertFalse(rootEntries.contains(RepositoryBrowserEntry.Parent))

        val linux = (rootEntries.first() as RepositoryBrowserEntry.Directory).value
        val linuxEntries = linux.browserEntries()
        assertEquals(RepositoryBrowserEntry.Parent, linuxEntries[0])
        assertTrue(linuxEntries[1] is RepositoryBrowserEntry.Directory)
        assertTrue(linuxEntries[2] is RepositoryBrowserEntry.Document)

        val network = (linuxEntries[1] as RepositoryBrowserEntry.Directory).value
        assertEquals("linux/network", network.path)
        assertEquals(RepositoryBrowserEntry.Parent, network.browserEntries().first())
        assertEquals("linux", network.path.substringBeforeLast('/', ""))
        assertEquals("", linux.path.substringBeforeLast('/', ""))
    }

    @Test
    fun browserSortsFoldersBeforeDocumentsCaseInsensitively() {
        val sorted = RepositoryParser.parse(
            """{"format":1,"name":"Sorted","items":[
              {"type":"document","name":"alpha","path":"alpha.md"},
              {"type":"directory","name":"Linux","items":[]},
              {"type":"document","name":"Notes","path":"Notes.md"},
              {"type":"directory","name":"archive","items":[]}
            ]}""",
        ).root.browserEntries()

        assertEquals(
            listOf("archive", "Linux", "alpha", "Notes"),
            sorted.map {
                when (it) {
                    RepositoryBrowserEntry.Parent -> ".."
                    is RepositoryBrowserEntry.Directory -> it.value.name
                    is RepositoryBrowserEntry.Document -> it.value.name
                }
            },
        )
    }

    @Test
    fun uploadRefreshKeepsNestedPathAndShowsNewDocument() {
        val currentPath = "linux/network"
        assertEquals(
            "/repo/linux/network/new.md",
            UploadPaths.destination("/repo", currentPath, "new.md"),
        )
        val refreshed = RepositoryParser.parse(
            source.replace(
                """{"type":"document","name":"wireguard","path":"linux/network/wireguard.md"}""",
                """{"type":"document","name":"new","path":"linux/network/new.md"},
                   {"type":"document","name":"wireguard","path":"linux/network/wireguard.md"}""",
            ),
        )

        val restored = refreshed.nearestDirectory(currentPath)
        assertEquals(currentPath, restored.path)
        assertEquals(listOf("new", "wireguard"), restored.documents.map { it.name })
    }

    @Test
    fun normalizesBaseUrlWithOrWithoutTrailingSlash() {
        assertEquals("https://example.test/md/", RepositoryPaths.normalizeBaseUrl("https://example.test/md"))
        assertEquals("https://example.test/md/", RepositoryPaths.normalizeBaseUrl("https://example.test/md/"))
        assertEquals(
            "https://example.test/md/linux/samba.md",
            RepositoryPaths.documentUrl("https://example.test/md", "linux/samba.md"),
        )
        assertEquals(
            "https://example.test/md/russian/%D1%84%D0%B0%D0%B9%D0%BB.md",
            RepositoryPaths.documentUrl("https://example.test/md", "russian/файл.md"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryPaths.normalizeBaseUrl("http://example.test/md")
        }
    }

    @Test
    fun restoresExactOrNearestParentAfterRefresh() {
        val repository = RepositoryParser.parse(source)
        assertEquals("linux/network", repository.nearestDirectory("linux/network").path)
        assertEquals("linux", repository.nearestDirectory("linux/missing").path)
        assertEquals("", repository.nearestDirectory("missing/child").path)
        assertNull(repository.findDirectory("missing"))
    }

    @Test
    fun rejectsTraversalAndExcessiveDepth() {
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryPaths.normalizeDocumentPath("../secret.md")
        }
        val tooDeep = source.replace(
            "{\"type\":\"document\",\"name\":\"wireguard\",\"path\":\"linux/network/wireguard.md\"}",
            "{\"type\":\"directory\",\"name\":\"deep\",\"items\":[]}",
        )
        assertThrows(IllegalArgumentException::class.java) { RepositoryParser.parse(tooDeep) }
        assertThrows(IllegalArgumentException::class.java) { RepositoryParser.parse("not json") }
        assertThrows(IllegalArgumentException::class.java) {
            RepositoryParser.parse("""{"format":1,"name":"Missing items"}""")
        }
    }
}
