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
import org.mdview.app.data.DocumentRepository
import org.mdview.app.data.ReadingPositionStore
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
    var restoredBlockIndex by mutableIntStateOf(0)
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
                    restoredBlockIndex = positions.load(loaded.id)
                        .coerceAtMost(loaded.blocks.lastIndex.coerceAtLeast(0))
                    selectedHeadingIndex = headingIndexForBlock(restoredBlockIndex)
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
        tocDepth = tocDepth % 3 + 1
        savedStateHandle["tocDepth"] = tocDepth
        selectedHeadingIndex = headingIndexForBlock(restoredBlockIndex)
    }

    fun updateTocOpen(open: Boolean) {
        tocOpen = open
        savedStateHandle["tocOpen"] = open
    }

    fun selectHeading(heading: MarkdownHeading): Int {
        selectedHeadingIndex = visibleHeadings.indexOf(heading).coerceAtLeast(0)
        return heading.blockIndex
    }

    fun onDocumentPosition(blockIndex: Int) {
        restoredBlockIndex = blockIndex
        document?.let { positions.save(it.id, blockIndex) }
        selectedHeadingIndex = headingIndexForBlock(blockIndex)
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

    private fun headingIndexForBlock(blockIndex: Int): Int {
        val headings = visibleHeadings
        return headings.indexOfLast { it.blockIndex <= blockIndex }.coerceAtLeast(0)
    }
}
