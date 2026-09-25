<?php
/*
 * ReaderService.php — created 2026-09-25, version 0.1.0.
 * Purpose: open an authorized logical document through the registered renderer pipeline.
 * Algorithm: resolve a guarded regular file, select by extension, and return DocumentView.
 */

declare(strict_types=1);

namespace Mdview\Web\Reader;

use Mdview\Web\Api\ApiException;
use Mdview\Web\Renderer\RendererRegistry;
use Mdview\Web\Repository\PathGuard;

final class ReaderService
{
    public function __construct(
        private readonly PathGuard $paths,
        private readonly RendererRegistry $renderers,
    ) {
    }

    public function open(string $repository, string $relativePath): DocumentView
    {
        $normalized = $this->paths->relativePath($relativePath);
        if ($normalized === '') {
            throw new ApiException('invalid_document', 400, 'Document path is required');
        }
        $canonical = $this->paths->resolveExisting($repository, $normalized);
        if (!is_file($canonical) || !is_readable($canonical)) {
            throw new ApiException('document_not_found', 404, 'Document not found');
        }
        $extension = strtolower(pathinfo($canonical, PATHINFO_EXTENSION));
        return $this->renderers->forExtension($extension)->render($canonical, basename($normalized));
    }
}
