/*
 * app.js — created 2026-09-25, version 0.1.0.
 * Purpose: exercise Auth, Repository, and Reader JSON APIs from a minimal vanilla-JS frontend.
 * Algorithm: retain only in-memory navigation, render text with DOM APIs, and insert only server-sanitized HTML.
 */

'use strict';

const apiUrl = 'mdview-server/api.php';
let csrfToken = '';
let currentRepository = '';
let currentPath = '';

const loginPanel = document.querySelector('#login-panel');
const repositoryPanel = document.querySelector('#repository-panel');
const readerPanel = document.querySelector('#reader-panel');
const statusNode = document.querySelector('#status');

async function api(action, options = {}) {
    const query = new URLSearchParams({action, ...(options.query || {})});
    const response = await fetch(`${apiUrl}?${query}`, {
        method: options.method || 'GET',
        credentials: 'same-origin',
        headers: options.body ? {'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken} : {},
        body: options.body ? JSON.stringify(options.body) : undefined,
    });
    const payload = await response.json();
    if (!response.ok || !payload.success) {
        throw new Error(payload.error?.message || `HTTP ${response.status}`);
    }
    return payload.data;
}

function showError(failure) {
    statusNode.textContent = failure instanceof Error ? failure.message : String(failure);
    statusNode.className = 'error';
}

function clearStatus() {
    statusNode.textContent = '';
    statusNode.className = '';
}

async function initialize() {
    csrfToken = (await api('csrf')).csrf_token;
    try {
        const session = await api('session');
        csrfToken = session.csrf_token;
        await showRepositories(session.user);
    } catch (_) {
        loginPanel.hidden = false;
    }
}

async function showRepositories(user) {
    loginPanel.hidden = true;
    readerPanel.hidden = true;
    repositoryPanel.hidden = false;
    document.querySelector('#identity').textContent = `${user.username} (${user.role})`;
    document.querySelector('#logout').hidden = false;
    const data = await api('repositories');
    const container = document.querySelector('#repositories');
    container.replaceChildren();
    for (const repository of data.repositories) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = repository.name;
        button.addEventListener('click', () => openDirectory(repository.id, '').catch(showError));
        container.append(button);
    }
}

async function openDirectory(repository, path) {
    clearStatus();
    const data = await api('directory', {query: {repository, path}});
    currentRepository = repository;
    currentPath = data.path;
    document.querySelector('#current-path').textContent = `${repository}/${data.path}`;
    document.querySelector('#up').hidden = data.path === '';
    const list = document.querySelector('#entries');
    list.replaceChildren();
    for (const entry of data.entries) {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = `${entry.type === 'directory' ? '[DIR]' : '[FILE]'} ${entry.name}`;
        if (entry.type === 'directory') {
            button.addEventListener('click', () => openDirectory(repository, entry.path).catch(showError));
        } else if (entry.readable) {
            button.addEventListener('click', () => openDocument(repository, entry.path).catch(showError));
        } else {
            button.disabled = true;
        }
        item.append(button);
        list.append(item);
    }
}

async function openDocument(repository, path) {
    clearStatus();
    const {document: view} = await api('document', {query: {repository, path}});
    repositoryPanel.hidden = true;
    readerPanel.hidden = false;
    document.querySelector('#document-title').textContent = view.title;
    const toc = document.querySelector('#toc');
    toc.replaceChildren();
    for (const heading of view.toc) {
        const link = document.createElement('a');
        link.href = `#${encodeURIComponent(heading.target)}`;
        link.textContent = heading.title;
        link.style.setProperty('--level', heading.level);
        toc.append(link);
    }
    // content is produced by Parsedown safe mode and annotated server-side.
    document.querySelector('#document-content').innerHTML = view.content;
}

document.querySelector('#login-form').addEventListener('submit', async event => {
    event.preventDefault();
    clearStatus();
    const values = new FormData(event.currentTarget);
    try {
        const data = await api('login', {
            method: 'POST',
            body: {username: values.get('username'), password: values.get('password')},
        });
        csrfToken = data.csrf_token;
        event.currentTarget.reset();
        await showRepositories(data.user);
    } catch (failure) {
        showError(failure);
    }
});

document.querySelector('#logout').addEventListener('click', async () => {
    try {
        const data = await api('logout', {method: 'POST', body: {}});
        csrfToken = data.csrf_token;
        repositoryPanel.hidden = true;
        readerPanel.hidden = true;
        loginPanel.hidden = false;
        document.querySelector('#identity').textContent = '';
        document.querySelector('#logout').hidden = true;
    } catch (failure) {
        showError(failure);
    }
});

document.querySelector('#up').addEventListener('click', () => {
    const parent = currentPath.includes('/') ? currentPath.slice(0, currentPath.lastIndexOf('/')) : '';
    openDirectory(currentRepository, parent).catch(showError);
});
document.querySelector('#back').addEventListener('click', () => {
    readerPanel.hidden = true;
    repositoryPanel.hidden = false;
});

initialize().catch(showError);
