/*
 * reader-state.js — created 2026-09-25, version 0.3.0.
 * Purpose: define browser-independent Reader mode transitions and DocumentView validation.
 * Algorithm: create immutable loading, ready, error, and Repository states while accepting
 * only the renderer-independent title/content fields required by the stage-three UI.
 */

'use strict';

(function exposeReaderState(root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.MdviewReaderState = api;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function createReaderState() {
    function repository() {
        return {mode: 'repository', status: 'idle', document: null, error: ''};
    }

    function loading() {
        return {mode: 'reader', status: 'loading', document: null, error: ''};
    }

    function ready(documentView) {
        if (!documentView || typeof documentView.title !== 'string' || typeof documentView.content !== 'string') {
            throw new TypeError('Invalid DocumentView response');
        }
        return {mode: 'reader', status: 'ready', document: documentView, error: ''};
    }

    function failed(message) {
        const safeMessage = typeof message === 'string' && message.trim() !== ''
            ? message
            : 'The document could not be loaded.';
        return {mode: 'reader', status: 'error', document: null, error: safeMessage};
    }

    function isExternalHttpLink(href) {
        return typeof href === 'string' && /^(https?:)?\/\//i.test(href);
    }

    return {repository, loading, ready, failed, isExternalHttpLink};
}));
