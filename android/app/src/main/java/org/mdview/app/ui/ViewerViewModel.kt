/*
 * ViewerViewModel.kt — created 2026-08-26, version 0.1.0.
 * Purpose: retain document, TOC, search, and reading state across rotation.
 * Algorithm: load one URI asynchronously, expose Compose state, derive search
 * matches once per query, and persist block-based positions per stable URI.
 */

package org.mdview.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mdview.app.data.DocumentRepository
import org.mdview.app.data.ReadingPositionStore
import org.mdview.app.markdown.DocumentSectionResolver
import org.mdview.app.markdown.DocumentSearch
import org.mdview.app.markdown.MarkdownDocument
import org.mdview.app.markdown.MarkdownHeading
import org.mdview.app.markdown.SearchMatch

class ViewerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = DocumentRepository(application.contentResolver)
    private val positions = ReadingPositionStore(application)

    var document by mutableStateOf<MarkdownDocument?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var tocDepth by mutableIntStateOf(savedStateHandle["tocDepth"] ?: 3)
        private set
    var tocOpen by mutableStateOf(savedStateHandle["tocOpen"] ?: false)
        private set
    var selectedHeadingIndex by mutableIntStateOf(0)
        private set
    private var searchState by mutableStateOf(
        SearchUiState(
            open = savedStateHandle["searchOpen"] ?: false,
            query = savedStateHandle["searchQuery"] ?: "",
        ),
    )
    var currentDocumentBlockIndex by mutableIntStateOf(0)
        private set

    init {
        savedStateHandle.get<String>("documentUri")?.let { openDocument(Uri.parse(it)) }
    }

    val visibleHeadings: List<MarkdownHeading>
        get() = document?.headings?.filter { it.level <= tocDepth }.orEmpty()

    val currentMatch: SearchMatch?
        get() = searchState.currentMatch

    val searchOpen: Boolean
        get() = searchState.open

    val searchQuery: String
        get() = searchState.query

    val searchMatches: List<SearchMatch>
        get() = searchState.matches

    val currentMatchIndex: Int
        get() = searchState.currentIndex

    fun openDocument(uri: Uri) {
        loading = true
        errorMessage = null
        savedStateHandle["documentUri"] = uri.toString()
        viewModelScope.launch {
            repository.load(uri).fold(
                onSuccess = { loaded ->
                    document = loaded
                    currentDocumentBlockIndex = positions.load(loaded.id)
                        .coerceAtMost(loaded.blocks.lastIndex.coerceAtLeast(0))
                    synchronizeCurrentSection()
                    updateSearch(searchQuery)
                    loading = false
                },
                onFailure = { failure ->
                    document = null
                    errorMessage = failure.message ?: "Could not read the selected document"
                    loading = false
                },
            )
        }
    }

    fun openSource(id: String, title: String, source: String) {
        loading = true
        errorMessage = null
        savedStateHandle["documentUri"] = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    org.mdview.app.markdown.MarkdownParser.parse(id, title, source)
                }
            }.fold(
                onSuccess = { loaded ->
                    document = loaded
                    currentDocumentBlockIndex = positions.load(loaded.id)
                        .coerceAtMost(loaded.blocks.lastIndex.coerceAtLeast(0))
                    synchronizeCurrentSection()
                    updateSearch(searchQuery)
                    loading = false
                },
                onFailure = { failure ->
                    document = null
                    errorMessage = failure.message ?: "Could not read the selected document"
                    loading = false
                },
            )
        }
    }

    fun cycleTocDepth() {
        tocDepth = ReaderToolbarModel.nextTocDepth(tocDepth)
        savedStateHandle["tocDepth"] = tocDepth
        synchronizeCurrentSection()
    }

    fun updateTocOpen(open: Boolean) {
        tocOpen = open
        savedStateHandle["tocOpen"] = open
        if (open) synchronizeCurrentSection()
    }

    fun selectHeading(heading: MarkdownHeading): Int {
        onDocumentPosition(heading.blockIndex)
        return heading.blockIndex
    }

    fun onDocumentPosition(blockIndex: Int) {
        currentDocumentBlockIndex = blockIndex
        document?.let { positions.save(it.id, blockIndex) }
        synchronizeCurrentSection()
    }

    fun openSearch() {
        searchState = searchState.copy(open = true)
        savedStateHandle["searchOpen"] = true
    }

    fun closeSearch() {
        searchState = searchState.closed()
        savedStateHandle["searchOpen"] = false
        savedStateHandle["searchQuery"] = ""
    }

    fun updateSearch(query: String): Int? {
        savedStateHandle["searchQuery"] = query
        val matches = document?.let { DocumentSearch.find(it, query) }.orEmpty()
        searchState = searchState.copy(
            query = query,
            matches = matches,
            currentIndex = if (matches.isEmpty()) -1 else 0,
        )
        return currentMatch?.blockIndex
    }

    fun nextMatch(direction: Int): Int? {
        if (searchMatches.isEmpty()) return null
        searchState = searchState.copy(
            currentIndex = Math.floorMod(currentMatchIndex + direction, searchMatches.size),
        )
        return currentMatch?.blockIndex
    }

    private fun synchronizeCurrentSection() {
        selectedHeadingIndex = DocumentSectionResolver.visibleHeadingIndex(
            headings = document?.headings.orEmpty(),
            blockIndex = currentDocumentBlockIndex,
            depth = tocDepth,
        )
    }
}
