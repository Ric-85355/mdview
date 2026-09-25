<?php
/*
 * PathGuard.php — created 2026-09-25, version 0.2.0.
 * Purpose: confine every repository read and write to a configured physical repository root.
 * Algorithm: decode and validate logical paths, resolve existing parents canonically, and
 * accept new objects only as one safe direct child of a contained directory.
 */

declare(strict_types=1);

namespace Mdview\Web\Repository;

use FilesystemIterator;
use Mdview\Web\Api\ApiException;

final class PathGuard
{
    private readonly string $root;

    /** @param list<string> $reservedNames */
    public function __construct(string $repositoryRoot, private readonly array $reservedNames)
    {
        $resolved = realpath($repositoryRoot);
        if ($resolved === false || !is_dir($resolved)) {
            throw new ApiException('configuration_error', 500, 'Repository root is not configured');
        }
        $this->root = rtrim($resolved, DIRECTORY_SEPARATOR);
    }

    /** @return list<string> */
    public function repositoryNames(): array
    {
        $names = [];
        $iterator = new FilesystemIterator($this->root, FilesystemIterator::SKIP_DOTS);
        foreach ($iterator as $entry) {
            $name = $entry->getFilename();
            if (in_array($name, $this->reservedNames, true) || $entry->isLink() || !$entry->isDir()) {
                continue;
            }
            $resolved = $entry->getRealPath();
            if ($resolved !== false && dirname($resolved) === $this->root) {
                $names[] = $name;
            }
        }
        natcasesort($names);
        return array_values($names);
    }

    public function repositoryPath(string $repository): string
    {
        $name = $this->singleSegment($repository, 'Invalid repository name');
        if (in_array($name, $this->reservedNames, true)) {
            throw new ApiException('repository_not_found', 404, 'Repository not found');
        }
        $candidate = $this->root . DIRECTORY_SEPARATOR . $name;
        if (is_link($candidate)) {
            throw new ApiException('repository_not_found', 404, 'Repository not found');
        }
        $resolved = realpath($candidate);
        if ($resolved === false || !is_dir($resolved) || dirname($resolved) !== $this->root) {
            throw new ApiException('repository_not_found', 404, 'Repository not found');
        }
        return $resolved;
    }

    public function resolveExisting(string $repository, string $relativePath): string
    {
        $repositoryPath = $this->repositoryPath($repository);
        $relative = $this->relativePath($relativePath);
        $candidate = $relative === ''
            ? $repositoryPath
            : $repositoryPath . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $relative);
        $resolved = realpath($candidate);
        if ($resolved === false) {
            throw new ApiException('not_found', 404, 'File or directory not found');
        }
        if (!$this->isWithin($resolved, $repositoryPath)) {
            throw new ApiException('path_forbidden', 403, 'Path leaves the repository');
        }
        return $resolved;
    }

    /**
     * Resolve an existing contained parent and one validated child name for a new object.
     *
     * @return array{repository_path: string, parent_path: string, name: string, destination: string}
     */
    public function resolveNewChild(string $repository, string $relativeParent, string $name): array
    {
        $repositoryPath = $this->repositoryPath($repository);
        $parentPath = $this->resolveExisting($repository, $relativeParent);
        if (!is_dir($parentPath)) {
            throw new ApiException('not_a_directory', 400, 'Requested path is not a directory');
        }
        if (!$this->isWithin($parentPath, $repositoryPath)) {
            throw new ApiException('path_forbidden', 403, 'Path leaves the repository');
        }

        $safeName = $this->childName($name);
        return [
            'repository_path' => $repositoryPath,
            'parent_path' => $parentPath,
            'name' => $safeName,
            'destination' => $parentPath . DIRECTORY_SEPARATOR . $safeName,
        ];
    }

    public function relativePath(string $path): string
    {
        $decoded = $path;
        for ($index = 0; $index < 5; $index++) {
            $next = rawurldecode($decoded);
            if ($next === $decoded) {
                break;
            }
            $decoded = $next;
        }
        if (str_contains($decoded, "\0") || str_starts_with($decoded, '/') || str_contains($decoded, '\\')) {
            throw new ApiException('invalid_path', 400, 'Invalid relative path');
        }
        if (preg_match('/%(?:2e|2f|5c)/i', $decoded) === 1) {
            throw new ApiException('invalid_path', 400, 'Invalid encoded path');
        }
        $trimmed = trim($decoded, '/');
        if ($trimmed === '') {
            return '';
        }
        $segments = explode('/', $trimmed);
        foreach ($segments as $segment) {
            if ($segment === '' || $segment === '.' || $segment === '..') {
                throw new ApiException('invalid_path', 400, 'Invalid relative path');
            }
        }
        return implode('/', $segments);
    }

    /** Validate and normalize one UTF-8 basename without accepting a path. */
    public function childName(string $name): string
    {
        if ($name === '' || trim($name) === '' || str_contains($name, '/') || str_contains($name, '\\')) {
            throw new ApiException('invalid_name', 400, 'Name must be one non-empty path segment');
        }
        if (!mb_check_encoding($name, 'UTF-8')) {
            throw new ApiException('invalid_name', 400, 'Name must be valid UTF-8');
        }
        try {
            $normalized = $this->relativePath($name);
        } catch (ApiException $failure) {
            throw new ApiException('invalid_name', 400, 'Name must be one non-empty path segment', $failure);
        }
        if ($normalized === '' || str_contains($normalized, '/')) {
            throw new ApiException('invalid_name', 400, 'Name must be one non-empty path segment');
        }
        return $normalized;
    }

    public function isWithinRepository(string $resolvedPath, string $repositoryPath): bool
    {
        return $this->isWithin($resolvedPath, $repositoryPath);
    }

    private function singleSegment(string $value, string $message): string
    {
        $decoded = $this->relativePath($value);
        if ($decoded === '' || str_contains($decoded, '/')) {
            throw new ApiException('invalid_repository', 400, $message);
        }
        return $decoded;
    }

    private function isWithin(string $candidate, string $parent): bool
    {
        $parent = rtrim($parent, DIRECTORY_SEPARATOR);
        return $candidate === $parent || str_starts_with($candidate, $parent . DIRECTORY_SEPARATOR);
    }
}
