# Validation: profile topic bodies and prompt simplification

Date: 2026-09-12.
Worktree: `/home/toph/nga-just-works-ai-summary`.
Branch: `feature/ai-summary`.
Baseline: `dc601c9390979b0a35e367156e0af58634448f0e`.

## Implementation evidence

| Acceptance criterion | Evidence |
| --- | --- |
| AC1: original topic body | `NgaProfilePageSourceTest` exercises matching topic/original TID, explicit floor zero, viewed author, misleading row order, other floors, unavailable content, and malformed responses. The prompt labels the retained body `主题正文：`. |
| AC2: simplified instructions | Source review of `ProfileSummaryInput` confirms the entire rejected fixed paragraph is deleted. `AiProfilePrompt` no longer requires numbered quotes in the roast preset. Existing style/custom tests preserve the selected instructions and custom text exactly. |
| AC3: accurate sample framing | `SummaryInputTest` covers retained counts, independent numbering, empty/partial samples, topic/reply bodies, and quote isolation. Body absence uses an application-owned notice. The common prompt states first-page scope and the 1,200-character cleaned prefix. |
| AC4: body and prompt limits | Both categories cover 1,199/1,200/1,201-character bodies and surrogate boundaries. `AiSummaryClientTest` sends maximum samples with each built-in preset unchanged and proves an oversized custom input fails before sending any request. |
| AC5: bounded collection ownership | Source tests cover one captured session, ordered sequential reads, at most 22 application-scheduled calls, cancellation, timeouts, late/synchronous callbacks, and safe typed failures. Existing loader/controller tests remain in the focused suite. |

## Executed checks

| Command | Result | Local log |
| --- | --- | --- |
| `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.ai.*' --tests 'sp.phone.ai.summary.*' --console=plain` | PASS: 204 tests in 13 classes; zero failures, errors, or skips. | `.temp/ai-profile-body-validation/focused-ai.log` |
| `./gradlew :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:lintDebug --console=plain` | PASS: debug build and app lint. App XML: 0 Error, 0 Fatal, 729 Warning, 1 Information. | `.temp/ai-profile-body-validation/app-build-lint.log` |
| `./gradlew :nga_phone_base_3.0:testDebugUnitTest --console=plain` | PASS: 368 tests in 39 classes; zero failures, errors, or skips. | `.temp/ai-profile-body-validation/app-full-unit.log` |
| `./gradlew lintDebug --continue --rerun-tasks --console=plain` | PASS: exit 0; 536 tasks executed. All 13 Android module XML reports exist and have zero Error/Fatal issues. | `.temp/ai-profile-body-validation/all-module-lint.log` |
| `./gradlew testDebugUnitTest --continue --console=plain` | Exit 1: two unchanged library example-test compilation failures, detailed below. This diagnostic command is not reported as passing. | `.temp/ai-profile-body-validation/all-module-unit.log` |

The focused suite includes 42 `NgaProfilePageSourceTest`, 9 `SummaryInputTest`,
and 25 `AiSummaryClientTest` cases. The focused suite is a subset of the full
app suite, not an additional unique test count. `git diff --check` passed at
implementation handoff. No competing Gradle invocations were run.

The implementer also inspected complete synthetic composed prompts using the
compiled classes. At maximum retained body and metadata sizes, prompt lengths
are 61,897 UTF-16 units for the forum-roast preset and 61,868 for the detailed
preset. A maximum 8,192-character custom instruction produces 69,844 units;
the existing 65,536-unit error boundary rejects it without truncating the
instructions or sending a partial prompt.

## All-module lint reports

Every module has 0 Error and 0 Fatal issues. Warning/information counts are
diagnostic, not a zero-warning claim. The machine-readable local summary is
`.temp/ai-profile-body-validation/lint-counts.json`.

| Module | Warning | Information |
| --- | ---: | ---: |
| `lib_bu_statistics` | 5 | 0 |
| `nga_phone_base_3.0` | 729 | 1 |
| `lib_core` | 8 | 1 |
| `lib_base_logger` | 8 | 0 |
| `lib_base_common` | 33 | 0 |
| `lib_core_data` | 1 | 0 |
| `lib_bu_message` | 11 | 2 |
| `lib_base_network` | 7 | 0 |
| `lib_base_service_api` | 2 | 0 |
| `lib_bu_account` | 6 | 0 |
| `lib_base_ui_compose` | 12 | 0 |
| `lib_base_ui` | 8 | 0 |
| `lib_module_debug` | 3 | 0 |

## Repository-wide test diagnostic

The aggregate command encountered exactly two failures:

- `:lib_bu_statistics:compileDebugUnitTestJavaWithJavac`: the existing
  `ExampleUnitTest` cannot resolve `org.junit` and emits five compiler errors.
- `:lib_module_debug:kaptDebugUnitTestKotlin`: the existing `ExampleUnitTest`
  generates an `@error.NonExistentClass` annotation stub.

Both modules and their test/build configuration are unchanged by this task.
These are the pinned example-fixture failures documented in
`.trellis/spec/backend/android-quality-guidelines.md` under Validation gate;
they do not replace or weaken the required passing app suite and all-module
zero-error lint checks. No product dependency or test variant was changed to
mask them. No new task-scoped failure was found.

The aggregate reused up-to-date successful reports for the app and ten other
modules: 449 reported tests across 54 classes, zero failures/errors/skips in
those reports. This is not a claim of 449 freshly executed tests in the
aggregate run. `lib_core` and `lib_base_ui` currently have passing up-to-date
reports; their historical failures were not reproduced. The two compilation
failures produced no test results. Per-module counts and task statuses are in
`.temp/ai-profile-body-validation/unit-counts.json`.

## Final review and contract sync

The `trellis-check` agent reviewed the complete feature diff, task artifacts,
all manifest references, and the affected package/layer indexes. Both agents
read the relevant complete sections of the AI-summary and Android-quality
specs directly because those files exceed the context injection cap. The
review found no task-scoped code defects or outstanding spec drift and required
no production/test edits. Production/app compilation passed; the separate
library test-compilation limitations are recorded above.

The backend AI-summary contract and operation registry now describe topic
enrichment, identity projection, 1,200-unit body prefixes, simplified prompts,
and bounded sequential cancellation. The test descriptions match their actual
owners. Both task context manifests validate with six valid entries each; the
only warnings are the handled per-file injection limits.

Acceptance criteria AC1–AC5 are complete. All Gradle processes and agent code
writes have stopped. The feature is ready for its work commit, followed by
finish-work and the authorized branch push.

## Verification limits

All response fixtures and model requests are synthetic or local MockWebServer
traffic. These checks do not claim live NGA/model compatibility. Additional
topic-detail reads can increase collection latency; existing cancellation
remains available. Unknown malformed NGA syntax fails through the safe error
path. Device tests were not run per project policy and are not a delivery
blocker.
