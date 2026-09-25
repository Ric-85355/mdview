/*
 * repository-mutations.js — created 2026-09-25, version 0.6.0.
 * Purpose: keep Repository mutation request/state logic testable outside the browser UI.
 * Algorithm: derive actions from server-provided identity, build current-folder payloads,
 * and distinguish a completed mutation from a subsequent directory-refresh failure.
 */

'use strict';

(function exposeRepositoryMutations(root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else {
        root.MdviewRepositoryMutations = api;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function createRepositoryMutations() {
    function actionsFor(user) {
        const allowed = user?.can_write === true;
        return {createDirectory: allowed, upload: allowed};
    }

    function createDirectoryBody(repository, path, name) {
        const normalizedName = typeof name === 'string' ? name.trim() : '';
        if (normalizedName === '') {
            throw new Error('Folder name must not be empty');
        }
        return {repository, path, name: normalizedName};
    }

    function uploadFormData(repository, path, file, FormDataConstructor = FormData) {
        if (!file) {
            return null;
        }
        const formData = new FormDataConstructor();
        formData.append('repository', repository);
        formData.append('path', path);
        formData.append('file', file);
        return formData;
    }

    async function execute(operation, refresh) {
        const result = await operation();
        try {
            await refresh();
        } catch (failure) {
            const error = new Error('Repository refresh failed after the operation completed', {cause: failure});
            error.mutationCompleted = true;
            throw error;
        }
        return result;
    }

    return {actionsFor, createDirectoryBody, uploadFormData, execute};
}));
