<?php
/*
 * app.example.php — created 2026-09-25, version 0.1.0.
 * Purpose: document non-secret deployment settings without hardcoding a host path.
 * Algorithm: return repository-root and explicit names excluded from Repository discovery.
 */

declare(strict_types=1);

return [
    'repository_root' => '/absolute/path/to/repository-root',
    'reserved_names' => ['mdview-server'],
    'ignored_entry_names' => ['.git', '.svn'],
];
