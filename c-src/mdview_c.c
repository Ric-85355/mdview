/*
 * mdview_c.c — created 2026-08-26, version 0.2.3.
 * Purpose: provide the ncursesw functional analogue of the Python mdview TUI.
 * Algorithm: keep navigation/search state over the shared source-mapped model,
 * rebuild rows after resize, and render equivalent panels, styles, and prompts.
 */

#include "mdview_model.h"

#include <getopt.h>
#include <locale.h>
#include <ncursesw/curses.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>
#include <wctype.h>

#define VERSION "0.2.3"
#define MIN_TERMINAL_HEIGHT 8
#define MIN_TERMINAL_WIDTH 40
#define MIN_PANEL_WIDTH 12
#define TOC_PERCENT 30
#define ERROR_SIZE 512

static volatile sig_atomic_t interrupted = 0;

typedef enum {
    PANEL_TOC,
    PANEL_DOCUMENT,
} ActivePanel;

typedef struct {
    const char *path;
    MdDocument document;
    MdHeadingList headings;
    MdVisualDocument visual;
    int toc_depth;
    size_t toc_selected;
    size_t toc_top;
    size_t document_top;
    ActivePanel active_panel;
    int last_document_width;
    wchar_t *search_input;
    size_t search_input_length;
    wchar_t *search_query;
    wchar_t *search_message;
    MdSearchResults search_results;
    size_t search_match_index;
    bool has_search_match;
} Viewer;

static int maximum_int(int left, int right)
{
    return left > right ? left : right;
}

static int minimum_int(int left, int right)
{
    return left < right ? left : right;
}

static void handle_interrupt(int signal_number)
{
    (void)signal_number;
    interrupted = 1;
}

static const char *base_name(const char *path)
{
    const char *separator = strrchr(path, '/');
    return separator == NULL ? path : separator + 1;
}

static wchar_t *wide_duplicate(const wchar_t *text)
{
    size_t length = wcslen(text);
    wchar_t *copy = malloc((length + 1) * sizeof(*copy));

    if (copy != NULL) {
        wmemcpy(copy, text, length + 1);
    }
    return copy;
}

static wchar_t *utf8_to_wide(const char *text)
{
    const char *source = text;
    size_t length = mbsrtowcs(NULL, &source, 0, NULL);
    wchar_t *wide;

    if (length == (size_t)-1) {
        return NULL;
    }
    wide = malloc((length + 1) * sizeof(*wide));
    if (wide != NULL) {
        source = text;
        (void)mbsrtowcs(wide, &source, length + 1, NULL);
    }
    return wide;
}

static wchar_t *wide_message(const wchar_t *prefix, const wchar_t *value)
{
    size_t prefix_length = wcslen(prefix);
    size_t value_length = wcslen(value);
    wchar_t *message = malloc(
        (prefix_length + value_length + 1) * sizeof(*message)
    );

    if (message != NULL) {
        wmemcpy(message, prefix, prefix_length);
        wmemcpy(message + prefix_length, value, value_length + 1);
    }
    return message;
}

static void replace_wide(wchar_t **target, wchar_t *replacement)
{
    free(*target);
    *target = replacement;
}

static void add_wide(
    int row,
    int column,
    const wchar_t *text,
    size_t text_length,
    int attribute,
    int limit
)
{
    wchar_t *copy;
    size_t clipped;

    if (limit <= 0 || text == NULL) {
        return;
    }
    copy = malloc((text_length + 1) * sizeof(*copy));
    if (copy == NULL) {
        return;
    }
    wmemcpy(copy, text, text_length);
    copy[text_length] = L'\0';
    clipped = md_clip_length(copy, limit);
    (void)attron(attribute);
    (void)mvaddnwstr(row, column, copy, (int)clipped);
    (void)attroff(attribute);
    free(copy);
}

static void add_wide_string(
    int row, int column, const wchar_t *text, int attribute, int limit
)
{
    add_wide(row, column, text, wcslen(text), attribute, limit);
}

static void add_wide_character(int row, int column, wchar_t character, int attribute)
{
    add_wide(row, column, &character, 1, attribute, 1);
}

static void draw_box(int top, int left, int bottom, int right, bool active)
{
    int attribute = active ? A_BOLD : A_NORMAL;
    wchar_t horizontal = active ? L'═' : L'─';
    wchar_t vertical = active ? L'║' : L'│';
    int column;
    int row;

    add_wide_character(top, left, active ? L'╔' : L'┌', attribute);
    for (column = left + 1; column < right - 1; ++column) {
        add_wide_character(top, column, horizontal, attribute);
    }
    add_wide_character(top, right - 1, active ? L'╗' : L'┐', attribute);
    for (row = top + 1; row < bottom - 1; ++row) {
        add_wide_character(row, left, vertical, attribute);
        add_wide_character(row, right - 1, vertical, attribute);
    }
    add_wide_character(bottom - 1, left, active ? L'╚' : L'└', attribute);
    for (column = left + 1; column < right - 1; ++column) {
        add_wide_character(bottom - 1, column, horizontal, attribute);
    }
    add_wide_character(
        bottom - 1, right - 1, active ? L'╝' : L'┘', attribute
    );
}

static int toc_width_for(int width)
{
    int toc_width = maximum_int(MIN_PANEL_WIDTH, width * TOC_PERCENT / 100);
    return minimum_int(toc_width, maximum_int(MIN_PANEL_WIDTH, width - MIN_PANEL_WIDTH));
}

static size_t visible_heading_count(const Viewer *viewer)
{
    size_t index;
    size_t count = 0;

    for (index = 0; index < viewer->headings.count; ++index) {
        if (viewer->headings.items[index].level <= viewer->toc_depth) {
            ++count;
        }
    }
    return count;
}

static MdHeading *visible_heading_at(Viewer *viewer, size_t visible_index)
{
    size_t index;
    size_t current = 0;

    for (index = 0; index < viewer->headings.count; ++index) {
        MdHeading *heading = &viewer->headings.items[index];
        if (heading->level > viewer->toc_depth) {
            continue;
        }
        if (current++ == visible_index) {
            return heading;
        }
    }
    return NULL;
}

static MdSearchMatch *current_search_match(Viewer *viewer)
{
    if (!viewer->has_search_match
        || viewer->search_match_index >= viewer->search_results.count) {
        return NULL;
    }
    return &viewer->search_results.items[viewer->search_match_index];
}

static void clamp_document(Viewer *viewer, int height)
{
    size_t visible = (size_t)maximum_int(1, height - 3);
    size_t maximum = viewer->visual.count > visible ? viewer->visual.count - visible : 0;

    if (viewer->document_top > maximum) {
        viewer->document_top = maximum;
    }
}

static void ensure_toc_visible(Viewer *viewer, int height)
{
    size_t visible = (size_t)maximum_int(1, height - 3);

    if (viewer->toc_selected < viewer->toc_top) {
        viewer->toc_top = viewer->toc_selected;
    } else if (viewer->toc_selected >= viewer->toc_top + visible) {
        viewer->toc_top = viewer->toc_selected - visible + 1;
    }
}

static void sync_toc_to_document(Viewer *viewer, int height)
{
    size_t count = visible_heading_count(viewer);
    size_t source_line;
    size_t index;
    size_t selected = 0;

    if (count == 0 || viewer->visual.count == 0) {
        return;
    }
    source_line = viewer->visual.rows[viewer->document_top].source_line;
    for (index = 0; index < count; ++index) {
        MdHeading *heading = visible_heading_at(viewer, index);
        if (heading != NULL && heading->source_line <= source_line) {
            selected = index;
        }
    }
    viewer->toc_selected = selected;
    ensure_toc_visible(viewer, height);
}

static int rebuild_visual(Viewer *viewer, int width, int height, char *error)
{
    int document_width = maximum_int(1, width - toc_width_for(width) - 3);
    size_t anchor_source = 0;
    bool match_was_visible = false;
    MdSearchMatch *match = current_search_match(viewer);
    MdVisualDocument replacement;

    if (document_width == viewer->last_document_width) {
        return 0;
    }
    if (viewer->visual.count > 0) {
        anchor_source = viewer->visual.rows[viewer->document_top].source_line;
        if (match != NULL) {
            size_t match_visual = md_visual_index_for_match(&viewer->visual, match);
            size_t visible_height = (size_t)maximum_int(1, height - 3);
            match_was_visible = match_visual >= viewer->document_top
                && match_visual < viewer->document_top + visible_height;
        }
    }
    if (md_visual_build(
            &viewer->document, document_width, &replacement, error, ERROR_SIZE
        ) != 0) {
        return -1;
    }
    md_visual_free(&viewer->visual);
    viewer->visual = replacement;
    viewer->document_top = match != NULL && match_was_visible
        ? md_visual_index_for_match(&viewer->visual, match)
        : md_visual_index_for_source(&viewer->visual, anchor_source);
    viewer->last_document_width = document_width;
    clamp_document(viewer, height);
    if (viewer->active_panel == PANEL_DOCUMENT) {
        sync_toc_to_document(viewer, height);
    }
    return 0;
}

static int style_attribute(MdStyle style)
{
    switch (style) {
    case MD_STYLE_HEADING1:
        return A_BOLD;
    case MD_STYLE_HEADING2:
    case MD_STYLE_HEADING:
        return A_BOLD | A_UNDERLINE;
    case MD_STYLE_HEADING3:
        return A_UNDERLINE;
    case MD_STYLE_CODE:
        return A_DIM;
    default:
        return A_NORMAL;
    }
}

static void draw_toc(Viewer *viewer, int height, int toc_width)
{
    size_t count = visible_heading_count(viewer);
    size_t visible = (size_t)maximum_int(0, height - 3);
    size_t row;

    for (row = 0; row < visible && viewer->toc_top + row < count; ++row) {
        size_t visible_index = viewer->toc_top + row;
        MdHeading *heading = visible_heading_at(viewer, visible_index);
        wchar_t text[4096];
        int indentation;
        int attribute;

        if (heading == NULL) {
            continue;
        }
        indentation = (heading->level - 1) * 2;
        (void)swprintf(
            text,
            sizeof(text) / sizeof(text[0]),
            L"%ls%*ls%ls",
            visible_index == viewer->toc_selected ? L"> " : L"  ",
            indentation,
            L"",
            heading->title
        );
        attribute = visible_index == viewer->toc_selected ? A_REVERSE : A_NORMAL;
        add_wide_string((int)row + 1, 1, text, attribute, toc_width - 2);
    }
}

static void draw_document(Viewer *viewer, int height, int toc_width, int width)
{
    size_t visible = (size_t)maximum_int(0, height - 3);
    size_t row_index;
    MdSearchMatch *match = current_search_match(viewer);

    for (row_index = 0;
         row_index < visible && viewer->document_top + row_index < viewer->visual.count;
         ++row_index) {
        MdVisualRow *row = &viewer->visual.rows[viewer->document_top + row_index];
        int available_width = width - toc_width - 2;
        add_wide(
            (int)row_index + 1,
            toc_width + 1,
            row->text,
            row->length,
            style_attribute(row->style),
            available_width
        );
        if (match != NULL) {
            MdHighlightRange ranges[16];
            size_t range_count = md_highlight_ranges(row, match, ranges, 16);
            size_t range_index;
            for (range_index = 0; range_index < range_count && range_index < 16;
                 ++range_index) {
                MdHighlightRange range = ranges[range_index];
                wchar_t saved = row->text[range.start];
                int prefix_width;
                row->text[range.start] = L'\0';
                prefix_width = md_display_width(row->text);
                row->text[range.start] = saved;
                add_wide(
                    (int)row_index + 1,
                    toc_width + 1 + prefix_width,
                    row->text + range.start,
                    range.end - range.start,
                    A_BOLD | A_UNDERLINE,
                    available_width - prefix_width
                );
            }
        }
    }
}

static void set_cursor(bool visible, int row, int column)
{
    if (curs_set(visible ? 1 : 0) == ERR) {
        return;
    }
    if (visible) {
        (void)move(row, column);
    }
}

static void draw_viewer(Viewer *viewer, int height, int width)
{
    int toc_width;
    wchar_t title[4096];
    wchar_t *path_name;
    wchar_t *status = NULL;
    const wchar_t *default_status =
        L" h Contents  l Document  j/k move  Tab toggle  1/2/3 TOC  / search  Q quit ";

    (void)erase();
    if (height < MIN_TERMINAL_HEIGHT || width < MIN_TERMINAL_WIDTH) {
        set_cursor(false, 0, 0);
        add_wide_string(0, 0, L"Resize terminal to at least 40×8", A_BOLD, width);
        (void)refresh();
        return;
    }
    toc_width = toc_width_for(width);
    draw_box(0, 0, height - 1, toc_width, viewer->active_panel == PANEL_TOC);
    draw_box(
        0, toc_width, height - 1, width, viewer->active_panel == PANEL_DOCUMENT
    );
    (void)swprintf(
        title, sizeof(title) / sizeof(title[0]), L" Contents (1–%d) ", viewer->toc_depth
    );
    add_wide_string(0, 2, title, A_BOLD, toc_width - 3);
    path_name = utf8_to_wide(base_name(viewer->path));
    if (path_name != NULL) {
        (void)swprintf(
            title,
            sizeof(title) / sizeof(title[0]),
            L" Document: %ls ",
            path_name
        );
        free(path_name);
        add_wide_string(0, toc_width + 2, title, A_BOLD, width - toc_width - 3);
    }
    draw_toc(viewer, height, toc_width);
    draw_document(viewer, height, toc_width, width);
    if (viewer->search_input != NULL) {
        status = wide_message(L" Search: ", viewer->search_input);
    } else if (viewer->search_message != NULL) {
        status = wide_message(L" ", viewer->search_message);
    }
    (void)attron(A_REVERSE);
    (void)mvhline(height - 1, 0, ' ', width);
    (void)attroff(A_REVERSE);
    add_wide_string(
        height - 1,
        0,
        status == NULL ? default_status : status,
        A_REVERSE,
        width
    );
    if (viewer->search_input != NULL) {
        int cursor_column = minimum_int(
            width - 1, md_display_width(status == NULL ? L"" : status)
        );
        set_cursor(true, height - 1, cursor_column);
    } else {
        set_cursor(false, 0, 0);
    }
    free(status);
    (void)refresh();
}

static void change_toc_depth(Viewer *viewer, int depth, int height)
{
    MdHeading *current = visible_heading_at(viewer, viewer->toc_selected);
    size_t current_source = current == NULL ? 0 : current->source_line;
    size_t count;
    size_t index;
    size_t selected = 0;
    size_t visible;
    size_t maximum_top;

    viewer->toc_depth = depth;
    count = visible_heading_count(viewer);
    if (count == 0) {
        viewer->toc_selected = 0;
        viewer->toc_top = 0;
        return;
    }
    for (index = 0; index < count; ++index) {
        MdHeading *heading = visible_heading_at(viewer, index);
        if (heading != NULL && heading->source_line <= current_source) {
            selected = index;
        }
    }
    viewer->toc_selected = selected;
    visible = (size_t)maximum_int(1, height - 3);
    maximum_top = count > visible ? count - visible : 0;
    viewer->toc_top = selected > visible / 3 ? selected - visible / 3 : 0;
    if (viewer->toc_top > maximum_top) {
        viewer->toc_top = maximum_top;
    }
}

static void handle_toc_key(Viewer *viewer, int key, int page, int height)
{
    size_t count = visible_heading_count(viewer);

    if (count == 0) {
        return;
    }
    if (key == KEY_UP && viewer->toc_selected > 0) {
        --viewer->toc_selected;
    } else if (key == KEY_DOWN && viewer->toc_selected + 1 < count) {
        ++viewer->toc_selected;
    } else if (key == KEY_PPAGE) {
        viewer->toc_selected = viewer->toc_selected > (size_t)page
            ? viewer->toc_selected - (size_t)page
            : 0;
    } else if (key == KEY_NPAGE) {
        viewer->toc_selected = (size_t)minimum_int(
            (int)(count - 1), (int)viewer->toc_selected + page
        );
    } else if (key == KEY_HOME) {
        viewer->toc_selected = 0;
    } else if (key == KEY_END) {
        viewer->toc_selected = count - 1;
    } else if (key == '\n' || key == '\r' || key == KEY_ENTER) {
        MdHeading *heading = visible_heading_at(viewer, viewer->toc_selected);
        if (heading != NULL) {
            viewer->document_top = md_visual_index_for_source(
                &viewer->visual, heading->source_line
            );
        }
    }
    ensure_toc_visible(viewer, height);
}

static void handle_document_key(Viewer *viewer, int key, int page, int height)
{
    if (key == KEY_UP && viewer->document_top > 0) {
        --viewer->document_top;
    } else if (key == KEY_DOWN) {
        ++viewer->document_top;
    } else if (key == KEY_PPAGE) {
        viewer->document_top = viewer->document_top > (size_t)page
            ? viewer->document_top - (size_t)page
            : 0;
    } else if (key == KEY_NPAGE) {
        viewer->document_top += (size_t)page;
    } else if (key == KEY_HOME) {
        viewer->document_top = 0;
    } else if (key == KEY_END) {
        viewer->document_top = viewer->visual.count;
    } else {
        return;
    }
    clamp_document(viewer, height);
    sync_toc_to_document(viewer, height);
}

static void reset_search(Viewer *viewer)
{
    replace_wide(&viewer->search_query, wide_duplicate(L""));
    replace_wide(&viewer->search_message, NULL);
    md_search_free(&viewer->search_results);
    viewer->has_search_match = false;
    viewer->search_match_index = 0;
}

static void show_search_match(Viewer *viewer, int height)
{
    MdSearchMatch *match = current_search_match(viewer);
    if (match == NULL) {
        return;
    }
    viewer->document_top = md_visual_index_for_match(&viewer->visual, match);
    clamp_document(viewer, height);
    sync_toc_to_document(viewer, height);
}

static int perform_search(Viewer *viewer, const wchar_t *query, int height, char *error)
{
    MdSearchResults results;

    if (md_search_find(
            &viewer->document, query, &results, error, ERROR_SIZE
        ) != 0) {
        return -1;
    }
    md_search_free(&viewer->search_results);
    viewer->search_results = results;
    replace_wide(&viewer->search_query, wide_duplicate(query));
    replace_wide(&viewer->search_message, NULL);
    if (results.count == 0) {
        viewer->has_search_match = false;
        viewer->search_match_index = 0;
        viewer->search_message = wide_message(L"Not found: ", query);
        return viewer->search_message == NULL ? -1 : 0;
    }
    viewer->has_search_match = true;
    viewer->search_match_index = 0;
    show_search_match(viewer, height);
    return 0;
}

static void repeat_search(Viewer *viewer, int direction, int height)
{
    if (!viewer->has_search_match || viewer->search_results.count == 0) {
        return;
    }
    if (direction > 0) {
        viewer->search_match_index =
            (viewer->search_match_index + 1) % viewer->search_results.count;
    } else {
        viewer->search_match_index = viewer->search_match_index == 0
            ? viewer->search_results.count - 1
            : viewer->search_match_index - 1;
    }
    show_search_match(viewer, height);
}

static int append_search_input(Viewer *viewer, wchar_t character)
{
    wchar_t *updated = realloc(
        viewer->search_input,
        (viewer->search_input_length + 2) * sizeof(*viewer->search_input)
    );
    if (updated == NULL) {
        return -1;
    }
    viewer->search_input = updated;
    viewer->search_input[viewer->search_input_length++] = character;
    viewer->search_input[viewer->search_input_length] = L'\0';
    return 0;
}

static int handle_search_input(
    Viewer *viewer,
    wint_t input,
    bool function_key,
    int height,
    char *error
)
{
    int key = (int)input;

    if (!function_key && input == 27) {
        replace_wide(&viewer->search_input, NULL);
        viewer->search_input_length = 0;
        replace_wide(&viewer->search_message, NULL);
    } else if ((!function_key && (input == L'\n' || input == L'\r'))
        || (function_key && key == KEY_ENTER)) {
        wchar_t *query = viewer->search_input;
        viewer->search_input = NULL;
        viewer->search_input_length = 0;
        if (query[0] != L'\0'
            && perform_search(viewer, query, height, error) != 0) {
            free(query);
            return -1;
        }
        free(query);
    } else if ((function_key && key == KEY_BACKSPACE)
        || (!function_key && (input == L'\b' || input == 127))) {
        if (viewer->search_input_length > 0) {
            viewer->search_input[--viewer->search_input_length] = L'\0';
        }
    } else if (!function_key && iswprint(input)) {
        if (append_search_input(viewer, (wchar_t)input) != 0) {
            (void)snprintf(error, ERROR_SIZE, "out of memory while editing search");
            return -1;
        }
    }
    return 0;
}

static bool handle_key(Viewer *viewer, wint_t input, bool function_key, int height)
{
    int key = (int)input;
    int page = maximum_int(1, height - 4);

    replace_wide(&viewer->search_message, NULL);
    if (!function_key && input == 27) {
        reset_search(viewer);
        return true;
    }
    if (!function_key && input == L'/') {
        replace_wide(&viewer->search_input, wide_duplicate(L""));
        viewer->search_input_length = 0;
        return true;
    }
    if (!function_key && input == L'.') {
        repeat_search(viewer, 1, height);
        return true;
    }
    if (!function_key && input == L',') {
        repeat_search(viewer, -1, height);
        return true;
    }
    if (!function_key && input == L'h') {
        viewer->active_panel = PANEL_TOC;
        return true;
    }
    if (!function_key && input == L'l') {
        viewer->active_panel = PANEL_DOCUMENT;
        return true;
    }
    if (!function_key && input == L'\t') {
        viewer->active_panel = viewer->active_panel == PANEL_TOC
            ? PANEL_DOCUMENT
            : PANEL_TOC;
        return true;
    }
    if (!function_key && input >= L'1' && input <= L'3') {
        change_toc_depth(viewer, (int)(input - L'0'), height);
        return true;
    }
    if (!function_key && input == L'j') {
        key = KEY_DOWN;
    } else if (!function_key && input == L'k') {
        key = KEY_UP;
    }
    if (viewer->active_panel == PANEL_TOC) {
        handle_toc_key(viewer, key, page, height);
    } else {
        handle_document_key(viewer, key, page, height);
    }
    return true;
}

static int run_viewer(Viewer *viewer, char *error)
{
    bool running = true;
    void (*previous_interrupt)(int);

    interrupted = 0;
    previous_interrupt = signal(SIGINT, handle_interrupt);
    if (previous_interrupt == SIG_ERR) {
        (void)snprintf(error, ERROR_SIZE, "could not install interrupt handler");
        return -1;
    }

    if (initscr() == NULL) {
        (void)signal(SIGINT, previous_interrupt);
        (void)snprintf(error, ERROR_SIZE, "could not initialize terminal");
        return -1;
    }
    (void)cbreak();
    (void)noecho();
    (void)keypad(stdscr, true);
    (void)curs_set(0);
    while (running && !interrupted) {
        int height;
        int width;
        wint_t input;
        int input_type;

        getmaxyx(stdscr, height, width);
        if (rebuild_visual(viewer, width, height, error) != 0) {
            (void)endwin();
            (void)signal(SIGINT, previous_interrupt);
            return -1;
        }
        draw_viewer(viewer, height, width);
        input_type = get_wch(&input);
        if (input_type == ERR) {
            continue;
        }
        if (viewer->search_input != NULL) {
            if (handle_search_input(
                    viewer, input, input_type == KEY_CODE_YES, height, error
                ) != 0) {
                (void)endwin();
                (void)signal(SIGINT, previous_interrupt);
                return -1;
            }
            continue;
        }
        if (input_type != KEY_CODE_YES && (input == L'Q' || input == L'q')) {
            running = false;
        } else {
            (void)handle_key(viewer, input, input_type == KEY_CODE_YES, height);
        }
    }
    (void)endwin();
    (void)signal(SIGINT, previous_interrupt);
    return interrupted ? 130 : 0;
}

static void print_help(const char *program)
{
    (void)printf(
        "usage: %s [-h] [--version] file\n\n"
        "View a Markdown file in the terminal\n\n"
        "positional arguments:\n"
        "  file        Markdown file encoded as UTF-8\n\n"
        "options:\n"
        "  -h, --help  show this help message and exit\n"
        "  --version   show program's version number and exit\n",
        program
    );
}

static int parse_arguments(int argc, char **argv, const char **path)
{
    static const struct option options[] = {
        {"help", no_argument, NULL, 'h'},
        {"version", no_argument, NULL, 'V'},
        {NULL, 0, NULL, 0},
    };
    int option;

    opterr = 0;
    while ((option = getopt_long(argc, argv, "h", options, NULL)) != -1) {
        if (option == 'h') {
            print_help(base_name(argv[0]));
            return 1;
        }
        if (option == 'V') {
            (void)printf("mdview-c %s\n", VERSION);
            return 1;
        }
        (void)fprintf(
            stderr,
            "usage: %s [-h] [--version] file\n"
            "%s: error: unrecognized arguments: %s\n",
            base_name(argv[0]),
            base_name(argv[0]),
            argv[optind - 1]
        );
        return -1;
    }
    if (optind == argc) {
        (void)fprintf(
            stderr,
            "usage: %s [-h] [--version] file\n"
            "%s: error: the following arguments are required: file\n",
            base_name(argv[0]),
            base_name(argv[0])
        );
        return -1;
    }
    if (optind + 1 < argc) {
        (void)fprintf(
            stderr,
            "usage: %s [-h] [--version] file\n"
            "%s: error: unrecognized arguments: %s\n",
            base_name(argv[0]),
            base_name(argv[0]),
            argv[optind + 1]
        );
        return -1;
    }
    *path = argv[optind];
    return 0;
}

int main(int argc, char **argv)
{
    Viewer viewer = {
        .toc_depth = 3,
        .active_panel = PANEL_TOC,
        .last_document_width = -1,
    };
    const char *path = NULL;
    char error[ERROR_SIZE] = {0};
    int argument_result;
    int result = EXIT_FAILURE;

    argument_result = parse_arguments(argc, argv, &path);
    if (argument_result != 0) {
        return argument_result > 0 ? EXIT_SUCCESS : 2;
    }
    if (setlocale(LC_ALL, "") == NULL) {
        (void)fprintf(stderr, "mdview-c: error: could not activate the current locale\n");
        return EXIT_FAILURE;
    }
    viewer.path = path;
    viewer.search_query = wide_duplicate(L"");
    if (viewer.search_query == NULL) {
        (void)fprintf(stderr, "mdview-c: error: out of memory\n");
        goto cleanup;
    }
    if (md_document_load(path, &viewer.document, error, sizeof(error)) != 0) {
        (void)fprintf(stderr, "mdview-c: error: %s\n", error);
        goto cleanup;
    }
    if (md_headings_build(
            &viewer.document, &viewer.headings, error, sizeof(error)
        ) != 0) {
        (void)fprintf(stderr, "mdview-c: error: %s\n", error);
        goto cleanup;
    }
    {
        int viewer_result = run_viewer(&viewer, error);
        if (viewer_result == 130) {
            result = 130;
            goto cleanup;
        }
        if (viewer_result != 0) {
        (void)fprintf(stderr, "mdview-c: terminal error: %s\n", error);
        goto cleanup;
        }
    }
    result = EXIT_SUCCESS;

cleanup:
    free(viewer.search_input);
    free(viewer.search_query);
    free(viewer.search_message);
    md_search_free(&viewer.search_results);
    md_visual_free(&viewer.visual);
    md_headings_free(&viewer.headings);
    md_document_free(&viewer.document);
    return result;
}
