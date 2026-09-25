<?php
/*
 * ApiException.php — created 2026-09-25, version 0.1.0.
 * Purpose: carry a public API error code and HTTP status without exposing internals.
 * Algorithm: retain safe client-facing fields while preserving Throwable chaining for logs.
 */

declare(strict_types=1);

namespace Mdview\Web\Api;

use RuntimeException;
use Throwable;

final class ApiException extends RuntimeException
{
    public function __construct(
        public readonly string $apiCode,
        public readonly int $httpStatus,
        string $message,
        ?Throwable $previous = null,
    ) {
        parent::__construct($message, 0, $previous);
    }

    public static function conflict(string $message = 'Object already exists'): self
    {
        return new self('conflict', 409, $message);
    }
}
