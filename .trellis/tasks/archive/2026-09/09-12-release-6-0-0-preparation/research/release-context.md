# Release preparation context

## Source of truth

- Release base: `5.6.1`; target stable version/tag: `6.0.0`.
- `build.gradle` accepts the CI version pair. Its `4.5.0` local fallback is
  unrelated to the tag-driven stable version and should remain unchanged.
- `.github/workflows/build.yml` already handles exact `X.Y.Z` tags, signed
  stable APK assembly, committed release notes, and four stable assets:
  `NGA-Just-Works-6.0.0.apk`, its `.sha256`, `NGA-Just-Works.apk`, and its `.sha256`.
- `release-notes/5.6.1.md` illustrates the required headings and comparison link.
- `README.md` describes shipped AI functionality and uses the version-free
  latest stable download URL. No README version update is needed.
- Existing release-script checks passed (36 tests) during the initial request.
  The maintainer subsequently said not to repeat any verification. Do not
  run tests, builds, lint, release-note validation scripts, or a review agent.

## Content evidence to inspect

Use `git log 5.6.1..HEAD`, the changed sources, and relevant task/spec documents
to distinguish final user-visible changes from intermediate implementations.

Main change groups include BYOK AI floor summaries and public-activity user
analysis; model discovery/manual selection/connection testing; streaming
results and folded reasoning; profile prompt presets/customization; profile
body inputs and unavailable-activity handling; thread-detail compatibility
mode; author IP locations from Web profiles; loading tips; floor-menu/cache
fixes; and reader refresh/page-switch rendering fixes.

Do not describe temporary disabled IP subscriptions or intermediate pacing
values as the final behavior. Public release notes should describe the user
outcome without exposing internal contracts or test counts.

The parallel `09-12-reader-refresh-stability` task owns the remaining reader
changes. Its implementation and validation records may move from the active
task directory into `.trellis/tasks/archive/2026-09/` while preparation runs.
It preserves applicable body WebViews and unchanged fresh IP text during
refresh. Include this outcome once its changes are committed.

## Ownership

The release-note implementer owns only `release-notes/6.0.0.md`. The main agent
owns this task's records, final delivery coordination, commit, archive, journal,
and push. No agent in this task may stage or commit the other task's files.

The maintainer subsequently said “可以发布了！”, explicitly authorizing stable
tag creation and push after main is pushed. Preparation changes are
documentation-only and the existing branch workflow ignores `.trellis/**`
and `**/*.md` pushes.
