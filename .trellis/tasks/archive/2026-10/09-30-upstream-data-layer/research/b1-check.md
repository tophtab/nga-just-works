# U3 B1 independent check

Date: 2026-10-01. Worktree: `/home/toph/nga-just-works-upstream-adoption`.
Baseline: B0 `05c2602e`. Verdict: B1 ready for checkpoint commit; no blocking
findings. This review does not certify B2–B5 or full U3 acceptance.

## Findings (fixed)

None. No product or test edits were needed during independent review.

## Findings (not fixed)

No B1 defect found. Explicit outstanding acceptance work remains:

- B2 must exercise the recorded notification/action/message-send/remote-filter
  gaps through minimal appropriate operation-local seams. B1's three decode
  extractions do not certify Android callbacks, compression/file IO, toast or
  notifier behavior, preferences, or Activity recreation at runtime.
- B2 must replace the labelled unknown-@type Topic characterization with the
  approved local tree + DisableReferenceDetect expectation, verifying consumed
  fields survive and extensions remain ordinary data. Existing AI/profile
  special-key/no-class-loading assertions must stay intact.
- All JSON2 imports/options/annotations, storage compatibility directions,
  dependency and R8 gates remain B2 work. The avatar reflection bean's missing
  JavaBean marker is known and intentionally unchanged here. No global parser
  setting or runtime dual-library fallback was introduced.

## Behavior review

Reviewed the three production diffs, all 14 new tests and six jdata fixtures,
plus b1-evidence.md against the previously loaded U3 artifacts/specs.

- TopicConvertFactory retains leading wrapper removal in its public facade.
  Typed decode remains before the existing display/mapping NullPointerException
  catch, so decode exceptions are not newly swallowed or turned into empty
  success. The package-local helper only performs the former typed parse.
- NGA upload retains wrapper replacement inside the existing callback catch.
  The helper preserves code9 selection before reading data, and only on the
  first uncompressed attempt. Other codes and compressed code9 continue to
  consume data when present; missing data fails. Empty data preserves null
  fields and numeric fields retain existing string coercion. Attachment append,
  success/error callback, toast, compression call, lifecycle and transport
  owners remain in place. The new immutable result is manually constructed,
  not an additional reflection contract.
- External avatar upload still uses its own error/errorinfo/string-data bean,
  has no NGA code9 selection or new wrapper handling, and remains at the same
  unguarded decode boundary in onPostExecute. Package visibility changes only
  permit testing the existing bean. Error flag precedence remains in its caller.
- The new Topic unknown-type test records actual old rejection; it does not
  weaken SafeJsonParser/ProfileWebUserParser, whose source and existing tests
  are unchanged. No existing test assertion or B0 golden changed.
- Jdata tests invoke the actual production bean seam and assert forum name/fid,
  time and topic map, not merely absence of exceptions. Independently compared
  all six resource bytes with the archive and checked README SHA-256 values.

## Verification

Reused the implementer's completed gates without duplicate Gradle execution;
independently inspected generated XML and the fresh lint completion log.

- Tests: pass, 778 tests across 13 modules, zero failures/errors/skips.
  App: 683 tests / 77 suites, including 14 new B1 tests.
- TypeCheck/build: pass in completed Debug assembly and unit-test gate,
  including Java/Kotlin compilation.
- Lint: pass, fresh `lintDebug --continue --rerun-tasks --console=plain`;
  536 tasks executed, all 13 module XML reports have zero Error/Fatal.
- `git diff --check`: pass.
- No review-time product edits, Gradle reruns, commits, NGA/device/signing or
  publication operations. Main owns metadata/spec synchronization and commit.
