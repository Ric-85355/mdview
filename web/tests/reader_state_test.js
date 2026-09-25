/*
 * reader_state_test.js — created 2026-09-25, version 0.3.0.
 * Purpose: regression-test Reader mode transitions without a browser or DOM library.
 * Algorithm: exercise loading, generic DocumentView readiness, failure, and Repository return.
 */

'use strict';

const assert = require('node:assert/strict');
const reader = require('../mdview-server/assets/reader-state.js');

assert.deepEqual(reader.loading(), {
    mode: 'reader',
    status: 'loading',
    document: null,
    error: '',
}, 'opening a document should clear stale content and enter loading state');

const documentView = {
    title: 'Очень длинный заголовок',
    content: '<h1 id="intro">Привет</h1><p>Reader content</p>',
    toc: [{level: 1, title: 'Привет', target: 'intro'}],
    metadata: {type: 'future-format'},
};
assert.deepEqual(reader.ready(documentView), {
    mode: 'reader',
    status: 'ready',
    document: documentView,
    error: '',
}, 'Reader should accept the generic DocumentView contract without inspecting its type');

assert.throws(() => reader.ready({title: 'Missing content'}), /Invalid DocumentView/);
assert.deepEqual(reader.failed('Document not found'), {
    mode: 'reader',
    status: 'error',
    document: null,
    error: 'Document not found',
});
assert.deepEqual(reader.repository(), {
    mode: 'repository',
    status: 'idle',
    document: null,
    error: '',
}, 'Reader back should return to Repository mode');
assert.equal(reader.isExternalHttpLink('https://example.com/page'), true);
assert.equal(reader.isExternalHttpLink('http://example.com/page'), true);
assert.equal(reader.isExternalHttpLink('//example.com/page'), true);
assert.equal(reader.isExternalHttpLink('#section'), false, 'internal anchors should retain normal navigation');
assert.equal(reader.isExternalHttpLink('relative/page'), false);
assert.equal(reader.isExternalHttpLink('javascript:alert(1)'), false);

console.log('PASS Reader loading, DocumentView, error, and Repository transitions');
