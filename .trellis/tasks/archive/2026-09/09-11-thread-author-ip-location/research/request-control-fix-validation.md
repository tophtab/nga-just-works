# Request-Control Fix Validation

Date: 2026-09-12. Reviewer: `implement_ip_location_resume`.
Snapshot captured: `2026-09-11T17:05:27.329016+00:00`.

Final integration update: the combined checker inspected and applied the
test-only candidate to the feature worktree, then added a cache-only dispatch
regression/fix. The final app gate passed 223 tests. See the parent's
`research/combined-check-report.md`; the snapshot evidence below remains the
independent pre-application validation record.

## Result and ownership

All three findings from `implementation-review.md` are fixed in the actual
implementation snapshot identified below. This verification supersedes that
report's earlier "awaiting owner fix" status for F1-F3. The original writer
implemented the production fixes while this reviewer worked in an independent
temporary copy. No production source was changed or patched by this reviewer.

`request-control-fixes.patch` now contains **test hunks only**. It adds two
still-missing repository regressions and strengthens the existing loopback
transport test. Do not apply the abandoned production prototype: the original
writer's current fixes are already present and independently verified.

## Verified production fixes

- F1: `ProfileEnvelopeParser` rejects non-object text after supported wrapper
  normalization through `NonProfileResponseException`; `ProfileLocationParser`
  maps it to the session stop. Bounded non-200 bodies can identify a site or
  challenge response, while 5xx profile-shaped bodies cannot become success.
- F2: `AuthorLocationRepository.complete` records rate-limit/session stops for
  the request's captured scope before rejecting stale consumer epochs. The stop
  survives a same-session notification and switching away/back, without clearing
  a different account's valid queue or accepting stale success data.
- F3: the operation-only network interceptor removes the HTTP 503 follow-up hint
  before OkHttp can repeat the request. It preserves the status/body and leaves
  HTTP 429 `Retry-After` available to the existing pause classifier.

These are the exact SHA-256 values of the production sources copied and tested.
Paths are under `nga_phone_base_3.0/src/main/java/sp/phone/profile/`:

| File | Tested SHA-256 |
| --- | --- |
| `ProfileEnvelopeParser.java` | `beeffb9037b92f33819d9a562e2675a60ba159abc2d78417bf1373eff0c631de` |
| `ProfileLocationParser.java` | `b8eda5a5a334734021569e7c1285b3f6ac0fc3927517ff80dab18a99d4ca2a4f` |
| `AuthorLocationRepository.java` | `104a7ab67e958b836abcb5277052e001adb3e3f63622d41b781a29435b10e0bd` |
| `ProfileLocationTransport.java` | `271dd27bbd985d69e7c5ae74be6422688ab6d53d368cc089df3b45b8d95fd0e2` |

## Executed validation

All compilation and tests ran in disposable copies under `/tmp/nga-profile-request-control-fix-vn6oqdp_`. No Gradle,
NGA request, account access, device operation, or shared product mutation ran.
The only network traffic was synthetic HTTP on IPv4 loopback.

| Run | Result |
| --- | --- |
| Earlier snapshot plus independent regression probes, before F2/F3 fixes | 31 tests, 4 expected failures |
| Actual current source snapshot, all five existing profile suites plus nine independent regression methods | 51 tests, 0 failures |
| Current source plus candidate test hunks, rerunning the two affected suites | 27 tests, 0 failures |
| `git apply --check --whitespace=error-all` against a copy of the candidate's base | Passed |
| Apply candidate to another disposable copy and compare changed files byte for byte with the tested candidate tree | Passed |

The pre-fix failures reported a second physical request after `503 + Retry-After:
0`, a new author request after same-session invalidation, and lost stop state on
returning to the original account or credentials. Those same independent cases
pass against the actual implementation hashes above.

The real configured client was tested with HTTP 503 retry hints `0` and `00`:
one invocation sent exactly **one physical request**, returned the original 503,
and retained its wrapper-prefixed HTML for session-stop classification. HTTP
429 with `Retry-After: 3600` also sent exactly one request and retained the
one-hour pause. Other probes cover stale-success isolation, ordinary malformed
object failures, prefixed/plain site rejection stopping the next author,
late rate-limit persistence, and scoped credential replacement.

The standalone setup used Java 17, locally cached Fastjson `1.1.71.android`,
OkHttp `3.12.0`, Okio `1.15.0`, JUnit `4.13.2`, Hamcrest Core `1.3`, and AndroidX
Annotation `1.1.0` for the bean's compile-only annotation. It compiled the pure
profile sources plus the actual `JavaBean`, `ThreadData`, `ThreadRowInfo`,
`Attachment`, and `ThreadPageInfo` source files. `AuthorLocationService` was
excluded because this was a host-JVM check, not an Android lifecycle test.

Execution used `javac -encoding UTF-8 -cp <cached jars> -d <temporary classes>`
and `java -cp <temporary classes>:<cached jars> org.junit.runner.JUnitCore` with:

- `sp.phone.profile.AuthorLocationRepositoryTest`
- `sp.phone.profile.AuthorLocationStoreTest`
- `sp.phone.profile.ProfileLocationParserTest`
- `sp.phone.profile.ProfileLocationTransportTest`
- `sp.phone.profile.ProfileSessionTest`
- `sp.phone.profile.ProfileRequestControlRegressionTest` (nine independent,
  temporary probe methods; equivalent existing tests were not duplicated into
  the final candidate patch).

Temporary `current-manifest.json`, `current-validation.json`, and the
`junit-current-before.log` / `junit-current-after.log` files preserve the exact
input hashes, class lists, and raw JUnit summaries for this session. This report
records the durable result; temporary paths need not remain available after
handoff.

## Candidate test patch

The candidate adds `queuedRateLimitPauseIsPersistedAfterConsumerInvalidation`
and `queuedRejectionIsBoundToTheOriginalCredentialsForTheSameUid` using the
existing repository harness. Its transport hunk strengthens the existing real
client test with an actual 503 challenge body and a longer 429 server delay.
It contains 49 added and 3 removed lines across two test files; there are no
`src/main` hunks and no new dependencies.

| Test file | Base SHA-256 | Patched SHA-256 |
| --- | --- | --- |
| `AuthorLocationRepositoryTest.java` | `47ec504c1efbd25a8544c10dc677170fdfb9c54eaa1d1fd270a885197c8c7bc8` | `28347110e020c4db9fcd4ecc188c1bda16ba7b638b0620544ba2db8f48d361b7` |
| `ProfileLocationTransportTest.java` | `3340c9bcc4f0d1355b7b030e028fa6e96dd65bb8ef89b2c96ffe5ea1da5b25da` | `d510c672db77d50b320c79f26c2b8259a15ce84ecda8617eb2c9c2f904d04df1` |

Original reviewed patch SHA-256:
`1957d9dfb766f19d553a0d629fb9de9f3eb705a2d1350695daf68c5213b3a949`.
The checked-in copy normalizes an empty context line to avoid trailing
whitespace in the task artifact; its added/deleted test payload is unchanged.
It passed `git apply --reverse --check --whitespace=error-all` against the
integrated tests. Checked-in patch SHA-256:
`140ae4cd79430e5b68ce470e4fb6c99204dabfc73306a34d02cb40cccb6831f0`.

Before applying after writer handoff, compare the base hashes or run
`git apply --check` from the feature worktree. If concurrent test edits have
changed the context, port only the missing assertions into those tests; do not
replace the writer's source files with temporary snapshots. The final checker
still owns the normal Android build/unit/lint gate and lifecycle/UI verification.
