/*
 * repository_mutations_test.js — created 2026-09-25, version 0.6.0.
 * Purpose: regression-test Repository mutation permissions, payloads, and refresh sequencing.
 * Algorithm: use fake FormData and operations to verify admin/user, current path, cancel,
 * successful refresh, and failure propagation without a browser or backend.
 */

'use strict';

const assert = require('node:assert/strict');
const mutations = require('../mdview-server/assets/repository-mutations.js');

class FakeFormData {
    constructor() {
        this.values = [];
    }

    append(name, value) {
        this.values.push([name, value]);
    }
}

assert.deepEqual(mutations.actionsFor({role: 'admin', can_write: true}), {
    createDirectory: true,
    upload: true,
}, 'admin actions should follow authenticated server identity');
assert.deepEqual(mutations.actionsFor({role: 'user', can_write: false}), {
    createDirectory: false,
    upload: false,
}, 'read-only user actions must remain unavailable');
assert.deepEqual(
    mutations.createDirectoryBody('Docs', 'linux/network', '  New Folder  '),
    {repository: 'Docs', path: 'linux/network', name: 'New Folder'},
    'New folder must use the current repository and path',
);
assert.throws(() => mutations.createDirectoryBody('Docs', '', '   '), /must not be empty/);

const file = {name: 'notes.bin'};
const upload = mutations.uploadFormData('Docs', 'linux/network', file, FakeFormData);
assert.deepEqual(upload.values, [
    ['repository', 'Docs'],
    ['path', 'linux/network'],
    ['file', file],
], 'Upload must use the selected file and current Repository destination');
assert.equal(mutations.uploadFormData('Docs', '', null, FakeFormData), null, 'picker cancel must not create a request');

(async () => {
    const calls = [];
    const result = await mutations.execute(
        async () => { calls.push('mutation'); return {name: 'created'}; },
        async () => { calls.push('refresh'); },
    );
    assert.deepEqual(calls, ['mutation', 'refresh'], 'successful mutation must refresh afterward');
    assert.deepEqual(result, {name: 'created'});

    let refreshCalled = false;
    await assert.rejects(
        mutations.execute(
            async () => { throw new Error('403 forbidden'); },
            async () => { refreshCalled = true; },
        ),
        /403 forbidden/,
        'API errors must reach the UI unchanged',
    );
    assert.equal(refreshCalled, false, 'failed mutation must not refresh or damage Repository state');

    await assert.rejects(
        mutations.execute(async () => ({ok: true}), async () => { throw new Error('offline'); }),
        failure => failure.mutationCompleted === true,
        'refresh failure must preserve the fact that the mutation completed',
    );
    console.log('PASS Repository mutation permissions, destinations, cancel, errors, and refresh');
})().catch(failure => {
    console.error(failure);
    process.exitCode = 1;
});
