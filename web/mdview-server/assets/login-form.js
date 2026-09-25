/*
 * login-form.js — created 2026-09-25, version 0.4.1.
 * Purpose: keep the submitted login form stable across asynchronous authentication.
 * Algorithm: read credentials from the already captured form, await authentication,
 * then reset that stable form reference without retaining the transient event object.
 */

'use strict';

(function exposeLoginForm(root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.MdviewLoginForm = api;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function createLoginForm() {
    async function submit(form, authenticate, FormDataType = FormData) {
        const values = new FormDataType(form);
        const data = await authenticate({
            username: values.get('username'),
            password: values.get('password'),
        });
        form.reset();
        return data;
    }

    return {submit};
}));
