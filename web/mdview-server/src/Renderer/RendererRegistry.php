<?php
/*
 * RendererRegistry.php — created 2026-09-25, version 0.1.0.
 * Purpose: choose a document renderer without coupling Reader to concrete formats.
 * Algorithm: preserve registration order and return the first renderer supporting an extension.
 */

declare(strict_types=1);

namespace Mdview\Web\Renderer;

use Mdview\Web\Api\ApiException;

final class RendererRegistry
{
    /** @var list<RendererInterface> */
    private array $renderers = [];

    public function register(RendererInterface $renderer): void
    {
        $this->renderers[] = $renderer;
    }

    public function forExtension(string $extension): RendererInterface
    {
        foreach ($this->renderers as $renderer) {
            if ($renderer->supports(strtolower($extension))) {
                return $renderer;
            }
        }
        throw new ApiException('unsupported_document_type', 400, 'Unsupported document type');
    }
}
