# Dynamic repository index deployment

Upload the contents of `server/mdrepo/` (`.htaccess`, `repository.php`, and
`repository.meta.json`) into the root of the Hostinger `mdrepo` directory.
Keep Markdown documents in that directory or at most two directory levels
below it. Do not upload this deployment README into `mdrepo`, because every
`.md` file there is intentionally indexed as a document.

The local `.htaccess` rewrites only `repository.json` to `repository.php`.
Markdown files remain normal static HTTP resources. The metadata file supplies
the repository name and optional description; the filesystem supplies all
directories and documents.

Before deployment, remove or rename the old static `repository.json`. Then
verify the dynamic endpoint:

```console
curl -i http://ricaro.top/mdrepo/repository.json
```

A successful response has status `200`, content type
`application/json; charset=utf-8`, format `1`, read-only capabilities, and an
`items` tree generated from `.md` files. Direct URLs such as
`http://ricaro.top/mdrepo/hardware/mikrotik.md` continue to work.

Missing or invalid metadata, including a missing non-empty `name`, produces an
HTTP `500` JSON error. `description` is optional. Symlinks, non-Markdown files,
empty/non-document directories, and content below the second directory level
are not indexed.

Run the deterministic local checks from the project root:

```console
php -l server/mdrepo/repository.php
php tests/test_repository_generator.php
php -S 127.0.0.1:8080 -t server/mdrepo
curl -i http://127.0.0.1:8080/repository.php
```

PHP's built-in server verifies the generator and HTTP response but does not
apply `.htaccess`. Verify the `repository.json` rewrite after uploading to
Hostinger. If Hostinger caches the old static response, purge its cache before
the final check.
