<?php
/*
 * repository.php — created 2026-08-30, version 0.3.0.
 * Purpose: generate a read-only Markdown repository index on shared hosting.
 * Algorithm: validate local metadata, recursively collect .md files through
 * two directory levels, sort them, and emit UTF-8 JSON with the newest mtime.
 */

declare(strict_types=1);

const REPOSITORY_FORMAT = 1;
const MAX_DIRECTORY_DEPTH = 2;

/**
 * Compare repository names case-insensitively with a stable binary tie-breaker.
 */
function compare_repository_names(string $left, string $right): int
{
    $folded = strcasecmp($left, $right);
    return $folded !== 0 ? $folded : strcmp($left, $right);
}

/**
 * Read and validate repository.meta.json from the repository root.
 *
 * @return array{name: string, description?: string}
 */
function read_repository_metadata(string $root): array
{
    $path = $root . DIRECTORY_SEPARATOR . 'repository.meta.json';
    $source = @file_get_contents($path);
    if ($source === false) {
        throw new RuntimeException('Repository metadata is unavailable');
    }

    try {
        $metadata = json_decode($source, true, 32, JSON_THROW_ON_ERROR);
    } catch (JsonException $error) {
        throw new RuntimeException('Repository metadata is invalid', 0, $error);
    }
    if (!is_array($metadata)) {
        throw new RuntimeException('Repository metadata must be an object');
    }

    $name = $metadata['name'] ?? null;
    if (!is_string($name) || trim($name) === '') {
        throw new RuntimeException('Repository metadata requires a name');
    }

    $result = ['name' => trim($name)];
    if (array_key_exists('description', $metadata)) {
        if (!is_string($metadata['description'])) {
            throw new RuntimeException('Repository description must be a string');
        }
        $result['description'] = $metadata['description'];
    }
    return $result;
}

/**
 * Return a filesystem mtime or fail without leaking filesystem details.
 */
function repository_mtime(string $path): int
{
    $timestamp = @filemtime($path);
    if ($timestamp === false) {
        throw new RuntimeException('Could not read repository modification time');
    }
    return $timestamp;
}

/**
 * Collect indexable children of one directory without following symlinks.
 *
 * @return array{items: list<array<string, mixed>>, updated: int}
 */
function scan_repository_directory(
    string $root,
    string $relativeDirectory = '',
    int $depth = 0
): array {
    $directory = $relativeDirectory === ''
        ? $root
        : $root . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $relativeDirectory);
    $entries = @scandir($directory);
    if ($entries === false) {
        throw new RuntimeException('Could not scan repository directory');
    }

    $directories = [];
    $documents = [];
    $updated = 0;
    foreach ($entries as $entry) {
        if ($entry === '.' || $entry === '..') {
            continue;
        }
        $absolutePath = $directory . DIRECTORY_SEPARATOR . $entry;
        if (is_link($absolutePath)) {
            continue;
        }
        $relativePath = $relativeDirectory === ''
            ? $entry
            : $relativeDirectory . '/' . $entry;

        if (is_dir($absolutePath)) {
            if ($depth >= MAX_DIRECTORY_DEPTH) {
                continue;
            }
            $child = scan_repository_directory($root, $relativePath, $depth + 1);
            if ($child['items'] !== []) {
                $directories[] = [
                    'type' => 'directory',
                    'name' => $entry,
                    'items' => $child['items'],
                ];
                $updated = max(
                    $updated,
                    $child['updated'],
                    repository_mtime($absolutePath)
                );
            }
            continue;
        }

        if (!is_file($absolutePath) || strtolower(pathinfo($entry, PATHINFO_EXTENSION)) !== 'md') {
            continue;
        }
        $documents[] = [
            'type' => 'document',
            'name' => pathinfo($entry, PATHINFO_FILENAME),
            'path' => str_replace(DIRECTORY_SEPARATOR, '/', $relativePath),
        ];
        $updated = max($updated, repository_mtime($absolutePath));
    }

    usort(
        $directories,
        static fn(array $left, array $right): int => compare_repository_names(
            $left['name'],
            $right['name']
        )
    );
    usort(
        $documents,
        static fn(array $left, array $right): int => compare_repository_names(
            $left['name'],
            $right['name']
        )
    );
    return ['items' => array_merge($directories, $documents), 'updated' => $updated];
}

/**
 * Build the complete format-1 repository index for a trusted local root.
 *
 * @return array<string, mixed>
 */
function build_repository_index(string $root): array
{
    $metadata = read_repository_metadata($root);
    $scan = scan_repository_directory($root);
    $updated = max(
        $scan['updated'],
        repository_mtime($root . DIRECTORY_SEPARATOR . 'repository.meta.json')
    );

    $index = [
        'format' => REPOSITORY_FORMAT,
        'name' => $metadata['name'],
    ];
    if (array_key_exists('description', $metadata)) {
        $index['description'] = $metadata['description'];
    }
    $index['updated'] = gmdate('Y-m-d\TH:i:s\Z', $updated);
    $index['capabilities'] = [
        'upload' => false,
        'delete' => false,
        'mkdir' => false,
    ];
    $index['items'] = $scan['items'];
    return $index;
}

/**
 * Emit one JSON response while hiding exceptions and server paths from users.
 */
function serve_repository_index(string $root): void
{
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-cache, no-store, must-revalidate');
    try {
        $index = build_repository_index($root);
        echo json_encode(
            $index,
            JSON_PRETTY_PRINT
                | JSON_UNESCAPED_SLASHES
                | JSON_UNESCAPED_UNICODE
                | JSON_THROW_ON_ERROR
        );
        echo "\n";
    } catch (Throwable $error) {
        http_response_code(500);
        error_log('mdview repository index error: ' . $error->getMessage());
        echo json_encode(
            ['error' => 'Repository index is unavailable'],
            JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR
        );
        echo "\n";
    }
}

if (realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) {
    serve_repository_index(__DIR__);
}
