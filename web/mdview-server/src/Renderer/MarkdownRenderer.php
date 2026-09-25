<?php
/*
 * MarkdownRenderer.php — created 2026-09-25, version 0.1.0.
 * Purpose: render UTF-8 Markdown as safe HTML and a renderer-independent DocumentView.
 * Algorithm: parse in Parsedown safe/strict mode, annotate headings with stable unique
 * Unicode slugs through DOM, derive title/TOC, and expose non-sensitive file metadata.
 */

declare(strict_types=1);

namespace Mdview\Web\Renderer;

use DOMDocument;
use DOMElement;
use DOMXPath;
use Mdview\Web\Api\ApiException;
use Mdview\Web\Reader\DocumentView;
use Parsedown;

final class MarkdownRenderer implements RendererInterface
{
    public function supports(string $extension): bool
    {
        return strtolower($extension) === 'md';
    }

    public function render(string $canonicalPath, string $displayName): DocumentView
    {
        $markdown = file_get_contents($canonicalPath);
        if ($markdown === false) {
            throw new ApiException('document_read_failed', 500, 'Could not read document');
        }
        if (!mb_check_encoding($markdown, 'UTF-8')) {
            throw new ApiException('invalid_encoding', 400, 'Markdown document must be UTF-8');
        }

        $parser = (new Parsedown())->setSafeMode(true)->setStrictMode(true);
        $fragment = $parser->text($markdown);
        [$content, $toc, $headingTitle] = $this->annotateHeadings($fragment);
        $title = $headingTitle ?? $displayName;

        return new DocumentView(
            $title,
            $content,
            $toc,
            [
                'type' => 'markdown',
                'extension' => 'md',
                'size' => filesize($canonicalPath) ?: 0,
                'modified' => gmdate(DATE_ATOM, filemtime($canonicalPath) ?: 0),
                'encoding' => 'UTF-8',
            ],
        );
    }

    /**
     * @return array{0: string, 1: list<array{level: int, title: string, target: string}>, 2: ?string}
     */
    private function annotateHeadings(string $fragment): array
    {
        $document = new DOMDocument('1.0', 'UTF-8');
        $previousErrors = libxml_use_internal_errors(true);
        $loaded = $document->loadHTML(
            '<?xml encoding="UTF-8"><div id="mdview-root">' . $fragment . '</div>',
            LIBXML_HTML_NOIMPLIED | LIBXML_HTML_NODEFDTD,
        );
        libxml_clear_errors();
        libxml_use_internal_errors($previousErrors);
        if (!$loaded) {
            throw new ApiException('render_failed', 500, 'Could not render Markdown document');
        }

        $root = null;
        foreach ($document->getElementsByTagName('div') as $element) {
            if ($element->getAttribute('id') === 'mdview-root') {
                $root = $element;
                break;
            }
        }
        if (!$root instanceof DOMElement) {
            throw new ApiException('render_failed', 500, 'Could not build document fragment');
        }

        $toc = [];
        $title = null;
        $usedTargets = [];
        $headingNodes = (new DOMXPath($document))->query(
            './/h1 | .//h2 | .//h3 | .//h4 | .//h5 | .//h6',
            $root,
        );
        if ($headingNodes !== false) {
            foreach ($headingNodes as $heading) {
                $level = (int) substr($heading->nodeName, 1);
                $headingTitle = trim((string) preg_replace('/\s+/u', ' ', $heading->textContent));
                if ($title === null && $headingTitle !== '') {
                    $title = $headingTitle;
                }
                $target = $this->uniqueTarget($headingTitle, $usedTargets);
                $heading->setAttribute('id', $target);
                if ($level <= 3) {
                    $toc[] = ['level' => $level, 'title' => $headingTitle, 'target' => $target];
                }
            }
        }

        $content = '';
        foreach (iterator_to_array($root->childNodes) as $child) {
            $content .= $document->saveHTML($child);
        }
        return [$content, $toc, $title];
    }

    /** @param array<string, int> $usedTargets */
    private function uniqueTarget(string $title, array &$usedTargets): string
    {
        $base = mb_strtolower($title, 'UTF-8');
        $base = trim((string) preg_replace('/[^\p{L}\p{N}]+/u', '-', $base), '-');
        if ($base === '') {
            $base = 'section';
        }
        $count = ($usedTargets[$base] ?? 0) + 1;
        $usedTargets[$base] = $count;
        return $count === 1 ? $base : $base . '-' . $count;
    }
}
