/*
 * repository-state.js — created 2026-09-25, version 0.2.0.
 * Purpose: normalize and persist browser-side Repository navigation state.
 * Algorithm: validate untrusted localStorage data, build ancestor fallbacks and breadcrumbs,
 * and expose the same pure helpers to browsers and dependency-free Node tests.
 */

'use strict';

(function exposeRepositoryState(root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.MdviewRepositoryState = api;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function createRepositoryState() {
    const storageKey = 'mdview.repository.v1';

    function validSegment(value) {
        return value !== '' && value !== '.' && value !== '..' && !value.includes('\\') && !value.includes('\0');
    }

    function normalizePath(value) {
        if (typeof value !== 'string' || value.startsWith('/') || value.endsWith('/')) {
            return '';
        }
        if (value === '') {
            return '';
        }
        const segments = value.split('/');
        return segments.every(validSegment) ? segments.join('/') : '';
    }

    function normalize(value) {
        if (!value || typeof value !== 'object' || typeof value.repository !== 'string' || value.repository === '') {
            return null;
        }
        const path = normalizePath(value.path);
        if (value.path !== path) {
            return null;
        }
        const numericScroll = Number(value.scrollTop);
        return {
            repository: value.repository,
            path,
            scrollTop: Number.isFinite(numericScroll) && numericScroll >= 0 ? numericScroll : 0,
        };
    }

    function load(storage) {
        try {
            return normalize(JSON.parse(storage.getItem(storageKey) || 'null'));
        } catch (_) {
            return null;
        }
    }

    function save(storage, value) {
        const normalized = normalize(value);
        if (normalized) {
            storage.setItem(storageKey, JSON.stringify(normalized));
        }
        return normalized;
    }

    function pathFallbacks(path) {
        const normalized = normalizePath(path);
        if (path !== normalized) {
            return [''];
        }
        const fallbacks = [normalized];
        let candidate = normalized;
        while (candidate !== '') {
            candidate = candidate.includes('/') ? candidate.slice(0, candidate.lastIndexOf('/')) : '';
            fallbacks.push(candidate);
        }
        return fallbacks;
    }

    function breadcrumbs(path) {
        const normalized = normalizePath(path);
        const items = [{label: '', path: ''}];
        if (normalized === '') {
            return items;
        }
        let current = '';
        for (const segment of normalized.split('/')) {
            current = current === '' ? segment : `${current}/${segment}`;
            items.push({label: segment, path: current});
        }
        return items;
    }

    function selectRepository(availableIds, savedId) {
        if (!Array.isArray(availableIds) || availableIds.length === 0) {
            return '';
        }
        return typeof savedId === 'string' && availableIds.includes(savedId) ? savedId : availableIds[0];
    }

    return {storageKey, normalize, load, save, pathFallbacks, breadcrumbs, selectRepository};
}));
