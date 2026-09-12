# Implementation results

Completed in `/home/toph/nga-just-works-compat-mode` on 2026-09-12.

## Change boundary and source

The behavior gap was the build workflow's branch allowlist: the compatibility
worktree published only `main`, while the reusable AI workflow published only
`main` and `feature/ai-summary`. The behavior belongs in the workflow trigger,
release identity, publication condition, and cleanup selection.

Reused `.github/workflows/build.yml` and the actual-Bash fixture tests from
`3ed2a4d1bc2af40844f7f2784d739f1206576b9b`. The implementation changes only:

- `.github/workflows/build.yml`: all-branch publication, bounded names, distinct
  release identities, literal shell/jq data, and same-branch cleanup.
- `scripts/test_release_workflow.py`: reused and extended offline behavior
  checks, including actual Git, Bash, checksum, and jq execution.
- `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ReleaseWorkflowContractTest.kt`:
  adapt affected workflow assertions while retaining Gradle, applicationId,
  SDK, signature verification, manifest, version, and single-build contracts.
- `README.md`: Releases download guidance and branch filename examples.

No helper script was necessary. App functionality, applicationId, signing
configuration, version algorithms, stable notes, and other worktrees were not
changed by the implementer. The main session owns specification changes,
commits, branch synchronization, finish-work, and push.

## Resulting behavior

- `push.branches: ["**"]` includes nested branch names. Existing version-tag
  matching and `.trellis/**` / Markdown-only exclusions remain in place.
- A permissionless `ref-identity` job hashes the exact complete `GITHUB_REF`
  before the build job chooses its concurrency group. This preserves distinct
  queues for case-only ref differences despite GitHub's case-insensitive group
  comparison. Same-ref branch runs still cancel prior runs; tags do not.
- Each branch uses signed `preview`; `main` and stable filename/version/signing
  behavior remains covered by regression tests. Gradle still starts once.
- Gradle cache writes depend on the validated identity outputs: only a preview
  with an empty asset suffix can write. `Main` and `MAIN` remain read-only even
  though GitHub expression string comparisons ignore case.
- Non-main filenames retain the AI convention. Slugs replace each byte outside
  ASCII `A-Za-z0-9._-` with `-`, keep at most 80 ASCII bytes, and fall back to
  `branch` if the resulting slug contains no ASCII alphanumeric character.
- Internal tags are
  `branch-<slug>-<sha256(original complete branch name)[:12]>-<commit sha[:12]>`.
  The digest input has no added newline. Normalized, truncated, or Unicode-only
  filename collisions therefore keep separate releases.
- Dynamic step values enter shell through environment variables. Tag filters
  pass values via `jq --arg`; API failures, including failures after emitting
  valid partial JSON, stop publication.
- Cleanup starts after successful publication. Current and known legacy
  prefixes must be followed by exactly 12 lowercase hexadecimal characters,
  and the current tag is excluded. `LEGACY_PREVIEW_TAG_PREFIX` is `preview-`
  for main, `branch-feature-ai-summary-` only for the original
  `refs/heads/feature/ai-summary`, and empty for other branches.

## Initial validation

| Check | Result |
| --- | --- |
| `python3 -m unittest discover -s scripts` | 32 tests passed in 38.274 s: 21 workflow, 8 version-code, 3 release-notes tests |
| `/tmp/nga-all-branches-apk-tools/actionlint .github/workflows/build.yml` | Passed, actionlint v1.7.7 |
| `bash -n` on extracted literal `run` blocks | All 6 passed |
| `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests gov.anzong.androidnga.ReleaseWorkflowContractTest --console=plain` | BUILD SUCCESSFUL in 51 s; XML reports 9 tests, 0 failures/errors/skips |
| `git diff --check` | Passed |

The offline workflow cases cover all four existing branches, nested branches,
legal shell metacharacters, mixed-case and punctuation preservation, Unicode,
long names, same-SHA slug collisions, successive commits, reruns, exact AI
legacy migration, pagination, non-prerelease protection, manifest/signature
failure, corrupt assets, API/publication failure, and partial cleanup failure.

Actionlint was installed in temporary tool storage with
`GOBIN=/tmp/nga-all-branches-apk-tools go install github.com/rhysd/actionlint/cmd/actionlint@v1.7.7`.
Shellcheck was not available; actionlint and the separate Bash syntax checks
completed successfully.

No local APK packaging, device/ADB operation, signing credential inspection,
real NGA/model request, or live GitHub Release mutation was performed. APK
packaging/signing remains an Actions check after the main session pushes the
approved shared change. Independent review and delivery remain with the main
session; no implementation blocker remains.

## Case-sensitive ref follow-up

Independent review identified two gaps in the initial implementation: raw ref
concurrency groups merge case-only branch names, and a GitHub expression
comparison with `refs/heads/main` also accepts `Main` and `MAIN`. These are
covered by the approved all-branches/isolation contract and were fixed without
changing release, signing, or cleanup behavior.

- Added the `ref-identity` pre-job with `permissions: {}`, no checkout, and no
  secrets. Its `ref_key` is the full lowercase SHA-256 digest of the exact raw
  `GITHUB_REF`, with no added newline. The build job declares `needs` on this
  job and combines its output with `github.workflow` for job-level concurrency.
- Cache read-only selection now uses
  `steps.release.outputs.prerelease != 'true' || steps.release.outputs.asset_suffix != ''`.
  The existing Bash identity comparison provides exact-main ownership.
- The fixture step parser now stops at either the next step or the next job,
  using indentation so pre-job metadata cannot be executed as Bash. The
  expression fixture models GitHub's case-insensitive equality and startsWith.
- Added actual-Bash cases for case-only refs, same-ref keys across runs/commits,
  branch/tag separation, operation without a checkout, exact-main cache writes,
  independent case-only preview releases, and job/output/concurrency wiring.

Superseding workflow validation on 2026-09-12:

| Check | Result |
| --- | --- |
| `python3 -m unittest discover -s scripts` | 36 tests passed in 39.831 s: 25 workflow, 8 version-code, 3 release-notes tests |
| `/tmp/nga-all-branches-apk-tools/actionlint .github/workflows/build.yml` | Passed, actionlint v1.7.7 |
| `bash -n` on indentation-aware extracted literal `run` blocks | All 7 passed |
| `git diff --check` | Passed |
| JVM release-contract gate | Prior 9-test pass retained; the follow-up did not change JVM code or the workflow sections asserted by those tests, so Gradle was not repeated |

Only the workflow, its Python fixture tests, and this report changed in the
follow-up. The main session maintains design/spec updates and will hand the
result back for independent review. No additional packaging, device operation,
remote mutation, commit, or other-worktree edit was performed.
