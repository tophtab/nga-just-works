# Second-round implementation results

## Changes and metrics
Implemented exactly the seven-method deletion plan: removed ArticleConvertFactoryTest.java (2 methods), FilterWordModelTest.kt (1), and one method each from AiModelEditorStateTest, NgaImageHostContractTest, ReadThreadFacadeParityTest and ArticleSelectionTextTest. Removed two now-unused imports from ReadThreadFacadeParityTest and corrected its stale reference to the deleted demonstration. All compatibility fixture files and retained snapshot methods are unchanged.

Baseline source inventory immediately before this round: 560 @Test methods in 83 test files. After: 553 methods in 81 files. This round removes 132 source lines net and 21 lexical assertion call sites (including Kotlin assert calls). Per-file measurements are in next-metrics.json. First-round changes remain intact; combined rounds remove 8 methods, 2 files, 232 source lines net and 49 assertion call sites. No skips, exclusions, scenario moves or count quotas were used.

## Retained coverage and intentional losses
- Obsolete resolveAttachmentsPrefix test path removed; active wire decoder, NgaImageHost, facade parity and PageAttachmentPrefixFlow coverage remains. The unused production helper was not edited.
- FilterWordModel.convertEntity loses its direct happy-path isSuccess smoke test. FilterUpdateDecodeTest and storage tests target different entry points and are not replacements.
- Editor draft/custom/loading preservation and stale-callback tests remain. The combined initial-empty-model + typing + first-success scenario no longer has a dedicated assertion.
- Legacy host mapping tests remain; the specific .ngacn.cc attachment combination no longer has its own assertion.
- Facade, container and fallback snapshots remain. Direct historical raw-helper ClassCastException/NullPointerException and null-entry exception types are no longer pinned.
- Valid WebView result decoding, Unicode and blank selection coverage remain. Defensive malformed JSON escape fallback loses its dedicated test.

## Validation
- Affected owners: `./gradlew :lib_base_common:testDebugUnitTest :lib_core:testDebugUnitTest :nga_phone_base_3.0:testDebugUnitTest --console=plain` passed. XML: common 26, core 27, app 498 = 551 tests, zero failures/errors/skips. Evidence: next-affected.log and next-affected-results.json.
- Final repository gate, run once: `./gradlew testDebugUnitTest lintDebug --continue --console=plain` passed. All unit XML: 553 tests / 81 reports, zero failures/errors/skips. All 13 module lint XML reports present with zero Error/Fatal. Evidence: next-debug-gate.log and next-gate-results.json. Normal incremental outputs/caches were used.
- `git diff --check` and scoped diff review passed. No product/configuration/workflow or snapshot data changes by this implementer; main-session documentation/release work was preserved.
- No source edits after the passing gate. Python release validation is owned by the main session.
- Device tests not run per project policy; no local packaging, commit or push by this implementer. No runtime speedup claim.
