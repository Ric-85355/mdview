<?php
/*
 * mdview.php — created 2026-09-25, version 0.6.0.
 * Purpose: provide Web MDView Repository, admin create/upload, and reading UI.
 * Algorithm: serve an accessible application shell whose vanilla-JS client authenticates,
 * navigates repositories and displays renderer-independent DocumentView responses.
 */

declare(strict_types=1);

const MDVIEW_WEB_VERSION = '0.6.0';
?><!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>MDView Web</title>
    <link rel="stylesheet" href="mdview-server/assets/app.css?v=<?= MDVIEW_WEB_VERSION ?>">
</head>
<body>
<header class="app-header">
    <strong>MDView</strong>
    <span id="identity"></span>
    <button id="logout" class="quiet-button" type="button" hidden>Log out</button>
</header>
<main>
    <section id="login-panel" class="login-panel">
        <h1>Sign in</h1>
        <form id="login-form">
            <label>Username <input name="username" autocomplete="username" required></label>
            <label>Password <input name="password" type="password" autocomplete="current-password" required></label>
            <button type="submit">Sign in</button>
        </form>
    </section>

    <section id="repository-panel" class="repository-panel" hidden aria-label="Repository browser">
        <div class="repository-toolbar">
            <label class="repository-picker">
                <span class="visually-hidden">Current repository</span>
                <select id="repository-select" aria-label="Current repository"></select>
            </label>
            <form id="repository-search" class="repository-search" role="search">
                <label class="visually-hidden" for="search-query">Search this repository</label>
                <input id="search-query" name="query" type="search" placeholder="Search names" autocomplete="off">
                <button type="submit">Search</button>
                <button id="clear-search" class="quiet-button" type="button" hidden>Clear</button>
            </form>
            <details id="repository-menu" class="menu">
                <summary aria-label="Repository menu">&#8942;</summary>
                <div class="menu-popover" role="menu">
                    <button id="new-folder-action" type="button" role="menuitem" disabled>New folder</button>
                    <button id="upload-action" type="button" role="menuitem" disabled>Upload</button>
                </div>
            </details>
            <input id="upload-input" type="file" hidden>
        </div>

        <nav id="breadcrumbs" class="breadcrumbs" aria-label="Current path"></nav>
        <div id="repository-heading" class="list-heading" aria-live="polite"></div>
        <div id="entry-list-region" class="entry-list-region" tabindex="-1">
            <ul id="entries" class="entries"></ul>
            <div id="empty-state" class="empty-state" hidden></div>
        </div>
    </section>

    <dialog id="new-folder-dialog" class="repository-dialog" aria-labelledby="new-folder-title">
        <form id="new-folder-form">
            <h2 id="new-folder-title">New folder</h2>
            <label for="new-folder-name">Folder name</label>
            <input id="new-folder-name" name="name" autocomplete="off" required>
            <p id="new-folder-error" class="dialog-error" role="alert"></p>
            <div class="dialog-actions">
                <button id="new-folder-cancel" class="quiet-button" type="button">Cancel</button>
                <button id="new-folder-submit" type="submit">Create</button>
            </div>
        </form>
    </dialog>

    <section id="reader-panel" class="reader-panel" hidden aria-label="Document reader">
        <div class="reader-toolbar" aria-label="Reader controls">
            <button id="reader-toc" class="reader-toolbar-start" type="button" disabled
                    aria-expanded="false" aria-controls="toc-drawer">
                Contents
            </button>
            <button id="reader-search" class="reader-toolbar-center" type="button" disabled
                    aria-expanded="false" aria-controls="reader-search-panel">
                Search
            </button>
            <button id="back" class="reader-toolbar-end" type="button">Repository</button>
        </div>
        <div id="reader-search-panel" class="reader-search-panel" role="search" hidden>
            <label class="reader-search-query">
                <span class="visually-hidden">Search this document</span>
                <input id="reader-search-query" type="search" placeholder="Search document"
                       autocomplete="off" enterkeyhint="search">
            </label>
            <output id="reader-search-count" class="reader-search-count" aria-live="polite">0 / 0</output>
            <button id="reader-search-previous" type="button" disabled>Previous</button>
            <button id="reader-search-next" type="button" disabled>Next</button>
            <button id="reader-search-clear" class="quiet-button" type="button" disabled>Clear</button>
            <button id="reader-search-close" class="quiet-button" type="button">Close</button>
        </div>
        <div id="toc-overlay" class="toc-overlay" hidden>
            <aside id="toc-drawer" class="toc-drawer" role="dialog" aria-labelledby="toc-title">
                <header class="toc-header">
                    <h2 id="toc-title">Contents</h2>
                    <button id="toc-close" class="toc-close" type="button" aria-label="Close contents">&times;</button>
                </header>
                <div class="toc-depth" aria-label="Maximum heading depth">
                    <span>Depth</span>
                    <div class="toc-depth-buttons">
                        <button type="button" data-toc-depth="1" aria-pressed="false">1</button>
                        <button type="button" data-toc-depth="2" aria-pressed="false">2</button>
                        <button type="button" data-toc-depth="3" aria-pressed="true">3</button>
                    </div>
                </div>
                <nav id="toc-items" class="toc-items" aria-label="Document contents"></nav>
            </aside>
        </div>
        <div id="reader-loading" class="reader-message" role="status" hidden>
            <span class="loading-indicator" aria-hidden="true"></span>
            <span>Opening document…</span>
        </div>
        <div id="reader-error" class="reader-message reader-error" role="alert" hidden>
            <strong>Could not open document</strong>
            <span id="reader-error-message"></span>
        </div>
        <div id="reader-document" class="reader-document" hidden>
            <header class="document-header">
                <h1 id="document-title"></h1>
            </header>
            <article id="document-content" class="document-content"></article>
        </div>
    </section>

    <p id="status" role="status" aria-live="polite"></p>
</main>
<script src="mdview-server/assets/repository-state.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
<script src="mdview-server/assets/reader-state.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
<script src="mdview-server/assets/login-form.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
<script src="mdview-server/assets/document-search.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
<script src="mdview-server/assets/reading-position.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
<script src="mdview-server/assets/repository-mutations.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
<script src="mdview-server/assets/app.js?v=<?= MDVIEW_WEB_VERSION ?>"></script>
</body>
</html>
