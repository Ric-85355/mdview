/*
 * test_mdview_navigation.c — created 2026-08-26, version 0.3.0.
 * Purpose: regression-test mdview-c panel activation and TOC navigation.
 * Algorithm: include the TUI state machine without starting curses, build a
 * small source-mapped document, and assert key-driven state transitions.
 */

#define main mdview_c_program_main
#include "../../c-src/mdview_c.c"
#undef main

#include <assert.h>
#include <locale.h>
#include <stdio.h>

#define ARRAY_COUNT(values) (sizeof(values) / sizeof((values)[0]))

int main(void)
{
    wchar_t *lines[] = {
        L"# First",
        L"first text",
        L"## Second",
        L"second text",
        L"### Third",
        L"third text",
        L"# Last",
        L"last text",
    };
    Viewer viewer = {
        .document = {.lines = lines, .line_count = ARRAY_COUNT(lines)},
        .toc_depth = 3,
        .active_panel = PANEL_TOC,
        .last_document_width = 60,
    };
    char error[256] = {0};
    size_t expected;

    assert(setlocale(LC_ALL, "") != NULL);
    assert(md_headings_build(
        &viewer.document, &viewer.headings, error, sizeof(error)
    ) == 0);
    assert(md_visual_build(
        &viewer.document, 60, &viewer.visual, error, sizeof(error)
    ) == 0);

    viewer.toc_selected = 1;
    assert(handle_key(&viewer, L'j', false, 9));
    assert(viewer.toc_selected == 2);
    assert(viewer.document_top == 0);
    assert(handle_key(&viewer, L'k', false, 9));
    assert(viewer.toc_selected == 1);
    assert(viewer.document_top == 0);

    expected = md_visual_index_for_source(
        &viewer.visual, viewer.headings.items[1].source_line
    );
    assert(handle_key(&viewer, L'l', false, 9));
    assert(viewer.active_panel == PANEL_DOCUMENT);
    assert(viewer.document_top == expected);
    viewer.document_top = 0;
    assert(handle_key(&viewer, L'l', false, 9));
    assert(viewer.document_top == 0);

    assert(handle_key(&viewer, L'h', false, 9));
    assert(viewer.active_panel == PANEL_TOC);
    assert(viewer.document_top == 0);
    viewer.toc_selected = 2;
    expected = md_visual_index_for_source(
        &viewer.visual, viewer.headings.items[2].source_line
    );
    assert(handle_key(&viewer, L'\t', false, 9));
    assert(viewer.active_panel == PANEL_DOCUMENT);
    assert(viewer.document_top == expected);
    assert(handle_key(&viewer, L'\t', false, 9));
    assert(viewer.active_panel == PANEL_TOC);
    assert(viewer.document_top == expected);

    viewer.document_top = 0;
    assert(handle_key(&viewer, L'\n', false, 9));
    assert(viewer.active_panel == PANEL_TOC);
    assert(viewer.document_top == expected);

    md_visual_free(&viewer.visual);
    md_headings_free(&viewer.headings);
    (void)puts("mdview-c navigation tests: OK");
    return 0;
}
