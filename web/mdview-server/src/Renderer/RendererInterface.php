<?php
/*
 * RendererInterface.php — created 2026-09-25, version 0.1.0.
 * Purpose: isolate format-specific parsing behind the common DocumentView contract.
 * Algorithm: select by extension and transform one canonical source file into DocumentView.
 */

declare(strict_types=1);

namespace Mdview\Web\Renderer;

use Mdview\Web\Reader\DocumentView;

interface RendererInterface
{
    public function supports(string $extension): bool;

    public function render(string $canonicalPath, string $displayName): DocumentView;
}
