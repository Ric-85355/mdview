/*
 * document_search_test.js — created 2026-09-25, version 0.5.0.
 * Purpose: regression-test in-document search matching and cyclic navigation.
 * Algorithm: exercise empty/single/multiple/Unicode ranges and immutable counter state.
 */

'use strict';

const assert = require('node:assert/strict');
const search = require('../mdview-server/assets/document-search.js');

assert.deepEqual(search.findRanges('Visible text', ''), [], 'empty query must not match');
assert.deepEqual(search.findRanges('One result', 'result'), [{start: 4, end: 10}]);
assert.equal(search.counter(search.results(search.initial(), 'result', 1)), '1 / 1');
assert.deepEqual(search.findRanges('one ONE One', 'one'), [
    {start: 0, end: 3},
    {start: 4, end: 7},
    {start: 8, end: 11},
], 'search should be case-insensitive and return every match');
assert.deepEqual(search.findRanges('Привет, мир! ПРИВЕТ', 'привет'), [
    {start: 0, end: 6},
    {start: 13, end: 19},
], 'Unicode text should search case-insensitively');
assert.deepEqual(search.findRanges('literal a+b value', 'a+b'), [{start: 8, end: 11}], 'query is literal, not a regex');

let state = search.results(search.open(search.initial()), 'one', 3);
assert.equal(search.counter(state), '1 / 3');
state = search.move(state, 1);
assert.equal(search.counter(state), '2 / 3');
state = search.move(search.move(state, 1), 1);
assert.equal(search.counter(state), '1 / 3', 'Next should wrap after the final match');
state = search.move(state, -1);
assert.equal(search.counter(state), '3 / 3', 'Previous should wrap before the first match');

const cleared = search.results(state, '', 0);
assert.equal(search.counter(cleared), '0 / 0');
assert.equal(cleared.current, -1);
assert.deepEqual(search.close(), search.initial(), 'closing search should clear query and highlights state');

console.log('PASS document search ranges, Unicode, counters, and cyclic navigation');
