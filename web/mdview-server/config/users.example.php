<?php
/*
 * users.example.php — created 2026-09-25, version 0.1.0.
 * Purpose: show the user/role/repository schema without publishing usable credentials.
 * Algorithm: return disabled placeholder hashes that deployers must replace with password_hash().
 */

declare(strict_types=1);

return [
    'admin' => [
        'password_hash' => 'REPLACE_WITH_PASSWORD_HASH',
        'role' => 'admin',
        'repositories' => ['*'],
    ],
    'reader' => [
        'password_hash' => 'REPLACE_WITH_PASSWORD_HASH',
        'role' => 'user',
        'repositories' => ['Example Repository'],
    ],
];
