# 发布 5.5.0 执行计划

## 1. Preflight and Scope Lock

- [x] Fetch `origin/main` and all tags without changing local files.
- [x] Confirm the current branch is `main`, local/remote divergence is zero, and GitHub access is
      the expected `tophtab` account with `ADMIN` permission.
- [x] Confirm all four required Actions Secret names exist without reading their values.
- [x] Confirm local tags, remote tags and GitHub Releases still contain neither `5.4.0` nor
      `5.5.0`.
- [x] Re-run `git log 5.3.2..HEAD` and compare product commits with the final PRD. If a new product
      commit exists, update the changelog and return to the final-review gate.
- [x] Record the exact release target commit before creating the notes commit and preserve all
      unrelated working-tree changes.

## 2. Finalize and Validate Changelog

- [x] Read `release-notes/5.5.0.md` top to bottom and confirm the three required sections describe
      net changes only, including the `7c4cc7df` confirmation-dialog fix.
- [x] Run `python3 scripts/validate_release_notes.py release-notes/5.5.0.md`.
- [x] Run `python3 scripts/test_validate_release_notes.py`.
- [x] Run
      `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests gov.anzong.androidnga.ReleaseWorkflowContractTest --no-daemon`.
- [x] Run `git diff --check` and the Trellis check workflow. Do not run local release/preview
      packaging, ADB or device tests.

## 3. Create the Release Notes Commit

- [x] Stage exactly `release-notes/5.5.0.md` with a path-specific `git add`.
- [x] Verify `git diff --cached --name-status` lists exactly that one file and inspect the full
      staged patch.
- [x] Commit with message `docs: add 5.5.0 release notes`.
- [x] Verify the commit tree contains no unrelated task, workspace, signing or product-code
      changes.

## 4. Publish Main and Stable Tag

- [x] Non-force push local `main` to `origin/main` and verify the remote branch resolves to the
      release notes commit.
- [x] Reconfirm no local or remote `5.5.0` tag exists.
- [x] Create annotated tag `5.5.0` on the exact release notes commit with message
      `NGA Just Works 5.5.0`.
- [x] Verify the local tag dereferences to the intended commit, then push only
      `refs/tags/5.5.0` to `origin`.
- [x] Stop publication work after the tag push. Do not watch/poll GitHub Actions and do not create
      a manual Release or upload local assets.

## 5. Trellis Wrap-up

- [x] Record the pushed branch/tag commit IDs and the deliberate no-polling handoff in the task
      result.
- [x] Keep the `5.5.0` tag fixed on the release notes commit while archiving the task and recording
      the developer journal.
- [x] Commit only the scoped Trellis wrap-up metadata separately from the stable tag; preserve any
      unrelated concurrent changes.

## Rollback Points

- Before the `main` push: edit/amend locally.
- After the `main` push but before the tag push: add a forward corrective commit and review it.
- After the tag push: do not move/delete/overwrite `5.5.0`; use a new patch version for corrections.
