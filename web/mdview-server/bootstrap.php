<?php
/*
 * bootstrap.php — created 2026-09-25, version 0.1.0.
 * Purpose: load Web MDView classes, configuration, and stage-one service graph.
 * Algorithm: autoload project namespaces, load explicit root/user configuration,
 * register Markdown renderer, and return isolated backend services to the API entry point.
 */

declare(strict_types=1);

use Mdview\Web\Api\ApiException;
use Mdview\Web\Auth\AuthService;
use Mdview\Web\Auth\Csrf;
use Mdview\Web\Reader\ReaderService;
use Mdview\Web\Renderer\MarkdownRenderer;
use Mdview\Web\Renderer\RendererRegistry;
use Mdview\Web\Repository\PathGuard;
use Mdview\Web\Repository\RepositoryService;

spl_autoload_register(static function (string $class): void {
    $prefix = 'Mdview\\Web\\';
    if (!str_starts_with($class, $prefix)) {
        return;
    }
    $relative = str_replace('\\', '/', substr($class, strlen($prefix)));
    require __DIR__ . '/src/' . $relative . '.php';
});

require_once __DIR__ . '/third-party/parsedown/Parsedown.php';

/** @return array<string, mixed> */
function mdview_load_app_config(): array
{
    $file = __DIR__ . '/config/app.php';
    $config = is_file($file) ? require $file : [];
    if (!is_array($config)) {
        throw new ApiException('configuration_error', 500, 'Invalid application configuration');
    }
    $environmentRoot = getenv('MDVIEW_REPOSITORY_ROOT');
    if (is_string($environmentRoot) && $environmentRoot !== '') {
        $config['repository_root'] = $environmentRoot;
    }
    if (!isset($config['repository_root']) || !is_string($config['repository_root'])) {
        throw new ApiException('configuration_error', 500, 'Repository root is not configured');
    }
    $config['reserved_names'] = array_values($config['reserved_names'] ?? ['mdview-server']);
    $config['ignored_entry_names'] = array_values($config['ignored_entry_names'] ?? ['.git', '.svn']);
    return $config;
}

/** @return array<string, array{password_hash: string, role: string, repositories: list<string>}> */
function mdview_load_users(): array
{
    $environmentFile = getenv('MDVIEW_USERS_FILE');
    $file = is_string($environmentFile) && $environmentFile !== ''
        ? $environmentFile
        : __DIR__ . '/config/users.php';
    if (!is_file($file)) {
        throw new ApiException('configuration_error', 500, 'User configuration is not installed');
    }
    $users = require $file;
    if (!is_array($users)) {
        throw new ApiException('configuration_error', 500, 'Invalid user configuration');
    }
    return $users;
}

/**
 * @return array{auth: AuthService, csrf: Csrf, repositories: RepositoryService, reader: ReaderService}
 */
function mdview_services(): array
{
    $config = mdview_load_app_config();
    $paths = new PathGuard($config['repository_root'], $config['reserved_names']);
    $renderers = new RendererRegistry();
    $renderers->register(new MarkdownRenderer());

    return [
        'auth' => new AuthService(mdview_load_users()),
        'csrf' => new Csrf(),
        'repositories' => new RepositoryService($paths, $config['ignored_entry_names']),
        'reader' => new ReaderService($paths, $renderers),
    ];
}
