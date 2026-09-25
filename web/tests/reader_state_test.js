/*
 * reader_state_test.js — created 2026-09-25, version 0.3.0.
 * Purpose: regression-test Reader and TOC state transitions without a browser or DOM library.
 * Algorithm: exercise DocumentView readiness, errors, drawer toggle, depth filtering, and return.
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
assert.throws(
    () => reader.ready({title: 'Bad TOC', content: '<p>x</p>', toc: [{level: 1, title: 'x', target: ''}]}),
    /Invalid DocumentView TOC item/,
);
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

const tocItems = [
    {level: 1, title: 'One', target: 'one'},
    {level: 2, title: 'Two', target: 'two'},
    {level: 3, title: 'Three', target: 'three'},
    {level: 4, title: 'Unsupported depth', target: 'four'},
];
const closedToc = {...reader.toc(tocItems), documentScrollTop: 420};
assert.equal(closedToc.available, true);
assert.equal(closedToc.depth, 3, 'TOC should default to depth 3');
assert.deepEqual(reader.visibleTocItems(closedToc).map(item => item.target), ['one', 'two', 'three']);

const openedToc = reader.toggleToc(closedToc);
assert.equal(openedToc.open, true, 'Contents should open the drawer');
assert.equal(openedToc.documentScrollTop, 420, 'opening TOC must preserve document scroll state');
const reclosedToc = reader.toggleToc(openedToc);
assert.equal(reclosedToc.open, false, 'repeated Contents action should close the drawer');
assert.equal(reclosedToc.documentScrollTop, 420, 'closing TOC must preserve document scroll state');
assert.equal(reader.closeToc(openedToc).open, false, 'the explicit close action should close the drawer');

assert.deepEqual(
    reader.visibleTocItems(reader.setTocDepth(openedToc, 1)).map(item => item.target),
    ['one'],
    'depth 1 should use level-one targets only',
);
assert.deepEqual(
    reader.visibleTocItems(reader.setTocDepth(openedToc, 2)).map(item => item.target),
    ['one', 'two'],
    'depth 2 should retain level-one and level-two targets',
);
assert.deepEqual(
    reader.visibleTocItems(reader.setTocDepth(openedToc, 3)).map(item => item.target),
    ['one', 'two', 'three'],
    'depth 3 should retain all supported targets',
);
assert.throws(() => reader.setTocDepth(openedToc, 4), /TOC depth/);

const emptyToc = reader.toc([]);
assert.equal(emptyToc.available, false, 'Contents should be disabled for an empty TOC');
assert.equal(reader.toggleToc(emptyToc).open, false, 'an empty TOC must not open a drawer');

console.log('PASS Reader, DocumentView, TOC depth/toggle, error, and Repository transitions');
