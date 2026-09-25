# Parsedown dependency

MDView Web vendors unmodified `Parsedown.php` version 1.8.0 from
`erusev/parsedown` so deployment does not require Composer on shared hosting.

- Source: <https://github.com/erusev/parsedown/tree/1.8.0>
- License: MIT; see `LICENSE.txt` in this directory.
- Runtime mode: `setSafeMode(true)` and `setStrictMode(true)`.

Do not replace this file with an unpinned branch snapshot. Review release notes
and rerun `php web/tests/run.php` when updating it.
