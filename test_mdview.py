# test_mdview.py — created 2026-08-25, version 0.3.0.
# Purpose: regression tests for repository browsing, Markdown display, navigation, and search.
# Algorithm: load the extensionless application module, feed deterministic
# model data and scripted get_wch input, and assert state transitions.

"""Unit tests for mdview's non-interactive document model."""

from __future__ import annotations

import importlib.machinery
import importlib.util
import subprocess
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


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


class CliTests(unittest.TestCase):
    """Verify release metadata exposed by the Python command line."""

    def test_version(self) -> None:
        process = subprocess.run(
            [str(APP_PATH), "--version"],
            check=True,
            capture_output=True,
            text=True,
        )
        self.assertEqual(process.stdout, "mdview 0.3.0\n")

    def test_local_file_is_optional(self) -> None:
        self.assertIsNone(mdview.parse_args([]).file)
        self.assertEqual(mdview.parse_args(["sample.md"]).file, Path("sample.md"))


class FakeScreen:
    """Provide terminal dimensions for viewer state tests."""

    def __init__(self, height: int = 9, width: int = 80):
        self.height = height
        self.width = width

    def getmaxyx(self) -> tuple[int, int]:
        """Return configured terminal dimensions."""
        return self.height, self.width


class FakeInteractiveScreen(FakeScreen):
    """Drive complete curses input loops without drawing a real terminal."""

    def __init__(self, keys: list[int | str], height: int = 9, width: int = 80):
        super().__init__(height, width)
        self.keys = iter(keys)

    def keypad(self, enabled: bool) -> None:
        """Accept the keypad mode used by both application loops."""

    def get_wch(self) -> int | str:
        """Return the next scripted key through the real dispatch path."""
        return next(self.keys)

    def erase(self) -> None:
        """Ignore screen clearing in state-only integration tests."""

    def addstr(self, *args) -> None:
        """Ignore terminal drawing while retaining the production call path."""

    def refresh(self) -> None:
        """Ignore refreshes in state-only integration tests."""

    def move(self, row: int, column: int) -> None:
        """Accept cursor movement used by the Reader."""


class RepositoryModelTests(unittest.TestCase):
    """Verify repository validation, directory traversal, and URL building."""

    SOURCE = """{
        "format": 1,
        "name": "Test Documentation",
        "items": [
            {
                "type": "directory",
                "name": "hardware",
                "items": [
                    {
                        "type": "document",
                        "name": "mikrotik",
                        "path": "hardware/mikrotik.md"
                    },
                    {
                        "type": "directory",
                        "name": "marine",
                        "items": [
                            {
                                "type": "document",
                                "name": "raymarine",
                                "path": "hardware/marine/raymarine.md"
                            }
                        ]
                    }
                ]
            },
            {
                "type": "directory",
                "name": "linux",
                "items": [
                    {
                        "type": "document",
                        "name": "samba",
                        "path": "linux/samba.md"
                    }
                ]
            }
        ]
    }"""

    def test_parses_valid_repository_and_direct_documents(self) -> None:
        repository = mdview.parse_repository_json(self.SOURCE)
        self.assertEqual(repository.name, "Test Documentation")
        self.assertEqual(
            [directory.name for directory in repository.directories],
            ["hardware", "linux"],
        )
        self.assertEqual(
            [document.name for document in repository.directories[0].documents],
            ["mikrotik"],
        )
        self.assertEqual(
            repository.directories[0].documents[0].path,
            "hardware/mikrotik.md",
        )

    def test_flattens_nested_directory_tree_with_depth(self) -> None:
        repository = mdview.parse_repository_json(self.SOURCE)
        rows = mdview.repository_directories(repository)
        self.assertEqual(
            [(directory.name, depth) for directory, depth in rows],
            [("hardware", 0), ("marine", 1), ("linux", 0)],
        )
        self.assertEqual(
            [document.name for document in rows[1][0].documents], ["raymarine"]
        )

    def test_builds_document_url_relative_to_repository_root(self) -> None:
        self.assertEqual(
            mdview.document_url(
                "http://ricaro.top/mdrepo/", "hardware/mikrotik.md"
            ),
            "http://ricaro.top/mdrepo/hardware/mikrotik.md",
        )

    def test_rejects_invalid_json(self) -> None:
        with self.assertRaisesRegex(mdview.RepositoryError, "invalid repository JSON"):
            mdview.parse_repository_json("{broken")

    def test_rejects_missing_required_fields(self) -> None:
        with self.assertRaisesRegex(mdview.RepositoryError, "valid name"):
            mdview.parse_repository_json('{"format": 1, "items": []}')
        with self.assertRaisesRegex(mdview.RepositoryError, "valid path"):
            mdview.parse_repository_json(
                '{"format": 1, "name": "x", "items": '
                '[{"type": "document", "name": "doc"}]}'
            )

    def test_rejects_unsupported_format_and_unsafe_paths(self) -> None:
        with self.assertRaisesRegex(mdview.RepositoryError, "unsupported"):
            mdview.parse_repository_json(
                '{"format": 2, "name": "x", "items": []}'
            )
        with self.assertRaisesRegex(mdview.RepositoryError, "unsafe path"):
            mdview.document_url("http://example.test/repo/", "../secret.md")

    def test_lowercase_l_opens_selected_document_like_enter(self) -> None:
        repository = mdview.parse_repository_json(self.SOURCE)
        opened: list[str] = []
        for key in ("l", mdview.curses.KEY_ENTER):
            view = mdview.RepositoryView(
                FakeInteractiveScreen([key, "Q"]),
                "http://example.test/repo/",
                repository,
            )
            view.active_panel = "documents"

            def open_document(document):
                opened.append(document.name)
                return "back"

            view._open_document = open_document
            with patch.object(mdview.curses, "curs_set"):
                view.run()
        self.assertEqual(opened, ["mikrotik", "mikrotik"])

        directory_view = mdview.RepositoryView(
            FakeInteractiveScreen(["l", "Q"]),
            "http://example.test/repo/",
            repository,
        )
        directory_view._open_document = open_document
        with patch.object(mdview.curses, "curs_set"):
            directory_view.run()
        self.assertEqual(opened, ["mikrotik", "mikrotik"])


class RepositoryRefreshTests(unittest.TestCase):
    """Verify atomic repository refresh and selection restoration."""

    @staticmethod
    def _repository(
        name: str,
        directories: list[tuple[str, list[tuple[str, str]]]],
    ):
        return mdview.Repository(
            name,
            tuple(
                mdview.RepositoryDirectory(
                    directory_name,
                    (directory_name,),
                    (),
                    tuple(
                        mdview.RepositoryDocument(document_name, document_path)
                        for document_name, document_path in documents
                    ),
                )
                for directory_name, documents in directories
            ),
        )

    def _view(self, repository, keys=None):
        return mdview.RepositoryView(
            FakeInteractiveScreen(keys or ["Q"]),
            "http://example.test/repo/",
            repository,
        )

    def test_successful_r_refresh_replaces_repository(self) -> None:
        original = self._repository("Old", [("docs", [("one", "docs/one.md")])])
        updated = self._repository("New", [("docs", [("one", "docs/one.md")])])
        view = self._view(original, ["r", "Q"])
        with patch.object(mdview, "load_repository", return_value=updated):
            with patch.object(mdview.curses, "curs_set"):
                view.run()
        self.assertIs(view.repository, updated)
        self.assertEqual(view.message, "Repository refreshed")

    def test_new_document_appears_after_refresh(self) -> None:
        original = self._repository("Repo", [("docs", [("one", "docs/one.md")])])
        updated = self._repository(
            "Repo",
            [("docs", [("one", "docs/one.md"), ("two", "docs/two.md")])],
        )
        view = self._view(original)
        with patch.object(mdview, "load_repository", return_value=updated):
            view._refresh_repository()
        self.assertEqual([document.name for document in view._documents()], ["one", "two"])

    def test_removed_selected_document_uses_nearest_index(self) -> None:
        original = self._repository(
            "Repo",
            [
                (
                    "docs",
                    [
                        ("one", "docs/one.md"),
                        ("two", "docs/two.md"),
                        ("three", "docs/three.md"),
                    ],
                )
            ],
        )
        updated = self._repository(
            "Repo",
            [("docs", [("one", "docs/one.md"), ("three", "docs/three.md")])],
        )
        view = self._view(original)
        view.document_selected = 1
        with patch.object(mdview, "load_repository", return_value=updated):
            view._refresh_repository()
        self.assertEqual(view.document_selected, 1)
        self.assertEqual(view._documents()[view.document_selected].name, "three")

    def test_refresh_error_keeps_old_repository_and_selection(self) -> None:
        original = self._repository(
            "Repo", [("docs", [("one", "docs/one.md"), ("two", "docs/two.md")])]
        )
        view = self._view(original)
        view.document_selected = 1
        with patch.object(
            mdview,
            "load_repository",
            side_effect=mdview.RepositoryError("network unavailable"),
        ):
            view._refresh_repository()
        self.assertIs(view.repository, original)
        self.assertEqual(view.document_selected, 1)
        self.assertEqual(view.message, "Refresh failed: network unavailable")

    def test_existing_directory_and_document_selection_are_preserved(self) -> None:
        original = self._repository(
            "Repo",
            [
                ("hardware", [("router", "hardware/router.md")]),
                ("linux", [("samba", "linux/samba.md")]),
            ],
        )
        updated = self._repository(
            "Repo",
            [
                ("archive", [("old", "archive/old.md")]),
                ("hardware", [("router", "hardware/router.md")]),
                (
                    "linux",
                    [("network", "linux/network.md"), ("samba", "linux/samba.md")],
                ),
            ],
        )
        view = self._view(original)
        view.directory_selected = 1
        view.document_selected = 0
        with patch.object(mdview, "load_repository", return_value=updated):
            view._refresh_repository()
        selected_directory = view._selected_directory()
        self.assertEqual(selected_directory.path, ("linux",))
        self.assertEqual(view._documents()[view.document_selected].path, "linux/samba.md")


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
        self.assertEqual(narrow[narrow_target].text, "Нужный")
        self.assertTrue(wide[wide_target].text.startswith("Нужный"))

    def test_code_lines_are_clipped_not_wrapped(self) -> None:
        visual = mdview.build_visual_lines(["```", "очень_длинный_код", "```"], 6)
        self.assertEqual([line.text for line in visual], ["```", "очень_", "```"])
        self.assertTrue(all(line.style == "code" for line in visual))

    def test_h1_is_uppercase_and_bold_without_marker(self) -> None:
        visual = mdview.build_visual_lines(["# Главный heading"], 80)[0]
        self.assertEqual(visual.text, "ГЛАВНЫЙ HEADING")
        self.assertEqual(visual.style, "heading1")
        self.assertEqual(mdview.document_attribute(visual.style), mdview.curses.A_BOLD)
        self.assertEqual(visual.source_offsets[0], 2)

    def test_h2_has_bold_and_underline_without_marker(self) -> None:
        visual = mdview.build_visual_lines(["## Section"], 80)[0]
        self.assertEqual(visual.text, "Section")
        self.assertEqual(visual.style, "heading2")
        self.assertEqual(
            mdview.document_attribute(visual.style),
            mdview.curses.A_BOLD | mdview.curses.A_UNDERLINE,
        )

    def test_h3_has_only_underline_without_marker(self) -> None:
        visual = mdview.build_visual_lines(["### Section"], 80)[0]
        self.assertEqual(visual.text, "Section")
        self.assertEqual(visual.style, "heading3")
        self.assertEqual(
            mdview.document_attribute(visual.style), mdview.curses.A_UNDERLINE
        )

    def test_inline_code_keeps_backticks_without_reverse(self) -> None:
        text = "Use `git status` and `[label](url)` first"
        visual = mdview.build_visual_lines([text], 80)[0]
        self.assertEqual(visual.text, text)
        attribute = mdview.document_attribute(visual.style)
        self.assertEqual(attribute, mdview.curses.A_NORMAL)
        self.assertFalse(attribute & mdview.curses.A_REVERSE)

    def test_link_keeps_url_in_parentheses_without_attributes(self) -> None:
        source = "Visit [OpenAI](https://openai.com) now"
        visual = mdview.build_visual_lines([source], 80)[0]
        self.assertEqual(visual.text, "Visit OpenAI (https://openai.com) now")
        self.assertEqual(mdview.document_attribute(visual.style), mdview.curses.A_NORMAL)
        url_start = visual.text.index("https://")
        self.assertEqual(visual.source_offsets[url_start], source.index("https://"))

    def test_plain_text_is_unchanged(self) -> None:
        text = "Обычный UTF-8 text"
        visual = mdview.build_visual_lines([text], 80)[0]
        self.assertEqual(visual.text, text)
        self.assertEqual(visual.source_offsets, tuple(range(len(text))))

    def test_simple_table_hides_separator_and_outer_borders(self) -> None:
        lines = [
            "| Article type | Maximum character length |",
            "| ------------ | ------------------------ |",
            "| articles | 31 |",
            "| categories | 27 |",
            "| map topics | 30 |",
        ]
        visual = mdview.build_visual_lines(lines, 80)
        self.assertEqual(
            [line.text for line in visual],
            [
                "Article type | Maximum character length",
                "articles     | 31",
                "categories   | 27",
                "map topics   | 30",
            ],
        )
        self.assertTrue(all(line.style == "table" for line in visual))
        self.assertEqual([line.source_line for line in visual], [0, 2, 3, 4])
        self.assertEqual(visual[1].source_offsets[0], lines[2].index("articles"))
        self.assertTrue(
            all(
                mdview.document_attribute(line.style) == mdview.curses.A_NORMAL
                for line in visual
            )
        )

    def test_table_columns_use_longest_cell_width(self) -> None:
        lines = [
            "| Name | Value |",
            "| --- | --- |",
            "| a | longer value |",
            "| longest name | x |",
        ]
        visual = mdview.build_visual_lines(lines, 80)
        self.assertEqual(
            [line.text for line in visual],
            [
                "Name         | Value",
                "a            | longer value",
                "longest name | x",
            ],
        )

    def test_pipe_in_plain_text_does_not_create_table(self) -> None:
        text = "Обычный текст | не таблица"
        visual = mdview.build_visual_lines([text], 80)
        self.assertEqual([line.text for line in visual], [text])
        self.assertEqual(visual[0].style, "normal")

    def test_unordered_list_preserves_three_nesting_levels(self) -> None:
        lines = ["- First", "  - Second", "    - Third"]
        visual = mdview.build_visual_lines(lines, 80)
        self.assertEqual(
            [line.text for line in visual],
            ["◆ First", "  ▸ Second", "    ▪ Third"],
        )
        self.assertTrue(all(line.style == "list" for line in visual))
        self.assertTrue(
            all(
                mdview.document_attribute(line.style) == mdview.curses.A_NORMAL
                for line in visual
            )
        )

    def test_ordered_list_preserves_numbers_and_three_nesting_levels(self) -> None:
        lines = ["1. First", "  2. Second", "    3. Third"]
        visual = mdview.build_visual_lines(lines, 80)
        self.assertEqual([line.text for line in visual], lines)
        self.assertTrue(all(line.style == "list" for line in visual))

    def test_long_list_item_wraps_below_its_text(self) -> None:
        visual = mdview.build_visual_lines(
            ["  - long list item that wraps"], 16
        )
        self.assertEqual(
            [line.text for line in visual],
            ["  ▸ long list", "    item that", "    wraps"],
        )
        self.assertTrue(all(line.style == "list" for line in visual))

    def test_inline_code_in_list_keeps_backticks(self) -> None:
        text = "- Run `git status` now"
        visual = mdview.build_visual_lines([text], 80)
        self.assertEqual([line.text for line in visual], ["◆ Run `git status` now"])
        self.assertEqual(visual[0].style, "list")

    def test_list_like_plain_text_is_unchanged(self) -> None:
        lines = ["-not a list", "1.not a list", "---"]
        visual = mdview.build_visual_lines(lines, 80)
        self.assertEqual([line.text for line in visual], lines)
        self.assertTrue(all(line.style == "normal" for line in visual))


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

    def test_h_activates_contents_without_moving_positions(self) -> None:
        viewer = self._viewer()
        viewer.active_panel = "document"
        viewer.document_top = 3
        viewer.toc_selected = 4
        viewer._handle_key("h")
        self.assertEqual(viewer.active_panel, "toc")
        self.assertEqual(viewer.document_top, 3)
        self.assertEqual(viewer.toc_selected, 4)

        viewer._handle_key("h")
        self.assertEqual(viewer.document_top, 3)
        self.assertEqual(viewer.toc_selected, 4)

    def test_lowercase_h_returns_from_remote_reader_toc_like_escape(self) -> None:
        outcomes: list[str] = []
        for key in ("h", "\x1b"):
            viewer = mdview.Viewer(
                FakeInteractiveScreen([key]),
                Path("remote.md"),
                ["# Remote"],
                return_on_escape=True,
            )
            viewer.active_panel = "toc"
            with patch.object(mdview.curses, "curs_set"):
                outcomes.append(viewer.run())
        self.assertEqual(outcomes, ["back", "back"])

        for return_on_escape, active_panel in ((True, "document"), (False, "toc")):
            viewer = mdview.Viewer(
                FakeInteractiveScreen(["h", "Q"]),
                Path("sample.md"),
                ["# Sample"],
                return_on_escape=return_on_escape,
            )
            viewer.active_panel = active_panel
            with patch.object(mdview.curses, "curs_set"):
                self.assertEqual(viewer.run(), "quit")

    def test_l_activates_document_at_selected_heading(self) -> None:
        viewer = self._viewer()
        viewer.document_top = 3
        viewer.toc_selected = 4
        selected_source = viewer._visible_headings()[4].source_line
        viewer._handle_key("l")
        self.assertEqual(viewer.active_panel, "document")
        self.assertEqual(
            viewer.document_top,
            mdview.visual_index_for_source(viewer.visual_lines, selected_source),
        )
        self.assertEqual(viewer.toc_selected, 4)

        viewer.document_top = 3
        viewer._handle_key("l")
        self.assertEqual(viewer.document_top, 3)
        self.assertEqual(viewer.toc_selected, 4)

    def test_tab_activates_document_at_selected_heading(self) -> None:
        viewer = self._viewer()
        viewer.document_top = 3
        viewer.toc_selected = 5
        selected_source = viewer._visible_headings()[5].source_line
        viewer._handle_key("\t")
        self.assertEqual(viewer.active_panel, "document")
        self.assertEqual(
            viewer.document_top,
            mdview.visual_index_for_source(viewer.visual_lines, selected_source),
        )

        viewer._handle_key("\t")
        self.assertEqual(viewer.active_panel, "toc")
        self.assertEqual(
            viewer.document_top,
            mdview.visual_index_for_source(viewer.visual_lines, selected_source),
        )

    def test_enter_still_jumps_without_activating_document(self) -> None:
        viewer = self._viewer()
        viewer.toc_selected = 3
        selected_source = viewer._visible_headings()[3].source_line
        viewer._handle_key("\n")
        self.assertEqual(viewer.active_panel, "toc")
        self.assertEqual(
            viewer.document_top,
            mdview.visual_index_for_source(viewer.visual_lines, selected_source),
        )

    def test_j_and_k_move_contents_selection(self) -> None:
        viewer = self._viewer()
        viewer.toc_selected = 1
        document_top = viewer.document_top
        viewer._handle_key("j")
        self.assertEqual(viewer.toc_selected, 2)
        self.assertEqual(viewer.document_top, document_top)
        viewer._handle_key("k")
        self.assertEqual(viewer.toc_selected, 1)
        self.assertEqual(viewer.document_top, document_top)

    def test_j_and_k_scroll_document(self) -> None:
        viewer = self._viewer(height=8)
        viewer.active_panel = "document"
        viewer.document_top = 1
        viewer._handle_key("j")
        self.assertEqual(viewer.document_top, 2)
        viewer._handle_key("k")
        self.assertEqual(viewer.document_top, 1)

    def test_j_and_k_keep_toc_synchronized_with_document(self) -> None:
        viewer = self._viewer(height=8)
        viewer.active_panel = "document"
        viewer.document_top = mdview.visual_index_for_source(viewer.visual_lines, 9)
        viewer._sync_toc_to_document()
        self.assertEqual(
            viewer._visible_headings()[viewer.toc_selected].title, "Ребёнок 3"
        )

        viewer._handle_key("j")
        self.assertEqual(
            viewer._visible_headings()[viewer.toc_selected].title, "Ребёнок 4"
        )
        viewer._handle_key("k")
        self.assertEqual(
            viewer._visible_headings()[viewer.toc_selected].title, "Ребёнок 3"
        )


class SearchTests(unittest.TestCase):
    """Verify case-insensitive source search and cyclic match navigation."""

    def _viewer(self, lines: list[str], height: int = 8, width: int = 80):
        viewer = mdview.Viewer(FakeScreen(height, width), Path("search.md"), lines)
        viewer.visual_lines = mdview.build_visual_lines(lines, 20)
        viewer.last_document_width = 20
        return viewer

    def test_search_forward(self) -> None:
        viewer = self._viewer(["alpha one", "middle", "alpha two", "tail"])
        viewer._perform_search("alpha")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_line, 0)
        viewer._repeat_search(1)
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_line, 2)

    def test_search_backward(self) -> None:
        viewer = self._viewer(["alpha one", "middle", "alpha two", "tail"])
        viewer._perform_search("alpha")
        viewer._repeat_search(-1)
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_line, 2)

    def test_search_wraps_in_both_directions(self) -> None:
        matches = mdview.find_text_matches(["one", "two one"], "one")
        self.assertEqual(mdview.stepped_match_index(1, len(matches), 1), 0)
        self.assertEqual(mdview.stepped_match_index(0, len(matches), -1), 1)

    def test_search_is_case_insensitive(self) -> None:
        matches = mdview.find_text_matches(["Alpha", "ALPHA", "other"], "alpha")
        self.assertEqual([match.source_line for match in matches], [0, 1])
        expanded = mdview.find_text_matches(["Straße"], "STRASSE")
        self.assertEqual(
            (expanded[0].source_start, expanded[0].source_end), (0, 6)
        )

    def test_multiple_matches_in_one_source_line_have_exact_ranges(self) -> None:
        matches = mdview.find_text_matches(["net net network"], "net")
        self.assertEqual(
            [(match.source_start, match.source_end) for match in matches],
            [(0, 3), (4, 7), (8, 11)],
        )

    def test_exact_fragment_range_is_highlighted(self) -> None:
        visual_line = mdview.build_visual_lines(["Internet connection"], 40)[0]
        match = mdview.find_text_matches(["Internet connection"], "net")[0]
        self.assertEqual((match.source_start, match.source_end), (5, 8))
        self.assertEqual(mdview.highlight_ranges(visual_line, match), [(5, 8)])

    def test_match_after_wrap_highlights_later_visual_line(self) -> None:
        line = "prefix words target"
        visual = mdview.build_visual_lines([line], 8)
        match = mdview.find_text_matches([line], "target")[0]
        target_index = mdview.visual_index_for_match(visual, match)
        self.assertGreater(target_index, 0)
        self.assertEqual(visual[target_index].text, "target")
        self.assertEqual(
            mdview.highlight_ranges(visual[target_index], match), [(0, 6)]
        )

    def test_match_crossing_wrap_boundary_highlights_both_parts(self) -> None:
        line = "12345 abcde"
        visual = mdview.build_visual_lines([line], 7)
        match = mdview.find_text_matches([line], "45 ab")[0]
        self.assertEqual([item.text for item in visual], ["12345", "abcde"])
        self.assertEqual(mdview.highlight_ranges(visual[0], match), [(3, 5)])
        self.assertEqual(mdview.highlight_ranges(visual[1], match), [(0, 2)])

    def test_search_supports_russian_utf8(self) -> None:
        lines = ["Первый раздел", "РУССКИЙ ТЕКСТ", "конец"]
        matches = mdview.find_text_matches(lines, "русский")
        self.assertEqual([match.source_line for match in matches], [1])
        self.assertEqual((matches[0].source_start, matches[0].source_end), (0, 7))
        visual = mdview.build_visual_lines(lines, 5)
        russian_parts = [
            mdview.highlight_ranges(line, matches[0])
            for line in visual
            if line.source_line == 1
        ]
        self.assertEqual(russian_parts, [[(0, 5)], [(0, 2)], []])

    def test_unicode_display_width_is_not_character_count(self) -> None:
        self.assertEqual(mdview.display_width("Русский"), 7)
        self.assertEqual(mdview.display_width("界"), 2)
        self.assertEqual(mdview.display_width("е\N{COMBINING ACUTE ACCENT}"), 1)

    def test_search_not_found_keeps_document_position(self) -> None:
        viewer = self._viewer(["one", "two", "three", "four", "five"])
        viewer.document_top = 2
        viewer._perform_search("missing")
        self.assertEqual(viewer.document_top, 2)
        self.assertEqual(viewer.search_query, "missing")
        self.assertEqual(viewer.search_matches, [])
        self.assertIsNone(viewer.search_match_index)
        self.assertEqual(viewer.search_message, "Not found: missing")
        viewer._handle_key(".")
        self.assertEqual(viewer.document_top, 2)

    def test_search_jump_uses_source_mapping_after_wrapping(self) -> None:
        lines = [
            "intro",
            "Очень длинная строка, где искомая цель находится после переноса",
            "tail one",
            "tail two",
            "tail three",
        ]
        viewer = self._viewer(lines, height=5)
        viewer.visual_lines = mdview.build_visual_lines(lines, 10)
        viewer._perform_search("цель")
        self.assertEqual(viewer.visual_lines[viewer.document_top].source_line, 1)
        match = viewer.search_matches[viewer.search_match_index]
        self.assertTrue(
            any(
                mdview.highlight_ranges(line, match)
                for line in viewer.visual_lines
                if line.source_line == 1
            )
        )

    def test_resize_keeps_current_search_match_visible(self) -> None:
        lines = [
            "alpha 1234567890 alpha more",
            "tail one",
            "tail two",
            "tail three",
            "tail four",
        ]
        viewer = self._viewer(lines, height=5, width=80)
        viewer._perform_search("alpha")
        viewer._repeat_search(1)
        current_start = viewer.search_matches[viewer.search_match_index].source_start
        viewer.screen.width = 40
        viewer._rebuild_after_resize()
        match = viewer.search_matches[viewer.search_match_index]
        match_visual = mdview.visual_index_for_match(viewer.visual_lines, match)
        self.assertEqual(current_start, 17)
        self.assertEqual(match.source_start, current_start)
        self.assertEqual(viewer.document_top, match_visual)
        ranges = mdview.highlight_ranges(viewer.visual_lines[match_visual], match)
        self.assertEqual(
            [viewer.visual_lines[match_visual].text[start:end] for start, end in ranges],
            ["alpha"],
        )

    def test_dot_and_comma_select_matches_within_same_line(self) -> None:
        viewer = self._viewer(["find find find", "tail one", "tail two"])
        viewer._perform_search("find")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_start, 0)
        viewer._handle_key(".")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_start, 5)
        viewer._handle_key(".")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_start, 10)
        viewer._handle_key(",")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_start, 5)

    def test_dot_and_comma_reuse_saved_query_from_either_panel(self) -> None:
        viewer = self._viewer(["find first", "middle", "find second", "tail"])
        viewer.active_panel = "toc"
        viewer._perform_search("find")
        viewer._handle_key(".")
        self.assertEqual(viewer.search_query, "find")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_line, 2)
        viewer.active_panel = "document"
        viewer._handle_key(",")
        self.assertEqual(viewer.search_matches[viewer.search_match_index].source_line, 0)

    def test_escape_cancels_active_search_input(self) -> None:
        viewer = self._viewer(["find first", "tail"])
        document_top = viewer.document_top
        viewer._handle_key("/")
        viewer._handle_search_input("f")
        viewer._handle_search_input("\x1b")
        self.assertIsNone(viewer.search_input)
        self.assertEqual(viewer.search_query, "")
        self.assertEqual(viewer.document_top, document_top)

    def test_escape_clears_successful_search_without_moving_document(self) -> None:
        viewer = self._viewer(["intro", "find result", "tail one", "tail two"])
        viewer._perform_search("find")
        document_top = viewer.document_top
        viewer._handle_key("\x1b")
        self.assertEqual(viewer.search_query, "")
        self.assertEqual(viewer.search_matches, [])
        self.assertIsNone(viewer.search_match_index)
        self.assertIsNone(viewer.search_message)
        self.assertIsNone(viewer._current_search_match())
        self.assertEqual(viewer.document_top, document_top)

    def test_dot_and_comma_do_nothing_after_search_reset(self) -> None:
        viewer = self._viewer(["find first", "middle", "find second", "tail"])
        viewer._perform_search("find")
        viewer._handle_key("\x1b")
        document_top = viewer.document_top
        viewer._handle_key(".")
        viewer._handle_key(",")
        self.assertEqual(viewer.document_top, document_top)
        self.assertEqual(viewer.search_query, "")

    def test_repeated_escape_is_not_a_quit_command(self) -> None:
        viewer = self._viewer(["content"])
        viewer._handle_key("\x1b")
        viewer._handle_key("\x1b")
        self.assertFalse(mdview.is_quit_key("\x1b"))
        self.assertTrue(mdview.is_quit_key("q"))
        self.assertTrue(mdview.is_quit_key("Q"))
        self.assertEqual(viewer.document_top, 0)


if __name__ == "__main__":
    unittest.main()
