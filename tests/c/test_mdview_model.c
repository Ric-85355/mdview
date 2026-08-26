/*
 * test_mdview_model.c — created 2026-08-26, version 0.2.3.
 * Purpose: regression-test C model parity with Python mdview scenarios.
 * Algorithm: render deterministic wide-source documents, assert Markdown
 * rows/styles/offsets, and verify Unicode search and highlight ranges.
 */

#include "mdview_model.h"

#include <assert.h>
#include <locale.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

#define ARRAY_COUNT(values) (sizeof(values) / sizeof((values)[0]))

static MdDocument document_from(wchar_t **lines, size_t count)
{
    return (MdDocument){.lines = lines, .line_count = count};
}

static MdVisualDocument render(MdDocument *document, int width)
{
    MdVisualDocument visual;
    char error[256] = {0};

    assert(md_visual_build(document, width, &visual, error, sizeof(error)) == 0);
    return visual;
}

static void assert_row(
    const MdVisualDocument *visual,
    size_t index,
    const wchar_t *text,
    size_t source_line,
    MdStyle style
)
{
    assert(index < visual->count);
    if (wcscmp(visual->rows[index].text, text) != 0) {
        (void)fwprintf(
            stderr,
            L"row %zu mismatch: expected <%ls>, got <%ls>\n",
            index,
            text,
            visual->rows[index].text
        );
    }
    assert(wcscmp(visual->rows[index].text, text) == 0);
    assert(visual->rows[index].source_line == source_line);
    assert(visual->rows[index].style == style);
}

static void test_heading_parser(void)
{
    wchar_t *lines[] = {
        L"# Один",
        L"## Two ##",
        L"```md",
        L"### Hidden",
        L"```",
        L"### Три",
        L"#### Four",
    };
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdHeadingList headings;
    char error[256] = {0};

    assert(md_headings_build(&document, &headings, error, sizeof(error)) == 0);
    assert(headings.count == 3);
    assert(headings.items[0].level == 1);
    assert(wcscmp(headings.items[0].title, L"Один") == 0);
    assert(headings.items[0].source_line == 0);
    assert(headings.items[1].level == 2);
    assert(wcscmp(headings.items[1].title, L"Two") == 0);
    assert(headings.items[1].source_line == 1);
    assert(headings.items[2].level == 3);
    assert(wcscmp(headings.items[2].title, L"Три") == 0);
    assert(headings.items[2].source_line == 5);
    md_headings_free(&headings);
}

static void test_headings_links_and_plain_text(void)
{
    wchar_t *lines[] = {
        L"# Главный heading",
        L"## Section",
        L"### Third",
        L"Visit [OpenAI](https://openai.com) now",
        L"Use `git status` and `[label](url)` first",
        L"Обычный UTF-8 text",
    };
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdVisualDocument visual = render(&document, 80);

    assert_row(&visual, 0, L"ГЛАВНЫЙ HEADING", 0, MD_STYLE_HEADING1);
    assert(visual.rows[0].source_offsets[0] == 2);
    assert_row(&visual, 1, L"Section", 1, MD_STYLE_HEADING2);
    assert_row(&visual, 2, L"Third", 2, MD_STYLE_HEADING3);
    assert_row(
        &visual,
        3,
        L"Visit OpenAI (https://openai.com) now",
        3,
        MD_STYLE_NORMAL
    );
    assert(visual.rows[3].source_offsets[14] == 15);
    assert_row(
        &visual,
        4,
        L"Use `git status` and `[label](url)` first",
        4,
        MD_STYLE_NORMAL
    );
    assert_row(&visual, 5, lines[5], 5, MD_STYLE_NORMAL);
    assert(visual.rows[5].source_offsets[0] == 0);
    md_visual_free(&visual);
}

static void test_code_is_clipped_not_wrapped(void)
{
    wchar_t *lines[] = {L"```", L"очень_длинный_код", L"```"};
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdVisualDocument visual = render(&document, 6);

    assert(visual.count == 3);
    assert_row(&visual, 0, L"```", 0, MD_STYLE_CODE);
    assert_row(&visual, 1, L"очень_", 1, MD_STYLE_CODE);
    assert_row(&visual, 2, L"```", 2, MD_STYLE_CODE);
    md_visual_free(&visual);
}

static void test_simple_table(void)
{
    wchar_t *lines[] = {
        L"| Article type | Maximum character length |",
        L"| ------------ | ------------------------ |",
        L"| articles | 31 |",
        L"| categories | 27 |",
        L"| map topics | 30 |",
    };
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdVisualDocument visual = render(&document, 80);

    assert(visual.count == 4);
    assert_row(
        &visual,
        0,
        L"Article type | Maximum character length",
        0,
        MD_STYLE_TABLE
    );
    assert_row(&visual, 1, L"articles     | 31", 2, MD_STYLE_TABLE);
    assert_row(&visual, 2, L"categories   | 27", 3, MD_STYLE_TABLE);
    assert_row(&visual, 3, L"map topics   | 30", 4, MD_STYLE_TABLE);
    assert(visual.rows[1].source_offsets[0] == 2);
    md_visual_free(&visual);
}

static void test_lists_and_wrapping(void)
{
    wchar_t *lines[] = {
        L"- First",
        L"  - Second",
        L"    - Third",
        L"1. First",
        L"  2. Second",
        L"    3. Third",
        L"  - long list item that wraps",
        L"- Run `git status` now",
        L"-not a list",
        L"1.not a list",
    };
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdVisualDocument visual = render(&document, 16);

    assert_row(&visual, 0, L"◆ First", 0, MD_STYLE_LIST);
    assert_row(&visual, 1, L"  ▸ Second", 1, MD_STYLE_LIST);
    assert_row(&visual, 2, L"    ▪ Third", 2, MD_STYLE_LIST);
    assert_row(&visual, 3, L"1. First", 3, MD_STYLE_LIST);
    assert_row(&visual, 4, L"  2. Second", 4, MD_STYLE_LIST);
    assert_row(&visual, 5, L"    3. Third", 5, MD_STYLE_LIST);
    assert_row(&visual, 6, L"  ▸ long list", 6, MD_STYLE_LIST);
    assert_row(&visual, 7, L"    item that", 6, MD_STYLE_LIST);
    assert_row(&visual, 8, L"    wraps", 6, MD_STYLE_LIST);
    assert_row(&visual, 9, L"◆ Run `git", 7, MD_STYLE_LIST);
    assert_row(&visual, 10, L"  status` now", 7, MD_STYLE_LIST);
    assert_row(&visual, 11, L"-not a list", 8, MD_STYLE_NORMAL);
    assert_row(&visual, 12, L"1.not a list", 9, MD_STYLE_NORMAL);
    md_visual_free(&visual);
}

static void test_word_wrap_and_source_mapping(void)
{
    wchar_t *lines[] = {L"12345 abcde", L"界界", L"tail"};
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdVisualDocument visual = render(&document, 7);

    assert_row(&visual, 0, L"12345", 0, MD_STYLE_NORMAL);
    assert_row(&visual, 1, L"abcde", 0, MD_STYLE_NORMAL);
    assert_row(&visual, 2, L"界界", 1, MD_STYLE_NORMAL);
    assert(md_visual_index_for_source(&visual, 2) == 3);
    assert_row(&visual, 3, L"tail", 2, MD_STYLE_NORMAL);
    md_visual_free(&visual);
}

static void test_search_and_highlights(void)
{
    wchar_t *lines[] = {
        L"net net network",
        L"РУССКИЙ ТЕКСТ",
        L"Straße",
        L"12345 abcde",
    };
    MdDocument document = document_from(lines, ARRAY_COUNT(lines));
    MdSearchResults results;
    MdVisualDocument visual = render(&document, 7);
    MdHighlightRange ranges[4];
    size_t crossing_row;
    char error[256] = {0};
    MdSearchMatch crossing = {
        .source_line = 3,
        .source_start = 3,
        .source_end = 8,
    };

    assert(md_search_find(&document, L"net", &results, error, sizeof(error)) == 0);
    assert(results.count == 3);
    assert(results.items[0].source_start == 0 && results.items[0].source_end == 3);
    assert(results.items[1].source_start == 4 && results.items[1].source_end == 7);
    assert(results.items[2].source_start == 8 && results.items[2].source_end == 11);
    md_search_free(&results);

    assert(md_search_find(
        &document, L"русский", &results, error, sizeof(error)
    ) == 0);
    assert(results.count == 1 && results.items[0].source_line == 1);
    assert(results.items[0].source_start == 0 && results.items[0].source_end == 7);
    md_search_free(&results);

    assert(md_search_find(
        &document, L"STRASSE", &results, error, sizeof(error)
    ) == 0);
    assert(results.count == 1 && results.items[0].source_line == 2);
    assert(results.items[0].source_start == 0 && results.items[0].source_end == 6);
    md_search_free(&results);

    crossing_row = md_visual_index_for_match(&visual, &crossing);
    assert(md_highlight_ranges(
        &visual.rows[crossing_row], &crossing, ranges, 4
    ) == 1);
    assert(ranges[0].start == 3 && ranges[0].end == 5);
    assert(md_highlight_ranges(
        &visual.rows[crossing_row + 1], &crossing, ranges, 4
    ) == 1);
    assert(ranges[0].start == 0 && ranges[0].end == 2);
    assert(crossing_row == md_visual_index_for_source(&visual, 3));
    md_visual_free(&visual);
}

int main(void)
{
    assert(setlocale(LC_ALL, "") != NULL);
    test_heading_parser();
    test_headings_links_and_plain_text();
    test_code_is_clipped_not_wrapped();
    test_simple_table();
    test_lists_and_wrapping();
    test_word_wrap_and_source_mapping();
    test_search_and_highlights();
    (void)puts("mdview-c model tests: OK");
    return 0;
}
