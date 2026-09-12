# Research: Topic-body response compatibility

- Query: Can the AI profile topic-body collector reject source-backed NGA read.php data that the established reader recognizes, and which regression cases are missing?
- Scope: internal; source comparison and existing sanitized research only.
- Date: 2026-09-12
- Worktree: /home/toph/nga-just-works-ai-summary; feature/ai-summary at parent-supplied baseline 9a8113a9. The parent identified 0482795c as the topic-body introduction; this researcher did not run Git.

## Findings

### 1. Strongest confirmed compatibility gap: raw control characters in quoted NGA fields

The current topic-body path cannot accept a literal LF, CR, or TAB inside any quoted string, even when that field is unrelated to the selected original post. This is a concrete source-level incompatibility with a previously observed class of native NGA responses; connecting it to the maintainer's latest failure still requires current response evidence or a controlled reproduction.

Evidence chain:

1. The prior authorized native response observation records HTTP 200, text/javascript with GBK, and strict JSON rejection at a string control character. It also records a subsequent legacy-Fastjson failure, so it must not be represented as a fully successful historical legacy parse: `.trellis/tasks/08-08-read-php-contract-utilization/research/read-php-evidence.md:49-53`.
2. `NgaTopicBodyParser.parse` normalizes the complete response and delegates to the shared data parser before selecting an original: `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaTopicBodyParser.java:28`.
3. The local normalizer copies quoted segments verbatim; `quotedEnd` locates an ending quote but does not convert raw controls into JSON escapes: `NgaTopicBodyParser.java:119-123,153-165`.
4. `NgaProfilePageSource.parseData` invokes `SafeJsonParser.parseObject`: `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaProfilePageSource.java:432`.
5. The shared preflight throws for every literal character below U+0020 inside a string, including LF, CR, and TAB: `nga_phone_base_3.0/src/main/java/sp/phone/ai/SafeJsonParser.java:42-44`. This scan covers ignored metadata as well as original-body content.
6. That runtime failure becomes the exact UI message `NGA 内容格式异常，请稍后重试`: `NgaProfilePageSource.java:44,446-447`.

The established reader has an additional concrete compatibility rule absent from the new normalizer: it removes an `alterinfo` string consisting of a bracketed word/whitespace label followed by whitespace, before direct Fastjson parsing. Its regex permits literal whitespace through `\\s`: `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:58-60`. A literal newline in this exact marker therefore gives a particularly small differential sample without relying on broad claims about Fastjson leniency.

This is not a reason to weaken the shared model decoder. Its rejection of a raw LF is explicitly tested at `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiResponseParserTest.java:148-159`. A proposed correction should adapt the known NGA representation locally before entering that decoder, preserve quote/escape semantics and resource bounds, and retain all original-post identity checks.

### 2. Sanitized payload for the parent's compiled-production reproduction

Java construction, to be invoked in the existing parser's package or reflectively. All identifiers and content below are synthetic. The Java `\n` escape must become an actual LF in the runtime string; passing JSON text containing the two characters backslash+n only tests the already-working escaped form.

```java
String baseline = "{\"data\":{\"__T\":{\"tid\":7300},\"__R\":{\"0\":{"
        + "\"tid\":7300,\"authorid\":42,\"lou\":0,"
        + "\"alterinfo\":\"ALTERINFO_PLACEHOLDER\",\"content\":\"BODY_SENTINEL\"}}}}";
String rawLf = baseline.replace("ALTERINFO_PLACEHOLDER", "[edit]\n ");
String escapedLf = baseline.replace("ALTERINFO_PLACEHOLDER", "[edit]\\n ");
// NgaTopicBodyParser.parse(rawLf, "42", "7300") => format error in current code.
// NgaTopicBodyParser.parse(escapedLf, "42", "7300") => BODY_SENTINEL.
```

The `alterinfo` field precedes another field so the legacy repair's required trailing comma is present. The payload deliberately omits optional topic author metadata but includes the topic TID and the original's TID, author ID, and explicit floor zero. Current tests explicitly allow the omitted topic author: `NgaProfilePageSourceTest.java:395-404`.

Useful second case: place `prefix` + a literal TAB or LF + `suffix` in an otherwise ignored `__U` metadata string while leaving the original unchanged. The entire preflight still fails. This isolates full-envelope compatibility from whether the original body itself needs escaping.

No runtime execution is claimed by this researcher. The parent owns the existing compiled-production harness and the final observed outputs.

### 3. Why the focused green tests do not cover this shape

- The body fixture helper constructs JSON objects and finishes with `root.toJSONString()`: `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/NgaProfilePageSourceTest.java:967-994`. Serialization escapes control characters, preventing the native wire variant from reaching the parser.
- The existing numeric/wrapper test replaces a placeholder with numeric tokens and adds known wrappers, but never injects literal controls into strings: `NgaProfilePageSourceTest.java:465-476`.
- The quoted-marker test checks preservation of literal comment markers and escaped quotes/backslashes; it does not exercise raw string controls: `NgaProfilePageSourceTest.java:480-484`.
- The metadata-isolation test has a plain signature sentinel and ordinary nested metadata: `NgaProfilePageSourceTest.java:361-368`. It proves projection isolation after decoding, not tolerance of source-backed wire forms in ignored metadata.
- Existing detail-failure integration coverage checks redirect, access denial, mismatched author, unsupported charset, and oversize responses: `NgaProfilePageSourceTest.java:792-821`. It does not cover this malformed-for-strict-JSON but NGA-observed representation.

Suggested missing regression cases for a reviewed fix:

1. Literal LF/CR/TAB in the known whitespace `alterinfo` shape, paired with escaped equivalents; the verified original text must be identical.
2. Literal controls in ignored metadata, paired with an unchanged original; metadata must remain excluded from model input.
3. Literal controls in the original text, if that exact compatibility policy is approved; decoded text should preserve its content before the existing plain-text projection.
4. Existing escaped backslashes, escaped quotes, and literal wrapper text in quoted content must remain unchanged.
5. Source-to-loader sequence using a valid topic list, the control-character detail, and replies: confirm the expected failure before repair and complete collection after repair; no extra detail requests, account changes, or model calls on a genuine failure.
6. Normalization expansion near the current input bound, plus all existing malformed envelope, identity, depth, special-key, and shared-model JSON rejection tests.

### 4. Comparison with existing behavior and remaining hypotheses

| Area | Existing source evidence | Current AI behavior | Assessment |
| --- | --- | --- | --- |
| Request shape | `ArticleListModel.java:49-66` builds page/output/noprefix/v2 with TID | `NgaProfilePageSource.java:318-326` builds the same fixed operation for page one | No request-parameter discrepancy found |
| Known wrappers | `ArticleConvertFactory.java:48-56` strips error-fill/js markers and repairs numeric text | `NgaTopicBodyParser.java:114-139` handles these outside strings | The common wrapper/numeric forms already have tests |
| Whitespace edit metadata | `ArticleConvertFactory.java:58` repairs bracketed whitespace `alterinfo` | Quoted metadata passes unchanged into strict preflight | Confirmed code-level gap; strongest deterministic sample above |
| Leading-zero author text | `ArticleConvertFactory.java:57` quotes this token | New normalizer only treats content/subject at `NgaTopicBodyParser.java:124` | Source difference; not established as a parser failure or present user trigger |
| Missing original content | Legacy renderer falls back to row subject at `ArticleConvertFactory.java:145-149` | AI rejects absent content and requires a verified original | Deliberate tested boundary (`NgaProfilePageSourceTest.java:452-461`); do not invent a title-only repair |
| Original selection | Legacy rendering maps all rows to beans at `ArticleConvertFactory.java:125-139` | AI requires explicit floor zero plus matching TID/author at `NgaTopicBodyParser.java:53-81` | Intended attribution protection; no evidence that removing it fixes this report |
| Other rows | Legacy skips non-object rows at `ArticleConvertFactory.java:126-132` | AI rejects malformed rows/floors before projection at `NgaTopicBodyParser.java:45-70` | Potential additional compatibility difference; no source-backed current malformed-row example found |
| Current exact response | Historical observation is from 2026-08-08 | No current live request made by this researcher | Today's failing stage/field cannot be claimed solely from historical evidence |

### 5. Why one topic-body decode failure stops the whole profile operation

The collector reads the topic list, then enriches its retained topics sequentially. A detail response invokes `NgaTopicBodyParser` at `NgaProfilePageSource.java:164-168`; its `PageException` reaches `fail` at lines 179-180. `fail` stops the load, clears accumulated entries, cancels its call, and notifies the outer loader at lines 244-264. The reply list is only requested after a successful topic result at `ProfileSummaryLoader.java:115-118`. Therefore this failure can occur before the reply list and before prompt/model submission, despite being presented as a general AI feature error.

The all-or-error behavior matches the current contract and should not be replaced with silent title-only success to hide the compatibility defect: `.trellis/spec/backend/ai-summary-contract.md:310-314`.

## Files found

- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaTopicBodyParser.java` — local NGA normalization and verified original-post projection.
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaProfilePageSource.java` — request sequence, bounded decoding, shared envelope parse, and UI-safe errors.
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/ProfileSummaryLoader.java` — topics-before-replies orchestration and terminal error propagation.
- `nga_phone_base_3.0/src/main/java/sp/phone/ai/SafeJsonParser.java` — shared bounded preflight that rejects raw string controls.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java` — established source-backed response repairs and legacy rendering behavior.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java` — existing THREAD.PAGE wire construction.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/NgaProfilePageSourceTest.java` — synthetic detail fixtures and collection regression coverage.
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/AiResponseParserTest.java` — explicit shared-decoder rejection of raw LF.
- `.trellis/tasks/08-08-read-php-contract-utilization/research/read-php-evidence.md` — sanitized historical response evidence and its limitations.

## External references and versions

- No external or live service contacted.
- The operation registry pins original Justwen source to `5d807617f8058950f7ea81dda405e38fb0cc37ec`: `.trellis/spec/backend/nga-platform-operation-registry.md:5-10`.
- The historical native-response experiment identifies Fastjson `1.1.71.android`: `.trellis/tasks/08-08-read-php-contract-utilization/research/read-php-evidence.md:53`. This researcher did not re-resolve dependencies or execute Gradle.

## Related specs

- `.trellis/spec/backend/index.md` — active contracts and offline-first investigation requirements.
- `.trellis/spec/backend/ai-summary-contract.md:251-314` — topic/reply collection, bounded encoding, explicit original identity, and unavailable-versus-malformed handling.
- `.trellis/spec/backend/nga-platform-access-rules.md:269-302` — operation-owned decoding and source-backed wrapper normalization.
- `.trellis/spec/backend/nga-platform-access-rules.md:370-415` — sanitized diagnostics and separately authorized live validation.
- `.trellis/spec/backend/nga-platform-operation-registry.md:42-43` — TOPIC.LIST and THREAD.PAGE wire/source references.
- `.trellis/spec/backend/network-foundation-contract.md:64-87,242-267` — legacy parser evidence, identity headers, privacy, and no account rotation.

## Caveats / Not Found

- No current raw response, credentials, device data, or private content was inspected or recorded. All example values are invented.
- The exact control character and owning field from the historical response were not retained. The LF/alterinfo example is a deterministic source-derived sample, not a quotation of a captured response.
- The historical follow-up legacy parse itself failed with `unclosed str`; known normalization may not repair arbitrary malformed responses. Retain genuine parse errors.
- `references/nga-clients` is absent from this feature worktree. Original-source lineage relies on the maintained pinned registry and current legacy implementation; this researcher did not reconstruct another checkout or run Git.
- Runtime reproduction, exact current-failure attribution, repair-plan approval, implementation, and final regression results remain with the parent task. Product/test/spec files were not changed here.

## Follow-up sanity review: current reproduction and proposed repair (2026-09-12)

Reviewed the parent's `research/live-result.md` and `design.md` after the initial research was written. The current-response limitation above is now resolved for the probed target by the parent's separately recorded evidence; this researcher did not make or repeat the requests.

- `research/live-result.md:13-34` reports a successful topic-list parse, followed by a native detail containing 53 literal TAB characters inside strings. The unchanged production parser emitted the exact reported error. Escaping only those controls in the same in-memory response yielded a verified 141-character original body. This differential supports attribution to the local representation/preflight boundary, after successful GBK decoding.
- `research/live-result.md:47-63` correctly limits attribution: the owning fields were not retained, the harness did not execute an APK, and one successful comparison does not establish universal NGA compatibility. No overclaim requiring correction was found.
- `design.md:11-32` places normalization at the existing NGA topic-detail boundary, preserves the strict shared model parser and original identity checks, and retains limits and real-error paths. This is supported by the evidence and the current contract.
- The planned fixture mutation after serialization directly addresses why earlier tests hid this response class. The metadata and full collection cases in `design.md:48-58` cover the consequential failure boundaries.

Two nonblocking clarifications for implementation and regression review:

1. Explicitly distinguish an existing JSON TAB escape, text containing a literal backslash followed by `t`, and an invalid backslash immediately followed by a literal TAB/LF/CR. Preserve the first two; reject the third instead of silently turning an invalid escape into new text. Test controls next to escaped quotes/backslashes so the quote scanner's state is exercised.
2. Specify the supported literal-control set as U+0009/U+000A/U+000D (TAB/LF/CR) for the proposed scope. Today's observation confirms TAB; the LF/CR cases are compatibility regressions supported by the established whitespace handling. The broad phrase “raw controls” in acceptance item 3 should not unintentionally extend compatibility to every U+0000–U+001F character. Unknown controls can retain strict rejection unless deliberately specified with evidence.

The design already calls for normalization expansion limits and preservation of identity/cancellation/model-parser regressions. No further live request is needed for these regression checks. Implementation and its validation remain pending the main task's review gate.
