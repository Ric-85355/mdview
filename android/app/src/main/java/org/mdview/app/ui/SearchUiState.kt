/*
 * SearchUiState.kt — created 2026-08-27, version 0.1.0.
 * Purpose: keep transient Android search UI state independent of reading position.
 * Algorithm: replace the active query and matches with an empty closed state
 * without carrying or changing document navigation state.
 */

package org.mdview.app.ui

import org.mdview.app.markdown.SearchMatch

internal data class SearchUiState(
    val open: Boolean = false,
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val currentIndex: Int = -1,
) {
    val currentMatch: SearchMatch?
        get() = matches.getOrNull(currentIndex)

    fun closed(): SearchUiState = SearchUiState()
}
