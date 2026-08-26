/*
 * dump_mdview_model.c — created 2026-08-26, version 0.3.0.
 * Purpose: expose the C visual model in a deterministic parity-test format.
 * Algorithm: load one UTF-8 document, render it at the requested width, then
 * print source line, style, offsets, and text for comparison with Python.
 */

#include "mdview_model.h"

#include <locale.h>
#include <stdio.h>
#include <stdlib.h>

int main(int argc, char **argv)
{
    MdDocument document;
    MdVisualDocument visual;
    char error[512] = {0};
    char *end = NULL;
    long width;
    size_t row;

    if (argc != 3) {
        (void)fprintf(stderr, "usage: dump-mdview-model FILE WIDTH\n");
        return 2;
    }
    if (setlocale(LC_ALL, "") == NULL) {
        (void)fprintf(stderr, "could not activate locale\n");
        return 1;
    }
    width = strtol(argv[2], &end, 10);
    if (*argv[2] == '\0' || *end != '\0' || width < 1 || width > 100000) {
        (void)fprintf(stderr, "invalid width: %s\n", argv[2]);
        return 2;
    }
    if (md_document_load(argv[1], &document, error, sizeof(error)) != 0) {
        (void)fprintf(stderr, "%s\n", error);
        return 1;
    }
    if (md_visual_build(
            &document, (int)width, &visual, error, sizeof(error)
        ) != 0) {
        (void)fprintf(stderr, "%s\n", error);
        md_document_free(&document);
        return 1;
    }
    for (row = 0; row < visual.count; ++row) {
        size_t index;
        (void)printf(
            "%zu\t%d\t", visual.rows[row].source_line, visual.rows[row].style
        );
        for (index = 0; index < visual.rows[row].length; ++index) {
            if (index > 0) {
                (void)putchar(',');
            }
            if (visual.rows[row].source_offsets[index] == MD_NO_OFFSET) {
                (void)putchar('-');
            } else {
                (void)printf("%zu", visual.rows[row].source_offsets[index]);
            }
        }
        (void)printf("\t%ls\n", visual.rows[row].text);
    }
    md_visual_free(&visual);
    md_document_free(&document);
    return 0;
}
