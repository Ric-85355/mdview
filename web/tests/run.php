<?php
/*
 * run.php — created 2026-09-25, version 0.2.0.
 * Purpose: test Web MDView reads, authorization, rendering, and guarded mutations.
 * Algorithm: build an isolated temporary repository tree, execute pure/service checks,
 * report each case, and remove only the uniquely generated temporary test directory.
 */

declare(strict_types=1);

use Mdview\Web\Api\ApiException;
use Mdview\Web\Api\JsonResponse;
use Mdview\Web\Auth\AuthService;
use Mdview\Web\Auth\Csrf;
use Mdview\Web\Auth\User;
use Mdview\Web\Reader\ReaderService;
use Mdview\Web\Renderer\MarkdownRenderer;
use Mdview\Web\Renderer\RendererRegistry;
use Mdview\Web\Repository\PathGuard;
use Mdview\Web\Repository\RepositoryService;

require dirname(__DIR__) . '/mdview-server/bootstrap.php';

$base = sys_get_temp_dir() . '/mdview-web-tests-' . bin2hex(random_bytes(6));
mkdir($base . '/sessions', 0777, true);
ini_set('session.save_path', $base . '/sessions');
session_id('mdview-web-tests-' . bin2hex(random_bytes(4)));
session_start();
ob_start();

/** @throws RuntimeException */
function assert_true(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

/** @throws RuntimeException */
function assert_same(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . '\nExpected: ' . var_export($expected, true)
            . '\nActual: ' . var_export($actual, true));
    }
}

/** @param callable(): void $operation */
function assert_api_error(string $code, int $status, callable $operation): void
{
    try {
        $operation();
    } catch (ApiException $failure) {
        assert_same($code, $failure->apiCode, 'Unexpected API error code');
        assert_same($status, $failure->httpStatus, 'Unexpected HTTP status');
        return;
    }
    throw new RuntimeException("Expected ApiException {$code}");
}

function remove_tree(string $path): void
{
    if (is_link($path) || is_file($path)) {
        unlink($path);
        return;
    }
    if (!is_dir($path)) {
        return;
    }
    foreach (new FilesystemIterator($path, FilesystemIterator::SKIP_DOTS) as $entry) {
        remove_tree($entry->getPathname());
    }
    rmdir($path);
}

$root = $base . '/repository-root';
$outside = $base . '/outside';
mkdir($root . '/Alpha/Folder With Space', 0777, true);
mkdir($root . '/Alpha/Z Empty', 0777, true);
mkdir($root . '/Русский Репо/Вложенный', 0777, true);
mkdir($root . '/mdview-server/config', 0777, true);
mkdir($root . '/Alpha/.git', 0777, true);
mkdir($outside, 0777, true);
file_put_contents($root . '/Alpha/README.md', "## Before\n# Раздел\n### Раздел\n\n**bold** <script>alert(1)</script> [bad](javascript:alert(1))\n");
file_put_contents($root . '/Alpha/Folder With Space/notes.md', "# Notes\nneedle only in content\n");
file_put_contents($root . '/Alpha/content-only.md', "# hidden-search-token\n");
file_put_contents($root . '/Alpha/image.png', 'not really an image');
file_put_contents($root . '/Русский Репо/Вложенный/Документ.md', "# Привет\n");
file_put_contents($root . '/mdview-server/config/users.php', '<?php return [];');
file_put_contents($outside . '/secret.md', '# Secret');
symlink($outside, $root . '/Alpha/escape');

$paths = new PathGuard($root, ['mdview-server']);
$repositories = new RepositoryService($paths);
$registry = new RendererRegistry();
$registry->register(new MarkdownRenderer());
$reader = new ReaderService($paths, $registry);

$tests = [];
$tests['repository discovery excludes service and unsafe top-level objects'] = static function () use ($paths): void {
    assert_same(['Alpha', 'Русский Репо'], $paths->repositoryNames(), 'Repository discovery mismatch');
};
$tests['roles and repository grants are independent'] = static function (): void {
    $admin = new User('admin', 'admin', ['*']);
    $limited = new User('reader', 'user', ['Alpha']);
    $wildcardReader = new User('all-reader', 'user', ['*']);
    assert_true($admin->canAccessRepository('Русский Репо') && $admin->canWrite(), 'Admin rights failed');
    assert_true($limited->canAccessRepository('Alpha'), 'Allowed repository denied');
    assert_true(!$limited->canAccessRepository('Русский Репо') && !$limited->canWrite(), 'User scope failed');
    assert_true($wildcardReader->canAccessRepository('Русский Репо') && !$wildcardReader->canWrite(), 'Read wildcard failed');
};
$tests['password hash login and backend permission checks'] = static function (): void {
    $_SESSION = [];
    $auth = new AuthService([
        'reader' => [
            'password_hash' => password_hash('test-password', PASSWORD_DEFAULT),
            'role' => 'user',
            'repositories' => ['Alpha'],
        ],
    ]);
    assert_api_error('invalid_credentials', 401, static fn () => $auth->login('reader', 'wrong'));
    assert_same('reader', $auth->login('reader', 'test-password')->username, 'Login failed');
    $auth->requireRepository('Alpha');
    assert_api_error('repository_forbidden', 403, static fn () => $auth->requireRepository('Русский Репо'));
    assert_api_error('operation_forbidden', 403, static fn () => $auth->requireWrite('Alpha'));
};
$tests['CSRF token is session-bound and validated'] = static function (): void {
    $_SESSION = [];
    $csrf = new Csrf();
    $token = $csrf->token();
    $csrf->validate($token);
    assert_api_error('csrf_invalid', 403, static fn () => $csrf->validate('wrong-token'));
    assert_true($csrf->rotate() !== $token, 'CSRF token did not rotate');
};
$tests['directory listing supports UTF-8 spaces and directory-first order'] = static function () use ($repositories): void {
    $alpha = $repositories->listDirectory('Alpha', '');
    assert_same('Folder With Space', $alpha['entries'][0]['name'], 'Directory must sort before files');
    assert_same('directory', $alpha['entries'][0]['type'], 'Directory type mismatch');
    $utf8 = $repositories->listDirectory('Русский Репо', 'Вложенный');
    assert_same('Документ.md', $utf8['entries'][0]['name'], 'UTF-8 filename mismatch');
};
$tests['empty directories remain navigable'] = static function () use ($repositories): void {
    $empty = $repositories->listDirectory('Alpha', 'Z Empty');
    assert_same('Z Empty', $empty['path'], 'Directory path mismatch');
    assert_same([], $empty['entries'], 'Empty directory should return an empty entry list');
};
$tests['create directory accepts one UTF-8 child inside the current path'] = static function () use ($repositories, $root): void {
    $created = $repositories->createDirectory('Alpha', 'Folder With Space', '  Тестовый каталог  ');
    assert_same('Folder With Space/  Тестовый каталог  ', $created['path'], 'Created path mismatch');
    assert_true(is_dir($root . '/Alpha/Folder With Space/  Тестовый каталог  '), 'Directory not created');
};
$tests['create directory rejects conflicts traversal and symlink escape'] = static function () use ($repositories): void {
    assert_api_error('conflict', 409, static fn () => $repositories->createDirectory('Alpha', '', 'README.md'));
    foreach (['', '   ', '.', '..', 'one/two', '../outside', 'one\\two', '%2e%2e'] as $unsafe) {
        assert_api_error('invalid_name', 400, static fn () => $repositories->createDirectory('Alpha', '', $unsafe));
    }
    assert_api_error('path_forbidden', 403, static fn () => $repositories->createDirectory('Alpha', 'escape', 'child'));
};
$tests['upload stores one UTF-8 file in current directory without overwrite'] = static function () use ($repositories, $root, $base): void {
    $temporary = $base . '/incoming-upload';
    file_put_contents($temporary, "uploaded \x00 bytes\n");
    $uploaded = $repositories->upload(
        'Alpha',
        'Folder With Space',
        'Новый файл.bin',
        $temporary,
        UPLOAD_ERR_OK,
        static fn (string $path): bool => $path === $temporary,
    );
    assert_same('Folder With Space/Новый файл.bin', $uploaded['path'], 'Uploaded path mismatch');
    assert_same("uploaded \x00 bytes\n", file_get_contents($root . '/Alpha/Folder With Space/Новый файл.bin'), 'Uploaded content mismatch');
    $listed = $repositories->listDirectory('Alpha', 'Folder With Space');
    $uploadedEntry = array_values(array_filter(
        $listed['entries'],
        static fn (array $entry): bool => $entry['name'] === 'Новый файл.bin',
    ))[0] ?? null;
    assert_true(is_array($uploadedEntry) && $uploadedEntry['readable'] === false, 'Unsupported upload should remain visible but unreadable');

    file_put_contents($temporary, 'replacement');
    assert_api_error('conflict', 409, static fn () => $repositories->upload(
        'Alpha', '', 'README.md', $temporary, UPLOAD_ERR_OK, static fn (): bool => true,
    ));
    assert_true(str_starts_with((string) file_get_contents($root . '/Alpha/README.md'), '## Before'), 'Existing file was changed');
};
$tests['upload rejects invalid destination and invalid PHP upload'] = static function () use ($repositories, $base): void {
    $temporary = $base . '/unsafe-upload';
    file_put_contents($temporary, 'unsafe');
    assert_api_error('not_found', 404, static fn () => $repositories->upload(
        'Alpha', 'missing', 'file.bin', $temporary, UPLOAD_ERR_OK, static fn (): bool => true,
    ));
    assert_api_error('path_forbidden', 403, static fn () => $repositories->upload(
        'Alpha', 'escape', 'file.bin', $temporary, UPLOAD_ERR_OK, static fn (): bool => true,
    ));
    foreach (['../file.bin', 'folder/file.bin', '/file.bin', 'folder\\file.bin'] as $unsafe) {
        assert_api_error('invalid_name', 400, static fn () => $repositories->upload(
            'Alpha', '', $unsafe, $temporary, UPLOAD_ERR_OK, static fn (): bool => true,
        ));
    }
    assert_api_error('invalid_upload', 400, static fn () => $repositories->upload(
        'Alpha', '', 'file.bin', $temporary, UPLOAD_ERR_OK, static fn (): bool => false,
    ));
    assert_api_error('upload_failed', 400, static fn () => $repositories->upload(
        'Alpha', '', 'file.bin', $temporary, UPLOAD_ERR_PARTIAL, static fn (): bool => true,
    ));
};
$tests['repository list obeys current user grants'] = static function () use ($repositories): void {
    $visible = $repositories->listRepositories(static fn (string $name): bool => $name === 'Alpha');
    assert_same([['id' => 'Alpha', 'name' => 'Alpha']], $visible, 'Forbidden repository leaked');
};
$tests['search checks names not Markdown contents'] = static function () use ($repositories): void {
    $named = $repositories->search('Alpha', 'notes');
    assert_same('Folder With Space/notes.md', $named[0]['path'], 'Filename search failed');
    assert_same([], $repositories->search('Alpha', 'needle'), 'Search inspected document contents');
};
$tests['traversal and encoded traversal are rejected'] = static function () use ($paths): void {
    foreach (['../outside', '%2e%2e/outside', '%252e%252e%252foutside', '/etc/passwd', 'a\\b'] as $unsafe) {
        assert_api_error('invalid_path', 400, static fn () => $paths->relativePath($unsafe));
    }
};
$tests['symlink cannot leave repository'] = static function () use ($paths): void {
    assert_api_error('path_forbidden', 403, static fn () => $paths->resolveExisting('Alpha', 'escape/secret.md'));
};
$tests['Markdown renderer returns safe DocumentView and ordered TOC'] = static function () use ($reader): void {
    $view = $reader->open('Alpha', 'README.md')->toArray();
    assert_same('Before', $view['title'], 'First heading in document order must be title');
    assert_same([2, 1, 3], array_column($view['toc'], 'level'), 'TOC order mismatch');
    assert_same(['before', 'раздел', 'раздел-2'], array_column($view['toc'], 'target'), 'Stable targets mismatch');
    assert_true(!str_contains($view['content'], '<script>'), 'Raw script was not escaped');
    assert_true(!str_contains($view['content'], 'javascript:'), 'Unsafe link destination survived');
    assert_same('markdown', $view['metadata']['type'], 'Renderer metadata mismatch');
};
$tests['Reader rejects unsupported files'] = static function () use ($reader): void {
    assert_api_error('unsupported_document_type', 400, static fn () => $reader->open('Alpha', 'image.png'));
};
$tests['JSON envelopes are unambiguous and path-free'] = static function (): void {
    assert_same(['success' => true, 'data' => ['value' => 1]], JsonResponse::successPayload(['value' => 1]), 'Success envelope mismatch');
    assert_same(
        ['success' => false, 'error' => ['code' => 'conflict', 'message' => 'Name already exists']],
        JsonResponse::errorPayload('conflict', 'Name already exists'),
        'Error envelope mismatch',
    );
    $conflict = ApiException::conflict('Name already exists');
    assert_same(409, $conflict->httpStatus, 'Conflict HTTP status mismatch');
};

$failed = 0;
try {
    foreach ($tests as $name => $test) {
        try {
            $test();
            echo "PASS {$name}\n";
        } catch (Throwable $failure) {
            $failed++;
            fwrite(STDERR, "FAIL {$name}: {$failure->getMessage()}\n");
        }
    }
} finally {
    session_write_close();
    remove_tree($base);
}

echo sprintf("%d tests, %d failed\n", count($tests), $failed);
$output = ob_get_clean();
echo $output;
exit($failed === 0 ? 0 : 1);
