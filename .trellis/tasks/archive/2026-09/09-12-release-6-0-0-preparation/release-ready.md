# Release 6.0.0 publication record

## Published ref

- Repository: `tophtab/nga-just-works`.
- Stable tag: `6.0.0` (annotated).
- Tagged commit: `c0dd626f8d42e84fdec8961d3e021bc67142c1f0`.
- Release notes: `release-notes/6.0.0.md` in that commit.
- Android version: `6.0.0`, versionCode `60000000`, derived by the existing CI.
- Reader/IP fix included: `ee4556f8`.
- Maintainer's publication instruction: “可以发布了！”.

## Completed delivery

1. The reader task had already pushed main through `d1e59291`, including its
   committed implementation, archive, and journal.
2. Release notes and preparation records were committed as `c0dd626f` and
   main was successfully pushed from `d1e59291` to `c0dd626f`.
3. The following commands completed successfully:

```sh
git tag -a 6.0.0 c0dd626f8d42e84fdec8961d3e021bc67142c1f0 -m 'NGA Just Works 6.0.0'
git push origin refs/tags/6.0.0
```

Git reported `[new tag] 6.0.0 -> 6.0.0`. This triggers the existing
`.github/workflows/build.yml` stable publication path. Subsequent task/archive
and journal commits are documentation-only descendants of the release source.

## Workflow handoff

The existing workflow builds and signs the stable APK, uses the committed
release notes, and publishes the normal `NGA Just Works 6.0.0` GitHub Release.
Its expected assets are:

- `NGA-Just-Works-6.0.0.apk`
- `NGA-Just-Works-6.0.0.apk.sha256`
- `NGA-Just-Works.apk`
- `NGA-Just-Works.apk.sha256`

Workflow: https://github.com/tophtab/nga-just-works/actions/workflows/build.yml

Release location once CI publishes it:
https://github.com/tophtab/nga-just-works/releases/tag/6.0.0

The tag push is confirmed. CI completion and asset availability were not
observed; the maintainer did not request workflow watching or device checks.

## Reused validation

The maintainer explicitly requested no repeated verification. No tests,
builds, lint, release-note validator, or separate review cycle were run after
that instruction. The owning reader task's completed gate is preserved in
`.trellis/tasks/archive/2026-09/09-12-reader-refresh-stability/validation.md`
and `check-results.md` (677 JVM tests, successful debug assembly, and no lint
Error/Fatal findings). The initial release-script run passed 36 tests before
the no-repeat instruction.

Only release notes and task/session documentation changed during preparation.
No application, version-default, workflow, signing, or specification changes
were needed for the existing release contract.
