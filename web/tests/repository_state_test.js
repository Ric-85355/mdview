/*
 * repository_state_test.js — created 2026-09-25, version 0.2.0.
 * Purpose: regression-test browser-independent Repository state and path recovery logic.
 * Algorithm: exercise the production state module with an in-memory localStorage substitute.
 */

'use strict';

const assert = require('node:assert/strict');
const state = require('../mdview-server/assets/repository-state.js');

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
const saved = state.save(storage, {repository: 'Репо с пробелом', path: 'Музыка/Сведение', scrollTop: 148.5});
assert.deepEqual(saved, {repository: 'Репо с пробелом', path: 'Музыка/Сведение', scrollTop: 148.5});
assert.deepEqual(state.load(storage), saved, 'saved Repository state should round-trip');

assert.deepEqual(
    state.pathFallbacks('Music/Mixing/Reaper'),
    ['Music/Mixing/Reaper', 'Music/Mixing', 'Music', ''],
    'missing paths should recover through every ancestor',
);
assert.deepEqual(
    state.breadcrumbs('Music/Folder With Space'),
    [
        {label: '', path: ''},
        {label: 'Music', path: 'Music'},
        {label: 'Folder With Space', path: 'Music/Folder With Space'},
    ],
    'breadcrumbs should preserve logical paths with spaces',
);

storage.setItem(state.storageKey, '{broken');
assert.equal(state.load(storage), null, 'broken localStorage JSON should be ignored');
assert.equal(state.normalize({repository: 'Alpha', path: '../Forbidden', scrollTop: 1}), null, 'unsafe saved paths should be discarded');
assert.deepEqual(state.pathFallbacks('../Forbidden'), [''], 'unsafe fallback should be Repository root');
assert.deepEqual(state.normalize({repository: 'Alpha', path: '', scrollTop: -5}), {repository: 'Alpha', path: '', scrollTop: 0});
assert.equal(state.selectRepository(['Alpha', 'Бета'], 'Бета'), 'Бета', 'an available saved repository should be restored');
assert.equal(state.selectRepository(['Alpha', 'Бета'], 'Removed'), 'Alpha', 'an unavailable repository should fall back');
assert.equal(state.selectRepository([], 'Removed'), '', 'an empty repository list should be handled');

console.log('PASS Repository client state, UTF-8, breadcrumbs, and ancestor recovery');
