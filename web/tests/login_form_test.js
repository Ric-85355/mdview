/*
 * login_form_test.js — created 2026-09-25, version 0.4.1.
 * Purpose: reproduce browsers clearing SubmitEvent.currentTarget during async login.
 * Algorithm: start the production login helper with a pending authenticator, clear the
 * event target before resolution, and verify captured credentials and form reset.
 */

'use strict';

const assert = require('node:assert/strict');
const loginForm = require('../mdview-server/assets/login-form.js');

const form = {
    fields: {username: 'reader', password: 'correct-password'},
    resetCount: 0,
    reset() {
        this.resetCount++;
        this.fields = {username: '', password: ''};
    },
};
let currentTarget = form;
const event = {
    get currentTarget() {
        return currentTarget;
    },
};

class FakeFormData {
    constructor(submittedForm) {
        this.values = {...submittedForm.fields};
    }

    get(name) {
        return this.values[name] ?? null;
    }
}

let finishAuthentication;
let receivedCredentials;
const authentication = new Promise(resolve => {
    finishAuthentication = resolve;
});
const capturedForm = event.currentTarget;
const submission = loginForm.submit(capturedForm, credentials => {
    receivedCredentials = credentials;
    return authentication;
}, FakeFormData);

// Browsers clear currentTarget after the synchronous listener portion returns.
currentTarget = null;
finishAuthentication({csrf_token: 'new-token', user: {username: 'reader'}});

submission.then(result => {
    assert.deepEqual(receivedCredentials, {username: 'reader', password: 'correct-password'});
    assert.equal(result.user.username, 'reader', 'successful login result should continue through the flow');
    assert.equal(form.resetCount, 1, 'the captured form should reset after successful login');
    assert.deepEqual(form.fields, {username: '', password: ''});
    assert.equal(currentTarget, null, 'the test must reproduce a cleared event.currentTarget');
    console.log('PASS async login captures and resets the submitted form');
}).catch(failure => {
    console.error(failure);
    process.exitCode = 1;
});
