# Whole-test deletion audit: reader and non-AI/profile JVM tests

Read-only source research for the next planning scope. No tests executed and no implementation changes made. Candidates below are whole files or methods, not assertion trimming. Paths are repository-relative.

## Recommended candidates

### 1. Delete ArticleConvertFactoryTest.java entirely (2 methods)

Path: `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/ArticleConvertFactoryTest.java:20`.

Both `missingGlobalFallsBackWithoutChangingOtherPageData` and `missingMalformedOrNonStringFieldFallsBack` exclusively exercise `ArticleConvertFactory.resolveAttachmentsPrefix(JSONObject)`. Repository-wide source search finds that package-private helper at `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:101` has no production callers; all five calls are in this test file. The live facade decodes typed wire data and calls `ReadThreadLegacyMapper`; its line 25 uses `NgaImageHost.attachmentsPrefix(wire.attachmentBaseView.value)` directly.

Retained owners: `lib_core/src/test/java/gov/anzong/androidnga/core/thread/ReadThreadWireDecoderTest.kt:98` (`missingNullAndWrongShapeRemainDistinctForOptionalFields`) covers missing/null/malformed global and non-string attachment prefix extraction. `NgaImageHostContractTest` covers fallback/sanitization. All facade snapshot and `PageAttachmentPrefixFlowTest` consumer tests remain. This is an obsolete execution path test, not a deletion of active page-prefix protection. The now-unused production helper can remain unchanged in a test-only scope; deleting production code would be a separately explicit scope extension.

### 2. Delete FilterWordModelTest.kt entirely (1 method; explicit weak-smoke loss)

Path: `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/activity/compose/filter/FilterWordModelTest.kt:10`, method `testConvertEntity`.

The method logs four sample results and checks only `result.isSuccess`; it never checks any decoded user/keyword list. An implementation returning two empty lists for every successful response passes. It is low-information smoke coverage rather than a meaningful parsing contract.

This is NOT duplicated by `FilterUpdateDecodeTest`: that test exercises `convertUpdateResult`, a different operation. Repository search found no other direct `convertEntity` test. Deletion intentionally loses the only smoke protection that these four remote-filter payloads avoid failure. `LegacyStorageGoldenTest` protects local filter persistence, not remote list parsing. Recommend deletion only with this modest but real coverage loss explicit; do not claim remote parsing remains fully covered. No replacement test is proposed for this deletion scope.

### 3. Delete productionAttachmentProjectionRejectsOldRawFallbackAndNullEntries (1 method)

Path: `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/ReadThreadFacadeParityTest.kt:83`.

This method manually deserializes legacy bean values and asserts exact `ClassCastException` / `NullPointerException` from `buildAttachmentData`. It explains why the old synthetic renderer could accept values the production projection rejected; it does not run the current facade.

Retain the containing file's actual-facade snapshot method and all container/fallback suites. Container inputs `attachment-invalid-size` and `attachment-invalid-subid` preserve current null outcomes through a renderer that calls real `buildAttachmentData`; main snapshots preserve current `null-attachment` behavior (which is success, not rejection). Thus the loss is exact direct-helper exception behavior for historical raw/null entries; do not incorrectly claim a remaining facade test verifies an NPE. The baseline provenance explanation stays in comments/research. This is a historical demonstration candidate, not justification to delete migration snapshots.

### 4. Delete malformedEscapesDegradeWithoutThrowing (1 method; low-risk unique loss)

Path: `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionTextTest.java:37`.

Confirmed the sole production decoder caller is `ArticleSelectionActionModeCallback.java:125`, receiving `WebView.evaluateJavascript` callback values. The three inputs are malformed JSON string escapes, outside the documented normal callback contract. Keep missing/quoted/unicode result decoding and blank-selection tests. Intentional loss: defensive behavior for malformed engine output or a future non-WebView caller is no longer pinned. This is a low-risk input-domain decision, not duplicate coverage.

## Secondary candidates, lower priority

- `lib_base_common/src/test/java/gov/anzong/androidnga/common/util/NgaImageHostContractTest.java:143`, `legacyNgacnAttachmentHostIsRewritten`: a single retired `.ngacn.cc` attachment replacement. `multipleOccurrencesAreAllRewritten` retains repeated attachment replacement using `.nga.178.com`; `unnumberedNonAttachmentHostBecomesUnnumberedNgaCn` retains unnumbered `.ngacn.cc` migration on another path. Can delete this one-method combination check, but explicitly lose `.ngacn.cc` in the attachment-specific regex; these are different regexes, so this is not exact duplicate proof. Prefer the stronger obsolete-helper candidate first.
- `nga_phone_base_3.0/src/test/java/sp/phone/param/ArticleLinkEntryContractTest.kt:15`, entire one-method file: manifest configuration snapshot of a browsable custom-scheme filter. Parser tests do not prove Android resolver entry. Whole-file deletion would exchange the sole automatic manifest-entry guard for manual manifest review. Do not include by default: the entry can break while parser tests remain green.

## Migration fixture overlap was checked, not inferred

All `inputs.json` files were parsed with Python solely for data comparison, not test execution. Both exact raw-string comparison and parsed-JSON equality produced the same result:

| Suite | Inputs | Both-mode outcomes |
| --- | --- | --- |
| read-wire-parity | 78 | 156 |
| read-wire-fallback-parity | 52 | 104 |
| read-wire-container-parity | 36 | 72 |

- Main versus fallback: zero equal inputs.
- Main versus container: zero equal inputs.
- Fallback versus container: exactly two equal inputs, `attachment-size` / `attachment-invalid-size` and `attachment-subid` / `attachment-invalid-subid`.
- Even these two use different renderers: container invokes production `buildAttachmentData` before recording the row; fallback uses a recording renderer and explicitly documents the old raw-attachment artifact. Container frozen outcomes are null; fallback historical outcomes are success with explicit current-null exceptions.

Similar snapshot helper code does not establish redundant cases. Whole-file removal would drop unique aliases, null assignment order, coercion, sparse traversal, renderer event order and compatibility outputs. `.trellis/spec/backend/ordinary-read-wire-contract.md:91` still requires successful facade output and render/lookup order parity. Recommend retaining all three fixture datasets and snapshot methods; do not regenerate frozen baselines or mark migration evidence obsolete without a separate accepted compatibility decision.

## Other examined tests to retain

- `LegacyStorageGoldenTest`: frozen read/write compatibility, transient-field exclusions and old-reader compatibility. Local persistence migration has ongoing data-loss risk; no whole-test deletion identified.
- `ArticleLaunchTargetTest`, `ReplySearchNavigationTest`, `ArticleRowPresentationTest`, `ArticleErrorsTest`: distinct state/navigation/source-availability/access-stop outcomes, not redundant data-class tests.
- `TabLayoutWithPagerContractTest`: despite its name, executes repeated gesture moves with stale rendered slots; not a source snapshot.
- `FunctionUtilsAvatarTest`: direct URL, nested extraction and passthrough are distinct adapter paths. Lower image-host tests do not exercise nested extraction.
- `InlineEmoticonSizeTest`: distinct AC width, other-family natural size and narrow-editor clamp behavior; only three methods.
- `EmoticonUtilsContractTest.fileNamesAreUniqueWithinEachCategory`: inventory check is a persisted-identity precondition, not merely fixed copy/count. Keep.
- `ArticleSelectionActionModeContractTest`: sole narrow source guard for implicit external text export; retain in this scope.

## Suggested next scope

Candidates 1–4 remove two whole files and two further methods: five test methods total (2 + 1 + 1 + 1). Keep all parity datasets. Candidate 1 has the strongest evidence; candidates 2–4 intentionally stop protecting limited weak-smoke/historical/invalid-input behavior and must be described that way in the planning summary. No assertion-only rewrites or new replacement tests are needed for this proposal.
