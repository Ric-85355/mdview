# test_mdview.py — created 2026-08-25, version 0.1.0.
# Purpose: regression tests for parsing and visual/source line mapping.
# Algorithm: load the extensionless application module, feed deterministic
# Markdown samples to pure functions, and assert their public results.

"""Unit tests for mdview's non-interactive document model."""

from __future__ import annotations

import importlib.machinery
import importlib.util
import sys
import unittest
from pathlib import Path


APP_PATH = Path(__file__).with_name("mdview")


def _load_application():
    loader = importlib.machinery.SourceFileLoader("mdview", str(APP_PATH))
    specification = importlib.util.spec_from_loader(loader.name, loader)
    if specification is None:
        raise RuntimeError("Не удалось создать спецификацию модуля mdview")
    module = importlib.util.module_from_spec(specification)
    sys.modules[loader.name] = module
    loader.exec_module(module)
    return module


mdview = _load_application()


class MarkdownModelTests(unittest.TestCase):
    """Verify parsing, wrapping, UTF-8 content, and heading destinations."""

    def test_headings_include_only_levels_one_to_three(self) -> None:
        lines = ["# Один", "## Два", "### Три", "#### Четыре"]
        headings = mdview.parse_headings(lines)
        self.assertEqual(
            [(item.level, item.title) for item in headings],
            [(1, "Один"), (2, "Два"), (3, "Три")],
        )

    def test_fenced_code_headings_are_not_in_toc(self) -> None:
        lines = ["# Раздел", "```md", "## Не заголовок", "```", "## Итог"]
        self.assertEqual(
            [item.title for item in mdview.parse_headings(lines)], ["Раздел", "Итог"]
        )

    def test_wrapping_maps_every_visual_line_to_source(self) -> None:
        lines = ["# Заголовок", "Очень длинная строка на русском языке"]
        visual = mdview.build_visual_lines(lines, 12)
        self.assertEqual(visual[0].source_line, 0)
        self.assertGreater(sum(item.source_line == 1 for item in visual), 1)
        destination = mdview.visual_index_for_source(visual, 1)
        self.assertEqual(visual[destination].source_line, 1)

    def test_resize_rebuild_keeps_source_destination(self) -> None:
        lines = ["Первый длинный абзац с переносом", "## Нужный раздел", "Текст"]
        narrow = mdview.build_visual_lines(lines, 8)
        wide = mdview.build_visual_lines(lines, 30)
        narrow_target = mdview.visual_index_for_source(narrow, 1)
        wide_target = mdview.visual_index_for_source(wide, 1)
        self.assertEqual(narrow[narrow_target].text, "##")
        self.assertTrue(wide[wide_target].text.startswith("## Нужный"))

    def test_code_lines_are_clipped_not_wrapped(self) -> None:
        visual = mdview.build_visual_lines(["```", "очень_длинный_код", "```"], 6)
        self.assertEqual([line.text for line in visual], ["```", "очень_", "```"])
        self.assertTrue(all(line.style == "code" for line in visual))


if __name__ == "__main__":
    unittest.main()
