# U4 independent implementation check

**Final verdict: PASS.** Actual app R8 and Preview compilation have now passed;
the initial guard blocker recorded below is resolved. U3-A7 and U4-A7 are met,
and the parent may proceed to its authorized finish-work/release steps.

Reviewed against `c2131fe9`, including the complete approved task artifacts,
check context, platform research and current system-back contract.

## Findings (fixed)

No reviewer product fixes required. The implementer corrected the existing
mirrored JVM release-workflow SDK expectations from 35 to 36; the reviewed diff
changes only those expectations and the test name.

## Verified

- Exact SDK/toolchain/checksum match approval. Effective configuration evidence
  lists all 13 modules at compile36/min29/Java17; existing Kotlin17 settings and
  shared Kotlin/Compose compiler 2.2.21 remain aligned. App target36, identity,
  version derivation, signing guard and build-type minification are preserved.
- Both Java hosts register Activity-lifecycle AndroidX callbacks. Current
  fragment null/added/view checks precede consumption; fallback disables the
  callback, delegates once and restores in finally. Production strategy tests
  cover consumption, decline, unavailable view and throwing fallback. Up,
  SwipeBack, drawer, WebView and draft ownership are unchanged. Lifecycle and
  predictive gesture execution are not claimed from fake/JVM tests.
- Independently parsed actual test XML: 820 tests across all 13 modules,
  zero failures/errors/skips; app 707 including four callback tests.
- Independently parsed all 13 actual lint XML reports: zero Error/Fatal,
  826 warnings. Fresh offline lint log records 538/538 tasks executed.
  Final offline Debug/build-test log is successful. Python script log records
  37 passing tests; real workflow fixture rejects target35 and accepts target36.
- Read actual Debug/Release/Preview merged XML: min29/target36, Debug suffix
  only, debuggable true/false/true. Actual Debug APK hash matches
  `4ba2a71bcf6bedf85a5a4e4daebafd22d720cd2132b837368a464183f1ed227b`.
  Native evidence lists exactly Bugly, graphics-path and Umeng arm64 libraries;
  ELF LOAD alignment/congruence and uncompressed ZIP offsets meet 16KB.
  Build Tools35 zipalign log passes. This is not native device execution.
- New bundled R8 8.10.24 classfile probe reports all six immutable bean cases
  passing. Reviewed unchanged probe source and new mapping/configuration: actual
  production keep rules are used; original bean inputs are excluded from the
  post-shrink runtime libraries. This does not substitute for app R8.
- Parent `integration-check.md` accurately retains the pending gates and merged
  reply-search fix. `release-notes/6.2.0.md` passes the actual validator and does
  not claim device validation or published assets. `git diff --check` passes.

## Initial review: two gates were pending (historical, resolved)

Actual app `minifyReleaseWithR8` and Preview Kotlin/Java compilation have not run.
Reviewed new-toolchain dry-run graphs contain 300 and 230 tasks respectively.
The unchanged signing guard rejects app `packageReleaseResources` and
`packagePreviewResources`; neither graph requests APK/AAB packaging, signing or
validateSigning. Standalone merged-manifest checks already passed.

The remaining access requirement is permission to privately load the existing
four signing configuration values solely to satisfy that unchanged guard while
running the named R8/compile tasks. Do not change the guard, exclude resource
dependencies or supply fake credentials. No local signed APK output or device
operation is required by these graphs. After successful execution, independently
review current app mapping/merged keeps and Preview compile evidence.

At this initial checkpoint U3-A7, U4-A7 and parent final acceptance remained
incomplete until those actual tasks passed.

## Final actual-app artifact audit

- Following the user's continuation of the concrete configuration-only scope,
  the implementer privately loaded existing configuration for the unchanged
  guard. Reviewed configured dry-run graphs remain 300/230 tasks and contain
  no APK/AAB packaging, signing or validateSigning tasks. App build.gradle and
  gradle.properties have no diff from the baseline.
- Actual offline `minifyReleaseWithR8` passed in 3m7s (261 tasks,176 executed).
  Read actual merged configuration and mapping sections, then reran the reviewed
  static DEX inspection against the current outputs: all six output hashes match
  the record, eight critical bean classes remain, all ten ThreadPageInfo alias
  getter/setter JSONField annotations remain, and both BoardEntity icon accessor
  annotations remain. Actual keep rules include JavaBean, Parcelable, JSON2,
  ARouter, Signature and annotation retention. The independent six-case R8
  classfile reflection probe remains complementary evidence.
- Actual offline Preview Kotlin/Java compilation and manifest task passed in
  13s (193 tasks,14 executed). Both compiler tasks appear executed in the log.
  Existing variant identity/minification settings remain unchanged.
- No product edits were required for either gate. Previously verified 820 tests,
  Python37, fresh13-module lint and native/APK evidence remain applicable.
  R8 emits legacy dependency stack-map warnings; the task succeeds. Neither DEX
  inspection nor classfile reflection is represented as minified Android runtime.

No unresolved implementation findings remain. No commit, push,
signing-credential access, Gradle invocation or device/NGA operation was
performed by this reviewer.
