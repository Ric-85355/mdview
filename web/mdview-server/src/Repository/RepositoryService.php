<?php
/*
 * RepositoryService.php — created 2026-09-25, version 0.2.0.
 * Purpose: expose repository reads plus safe direct-child directory creation and upload.
 * Algorithm: enumerate guarded paths for reads; for writes resolve a canonical parent,
 * validate one basename, and create destinations exclusively so existing data is untouched.
 */

declare(strict_types=1);

namespace Mdview\Web\Repository;

use Mdview\Web\Api\ApiException;

final class RepositoryService
{
    /** @param list<string> $ignoredEntryNames */
    public function __construct(
        private readonly PathGuard $paths,
        private readonly array $ignoredEntryNames = ['.git', '.svn'],
    ) {
    }

    /**
     * @param callable(string): bool $canAccess
     * @return list<array{id: string, name: string}>
     */
    public function listRepositories(callable $canAccess): array
    {
        $repositories = [];
        foreach ($this->paths->repositoryNames() as $name) {
            if ($canAccess($name)) {
                $repositories[] = ['id' => $name, 'name' => $name];
            }
        }
        return $repositories;
    }

    /**
     * @return array{repository: string, path: string, entries: list<array<string, mixed>>}
     */
    public function listDirectory(string $repository, string $relativePath): array
    {
        $normalized = $this->paths->relativePath($relativePath);
        $repositoryPath = $this->paths->repositoryPath($repository);
        $directory = $this->paths->resolveExisting($repository, $normalized);
        if (!is_dir($directory)) {
            throw new ApiException('not_a_directory', 400, 'Requested path is not a directory');
        }

        $entries = [];
        foreach (new \FilesystemIterator($directory, \FilesystemIterator::SKIP_DOTS) as $entry) {
            if (in_array($entry->getFilename(), $this->ignoredEntryNames, true)) {
                continue;
            }
            $resolved = $entry->getRealPath();
            if ($resolved === false || !$this->paths->isWithinRepository($resolved, $repositoryPath)) {
                continue;
            }
            $type = is_dir($resolved)
                ? 'directory'
                : ($this->isMarkdown($entry->getFilename()) ? 'document' : 'file');
            if ($type === 'file' && !is_file($resolved)) {
                continue;
            }
            $path = $normalized === '' ? $entry->getFilename() : $normalized . '/' . $entry->getFilename();
            $entries[] = [
                'type' => $type,
                'name' => $entry->getFilename(),
                'path' => $path,
                'readable' => $type === 'document',
            ];
        }
        usort($entries, self::compareEntries(...));

        return ['repository' => $repository, 'path' => $normalized, 'entries' => $entries];
    }

    /** @return list<array{type: string, name: string, path: string, readable: bool}> */
    public function search(string $repository, string $query, int $limit = 200): array
    {
        $needle = trim($query);
        if ($needle === '') {
            throw new ApiException('invalid_query', 400, 'Search query must not be empty');
        }

        $repositoryPath = $this->paths->repositoryPath($repository);
        $stack = [['directory' => $repositoryPath, 'relative' => '']];
        $visited = [];
        $results = [];

        while ($stack !== [] && count($results) < $limit) {
            $current = array_pop($stack);
            $canonicalDirectory = realpath($current['directory']);
            if ($canonicalDirectory === false || isset($visited[$canonicalDirectory])) {
                continue;
            }
            $visited[$canonicalDirectory] = true;

            foreach (new \FilesystemIterator($canonicalDirectory, \FilesystemIterator::SKIP_DOTS) as $entry) {
                $name = $entry->getFilename();
                if (in_array($name, $this->ignoredEntryNames, true)) {
                    continue;
                }
                $resolved = $entry->getRealPath();
                if ($resolved === false || !$this->paths->isWithinRepository($resolved, $repositoryPath)) {
                    continue;
                }
                $relative = $current['relative'] === '' ? $name : $current['relative'] . '/' . $name;
                $isDirectory = is_dir($resolved);
                if (mb_stripos($name, $needle, 0, 'UTF-8') !== false) {
                    $results[] = [
                        'type' => $isDirectory ? 'directory' : ($this->isMarkdown($name) ? 'document' : 'file'),
                        'name' => $name,
                        'path' => $relative,
                        'readable' => !$isDirectory && $this->isMarkdown($name),
                    ];
                    if (count($results) >= $limit) {
                        break;
                    }
                }
                if ($isDirectory) {
                    $stack[] = ['directory' => $resolved, 'relative' => $relative];
                }
            }
        }

        usort($results, static fn (array $left, array $right): int => strnatcasecmp($left['path'], $right['path']));
        return $results;
    }

    /**
     * Create one direct child directory without replacing an existing object.
     *
     * @return array{repository: string, path: string, name: string}
     */
    public function createDirectory(string $repository, string $relativeParent, string $name): array
    {
        $target = $this->newTarget($repository, $relativeParent, $name);
        if (file_exists($target['destination']) || is_link($target['destination'])) {
            throw ApiException::conflict('An object with this name already exists');
        }
        if (!@mkdir($target['destination'], 0777, false)) {
            if (file_exists($target['destination']) || is_link($target['destination'])) {
                throw ApiException::conflict('An object with this name already exists');
            }
            throw new ApiException('create_failed', 500, 'Could not create directory');
        }

        $created = realpath($target['destination']);
        if ($created === false || !$this->paths->isWithinRepository($created, $target['repository_path'])) {
            @rmdir($target['destination']);
            throw new ApiException('create_failed', 500, 'Could not create directory');
        }
        return [
            'repository' => $repository,
            'path' => $this->joinRelative($this->paths->relativePath($relativeParent), $target['name']),
            'name' => $target['name'],
        ];
    }

    /**
     * Copy one verified PHP upload into an exclusively created direct child file.
     *
     * @param callable(string): bool|null $isUploadedFile Test seam; production uses is_uploaded_file().
     * @return array{repository: string, path: string, name: string}
     */
    public function upload(
        string $repository,
        string $relativeParent,
        string $name,
        string $temporaryPath,
        int $uploadError,
        ?callable $isUploadedFile = null,
    ): array {
        if ($uploadError !== UPLOAD_ERR_OK) {
            throw new ApiException('upload_failed', 400, self::uploadErrorMessage($uploadError));
        }
        $checker = $isUploadedFile ?? is_uploaded_file(...);
        if ($temporaryPath === '' || !$checker($temporaryPath) || !is_file($temporaryPath)) {
            throw new ApiException('invalid_upload', 400, 'Uploaded file is invalid');
        }

        $target = $this->newTarget($repository, $relativeParent, $name);
        $source = @fopen($temporaryPath, 'rb');
        if ($source === false) {
            throw new ApiException('upload_failed', 500, 'Could not read uploaded file');
        }
        $destination = @fopen($target['destination'], 'xb');
        if ($destination === false) {
            fclose($source);
            if (file_exists($target['destination']) || is_link($target['destination'])) {
                throw ApiException::conflict('An object with this name already exists');
            }
            throw new ApiException('upload_failed', 500, 'Could not create uploaded file');
        }

        $copied = false;
        try {
            $copied = stream_copy_to_stream($source, $destination) !== false && fflush($destination);
        } finally {
            fclose($source);
            fclose($destination);
        }
        if (!$copied) {
            @unlink($target['destination']);
            throw new ApiException('upload_failed', 500, 'Could not store uploaded file');
        }

        return [
            'repository' => $repository,
            'path' => $this->joinRelative($this->paths->relativePath($relativeParent), $target['name']),
            'name' => $target['name'],
        ];
    }

    /** @return array{repository_path: string, parent_path: string, name: string, destination: string} */
    private function newTarget(string $repository, string $relativeParent, string $name): array
    {
        $target = $this->paths->resolveNewChild($repository, $relativeParent, $name);
        if (in_array($target['name'], $this->ignoredEntryNames, true)) {
            throw new ApiException('invalid_name', 400, 'This name is reserved');
        }
        return $target;
    }

    private function joinRelative(string $parent, string $name): string
    {
        return $parent === '' ? $name : $parent . '/' . $name;
    }

    private static function uploadErrorMessage(int $error): string
    {
        return match ($error) {
            UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE => 'Uploaded file is too large',
            UPLOAD_ERR_PARTIAL => 'File upload was incomplete',
            UPLOAD_ERR_NO_FILE => 'No file was uploaded',
            default => 'File upload failed',
        };
    }

    private function isMarkdown(string $name): bool
    {
        return strtolower(pathinfo($name, PATHINFO_EXTENSION)) === 'md';
    }

    /** @param array<string, mixed> $left @param array<string, mixed> $right */
    private static function compareEntries(array $left, array $right): int
    {
        $leftDirectory = $left['type'] === 'directory';
        $rightDirectory = $right['type'] === 'directory';
        if ($leftDirectory !== $rightDirectory) {
            return $leftDirectory ? -1 : 1;
        }
        return strnatcasecmp((string) $left['name'], (string) $right['name']);
    }
}
