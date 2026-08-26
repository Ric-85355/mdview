#!/usr/bin/env python3
# compare_models.py — created 2026-08-26, version 0.3.0.
# Purpose: compare C rendering with the current Python implementation.
# Algorithm: render one fixture through both models and compare every visual
# row's text, style, source line, and source-character offset mapping.

from __future__ import annotations

import runpy
import subprocess
import sys
from pathlib import Path


STYLE_NUMBERS = {
    "normal": 0,
    "heading1": 1,
    "heading2": 2,
    "heading3": 3,
    "heading": 4,
    "list": 5,
    "table": 6,
    "code": 7,
}


def main() -> int:
    """Compare the two source-mapped render models at several widths."""
    if len(sys.argv) != 4:
        raise SystemExit("usage: compare_models.py MDVIEW DUMP FIXTURE")
    mdview_path, dump_path, fixture_path = map(Path, sys.argv[1:])
    module = runpy.run_path(str(mdview_path))
    source_lines = module["read_markdown"](fixture_path)

    for width in (18, 37, 72):
        expected = module["build_visual_lines"](source_lines, width)
        process = subprocess.run(
            [str(dump_path), str(fixture_path), str(width)],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        actual = []
        for raw_line in process.stdout.splitlines():
            source, style, offsets, text = raw_line.split("\t", 3)
            parsed_offsets = tuple(
                None if value == "-" else int(value)
                for value in offsets.split(",")
                if value
            )
            actual.append((text, int(source), int(style), parsed_offsets))
        wanted = [
            (
                line.text,
                line.source_line,
                STYLE_NUMBERS[line.style],
                line.source_offsets,
            )
            for line in expected
        ]
        if actual != wanted:
            for index, (c_row, py_row) in enumerate(zip(actual, wanted)):
                if c_row != py_row:
                    raise AssertionError(
                        f"width {width}, row {index}: C={c_row!r}, Python={py_row!r}"
                    )
            raise AssertionError(
                f"width {width}: C rows={len(actual)}, Python rows={len(wanted)}"
            )
    print("Python/C render parity: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
