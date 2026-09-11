# Research: Field-wise App content reuse after scope revision

- Query: Replace the old blanket rejection of nonempty App metadata with the widest source-supported reuse of upstream content, replies, comments, and identity behavior, without inventing an attachment/parent protocol or silently losing core content.
- Scope: Internal source inspection and offline planning only. This revises the content recommendations in `parser-render-adaptation.md`; it does not activate the task or change product code.
- Date: 2026-09-11
- Baselines: Current checkout identified by the dispatch as `fix/thread-menu-cache@5bb92cf0`, including the `6203dad5` menu/cache repairs. `U:` below means the already exported upstream `22ba3082` snapshot at `/tmp/nga-upstream-august-2026-review/upstream-22ba3082/`. The App DTO/parser are durably pinned at their originating `2becba2acc3f6c85340424cd09bb03fa7d759db0` in [upstream-source.md](upstream-source.md).

## Findings

### 1. The correction to the previous plan

An unused optional field is not a requirement of the upstream native reader. The old policy converted every nonempty `attches`, `hot_post`, `comment_to_id`, `html_head_extra`, or true `isTieTiao` into a whole-page failure. That was a new local exclusion, not a limitation enforced by upstream.

`ThreadInfoAppParse` actually reads each `result` entry, its source body/title, ordinary scalar identifiers, nested author, and the root topic metadata. It renders every result independently. The fields above are declared in `ThreadAppBean` but never consulted by that parser. Neither unknown extra metadata nor a non-20-row shape should cause this content adapter to reject otherwise usable source. Pagination validation has its own owner.

The revised guarantee is concrete: preserve every returned row's usable source and actual identity; reuse known inline content and known comment/reply behavior; retain uninterpreted extensions in the original response. It is not a claim that upstream already decodes every optional App sidecar. A body, attachment, or parent relation that is actually unavailable must not be represented as successfully reconstructed.

The user's broader direction and complete August inventory are [scope-revision.md](../scope-revision.md) and [adoption-map.md](../../09-11-upstream-august-2026-review/research/adoption-map.md). Earlier exclusions in the old design and research are historical recommendations, not requirements for this revision.

### 2. What upstream consumes, retains, or ignores

App declaration anchors use `U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt`; App conversion anchors use `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt`.

| Field / behavior | Actual upstream source | Revised local contract |
| --- | --- | --- |
| `result` | DTO `:29` is a list; parser `:43` iterates `result.size`, `:96` reads first result for tid without checking emptiness. | Decode a real list, preserve order and every row; validate before first-row access. Do not filter malformed rows out and call the remainder a complete page. A typed empty-result outcome, if supported by the request-mode design, is different from malformed success. |
| `content`, `subject`, `alterinfo` | DTO `:110`, `:130`, `:102`; parser `:50` maps alter info, `:53` uses `content.ifEmpty { subject }`. It does not preserve a separate nonempty row subject. | Reuse source fallback. Set non-null `row.content`; retain a separate subject when body is present, but clear it when subject became the body. Keep `alterinfo` for the existing hidden/moderation fallback. Do not put formatted HTML into source fields. |
| `attches` | DTO `:104`: nullable **String**, with this spelling. No App parser use and no assignment to `ThreadPostInfo.attachInfo`. | Retain opaque raw value. Do not rename it to legacy `attachs` or pretend a String is the existing attachment map. Inline images/audio/video in body still render through existing decoders. Nonempty or unusual-shaped `attches` alone does not reject a page. If actual core content is only available through an undecodable sidecar, use the unavailable-content outcome below. |
| `hot_post` | Root DTO `:19`: nullable String. No App parser use. | Retain; no extra hot-reply rendering is implied. Do not map it to legacy per-row `"17"` or infer its delimiters. Existing normal-parser `hotReplies` retention remains. This fork does not have a proven full hot-reply UI to transplant. |
| `html_head_extra` | Root DTO `:21`: nullable String. No App parser use or injection into HTML. | Ignore for rendering and retain in raw response. Nonempty scripts/styles/markup in this optional field do not invalidate the independent body. Do not execute it, append it to the template, or concatenate it into source. |
| `comment_to_id` | DTO `:108`: nullable String. No App parser use; there is no App parent-resolution algorithm. | Retain an opaque relationship reference. Its nonemptiness is not a body error. Do not infer that a numeric string is a parent PID, a floor, or an array index; do not synthesize nested comments from it. A typed reply link already present in body is separately supported. |
| `isTieTiao` | DTO `:118`: Boolean default false. App parser never maps it; `ThreadPostInfo.isComment` stays false (`U:.../model/ThreadInfo.kt:153`). | Add an explicit local row-kind projection so a true Boolean can render as a comment without losing its body. This is a local adaptation based on the declared marker and existing comment UI, not an already-working upstream App mapping. Details below. |
| `vote`, score | DTO `:136` has nullable `vote`, mapped by parser `:51`; `vote_bad`/`vote_good` at `:138`/`:140` are unused. The App DTO contains **no `score` field**. Upstream display model score defaults to 0 and UI displays it. | Preserve valid `vote` as the current poll payload. Preserve good/bad extension values without inventing `score = good - bad`. App score is unknown; hide its number instead of presenting an invented zero. The normal endpoint's explicit score and standalone vote buttons remain. |
| `code`, `msg` | Root DTO `:9` has nullable Int `code`, `:25` has String `msg`; neither is checked by `ThreadInfoAppParse`. | Do not invent success numbers, and do not veto every nonnull code. The operation classifier owns errors. A structurally valid, identity-consistent data envelope may be accepted independently of an uninterpreted code; a recognized rejection/challenge still wins. See section 6. |
| Reply-header HTML | Feature patch `2becba2a.patch:351` adds recognition of `<b>Reply to [pid=…,…,…]Reply[/pid] …</b>`. | Reuse this exact additional dialect through narrow source normalization into the current BBCode form, then use the shared decoder. Ordinary BBCode/HTML already accepted by the renderer remains accepted. |

Source-level consumer search covered the exported `lib_core*` trees. The ignored fields have DTO declaration hits only. “Retain” above means the new adapter retains the complete original decoded response, as upstream already does in `ThreadInfo.rawData`; it does not mean upstream copied these fields into its display model.

### 3. Optional values and sentinels: acceptance is not a semantic claim

The source supplies nullable String declarations for `attches`, `hot_post`, `comment_to_id`, and `html_head_extra`, an empty-string default for `content`, and false for `isTieTiao`. There is no included App response fixture establishing live sentinels. The upstream unit test reads absent/untracked `tem.json` and prints rather than asserting (`U:lib_core/src/test/java/com/client/androidnga/core/parse/ThreadInfoAppParseTest.kt:9`).

| Value shape | What the evidence establishes | Recommended handling |
| --- | --- | --- |
| Absent/null optional String | Nullable DTO is compatible with no value. | Continue; preserve raw input. |
| Empty/blank optional String | A String is declared; blank contains no displayable source text. No field-specific marker meaning is established. | Continue; no attachment/parent/hot-post object is created. |
| Literal Strings `"0"`, `"[]"`, `"{}"` | No official or fixture-backed “empty sentinel” mapping was found. | Accept as opaque optional values. Do not call them proven empty, reject the page, or construct content from them. |
| Nonempty String, JSON array/object, number, or Boolean in an ignored optional extension | Non-String shapes differ from the declared DTO. The App renderer does not consume the extension at all. | Preserve the original node in raw data; skip its projection. Schema drift in an unused extension is a local degradation, not a body failure. Never coerce arbitrary object/array values into body strings. |
| `isTieTiao: true` / `false` | Boolean is the source-declared type; false is its DTO default. | Project the marker as below; neither value is a page rejection. |
| `isTieTiao: 0`, `1`, `"true"`, or another wrong-shaped value | No wire-level Boolean coercion contract was found. | Keep the raw marker; use unknown row kind when classification would otherwise be ambiguous. Readable body still renders. Do not grant comment-sensitive actions based on invented coercion. |
| Body/identity has an object/array where a source string/integer is needed | These are consumed, not optional extensions. | A failed projection is a real row-level failure, not something to paper over by stringifying or assigning 0. |

This deliberately removes the old need to prove the meaning of every harmless sentinel before showing body content. It does not promise to render standalone attachments encoded by undocumented `attches`. If later fixtures establish that encoding, one field decoder can add it without changing this acceptance policy.

### 4. Minimum row, parent, and identity model

There are three different situations; they must not be conflated:

1. **Ordinary App reply:** upstream already maps and renders it independently, including a sparse author object. Preserve that. `fid == 0`, missing avatar/signature/level, or nonempty `comment_to_id` alone cannot redefine it as a malformed post.
2. **App row explicitly marked `isTieTiao=true`:** preserve its source, author, post time, own PID/TID, and response position. Project an explicit comment kind. Display the body as a comment, with no invented ordinary floor number, and reuse comment menu rules. It need not be nested to be readable.
3. **Known normal-endpoint nested comment:** parentage is established by `parent.comment` membership. Current `ArticleConvertFactory.java:225` recursively maps that known structure; upstream `ThreadInfoParse.kt:83`, `:124` does the equivalent. Keep it and mark that nested row explicitly as a comment if introducing the shared row-kind field. Do not route this known case to unsupported content.

The smallest useful logical metadata is:

- `kind = POST / COMMENT / UNKNOWN`, with absence of new metadata preserving old row behavior. For App: true Boolean → COMMENT; false Boolean → POST when ordinary row identity is usable; missing/null marker can follow upstream's ordinary-row default when identity is otherwise coherent. A contradictory/malformed marker can remain UNKNOWN without losing its body.
- `parent = none / known parent row identity / opaque reference`. Only known legacy nesting presently establishes a parent relation. Keep App `comment_to_id` opaque; even if its digits equal another returned PID, that coincidence is not proof of the protocol. An actual `[pid=…]Reply[/pid]` body link continues to work as a reply link, not proof of containment.
- Row identity comes from the row's validated `tid`, `pid`, optional `lou`, and request context. Do not use parent ID or topic author ID as the row's own identity. A PID of 0 can be the thread root when known tid/root context establishes that meaning (`ArticleListAdapter.java:205`); 0 cannot stand in for a missing reply PID.
- User identity comes from nested `author.uid`. Missing uid is optional presentation data, not a reason to throw away body. Profile/filter/blacklist actions require an actual usable user identity; generic placeholders and anonymous sentinel IDs do not authorize those actions.
- A nullable OP decision and known-score indicator are needed at the legacy UI boundary. These can live in one small optional row-context object or additive fields; a wholesale replacement of `ThreadRowInfo` is unnecessary. Coordinate floor/page fields with the pagination owner instead of adding a second pagination model here.

The current `FunctionUtils.isComment` uses missing alterinfo/attachments/comments/avatar/level/signature (`:393`); the upstream replacement uses missing alterinfo/attachments/comments/signature plus `fid == 0` and missing level (`U:ThreadInfoParse.kt:115`). Both are heuristics. Reuse the explicit `isComment` display concept and menu behavior, not a second heuristic that mislabels sparse App replies.

For known comments, transplant upstream's hiding of `menu_post_comment` and `menu_show_this_person_only` (`U:ArticleListFragment.java:176`). Keep this fork's repaired menu order and removals. `menu_edit` already checks `FunctionUtils.isComment` locally (`ArticleListFragment.java:87`); its predicate must use the same explicit kind. Unknown kind may read normally while actions that require an ordinary editable post remain unavailable. Ordinary replies retain ordinary reply/filter behavior when their source and identity are valid.

Do not replace an unresolved comment's PID with its parent reference to make actions appear to work. A comment can be shown with an unresolved parent; an action that requires the missing relation simply cannot manufacture it. Keep all result entries in the read result, and let the pagination owner separate displayed entries from numbered posts.

### 5. UID OP badge and content rendering interoperability

Upstream App parser sets `isThreadAuthor` by `postInfo.authorId == threadBean.tauthorid` (`:59`); its normal parser uses root `__T.authorid` (`ThreadInfoParse.kt:199`). The current `FunctionUtils.handleNickName` compares the display name against a topic-owner name (`:317`), and current `ArticleListFragment` initializes that name from a row with `lou == 0`.

Reuse UID comparison on every page and request mode, including PID and author-filter results. Require actual known, usable identities before equality: `0 == 0` or a shared anonymous sentinel must never produce an OP badge. Compute a known false result as well as true; unknown identity may preserve an established nonempty, non-placeholder legacy-name fallback, but App placeholders must not become OP through `Objects.equals(null, null)`. Anonymous display/masking remains separate from badge calculation and must not expose a hidden real identity.

Keep the existing shared rendering seam in `ArticleConvertFactory.java:145` and `:162`:

1. Decode/validate the App envelope and consumed fields into fresh rows. Map author display, blacklist state, source, and known optional content before HTML.
2. Resolve the **complete page attachment prefix** with `NgaImageHost.attachmentsPrefix(rawAttachPrefix)`. Never copy upstream's `split("/")[0]` truncation.
3. Normalize only the recognized HTML reply header to `[b]Reply to …[/b]`. Use one pure helper for the editable source that also goes to the renderer, with literal-safe capture replacement. Leave unrelated `<b>`, HTML entities, BBCode quotes, and body content unchanged.
4. Set known attachments and known nested comments before `buildHtmlData`. The normal parser already does this. Do not import upstream's normal-parser defect assigning `attachInfo` after `HtmlConvertFactory.convert` (`U:ThreadInfoParse.kt:87`, `:92`).
5. Invoke the existing `HtmlConvertFactory` once per fresh row; store HTML separately from source and collect images once. Preserve text/image preferences, vote, subject, signature, dark mode, blocked/hidden rendering, and the local image-host rules.

Source-based quote/edit is important: `ArticleListAdapter.java:195` and `ArticleListFragment.java:97` consume `row.content`, not formatted HTML. The current reply-header stripping regex understands the BBCode version (`ArticleListAdapter.java:194`), so the narrow outer-header normalization lets rendering and quoting agree. Do not introduce a general HTML-to-BBCode converter or substitute the fully rendered document into the composer. A source that truly cannot support lossless editing can still be readable; disable only the affected edit/quote operation, not native reading of every other row.

The current core already supports plain BBCode, inline images, relative typed video/audio, polls, and quote links. Keep `ForumBasicDecoder.java:238`/`:241` audio/video with the local complete prefix. The August untyped `[flash]./…[/flash]` rule and video CSS can be added through the shared decoder/style work item; never overwrite the audio rule or replace this fork's image-host helper with upstream's weaker host handling.

Historical WP source unescaping in `ArticleConvertFactory.java:150` must remain on the normal-response path. Extract the source-neutral HTML stage without deleting it. `from_client=103` in an App row establishes a client family, not that the new endpoint returns the same escaped body encoding; do not unescape arbitrary App source twice. Preserve detailed `from_client` text as well as the existing client-family icon.

The known nested comment builder has a separate defect: `HtmlCommentBuilder.java:39` takes `indexOf("[/b]")`, then unconditionally `substring(end + 4)`, dropping the first 3 characters or throwing if no header exists. Do not feed unrecognized App comments through this path just to get a comment appearance. Independent App comment rows can use the ordinary row renderer. If sharing the nested builder with additional inputs, strip only a complete recognized header and keep the entire body otherwise; keep each comment's own avatar (`ArticleConvertFactory.java:196`).

### 6. Success, local degradation, and actual unavailable content

The content adapter must consume a classified data envelope, not decide business success from `code == 0` or HTTP 200. Conversely, the old rule “any unknown nonnull code means rejection even with valid data” has no support in upstream and would unnecessarily reject data it renders.

Recommended operation-boundary order:

1. Apply the operation's already-evidenced HTTP/auth/challenge/rate-limit/business-rejection classification. A recognized failure cannot be overridden by residual data, a matching tid, or a success-looking numeric code.
2. Validate the data structure and request/row identities independently. `result` must not be a scalar/error object cast into a list. Unknown code/message metadata can remain uninterpreted when the independent data branch is valid. A numeric code alone does not gain invented success or failure semantics.
3. A missing/malformed data branch is an explicit error/unknown response. Empty result handling belongs to the request-mode design; do not read `result[0]` or publish a zero-page success by default. An envelope with positive contradictory evidence remains ambiguous/error rather than becoming success merely because it has a `result` key.

Field-wise outcomes:

| Situation | Outcome |
| --- | --- |
| Usable source/identity for every returned row; unknown extra root/author/sidecar fields | Native reading. Preserve original response. Omit unsupported optional decoration without treating it as an error, and do not claim undocumented sidecars were rendered. |
| Known inline attachment/media path in body; unrelated opaque `attches` | Use the existing body decoder and complete page prefix. Opaque sidecar presence does not cancel visible media or body. |
| Explicit empty source string with coherent row identity, no conflicting evidence of missing content | Existing empty/hidden presentation is acceptable. A missing field default is not by itself evidence that the server deliberately hid a body. |
| Null/empty body but nonempty source subject or server alter/moderation text | Use the upstream fallback/current alterinfo path; do not discard the row merely for empty body. |
| Missing/unusable body and no usable title/alter source, or source available only through an undecodable content-bearing structure | Actual unavailable core content. Keep a clear browser/error outcome; do not silently label it hidden, drop it, or call its substitute a complete successful post. |
| Valid visible body plus a positively identified unsupported content dependency/section | Keep the readable native content if the UI can explicitly mark the unavailable part and provide the existing browser action; otherwise return the existing unsupported-content route. An opaque field's mere existence is not this positive evidence. Never quietly remove a recognized section. |
| Missing author/avatar/buff decoration, invalid optional reputation, unknown score | Local presentation degradation; body remains readable. Disable only actions needing the missing identity, and reset recycled view state. |
| Unknown/opaque App parent relation with a readable comment body | Show the comment source in returned order with unresolved parent; do not fabricate nesting, floor, or parent jump. |
| Essential row identity conflicts with the requested thread/target, or malformed result entry would have to be discarded | Invalid/incoherent result. Do not publish a partial list as the requested full result or cache it as such. |

“Preserve raw” is not permission to claim missing visible content is rendered. Nor should a generic unknown-field rule become a new warning on every post. Report an unavailable section only when that section is actually identified as unavailable. Internally retain coverage distinctions if needed for actions/cache, without putting protocol field names into product UI.

Cache/request owners must retain actual request mode, page mapping, and raw format. A native author-filter/PID result does not become a full-thread cache merely because its body is readable. No raw metadata enters logs, exception messages, generated DTO `toString()`, or task fixtures.

### 7. Smallest adapter/DTO design

Keep the previous research's useful seam and change its acceptance policy:

- Copy/adapt upstream `ThreadAppBean` field names and `ThreadInfoAppParse` scalar/user mapping into the app's Kotlin boundary, retaining upstream GPL/source attribution. Do not import the entire upstream model/refactor chain or migrate fastjson globally to get this feature.
- Parse via the current JSON DOM with explicit extraction of consumed fields. Build a small typed DTO; make essential field presence observable rather than letting default zero/empty stand for successful decoding. Unused extensions need no strict reflective mapping. The full original response already preserves them, so avoid duplicating it into every row.
- Map into current `ThreadData`, `ThreadPageInfo`, and `ThreadRowInfo`; add only the small explicit row-kind/identity/known-score context required by current consumers. Reuse shared client and anonymous-name leaf helpers, blacklist lookup, avatar normalization, and rendering. Do not manufacture legacy `__U`/`__GROUPS` objects to call the old parser.
- Keep pure source normalization and DTO projection testable with injected blacklist/render callbacks. The renderer is Android/assets dependent and uses static mutable decoder state; this work does not establish concurrent-render safety.
- Keep original normal-endpoint attachments/comments/hot-field handling and WP preprocessing. If a known App sidecar schema is later evidenced, decode just that field into the existing attachment/comment display types before HTML.

This is actual reuse of upstream DTOs, row conversion, UID badge, quote dialect, and comment presentation with local compatibility edits. Omitting an undocumented sidecar decoder that upstream itself never implemented is not a reason to reject the whole upstream feature.

### 8. Meaningful offline cases for the revised implementation

These are proposed assertions, **not executed tests**. Use synthetic content/IDs and no live account or service.

| Case | Required assertion |
| --- | --- |
| Valid ordinary App row with all ignored fields absent; repeat with null, blank, `"0"`, `"[]"`, `"{}"`, nonempty String, object, array | Same body/row identity stays readable; original raw data preserved; no manufactured attachments/parents or global unsupported outcome. Wrong-shaped unused extensions do not crash reflective deserialization. |
| Nonempty `html_head_extra` with synthetic script/style and valid body | Extra is not injected/executed; body is retained; result is not rejected just for the extra. |
| Inline image + typed audio/video + complete path-bearing `attachPrefix` and opaque `attches` | Existing decoder produces expected page-scoped URLs and image list; manual override/legacy host behavior stays intact. |
| Sparse normal App reply with no avatar, level, signature, alterinfo | Explicit POST kind prevents the old missing-fields heuristic from labeling it a comment. Ordinary usable author-filter action stays available. |
| `isTieTiao=true`, nonempty source, real own PID/TID, opaque parent ref, no lou | Body appears as a comment without invented floor or nesting; comment menu hides the two upstream actions; no row is dropped. Quote/report cannot use the opaque parent as own PID. |
| Wrong-shaped `isTieTiao`, readable body, valid tid/pid | Native body survives; uncertain comment-sensitive actions are not granted through Boolean coercion. |
| Known legacy nested comment, own avatar, source with and without recognized reply header | Existing nesting remains; own avatar is used; adapting shared header stripping never removes the first characters of an ordinary body or throws on short content. |
| HTML reply header followed by BBCode/line breaks; adjacent headers; unrelated `<b>`; `$` and backslashes in content | Only recognized header boundaries normalize. Same source reaches quote/editor and renderer, reply PID/TID link remains correct, content outside the header is unchanged. |
| Root author UID present on a later page/PID/filter result; same name but different UID; 0/0; anonymous sentinel/placeholder | UID determines known true/false OP state, missing/sentinel equality never creates a badge or profile identity, source remains readable. |
| Optional author fields missing/invalid, both mute buff IDs, anonymous source tokens | Body remains; blacklist/mute/avatar/anonymous mapping preserves local behavior; invalid identity does not create UID 0 actions or expose hidden names. |
| App good/bad values present; normal endpoint explicit score; recycled row views | No inferred App score/zero display; known legacy score is shown; visibility resets on every bind; poll action remains separate. |
| Valid data with absent/null/arbitrary numeric code and benign msg | Data validation determines native success; no guessed success-number table and no blanket nonnull-code rejection. |
| Recognized auth/challenge/error plus result; code-only envelope; scalar result; missing essential body/target mismatch | Error wins or data validation fails; no success/cache side effects and no silent row filtering. |
| Explicit empty body vs absent/unusable body vs subject-only/alter-only source | Intended supported fallbacks work; unavailable core source is not silently turned into a successful hidden row. |
| Normal endpoint attachment/comment/blacklist data passed to shared renderer; legacy escaped WP body | All source data is present before HTML; retained features and detailed client text are not lost by extraction. |

### Files found and source anchors

- `U:lib_core_data/src/main/java/com/client/androidnga/core/data/bean/ThreadAppBean.kt:5` — declared App root/result/author fields; no standalone App score or nested comment DTO.
- [upstream-source.md](upstream-source.md) — durable feature-commit copies of the App DTO/parser/helper with provenance links.
- `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoAppParse.kt:25` — actual consumed fields, independent result-row conversion, raw response retention, unchecked first row, UID OP comparison.
- `U:lib_core/src/main/java/com/client/androidnga/core/parse/ThreadInfoParse.kt:83` — known nested comments; `:92` attachment-order defect; `:115` comment heuristic; `:199` normal-path UID OP.
- `U:nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:176` — upstream comment menu behavior to reuse.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ArticleConvertFactory.java:118` — existing shared row pipeline; `:145` WP/source handling; `:162` attachments/comments-before-HTML; `:212` distinct legacy hot field.
- `nga_phone_base_3.0/src/main/java/sp/phone/http/bean/ThreadRowInfo.java:14` — current row identity, body/HTML, attachments/comments, score fields.
- `nga_phone_base_3.0/src/main/java/sp/phone/util/FunctionUtils.java:317` — current name-based OP; `:368` avatar normalization; `:393` sparse-field comment heuristic.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java:188` — source quote, actual row target; `:282` current profile behavior; `:456` unconditional floor/score labels requiring local adaptation.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:87` — comment edit guard; `:113` identity-dependent author filter.
- `lib_core/src/main/java/gov/anzong/androidnga/core/HtmlConvertFactory.java:24` — blocked/hidden/alter/body branch and common builders.
- `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlCommentBuilder.java:39` — unconditional header slicing that must not be applied to arbitrary comment source.
- `lib_core/src/main/java/gov/anzong/androidnga/core/decode/ForumBasicDecoder.java:52` — current BBCode reply handling; `:238`/`:241` local relative video/audio.

### Related specs and external references

- Read `.trellis/workflow.md`, backend/frontend spec indexes, and `.trellis/spec/guides/code-reuse-thinking-guide.md`: research is persisted, code remains untouched, reuse existing data/rendering seams.
- `.trellis/spec/backend/nga-platform-access-rules.md:285`, `:304`, `:378`, `:395`: operation-owned decoding, typed data/error separation, raw-data privacy, no live requests during offline research. Page-prefix contract remains required.
- `.trellis/spec/frontend/component-guidelines.md:447`: preserve repaired menu removals/order and standalone vote behavior; cache contract remains with its owner.
- `.trellis/spec/frontend/android-migration-architecture.md:50`, `:69`, `:83`: new Kotlin adapter, preserved user-visible/source contracts, justified stable Java renderer, GPL provenance.
- Upstream feature: https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/2becba2acc3f6c85340424cd09bb03fa7d759db0 . Local exported source/patch inspected; no new fetch.
- Prior focused research reused: [parser-render-adaptation.md](parser-render-adaptation.md), [upstream-source.md](upstream-source.md). This file supersedes its blanket optional-field rejection recommendations, not its verified source anchors or local preservation requirements.

## Caveats / Not Found

- No real App response fixture, success-code mapping, `attches` encoding, `hot_post` encoding, `comment_to_id` target semantics, vote-score formula, or live coverage measurement was found. Source DTO declarations/defaults are not a current-service guarantee.
- Upstream does **not** implement App nested-comment reconstruction or standalone App attachment projection. Their absence must be explained honestly, not turned into a blanket exclusion of readable rows, and not filled with invented protocols.
- App `isTieTiao` → explicit local comment kind is an adaptation recommended here; upstream's App parser ignored the marker. Unknown parent references remain unresolved.
- If the revised UI cannot visibly represent an actually unavailable content section, browser/error fallback remains necessary for that response. This is narrower than rejecting every nonempty optional field.
- Pagination, request-mode caching, transport/session/lifecycle, and final detailed-plan approval remain with the main task. No product edits, Git operations, builds/tests, live requests, account reads, or device operations were performed for this topic.
