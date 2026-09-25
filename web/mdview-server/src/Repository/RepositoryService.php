<?php
/*
 * RepositoryService.php — created 2026-09-25, version 0.1.0.
 * Purpose: expose read-only repository discovery, directory listing, and name search.
 * Algorithm: enumerate guarded canonical paths, skip service/unsafe objects, and return
 * only logical names and repository-relative paths with directories ordered before files.
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
