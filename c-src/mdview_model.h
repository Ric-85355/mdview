/*
 * mdview_model.h — created 2026-08-26, experimental version 0.2.3.
 * Purpose: public Unicode document, Markdown-render, and search model for mdview-c.
 * Algorithm: retain source text as wide characters, render source-mapped visual
 * rows, and expose source-level search matches independently from curses.
 */

#ifndef MDVIEW_MODEL_H
#define MDVIEW_MODEL_H

#include <stddef.h>
#include <wchar.h>

#define MD_NO_OFFSET ((size_t)-1)

typedef enum {
    MD_STYLE_NORMAL,
    MD_STYLE_HEADING1,
    MD_STYLE_HEADING2,
    MD_STYLE_HEADING3,
    MD_STYLE_HEADING,
    MD_STYLE_LIST,
    MD_STYLE_TABLE,
    MD_STYLE_CODE,
} MdStyle;

typedef struct {
    wchar_t **lines;
    size_t line_count;
} MdDocument;

typedef struct {
    int level;
    wchar_t *title;
    size_t source_line;
} MdHeading;

typedef struct {
    MdHeading *items;
    size_t count;
} MdHeadingList;

typedef struct {
    wchar_t *text;
    size_t *source_offsets;
    size_t length;
    size_t source_line;
    MdStyle style;
} MdVisualRow;

typedef struct {
    MdVisualRow *rows;
    size_t count;
} MdVisualDocument;

typedef struct {
    size_t source_line;
    size_t source_start;
    size_t source_end;
} MdSearchMatch;

typedef struct {
    MdSearchMatch *items;
    size_t count;
} MdSearchResults;

typedef struct {
    size_t start;
    size_t end;
} MdHighlightRange;

/* Load a regular UTF-8 file into source lines without line endings. */
int md_document_load(
    const char *path, MdDocument *document, char *error, size_t error_size
);
/* Release all source lines owned by a document. */
void md_document_free(MdDocument *document);

/* Build level 1–3 ATX headings while excluding fenced code blocks. */
int md_headings_build(
    const MdDocument *document,
    MdHeadingList *headings,
    char *error,
    size_t error_size
);
/* Release all titles and entries owned by a heading list. */
void md_headings_free(MdHeadingList *headings);

/* Render source-mapped visual rows at the given character wrap width. */
int md_visual_build(
    const MdDocument *document,
    int width,
    MdVisualDocument *visual,
    char *error,
    size_t error_size
);
/* Release all text and offset mappings owned by a visual document. */
void md_visual_free(MdVisualDocument *visual);
/* Locate the first visual row at or after a source line. */
size_t md_visual_index_for_source(
    const MdVisualDocument *visual, size_t source_line
);
/* Locate the first visual row displaying any part of a search match. */
size_t md_visual_index_for_match(
    const MdVisualDocument *visual, const MdSearchMatch *match
);

/* Find non-overlapping, case-insensitive matches in source order. */
int md_search_find(
    const MdDocument *document,
    const wchar_t *query,
    MdSearchResults *results,
    char *error,
    size_t error_size
);
/* Release a collection returned by md_search_find. */
void md_search_free(MdSearchResults *results);
/* Return display-character runs belonging to one source-level match. */
size_t md_highlight_ranges(
    const MdVisualRow *row,
    const MdSearchMatch *match,
    MdHighlightRange *ranges,
    size_t capacity
);

/* Return the terminal-cell width of a wide string in the active locale. */
int md_display_width(const wchar_t *text);
/* Return the character count that fits within a terminal-cell limit. */
size_t md_clip_length(const wchar_t *text, int maximum_width);

#endif
