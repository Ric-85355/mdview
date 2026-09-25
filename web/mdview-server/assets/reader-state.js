/*
 * reader-state.js — created 2026-09-25, version 0.4.0.
 * Purpose: define browser-independent Reader and TOC drawer state transitions.
 * Algorithm: validate renderer-independent DocumentView fields, normalize TOC items, and
 * create immutable mode, drawer, and depth-filter transitions for the vanilla-JS UI.
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
        if (!documentView || typeof documentView.title !== 'string' || typeof documentView.content !== 'string'
            || !Array.isArray(documentView.toc)) {
            throw new TypeError('Invalid DocumentView response');
        }
        const toc = documentView.toc.map(item => {
            if (!item || !Number.isInteger(item.level) || item.level < 1
                || typeof item.title !== 'string' || typeof item.target !== 'string' || item.target === '') {
                throw new TypeError('Invalid DocumentView TOC item');
            }
            return {level: item.level, title: item.title, target: item.target};
        });
        return {mode: 'reader', status: 'ready', document: {...documentView, toc}, error: ''};
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

    function toc(items) {
        const supportedItems = items.filter(item => item.level <= 3);
        return {items: supportedItems, available: supportedItems.length > 0, open: false, depth: 3};
    }

    function toggleToc(state) {
        return state.available ? {...state, open: !state.open} : {...state, open: false};
    }

    function closeToc(state) {
        return {...state, open: false};
    }

    function setTocDepth(state, depth) {
        if (![1, 2, 3].includes(depth)) {
            throw new RangeError('TOC depth must be 1, 2, or 3');
        }
        return {...state, depth};
    }

    function visibleTocItems(state) {
        return state.items.filter(item => item.level <= state.depth);
    }

    return {
        repository,
        loading,
        ready,
        failed,
        isExternalHttpLink,
        toc,
        toggleToc,
        closeToc,
        setTocDepth,
        visibleTocItems,
    };
}));
