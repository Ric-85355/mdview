<?php
/*
 * mdview.php — created 2026-09-25, version 0.1.0.
 * Purpose: provide the minimal stage-one Web MDView entry point and verification UI.
 * Algorithm: serve static HTML that authenticates through JSON API and exercises repository/reader flows.
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
<header>
    <strong>MDView Web</strong>
    <span id="identity"></span>
    <button id="logout" type="button" hidden>Log out</button>
</header>
<main>
    <section id="login-panel">
        <h1>Sign in</h1>
        <form id="login-form">
            <label>Username <input name="username" autocomplete="username" required></label>
            <label>Password <input name="password" type="password" autocomplete="current-password" required></label>
            <button type="submit">Sign in</button>
        </form>
    </section>
    <section id="repository-panel" hidden>
        <h1>Repositories</h1>
        <nav id="repositories" aria-label="Repositories"></nav>
        <div class="path-row">
            <button id="up" type="button" hidden>..</button>
            <code id="current-path"></code>
        </div>
        <ul id="entries"></ul>
    </section>
    <section id="reader-panel" hidden>
        <button id="back" type="button">Repository</button>
        <h1 id="document-title"></h1>
        <nav id="toc" aria-label="Table of contents"></nav>
        <article id="document-content"></article>
    </section>
    <p id="status" role="status"></p>
</main>
<script src="mdview-server/assets/app.js"></script>
</body>
</html>
