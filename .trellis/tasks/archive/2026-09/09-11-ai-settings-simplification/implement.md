# Implementation Plan

## Readiness

- [x] User requested the seven changes, approved task creation, and said continue.
- [x] Inspect the actual feature worktree, specs, tests, routes, and existing icon.
- [x] Converge PRD, design, scope, acceptance criteria, and spec context manifests.
- [x] Activate this task in the feature worktree.
- [x] Record later authorization for the profile rename, bounded live check,
  stopping local builds, and commit/finish-work/push.

## Execution

1. [x] Implement HTTP-compatible endpoint validation and isolated model discovery;
   cover response parsing, endpoint construction, authentication, and failure paths.
2. [x] Simplify settings and toolbar Save; implement model list/custom/fallback
   interaction and request lifecycle with focused tests.
3. [x] Review the integrated changes through the Trellis check agent, verifying
   draft-vs-saved fields, missing-model discovery, cached-list identity, stale
   callbacks, custom-input preservation, and existing secret-handling invariants.
4. [x] Record completed validation and relevant pre-existing diagnostics, honor
   the user's stop on remaining local builds, and resolve task-owned findings.
5. [x] Update the owning AI contract and record validation for task handoff.
6. [x] Apply the subsequently requested profile-only `AI查成分` label and verify
   resource references without starting another local build.

Detailed outcomes and omitted checks are recorded in `validation.md`. Delivery
continues through the authorized commit, finish-work, and feature-branch push.

## Validation

The original plan included focused tests, an app build/all-unit/androidTest
compile, all-module lint, and repository-wide unit diagnostics. During execution,
the user explicitly declined further local builds. Do not start additional
Gradle builds, lint, or test runs to finish this task. Record completed results,
review the changed resources/source, and run `git diff --check`.

The user also authorized a bounded live model-service check. Use only the
supplied service, a temporary Key passed in memory, model discovery, and the
existing 8-token connection test. Preserve only redacted/nonsecret evidence.
No ADB, device discovery/install, live NGA traffic, or signed release packaging
is part of validation. Commit, finish-work, and push are separately authorized.

## Review and Rollback Points

Keep UI and transport changes within their file ownership. Review together before
completion; discovery must not depend on a nonblank model. Do not modify the store
format, summary controllers, global network config, or unrelated test baselines.
If integration reveals a broader behavior change, record it before expanding work.
