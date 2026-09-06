# Sync AI summary branch and publish branch previews

## Goal

Bring `feature/ai-summary` up to date with `main` and make its signed preview
APK directly downloadable from GitHub Releases, with a visible branch label.

## Background

- The user requested both changes and approved creating this task and continuing.
- The clean feature worktree is `/home/toph/nga-just-works-ai-summary` at
  `c52e045c658815cfb8dbc423b316c48401149d94`.
- `main` is at `780594bf67ba52aef1e85605f7504f3d017bf9af`. Its one exclusive
  commit updates the Trellis runtime/integrations to 0.6.16; the feature branch
  has five exclusive commits. An initial merge-tree check reports no conflicts.
- `.github/workflows/build.yml` already builds the feature branch successfully,
  but uploads a seven-day Actions artifact and skips GitHub Release creation.
- Main previews remove older `debug-*` and `preview-*` prereleases, so a new
  branch publication needs a separate tag namespace.

## Requirements

1. Include the current `main` history in the feature branch while preserving its
   existing AI implementation and published commit history.
2. Eligible pushes to `feature/ai-summary` publish the existing signed preview
   APK and SHA-256 sidecar as a public GitHub prerelease.
3. Show the branch in the Release title and downloadable APK filename. The
   branch label must be safe for a single filename (`feature-ai-summary`).
4. Keep regular main preview and stable release behavior compatible. Separate
   branch publication identities even if two channels build the same commit.
5. Clean up older previews only within the publishing channel, after successful
   publication. Main and feature previews must coexist, and stable releases
   must be retained.
6. Preserve the existing production application ID, signing, Android version
   rules, and package verification. The branch label is release/asset metadata.
7. Make the download location clear in the repository documentation and handoff.

## Acceptance Criteria

- `origin/main` is an ancestor of the completed feature branch; behind count is
  zero, and the Trellis update is present without custom runtime edits.
- The workflow publishes feature APKs through GitHub Releases rather than
  `actions/upload-artifact`.
- Main, feature, and stable identity checks produce distinct expected titles,
  tags, and asset names, while retaining valid Android version values.
- Same-commit reruns retain their identity and validate the existing target
  before replacing prerelease assets.
- Cleanup verification preserves the current tag, other branches, main
  previews when publishing a branch, and stable/unrelated releases.
- Workflow/Bash lint, focused offline publication checks, existing Python
  version/release-note tests, and `git diff --check` pass before push.
- Commit the reviewed changes and push only `feature/ai-summary`; provide the
  Releases download location and accurately distinguish pushed configuration
  from a verified CI publication.

## Scope Boundaries

This is one lightweight integration/CI task. It does not merge the AI branch
into `main`, rewrite remote history, create a stable release, enable arbitrary
branches or manual dispatch, change Android product code, rebuild signed APKs
locally, or operate any device. Packaging/signature validation remains in CI.
No blocking product decisions remain.
