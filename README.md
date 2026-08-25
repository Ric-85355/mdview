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
* basic visual distinction for headings, lists and code;
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

The current version provides simplified display of:

* headings;
* paragraphs;
* unordered lists;
* ordered lists;
* code lines;
* fenced code blocks.

Long lines inside fenced code blocks are clipped to the panel width.

Some Markdown syntax may remain visible.

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
* базовое визуальное различение заголовков, списков и кода;
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

Текущая версия упрощённо отображает:

* заголовки;
* абзацы;
* маркированные списки;
* нумерованные списки;
* строки кода;
* fenced code blocks.

Длинные строки внутри fenced code blocks обрезаются по ширине панели.

Часть Markdown-разметки может оставаться видимой.

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
