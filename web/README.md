# MDView Web

This directory contains completed Web MDView stages 1–4 and stage 5A: the PHP foundation,
responsive Repository UI, renderer-independent Reader UI, TOC drawer, document
search, browser-local reading positions, and admin directory creation/single-file
upload. After authentication, the browser
can select any server-authorized repository, navigate folders through
breadcrumbs, search file and folder names, and open Markdown through the
adaptive `DocumentView` Reader. Repository location and list scroll are kept
in browser `localStorage` and restored after reload or return from a document.

The Reader provides its final toolbar structure, document title, loading/error
states, bounded desktop reading width, mobile layout, and neutral content
styles. Contents opens the same left overlay drawer on desktop and mobile,
with H1–H3 depth controls and navigation by `DocumentView.toc` targets. Search
works on displayed document text without a backend request, highlights all
matches, provides cyclic Previous/Next navigation and removes its temporary
markup when cleared or closed. Per-document scroll offsets use a versioned
`localStorage` key built from repository and logical document path. Repository
admins can create one immediate child folder or upload one arbitrary file into
the current folder. Read-only users cannot invoke these actions. Rename, Move,
Delete, overwrite, and multiple/drag-and-drop upload remain unimplemented.

## Layout

```text
web/
├── mdview.php                    # browser entry point
├── mdview-server/
│   ├── api.php                   # internal JSON API
│   ├── bootstrap.php             # configuration and service graph
│   ├── assets/                   # responsive UI plus Repository/Reader state modules
│   ├── config/                   # ignored local config + safe examples
│   ├── src/                      # Auth, Repository, Reader, Renderer, API
│   └── third-party/parsedown/    # pinned Parsedown 1.8.0 (MIT)
└── tests/
    ├── run.php                   # dependency-free backend tests
    ├── http_smoke.php            # real HTTP/session/API smoke test
    ├── login_form_test.js         # asynchronous browser login regression test
    ├── document_search_test.js    # search matching/navigation state tests
    ├── document_search_dom_test.html # real DOM highlight integrity test
    ├── reader_state_test.js       # Reader, DocumentView, and TOC state tests
    ├── reading_position_test.js   # per-document position persistence tests
    ├── repository_mutations_test.js # admin action/payload/refresh tests
    └── repository_state_test.js  # browser-independent client-state tests
```

For Hostinger deployment, place `mdview.php` at `repository-root/mdview.php`
and the complete `mdview-server/` directory at
`repository-root/mdview-server/`. User repositories remain ordinary sibling
directories and are never copied into this Git repository.

Browser-loaded CSS and JavaScript URLs use the shared deterministic version
query from `MDVIEW_WEB_VERSION` in `mdview.php` (currently `?v=0.6.0`). Whenever
the Web version or deployed frontend assets change, update that constant so
browsers and hosting caches request the new asset URLs without a hard refresh.

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
| POST | `create_directory` | admin; CSRF required | Create one direct child directory |
| POST multipart | `upload` | admin; CSRF required | Upload one file without overwrite |

Every write rechecks authentication, repository access, the admin role, CSRF,
and guarded relative paths on the backend. Upload uses the browser filename only
after single-segment validation; MIME type is not trusted. Existing targets return
HTTP 409 and are never overwritten. PHP's configured upload/post-size limits still apply.
The hosting PHP user needs write permission on user repository directories.

## Local verification

Requirements: PHP 8.1+ with DOM, mbstring, JSON, session, and fileinfo. Run:

```console
php web/tests/run.php
php web/tests/http_smoke.php
node web/tests/repository_state_test.js
node web/tests/reader_state_test.js
node web/tests/login_form_test.js
node web/tests/document_search_test.js
node web/tests/reading_position_test.js
node web/tests/repository_mutations_test.js
node --check web/mdview-server/assets/repository-state.js
node --check web/mdview-server/assets/reader-state.js
node --check web/mdview-server/assets/login-form.js
node --check web/mdview-server/assets/document-search.js
node --check web/mdview-server/assets/reading-position.js
node --check web/mdview-server/assets/repository-mutations.js
node --check web/mdview-server/assets/app.js
find web -name '*.php' -print0 | xargs -0 -n1 php -l
```

For a manual run, install local `app.php`/`users.php`, then:

```console
php -S 127.0.0.1:8080 -t web
```

Open `http://127.0.0.1:8080/mdview.php`. The built-in PHP server is for local
verification only. The optional real-DOM regression can be opened directly as
`web/tests/document_search_dom_test.html`; its page title and result text become
`PASS` when transient search marks preserve links, element nesting, and TOC ids.
