# Research: LNGA signed HTML extraction and native rendering

- Query: Can LNGA's signed HTML reader preserve native reading and provide a portable alternative for this project's failed reads?
- Scope: mixed; source and checked-in offline fixtures only, no live NGA requests.
- Date: 2026-10-02
- Source: `apap6628114/lnga_harmony`, commit `df6cfcc2e4cfc2efa95269aac398707e9d4d3e81` (read from local Git ref without performing Git operations).
- Source root: `/tmp/nga-api-comparison-WnvQGx/lnga_harmony`. Paths below are relative to this root unless explicitly identified as current-project files.

## Findings

### Reachable read-to-display path

1. `entry/src/main/ets/pages/ThreadPanel.ets:1147` calls `getThread`; `:2183` renders `PostItem`.
2. `service/api/ThreadApi.ets:67` (under `entry/src/main/ets/`) builds POST `/read.php` with `tid`, `pid`, `authorid`, `page`, `__localres=1`, output `17`; `:191` calls the signed transport, extracts wrapper `html`, and executes `parseHtmlTask` in ArkTS taskpool. This is the current production path, not just a tool experiment. `fetchThreadRaw` requires a session (`:284`), is shared with pid lookup and offline saving, and returns null plus an error on failure. `getThread :308` calls `fillThreadResult :459`, rejects zero usable rows before filtering can obscure the distinction, and has no JSON fallback.
3. `parser/task/HtmlParseTask.ets:1` delegates to `parser/nga/html-thread/index.ets:48`.
4. HTML extraction assembles synthetic `{data:{__R,__U,__T,__F,__ROWS,__R__ROWS,__R__ROWS_PAGE,__PAGE}}`; `parser/ThreadParser.ets:14` parses it into ThreadData using actual `Object.keys(__R)` iteration; `ThreadApi.ets:490` maps rows/users to PostInfo.
5. `common/components/PostItem.ets:104` passes `getCachedBBNodes(post.content)` to `BBCodeContentView`, with separate attachments/comments/hot replies. `parser/bbcode/BBCodeCache.ets:71` calls `parseBBCode`; `parser/bbcode/parser.ets:52` preprocesses then parses native nodes. `common/components/BBCodeContentView.ets:581` dispatches image nodes, `:591` video nodes.

This is neither a hidden browser nor a DOM rendering engine. It scans HTML string markers and **reads literal arguments of embedded JavaScript calls as data**, then reuses a BBCode/native rendering chain. No `eval`, `new Function`, or `executeJavaScript` occurrence was found under `entry/src/main/ets/parser`. This statement applies to this parsing/display path; the app separately has login and WebView screens. It does not execute a challenge or establish browser verification state.

### Extraction inventory and limits

All following parser files live under `entry/src/main/ets/parser/nga/html-thread/`.

| Data | Implementation evidence | Limits relevant to a port |
| --- | --- | --- |
| Main/reply body and subject | `DomMarkerExtractor.ets:19,52` selects exact single-quoted `postcontentN`, `postsubjectN` markers and unescapes HTML entities | Not a general HTML parser; first `</p>` or `</span>` ends body, regardless of nesting. Missing marker becomes empty string. Must not classify that as complete editable source. |
| PID, author UID, timestamp, type, scores, client | `PostArgScanner.ets:99` scans balanced `commonui.postArg.proc(...)` and reads fixed argument positions; requires >=21 args | Non-numeric pseudo-floor `_pid` / `__pid` skipped from normal rows; malformed numbers often become zero. Positional protocol changes can silently corrupt meaning. |
| Users / groups / avatar / signatures | `PostArgScanner.ets:168` finds first `commonui.userInfo.setAll`, balances braces, sanitizes then `JSON.parse`s | Failure returns `{}`; ThreadApi `:497-542` preserves available name/avatar/signature/rvrc/group/mute/reputation/post-count. No independent author IP/location projection found in html-thread parser, ThreadParser, PostInfo or post-item components. |
| Main-post poll | `index.ets:59,109,183`; `PostArgScanner.ets:311` extracts setDefault argument; ThreadApi `:584` backs main-row vote from topic | This preserves poll payload, not proof that vote/like request succeeds. Display scores come from postArg, not a server-confirmed interaction. |
| Attachments | `AttachParser.ets:21` scans `ubbcode.attach.load('postattachN',...,'postcontentN',[...])`, extracts url/name/type/thumb/size/dscp/original-name/aid | Fields use single-quoted regexes, no general JS literal parser; numeric/unquoted/double-quoted fields and escaped quotes need negative fixtures. Missing list becomes empty attachments. `subid` is locally generated index. |
| Hot replies | `HotReplyParser.ets:57` scans highlight container and `__pid` metadata; `index.ets:131` reuses same-page original row attachments | Attachments unavailable if corresponding original row is absent; marker spelling and spacing are significant. |
| Comments | `CommentParser.ets:67` scans `comment_for_<parentPid>`, ends at `postsign<lou>`, extracts comment poster/body/time and `_pid` metadata | Reuses same-page original row metadata and attachments; otherwise sparse defaults. Also mutates matched original row with comment_to_id/reply_to and a synthetic subject. Leaf comment structure; absent closing boundary can extend scan into later page HTML. |
| Page / count / topic identity | `index.ets:51-54,147-215`: `__CURRENT_TID/FID/PAGE/PAGE_POSTS`; setDefault fixed positions for replies, OP ID and lastpost | Defaults are tid/fid=0, page=1, size=20. Topic creation time zero off first page; missing OP ID falls back to first current-row author. lastposter is last current-page author. locked/recommend/digest/type hardcoded zero; unknown must not become authoritative false in a port. |
| PID navigation | `ThreadApi.ets:369` requests last page then binary-searches last PID per page; `:382-394` derives totals | Assumes increasing PID and clamps page size to at least 20. Does not prove all lookup/author-filtered layouts use that size; current project must preserve explicit source coordinates and real target identity. |

### Native body support is broader than HTML reconstruction

`model/BBCodeNode.ets:4` includes text/bold/italic/underline/strike/color/size/font/URL/image/quote/collapse/code/list/PID/UID/TID links/mention/reply-header/emotion/video/audio/dice/warnings/album/float/alignment/table/style/flash/paragraph/sub/sup/heading nodes. These describe renderer capabilities **if source markup survives extraction**. They do not prove the HTML reader reconstructs every feature.

`parser/bbcode/lexer.ets:168-231` converts `<br>` to newlines, recognizes a small HTML subset (blockquote, double-quoted HTTP links, NGA emotion images), and strips other HTML tags while protecting code. Arbitrary HTML layout, attributes, scripts and handlers are not rendered by a browser. Generic HTML images/tables are not automatically equivalent to retained `[img]`/`[table]` source. Native image/media loading is a later side effect, outside this offline parser investigation.

### Critical incompatibility with this project's ordinary adapter

LNGA uses absolute page floor numbers as `__R` keys and sets `__R__ROWS = maxLou + 1`, not number of populated slots (`index.ets:116,147,213`). Its ThreadParser traverses keys, so a page containing only floor 20 is accepted with declared count 21. The current project's `.trellis/spec/backend/ordinary-read-wire-contract.md` explicitly requires traversal by `__R__ROWS`, indexed-row validation, separate total/current counts and original kind/presence evidence. Feeding LNGA synthetic data directly into that decoder would fail or force harmful relaxation of existing contracts.

A portable implementation should extract a distinct HTML-source result with explicit known/unknown/incomplete metadata, then use a source-specific app adapter. It must not claim synthetic defaults are original JSON evidence, and must not pass extracted display HTML as editable original BBCode. Preserve native navigation/reply/cache eligibility based on real PID/TID/body completeness and explicit page basis.

### Offline evidence and its actual strength

- `tools/bbcode-ts/tests/app-html-parser.test.ts`: four synthetic tests for pseudo-comment floor exclusion, fixed-position pagination without trailing page-size argument, OP metadata first page and cross-page absence.
- `tools/bbcode-ts/tests/app-html-mode-coverage.test.ts:7`: comparison requires **90% visible-text coverage**, selected strict fields, topic/page/user names and comment counts/content checks. This is not byte-for-byte source or rendering equivalence. Attachments and every rich-content node are not required by this APP test's strict-field list.
- `tools/bbcode-ts/samples/app-html-pairs.lst`: five checked-in pairs, only two thread IDs (one first page plus four consecutive pages of another thread). Real files exist. Useful regression fixtures, not a representative success-rate sample.
- `tools/bbcode-ts/tests/html-coverage.ts:74` supports sample-specific floor remapping. **The current `samples/html-pair-gaps.json` explicitly retracts its old floor-shift claim** after PID comparison: hidden floor 11 is absent in both HTML and JSON, remaining floor numbers agree. Do not cite that obsolete claim as proof HTML floors are wrong. Sparse floor slots remain a real adapter consideration.
- `entry/src/test/AppThreadUnit.test.ets:166` covers current wrapper/error-page extraction, metadata, edits, comments and ThreadParser. At `:346`, a synthetic HTTP-403-shaped error body is passed to `parseNgaError` directly. That is a pure helper test, **not a transport test proving successful HTML recovery from HTTP 403**. It is not itself obsolete; interpreting it as a successful live 403 fix would be wrong.
- Desktop HTML-pair tests exercise the shared extractor, but runtime accepts APP output17 only (`html-thread/index.ets:4`). Old web-HTML/JSON comparisons do not establish current signed-request behavior.
- Inspected `tools/bbcode-ts/package.json`: `test` builds with tsc then node:test; inspect/fetch commands can contact NGA, sync writes production sources. No `node_modules` or built `dist` is present. No installs/builds/tests were run in this research; inspection and fixture inventory only. This avoids writing outside the assigned research directory or accidentally calling fetch commands.

### Portability, licensing and proposed acceptance fixtures

Portable: balanced string scanning, non-executing argument extraction, independent response-to-native-model boundary, fixtures for error-envelope and source completeness. ArkTS `@Concurrent`, taskpool, AppStore/session, Harmony logger/model classes and ArkUI view code are platform-specific; an Android port needs its own worker/cancellation and existing account/source/rendering boundaries. `LICENSE:1` contains GPL version 2. Treat copying or translating source as reuse requiring license/notice compatibility review; do not assume a platform rewrite removes obligations. No implementation license decision is made here.

Minimum offline experiment acceptance cases:

1. Success output17 wrapper with nonzero success code (521), first page and later page; exact TID/PID/page/OP and row ordering. Retain unknown topic flags rather than zero-fill.
2. HTTP403, login/rate/permission/error envelopes and `template=std_error` even HTTP200/code521; preserve reason without returning empty success or automatically sending another request.
3. Missing/truncated postcontent with otherwise valid postArg, nested spans, double quotes, entity escapes, Unicode and script-like body text. Never execute anything; mark untrustworthy source incomplete; no source-dependent edit/cache.
4. Missing middle floor, page2 absolute floor keys, filtered sparse floors, pid-only reply and missing target. Count current slots independently of total/floor maxima; resolve identity from response evidence.
5. Poll, score, avatar/anonymous/user missing, edited body, inline relative image/media, attachment with escaped quote and non-string size, comments/hot replies with off-page originals.
6. Quotes/tables/collapse/code/mixed HTML-BBCode and literal markup: compare complete source where available plus native structure and image/link targets, not only 90% plain-text coverage.
7. Large/truncated response and malformed balanced calls: bounded parsing, cancellation, no UI/network side effects. Existing raw-response size/charset/security policy remains transport-owned.

## Related Specs

Current project: `.trellis/spec/backend/ordinary-read-wire-contract.md`, `thread-detail-compat-contract.md`, `nga-platform-access-rules.md`, `network-foundation-contract.md`; adjacent `thread-page-cache-contract.md`, `thread-page-prefetch-contract.md`, `author-profile-location-contract.md`; frontend `reader-media-contract.md`. Backend/frontend indexes and relevant ordinary/compat contracts were read. These prohibit treating display defaults as reliable source and require source/query/account identity to remain explicit.

## External References

Pinned repository: https://github.com/apap6628114/lnga_harmony/tree/df6cfcc2e4cfc2efa95269aac398707e9d4d3e81 . Per-file links can be built with `/blob/<commit>/<path>#L<line>`. No external live NGA or current-service validation was performed.

## Caveats / Not Found

This establishes a technically plausible native reader architecture, not a lower HTTP403 rate. Changing response extraction cannot make an already-rejected HTTP request succeed. Success must be established separately by same-account/same-thread/same-network comparison with explicit live-test authorization. Existing offline fixtures demonstrate some extraction fidelity but have narrow topic coverage, tolerant text thresholds and no guarantee of editable-source parity. This report deliberately does not recommend copying LNGA's lenient missing-field defaults or replacing the current production reader.
