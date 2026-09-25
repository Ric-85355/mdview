<?php
/*
 * api.php — created 2026-09-25, version 0.2.0.
 * Purpose: expose the internal JSON API for Auth, Repository, Reader, and admin mutations.
 * Algorithm: authorize each request, validate session CSRF before writes, delegate guarded
 * filesystem work, and map failures to the common response envelope without path leakage.
 */

declare(strict_types=1);

use Mdview\Web\Api\ApiException;
use Mdview\Web\Api\JsonResponse;

require __DIR__ . '/bootstrap.php';

session_name('MDVIEWSESSID');
session_set_cookie_params([
    'httponly' => true,
    'secure' => isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
    'samesite' => 'Strict',
    'path' => '/',
]);
session_start();

/** @return array<string, mixed> */
function mdview_request_body(): array
{
    $contentType = $_SERVER['CONTENT_TYPE'] ?? '';
    if (str_contains(strtolower($contentType), 'application/json')) {
        $raw = file_get_contents('php://input');
        try {
            $decoded = json_decode($raw === false ? '' : $raw, true, 32, JSON_THROW_ON_ERROR);
        } catch (JsonException $failure) {
            throw new ApiException('invalid_json', 400, 'Request body is not valid JSON', $failure);
        }
        if (!is_array($decoded)) {
            throw new ApiException('invalid_request', 400, 'JSON object expected');
        }
        return $decoded;
    }
    return $_POST;
}

/** @param array<string, mixed> $source */
function mdview_string(array $source, string $key, bool $required = true): string
{
    $value = $source[$key] ?? null;
    if (!is_string($value) || ($required && trim($value) === '')) {
        throw new ApiException('invalid_request', 400, "Missing or invalid field: {$key}");
    }
    return $value;
}

/** @return array{name: string, tmp_name: string, error: int} */
function mdview_uploaded_file(string $key): array
{
    $file = $_FILES[$key] ?? null;
    if (!is_array($file)
        || !isset($file['name'], $file['tmp_name'], $file['error'])
        || !is_string($file['name'])
        || !is_string($file['tmp_name'])
        || !is_int($file['error'])) {
        throw new ApiException('invalid_upload', 400, 'One uploaded file is required');
    }
    return ['name' => $file['name'], 'tmp_name' => $file['tmp_name'], 'error' => $file['error']];
}

try {
    $services = mdview_services();
    $auth = $services['auth'];
    $csrf = $services['csrf'];
    $repositories = $services['repositories'];
    $reader = $services['reader'];
    $action = isset($_GET['action']) && is_string($_GET['action']) ? $_GET['action'] : '';
    $method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

    if ($action === 'csrf' && $method === 'GET') {
        JsonResponse::sendSuccess(['csrf_token' => $csrf->token()]);
    }

    if ($action === 'login' && $method === 'POST') {
        $body = mdview_request_body();
        $csrf->validate($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ($body['csrf_token'] ?? null));
        $user = $auth->login(mdview_string($body, 'username'), mdview_string($body, 'password'));
        JsonResponse::sendSuccess(['user' => $user->toArray(), 'csrf_token' => $csrf->rotate()]);
    }

    if ($action === 'logout' && $method === 'POST') {
        $body = mdview_request_body();
        $auth->requireUser();
        $csrf->validate($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ($body['csrf_token'] ?? null));
        $auth->logout();
        JsonResponse::sendSuccess(['logged_out' => true, 'csrf_token' => $csrf->rotate()]);
    }

    if ($action === 'create_directory' && $method === 'POST') {
        $auth->requireUser();
        $body = mdview_request_body();
        $repository = mdview_string($body, 'repository');
        $auth->requireWrite($repository);
        $csrf->validate($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ($body['csrf_token'] ?? null));
        $created = $repositories->createDirectory(
            $repository,
            mdview_string($body, 'path', false),
            mdview_string($body, 'name'),
        );
        JsonResponse::sendSuccess(['directory' => $created], 201);
    }

    if ($action === 'upload' && $method === 'POST') {
        $auth->requireUser();
        $body = mdview_request_body();
        $repository = mdview_string($body, 'repository');
        $auth->requireWrite($repository);
        $csrf->validate($_SERVER['HTTP_X_CSRF_TOKEN'] ?? ($body['csrf_token'] ?? null));
        $file = mdview_uploaded_file('file');
        $uploaded = $repositories->upload(
            $repository,
            mdview_string($body, 'path', false),
            $file['name'],
            $file['tmp_name'],
            $file['error'],
        );
        JsonResponse::sendSuccess(['file' => $uploaded], 201);
    }

    if ($method !== 'GET') {
        throw new ApiException('invalid_method', 400, 'Unsupported request method');
    }

    $user = $auth->requireUser();
    if ($action === 'session') {
        JsonResponse::sendSuccess(['user' => $user->toArray(), 'csrf_token' => $csrf->token()]);
    }
    if ($action === 'repositories') {
        JsonResponse::sendSuccess([
            'repositories' => $repositories->listRepositories($user->canAccessRepository(...)),
        ]);
    }

    $repository = isset($_GET['repository']) && is_string($_GET['repository']) ? $_GET['repository'] : '';
    $auth->requireRepository($repository);
    if ($action === 'directory') {
        $path = isset($_GET['path']) && is_string($_GET['path']) ? $_GET['path'] : '';
        JsonResponse::sendSuccess($repositories->listDirectory($repository, $path));
    }
    if ($action === 'search') {
        $query = isset($_GET['query']) && is_string($_GET['query']) ? $_GET['query'] : '';
        JsonResponse::sendSuccess(['results' => $repositories->search($repository, $query)]);
    }
    if ($action === 'document') {
        $path = isset($_GET['path']) && is_string($_GET['path']) ? $_GET['path'] : '';
        JsonResponse::sendSuccess(['document' => $reader->open($repository, $path)->toArray()]);
    }

    throw new ApiException('unknown_action', 400, 'Unknown API action');
} catch (ApiException $failure) {
    JsonResponse::sendError($failure->apiCode, $failure->getMessage(), $failure->httpStatus);
} catch (Throwable $failure) {
    error_log('MDView API failure: ' . $failure->getMessage());
    JsonResponse::sendError('internal_error', 'Internal server error', 500);
}
