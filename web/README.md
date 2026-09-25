# MDView Web — stage 1 foundation

This directory contains the first Web MDView implementation. It intentionally
provides only a technical UI for verifying authentication, Repository reads,
and Markdown `DocumentView` rendering. Final Repository/Reader interfaces,
document search, localStorage state, and all repository mutations belong to
later stages in `docs/MD-WEB-implementation-plan.md`.

## Layout

```text
web/
├── mdview.php                    # browser entry point
├── mdview-server/
│   ├── api.php                   # internal JSON API
│   ├── bootstrap.php             # configuration and service graph
│   ├── assets/                   # CSS and vanilla JavaScript
│   ├── config/                   # ignored local config + safe examples
│   ├── src/                      # Auth, Repository, Reader, Renderer, API
│   └── third-party/parsedown/    # pinned Parsedown 1.8.0 (MIT)
└── tests/run.php               # dependency-free foundation tests
```

For Hostinger deployment, place `mdview.php` at `repository-root/mdview.php`
and the complete `mdview-server/` directory at
`repository-root/mdview-server/`. User repositories remain ordinary sibling
directories and are never copied into this Git repository.

## Configuration

The backend requires an explicit repository root. Either copy
`mdview-server/config/app.example.php` to `app.php` and set the absolute
deployment path, or set `MDVIEW_REPOSITORY_ROOT`. `app.php` is ignored by Git.

Copy `users.example.php` to `users.php`. Replace every placeholder with a hash
created on the deployment machine, never with a plaintext password:

```console
php -r 'echo password_hash("choose-a-password", PASSWORD_DEFAULT), PHP_EOL;'
```

Roles are `admin` and `user`. An admin can access every user repository and is
the only write-capable role. A user is read-only and receives a repository list,
for example `['English', 'Boats']`; `['*']` grants read access to all user
repositories. Only username is retained in PHP session. Role and grants are
reloaded from `users.php` on each request.

The local `.htaccess` prevents direct Apache access to config, source, bootstrap,
and third-party directories. Equivalent server rules are required if deployment
does not honor Apache `.htaccess`.

## API

All responses use one envelope:

```json
{"success":true,"data":{}}
```

or:

```json
{"success":false,"error":{"code":"machine_code","message":"Safe message"}}
```

Implemented actions:

| Method | `action` | Authentication | Purpose |
|---|---|---|---|
| GET | `csrf` | no | Issue session CSRF token |
| POST | `login` | no; CSRF required | Verify hash and create session |
| POST | `logout` | yes; CSRF required | End authenticated session |
| GET | `session` | yes | Current user and CSRF token |
| GET | `repositories` | yes | Accessible repositories |
| GET | `directory` | yes | List `repository` + relative `path` |
| GET | `search` | yes | Name-only search using `query` |
| GET | `document` | yes | Render Markdown `DocumentView` |

CSRF infrastructure is ready for all future mutating endpoints. No mutation
endpoint is implemented in stage 1.

## Local verification

Requirements: PHP 8.1+ with DOM, mbstring, JSON, session, and fileinfo. Run:

```console
php web/tests/run.php
php web/tests/http_smoke.php
find web -name '*.php' -print0 | xargs -0 -n1 php -l
```

For a manual run, install local `app.php`/`users.php`, then:

```console
php -S 127.0.0.1:8080 -t web
```

Open `http://127.0.0.1:8080/mdview.php`. The built-in PHP server is for local
verification only.
