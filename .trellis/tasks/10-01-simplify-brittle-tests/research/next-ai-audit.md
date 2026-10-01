# Research: Further AI/profile test deletion audit

- Query: Identify genuinely unnecessary whole test methods, retaining security, cancellation and migration boundaries.
- Scope: internal; representative AI configuration, parser, transport, summary, model editor and author profile tests.
- Date: 2026-10-01

## Findings

### Recommended whole-method deletion

`nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/AiModelEditorStateTest.java:15`, `discoveryWithoutAConfiguredModelPreservesTextTypedWhileLoading` (approximately 13 lines including annotation).

This initializes an empty editor, types a draft during loading, delivers choices, and checks loading/custom/draft/choice behavior. Remaining owners in the same test class are:

- `reopeningTheSameModelRejectsSuccessAndErrorFromThePreviousEditor` at line 114: explicitly verifies LOADING, edits a draft during loading, accepts the current result, preserves the draft and publishes choices, additionally rejecting old success/error deliveries.
- `matchingDiscoveryResultDoesNotSwitchAwayFromCustomInput` at line 28: verifies custom mode and draft are preserved even when provider choices contain the entered text (a stronger selection edge case).
- `aDuplicateCallbackCannotReplaceACompletedResult` at line 143: opens with an empty model, accepts choices and preserves its first completed result. Existing `withCachedModels` helper also successfully opens an empty model and loads choices in several retained tests.

Production evidence: `AiModelEditorState.java:19` unconditionally opens with the supplied current model; it has no empty-model special branch. `modelsLoaded` at line 32 only validates generation/status and updates available choices, never the custom draft. The deleted scenario combines already-owned operations on the same production path. Explicit residual loss: no single remaining method combines *initial empty model + typing + first successful choices*; all individual invariants and the relevant branch are retained. This is a low-risk redundant scenario deletion, not a claim of mathematical equivalence under arbitrary future implementations.

### Keep: implementation-property test contains unique safety owners

`nga_phone_base_3.0/src/test/java/sp/phone/ai/AiSummaryClientTest.java:397`, `productionClientHasHttpAndTlsWithoutCookieAuthenticationLoggingOrRetryHooks` must not be deleted wholesale under the current preservation rules.

Behavioral overlaps exist: system/response cookies (233), authentication challenge (254), one-shot 503 handling (300), redirects (318), body timeout (338), and disconnect retry handling (349). However proxy authentication, empty application/network interceptor lists (including absence of body logging), exact allowed TLS/cleartext specs, no cache, production timeout configuration, and loopback-only test seam are not all demonstrated by those behaviors. Production transport factories are `AiSummaryClient.java:279`, derived summary transport at 298, and model transport at 309. It could be narrowed, but doing so would be assertion cleanup rather than a safe whole-method deletion.

### Keep: parser migration/safety cases

`AiResponseParserTest.java:112` (`typeAndReferenceKeysAreOrdinaryData`) and line 122 (`decimalPrecisionAndNestedSpecialKeysRemainOrdinaryData`) overlap in special-key parsing but are not safe deletion recommendations: one checks root keys plus actual completion parsing, the other nested keys and decimal/large-integer precision. The JSON compatibility contract explicitly preserves ordinary root/nested special keys and BigDecimal precision. `SafeJsonParser.java:18` calls Fastjson2 with local `DisableReferenceDetect`/`DisableSingleQuote`; `AiResponseParser.java:43` routes typed completion decoding through the bounded decoder. Do not characterize these as testing irrelevant third-party behavior.

### Keep: other sampled methods protect distinct behavior

- `SummaryInputTest.java:91` has a single-item loop, but its remaining zero-floor case protects floor zero availability and presentation snapshot stability; the ordinary floor snapshot case at 24 uses floor 7. A pointless loop can be simplified without deleting the necessary test.
- `SummaryControllerTest.java:75` actually invokes `AiSummarySources.floor` under each saved profile style, so it adds integration ownership beyond `SummaryInputTest` testing the DTO's prompt directly.
- `AiSettingsContractTest.java:22` is brittle source inspection but remains the only inspected host-side owner for transient API-key editor persistence/autofill/capture protections; do not drop it merely because it is text-based.
- `ProfileSessionTest` has only three methods and each covers distinct trusted-origin/credential construction/replacement behavior.
- `AuthorLocationStoreTest` persistence tests cover independent migration, TTL, rate-limit, network cooldown, cache partitioning and corruption behaviors; no persuasive whole-method duplicate found in the sample.
- The two prompt DTO tests and three prompt editor tests protect different validation, privacy and transactional editing boundaries.

## Files Found

- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/AiModelEditorState.java`: transient model selection/cache/generation owner.
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/AiModelEditorStateTest.java`: recommended deletion and retained same-layer owners.
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/AiProfilePromptEditorStateTest.java`: prompt editing/confirmation/cancel tests.
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/AiSettingsContractTest.java`: source/XML secret persistence guard.
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiSummaryClient.java`: isolated production transport factories.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiSummaryClientTest.java`: fake-server behaviors and transport properties.
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/SafeJsonParser.java`: bounded, locally configured JSON decoder.
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/AiResponseParser.java`: typed completions and model list decoding.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiResponseParserTest.java`: shape, resource, migration and privacy contracts.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiConfigTest.java`: endpoint/key/model validation.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiProfilePromptTest.java`: custom prompt validation and private diagnostics.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/SummaryInputTest.java`: immutable bounded input snapshots.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/SummaryControllerTest.java`: generation, lifecycle and stream presentation ownership.
- `nga_phone_base_3.0/src/test/java/sp/phone/profile/ProfileSessionTest.java`: trusted session construction.
- `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationStoreTest.java`: cache persistence/expiry/migration.

## Related Specs

- `.trellis/spec/backend/ai-summary-contract.md`: secret isolation, model editor intent, transport policies, summary lifecycle.
- `.trellis/spec/backend/json-compatibility-contract.md:40`: parser flags, exact numeric preservation and ordinary nested special keys.
- `.trellis/spec/backend/author-profile-location-contract.md`: session/cache/lifecycle contract.
- `.trellis/spec/backend/android-quality-guidelines.md`: local verification and device authorization boundaries.

## External References

None; repository evidence only. Tests and code reference the project's Fastjson2 and OkHttp integration rather than external claims.

## Caveats / Not Found

This is a representative static audit, not an exhaustive proof of all 560 tests. No tests run and no product/test code changed. Only one whole-method deletion is strongly supported in this scope. Do not pursue an arbitrary test-count reduction by weakening distinct safety or migration owners. Line anchors describe the inspected working tree and can shift after other agents edit files.
