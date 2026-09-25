/*
 * document-search.js — created 2026-09-25, version 0.5.0.
 * Purpose: provide browser-independent state and matching for in-document search.
 * Algorithm: find non-overlapping Unicode-aware case-insensitive ranges and keep an immutable
 * cyclic active-match index; DOM highlighting remains in the Reader controller.
 */

'use strict';

(function exposeDocumentSearch(root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.MdviewDocumentSearch = api;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function createDocumentSearch() {
    function initial() {
        return {open: false, query: '', count: 0, current: -1};
    }

    function open(state) {
        return {...state, open: true};
    }

    function close() {
        return initial();
    }

    function results(state, query, count) {
        const normalizedQuery = typeof query === 'string' ? query : '';
        const normalizedCount = Number.isInteger(count) && count > 0 ? count : 0;
        return {
            ...state,
            query: normalizedQuery,
            count: normalizedCount,
            current: normalizedCount > 0 ? 0 : -1,
        };
    }

    function move(state, step) {
        if (state.count === 0) {
            return {...state, current: -1};
        }
        const direction = step < 0 ? -1 : 1;
        return {...state, current: (state.current + direction + state.count) % state.count};
    }

    function counter(state) {
        return state.count === 0 ? '0 / 0' : `${state.current + 1} / ${state.count}`;
    }

    function findRanges(text, query) {
        if (typeof text !== 'string' || typeof query !== 'string' || query === '') {
            return [];
        }
        const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const pattern = new RegExp(escapedQuery, 'giu');
        const ranges = [];
        for (const match of text.matchAll(pattern)) {
            ranges.push({start: match.index, end: match.index + match[0].length});
        }
        return ranges;
    }

    function clearHighlights(root) {
        const parents = new Set();
        for (const mark of root.querySelectorAll('mark[data-mdview-search]')) {
            const parent = mark.parentNode;
            if (!parent) {
                continue;
            }
            parent.replaceChild(root.ownerDocument.createTextNode(mark.textContent || ''), mark);
            parents.add(parent);
        }
        for (const parent of parents) {
            parent.normalize();
        }
    }

    function searchableTextNodes(root) {
        const excluded = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEXTAREA']);
        const nodeFilter = root.ownerDocument.defaultView.NodeFilter;
        const nodes = [];
        const walker = root.ownerDocument.createTreeWalker(root, nodeFilter.SHOW_TEXT, {
            acceptNode(node) {
                if (!node.nodeValue || node.nodeValue === '') {
                    return nodeFilter.FILTER_REJECT;
                }
                for (let element = node.parentElement; element && element !== root; element = element.parentElement) {
                    if (excluded.has(element.tagName) || element.matches('mark[data-mdview-search]')) {
                        return nodeFilter.FILTER_REJECT;
                    }
                }
                return nodeFilter.FILTER_ACCEPT;
            },
        });
        while (walker.nextNode()) {
            nodes.push(walker.currentNode);
        }
        return nodes;
    }

    function highlight(root, query) {
        clearHighlights(root);
        if (query === '') {
            return [];
        }
        let matchIndex = 0;
        for (const textNode of searchableTextNodes(root)) {
            const ranges = findRanges(textNode.nodeValue || '', query);
            const baseIndex = matchIndex;
            matchIndex += ranges.length;
            for (let index = ranges.length - 1; index >= 0; index--) {
                const match = ranges[index];
                const range = root.ownerDocument.createRange();
                range.setStart(textNode, match.start);
                range.setEnd(textNode, match.end);
                const mark = root.ownerDocument.createElement('mark');
                mark.dataset.mdviewSearch = '';
                mark.dataset.searchIndex = String(baseIndex + index);
                range.surroundContents(mark);
            }
        }
        return [...root.querySelectorAll('mark[data-mdview-search]')]
            .sort((left, right) => Number(left.dataset.searchIndex) - Number(right.dataset.searchIndex));
    }

    return {initial, open, close, results, move, counter, findRanges, clearHighlights, highlight};
}));
