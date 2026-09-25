/*
 * reading_position_test.js — created 2026-09-25, version 0.5.0.
 * Purpose: regression-test per-document reading position persistence and restore priority.
 * Algorithm: use memory storage to verify keys, missing values, clamping, and explicit-nav guard.
 */

'use strict';

const assert = require('node:assert/strict');
const positions = require('../mdview-server/assets/reading-position.js');

class MemoryStorage {
    constructor() {
        this.values = new Map();
    }

    getItem(key) {
        return this.values.has(key) ? this.values.get(key) : null;
    }

    setItem(key, value) {
        this.values.set(key, String(value));
    }
}

const storage = new MemoryStorage();
assert.equal(positions.load(storage, 'Repo', 'one.md'), null, 'missing position should remain absent');
positions.save(storage, 'Repo', 'one.md', 420);
positions.save(storage, 'Repo', 'two.md', 75);
positions.save(storage, 'Репо', 'Папка/Файл.md', 128);
assert.equal(positions.load(storage, 'Repo', 'one.md'), 420);
assert.equal(positions.load(storage, 'Repo', 'two.md'), 75, 'documents should keep separate positions');
assert.equal(positions.load(storage, 'Репо', 'Папка/Файл.md'), 128);
assert.notEqual(positions.key('Repo', 'one.md'), positions.key('Repo', 'two.md'));
assert.equal(positions.clamp(900, 700, 200), 500, 'oversized position should clamp to page maximum');
assert.equal(positions.clamp(100, 700, 200), 100);
assert.equal(positions.clamp(100, 100, 300), 0);
assert.equal(positions.shouldRestore(0, 0), true);
assert.equal(
    positions.shouldRestore(0, 1),
    false,
    'explicit TOC/search navigation must supersede delayed automatic restore',
);

const unavailableStorage = {
    getItem() { throw new Error('blocked'); },
    setItem() { throw new Error('blocked'); },
};
assert.equal(positions.load(unavailableStorage, 'Repo', 'one.md'), null);
assert.equal(positions.save(unavailableStorage, 'Repo', 'one.md', 10), null);

console.log('PASS per-document reading position save, restore, clamp, and navigation priority');
