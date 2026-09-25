<?php
/*
 * JsonResponse.php — created 2026-09-25, version 0.1.0.
 * Purpose: provide one JSON envelope for every Web MDView API response.
 * Algorithm: build deterministic success/error payloads and emit UTF-8 JSON with an HTTP status.
 */

declare(strict_types=1);

namespace Mdview\Web\Api;

final class JsonResponse
{
    /** @return array{success: true, data: mixed} */
    public static function successPayload(mixed $data): array
    {
        return ['success' => true, 'data' => $data];
    }

    /** @return array{success: false, error: array{code: string, message: string}} */
    public static function errorPayload(string $code, string $message): array
    {
        return ['success' => false, 'error' => ['code' => $code, 'message' => $message]];
    }

    public static function sendSuccess(mixed $data, int $status = 200): never
    {
        self::send(self::successPayload($data), $status);
    }

    public static function sendError(string $code, string $message, int $status): never
    {
        self::send(self::errorPayload($code, $message), $status);
    }

    /** @param array<string, mixed> $payload */
    private static function send(array $payload, int $status): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        echo json_encode(
            $payload,
            JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE,
        );
        exit;
    }
}
