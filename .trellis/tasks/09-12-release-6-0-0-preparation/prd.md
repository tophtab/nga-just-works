# Prepare release 6.0.0

## Goal

Complete release preparation and, following the maintainer's final publication
instruction, tag the prepared, pushed main commit as `6.0.0`.

## Background and authorization

- The maintainer requested release preparation, then clarified the sequence:
  existing changes are committed and pushed first, and stable publication
  waits for one final instruction: “你需要把之前准备之类的全部都做好，我只需要一声令下，你直接发布就行了”.
- The subsequent clarification explicitly removes repeated validation:
  “不需要再走检验之类的东西，因为之前那个提交改动push肯定是已经做好检测的”.
  Reuse the owning implementation task's results. Do not rerun tests, builds,
  lint, release validators, or add a separate quality-review cycle.
- The prior initial inspection established stable base `5.6.1`, local/remote
  main at `5f44cf43ee96e13074ad081fa33beede0b33da7a`, no remote `6.0.0` tag,
  and the derived stable versionCode `60000000`. Its 36 release-script tests
  already passed before the maintainer waived further validation.
- The parallel `09-12-reader-refresh-stability` task owns the reader/IP changes
  and their existing staged files, checks, commits, archive, and push.
- With validation and application changes excluded, this is a lightweight
  documentation/release-handoff task. PRD-only planning is sufficient. The
  maintainer's follow-ups authorize completing preparation now, including its
  documentation commits and push.
- The maintainer then explicitly authorized publication: “可以发布了！”.
  Finish the prepared documentation and push main, then create and push the
  exact stable tag. No additional preparation approval is needed.
- The reader/IP fix landed as `ee4556f8`, followed by its archive and journal
  commits through `d1e59291`; that task owns the inherited verification.

## Requirements

1. Add `release-notes/6.0.0.md` in Chinese, accurately covering user-visible
   changes since `5.6.1`, including the reader fix when its owning task lands.
   Use exactly `## 新增`, `## 删除`, and `## 修复` in that order, with a non-empty
   list in each section, and a `5.6.1...6.0.0` comparison link.
2. Keep application sources, Gradle defaults, workflow, signing configuration,
   and test files unchanged. The existing tag-driven workflow already derives
   `versionName=6.0.0` and `versionCode=60000000`.
3. Preserve the parallel session's working tree and staged changes. Commit
   only this task's release notes and records, after its owner has completed
   the shared-index work. Push the completed preparation to main.
4. Record the release target and exact authorized publication
   action. Use Git/ref facts for delivery coordination only; do not introduce
   another verification gate or ask for another preparation approval.
5. Following the maintainer's publication authorization, create an annotated
   `6.0.0` tag on the recorded prepared commit after main is pushed, and push
   that exact tag. The existing GitHub workflow builds, signs, and publishes
   the stable Release with the committed notes. Report the pushed refs without
   CI polling, local packaging, installation, or another quality gate.

## Acceptance Criteria

- [x] `release-notes/6.0.0.md` contains the intended user-facing release text.
- [ ] The release notes and required reader/IP changes are committed and
      included in the pushed main history.
- [ ] A durable release handoff records the version, pushed target, notes,
      publication commands, and the maintainer's no-repeat-validation choice.
- [x] No new test/build/lint/validator run or quality-review cycle was added
      after the maintainer's clarification.
- [x] The maintainer explicitly authorized stable publication before tag creation.
- [ ] Push `6.0.0` after the prepared main history has been pushed.

## Out of Scope

New application behavior, workflow changes, local APK packaging, device/live
service checks, reworking the other session's implementation, and following
the CI run after the publication tag push.
