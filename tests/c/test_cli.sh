#!/bin/sh
# test_cli.sh — created 2026-08-26, version 0.2.3.
# Purpose: regression-test the non-curses mdview-c command-line interface.
# Algorithm: invoke help, version, and error paths and validate status/text.

set -eu

program=$1

help_output=$($program --help)
printf '%s' "$help_output" | grep -F "usage: mdview-c [-h] [--version] file" >/dev/null
printf '%s' "$help_output" | grep -F "View a Markdown file in the terminal" >/dev/null

version_output=$($program --version)
test "$version_output" = "mdview-c 0.2.3"

set +e
missing_output=$($program 2>&1)
missing_status=$?
file_output=$($program /definitely/missing-mdview-file 2>&1)
file_status=$?
set -e

test "$missing_status" -eq 2
printf '%s' "$missing_output" \
    | grep -F "the following arguments are required: file" >/dev/null
test "$file_status" -eq 1
printf '%s' "$file_output" \
    | grep -F "file does not exist: /definitely/missing-mdview-file" >/dev/null

printf '%s\n' "mdview-c CLI tests: OK"
