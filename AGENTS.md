# AGENTS.md

## Purpose

This file defines the default working rules for AI coding agents operating in this repository.

The goal is to make changes safely, minimally, and predictably while preserving the existing architecture, behavior, and project conventions.

---

## 1. Start by understanding the project

Before modifying any code:

1. Read this `AGENTS.md`.
2. Read the main project documentation, especially:
   - `README.md`
   - `CONTRIBUTING.md`
   - architecture/design documentation
   - build and test instructions
3. If any of the following files exist, read them before analysis or changes:
   - `AI_CONTEXT.md`
   - `docs/AI_CONTEXT.md`
   - `ARCHITECTURE.md`
   - `docs/ARCHITECTURE.md`
4. Inspect the repository structure and identify the files directly related to the task.
5. Check the current Git branch and working tree status.
6. Do not assume that the newest-looking file or branch is the correct source of truth. Prefer the documentation and code that belong to the current branch.

If project-specific instructions conflict with this file, the more specific project instruction takes priority.

---

## 2. Do not change code before understanding the cause

For bug-fixing tasks, first determine:

- the actual cause of the problem;
- the code path involved;
- the relevant files, classes, functions, or modules;
- the current behavior;
- the expected behavior;
- the smallest safe place to make the correction.

Do not start with speculative refactoring.

When asked only to analyze a problem, do not modify files.

---

## 3. Prefer minimal changes

Make the smallest change that fully solves the requested problem.

Avoid:

- unrelated cleanup;
- stylistic rewrites;
- large refactoring unless required;
- renaming unrelated symbols;
- reformatting entire files;
- replacing working subsystems without need;
- adding new dependencies unless justified.

Preserve existing public behavior unless the task explicitly requires changing it.

---

## 4. Preserve project conventions

Follow the repository's existing conventions for:

- code style;
- naming;
- directory structure;
- architecture;
- error handling;
- logging;
- configuration;
- localization;
- tests;
- documentation.

Before introducing a new pattern, check whether the project already has an established equivalent.

---

## 5. Respect existing user changes

Before editing files, inspect the working tree.

Do not overwrite or revert unrelated user changes.

Do not use destructive Git commands unless explicitly requested.

Do not silently modify files outside the task scope.

If a file contains both relevant and unrelated user changes, preserve the unrelated changes.

---

## 6. Git rules

Unless explicitly asked otherwise:

- work on the current branch;
- do not create or switch branches;
- do not commit;
- do not push;
- do not amend commits;
- do not rebase;
- do not reset user work.

When preparing changes, keep them logically grouped and suitable for a clean commit.

If asked to propose commit messages, make them short and descriptive.

---

## 7. Testing

After changing code, run the most relevant available checks.

Prefer, where applicable:

1. targeted tests for the changed functionality;
2. project test suite;
3. lint/static analysis;
4. build;
5. formatting checks;
6. repository-specific validation tools.

Do not claim a test passed unless it was actually run successfully.

If a test cannot be run, state that clearly and explain why.

When fixing a bug, add or update a regression test when practical.

---

## 8. Do not hide failures

If a command, build, test, or analysis fails:

- report the failure;
- identify the relevant error;
- investigate the cause;
- do not describe the task as complete until the remaining limitation is clearly stated.

Do not remove failing tests merely to obtain a green test run unless the test itself is demonstrably incorrect and changing it is part of the task.

---

## 9. Dependencies

Do not add, upgrade, downgrade, or replace dependencies unless needed for the requested task.

Before adding a dependency, consider whether the same result can be achieved with:

- the standard library;
- an existing project dependency;
- existing project infrastructure.

Do not make broad dependency updates as part of an unrelated fix.

---

## 10. Generated files and build artifacts

Do not edit generated files manually unless the project specifically requires it.

Do not commit or modify build artifacts, caches, binaries, temporary files, or local environment files unless they are intentionally part of the repository.

Respect `.gitignore`.

---

## 11. Configuration and secrets

Never place secrets, passwords, tokens, private keys, API keys, credentials, or personal data in source files, logs, examples, tests, or documentation.

Do not print existing secrets to the output.

Use the project's established configuration mechanism.

---

## 12. Backward compatibility

Consider compatibility with:

- existing configuration files;
- stored user data;
- public APIs;
- command-line interfaces;
- supported operating systems;
- supported runtime/library versions.

Do not break compatibility unnecessarily.

If compatibility must change, explicitly identify the impact.

---

## 13. Platform-specific code

When the project supports multiple operating systems or environments:

- determine whether the issue is platform-specific;
- avoid modifying other platform implementations unnecessarily;
- keep platform-specific fixes isolated where practical;
- verify that shared code changes do not alter unrelated platforms.

Do not assume that similar symptoms on different platforms have the same underlying cause.

---

## 14. Documentation

Update documentation when the change affects:

- user-visible behavior;
- installation;
- configuration;
- command-line arguments;
- architecture;
- developer workflow.

Do not add documentation changes for internal implementation details that users do not need unless the repository convention requires them.

---

## 15. Comments

Add comments only when they explain something that is not obvious from the code itself, such as:

- a non-obvious constraint;
- a compatibility requirement;
- a platform/API quirk;
- reasoning behind unusual behavior.

Do not add comments that merely restate the code.

---

## 16. When investigating a bug

A good investigation should establish, when possible:

1. reproduction conditions;
2. current execution path;
3. actual low-level behavior;
4. expected behavior;
5. root cause;
6. minimal fix;
7. possible side effects;
8. regression tests.

When useful, compare the behavior before and after the proposed change.

---

## 17. Before finishing

Review the final diff and check:

- only intended files changed;
- no debug code remains;
- no temporary files were added;
- no unrelated formatting changes were introduced;
- comments and documentation match the implementation;
- tests correspond to the actual bug or feature;
- the change is no larger than necessary.

---

## 18. Final report

At the end of the task, provide a concise report containing:

- what was found;
- what was changed;
- which files were changed;
- why the change solves the problem;
- what tests/checks were run;
- their results;
- any remaining risks or limitations.

For analysis-only tasks, report findings and proposed changes without modifying files.

---

## 19. Default principle

When uncertain, prefer:

**understand first → change minimally → test → review the diff → report accurately.**
