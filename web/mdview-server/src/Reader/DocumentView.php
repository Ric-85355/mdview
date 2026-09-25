<?php
/*
 * DocumentView.php — created 2026-09-25, version 0.1.0.
 * Purpose: define the renderer-independent Reader response contract.
 * Algorithm: validate and serialize title, HTML fragment, uniform TOC items, and metadata.
 */

declare(strict_types=1);

namespace Mdview\Web\Reader;

use InvalidArgumentException;

final class DocumentView
{
    /**
     * @param list<array{level: int, title: string, target: string}> $toc
     * @param array<string, mixed> $metadata
     */
    public function __construct(
        public readonly string $title,
        public readonly string $content,
        public readonly array $toc,
        public readonly array $metadata,
    ) {
        foreach ($toc as $item) {
            if (!isset($item['level'], $item['title'], $item['target'])) {
                throw new InvalidArgumentException('Invalid TOC item');
            }
        }
    }

    /**
     * @return array{title: string, content: string, toc: list<array{level: int, title: string, target: string}>, metadata: array<string, mixed>}
     */
    public function toArray(): array
    {
        return [
            'title' => $this->title,
            'content' => $this->content,
            'toc' => $this->toc,
            'metadata' => $this->metadata,
        ];
    }
}
