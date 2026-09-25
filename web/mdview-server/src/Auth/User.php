<?php
/*
 * User.php — created 2026-09-25, version 0.1.0.
 * Purpose: represent validated server-side identity, role, and repository grants.
 * Algorithm: normalize configured grants and answer read/write authorization questions.
 */

declare(strict_types=1);

namespace Mdview\Web\Auth;

use InvalidArgumentException;

final class User
{
    /** @param list<string> $repositories */
    public function __construct(
        public readonly string $username,
        public readonly string $role,
        public readonly array $repositories,
    ) {
        if (!in_array($role, ['admin', 'user'], true)) {
            throw new InvalidArgumentException('Unsupported user role');
        }
    }

    public function canAccessRepository(string $repository): bool
    {
        return $this->role === 'admin'
            || in_array('*', $this->repositories, true)
            || in_array($repository, $this->repositories, true);
    }

    public function canWrite(): bool
    {
        return $this->role === 'admin';
    }

    /** @return array{username: string, role: string, repositories: list<string>, can_write: bool} */
    public function toArray(): array
    {
        return [
            'username' => $this->username,
            'role' => $this->role,
            'repositories' => $this->repositories,
            'can_write' => $this->canWrite(),
        ];
    }
}
