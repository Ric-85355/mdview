# mdview

A lightweight TUI Markdown viewer for Linux terminals with a navigable table of contents and UTF-8 support.

[English](#english) | [Русский](#русский)

---

## English

### Overview

`mdview` is a lightweight read-only Markdown viewer for Linux terminals.

Current release: `0.3.0`.

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

Repeat this copy after updating the repository; `mdview` from `PATH` is a
separate installed file and is not updated when `./mdview` changes.

Then run:

```console
mdview filename.md
```

Run without a file to open Sources:

```console
mdview
```

Sources shows the system `Local` entry followed by every configured repository.
Select `Local` and press `Enter` or `l` to enter an absolute, relative, or
`~`-based Markdown path and open it in the existing Reader. A local document
opened this way returns to Sources; a file passed on the command line retains
the original exit behavior.

For a repository, the right Sources panel shows its top-level directories.
Activate one to enter Repository View, where directories are shown on the left
and their direct documents on the right. Use `Tab` to switch panels, `j`/`k` or
the arrow keys to select an item, `h`/left to move back, `l`/right to move
forward, and `Enter` to activate. In the document list, `l` opens the selected
document as well. `Esc`, or `h`/left while the Reader TOC has focus, returns
from a document to its parent view without losing the current selection. Press
`r` in Repository View to request `repository.json`
again and refresh the directory/document lists without restarting mdview.
Press `/`, type a query, and press `Enter` to search the current repository by
document name or relative path. Results appear in the document panel; `Enter`
or `l` opens the selected result, while `Esc` returns to directory browsing.
This search is case-insensitive and does not inspect Markdown contents.
With a document selected in the right panel, press `d` to save its original
UTF-8 Markdown source locally. The prompt starts with the source filename; edit
it to choose another path. Existing files require an explicit `y` confirmation
and a download never opens another Reader automatically.

### Python Settings

The Python client stores format-1 JSON settings in
`~/.config/mdview/config.json`, or `$XDG_CONFIG_HOME/mdview/config.json` when
`XDG_CONFIG_HOME` is set. On the first Sources launch it creates the
file with the temporary `http://ricaro.top/mdrepo/` test repository. Direct
local-file launches do not read or create this configuration. Every entry in
`repositories` is available in Sources; `id` is its stable internal identifier
and `name` is the displayed alias.

```json
{
  "format": 1,
  "app": {"default_download_dir": "/home/user/Downloads"},
  "repositories": [
    {
      "id": "personal",
      "name": "Personal",
      "url": "https://example.com/mdrepo/",
      "download_dir": null,
      "admin": {"enabled": false}
    }
  ]
}
```

The array may contain multiple repositories, and Sources shows every entry.
A repository `download_dir` overrides the global
`app.default_download_dir`; if neither is set, the save prompt starts with only
the document filename. The local `name` is the user-facing alias, while the
name in `repository.json` remains server metadata. Admin/SFTP fields are parsed
and preserved for the future, but no SFTP connection or Settings UI exists yet.
Passwords, private keys, passphrases, and tokens must not be stored in this file.

### Dynamic Repository Server

The shared-hosting deployment bundle is in `server/mdrepo/`. Its PHP generator
builds format-1 `repository.json` from the actual `.md` files, using
`repository.meta.json` for the repository name and optional description. See
`server/README.md` for Hostinger deployment and verification instructions.

### Controls

* `Tab` — switch panels; when leaving the TOC, jump to its selected heading;
* `h` — activate the table of contents panel;
* `l` — activate the document panel at the selected TOC heading;
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
* Settings UI;
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

### C Version

The C implementation lives alongside the Python implementation. It uses
`ncursesw` and wide-character input/output and builds as `build/mdview-c`.
Both implementations provide the same local Reader behavior. Repository View
is currently available only in the Python implementation.

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

The C port follows the Python local Reader's user-visible behavior: the
two-panel TUI and navigation, search and exact match highlighting, resize
reflow with source mapping, and the same minimal rendering for headings,
links, inline and fenced code, lists, and tables. `make test` includes
curses-independent C tests, CLI checks, model parity checks against Python,
and a UTF-8 pseudo-terminal smoke test.

### Android Version

The independent experimental Android application is in `android/`. It targets
Android 9 (API 28) and newer and uses Kotlin, Jetpack Compose, and standard
Storage Access Framework APIs. It can select a local Markdown document through
the system picker or receive one through **Open with** without broad storage
permission.

The first version renders headings, paragraphs, bullet and numbered lists,
bold, italic, inline code, fenced code blocks, and links. It provides an
separate table of contents area for H1–H3 with a depth badge, context-sensitive TOC
button, double-tap closing, section synchronization, document search, per-URI reading
position restoration, rotation-safe state, and system light/dark themes.

Build prerequisites are JDK 17 and an Android SDK containing platform API 35.
The Gradle wrapper downloads the remaining build dependencies:

```console
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
Links are styled for reading but are not opened in this first version. Images,
tables, editing, and a dedicated tablet layout are not implemented.

The current experimental build permanently reserves a 50 dp bottom banner with
a local advertising placeholder for UX evaluation. No advertising network,
tracking, or network permission has been added.

---

## Русский

### Описание

`mdview` — лёгкий TUI-просмотрщик Markdown-файлов для Linux-терминала.

Текущий релиз: `0.3.0`.

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

После обновления репозитория повторите копирование: команда `mdview` из
`PATH` запускает отдельную установленную копию, которая не обновляется вместе
с `./mdview`.

После этого запуск:

```console
mdview filename.md
```

Запуск без имени файла открывает режим Sources:

```console
mdview
```

Sources показывает системный пункт `Local`, а затем все настроенные
репозитории. `Enter` или `l` на `Local` запрашивает абсолютный, относительный или
начинающийся с `~` путь и открывает файл в том же Reader. Выход из такого документа
возвращает в Sources; прямой запуск `mdview filename.md` сохраняет прежнее поведение.

Для репозитория в правой панели Sources видны только каталоги первого уровня.
Активация каталога открывает Repository View: слева дерево каталогов, справа документы.
`j`/`k` и стрелки выбирают элементы, `h`/← движется назад или влево, `l`/→ — вперёд или
вправо, `Tab` переключает панели, `Enter` активирует. `Esc` либо `h`/← при активном оглавлении
Reader возвращает в родительский режим. Клавиша `r` в Repository View повторно запрашивает
`repository.json` и обновляет каталоги и документы без перезапуска mdview.
Клавиша `/` открывает поиск по имени документа и его относительному
пути в текущем репозитории. `Enter` или `l` открывает выбранный результат,
а `Esc` возвращает к дереву каталогов. Поиск регистронезависимый и не читает
содержимое Markdown-файлов.
При выбранном документе в правой панели `d` сохраняет его исходный UTF-8
Markdown в локальный файл. В поле сразу предложено исходное имя; путь можно
изменить. Замена существующего файла требует явного подтверждения `y`; после
скачивания Reader автоматически не открывается.

### Настройки Python-версии

Конфигурация формата 1 хранится в `~/.config/mdview/config.json` или в
`$XDG_CONFIG_HOME/mdview/config.json`, если задан `XDG_CONFIG_HOME`. При первом
сетевом запуске файл создаётся автоматически с временным тестовым репозиторием
`http://ricaro.top/mdrepo/`. Запуск локального файла конфигурацию не читает и не создаёт.

Формат JSON совпадает с приведённым выше английским примером. Массив `repositories`
поддерживает несколько записей, и все они показываются в Sources. Поле
репозитория `download_dir` имеет приоритет над `app.default_download_dir`; если оба пути
не заданы, диалог предлагает только имя файла. Локальное `name` считается alias,
а серверное имя остаётся метаданными. Admin/SFTP-поля загружаются и сохраняются, но
SFTP и Settings UI ещё не реализованы. Пароли, приватные ключи, passphrase и токены в файле
запрещены.

### Динамический сервер репозитория

Deploy-комплект для shared hosting находится в `server/mdrepo/`. PHP-генератор
формирует `repository.json` формата 1 из фактических `.md`-файлов, а имя и
необязательное описание берёт из `repository.meta.json`. Инструкции загрузки
на Hostinger и проверки находятся в `server/README.md`.

### Управление

* `Tab` — переключение панелей; при выходе из оглавления выполняется переход к выбранному заголовку;
* `h` — активировать панель оглавления;
* `l` — активировать панель документа с переходом к выбранному заголовку оглавления;
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
* Settings UI;
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

### C-версия

Рядом с Python-реализацией размещена реализация на C. Она использует
`ncursesw` и wide-character API и собирается как `build/mdview-c`.
Обе реализации предоставляют одинаковое поведение локального Reader.
Repository View пока доступен только в Python-реализации.

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

C-порт повторяет поведение локального Python Reader: двухпанельный TUI
и навигацию, поиск с точной подсветкой, перестроение после resize с source
mapping и тот же минимальный рендер заголовков, ссылок, inline/fenced code,
списков и таблиц. `make test` запускает автономные C-тесты, проверки CLI,
сравнение модели с Python-эталоном и UTF-8 smoke-тест в псевдотерминале.

### Android-версия

Независимое экспериментальное Android-приложение находится в `android/`.
Оно работает на Android 9 (API 28) и новее, написано на Kotlin и Jetpack
Compose и использует Storage Access Framework. Markdown-файл можно выбрать
через системный picker или передать через «Открыть с помощью» без полного
доступа к файловой системе.

Первая версия отображает заголовки, абзацы, маркированные и нумерованные
списки, bold, italic, inline code, fenced code blocks и ссылки. Есть отдельная
область оглавления H1–H3 с бейджем глубины, контекстной TOC-кнопкой, закрытием
по double tap и синхронизацией, поиск,
восстановление позиции для каждого URI, сохранение состояния при повороте и
системные светлая/тёмная темы.

Для сборки нужны JDK 17 и Android SDK с platform API 35:

```console
cd android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Debug APK создаётся в `android/app/build/outputs/apk/debug/app-debug.apk`.
Ссылки в первой версии стилизуются, но не открываются. Изображения, таблицы,
редактирование и отдельный планшетный интерфейс пока не реализованы.

Текущая экспериментальная сборка постоянно резервирует внизу 50 dp для
локальной рекламной UX-заглушки. Рекламная сеть, tracking и сетевые
разрешения не добавлены.
