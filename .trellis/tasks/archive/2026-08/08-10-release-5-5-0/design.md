# 发布 5.5.0 技术设计

## Release Boundary

稳定版内容由 `5.3.2` 之后、最终 release notes 提交之前的全部已提交产品变化
组成。规划时的产品边界是 `1800c224`，其中最后一项产品修复为 `7c4cc7df`。
执行开始时必须先 fetch 并重新比较 `5.3.2..HEAD`；任何新增产品提交都会改变
changelog 合同，必须返回规划更新后再发布。

本任务新增的稳定版源文件只有 `release-notes/5.5.0.md`。发布提交通过精确路径
暂存，Trellis 规划/归档、journal 或其他并行改动不得进入该提交。稳定标签指向
release notes 提交；后续 Trellis 收尾提交不得移动标签。

## Publication Flow

```text
validated release-notes/5.5.0.md
        -> scoped documentation commit on main
        -> non-force push to origin/main
        -> annotated tag 5.5.0 on that exact commit
        -> push refs/tags/5.5.0
        -> existing build.yml derives CI_VERSION_NAME=5.5.0
        -> one signed/minified release APK build
        -> manifest, signer and checksum verification
        -> GitHub Release created from the validated notes file
```

The existing workflow owns versionCode derivation, signing-secret restoration, APK naming and
publication. This task does not duplicate those mechanisms locally or mutate the workflow.

## Changelog Contract

`release-notes/5.5.0.md` is the complete stable Release body. It contains exactly the three
required second-level headings in the established order. The content describes net behavior
relative to `5.3.2`, not transient implementations that were added and removed before release.
The final Lint item combines the earlier 11 application-module errors with the last
`ConfirmDialog` error, accurately reporting 12 resolved errors and 13 modules at zero
Error/Fatal.

## Isolation and Concurrency

- Re-fetch `origin/main` and tags immediately before final validation.
- Abort and return to planning if `origin/main` changes the product range after the final review.
- Use `git add -- release-notes/5.5.0.md`; never use broad staging.
- Inspect `git diff --cached --name-status` and the staged patch before commit.
- Push `main` and the tag without force. A non-fast-forward or existing-tag rejection is a hard
  stop, not permission to overwrite remote state.
- Task metadata remains outside the stable tag and may be committed during Trellis wrap-up.

## Validation Strategy

Local validation covers the only new release input and the publication contract:

- validate the `5.5.0` notes with the repository validator;
- run the validator unit suite;
- run the focused `ReleaseWorkflowContractTest` JVM test;
- run `git diff --check` and Trellis task validation/check;
- verify GitHub permission, required Secret names, tag/Release absence and remote alignment.

The product tree already passed repository-wide module Lint after `7c4cc7df`, and the current
main Debug publication workflow succeeded. Because this task changes only Markdown, the project
contract keeps signed packaging in GitHub Actions and does not authorize local
`assembleRelease`, APK installation or device work.

## Rollback and Failure Handling

- Before pushing `main`, edit or replace the local release notes commit normally.
- After pushing `main` but before pushing the tag, add a corrective notes commit and tag the new
  reviewed commit; do not rewrite public history.
- If tag creation or push reports an existing `5.5.0`, stop and inspect ownership/state; never
  force-update the tag.
- After the stable tag is pushed, treat `5.5.0` as immutable. Do not move/delete it or replace
  stable assets. A release defect is corrected with a new version such as `5.5.1`.
- Per project policy, tag push ends publication work. Do not poll Actions or synthesize a manual
  Release if CI has not yet completed.
