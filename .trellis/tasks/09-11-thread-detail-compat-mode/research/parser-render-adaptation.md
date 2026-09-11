# Research: App API parser and native renderer adaptation

> Scope revision: the original source findings below remain evidence; conservative scope/admission recommendations are historical. Use ../design.md and the content-reuse-revision.md / query-pagination-revision.md reports for the current selected behavior.

- Query: What is the smallest credible reuse of upstream `ThreadAppBean` / `ThreadInfoAppParse` with this fork's `ThreadData`, `ThreadRowInfo`, and renderer? Which payloads can be supported without a real App API fixture?
- Scope: Internal source comparison and offline planning; no implementation, Git operations, network, account, or device access.
- Date: 2026-09-11
- Baseline: Root checkout identified by the dispatch as `fix/thread-menu-cache@5bb92cf0`, including the `6203dad5` cache/menu repair. Source anchors below refer to that checkout. `U:` means the exported upstream `22ba3082` snapshot in `/tmp/nga-upstream-august-2026-review/upstream-22ba3082/`; the feature originated in `2becba2acc3f6c85340424cd09bb03fa7d759db0`.

## Findings

### Recommended boundary

Use an app-level Kotlin DTO and adapter derived from the upstream field declarations and scalar mapping. Keep the existing Java presentation models and `lib_core` HTML pipeline. This does **not** require moving models, adding Kotlin to `lib_core_data`, migrating fastjson1, introducing `ThreadInfo/ThreadPostInfo`, or replacing the Presenter.

Recommended sequence inside the new parser boundary:

1. The operation's response classifier accepts a data envelope; parser validation checks required structure, identity, and the content subset below. Do not deserialize absent numeric fields directly into zero and infer success from that default.
2. Decode into an app-local `ThreadAppBean`-derived DTO. Preserve original field names; retain nullability/presence for fields whose absence matters. Use the existing `com.alibaba.fastjson` DOM at this boundary with explicit type checks, then construct the Kotlin DTO. This avoids relying on fastjson1's reflective construction of upstream Kotlin primary constructors or silently coercing arrays/objects into strings.
3. Map to fresh `ThreadData`, non-null row list, non-null page metadata, and fresh `ThreadRowInfo` instances. Metadata/paging validity is owned by the other planning topic; do not index `result[0]` until validation establishes a usable result.
4. Resolve `NgaImageHost.attachmentsPrefix(rawAttachPrefix)` once for the page. Populate user state and supported row data **before** rendering. Pass the complete prefix as an argument, never a global or `ThreadRowInfo` field.
5. Invoke the shared app renderer once per fresh row. Preserve source content in `row.content`, HTML in `row.mFormattedHtmlData`, and the complete original decoded response in `ThreadData.rawData` for the format-aware cache layer.

The adapter should have testable pure decoding/mapping functions and injected `isBlocked(uid)` / row-render callbacks, or equivalent small seams. Production binds the existing blacklist manager and renderer; offline tests need not initialize `ThemeManager`, `PhoneConfiguration`, Android assets, or account storage. Do not log a DTO, raw input, or exception containing payload text. Prefer an ordinary Kotlin DTO class, or override a data class's `toString()` with a payload-free representation, rather than generating a raw-response-bearing diagnostic string.

### Exact reuse and extraction seams

All paths in this section are under `nga_phone_base_3.0/src/main/java/` unless explicitly qualified.

| Current code / anchor | Reuse or extraction recommendation |
| --- | --- |
| `sp/phone/mvp/model/convert/ArticleConvertFactory.java:145` (`buildRowContent`) and `:162` (`buildHtmlData`) | The renderer seam already exists: `(ThreadRowInfo, completeAttachmentsPrefix) -> formatted HTML + image URLs`. The smallest edit is a package-visible renderer entry in the same package; alternatively move these methods together to one small `ArticleRowRenderer` helper. Keep the legacy call at `:139` on the same path. Do not duplicate the HTML assembly in the App adapter. |
| `ArticleConvertFactory.java:145` | The existing method also normalizes null content and decodes historical `from_client=103 ` content. Keep that preprocessing on the old parser. If extracting a source-neutral renderer, leave `:146–154` in the legacy parser and share `:155–201`. The App parser has its own narrowly defined source normalization below; do not assume the new endpoint still returns WP-escaped text merely because its client code is `103`. |
| `ArticleConvertFactory.java:164` | Preserve every `HtmlData` input: page prefix, alter info, dark mode, blacklist, text/emoticon sizes, optional signature, vote, subject, image-load preference, NGA link host, pid/tid/uid. `:178` maps attachments **before** conversion; `:189` maps comments using each comment's own avatar. |
| `ArticleConvertFactory.java:238` | Extract/overload `buildRowClientInfo(row, String client)` from the JSON-specific method; legacy obtains `rowObj.getString("from_client")` then delegates. Preserve full raw client text and current `ios/wp/android/unknown` categories. Upstream's new enum is unnecessary and would change browser icons/details. |
| `ArticleConvertFactory.java:275` and U `lib_core/src/main/java/com/client/androidnga/core/parse/ThreadParseUtils.kt:7` | The same anonymous-name table/algorithm can become a small app helper shared by both parsers. Preserve output for valid legacy names; add guarded use for the new nullable fields. Do not fabricate a `__U`/`__GROUPS` JSON map just to call the old user mapper. |
| `ArticleConvertFactory.java:262` | Keep legacy `__U` / group lookup intact. Map the App's nested `author` separately into the same row setters; it has `member` directly and no proven legacy group-map equivalent. Share only actual common leaf transformations. |
| `lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java:20` | Unchanged core entry takes `HtmlData` plus an image-output list. It handles blocked/hidden states, body, and builders. Full HTML conversion needs Android text helpers/assets; pure DTO tests must not accidentally call it on the host JVM. |

The source-only extraction must preserve legacy attachment/comment order, WP decoding, and image collection. `buildRowContent` currently appends to `row.getImageUrls()`; call it once on a fresh row rather than re-rendering an existing row and accumulating duplicate image entries. Existing decoder instances are static (`ForumDecoder.java:15`), and `ForumImageDecoder.java:18,59` has mutable image-list state: this research does not establish general concurrent renderer safety. Keep request/concurrency policy with its owner and do not claim that passing a page prefix alone makes every renderer component thread-safe.

### Concrete field mapping

Current row setters are in `sp/phone/http/bean/ThreadRowInfo.java`; public `fid` has a getter but **no setter** (`:15,152`), so the adapter uses `row.fid = dto.fid`. `ThreadData` setters are at `:24,32,40,48,56` in its file. Page metadata setters are in `sp/phone/mvp/model/entity/ThreadPageInfo.java`.

| Upstream field / source evidence | Current destination and local rule |
| --- | --- |
| Root `tauthorid`, `tauthor`, `tsubject`, `fid`; U `ThreadInfoAppParse.kt:90` | `ThreadPageInfo.setAuthorId`, `setAuthor`, `setSubject`, `setFid`. Apply the same safe anonymous display normalization to the root author when it carries the recognized token. Require a usable topic identity/metadata before native success; use validated row tid/request context, not an unchecked first element. |
| Root `vrows`, `currentPage`, `perPage`, `totalPage` | Preserve separately until the pagination owner validates them. `vrows -> ThreadData.__ROWS` is upstream's mapping, not independent proof of reply/page semantics. `rowNum` is the resulting list's actual size. Do not silently drop malformed rows and keep totals unchanged. |
| Root `forum_name`, `forum_bit`, `tmisc_bit1`, `is_forum_admin` | Keep in DTO if needed for diagnostics/forward work; do not guess new navigation/permission semantics or equate `forum_name` with the legacy mirror-board flag. These are not needed for the basic row renderer. |
| Result `pid`, `tid`, `fid`, `lou`; U parser `:46–49` | `setPid`, `setTid`, public `fid`, `setLou`. Validate identity/range/paging together; never derive lou from the result-array index. Identity-dependent actions need an actual valid target. |
| `content`, `subject`; U parser `:53` | Use content as source text; for null/empty content and non-empty subject, use subject as content and clear the row subject to avoid displaying it twice. Guarantee App rows have a non-null content string, including an empty/hidden row. Keep page title from `tsubject`. Do not store rendered HTML in `content`. |
| `alterinfo`, `postdate`, `vote`; U parser `:50–52` | `setAlterinfo`, `setPostdate`, `setVote`. These are explicit upstream scalar mappings. Preserve the raw vote string for the existing poll renderer, distinct from floor score. `postdatetimestamp` is declared but unused by upstream; do not guess seconds/time-zone/date fallback rules. |
| `from_client`; U DTO `ThreadAppBean.kt:116` | `setFromClient` plus the shared current client-family helper. Keep device/application detail text. The mapping of `101` and `100` remains current `ios` / `android`, rather than introducing upstream browser enums. |
| `author.uid`; U parser `:74–75` | `setAuthorid`. Missing author/uid is optional presentation data, not a reason to discard readable body. Use neutral display, leave identity unknown (`0` in this legacy model), and block profile/filter/blacklist actions that require a real uid. Do not substitute the topic author's uid into an arbitrary reply. |
| `author.username`, `author.annoy`; U DTO `:93–95`, parser `:79–81` | Prefer a valid `#anony_` token in username; otherwise a valid token in `annoy` can supply anonymous display. Decode only `#anony_` + 32 hex digits (the current 39-character form). An explicit anonymous marker sets `setISANONYMOUS(true)` even if malformed; use a neutral anonymous label instead of exposing a different identifying username or throwing. Preserve ordinary username verbatim. Upstream checks `annoy` but fails to decode it; that defect is not desirable compatibility. |
| `author.avatar`; U parser `:80` | Keep the existing `js_escap_avatar` / `FunctionUtils.parseAvatarUrl` path, which extracts direct/nested avatar URLs and calls `NgaImageHost.normalizeLegacyHosts` (`FunctionUtils.java:368,389`). All current adapter/comment avatar consumers already call this helper. Do not feed the new raw avatar directly to Glide or concatenate the page attachment prefix onto a profile URL. |
| `author.yz`, `mute_time`, `buffs`; U DTO `:51,73,97` | `setYz` / `setMuteTime` using nullable scalar-to-string conversion; preserve `-1` nuked display. `setMuted` checks the current `ForumConstants.BUFF_MUTE_IDS` (`117` and `105`, `ForumConstants.java:8`), rather than upstream's `105` only. Buff values are not needed to detect presence; inspect object keys rather than requiring every value to be a string. `mute_status` has no established mapping here. |
| `author.rvrc`, `postnum`, `member`, `signature`; U parser `:76,84–86` | `setAurvrc` with safe integral parsing, `setReputation` with safe finite `rvrc / 10f`, `setPostCount`, `setMemberGroup`, `setSignature`. Missing/invalid optional values degrade locally; they must not abort a page. `author.reputation` is a different unused field; do not substitute it for rvrc without evidence. |
| Current blacklist setting; U parser `:82` | Set `set_IsInBlackList(isBlocked(uid))` before rendering, for a usable author identity. Never copy the full blacklist into DTOs or logs. Rendering then follows current `[屏蔽]` behavior. |
| `vote_good`, `vote_bad`; U DTO `:138–140` | Upstream does not map them. No evidence establishes `score = good - bad`, including sign conventions. Retain fields without calculating a score. Recommended tiny presentation extension: `scoreKnown` defaults true for old rows, false for App rows, and adapter hides unknown score. Otherwise the current unconditional `scoreTv.setText(getScore())` (`ArticleListAdapter.java:458`) falsely displays zero. |

`ThreadRowInfo` has no UID-based “thread author” field. Current UI uses a topic-owner name (`FunctionUtils.java:317`; `ArticleListFragment.java:307`). Mapping `tauthorid` does not by itself give upstream's UID-based badge behavior. Keep that separate or deliberately add an optional known author flag with valid nonzero identity comparison; never equate absent uid `0` to absent topic uid `0`. This is not necessary to introduce the fallback endpoint.

### HTML quote normalization without a core change

The exact new upstream rule is in `/tmp/nga-upstream-august-2026-review/patches/2becba2a.patch:351–355`: support `<b>Reply to [pid=…,…,…]Reply[/pid] …</b>` in addition to the BBCode spelling. The current core already recognizes `[b]Reply to ...[/b]` and emits the quote/link style (`lib_core/.../decode/ForumBasicDecoder.java:52–59`).

For the new App adapter only, normalize **that complete recognized header's outer `<b>` pair** to `[b]` / `[/b]`, preserving its interior, then run the current decoder. Do not globally replace all HTML tags, convert arbitrary HTML to BBCode, unescape every entity, or strip quote blocks. Restrict the pid/tid/page tuple and closing boundary sufficiently to avoid swallowing multiple headers or unrelated `<b>` text. Use literal-safe replacement for captured content containing `$` or backslashes.

Example local contract:

```text
Input:      <b>Reply to [pid=12,34,1]Reply[/pid] Post by [uid=56]reader[/uid] (2026-08-24)</b><br/>[b]body[/b]
Row source: [b]Reply to [pid=12,34,1]Reply[/pid] Post by [uid=56]reader[/uid] (2026-08-24)[/b]<br/>[b]body[/b]
```

This also makes the existing reply editor's header-removal regex work (`ArticleListAdapter.java:193–204`); its quote builder deliberately reads source `row.content`, and editing does too (`ArticleListFragment.java:95–97`). Keep original response text in `ThreadData.rawData`, so this narrow editable-source normalization does not erase cache evidence.

This approach has less behavior impact than editing the shared decoder, and does not touch `lib_core`. If implementation instead changes core, its owned `ExampleUnitTest.testQuote` baseline must be fixed per the quality spec; do not call it unrelated after editing that module. App-level tests can exercise the new pure normalizer and the existing `ForumBasicDecoder` using an explicit `HtmlData` NGA host and attachment prefix. Full template rendering remains Android/asset-dependent. No actual decoder/build tests were run in this research.

### Supported subset and explicit unsupported content

There is no real App API fixture in the upstream snapshot. Its `ThreadInfoAppParseTest` loads untracked `tem.json` and prints without assertions; the previous audit established this gap. The following is a **proposed local supported subset**, not an official API contract or a claim about current server traffic.

| Input | Support / rejection recommendation |
| --- | --- |
| Basic result rows with supported scalar types, coherent page identity/metadata, plain source/BBCode, current supported inline media, and the exact HTML quote variant above | Native adaptation. Use current renderer and collect its normal image URLs. Unknown optional author details degrade locally. |
| `attches` absent, JSON null, or blank string | No standalone attachments declared in the supported subset. Continue. A non-empty string, object, array, numeric sentinel, or malformed value is `UnsupportedContent`, not a guessed attachment map. |
| `hot_post` absent, JSON null, or blank string | Continue without hot-reply extension. Any non-empty/other-typed value is `UnsupportedContent`; do not equate it with legacy comma-separated field `"17"`. |
| `comment_to_id` absent, JSON null, or blank string, and `isTieTiao` absent/null/false | Treat the accepted row as an ordinary post **by the local supported subset**. Do not infer a parent linkage or construct nested comments. Other values or malformed boolean shapes are `UnsupportedContent`. |
| Any row has non-empty `comment_to_id` or `isTieTiao=true` | Reject the entire page for native compatibility rendering. Do not silently drop the row, attach it to a guessed PID, show it as a normal floor, or advertise editable full content. |
| Non-empty `html_head_extra` | No current supported use. Do not inject it into the app template or run scripts. Prefer `UnsupportedContent` until a fixture proves it optional; a future explicitly ignored metadata rule must be recorded. |
| Root `code` absent and otherwise supported valid envelope | Can be accepted by the operation classifier's structure-based success branch. Presence of `code` has no known success contract: do not invent `0`/`200` success. Unknown code values go to an explicit unsupported/error classification; known auth/challenge/rejection policy belongs to the response-classification topic. |

Literal `"0"`, `"[]"`, and `"{}"` are **not** yet proven empty sentinels. If the service emits those on every row, this conservative subset could reject most or all otherwise-readable App responses. There is no evidence to estimate frequency. State that limitation explicitly; widening the subset later needs a redacted fixture and an assertion for what the field means. No additional client archaeology or unauthorized probe is required to complete this plan.

On unsupported content, return one typed failure with a coarse reason (field category, no payload values) and let the foreground owner open the existing internal WebView when its setting allows it, or end loading with a clear compatibility error. Do not cache it as a successful native page, set sticky success state, or retry it as if it were a transport error. The parser itself opens no browser and changes no account.

Why reject the whole page: legacy consumers expect coherent floor identity/count and can quote/edit what they display. A successful partial list would conceal omitted content and could make caches and later actions misleading. This does not prevent current `read.php` rendering from continuing to support its existing attachments/comments/hot-reply data.

### Small presentation adaptations required by that subset

- **Ordinary-post vs comment classification:** `FunctionUtils.isComment` (`:393`) infers a comment when alterinfo/attachments/comments/avatar/level/signature are all null. Sparse ordinary App rows can satisfy that test. Add an optional explicit comment-kind override on `ThreadRowInfo`: old rows leave it null and preserve their heuristic; accepted App ordinary rows set false. Do not fake `level`, an empty avatar, or attachment data merely to defeat the heuristic.
- **Unknown author identity:** current profile click navigates for any non-null name (`ArticleListAdapter.java:282–289`), and author-filter/blacklist menus use the row uid (`ArticleListFragment.java:111–118`). A neutral placeholder author must not activate UID 0 actions. Gate those actions on a valid identity, scoped to new rows or expressed as a tested legacy-safe identity rule. Do not mislabel unknown authors as anonymous just to block actions.
- **Unknown score:** preserve old row score rendering; explicitly hide the App row's unknown score. Reset visibility on every bind to handle recycled views. The positive/negative vote action handlers themselves use tid/pid, not the score; do not repopulate the removed menu entries.
- **Quote/edit source:** source normalization has a non-null output; escaped content and BBCode remain editable, while HTML stays a separate field. No blanket raw HTML-to-BBCode conversion is justified. Other mixed-HTML dialects remain unverified; this is not a lossless parser guarantee.

### Expected touched files for this slice

- New app package files under `sp/phone/mvp/model/convert/`: upstream-derived App DTO + pure decoder/adapter, and a small source normalizer (these may share a file if cohesive).
- `sp/phone/mvp/model/convert/ArticleConvertFactory.java`: renderer/client/anonymous helper delegation only; preserve existing legacy parse behavior and page-prefix extraction.
- Optional one shared app renderer/helper file, preserving the current Java interoperability boundary rather than moving it to core.
- `sp/phone/http/bean/ThreadRowInfo.java`, `sp/phone/util/FunctionUtils.java`, `sp/phone/ui/adapter/ArticleListAdapter.java`, and `sp/phone/ui/fragment/ArticleListFragment.java`: only the explicit comment/unknown-score/unknown-identity presentation needed above. Presentation-only additions must not accidentally become accepted server JSON or change persisted formats.
- Focused app JVM tests and sanitized App fixtures under app `src/test/`. No `lib_core` / `lib_core_data` / common JSON dependency change is required by this recommendation.
- Endpoint, Presenter, page metadata, cache format, settings, and browser URL changes are dependencies owned by the other task design; this report does not prescribe their implementation.

### Meaningful offline test inputs

1. **Equivalent row data:** synthetic legacy and App rows with the same tid/pid/lou, BBCode, subject, author, muted/blocked state, signature, and client text. Assert the same pre-render `HtmlData` inputs and effective prefix; record that the fixture is synthetic, not a server capture.
2. **Missing optional author:** null author, missing username/avatar/member/rvrc, invalid finite-number input. Body remains readable and non-null; no exception, UID 0 action, literal `"null"` author label, or false anonymous identity.
3. **Anonymous cases:** valid username token, ordinary username + valid `annoy` token, malformed anonymous token, and unrelated short username. Assert stable name output, anonymous flag, no accidental identifying-name fallback, and quote UID masking through the current action contract.
4. **Client/user details:** `1`, `7`, `101`, `8`, `100`, `9`, `103`, whitespace, unknown code, and full detail string. Assert retained raw text/current device family. Test buffs `105` and `117`, plus `yz=-1` and mute-time display inputs before rendering.
5. **Quote source + output:** known HTML header vs equivalent BBCode header, two headers, unrelated `<b>text</b>`, missing closing tag, nested quote, and literal `$`/backslash in author/body. Assert narrow normalization, idempotence, equal basic decoder output for supported headers, intact quote/edit source, and unchanged original raw response.
6. **Image context:** `attachPrefix="http://img9.nga.cn/attachments/"`, protocol-relative/bare host, retired host, blank/invalid/non-string, and manual override. Exercise two pages with different prefixes, body `[img]./…[/img]`, historic absolute URLs, signature media, and direct/nested legacy avatar strings. Reuse `NgaImageHostContractTest` and `FunctionUtilsAvatarTest`; do not create another host resolver.
7. **Legacy renderer extraction regression:** supported legacy row with an independent attachment and a comment with its own avatar; assert those reach `HtmlData` before conversion. Keep legacy WP content decoding and current signature/image preferences. Full Android render runtime is not claimed by a captured-input JVM assertion.
8. **Unsupported content matrix:** absent/null/blank vs non-empty `attches`, `hot_post`, `comment_to_id`, true/malformed `isTieTiao`, and sentinel strings `"0"/"[]"/"{}"`. Assert a typed whole-page failure, no partial successful data/render calls, and no successful cache/sticky-mode side effect in the owner's integration tests.
9. **Identity/shape and score:** malformed/null result member, mixed tids, empty result, overflow/wrong-typed scalar, missing code versus present unknown code, unknown App score vs legacy zero/nonzero score. Pagination/classifier tests own the exact success expectations for empty/paged data.

## Files found

- `U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:5` — declared App response fields and nested author/result DTOs; declarations are not fixture-backed wire formats.
- `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:25` — App scalar mapping, early rendering, incomplete optional-field support, and unchecked first result.
- `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadParseUtils.kt:7` — anonymous-name and client-family helpers available for bounded reuse.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:118` — existing row preparation order and renderer seam.
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadRowInfo.java:12` — mutable Java row data, source/HTML/image separation, current presentation flags.
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadData.java:9` — row/page/raw-response holder.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/entity/ThreadPageInfo.java:7` — current page metadata and setters.
- `nga_phone_base_3.0/src/main/java/sp/phone/util/FunctionUtils.java:294` — user labels, avatar extraction/normalization, and comment heuristic.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java:188` — source-based quote action, author/score/avatar consumers.
- `lib_core/src/main/java/gov/anzong/androidnga/core/data/HtmlData.java:158` — complete page-prefix propagation contract.
- `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java:38` — legacy comment formatting assumes a `[/b]` header; another reason not to feed unknown App comment shapes into it.
- `lib_base_common/src/main/java/gov/anzong/androidnga/common/util/NgaImageHost.java:93` — single image-host resolver with manual override and page-local automatic values.
- `nga_phone_base_3.0/src/test/java/sp/phone/mvp/model/convert/ArticleConvertFactoryTest.java:19` — existing prefix parser tests to preserve/extend.
- `lib_core/src/test/java/gov/anzong/androidnga/core/ExampleUnitTest.java:25` — legacy test baseline becomes owned if core changes.

## Related specs and external references

- Read `.trellis/workflow.md`, backend/frontend indexes, and `.trellis/spec/guides/code-reuse-thinking-guide.md`: persist research; use shared boundaries instead of duplicate parsers/renderers.
- `.trellis/spec/backend/nga-platform-access-rules.md`: operation `THREAD.PAGE`, page-scoped full prefix, unknown/unsupported evidence category, raw-response privacy, and offline validation.
- `.trellis/spec/backend/nga-platform-operation-registry.md:43`: existing `THREAD.PAGE` boundary. App fallback is a new fork delta, not a retroactive original-source contract.
- `.trellis/spec/frontend/android-migration-architecture.md`: Kotlin for new code, stable Java/legacy renderer allowed behind a bounded tested compatibility adapter; preserve user data and GPL provenance.
- `.trellis/spec/frontend/component-guidelines.md:447,599`: keep the repaired floor-menu entries/order; standalone support/oppose and the poll dialog remain separate. Native article rows still render through `LocalWebView`, not the unreachable `tv_content` branch; the whole-page `ForumWebFragment` fallback is a different path.
- `.trellis/spec/backend/android-quality-guidelines.md`, validation-gate section: app tests/compile/lint; inspect all module lint errors; any changed legacy module owns its failing test baseline; device operations are not authorized by a general feature request.
- Reused `.trellis/tasks/09-11-upstream-august-2026-review/research/bugfix-browser.md` and `data-refactor.md`; no repeated upstream audit or searches of other clients for missing App wire semantics.
- Upstream source reference: https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/2becba2acc3f6c85340424cd09bb03fa7d759db0 (locally inspected snapshot/patch; no network fetch in this research).
- Current build facts: app Kotlin plugin is already enabled (`nga_phone_base_3.0/build.gradle:3`); `lib_core_data/build.gradle:1` enables only the Android library plugin. Kotlin version is `2.0.21`; existing common fastjson1 and core-only fastjson2 remain unchanged by the recommendation.

## Caveats / Not Found

- No real App API response fixture, verified success-code values, attachment encoding, comment linkage, hot-post format, score sign semantics, or per-page field semantics was found in the supplied evidence. No live availability or coverage claim follows from this plan.
- All acceptance inputs above are proposed synthetic/offline cases, not executed tests. No source files or other task artifacts were changed by this research.
- The conservative unsupported-content rules can substantially reduce fallback success; treating sentinel values or omitted nested content as harmless would require evidence. Preserve a clear browser/error outcome rather than advertise complete native parity.
- The app-level quote normalization supports exactly the source-observed extra dialect, not general HTML editing/sanitization. Existing core/global renderer behavior and unrelated legacy defects are not implicitly fixed.
