# mdview

A lightweight TUI Markdown viewer for Linux terminals with a navigable table of contents and UTF-8 support.

[English](#english) | [Русский](#русский)

---

## English

### Overview

`mdview` is a lightweight read-only Markdown viewer for Linux terminals.

It uses Python 3 and the standard `curses` module and does not require any external Python packages.

The interface consists of two panels:

* table of contents on the left;
* Markdown document on the right.

The table of contents is built automatically from Markdown headings and supports levels 1–3.

### Features

* two-panel TUI interface;
* automatic table of contents;
* heading levels 1, 2 and 3;
* quick switching between TOC depths;
* navigation from the TOC to document sections;
* automatic TOC synchronization while scrolling the document;
* case-insensitive text search with cyclic navigation and exact highlighting;
* UTF-8 support, including Cyrillic text;
* automatic line wrapping;
* terminal resize handling;
* minimal heading and link presentation;
* fenced code block support;
* read-only operation;
* no external Python dependencies.

### Requirements

* Linux;
* Python 3;
* terminal with `curses` support;
* UTF-8 locale.

### Running

Make the file executable once:

```console
chmod +x mdview
```

Then open a Markdown file:

```console
./mdview README.md
```

You can also install it somewhere in your `PATH`, for example:

```console
sudo cp mdview /usr/local/bin/mdview
```

Then run:

```console
mdview filename.md
```

### Controls

* `Tab` — switch between the table of contents and document panels;
* `h` — activate the table of contents panel;
* `l` — activate the document panel;
* `j`, `k` — move down or up in the active panel;
* `↑`, `↓` — move up or down;
* `PgUp`, `PgDn` — move one page;
* `Home`, `End` — jump to the beginning or end;
* `Enter` — jump to the selected heading, or submit an active search;
* `1` — show level 1 headings only;
* `2` — show levels 1 and 2;
* `3` — show levels 1, 2 and 3;
* `/` — enter a search query;
* `.` — jump to the next match;
* `,` — jump to the previous match;
* `Q`, `q` — exit;
* `Esc` — cancel search input or clear the completed search and its highlight.

### Markdown Support

`mdview` is not a full Markdown renderer.

The current version provides minimal display of:

* level 1 headings in uppercase and bold without the `#` marker;
* level 2 headings in bold and underline without the `##` marker;
* level 3 headings in underline without the `###` marker;
* links as `text (URL)` without decorative attributes;
* simple tables with space-aligned columns and no separator row or outer borders;
* unordered `-` lists and numbered `1.` lists with up to three nesting levels;
* paragraphs;
* code lines;
* fenced code blocks.

Inline code keeps its backticks and has no special styling. Bold and italic
Markdown markers remain visible and have no special styling.

Long lines inside fenced code blocks are clipped to the panel width.

Some Markdown syntax may remain visible.

Table alignment markers and complex Markdown inside table cells are not supported.
Task lists, mixed lists, and complex nested list structures are not supported.

### Current Limitations

The following features are not currently supported:

* editing;
* clickable links;
* images;
* mouse input;
* themes;
* configuration files;
* plugins;
* multiple open documents;
* full Markdown rendering.

### Testing

Run the unit tests:

```console
python3 -m unittest -v
```

Check syntax:

```console
python3 -m py_compile mdview test_mdview.py
```

### Experimental C Version

An experimental C port lives alongside the Python implementation.
It uses `ncursesw` and wide-character input/output, and builds as
`build/mdview-c`. The Python `mdview` remains the reference implementation.

Install a C compiler, `pkg-config`, and the ncurses wide-character development
package (for example, `libncursesw5-dev` on Debian/Ubuntu), then build and test:

```console
make
make test
```

Run the C version with a UTF-8 Markdown file:

```console
./build/mdview-c README.md
```

The C port now follows the Python version's user-visible behavior: the
two-panel TUI and navigation, search and exact match highlighting, resize
reflow with source mapping, and the same minimal rendering for headings,
links, inline and fenced code, lists, and tables. `make test` includes
curses-independent C tests, CLI checks, model parity checks against Python,
and a UTF-8 pseudo-terminal smoke test.

---

## Русский

### Описание

`mdview` — лёгкий TUI-просмотрщик Markdown-файлов для Linux-терминала.

Программа работает только в режиме чтения, использует Python 3 и стандартный модуль `curses` и не требует установки внешних Python-пакетов.

Интерфейс состоит из двух панелей:

* слева — оглавление;
* справа — Markdown-документ.

Оглавление строится автоматически по заголовкам Markdown и поддерживает уровни 1–3.

### Возможности

* двухпанельный TUI-интерфейс;
* автоматическое оглавление;
* заголовки уровней 1, 2 и 3;
* быстрое переключение глубины оглавления;
* переход из оглавления к разделам документа;
* автоматическая синхронизация оглавления при прокрутке документа;
* поиск без учёта регистра с циклической навигацией и точной подсветкой;
* поддержка UTF-8, включая русский текст;
* автоматический перенос строк;
* корректная работа при изменении размера терминала;
* минимальное отображение заголовков и ссылок;
* поддержка fenced code blocks;
* работа только в режиме чтения;
* отсутствие внешних Python-зависимостей.

### Требования

* Linux;
* Python 3;
* терминал с поддержкой `curses`;
* UTF-8 locale.

### Запуск

Один раз сделайте файл исполняемым:

```console
chmod +x mdview
```

После этого откройте Markdown-файл:

```console
./mdview README.md
```

При желании программу можно установить в каталог из `PATH`, например:

```console
sudo cp mdview /usr/local/bin/mdview
```

После этого запуск:

```console
mdview filename.md
```

### Управление

* `Tab` — переключение между оглавлением и документом;
* `h` — активировать панель оглавления;
* `l` — активировать панель документа;
* `j`, `k` — перемещение вниз или вверх в активной панели;
* `↑`, `↓` — перемещение вверх и вниз;
* `PgUp`, `PgDn` — перемещение на страницу;
* `Home`, `End` — начало или конец;
* `Enter` — переход к выбранному заголовку или запуск введённого поиска;
* `1` — показывать только заголовки первого уровня;
* `2` — показывать заголовки первого и второго уровней;
* `3` — показывать заголовки первого, второго и третьего уровней;
* `/` — ввести поисковый запрос;
* `.` — перейти к следующему совпадению;
* `,` — перейти к предыдущему совпадению;
* `Q`, `q` — выход;
* `Esc` — отменить ввод поиска или очистить завершённый поиск и его подсветку.

### Поддержка Markdown

`mdview` не является полноценным Markdown-рендерером.

Текущая версия минимально отображает:

* заголовки первого уровня в uppercase с bold без маркера `#`;
* заголовки второго уровня с bold и underline без маркера `##`;
* заголовки третьего уровня с underline без маркера `###`;
* ссылки в виде `текст (URL)` без декоративных атрибутов;
* простые таблицы с выравниванием колонок пробелами, без строки-разделителя и внешних границ;
* маркированные списки с `-` и нумерованные списки с `1.` до трёх уровней вложенности;
* абзацы;
* строки кода;
* fenced code blocks.

Inline code сохраняет обратные кавычки и не имеет отдельного стиля. Маркеры
bold и italic остаются видимыми и также не имеют отдельного стиля.

Длинные строки внутри fenced code blocks обрезаются по ширине панели.

Часть Markdown-разметки может оставаться видимой.

Маркеры выравнивания таблиц и сложная Markdown-разметка внутри ячеек не поддерживаются.
Task lists, смешанные списки и сложные вложенные конструкции не поддерживаются.

### Текущие ограничения

Пока не поддерживаются:

* редактирование;
* переход по ссылкам;
* изображения;
* мышь;
* темы;
* конфигурационные файлы;
* плагины;
* несколько открытых документов;
* полноценный Markdown-рендеринг.

### Проверка

Запуск модульных тестов:

```console
python3 -m unittest -v
```

Проверка синтаксиса:

```console
python3 -m py_compile mdview test_mdview.py
```

### Экспериментальная C-версия

Рядом с Python-реализацией размещён экспериментальный порт на C. Он
использует `ncursesw` и wide-character API и собирается как
`build/mdview-c`. Python-версия `mdview` остаётся эталонной.

Для сборки нужны C-компилятор, `pkg-config` и development-пакет wide-character
ncurses (например, `libncursesw5-dev` в Debian/Ubuntu):

```console
make
make test
```

Запуск C-версии с UTF-8 Markdown-файлом:

```console
./build/mdview-c README.md
```

C-порт повторяет пользовательское поведение Python-версии: двухпанельный TUI
и навигацию, поиск с точной подсветкой, перестроение после resize с source
mapping и тот же минимальный рендер заголовков, ссылок, inline/fenced code,
списков и таблиц. `make test` запускает автономные C-тесты, проверки CLI,
сравнение модели с Python-эталоном и UTF-8 smoke-тест в псевдотерминале.
