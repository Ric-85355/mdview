<?php
/*
 * test_repository_generator.php — created 2026-08-30, version 0.3.0.
 * Purpose: regression-test the dynamic repository index without a web server.
 * Algorithm: create a temporary mixed filesystem, invoke generator functions,
 * validate sorting/depth/UTF-8/errors, and remove only that temporary tree.
 */

declare(strict_types=1);

require_once __DIR__ . '/../server/mdrepo/repository.php';

/**
 * Fail the test process with a concise assertion message.
 */
function assert_repository_test(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

/**
 * Create a UTF-8 test file and its parent directory.
 */
function write_repository_fixture(string $root, string $path, string $content): void
{
    $absolutePath = $root . DIRECTORY_SEPARATOR . $path;
    $directory = dirname($absolutePath);
    if (!is_dir($directory) && !mkdir($directory, 0700, true) && !is_dir($directory)) {
        throw new RuntimeException('Could not create fixture directory');
    }
    if (file_put_contents($absolutePath, $content) === false) {
        throw new RuntimeException('Could not write fixture file');
    }
}

/**
 * Remove the uniquely named temporary fixture after the test.
 */
function remove_repository_fixture(string $path): void
{
    if (!is_dir($path)) {
        return;
    }
    foreach (scandir($path) ?: [] as $entry) {
        if ($entry === '.' || $entry === '..') {
            continue;
        }
        $child = $path . DIRECTORY_SEPARATOR . $entry;
        if (is_dir($child) && !is_link($child)) {
            remove_repository_fixture($child);
        } else {
            unlink($child);
        }
    }
    rmdir($path);
}

$root = sys_get_temp_dir() . DIRECTORY_SEPARATOR
    . 'mdview-repository-test-' . bin2hex(random_bytes(8));
if (!mkdir($root, 0700, true)) {
    throw new RuntimeException('Could not create temporary repository');
}

try {
    write_repository_fixture(
        $root,
        'repository.meta.json',
        '{"name":"Тестовый репозиторий","description":"Документы"}'
    );
    write_repository_fixture($root, 'root.md', '# Root');
    write_repository_fixture($root, 'Alpha.md', '# Alpha');
    write_repository_fixture($root, 'archive.zip', 'ignored');
    write_repository_fixture($root, 'hardware/raymarine.md', '# Raymarine');
    write_repository_fixture($root, 'hardware/MikroTik.md', '# MikroTik');
    write_repository_fixture($root, 'hardware/diagram.png', 'ignored');
    write_repository_fixture($root, 'linux/network.md', '# Network');
    write_repository_fixture($root, 'linux/advanced/routing.md', '# Routing');
    write_repository_fixture($root, 'linux/advanced/сеть.md', '# Сеть');
    write_repository_fixture($root, 'linux/advanced/deeper/hidden.md', '# Hidden');
    write_repository_fixture($root, 'src/main.py', 'ignored');

    $timestamp = 1700000000;
    $iterator = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS),
        RecursiveIteratorIterator::CHILD_FIRST
    );
    foreach ($iterator as $item) {
        touch($item->getPathname(), $timestamp);
    }

    $index = build_repository_index($root);
    assert_repository_test($index['format'] === 1, 'format must be 1');
    assert_repository_test(
        $index['name'] === 'Тестовый репозиторий',
        'UTF-8 repository name was not preserved'
    );
    assert_repository_test($index['description'] === 'Документы', 'description missing');
    assert_repository_test(
        $index['updated'] === '2023-11-14T22:13:20Z',
        'updated must use the newest indexed mtime in UTC'
    );
    assert_repository_test(
        $index['capabilities'] === ['upload' => false, 'delete' => false, 'mkdir' => false],
        'capabilities must remain read-only'
    );

    $rootNames = array_column($index['items'], 'name');
    assert_repository_test(
        $rootNames === ['hardware', 'linux', 'Alpha', 'root'],
        'root directories and documents are not stably sorted'
    );
    assert_repository_test(!in_array('src', $rootNames, true), 'empty source directory leaked');
    assert_repository_test(!in_array('archive', $rootNames, true), 'non-Markdown file leaked');

    $hardware = $index['items'][0];
    assert_repository_test(
        array_column($hardware['items'], 'name') === ['MikroTik', 'raymarine'],
        'documents are not sorted case-insensitively'
    );
    $advanced = $index['items'][1]['items'][0];
    assert_repository_test($advanced['name'] === 'advanced', 'second-level directory missing');
    assert_repository_test(
        array_column($advanced['items'], 'name') === ['routing', 'сеть'],
        'second-level Markdown documents or UTF-8 names are incorrect'
    );
    assert_repository_test(
        !in_array('deeper', array_column($advanced['items'], 'name'), true),
        'content deeper than two directory levels leaked'
    );

    $encoded = json_encode($index, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
    assert_repository_test(str_contains($encoded, 'Тестовый репозиторий'), 'UTF-8 JSON failed');

    write_repository_fixture($root, 'repository.meta.json', '{"name":"Name only"}');
    $withoutDescription = build_repository_index($root);
    assert_repository_test(
        !array_key_exists('description', $withoutDescription),
        'optional description must remain optional'
    );

    write_repository_fixture($root, 'repository.meta.json', '{broken');
    try {
        build_repository_index($root);
        throw new RuntimeException('invalid metadata was accepted');
    } catch (RuntimeException $error) {
        assert_repository_test(
            $error->getMessage() === 'Repository metadata is invalid',
            'invalid metadata returned an unexpected error'
        );
    }
    http_response_code(200);
    ob_start();
    serve_repository_index($root);
    $errorResponse = ob_get_clean();
    assert_repository_test(http_response_code() === 500, 'server error must use HTTP 500');
    assert_repository_test(
        json_decode($errorResponse, true, 32, JSON_THROW_ON_ERROR)
            === ['error' => 'Repository index is unavailable'],
        'server error response leaked details or used an unexpected format'
    );

    write_repository_fixture($root, 'repository.meta.json', '{"description":"missing name"}');
    try {
        build_repository_index($root);
        throw new RuntimeException('missing metadata name was accepted');
    } catch (RuntimeException $error) {
        assert_repository_test(
            $error->getMessage() === 'Repository metadata requires a name',
            'missing name returned an unexpected error'
        );
    }

    echo "Repository generator tests: OK\n";
} finally {
    remove_repository_fixture($root);
}
