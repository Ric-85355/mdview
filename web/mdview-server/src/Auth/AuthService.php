<?php
/*
 * AuthService.php — created 2026-09-25, version 0.1.0.
 * Purpose: authenticate configured users and enforce role/repository permissions.
 * Algorithm: verify password hashes, store only username in session, and reload grants per request.
 */

declare(strict_types=1);

namespace Mdview\Web\Auth;

use Mdview\Web\Api\ApiException;

final class AuthService
{
    private const SESSION_USER = 'mdview_username';

    /**
     * @param array<string, array{password_hash: string, role: string, repositories: list<string>}> $users
     */
    public function __construct(private readonly array $users)
    {
    }

    public function login(string $username, string $password): User
    {
        $record = $this->users[$username] ?? null;
        if ($record === null || !password_verify($password, $record['password_hash'] ?? '')) {
            throw new ApiException('invalid_credentials', 401, 'Invalid username or password');
        }

        session_regenerate_id(true);
        $_SESSION[self::SESSION_USER] = $username;
        return $this->userFromRecord($username, $record);
    }

    public function logout(): void
    {
        unset($_SESSION[self::SESSION_USER]);
        session_regenerate_id(true);
    }

    public function currentUser(): ?User
    {
        $username = $_SESSION[self::SESSION_USER] ?? null;
        if (!is_string($username) || !isset($this->users[$username])) {
            return null;
        }
        return $this->userFromRecord($username, $this->users[$username]);
    }

    public function requireUser(): User
    {
        return $this->currentUser()
            ?? throw new ApiException('authentication_required', 401, 'Authentication required');
    }

    public function requireRepository(string $repository): User
    {
        $user = $this->requireUser();
        if (!$user->canAccessRepository($repository)) {
            throw new ApiException('repository_forbidden', 403, 'Repository access denied');
        }
        return $user;
    }

    public function requireWrite(string $repository): User
    {
        $user = $this->requireRepository($repository);
        if (!$user->canWrite()) {
            throw new ApiException('operation_forbidden', 403, 'This operation requires the admin role');
        }
        return $user;
    }

    /** @param array{password_hash: string, role: string, repositories: list<string>} $record */
    private function userFromRecord(string $username, array $record): User
    {
        return new User($username, $record['role'], array_values($record['repositories']));
    }
}
