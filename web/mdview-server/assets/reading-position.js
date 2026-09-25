/*
 * reading-position.js — created 2026-09-25, version 0.5.0.
 * Purpose: persist independent browser-local reading positions for logical documents.
 * Algorithm: encode repository/path into a versioned key, validate stored offsets, clamp
 * restoration to current page metrics, and guard delayed restore after explicit navigation.
 */

'use strict';

(function exposeReadingPosition(root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.MdviewReadingPosition = api;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function createReadingPosition() {
    const prefix = 'mdview.reading.v1:';

    function key(repository, path) {
        return `${prefix}${encodeURIComponent(repository)}:${encodeURIComponent(path)}`;
    }

    function save(storage, repository, path, offset) {
        const numericOffset = Number(offset);
        const normalized = Number.isFinite(numericOffset) && numericOffset >= 0 ? numericOffset : 0;
        try {
            storage.setItem(key(repository, path), String(normalized));
        } catch (_) {
            return null;
        }
        return normalized;
    }

    function load(storage, repository, path) {
        let stored;
        try {
            stored = storage.getItem(key(repository, path));
        } catch (_) {
            return null;
        }
        if (stored === null) {
            return null;
        }
        const offset = Number(stored);
        return Number.isFinite(offset) && offset >= 0 ? offset : null;
    }

    function clamp(offset, scrollHeight, viewportHeight) {
        const maximum = Math.max(0, Number(scrollHeight) - Number(viewportHeight));
        return Math.min(Math.max(0, Number(offset) || 0), maximum);
    }

    function shouldRestore(openNavigationVersion, currentNavigationVersion) {
        return openNavigationVersion === currentNavigationVersion;
    }

    return {prefix, key, save, load, clamp, shouldRestore};
}));
