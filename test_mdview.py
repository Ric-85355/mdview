# test_mdview.py — created 2026-08-25, version 0.1.1.
# Purpose: regression tests for document modeling and navigation state.
# Algorithm: load the extensionless application module, feed deterministic
# Markdown samples to its model and viewer, and assert state transitions.

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


class FakeScreen:
    """Provide terminal dimensions for viewer state tests."""

    def __init__(self, height: int = 9, width: int = 80):
        self.height = height
        self.width = width

    def getmaxyx(self) -> tuple[int, int]:
        """Return configured terminal dimensions."""
        return self.height, self.width


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


class NavigationStateTests(unittest.TestCase):
    """Verify TOC viewport positioning and document-to-TOC synchronization."""

    def _viewer(self, height: int = 9) -> object:
        lines = [
            "# Корень",
            "## Перед 1",
            "## Перед 2",
            "## Перед 3",
            "## Перед 4",
            "## Перед 5",
            "## Родитель",
            "### Ребёнок 1",
            "### Ребёнок 2",
            "### Ребёнок 3",
            "### Ребёнок 4",
            "## Следующий",
            "Текст 1",
            "Текст 2",
            "Текст 3",
            "Текст 4",
            "Текст 5",
        ]
        viewer = mdview.Viewer(FakeScreen(height), Path("sample.md"), lines)
        viewer.visual_lines = mdview.build_visual_lines(lines, 60)
        viewer.last_document_width = 60
        return viewer

    def test_depth_change_keeps_selected_heading(self) -> None:
        viewer = self._viewer()
        viewer.toc_depth = 2
        viewer.toc_selected = 6
        selected_source = viewer._visible_headings()[viewer.toc_selected].source_line
        viewer._change_toc_depth(3)
        selected = viewer._visible_headings()[viewer.toc_selected]
        self.assertEqual(selected.source_line, selected_source)

    def test_level_three_expansion_repositions_toc_viewport(self) -> None:
        viewer = self._viewer()
        viewer.toc_depth = 2
        viewer.toc_selected = 6
        viewer.toc_top = 1
        document_top = viewer.document_top
        viewer._change_toc_depth(3)
        visible_height = viewer.screen.height - 3
        selected_row = viewer.toc_selected - viewer.toc_top
        self.assertEqual(selected_row, visible_height // 3)
        self.assertGreater(len(viewer._visible_headings()) - viewer.toc_selected, 1)
        self.assertEqual(viewer.document_top, document_top)

    def test_document_scroll_selects_current_heading(self) -> None:
        viewer = self._viewer(height=8)
        viewer.toc_depth = 3
        viewer.active_panel = "document"
        viewer.document_top = mdview.visual_index_for_source(viewer.visual_lines, 9)
        viewer._handle_document_key(mdview.curses.KEY_DOWN, page=4, height=8)
        selected = viewer._visible_headings()[viewer.toc_selected]
        self.assertEqual(selected.title, "Ребёнок 4")
        self.assertLessEqual(viewer.toc_top, viewer.toc_selected)
        self.assertLess(viewer.toc_selected, viewer.toc_top + 5)

    def test_reverse_sync_respects_each_toc_depth(self) -> None:
        viewer = self._viewer()
        viewer.document_top = mdview.visual_index_for_source(viewer.visual_lines, 8)
        expected = {1: "Корень", 2: "Родитель", 3: "Ребёнок 2"}
        for depth, title in expected.items():
            with self.subTest(depth=depth):
                viewer.toc_depth = depth
                viewer._sync_toc_to_document()
                selected = viewer._visible_headings()[viewer.toc_selected]
                self.assertEqual(selected.title, title)


if __name__ == "__main__":
    unittest.main()
