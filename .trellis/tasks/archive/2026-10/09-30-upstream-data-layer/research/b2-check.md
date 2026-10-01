# U3 B2 independent check

Date: 2026-10-01. Dedicated worktree `nga-just-works-upstream-adoption`.
Baseline: B1 `1b4f8ade`. Verdict: atomic B2 code checkpoint ready; no blocking
code findings. U3-A7 and overall U3/parent acceptance remain pending for actual
app R8 evidence through U4. This is not B3–B5 acceptance.

## Findings (fixed)

None. Independent review required no product or test changes.

## Findings (not fixed)

- Actual app `minifyReleaseWithR8 --dry-run` is blocked by the unchanged signing
  guard at `packageReleaseResources`. Reviewed b2-r8-guard.log: no actual app
  mapping/merged-rule evidence exists for this batch. Keep U3-A7 and parent
  acceptance open through U4; the successful classfile probe cannot substitute
  for it. Do not bypass the guard or load signing credentials for this check.
- Android preferences/DataStore/Activity lifecycle and most transport/UI callback
  orchestration remain source-reviewed as explicitly mapped in b2-evidence.md.
  Three-direction storage evidence exercises real codecs/beans/stores plus the
  fixed old parser, not an old APK runtime or a device recreation test.
- Known legacy malformed-message-loop behavior is unchanged and outside this
  migration's repair scope. Passing valid-message fixtures does not certify it.

## Scope and behavior review

Reviewed the complete production/test diff, new operation tests, B2 evidence,
new JSON compatibility spec, dependency outputs, and the R8 script/harness,
cases, result, mapping and configuration. The getter/setter alternateNames
lesson in the main-owned spec matches ThreadPageInfo and its actual tests.

All inventoried runtime JSON consumers move to fixed JSON2 through common;
no B3 wire DTO/module refactor appears. Independent production scan found no
old fastjson imports, ParserConfig/SerializerFeature, old version variable,
global JSONFactory configuration, AutoType/ClassForName, FieldBased or
IgnoreCheckClose. The old app test oracle and kapt dependency are intentional.

Operation-local options match the design: Topic tree + DisableReferenceDetect
then local SmartMatch; Article/preflight SmartMatch; unquoted upload inputs;
bounded AI/profile parsers retain ordinary special keys, decimal precision,
full-document and nesting/character/script checks. Canonical stored names are
preserved while ThreadPageInfo aliases are on both getters and setters.
JSON2 exclusion annotations cover cache handles, compiled Pattern, parentId and
U1 icons; report/avatar beans now use the existing JavaBean marker.

The additional seams preserve distinct operation outcomes and boundaries:
message/filter/like/proxy/avatar data precedence, report error precedence and
empty no-callback outcome, notification decode failures reaching its existing
catch, search nullable names before lookup, preflight auth/null/error behavior,
and category index gaps. Comment decoding preserves script extraction, exact
code/message selection and sticky outer success. Wrappers, logging catches,
transport, account/lifecycle, compression calls and retries remain with their
existing owners. New tests invoke production seams; report also executes its
real callback selection. They do not replace Android stubs with permissive
settings or pretend to exercise network callbacks.

The sole altered existing characterization is explicitly approved Topic unused
@type/$ref extension handling. Existing AI/profile assertions are retained.
All nine B0 old-writer resource bytes independently match B1 Git contents.
The complete 15-row storage matrix distinguishes JSON codecs, opaque raw/ZIP
bytes, source-only managers, and non-JSON Room/Bundle/AI settings. New writes
are read by the fixed old parser/typed beans and by current readers; current
store/recovery/identity tests remain. No schema/key/path or data-clearing change.

## R8 review

The probe extracts 15 actual compiled bean/marker classfiles, including nested
Topic beans, ThreadPageInfo/ReplyInfo, Compose-backed BoardEntity, FilterKeyword
and both newly marked response beans. Independently compared selected bytes to
current compiled input jars. No selected class is duplicated in libraries.jar;
post-shrink execution uses only shrunk.jar plus that filtered library jar.

The harness receives class and method names from external cases.json; no direct
bean references allow reflection-aware retention to hide missing markers.
It applies actual app proguard.cfg/common consumer-rules.pro; its only added
keep is harness main. Mapping shows harness helper obfuscation; production
names survive marker rules. Current result records R8 8.6.27 from AGP8.6.1 and
its artifact hash, and successful nested/alias/output/exclusion checks for all
six families. This proves the selected production beans under those rules on
host JVM, not full-app DEX shrinking or minified Android runtime.

## Verification

Reused completed unchanged gates; no duplicate Gradle execution by checker.

- Tests: pass. Independently summed all module XML: 789 tests, zero failures,
  errors or skips; app 693, message 2, remaining modules as b2-evidence records.
- TypeCheck/build: pass in completed all-module Debug assembly/unit-test gate.
- Lint: pass. Fresh rerun completed all 536 tasks; independently checked all
  13 module XML reports for zero Error/Fatal.
- Dependencies: reviewed debug/release dependencyInsight outputs, JSON2
  2.0.59.android8 only; kapt fastjson1 1.2.69 is via ARouter compiler. App
  1.1.71.android is testImplementation only.
- Classfile R8: pass for documented probe; actual app R8 remains pending.
- `git diff --check`: pass. Immutable B0 goldens: unchanged.
- No reviewer product edits, commits, NGA/device/signing/publication operations.
