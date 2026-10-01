# Independent release integration review

Reviewed rebased `5abfb1cb` against `origin/main` (`d1dcbff0`) and the original
simplification commit `b427c193`. Scope is preservation of newly integrated
6.2.0 coverage, SDK 36/Fastjson2 contracts, dependencies and 6.2.1 notes; the
earlier comprehensive review remains the original-deletion audit.

## Findings

No unresolved integration issue found. No reviewer code edit was required.

- All 46 upstream-added test source/fixture files are byte-identical to
  `origin/main`. This includes message decode, frozen legacy storage, ordinary
  read wire parity, media, board-icon and system-back coverage/resources.
- Every upstream-added method in an existing suite remains. Checked upstream
  added assertion/import lines against resolved current files as well as
  method names: only the Fastjson2 import in the intentionally deleted
  `HomeBoardOrderContractTest` is absent. That upstream edit added no behavior.
  Decimal precision/special keys, media source preservation, frozen owned-cache
  fields and legacy author-location storage methods all survive the merge.
- `lib_bu_message` correctly retains direct `junit:junit:4.13.2` for new
  `MessagePostDecodeTest`. All five current JUnit-owning modules have direct
  test dependencies: common, Compose, message, core and application. Remaining
  dependency deletions are test-only and belong to modules with no remaining
  matching test sources.
- Release regression tests retain minSdk 29, compileSdk/targetSdk 36, and
  platform-36 installation expectations. The Python wrong-target row uses 35,
  preserving the newly relevant downgrade rejection while dropping equivalent
  target-34 repetition. Build-tools 35.0.0 is intentionally unchanged upstream.
- The merged Android quality spec retains upstream SDK 36 requirements and
  opt-in device policy alongside focused validation guidance. New JSON and
  ordinary-read contracts and their newly added coverage remain intact.
- No application production source, root build configuration or publication
  workflow differs from `origin/main` in this maintenance change.
- `release-notes/6.2.1.md` accurately describes test/validation maintenance,
  names 6.2.0 as unchanged application functionality, and links the correct
  version comparison. No unsupported runtime speed or feature claim appears.

## Verification

- `python3 scripts/validate_release_notes.py release-notes/6.2.1.md`: pass.
- `git diff --check`: pass.
- Upstream-file identity and added-line/method preservation checks: pass.
- Module-local JUnit dependency audit: pass.
- Combined Debug tests and Java/Kotlin compilation/type checks: pass. Reviewer
  parsed `release-gate.json`: 561 tests across 83 suites, zero
  failures/errors/skips. This includes the upstream test additions.
- Lint: pass; `release-gate.json` records all 13 module reports with zero
  Error/Fatal findings. Main's combined Gradle gate completed successfully.
- Full Python suite: main runs it serially after Gradle and records the result.
  Earlier 472-test evidence remains explicitly a pre-integration checkpoint;
  the 561-test gate is the validation for this combined tree.
- Device execution: not run per project policy.

## Final disposition

Python32 tests passed (release-python.log). Maintainer cancelled release and
confirmed commit/finish-work only, no push. Uncommitted6.2.1 notes removed;
version preflight evidence is historical and did not create a tag or APK.
