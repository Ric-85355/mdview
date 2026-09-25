<?php
/*
 * Csrf.php — created 2026-09-25, version 0.1.0.
 * Purpose: issue and validate CSRF tokens tied to the active PHP session.
 * Algorithm: keep a random token server-side and compare submitted values in constant time.
 */

declare(strict_types=1);

namespace Mdview\Web\Auth;

use Mdview\Web\Api\ApiException;

final class Csrf
{
    private const SESSION_KEY = 'mdview_csrf';

    public function token(): string
    {
        $token = $_SESSION[self::SESSION_KEY] ?? null;
        if (!is_string($token) || strlen($token) < 32) {
            $token = bin2hex(random_bytes(32));
            $_SESSION[self::SESSION_KEY] = $token;
        }
        return $token;
    }

    public function validate(?string $submitted): void
    {
        if (!is_string($submitted) || !hash_equals($this->token(), $submitted)) {
            throw new ApiException('csrf_invalid', 403, 'Invalid CSRF token');
        }
    }

    public function rotate(): string
    {
        unset($_SESSION[self::SESSION_KEY]);
        return $this->token();
    }
}
