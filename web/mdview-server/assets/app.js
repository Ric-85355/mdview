/*
 * app.js — created 2026-09-25, version 0.5.0.
 * Purpose: provide Auth, Repository, Reader, TOC, search, and reading-position UI.
 * Algorithm: render explicit modes, decorate visible text nodes with transient search marks,
 * and persist debounced per-document scroll offsets without changing DocumentView data.
 */

'use strict';

const apiUrl = 'mdview-server/api.php';
const stateTools = window.MdviewRepositoryState;
const readerTools = window.MdviewReaderState;
const loginForm = window.MdviewLoginForm;
const documentSearchTools = window.MdviewDocumentSearch;
const readingPositionTools = window.MdviewReadingPosition;
let csrfToken = '';
let repositories = [];
let currentRepository = '';
let currentPath = '';
let currentScrollTop = 0;
let returnScrollTop = 0;
let searchActive = false;
let requestSequence = 0;
let readerRequestSequence = 0;
let readerState = readerTools.repository();
let tocState = readerTools.toc([]);
let documentSearchState = documentSearchTools.initial();
let documentSearchMarks = [];
let activeDocument = null;
let documentNavigationVersion = 0;
let readingRestoreSequence = 0;
let readingSaveTimer = null;

const loginPanel = document.querySelector('#login-panel');
const repositoryPanel = document.querySelector('#repository-panel');
const readerPanel = document.querySelector('#reader-panel');
const repositorySelect = document.querySelector('#repository-select');
const entriesNode = document.querySelector('#entries');
const emptyState = document.querySelector('#empty-state');
const listRegion = document.querySelector('#entry-list-region');
const searchForm = document.querySelector('#repository-search');
const searchInput = document.querySelector('#search-query');
const clearSearchButton = document.querySelector('#clear-search');
const readerLoading = document.querySelector('#reader-loading');
const readerError = document.querySelector('#reader-error');
const readerErrorMessage = document.querySelector('#reader-error-message');
const readerDocument = document.querySelector('#reader-document');
const documentTitle = document.querySelector('#document-title');
const documentContent = document.querySelector('#document-content');
const tocButton = document.querySelector('#reader-toc');
const tocOverlay = document.querySelector('#toc-overlay');
const tocCloseButton = document.querySelector('#toc-close');
const tocItems = document.querySelector('#toc-items');
const tocDepthButtons = document.querySelectorAll('[data-toc-depth]');
const documentSearchButton = document.querySelector('#reader-search');
const documentSearchPanel = document.querySelector('#reader-search-panel');
const documentSearchInput = document.querySelector('#reader-search-query');
const documentSearchCount = document.querySelector('#reader-search-count');
const documentSearchPrevious = document.querySelector('#reader-search-previous');
const documentSearchNext = document.querySelector('#reader-search-next');
const documentSearchClear = document.querySelector('#reader-search-clear');
const documentSearchClose = document.querySelector('#reader-search-close');
const statusNode = document.querySelector('#status');

class ApiError extends Error {
    constructor(message, status, code) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = code;
    }
}

async function api(action, options = {}) {
    const query = new URLSearchParams({action, ...(options.query || {})});
    let response;
    try {
        response = await fetch(`${apiUrl}?${query}`, {
            method: options.method || 'GET',
            credentials: 'same-origin',
            headers: options.body ? {'Content-Type': 'application/json', 'X-CSRF-Token': csrfToken} : {},
            body: options.body ? JSON.stringify(options.body) : undefined,
        });
    } catch (failure) {
        throw new ApiError('Could not reach the server', 0, 'network_error');
    }

    let payload;
    try {
        payload = await response.json();
    } catch (_) {
        throw new ApiError('The server returned an invalid response', response.status, 'invalid_response');
    }
    if (!response.ok || !payload.success) {
        throw new ApiError(
            payload.error?.message || `HTTP ${response.status}`,
            response.status,
            payload.error?.code || 'request_failed',
        );
    }
    return payload.data;
}

function showError(failure) {
    if (failure instanceof ApiError && failure.status === 401) {
        showLogin('Your session has expired. Sign in again.');
        return;
    }
    statusNode.textContent = failure instanceof Error ? failure.message : String(failure);
    statusNode.className = 'status error';
}

function setStatus(message = '') {
    statusNode.textContent = message;
    statusNode.className = message === '' ? 'status' : 'status notice';
}

function setLoading(message) {
    setStatus(message);
    repositoryPanel.setAttribute('aria-busy', 'true');
}

function finishLoading() {
    repositoryPanel.removeAttribute('aria-busy');
}

function clearDocumentSearchHighlights() {
    documentSearchTools.clearHighlights(documentContent);
    documentSearchMarks = [];
}

function highlightDocumentMatches(query) {
    return documentSearchTools.highlight(documentContent, query);
}

function renderDocumentSearch() {
    const ready = readerState.mode === 'reader' && readerState.status === 'ready';
    documentSearchButton.disabled = !ready;
    documentSearchButton.setAttribute('aria-expanded', String(ready && documentSearchState.open));
    documentSearchPanel.hidden = !ready || !documentSearchState.open;
    documentSearchCount.textContent = documentSearchTools.counter(documentSearchState);
    const hasMatches = documentSearchState.count > 0;
    documentSearchPrevious.disabled = !hasMatches;
    documentSearchNext.disabled = !hasMatches;
    documentSearchClear.disabled = documentSearchState.query === '';
}

function selectDocumentSearchMatch(shouldScroll = true) {
    for (const [index, mark] of documentSearchMarks.entries()) {
        const current = index === documentSearchState.current;
        mark.classList.toggle('is-current', current);
        if (current) {
            mark.setAttribute('aria-current', 'true');
        } else {
            mark.removeAttribute('aria-current');
        }
    }
    renderDocumentSearch();
    const currentMark = documentSearchMarks[documentSearchState.current];
    if (shouldScroll && currentMark) {
        documentNavigationVersion++;
        currentMark.scrollIntoView({block: 'center'});
    }
}

function searchCurrentDocument(query) {
    documentSearchMarks = highlightDocumentMatches(query);
    documentSearchState = documentSearchTools.results(documentSearchState, query, documentSearchMarks.length);
    selectDocumentSearchMatch(documentSearchMarks.length > 0);
}

function resetDocumentSearch() {
    clearDocumentSearchHighlights();
    documentSearchState = documentSearchTools.close();
    documentSearchInput.value = '';
    renderDocumentSearch();
}

function openDocumentSearch() {
    if (readerState.status !== 'ready') {
        return;
    }
    documentSearchState = documentSearchTools.open(documentSearchState);
    renderDocumentSearch();
    documentSearchInput.focus({preventScroll: true});
}

function closeDocumentSearch() {
    resetDocumentSearch();
    documentSearchButton.focus({preventScroll: true});
}

function cancelReadingRestore() {
    readingRestoreSequence++;
}

function saveReadingPosition() {
    if (!activeDocument || readerState.status !== 'ready') {
        return;
    }
    readingPositionTools.save(
        localStorage,
        activeDocument.repository,
        activeDocument.path,
        window.scrollY,
    );
}

function scheduleReadingPositionSave() {
    if (!activeDocument || readerState.status !== 'ready') {
        return;
    }
    window.clearTimeout(readingSaveTimer);
    readingSaveTimer = window.setTimeout(saveReadingPosition, 200);
}

function restoreReadingPosition(repository, path) {
    const restoreId = ++readingRestoreSequence;
    const openingNavigationVersion = documentNavigationVersion;
    requestAnimationFrame(() => requestAnimationFrame(() => {
        if (
            restoreId !== readingRestoreSequence
            || !activeDocument
            || activeDocument.repository !== repository
            || activeDocument.path !== path
            || !readingPositionTools.shouldRestore(openingNavigationVersion, documentNavigationVersion)
        ) {
            return;
        }
        const stored = readingPositionTools.load(localStorage, repository, path);
        if (stored === null) {
            return;
        }
        const top = readingPositionTools.clamp(
            stored,
            document.documentElement.scrollHeight,
            window.innerHeight,
        );
        window.scrollTo({top});
    }));
}

function showLogin(message = '') {
    requestSequence++;
    readerRequestSequence++;
    readerState = readerTools.repository();
    tocState = readerTools.toc([]);
    cancelReadingRestore();
    activeDocument = null;
    resetDocumentSearch();
    document.body.classList.remove('reader-active');
    repositories = [];
    repositoryPanel.hidden = true;
    readerPanel.hidden = true;
    loginPanel.hidden = false;
    document.querySelector('#identity').textContent = '';
    document.querySelector('#logout').hidden = true;
    setStatus(message);
}

function renderToc() {
    tocButton.disabled = !tocState.available;
    tocButton.setAttribute('aria-expanded', String(tocState.open));
    tocOverlay.hidden = !tocState.open;
    tocItems.replaceChildren();

    for (const depthButton of tocDepthButtons) {
        const depth = Number(depthButton.dataset.tocDepth);
        depthButton.setAttribute('aria-pressed', String(depth === tocState.depth));
    }

    for (const item of readerTools.visibleTocItems(tocState)) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `toc-item toc-level-${item.level}`;
        button.textContent = item.title;
        button.dataset.tocTarget = item.target;
        button.addEventListener('click', () => navigateToTocTarget(item.target));
        tocItems.append(button);
    }
}

function openToc() {
    if (!tocState.available || tocState.open) {
        return;
    }
    tocState = readerTools.toggleToc(tocState);
    renderToc();
    tocCloseButton.focus({preventScroll: true});
}

function closeToc(restoreFocus = true) {
    if (!tocState.open) {
        return;
    }
    tocState = readerTools.closeToc(tocState);
    renderToc();
    if (restoreFocus) {
        tocButton.focus({preventScroll: true});
    }
}

function navigateToTocTarget(target) {
    const heading = [...documentContent.querySelectorAll('[id]')]
        .find(element => element.id === target);
    closeToc(false);
    if (!heading) {
        return;
    }
    documentNavigationVersion++;
    if (!heading.hasAttribute('tabindex')) {
        heading.setAttribute('tabindex', '-1');
    }
    heading.focus({preventScroll: true});
    heading.scrollIntoView({block: 'start'});
}

function prepareDocumentLinks() {
    for (const link of documentContent.querySelectorAll('a[href]')) {
        const href = link.getAttribute('href') || '';
        if (readerTools.isExternalHttpLink(href)) {
            link.target = '_blank';
            link.rel = 'noopener noreferrer';
        }
    }
}

function renderReader(nextState) {
    readerState = nextState;
    resetDocumentSearch();
    const inReader = readerState.mode === 'reader';
    document.body.classList.toggle('reader-active', inReader);
    repositoryPanel.hidden = inReader;
    readerPanel.hidden = !inReader;

    readerLoading.hidden = readerState.status !== 'loading';
    readerError.hidden = readerState.status !== 'error';
    readerDocument.hidden = readerState.status !== 'ready';

    if (readerState.status === 'loading') {
        tocState = readerTools.toc([]);
        renderToc();
        documentTitle.textContent = '';
        documentContent.replaceChildren();
        readerErrorMessage.textContent = '';
        document.title = 'Opening document… — MDView';
    } else if (readerState.status === 'error') {
        tocState = readerTools.toc([]);
        renderToc();
        documentTitle.textContent = '';
        documentContent.replaceChildren();
        readerErrorMessage.textContent = readerState.error;
        document.title = 'Document error — MDView';
    } else if (readerState.status === 'ready') {
        documentTitle.textContent = readerState.document.title;
        // DocumentView.content is sanitized by the selected backend renderer.
        documentContent.innerHTML = readerState.document.content;
        prepareDocumentLinks();
        tocState = readerTools.toc(readerState.document.toc);
        renderToc();
        renderDocumentSearch();
        document.title = `${readerState.document.title} — MDView`;
    } else {
        tocState = readerTools.toc([]);
        renderToc();
        document.title = 'MDView Web';
    }
}

function persistRepositoryState() {
    if (currentRepository === '') {
        return;
    }
    stateTools.save(localStorage, {
        repository: currentRepository,
        path: currentPath,
        scrollTop: currentScrollTop,
    });
}

function renderRepositoryChoices() {
    repositorySelect.replaceChildren();
    for (const repository of repositories) {
        const option = document.createElement('option');
        option.value = repository.id;
        option.textContent = repository.name;
        repositorySelect.append(option);
    }
    repositorySelect.value = currentRepository;
}

function renderBreadcrumbs() {
    const breadcrumbs = document.querySelector('#breadcrumbs');
    breadcrumbs.replaceChildren();
    const currentName = repositories.find(repository => repository.id === currentRepository)?.name || currentRepository;
    for (const [index, item] of stateTools.breadcrumbs(currentPath).entries()) {
        if (index > 0) {
            const separator = document.createElement('span');
            separator.className = 'breadcrumb-separator';
            separator.textContent = '/';
            separator.setAttribute('aria-hidden', 'true');
            breadcrumbs.append(separator);
        }
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = item.path === '' ? currentName : item.label;
        button.setAttribute('aria-label', item.path === '' ? 'Repository root' : `Open ${item.label}`);
        if (item.path === currentPath) {
            button.disabled = true;
            button.setAttribute('aria-current', 'page');
        } else {
            button.addEventListener('click', () => openDirectory(currentRepository, item.path).catch(showError));
        }
        breadcrumbs.append(button);
    }
}

function entryIcon(type) {
    const icon = document.createElement('span');
    icon.className = `entry-icon entry-icon-${type}`;
    icon.setAttribute('aria-hidden', 'true');
    return icon;
}

function createItemMenu(entry) {
    const details = document.createElement('details');
    details.className = 'menu item-menu';
    const summary = document.createElement('summary');
    summary.textContent = '\u22ee';
    summary.setAttribute('aria-label', `Actions for ${entry.name}`);
    const popover = document.createElement('div');
    popover.className = 'menu-popover';
    popover.setAttribute('role', 'menu');
    for (const label of ['Rename', 'Move', 'Delete']) {
        const action = document.createElement('button');
        action.type = 'button';
        action.disabled = true;
        action.textContent = label;
        action.setAttribute('role', 'menuitem');
        popover.append(action);
    }
    details.append(summary, popover);
    return details;
}

function renderEntries(entries, options = {}) {
    entriesNode.replaceChildren();
    emptyState.hidden = entries.length !== 0;
    emptyState.textContent = options.emptyMessage || 'This folder is empty';
    for (const entry of entries) {
        const item = document.createElement('li');
        item.className = 'entry-row';

        const openButton = document.createElement('button');
        openButton.type = 'button';
        openButton.className = 'entry-open';
        openButton.append(entryIcon(entry.type));
        const text = document.createElement('span');
        text.className = 'entry-text';
        const name = document.createElement('span');
        name.className = 'entry-name';
        name.textContent = entry.name;
        text.append(name);
        if (options.showPath) {
            const path = document.createElement('span');
            path.className = 'entry-path';
            path.textContent = entry.path;
            text.append(path);
        }
        openButton.append(text);

        if (entry.type === 'directory') {
            openButton.addEventListener('click', () => openDirectory(currentRepository, entry.path).catch(showError));
        } else if (entry.readable) {
            openButton.addEventListener('click', () => openDocument(currentRepository, entry.path).catch(showError));
        } else {
            openButton.disabled = true;
            openButton.title = 'This file type is not supported';
        }
        item.append(openButton, createItemMenu(entry));
        entriesNode.append(item);
    }
}

async function requestDirectory(repository, path) {
    return api('directory', {query: {repository, path}});
}

async function openDirectory(repository, path, options = {}) {
    const requestId = ++requestSequence;
    setLoading('Loading folder…');
    try {
        const data = await requestDirectory(repository, path);
        if (requestId !== requestSequence) {
            return;
        }
        currentRepository = repository;
        currentPath = data.path;
        currentScrollTop = options.restoreScroll || 0;
        returnScrollTop = currentScrollTop;
        searchActive = false;
        searchInput.value = '';
        clearSearchButton.hidden = true;
        repositorySelect.value = repository;
        document.querySelector('#repository-heading').textContent = 'Folder contents';
        renderBreadcrumbs();
        renderEntries(data.entries);
        requestAnimationFrame(() => {
            listRegion.scrollTop = returnScrollTop;
        });
        persistRepositoryState();
        setStatus('');
    } finally {
        if (requestId === requestSequence) {
            finishLoading();
        }
    }
}

async function restoreDirectory(savedState) {
    const repository = stateTools.selectRepository(
        repositories.map(available => available.id),
        savedState?.repository,
    );
    const paths = stateTools.pathFallbacks(savedState?.repository === repository ? savedState.path : '');
    for (const path of paths) {
        try {
            await openDirectory(repository, path, {
                restoreScroll: path === savedState?.path ? savedState.scrollTop : 0,
            });
            return;
        } catch (failure) {
            const recoverable = failure instanceof ApiError
                && (failure.status === 404 || failure.code === 'not_a_directory');
            if (!recoverable) {
                throw failure;
            }
        }
    }
    await openDirectory(repository, '');
}

async function showRepositories(user) {
    loginPanel.hidden = true;
    renderReader(readerTools.repository());
    document.querySelector('#identity').textContent = `${user.username} (${user.role})`;
    document.querySelector('#logout').hidden = false;
    setLoading('Loading repositories…');
    try {
        const data = await api('repositories');
        repositories = data.repositories;
        if (repositories.length === 0) {
            currentRepository = '';
            currentPath = '';
            renderRepositoryChoices();
            document.querySelector('#breadcrumbs').replaceChildren();
            renderEntries([], {emptyMessage: 'No repositories are available'});
            repositorySelect.disabled = true;
            searchInput.disabled = true;
            setStatus('No repositories are available for this account.');
            return;
        }
        repositorySelect.disabled = false;
        searchInput.disabled = false;
        const savedState = stateTools.load(localStorage);
        currentRepository = stateTools.selectRepository(
            repositories.map(repository => repository.id),
            savedState?.repository,
        );
        renderRepositoryChoices();
        await restoreDirectory(savedState);
    } finally {
        finishLoading();
    }
}

async function searchRepository(query) {
    const normalized = query.trim();
    if (normalized === '') {
        await openDirectory(currentRepository, currentPath, {restoreScroll: currentScrollTop});
        return;
    }
    const requestId = ++requestSequence;
    setLoading('Searching…');
    try {
        const data = await api('search', {query: {repository: currentRepository, query: normalized}});
        if (requestId !== requestSequence) {
            return;
        }
        searchActive = true;
        clearSearchButton.hidden = false;
        document.querySelector('#repository-heading').textContent = `Search results for “${normalized}”`;
        renderEntries(data.results, {showPath: true, emptyMessage: 'No matching files or folders'});
        listRegion.scrollTop = 0;
        returnScrollTop = 0;
        setStatus('');
    } finally {
        if (requestId === requestSequence) {
            finishLoading();
        }
    }
}

async function openDocument(repository, path) {
    const readerRequestId = ++readerRequestSequence;
    cancelReadingRestore();
    activeDocument = null;
    documentNavigationVersion = 0;
    returnScrollTop = listRegion.scrollTop;
    if (!searchActive) {
        currentScrollTop = returnScrollTop;
        persistRepositoryState();
    }
    setStatus('');
    renderReader(readerTools.loading());
    window.scrollTo({top: 0});
    try {
        const {document: view} = await api('document', {query: {repository, path}});
        if (readerRequestId !== readerRequestSequence) {
            return;
        }
        renderReader(readerTools.ready(view));
        activeDocument = {repository, path};
        restoreReadingPosition(repository, path);
    } catch (failure) {
        if (readerRequestId !== readerRequestSequence) {
            return;
        }
        if (failure instanceof ApiError && failure.status === 401) {
            showError(failure);
            return;
        }
        const message = failure instanceof Error ? failure.message : 'The document could not be loaded.';
        renderReader(readerTools.failed(message));
    }
}

async function initialize() {
    csrfToken = (await api('csrf')).csrf_token;
    try {
        const session = await api('session');
        csrfToken = session.csrf_token;
        await showRepositories(session.user);
    } catch (failure) {
        if (failure instanceof ApiError && failure.status === 401) {
            showLogin();
            return;
        }
        throw failure;
    }
}

document.querySelector('#login-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    setStatus('Signing in…');
    try {
        const data = await loginForm.submit(form, credentials => api('login', {
            method: 'POST',
            body: credentials,
        }));
        csrfToken = data.csrf_token;
        await showRepositories(data.user);
    } catch (failure) {
        showError(failure);
    }
});

document.querySelector('#logout').addEventListener('click', async () => {
    try {
        const data = await api('logout', {method: 'POST', body: {}});
        csrfToken = data.csrf_token;
        showLogin();
    } catch (failure) {
        showError(failure);
    }
});

repositorySelect.addEventListener('change', () => {
    openDirectory(repositorySelect.value, '').catch(failure => {
        repositorySelect.value = currentRepository;
        showError(failure);
    });
});

searchForm.addEventListener('submit', event => {
    event.preventDefault();
    searchRepository(searchInput.value).catch(showError);
});

clearSearchButton.addEventListener('click', () => {
    searchInput.value = '';
    openDirectory(currentRepository, currentPath, {restoreScroll: currentScrollTop}).catch(showError);
    searchInput.focus();
});

searchInput.addEventListener('input', () => {
    if (searchInput.value === '' && searchActive) {
        openDirectory(currentRepository, currentPath, {restoreScroll: currentScrollTop}).catch(showError);
    }
});

listRegion.addEventListener('scroll', () => {
    returnScrollTop = listRegion.scrollTop;
    if (searchActive) {
        return;
    }
    currentScrollTop = returnScrollTop;
    persistRepositoryState();
}, {passive: true});

document.querySelector('#back').addEventListener('click', () => {
    window.clearTimeout(readingSaveTimer);
    saveReadingPosition();
    cancelReadingRestore();
    activeDocument = null;
    readerRequestSequence++;
    renderReader(readerTools.repository());
    requestAnimationFrame(() => {
        listRegion.scrollTop = returnScrollTop;
    });
});

documentSearchButton.addEventListener('click', () => {
    if (documentSearchState.open) {
        closeDocumentSearch();
    } else {
        openDocumentSearch();
    }
});

documentSearchInput.addEventListener('input', () => {
    searchCurrentDocument(documentSearchInput.value);
});

documentSearchInput.addEventListener('keydown', event => {
    if (event.key === 'Enter') {
        event.preventDefault();
        documentSearchState = documentSearchTools.move(documentSearchState, event.shiftKey ? -1 : 1);
        selectDocumentSearchMatch();
    }
});

documentSearchPrevious.addEventListener('click', () => {
    documentSearchState = documentSearchTools.move(documentSearchState, -1);
    selectDocumentSearchMatch();
});

documentSearchNext.addEventListener('click', () => {
    documentSearchState = documentSearchTools.move(documentSearchState, 1);
    selectDocumentSearchMatch();
});

documentSearchClear.addEventListener('click', () => {
    documentSearchInput.value = '';
    searchCurrentDocument('');
    documentSearchInput.focus({preventScroll: true});
});

documentSearchClose.addEventListener('click', closeDocumentSearch);

tocButton.addEventListener('click', () => {
    if (tocState.open) {
        closeToc();
    } else {
        openToc();
    }
});

tocCloseButton.addEventListener('click', () => closeToc());

tocOverlay.addEventListener('click', event => {
    if (event.target === tocOverlay) {
        closeToc();
    }
});

for (const depthButton of tocDepthButtons) {
    depthButton.addEventListener('click', () => {
        tocState = readerTools.setTocDepth(tocState, Number(depthButton.dataset.tocDepth));
        renderToc();
    });
}

document.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
        if (tocState.open) {
            event.preventDefault();
            closeToc();
        } else if (documentSearchState.open) {
            event.preventDefault();
            closeDocumentSearch();
        }
    }
});

window.addEventListener('scroll', scheduleReadingPositionSave, {passive: true});
window.addEventListener('pagehide', saveReadingPosition);

initialize().catch(showError);
