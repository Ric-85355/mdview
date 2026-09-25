<?php
/*
 * mdview.php — created 2026-09-25, version 0.2.0.
 * Purpose: provide the Web MDView entry point and stage-two Repository interface.
 * Algorithm: serve an accessible application shell whose vanilla-JS client authenticates,
 * navigates repositories, searches names, and opens the existing technical Reader.
 */

declare(strict_types=1);
?><!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>MDView Web</title>
    <link rel="stylesheet" href="mdview-server/assets/app.css">
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
                    <button type="button" role="menuitem" disabled>New folder</button>
                    <button type="button" role="menuitem" disabled>Upload</button>
                </div>
            </details>
        </div>

        <nav id="breadcrumbs" class="breadcrumbs" aria-label="Current path"></nav>
        <div id="repository-heading" class="list-heading" aria-live="polite"></div>
        <div id="entry-list-region" class="entry-list-region" tabindex="-1">
            <ul id="entries" class="entries"></ul>
            <div id="empty-state" class="empty-state" hidden></div>
        </div>
    </section>

    <section id="reader-panel" class="reader-panel" hidden>
        <div class="reader-toolbar">
            <button id="back" type="button">&#8592; Repository</button>
        </div>
        <h1 id="document-title"></h1>
        <nav id="toc" aria-label="Table of contents"></nav>
        <article id="document-content"></article>
    </section>

    <p id="status" role="status" aria-live="polite"></p>
</main>
<script src="mdview-server/assets/repository-state.js"></script>
<script src="mdview-server/assets/app.js"></script>
</body>
</html>
