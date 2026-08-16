# 5.5.0 Release Evidence

Snapshot gathered on 2026-08-10 in `/home/toph/nga-just-works`. Re-run every
remote/state check immediately before publication because branch, tag, Release,
and workflow state can change after this planning snapshot.

## Repository Boundary

- `git status --short --branch` showed `main...origin/main` with no product-code
  changes after the final bug-fix task. The remaining planned changes are the
  task artifacts and untracked `release-notes/5.5.0.md`.
- `git rev-list --left-right --count origin/main...HEAD` returned `0 0` during
  planning.
- Planning HEAD and `origin/main` were both `1800c224` (`chore: record journal`).
- The last product fix in the intended release range is `7c4cc7df`
  (`fix(android): clear remaining repository lint error`). It changes
  `ConfirmDialog` from `context!!` to `requireContext()` and keeps confirmation
  and cancellation behavior unchanged.
- The archived fix task records a repository-wide result of 13 Android modules
  at `0 Error / 0 Fatal`; this last fix raises the release-range total from 11
  resolved application-module errors to 12 resolved repository errors.

## User-Facing Product Commits Since 5.3.2

The net changelog inputs found in `git log 5.3.2..HEAD` are:

| Commit | Release-note impact |
| --- | --- |
| `11694d3c` | Restore Android 10 / API 29 installation support. |
| `3fa36a8b` | Add persisted, accessible home-board tab ordering and the new default order. |
| `9bdbbc0c` | Remove random loading sayings. |
| `f348d985` | Keep forum/article FABs visible and avoid reply-FAB content obstruction. |
| `59a32710` | Prefetch at most two following non-final article pages. |
| `6cfc5fa7` | Clear 11 inherited application-module Lint errors. |
| `6ac8c79e` | Add long-press refresh on the selected article page tab. |
| `19943019` | Set held long-press refresh repetition to five seconds. |
| `7c4cc7df` | Clear the final repository Lint error in `ConfirmDialog`. |

Trellis, journal, spec, and archive commits in the range are not standalone
user-facing changelog entries. The transient overflow-menu refresh entry was
removed before this release and therefore is not described as a net change.

## Tag and Release State

- Local stable tags and `git ls-remote --tags --refs origin` identify `5.3.2`
  as the newest stable tag. Neither local nor remote state contained `5.4.0`
  or `5.5.0` during planning.
- `gh release list` identified `NGA Just Works 5.3.2` as the latest stable
  GitHub Release. No `5.4.0` or `5.5.0` Release existed.
- `5.3.2` dereferences to commit `6fc543ba`, whose subject is
  `docs: add 5.3.2 release notes`.
- Existing stable tags are annotated. `git cat-file tag 5.3.2` uses the message
  `NGA Just Works 5.3.2`, establishing the matching `5.5.0` tag convention.

## Publication Contract

Evidence from `.github/workflows/build.yml`,
`.trellis/spec/backend/android-quality-guidelines.md`, and
`ReleaseWorkflowContractTest.kt`:

- Stable publication is triggered only by an exact `X.Y.Z` tag.
- The tag becomes `CI_VERSION_NAME`; Gradle's local fallback version is not
  edited for a stable release.
- Stable execution uses one Gradle invocation containing `verifyReleaseTag`
  and `:nga_phone_base_3.0:assembleRelease`.
- The workflow validates `release-notes/<tag>.md` before creating the Release
  and publishes it through `--notes-file`; stable publication does not use
  generated notes.
- The APK must have application ID `com.github.tophtab.ngajustworks`, minSdk
  29, targetSdk 35, the derived version name/code, `debuggable=false`, and a
  valid release signature.
- Publication stages exactly the APK and its SHA-256 sidecar before
  `gh release create --verify-tag`.
- Local signed packaging, APK/device work, and polling the tag-triggered
  workflow are outside the default contract. A successful tag push ends the
  publication action; failure investigation waits for a later explicit user
  request.
- Stable tags/assets are immutable. A post-tag defect requires a new version;
  it does not authorize moving or overwriting `5.5.0`.

## GitHub Readiness

- `gh auth status` showed the active GitHub account `tophtab` with repository
  and workflow scopes. No token value is recorded here.
- `gh repo view` reported `tophtab/nga-just-works`, public, with
  `viewerPermission: ADMIN`.
- `gh secret list --app actions` showed all required secret names:
  `ANDROID_SIGNING_KEYSTORE_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`,
  `ANDROID_SIGNING_KEY_ALIAS`, and `ANDROID_SIGNING_KEY_PASSWORD`. Values were
  not read.
- Workflow run `31394314340` for commit `1800c224` completed successfully, and
  the corresponding Debug prerelease was visible as `debug-1800c2245ac7`.
  This is current-tree signed-preview evidence, not a substitute for the
  stable tag build.

## Execution Implications

1. Fetch and repeat branch/tag/Release checks immediately before release.
2. If the product range differs from this snapshot, update the changelog and
   return to final planning review.
3. Stage only `release-notes/5.5.0.md`, inspect the cached diff, and push
   without force.
4. Create annotated tag `5.5.0` with message `NGA Just Works 5.5.0` on the
   exact release-notes commit, push only that tag ref, and do not poll CI.
