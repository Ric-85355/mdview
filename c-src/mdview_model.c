/*
 * mdview_model.c — created 2026-08-26, version 0.3.0.
 * Purpose: implement the curses-independent Python-compatible mdview model.
 * Algorithm: parse UTF-8 into wide source lines, apply the same minimal
 * Markdown transforms, wrap with source offsets, and search source text.
 */

#include "mdview_model.h"

#include <errno.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <wctype.h>

typedef struct {
    wchar_t *text;
    size_t *offsets;
    size_t length;
    size_t capacity;
} MappedText;

typedef struct {
    size_t start;
    size_t end;
} Cell;

typedef struct {
    Cell *cells;
    size_t count;
} CellRow;

static void set_error(char *error, size_t error_size, const char *format, ...)
{
    va_list arguments;

    if (error == NULL || error_size == 0) {
        return;
    }
    va_start(arguments, format);
    (void)vsnprintf(error, error_size, format, arguments);
    va_end(arguments);
}

static wchar_t *duplicate_wide_range(const wchar_t *start, size_t length)
{
    wchar_t *copy = malloc((length + 1) * sizeof(*copy));

    if (copy == NULL) {
        return NULL;
    }
    wmemcpy(copy, start, length);
    copy[length] = L'\0';
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
    if (wide == NULL) {
        return NULL;
    }
    source = text;
    (void)mbsrtowcs(wide, &source, length + 1, NULL);
    return wide;
}

static int mapped_reserve(MappedText *mapped, size_t needed)
{
    wchar_t *text;
    size_t *offsets;
    size_t capacity = mapped->capacity == 0 ? 32 : mapped->capacity;

    while (capacity < needed) {
        capacity *= 2;
    }
    text = realloc(mapped->text, (capacity + 1) * sizeof(*text));
    if (text == NULL) {
        return -1;
    }
    mapped->text = text;
    offsets = realloc(mapped->offsets, capacity * sizeof(*offsets));
    if (offsets == NULL) {
        return -1;
    }
    mapped->offsets = offsets;
    mapped->capacity = capacity;
    return 0;
}

static int mapped_append(MappedText *mapped, wchar_t character, size_t offset)
{
    if (mapped_reserve(mapped, mapped->length + 1) != 0) {
        return -1;
    }
    mapped->text[mapped->length] = character;
    mapped->offsets[mapped->length] = offset;
    mapped->length++;
    mapped->text[mapped->length] = L'\0';
    return 0;
}

static int mapped_append_range(
    MappedText *mapped,
    const wchar_t *text,
    size_t start,
    size_t end,
    bool uppercase
)
{
    size_t index;

    for (index = start; index < end; ++index) {
        wchar_t character = text[index];
        if (uppercase && character == L'ß') {
            if (mapped_append(mapped, L'S', index) != 0
                || mapped_append(mapped, L'S', index) != 0) {
                return -1;
            }
        } else if (mapped_append(
                       mapped,
                       uppercase ? (wchar_t)towupper(character) : character,
                       index
                   ) != 0) {
            return -1;
        }
    }
    return 0;
}

static void mapped_free(MappedText *mapped)
{
    free(mapped->text);
    free(mapped->offsets);
    *mapped = (MappedText){0};
}

static int append_document_line(MdDocument *document, wchar_t *line)
{
    wchar_t **updated = realloc(
        document->lines, (document->line_count + 1) * sizeof(*document->lines)
    );

    if (updated == NULL) {
        return -1;
    }
    document->lines = updated;
    document->lines[document->line_count++] = line;
    return 0;
}

int md_document_load(
    const char *path, MdDocument *document, char *error, size_t error_size
)
{
    struct stat status;
    FILE *stream;
    char *line = NULL;
    size_t capacity = 0;
    ssize_t length;

    *document = (MdDocument){0};
    if (stat(path, &status) != 0) {
        if (errno == ENOENT) {
            set_error(error, error_size, "file does not exist: %s", path);
        } else if (errno == EACCES) {
            set_error(error, error_size, "permission denied: %s", path);
        } else {
            set_error(error, error_size, "could not access file %s", path);
        }
        return -1;
    }
    if (!S_ISREG(status.st_mode)) {
        set_error(error, error_size, "path is not a regular file: %s", path);
        return -1;
    }
    stream = fopen(path, "r");
    if (stream == NULL) {
        set_error(
            error,
            error_size,
            errno == EACCES ? "permission denied: %s" : "could not read file: %s",
            path
        );
        return -1;
    }
    while ((length = getline(&line, &capacity, stream)) >= 0) {
        wchar_t *wide;

        while (length > 0 && (line[length - 1] == '\n' || line[length - 1] == '\r')) {
            line[--length] = '\0';
        }
        wide = utf8_to_wide(line);
        if (wide == NULL) {
            set_error(error, error_size, "file is not valid UTF-8: %s", path);
            free(line);
            (void)fclose(stream);
            md_document_free(document);
            return -1;
        }
        if (append_document_line(document, wide) != 0) {
            free(wide);
            set_error(error, error_size, "out of memory while reading %s", path);
            free(line);
            (void)fclose(stream);
            md_document_free(document);
            return -1;
        }
    }
    free(line);
    if (ferror(stream)) {
        set_error(error, error_size, "could not read file: %s", path);
        (void)fclose(stream);
        md_document_free(document);
        return -1;
    }
    if (fclose(stream) != 0) {
        set_error(error, error_size, "could not close file: %s", path);
        md_document_free(document);
        return -1;
    }
    return 0;
}

void md_document_free(MdDocument *document)
{
    size_t index;

    for (index = 0; index < document->line_count; ++index) {
        free(document->lines[index]);
    }
    free(document->lines);
    *document = (MdDocument){0};
}

static bool heading_bounds(
    const wchar_t *line,
    int *level,
    size_t *title_start,
    size_t *title_end
)
{
    size_t position = 0;
    size_t hash_start;
    size_t end;

    while (position < 3 && line[position] == L' ') {
        ++position;
    }
    hash_start = position;
    while (line[position] == L'#') {
        ++position;
    }
    *level = (int)(position - hash_start);
    if (*level < 1 || *level > 6 || (line[position] != L' ' && line[position] != L'\t')) {
        return false;
    }
    while (line[position] == L' ' || line[position] == L'\t') {
        ++position;
    }
    end = wcslen(line);
    while (end > position && (line[end - 1] == L' ' || line[end - 1] == L'\t')) {
        --end;
    }
    while (end > position + 1 && line[end - 1] == L'#') {
        --end;
    }
    while (end > position && (line[end - 1] == L' ' || line[end - 1] == L'\t')) {
        --end;
    }
    if (end == position) {
        return false;
    }
    *title_start = position;
    *title_end = end;
    return true;
}

static int append_heading(
    MdHeadingList *headings,
    int level,
    const wchar_t *title,
    size_t length,
    size_t source_line
)
{
    MdHeading *updated = realloc(
        headings->items, (headings->count + 1) * sizeof(*headings->items)
    );

    if (updated == NULL) {
        return -1;
    }
    headings->items = updated;
    headings->items[headings->count] = (MdHeading){
        .level = level,
        .title = duplicate_wide_range(title, length),
        .source_line = source_line,
    };
    if (headings->items[headings->count].title == NULL) {
        return -1;
    }
    headings->count++;
    return 0;
}

int md_headings_build(
    const MdDocument *document,
    MdHeadingList *headings,
    char *error,
    size_t error_size
)
{
    bool in_fence = false;
    size_t source_line;

    *headings = (MdHeadingList){0};
    for (source_line = 0; source_line < document->line_count; ++source_line) {
        const wchar_t *line = document->lines[source_line];
        const wchar_t *stripped = line;
        int level;
        size_t start;
        size_t end;

        while (iswspace(*stripped)) {
            ++stripped;
        }
        if (wcsncmp(stripped, L"```", 3) == 0) {
            in_fence = !in_fence;
            continue;
        }
        if (!in_fence && heading_bounds(line, &level, &start, &end) && level <= 3) {
            if (append_heading(
                    headings, level, line + start, end - start, source_line
                ) != 0) {
                set_error(error, error_size, "out of memory while parsing headings");
                md_headings_free(headings);
                return -1;
            }
        }
    }
    return 0;
}

void md_headings_free(MdHeadingList *headings)
{
    size_t index;

    for (index = 0; index < headings->count; ++index) {
        free(headings->items[index].title);
    }
    free(headings->items);
    *headings = (MdHeadingList){0};
}

int md_display_width(const wchar_t *text)
{
    int width = wcswidth(text, wcslen(text));
    return width < 0 ? (int)wcslen(text) : width;
}

size_t md_clip_length(const wchar_t *text, int maximum_width)
{
    size_t index = 0;
    int width = 0;

    while (text[index] != L'\0') {
        int character_width = wcwidth(text[index]);
        if (character_width < 0) {
            character_width = 1;
        }
        if (width + character_width > maximum_width) {
            break;
        }
        width += character_width;
        ++index;
    }
    return index;
}

static int append_visual_row(
    MdVisualDocument *visual,
    const wchar_t *text,
    const size_t *offsets,
    size_t length,
    size_t source_line,
    MdStyle style
)
{
    MdVisualRow *updated = realloc(
        visual->rows, (visual->count + 1) * sizeof(*visual->rows)
    );
    wchar_t *text_copy;
    size_t *offset_copy = NULL;

    if (updated == NULL) {
        return -1;
    }
    visual->rows = updated;
    text_copy = duplicate_wide_range(text, length);
    if (text_copy == NULL) {
        return -1;
    }
    if (length > 0) {
        offset_copy = malloc(length * sizeof(*offset_copy));
        if (offset_copy == NULL) {
            free(text_copy);
            return -1;
        }
        memcpy(offset_copy, offsets, length * sizeof(*offset_copy));
    }
    visual->rows[visual->count++] = (MdVisualRow){
        .text = text_copy,
        .source_offsets = offset_copy,
        .length = length,
        .source_line = source_line,
        .style = style,
    };
    return 0;
}

static int render_minimal_markdown(
    const wchar_t *source,
    size_t start,
    size_t end,
    bool uppercase,
    MappedText *rendered
)
{
    size_t position = start;
    size_t plain_start = start;
    size_t backticks = 0;

    while (position < end) {
        if (source[position] == L'`') {
            ++backticks;
        }
        if (source[position] == L'[' && backticks % 2 == 0) {
            size_t close_text = position + 1;
            size_t close_url;
            while (close_text < end && source[close_text] != L']') {
                ++close_text;
            }
            if (close_text > position + 1 && close_text + 2 < end
                && source[close_text + 1] == L'(') {
                close_url = close_text + 2;
                while (close_url < end && source[close_url] != L')') {
                    ++close_url;
                }
                if (close_url > close_text + 2 && close_url < end) {
                    if (mapped_append_range(
                            rendered, source, plain_start, position, uppercase
                        ) != 0
                        || mapped_append_range(
                            rendered, source, position + 1, close_text, uppercase
                        ) != 0
                        || mapped_append(rendered, L' ', MD_NO_OFFSET) != 0
                        || mapped_append(rendered, L'(', close_text + 1) != 0
                        || mapped_append_range(
                            rendered,
                            source,
                            close_text + 2,
                            close_url,
                            uppercase
                        ) != 0
                        || mapped_append(rendered, L')', close_url) != 0) {
                        return -1;
                    }
                    position = close_url + 1;
                    plain_start = position;
                    continue;
                }
            }
        }
        ++position;
    }
    return mapped_append_range(rendered, source, plain_start, end, uppercase);
}

static int expanded_mapped(const MappedText *source, MappedText *expanded)
{
    size_t index;
    int column = 0;

    for (index = 0; index < source->length; ++index) {
        if (source->text[index] == L'\t') {
            int spaces = 8 - column % 8;
            int space;
            for (space = 0; space < spaces; ++space) {
                if (mapped_append(expanded, L' ', source->offsets[index]) != 0) {
                    return -1;
                }
            }
            column += spaces;
        } else {
            int character_width = wcwidth(source->text[index]);
            if (mapped_append(
                    expanded, source->text[index], source->offsets[index]
                ) != 0) {
                return -1;
            }
            column += character_width < 0 ? 1 : character_width;
        }
    }
    return 0;
}

static int append_wrapped(
    MdVisualDocument *visual,
    const MappedText *source,
    int width,
    const wchar_t *subsequent_indent,
    size_t source_line,
    MdStyle style
)
{
    MappedText expanded = {0};
    size_t position = 0;
    bool first = true;
    size_t indent_length = wcslen(subsequent_indent);

    if (expanded_mapped(source, &expanded) != 0) {
        return -1;
    }
    if (expanded.length == 0) {
        int result = append_visual_row(
            visual, L"", NULL, 0, source_line, style
        );
        mapped_free(&expanded);
        return result;
    }
    while (position < expanded.length) {
        int indent_width = first ? 0 : md_display_width(subsequent_indent);
        int available = width - indent_width;
        size_t fit = position;
        size_t last_space = MD_NO_OFFSET;
        size_t end;
        size_t next;
        int used = 0;
        MappedText row = {0};

        if (available < 1) {
            available = 1;
        }
        if (!first) {
            while (position < expanded.length
                   && iswspace(expanded.text[position])) {
                ++position;
            }
        }
        fit = position;
        while (fit < expanded.length) {
            if (fit > position && used + 1 > available) {
                break;
            }
            if (iswspace(expanded.text[fit])) {
                last_space = fit;
            }
            ++used;
            ++fit;
            if (used >= available) {
                break;
            }
        }
        if (fit < expanded.length && iswspace(expanded.text[fit])) {
            end = fit;
            next = fit + 1;
        } else if (fit < expanded.length && last_space != MD_NO_OFFSET
            && last_space >= position) {
            size_t word_start = last_space + 1;
            size_t word_end = word_start;

            while (word_end < expanded.length
                   && !iswspace(expanded.text[word_end])) {
                ++word_end;
            }
            if (word_end - word_start > (size_t)available) {
                end = fit;
                next = fit;
            } else {
                end = last_space;
                next = last_space + 1;
            }
        } else {
            end = fit;
            next = fit;
        }
        while (end > position && iswspace(expanded.text[end - 1])) {
            --end;
        }
        if (!first) {
            size_t index;
            for (index = 0; index < indent_length; ++index) {
                if (mapped_append(&row, subsequent_indent[index], MD_NO_OFFSET) != 0) {
                    mapped_free(&row);
                    mapped_free(&expanded);
                    return -1;
                }
            }
        }
        while (position < end) {
            if (mapped_append(
                    &row, expanded.text[position], expanded.offsets[position]
                ) != 0) {
                mapped_free(&row);
                mapped_free(&expanded);
                return -1;
            }
            ++position;
        }
        if (append_visual_row(
                visual,
                row.text == NULL ? L"" : row.text,
                row.offsets,
                row.length,
                source_line,
                style
            ) != 0) {
            mapped_free(&row);
            mapped_free(&expanded);
            return -1;
        }
        mapped_free(&row);
        position = next;
        first = false;
    }
    mapped_free(&expanded);
    return 0;
}

static bool list_bounds(
    const wchar_t *line,
    bool *unordered,
    size_t *indent,
    size_t *marker_start,
    size_t *marker_end,
    size_t *content_start
)
{
    size_t position = 0;
    size_t digits;

    while (position < 8 && line[position] == L' ') {
        ++position;
    }
    if (line[position] == L' ') {
        return false;
    }
    *indent = position;
    *marker_start = position;
    if (line[position] == L'-') {
        *unordered = true;
        ++position;
    } else {
        digits = position;
        while (iswdigit(line[position])) {
            ++position;
        }
        if (position == digits || line[position] != L'.') {
            return false;
        }
        *unordered = false;
        ++position;
    }
    *marker_end = position;
    if (!iswspace(line[position])) {
        return false;
    }
    while (iswspace(line[position])) {
        ++position;
    }
    if (*unordered && line[position] == L'['
        && (line[position + 1] == L' ' || line[position + 1] == L'x'
            || line[position + 1] == L'X')
        && line[position + 2] == L']'
        && (line[position + 3] == L'\0' || iswspace(line[position + 3]))) {
        return false;
    }
    *content_start = position;
    return true;
}

static CellRow table_cells(const wchar_t *line)
{
    CellRow row = {0};
    size_t length = wcslen(line);
    size_t start = 0;
    size_t position;

    if (wcschr(line, L'|') == NULL) {
        return row;
    }
    for (position = 0; position <= length; ++position) {
        if (position == length || line[position] == L'|') {
            Cell *updated = realloc(row.cells, (row.count + 1) * sizeof(*row.cells));
            size_t left = start;
            size_t right = position;
            if (updated == NULL) {
                free(row.cells);
                return (CellRow){0};
            }
            row.cells = updated;
            while (left < right && iswspace(line[left])) {
                ++left;
            }
            while (right > left && iswspace(line[right - 1])) {
                --right;
            }
            row.cells[row.count++] = (Cell){.start = left, .end = right};
            start = position + 1;
        }
    }
    if (line[0] == L'|' || (length > 0 && iswspace(line[0])
        && line[wcsspn(line, L" \t")] == L'|')) {
        memmove(row.cells, row.cells + 1, (--row.count) * sizeof(*row.cells));
    }
    position = length;
    while (position > 0 && iswspace(line[position - 1])) {
        --position;
    }
    if (position > 0 && line[position - 1] == L'|' && row.count > 0) {
        --row.count;
    }
    if (row.count < 2) {
        free(row.cells);
        return (CellRow){0};
    }
    return row;
}

static bool separator_row(const wchar_t *line, const CellRow *row)
{
    size_t column;

    for (column = 0; column < row->count; ++column) {
        size_t position;
        size_t length = row->cells[column].end - row->cells[column].start;
        if (length < 3) {
            return false;
        }
        for (position = row->cells[column].start;
             position < row->cells[column].end;
             ++position) {
            if (line[position] != L'-') {
                return false;
            }
        }
    }
    return true;
}

static int render_table(
    const MdDocument *document,
    size_t start,
    int width,
    MdVisualDocument *visual,
    size_t *next_line
)
{
    CellRow header;
    CellRow separator;
    CellRow *rows = NULL;
    size_t *source_lines = NULL;
    int *column_widths = NULL;
    size_t row_count = 0;
    size_t current;
    size_t column;
    int result = 0;

    if (start + 1 >= document->line_count) {
        return 0;
    }
    header = table_cells(document->lines[start]);
    separator = table_cells(document->lines[start + 1]);
    if (header.count == 0 || separator.count != header.count
        || !separator_row(document->lines[start + 1], &separator)) {
        free(header.cells);
        free(separator.cells);
        return 0;
    }
    free(separator.cells);
    rows = malloc((document->line_count - start) * sizeof(*rows));
    source_lines = malloc((document->line_count - start) * sizeof(*source_lines));
    column_widths = calloc(header.count, sizeof(*column_widths));
    if (rows == NULL || source_lines == NULL || column_widths == NULL) {
        result = -1;
        goto cleanup;
    }
    rows[row_count] = header;
    source_lines[row_count++] = start;
    current = start + 2;
    while (current < document->line_count) {
        CellRow row = table_cells(document->lines[current]);
        if (row.count != header.count) {
            free(row.cells);
            break;
        }
        rows[row_count] = row;
        source_lines[row_count++] = current++;
    }
    for (current = 0; current < row_count; ++current) {
        const wchar_t *line = document->lines[source_lines[current]];
        for (column = 0; column < header.count; ++column) {
            wchar_t *cell = duplicate_wide_range(
                line + rows[current].cells[column].start,
                rows[current].cells[column].end - rows[current].cells[column].start
            );
            int cell_width;
            if (cell == NULL) {
                result = -1;
                goto cleanup;
            }
            cell_width = md_display_width(cell);
            free(cell);
            if (cell_width > column_widths[column]) {
                column_widths[column] = cell_width;
            }
        }
    }
    for (current = 0; current < row_count; ++current) {
        const wchar_t *line = document->lines[source_lines[current]];
        MappedText rendered = {0};
        for (column = 0; column < header.count; ++column) {
            Cell cell = rows[current].cells[column];
            wchar_t *cell_text;
            int padding;
            if (mapped_append_range(
                    &rendered, line, cell.start, cell.end, false
                ) != 0) {
                mapped_free(&rendered);
                result = -1;
                goto cleanup;
            }
            cell_text = duplicate_wide_range(line + cell.start, cell.end - cell.start);
            if (cell_text == NULL) {
                mapped_free(&rendered);
                result = -1;
                goto cleanup;
            }
            padding = column_widths[column] - md_display_width(cell_text);
            free(cell_text);
            if (column + 1 < header.count) {
                while (padding-- > 0) {
                    if (mapped_append(&rendered, L' ', MD_NO_OFFSET) != 0) {
                        mapped_free(&rendered);
                        result = -1;
                        goto cleanup;
                    }
                }
                if (mapped_append(&rendered, L' ', MD_NO_OFFSET) != 0
                    || mapped_append(&rendered, L'|', MD_NO_OFFSET) != 0
                    || mapped_append(&rendered, L' ', MD_NO_OFFSET) != 0) {
                    mapped_free(&rendered);
                    result = -1;
                    goto cleanup;
                }
            }
        }
        {
            size_t clipped = md_clip_length(rendered.text, width);
            if (append_visual_row(
                    visual,
                    rendered.text,
                    rendered.offsets,
                    clipped,
                    source_lines[current],
                    MD_STYLE_TABLE
                ) != 0) {
                mapped_free(&rendered);
                result = -1;
                goto cleanup;
            }
        }
        mapped_free(&rendered);
    }
    *next_line = start + 2 + row_count - 1;
    result = 1;

cleanup:
    if (rows != NULL) {
        for (current = 0; current < row_count; ++current) {
            free(rows[current].cells);
        }
    } else {
        free(header.cells);
    }
    free(rows);
    free(source_lines);
    free(column_widths);
    return result;
}

static int render_regular_line(
    const wchar_t *line,
    size_t source_line,
    int width,
    MdVisualDocument *visual
)
{
    int level;
    size_t title_start;
    size_t title_end;
    bool unordered;
    size_t indent;
    size_t marker_start;
    size_t marker_end;
    size_t content_start;
    MappedText rendered = {0};
    MdStyle style = MD_STYLE_NORMAL;
    wchar_t *indent_text = NULL;
    int result;

    if (heading_bounds(line, &level, &title_start, &title_end)) {
        if (level <= 3) {
            style = level == 1 ? MD_STYLE_HEADING1
                : level == 2 ? MD_STYLE_HEADING2
                             : MD_STYLE_HEADING3;
            result = render_minimal_markdown(
                line, title_start, title_end, level == 1, &rendered
            );
        } else {
            style = MD_STYLE_HEADING;
            result = mapped_append_range(
                &rendered, line, 0, wcslen(line), false
            );
        }
    } else if (list_bounds(
                   line,
                   &unordered,
                   &indent,
                   &marker_start,
                   &marker_end,
                   &content_start
               )) {
        static const wchar_t markers[] = {L'◆', L'▸', L'▪'};
        size_t position;
        size_t level_index = indent / 2 > 2 ? 2 : indent / 2;
        for (position = 0; position < indent; ++position) {
            if (mapped_append(&rendered, L' ', position) != 0) {
                mapped_free(&rendered);
                return -1;
            }
        }
        if (unordered) {
            if (mapped_append(
                    &rendered, markers[level_index], marker_start
                ) != 0) {
                mapped_free(&rendered);
                return -1;
            }
        } else if (mapped_append_range(
                       &rendered, line, marker_start, marker_end, false
                   ) != 0) {
            mapped_free(&rendered);
            return -1;
        }
        if (mapped_append(&rendered, L' ', marker_end) != 0
            || render_minimal_markdown(
                line, content_start, wcslen(line), false, &rendered
            ) != 0) {
            mapped_free(&rendered);
            return -1;
        }
        style = MD_STYLE_LIST;
        indent_text = malloc(
            (indent + (marker_end - marker_start) + 2) * sizeof(*indent_text)
        );
        if (indent_text == NULL) {
            mapped_free(&rendered);
            return -1;
        }
        for (position = 0; position < indent + (marker_end - marker_start) + 1;
             ++position) {
            indent_text[position] = L' ';
        }
        indent_text[position] = L'\0';
        result = 0;
    } else {
        result = render_minimal_markdown(
            line, 0, wcslen(line), false, &rendered
        );
    }
    if (result == 0) {
        result = append_wrapped(
            visual,
            &rendered,
            width,
            indent_text == NULL ? L"" : indent_text,
            source_line,
            style
        );
    }
    free(indent_text);
    mapped_free(&rendered);
    return result;
}

int md_visual_build(
    const MdDocument *document,
    int width,
    MdVisualDocument *visual,
    char *error,
    size_t error_size
)
{
    bool in_fence = false;
    size_t source_line = 0;

    *visual = (MdVisualDocument){0};
    if (width < 1) {
        width = 1;
    }
    while (source_line < document->line_count) {
        const wchar_t *line = document->lines[source_line];
        const wchar_t *stripped = line;
        bool unordered;
        size_t indent;
        size_t marker_start;
        size_t marker_end;
        size_t content_start;
        bool list_line;

        while (iswspace(*stripped)) {
            ++stripped;
        }
        if (wcsncmp(stripped, L"```", 3) == 0) {
            size_t length = wcslen(line);
            size_t clipped = length < (size_t)width ? length : (size_t)width;
            size_t *offsets = malloc(clipped * sizeof(*offsets));
            size_t index;
            if (clipped > 0 && offsets == NULL) {
                goto memory_error;
            }
            for (index = 0; index < clipped; ++index) {
                offsets[index] = index;
            }
            if (append_visual_row(
                    visual, line, offsets, clipped, source_line, MD_STYLE_CODE
                ) != 0) {
                free(offsets);
                goto memory_error;
            }
            free(offsets);
            in_fence = !in_fence;
            ++source_line;
            continue;
        }
        list_line = list_bounds(
            line,
            &unordered,
            &indent,
            &marker_start,
            &marker_end,
            &content_start
        );
        if (in_fence || ((line[0] == L'\t' || wcsncmp(line, L"    ", 4) == 0)
            && !list_line)) {
            size_t length = wcslen(line);
            size_t clipped = length < (size_t)width ? length : (size_t)width;
            size_t *offsets = malloc(clipped * sizeof(*offsets));
            size_t index;
            if (clipped > 0 && offsets == NULL) {
                goto memory_error;
            }
            for (index = 0; index < clipped; ++index) {
                offsets[index] = index;
            }
            if (append_visual_row(
                    visual, line, offsets, clipped, source_line, MD_STYLE_CODE
                ) != 0) {
                free(offsets);
                goto memory_error;
            }
            free(offsets);
            ++source_line;
            continue;
        }
        {
            size_t next_line = source_line;
            int table = render_table(
                document, source_line, width, visual, &next_line
            );
            if (table < 0) {
                goto memory_error;
            }
            if (table > 0) {
                source_line = next_line;
                continue;
            }
        }
        if (render_regular_line(line, source_line, width, visual) != 0) {
            goto memory_error;
        }
        ++source_line;
    }
    if (visual->count == 0
        && append_visual_row(
            visual, L"", NULL, 0, 0, MD_STYLE_NORMAL
        ) != 0) {
        goto memory_error;
    }
    return 0;

memory_error:
    set_error(error, error_size, "out of memory while rendering document");
    md_visual_free(visual);
    return -1;
}

void md_visual_free(MdVisualDocument *visual)
{
    size_t index;

    for (index = 0; index < visual->count; ++index) {
        free(visual->rows[index].text);
        free(visual->rows[index].source_offsets);
    }
    free(visual->rows);
    *visual = (MdVisualDocument){0};
}

size_t md_visual_index_for_source(
    const MdVisualDocument *visual, size_t source_line
)
{
    size_t index;

    for (index = 0; index < visual->count; ++index) {
        if (visual->rows[index].source_line >= source_line) {
            return index;
        }
    }
    return visual->count == 0 ? 0 : visual->count - 1;
}

size_t md_highlight_ranges(
    const MdVisualRow *row,
    const MdSearchMatch *match,
    MdHighlightRange *ranges,
    size_t capacity
)
{
    size_t count = 0;
    size_t start = MD_NO_OFFSET;
    size_t index;

    if (row->source_line != match->source_line) {
        return 0;
    }
    for (index = 0; index < row->length; ++index) {
        size_t offset = row->source_offsets[index];
        bool highlighted = offset != MD_NO_OFFSET
            && offset >= match->source_start && offset < match->source_end;
        if (highlighted && start == MD_NO_OFFSET) {
            start = index;
        } else if (!highlighted && start != MD_NO_OFFSET) {
            if (count < capacity) {
                ranges[count] = (MdHighlightRange){.start = start, .end = index};
            }
            ++count;
            start = MD_NO_OFFSET;
        }
    }
    if (start != MD_NO_OFFSET) {
        if (count < capacity) {
            ranges[count] = (MdHighlightRange){.start = start, .end = row->length};
        }
        ++count;
    }
    return count;
}

size_t md_visual_index_for_match(
    const MdVisualDocument *visual, const MdSearchMatch *match
)
{
    size_t index;

    for (index = 0; index < visual->count; ++index) {
        if (md_highlight_ranges(&visual->rows[index], match, NULL, 0) > 0) {
            return index;
        }
    }
    return md_visual_index_for_source(visual, match->source_line);
}

static size_t casefold_character(wchar_t character, wchar_t folded[3])
{
    if (character == L'ß' || character == L'ẞ') {
        folded[0] = L's';
        folded[1] = L's';
        return 2;
    }
    if (character == L'ς') {
        folded[0] = L'σ';
        return 1;
    }
    folded[0] = towlower(character);
    return 1;
}

static int fold_text(
    const wchar_t *text,
    wchar_t **folded,
    size_t **mapping,
    size_t *folded_length
)
{
    size_t source_length = wcslen(text);
    wchar_t *result = malloc((source_length * 3 + 1) * sizeof(*result));
    size_t *offsets = mapping == NULL
        ? NULL
        : malloc(source_length * 3 * sizeof(*offsets));
    size_t source_index;
    size_t output = 0;

    if (result == NULL || (mapping != NULL && offsets == NULL)) {
        free(result);
        free(offsets);
        return -1;
    }
    for (source_index = 0; source_index < source_length; ++source_index) {
        wchar_t characters[3];
        size_t count = casefold_character(text[source_index], characters);
        size_t index;
        for (index = 0; index < count; ++index) {
            result[output] = characters[index];
            if (offsets != NULL) {
                offsets[output] = source_index;
            }
            ++output;
        }
    }
    result[output] = L'\0';
    *folded = result;
    if (mapping != NULL) {
        *mapping = offsets;
    }
    *folded_length = output;
    return 0;
}

static int append_search_match(
    MdSearchResults *results,
    size_t source_line,
    size_t source_start,
    size_t source_end
)
{
    MdSearchMatch *updated = realloc(
        results->items, (results->count + 1) * sizeof(*results->items)
    );
    if (updated == NULL) {
        return -1;
    }
    results->items = updated;
    results->items[results->count++] = (MdSearchMatch){
        .source_line = source_line,
        .source_start = source_start,
        .source_end = source_end,
    };
    return 0;
}

int md_search_find(
    const MdDocument *document,
    const wchar_t *query,
    MdSearchResults *results,
    char *error,
    size_t error_size
)
{
    wchar_t *folded_query;
    size_t query_length;
    size_t source_line;

    *results = (MdSearchResults){0};
    if (fold_text(query, &folded_query, NULL, &query_length) != 0) {
        goto memory_error;
    }
    if (query_length == 0) {
        free(folded_query);
        return 0;
    }
    for (source_line = 0; source_line < document->line_count; ++source_line) {
        wchar_t *folded_line;
        size_t *mapping;
        size_t line_length;
        size_t position = 0;

        if (fold_text(
                document->lines[source_line],
                &folded_line,
                &mapping,
                &line_length
            ) != 0) {
            free(folded_query);
            goto memory_error;
        }
        while (position + query_length <= line_length) {
            if (wmemcmp(folded_line + position, folded_query, query_length) == 0) {
                if (append_search_match(
                        results,
                        source_line,
                        mapping[position],
                        mapping[position + query_length - 1] + 1
                    ) != 0) {
                    free(folded_line);
                    free(mapping);
                    free(folded_query);
                    goto memory_error;
                }
                position += query_length;
            } else {
                ++position;
            }
        }
        free(folded_line);
        free(mapping);
    }
    free(folded_query);
    return 0;

memory_error:
    set_error(error, error_size, "out of memory while searching document");
    md_search_free(results);
    return -1;
}

void md_search_free(MdSearchResults *results)
{
    free(results->items);
    *results = (MdSearchResults){0};
}
