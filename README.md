<!-- README.md — created 2026-08-25, version 0.1.0. -->
<!-- Purpose: describe installation, launch, controls, and first-version limits. -->
<!-- Algorithm: not applicable; this file documents the executable workflow. -->

# mdview

`mdview` — лёгкий TUI-просмотрщик Markdown-файлов для Linux-терминала. Он работает
только для чтения, использует Python 3 и стандартный модуль `curses`.

## Запуск

Сделайте файл исполняемым один раз и передайте путь к UTF-8 Markdown-файлу:

```console
chmod +x mdview
./mdview README.md
```

Установка пакетов через `pip` не требуется.

## Управление

- `Tab` — переключить активную панель;
- `↑`, `↓`, `PgUp`, `PgDn`, `Home`, `End` — навигация в активной панели;
- `Enter` — перейти из оглавления к выбранному заголовку;
- `1`, `2`, `3` — выбрать максимальный уровень оглавления;
- `Q` или `Esc` — выход.

## Ограничения первой версии

Поддерживается упрощённое отображение заголовков, абзацев, списков и кода без
полноценного Markdown-рендеринга. Длинные строки блоков кода обрезаются по ширине
панели. Поиск, ссылки, изображения, мышь, темы, конфигурация и редактирование не
поддерживаются.

## Проверка

```console
python3 -m unittest -v
python3 -m py_compile mdview test_mdview.py
```
