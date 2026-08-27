/*
 * MdviewApp.kt — created 2026-08-26, version 0.1.0.
 * Purpose: render the Android reader, separate TOC area, search, and top controls.
 * Algorithm: display parsed blocks in a LazyColumn, synchronize its first item
 * with ViewModel state, and navigate by block indexes for TOC/search/restore.
 */

package org.mdview.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mdview.app.BuildConfig
import org.mdview.app.R
import org.mdview.app.markdown.InlineContent
import org.mdview.app.markdown.InlineStyle
import org.mdview.app.markdown.DocumentSearch
import org.mdview.app.markdown.MarkdownBlock
import org.mdview.app.markdown.MarkdownDocument
import org.mdview.app.markdown.MarkdownHeading
import org.mdview.app.markdown.SearchMatch

@Composable
fun MdviewApp(viewModel: ViewerViewModel, onOpenDocument: () -> Unit) {
    MdviewTheme {
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var aboutOpen by remember { mutableStateOf(false) }
        val document = viewModel.document
        val navigateToBlock: (Int) -> Unit = { blockIndex ->
            viewModel.onDocumentPosition(blockIndex)
            scope.launch { listState.animateScrollToItem(blockIndex) }
        }

        LaunchedEffect(document?.id) {
            if (document != null && document.blocks.isNotEmpty()) {
                listState.scrollToItem(viewModel.currentDocumentBlockIndex)
            }
        }
        LaunchedEffect(listState, document?.id) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect(viewModel::onDocumentPosition)
        }

        Scaffold(
            topBar = {
                ReaderTopBar(
                    depth = viewModel.tocDepth,
                    tocOpen = viewModel.tocOpen,
                    onOpenDocument = onOpenDocument,
                    onTocAction = {
                        if (viewModel.tocOpen) viewModel.cycleTocDepth()
                        else viewModel.updateTocOpen(true)
                    },
                    onSearch = viewModel::openSearch,
                    onAbout = { aboutOpen = true },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (viewModel.searchOpen) {
                    SearchBar(
                        query = viewModel.searchQuery,
                        current = viewModel.currentMatchIndex,
                        count = viewModel.searchMatches.size,
                        onQueryChange = { query ->
                            viewModel.updateSearch(query)?.let(navigateToBlock)
                        },
                        onMove = { direction ->
                            viewModel.nextMatch(direction)?.let(navigateToBlock)
                        },
                        onClose = viewModel::closeSearch,
                    )
                }
                ReaderBody(
                    viewModel = viewModel,
                    document = document,
                    listState = listState,
                    onOpenDocument = onOpenDocument,
                    onScrollTo = { scope.launch { listState.animateScrollToItem(it) } },
                    modifier = Modifier.weight(1f),
                )
                AdBannerPlaceholder()
            }
        }
        if (aboutOpen) {
            AlertDialog(
                onDismissRequest = { aboutOpen = false },
                confirmButton = {
                    TextButton(onClick = { aboutOpen = false }) { Text("OK") }
                },
                title = { Text("mdview") },
                text = { Text("Read-only Markdown viewer for Android ${BuildConfig.VERSION_NAME}") },
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    depth: Int,
    tocOpen: Boolean,
    onOpenDocument: () -> Unit,
    onTocAction: () -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text("☰", fontSize = 24.sp)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open file") },
                        onClick = { menuOpen = false; onOpenDocument() },
                    )
                    DropdownMenuItem(
                        text = { Text("About") },
                        onClick = { menuOpen = false; onAbout() },
                    )
                }
            }
            TocButton(depth = depth, tocOpen = tocOpen, onClick = onTocAction)
            TextButton(onClick = onSearch) { Text("⌕", fontSize = 26.sp) }
        }
    }
}

@Composable
private fun TocButton(depth: Int, tocOpen: Boolean, onClick: () -> Unit) {
    Box {
        TextButton(onClick = onClick) {
            Text("☷", fontSize = 22.sp)
        }
        Surface(
            modifier = Modifier.size(18.dp).align(Alignment.TopEnd),
            shape = CircleShape,
            color = if (tocOpen) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
            contentColor = if (tocOpen) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(depth.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReaderBody(
    viewModel: ViewerViewModel,
    document: MarkdownDocument?,
    listState: LazyListState,
    onOpenDocument: () -> Unit,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = viewModel.tocOpen && document != null,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
        ) {
            TocPanel(
                headings = viewModel.visibleHeadings,
                selectedIndex = viewModel.selectedHeadingIndex,
                onSelect = { onScrollTo(viewModel.selectHeading(it)) },
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                viewModel.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                viewModel.errorMessage != null -> ErrorState(
                    viewModel.errorMessage!!,
                    onOpenDocument,
                )
                document == null -> EmptyState(onOpenDocument)
                document.blocks.isEmpty() -> Text(
                    "This Markdown file is empty.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> DocumentList(
                    document = document,
                    currentMatch = viewModel.currentMatch,
                    listState = listState,
                    onDoubleTap = {
                        if (viewModel.tocOpen) viewModel.updateTocOpen(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun DocumentList(
    document: MarkdownDocument,
    currentMatch: SearchMatch?,
    listState: LazyListState,
    onDoubleTap: () -> Unit,
) {
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            itemsIndexed(document.blocks, key = { index, _ -> index }) { index, block ->
                MarkdownBlockView(block, currentMatch?.takeIf { it.blockIndex == index })
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, match: SearchMatch?) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            richText(block.content, match),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
            fontSize = when (block.level) { 1 -> 28.sp; 2 -> 23.sp; else -> 19.sp },
            fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold,
        )
        is MarkdownBlock.Paragraph -> Text(
            richText(block.content, match),
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            lineHeight = 23.sp,
        )
        is MarkdownBlock.ListItem -> Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = (block.level * 18).dp,
                top = 3.dp,
                bottom = 3.dp,
            ),
            verticalAlignment = Alignment.Top,
        ) {
            Text("${block.marker} ", fontWeight = FontWeight.Medium)
            Text(richText(block.content, match), modifier = Modifier.weight(1f), lineHeight = 22.sp)
        }
        is MarkdownBlock.CodeBlock -> CodeBlockView(block, match)
        MarkdownBlock.Spacer -> Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock, match: SearchMatch?) {
    val scrollState = rememberScrollState()
    var textLayout by remember(block.plainText) { mutableStateOf<TextLayoutResult?>(null) }
    val matchPosition = match?.let { DocumentSearch.positionInCodeBlock(block.plainText, it) }

    LaunchedEffect(matchPosition, textLayout) {
        val layout = textLayout ?: return@LaunchedEffect
        val position = matchPosition ?: return@LaunchedEffect
        val line = layout.getLineForOffset(position.characterOffset)
        scrollState.animateScrollTo(layout.getLineTop(line).toInt())
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(Modifier.heightIn(max = 360.dp).verticalScroll(scrollState)) {
            Text(
                highlightedPlainText(block.plainText, match),
                modifier = Modifier.padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                onTextLayout = { textLayout = it },
            )
        }
    }
}

@Composable
private fun richText(content: InlineContent, match: SearchMatch?): AnnotatedString =
    buildAnnotatedString {
        append(content.plainText)
        content.spans.forEach { span ->
            val style = when (span.style) {
                InlineStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                InlineStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                InlineStyle.Code -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                )
                InlineStyle.Link -> SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
            }
            addStyle(style, span.start, span.end)
        }
        match?.let {
            addStyle(
                SpanStyle(
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
                it.start.coerceIn(0, length),
                it.end.coerceIn(0, length),
            )
        }
    }

@Composable
private fun highlightedPlainText(text: String, match: SearchMatch?): AnnotatedString =
    buildAnnotatedString {
        append(text)
        match?.let {
            addStyle(
                SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer),
                it.start.coerceIn(0, length),
                it.end.coerceIn(0, length),
            )
        }
    }

@Composable
private fun TocPanel(
    headings: List<MarkdownHeading>,
    selectedIndex: Int,
    onSelect: (MarkdownHeading) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, headings.size) {
        if (selectedIndex !in headings.indices) return@LaunchedEffect
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.none { it.index == selectedIndex }) {
            listState.animateScrollToItem(selectedIndex)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.48f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        if (headings.isEmpty()) {
            Text("No headings at this depth", Modifier.padding(20.dp))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                itemsIndexed(headings) { index, heading ->
                    val selected = index == selectedIndex
                    Text(
                        heading.title,
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                if (selected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent,
                            )
                            .clickable { onSelect(heading) }
                            .padding(
                                start = (16 + (heading.level - 1) * 22).dp,
                                end = 16.dp,
                                top = 11.dp,
                                bottom = 11.dp,
                            ),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    current: Int,
    count: Int,
    onQueryChange: (String) -> Unit,
    onMove: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search)) },
        )
        Spacer(Modifier.width(6.dp))
        Text(if (count == 0) "0/0" else "${current + 1}/$count")
        TextButton(onClick = { onMove(-1) }, enabled = count > 0) { Text("‹") }
        TextButton(onClick = { onMove(1) }, enabled = count > 0) { Text("›") }
        TextButton(onClick = onClose) { Text("×") }
    }
}

@Composable
private fun EmptyState(onOpenDocument: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Open a local Markdown document to start reading.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenDocument) { Text(stringResource(R.string.open_document)) }
    }
}

@Composable
private fun ErrorState(message: String, onOpenDocument: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenDocument) { Text("Choose another file") }
    }
}
