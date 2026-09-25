<?php
/*
 * http_smoke.php — created 2026-09-25, version 0.2.0.
 * Purpose: verify the real HTTP session/ACL/CSRF/read/write pipeline without production data.
 * Algorithm: launch PHP's local server against temporary config/repositories, call API
 * through cURL with a cookie jar, assert access/error envelopes, then tear everything down.
 */

declare(strict_types=1);

/** @throws RuntimeException */
function smoke_assert(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

function smoke_remove_tree(string $path): void
{
    if (is_link($path) || is_file($path)) {
        unlink($path);
        return;
    }
    if (!is_dir($path)) {
        return;
    }
    foreach (new FilesystemIterator($path, FilesystemIterator::SKIP_DOTS) as $entry) {
        smoke_remove_tree($entry->getPathname());
    }
    rmdir($path);
}

/**
 * @param array<string, mixed>|null $body
 * @return array{status: int, payload: array<string, mixed>}
 */
function smoke_request(string $url, string $cookieJar, ?array $body = null, string $csrf = ''): array
{
    $handle = curl_init($url);
    $headers = ['Accept: application/json'];
    if ($body !== null) {
        $headers[] = 'Content-Type: application/json';
        if ($csrf !== '') {
            $headers[] = 'X-CSRF-Token: ' . $csrf;
        }
        curl_setopt($handle, CURLOPT_POST, true);
        curl_setopt($handle, CURLOPT_POSTFIELDS, json_encode($body, JSON_THROW_ON_ERROR));
    }
    curl_setopt_array($handle, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_COOKIEFILE => $cookieJar,
        CURLOPT_COOKIEJAR => $cookieJar,
        CURLOPT_TIMEOUT => 3,
    ]);
    $response = curl_exec($handle);
    if (!is_string($response)) {
        $message = curl_error($handle);
        curl_close($handle);
        throw new RuntimeException('HTTP request failed: ' . $message);
    }
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    $payload = json_decode($response, true, 32, JSON_THROW_ON_ERROR);
    smoke_assert(is_array($payload), 'JSON response object expected');
    return ['status' => $status, 'payload' => $payload];
}

/**
 * @param array<string, string|CURLFile> $fields
 * @return array{status: int, payload: array<string, mixed>}
 */
function smoke_multipart_request(string $url, string $cookieJar, array $fields, string $csrf): array
{
    $handle = curl_init($url);
    curl_setopt_array($handle, [
        CURLOPT_POST => true,
        CURLOPT_POSTFIELDS => $fields,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => ['Accept: application/json', 'X-CSRF-Token: ' . $csrf],
        CURLOPT_COOKIEFILE => $cookieJar,
        CURLOPT_COOKIEJAR => $cookieJar,
        CURLOPT_TIMEOUT => 3,
    ]);
    $response = curl_exec($handle);
    if (!is_string($response)) {
        $message = curl_error($handle);
        curl_close($handle);
        throw new RuntimeException('HTTP multipart request failed: ' . $message);
    }
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    $payload = json_decode($response, true, 32, JSON_THROW_ON_ERROR);
    smoke_assert(is_array($payload), 'JSON multipart response object expected');
    return ['status' => $status, 'payload' => $payload];
}

$base = sys_get_temp_dir() . '/mdview-web-http-' . bin2hex(random_bytes(6));
$repositoryRoot = $base . '/repository-root';
$sessionDirectory = $base . '/sessions';
$usersFile = $base . '/users.php';
$cookieJar = $base . '/cookies.txt';
$adminCookieJar = $base . '/admin-cookies.txt';
$logFile = $base . '/server.log';
$server = null;
$pipes = [];

try {
    mkdir($repositoryRoot . '/Allowed', 0777, true);
    mkdir($repositoryRoot . '/Forbidden', 0777, true);
    mkdir($sessionDirectory, 0777, true);
    file_put_contents($repositoryRoot . '/Allowed/hello.md', "# Hello\n\nSafe **Markdown**.\n");
    file_put_contents($repositoryRoot . '/Forbidden/secret.md', "# Secret\n");
    $users = [
        'reader' => [
            'password_hash' => password_hash('smoke-password', PASSWORD_DEFAULT),
            'role' => 'user',
            'repositories' => ['Allowed'],
        ],
        'admin' => [
            'password_hash' => password_hash('admin-password', PASSWORD_DEFAULT),
            'role' => 'admin',
            'repositories' => ['*'],
        ],
    ];
    file_put_contents($usersFile, '<?php return ' . var_export($users, true) . ';');

    $socket = stream_socket_server('tcp://127.0.0.1:0', $errorNumber, $errorMessage);
    if ($socket === false) {
        throw new RuntimeException("Could not reserve local port: {$errorMessage}");
    }
    $address = stream_socket_get_name($socket, false);
    fclose($socket);
    $port = (int) substr((string) $address, strrpos((string) $address, ':') + 1);
    $webRoot = realpath(dirname(__DIR__));
    smoke_assert(is_string($webRoot), 'Web root not found');

    $descriptors = [
        0 => ['file', '/dev/null', 'r'],
        1 => ['file', $logFile, 'a'],
        2 => ['file', $logFile, 'a'],
    ];
    $environment = getenv();
    if (!is_array($environment)) {
        $environment = [];
    }
    $environment['MDVIEW_REPOSITORY_ROOT'] = $repositoryRoot;
    $environment['MDVIEW_USERS_FILE'] = $usersFile;
    $server = proc_open(
        [PHP_BINARY, '-d', 'session.save_path=' . $sessionDirectory, '-S', '127.0.0.1:' . $port, '-t', $webRoot],
        $descriptors,
        $pipes,
        $webRoot,
        $environment,
    );
    smoke_assert(is_resource($server), 'Could not start PHP server');
    $baseUrl = 'http://127.0.0.1:' . $port . '/mdview-server/api.php';

    $csrfResponse = null;
    for ($attempt = 0; $attempt < 30; $attempt++) {
        try {
            $csrfResponse = smoke_request($baseUrl . '?action=csrf', $cookieJar);
            break;
        } catch (Throwable) {
            usleep(100_000);
        }
    }
    smoke_assert($csrfResponse !== null && $csrfResponse['status'] === 200, 'CSRF endpoint unavailable');
    $csrf = $csrfResponse['payload']['data']['csrf_token'] ?? '';
    smoke_assert(is_string($csrf) && $csrf !== '', 'CSRF token missing');

    $unauthorized = smoke_request($baseUrl . '?action=repositories', $cookieJar);
    smoke_assert($unauthorized['status'] === 401, 'Unauthenticated request must return 401');
    $unauthorizedMutation = smoke_request(
        $baseUrl . '?action=create_directory',
        $cookieJar,
        ['repository' => 'Allowed', 'path' => '', 'name' => 'No auth'],
        $csrf,
    );
    smoke_assert($unauthorizedMutation['status'] === 401, 'Unauthenticated mutation must return 401');

    $login = smoke_request(
        $baseUrl . '?action=login',
        $cookieJar,
        ['username' => 'reader', 'password' => 'smoke-password'],
        $csrf,
    );
    smoke_assert($login['status'] === 200 && $login['payload']['success'] === true, 'Login failed');
    $readerCsrf = $login['payload']['data']['csrf_token'] ?? '';

    $readerMutation = smoke_request(
        $baseUrl . '?action=create_directory',
        $cookieJar,
        ['repository' => 'Allowed', 'path' => '', 'name' => 'Reader folder'],
        is_string($readerCsrf) ? $readerCsrf : '',
    );
    smoke_assert($readerMutation['status'] === 403, 'Read-only user mutation must return 403');
    $readerUploadSource = $base . '/reader-upload-source';
    file_put_contents($readerUploadSource, 'reader must not upload');
    $readerUpload = smoke_multipart_request(
        $baseUrl . '?action=upload',
        $cookieJar,
        [
            'repository' => 'Allowed',
            'path' => '',
            'file' => new CURLFile($readerUploadSource, 'text/plain', 'reader.txt'),
        ],
        is_string($readerCsrf) ? $readerCsrf : '',
    );
    smoke_assert($readerUpload['status'] === 403, 'Read-only user upload must return 403');
    smoke_assert(!file_exists($repositoryRoot . '/Allowed/reader.txt'), 'Read-only upload changed repository');

    $repositories = smoke_request($baseUrl . '?action=repositories', $cookieJar);
    smoke_assert(
        $repositories['payload']['data']['repositories'] === [['id' => 'Allowed', 'name' => 'Allowed']],
        'Repository ACL leaked or hid data',
    );

    $forbidden = smoke_request(
        $baseUrl . '?action=directory&repository=Forbidden&path=',
        $cookieJar,
    );
    smoke_assert($forbidden['status'] === 403, 'Forbidden repository must return 403');

    $document = smoke_request(
        $baseUrl . '?action=document&repository=Allowed&path=hello.md',
        $cookieJar,
    );
    smoke_assert($document['status'] === 200, 'Document endpoint failed');
    smoke_assert($document['payload']['data']['document']['title'] === 'Hello', 'DocumentView title mismatch');
    smoke_assert(isset($document['payload']['data']['document']['content']), 'DocumentView content missing');

    $missingDocument = smoke_request(
        $baseUrl . '?action=document&repository=Allowed&path=removed.md',
        $cookieJar,
    );
    smoke_assert($missingDocument['status'] === 404, 'Missing document must return 404');
    smoke_assert($missingDocument['payload']['success'] === false, 'Missing document must use error envelope');

    $traversal = smoke_request(
        $baseUrl . '?action=directory&repository=Allowed&path=%252e%252e%252fForbidden',
        $cookieJar,
    );
    smoke_assert($traversal['status'] === 400, 'Encoded traversal must return 400');

    $adminCsrfResponse = smoke_request($baseUrl . '?action=csrf', $adminCookieJar);
    $adminCsrf = $adminCsrfResponse['payload']['data']['csrf_token'] ?? '';
    smoke_assert(is_string($adminCsrf) && $adminCsrf !== '', 'Admin CSRF token missing');
    $adminLogin = smoke_request(
        $baseUrl . '?action=login',
        $adminCookieJar,
        ['username' => 'admin', 'password' => 'admin-password'],
        $adminCsrf,
    );
    smoke_assert($adminLogin['status'] === 200, 'Admin login failed');
    $adminCsrf = $adminLogin['payload']['data']['csrf_token'] ?? '';
    smoke_assert(is_string($adminCsrf) && $adminCsrf !== '', 'Rotated admin CSRF token missing');

    $badCsrf = smoke_request(
        $baseUrl . '?action=create_directory',
        $adminCookieJar,
        ['repository' => 'Allowed', 'path' => '', 'name' => 'Bad CSRF'],
        'wrong-token',
    );
    smoke_assert($badCsrf['status'] === 403, 'Invalid mutation CSRF must return 403');
    $badCsrfUpload = smoke_multipart_request(
        $baseUrl . '?action=upload',
        $adminCookieJar,
        [
            'repository' => 'Allowed',
            'path' => '',
            'file' => new CURLFile($readerUploadSource, 'text/plain', 'bad-csrf.txt'),
        ],
        'wrong-token',
    );
    smoke_assert($badCsrfUpload['status'] === 403, 'Invalid upload CSRF must return 403');
    smoke_assert(!file_exists($repositoryRoot . '/Allowed/bad-csrf.txt'), 'Invalid CSRF upload changed repository');

    $created = smoke_request(
        $baseUrl . '?action=create_directory',
        $adminCookieJar,
        ['repository' => 'Allowed', 'path' => '', 'name' => 'Новая папка'],
        $adminCsrf,
    );
    smoke_assert($created['status'] === 201 && is_dir($repositoryRoot . '/Allowed/Новая папка'), 'Admin create directory failed');
    $duplicateDirectory = smoke_request(
        $baseUrl . '?action=create_directory',
        $adminCookieJar,
        ['repository' => 'Allowed', 'path' => '', 'name' => 'Новая папка'],
        $adminCsrf,
    );
    smoke_assert($duplicateDirectory['status'] === 409, 'Duplicate directory must return 409');

    $uploadSource = $base . '/upload-source';
    file_put_contents($uploadSource, "uploaded through HTTP\n");
    $uploaded = smoke_multipart_request(
        $baseUrl . '?action=upload',
        $adminCookieJar,
        [
            'repository' => 'Allowed',
            'path' => 'Новая папка',
            'file' => new CURLFile($uploadSource, 'application/octet-stream', 'Файл data.bin'),
        ],
        $adminCsrf,
    );
    smoke_assert($uploaded['status'] === 201, 'Admin multipart upload failed');
    smoke_assert(
        file_get_contents($repositoryRoot . '/Allowed/Новая папка/Файл data.bin') === "uploaded through HTTP\n",
        'Multipart upload content or destination mismatch',
    );
    $duplicateUpload = smoke_multipart_request(
        $baseUrl . '?action=upload',
        $adminCookieJar,
        [
            'repository' => 'Allowed',
            'path' => 'Новая папка',
            'file' => new CURLFile($uploadSource, 'application/octet-stream', 'Файл data.bin'),
        ],
        $adminCsrf,
    );
    smoke_assert($duplicateUpload['status'] === 409, 'Duplicate upload must return 409');
    smoke_assert(
        file_get_contents($repositoryRoot . '/Allowed/Новая папка/Файл data.bin') === "uploaded through HTTP\n",
        'Duplicate upload changed existing file',
    );

    echo "PASS HTTP session/CSRF/ACL/Reader/create/upload smoke test\n";
} finally {
    if (is_resource($server)) {
        proc_terminate($server);
        proc_close($server);
    }
    smoke_remove_tree($base);
}
