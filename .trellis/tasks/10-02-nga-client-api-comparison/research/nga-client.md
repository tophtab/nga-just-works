# Research: ForeverZero/NgaClient API surface

- Query: Compare every implemented NGA operation, transport/session/codec boundary, and useful API lessons against the current fork.
- Scope: mixed (pinned external source inspected locally; current project source and specs).
- Date: 2026-10-02

## Findings

### Identity and evidence level

Repository: https://github.com/ForeverZero/NgaClient, remote `https://github.com/ForeverZero/NgaClient.git`, default/checked-out branch `main`, SHA **`c728a152f3ddffe2e816de6c947362844fe07ab6`**. Read-only filesystem inspection of `.git/config`, `.git/HEAD`, `.git/refs/heads/main`, `.git/packed-refs`, and `.git/refs/remotes/origin/HEAD` established these facts. Checkout: `/tmp/nga-api-comparison-WnvQGx/NgaClient`.

This is a small Java library, not a full application: five public read methods and live-service demonstration tests. README lines 1–7 explicitly attribute the APIs to APK decompilation/captured traffic, disclaim reliability, and say login is unimplemented. All statements below are **source-observed**, not server-verified. No NGA calls, credentials, tests that send requests, or builds were run.

### Files found and pinned source map

All upstream line anchors below refer to this pinned revision. Prefix `J` means `src/main/java/org/zhd/ngaclient/`.

| Anchor | Path and responsibility |
| --- | --- |
| [Client][client] | `J/NgaClient.java`: all five requests, form transport, response validation. |
| [Interface][interface] | `J/INgaClient.java`: public API, including explicit unresolved Chinese-search note. |
| [URL][url] | `J/common/NgaUrl.java`: fixed origin, path/selector constants, query builder. |
| [Constants][constants] | `J/common/NgaConst.java`: default app identity and embedded signing material. |
| [Codes][codes] | `J/common/NgaResponseCode.java`: 0, 5, 53 application codes. |
| [Sign][sign] / [Time][time] | `J/util/SignUtil.java`, `TimeUtil.java`: concatenated MD5 and epoch seconds. |
| [Base][base] / [Page][page] | `J/dto/BaseNgaResponse.java`, `NgaPageResponse.java`: code/message and typed paged result. |
| [Category][category] / [Icons][icons] | `J/dto/HomeCategoryV2Response.java`, `ForumIconList.java`: category and icon metadata. |
| [Subject list][subjects] / [Subject][subject] / [Search][search] | `J/dto/SubjectListResponse.java`, `SubjectInfo.java`, `SubjectSearchResponse.java`: forum context, topic rows, search results. |
| [Post list][posts] / [Reply][reply] / [Attachment][attachment] / [Author][author] | `J/dto/PostListResponse.java`, `ThreadReply.java`, `Attachment.java`, `Author.java`: reply graph and extended raw fields. |
| [Tests][tests] / [README][readme] / [POM][pom] | `src/test/java/TestNgaClient.java`, `readme.md`, `pom.xml`: reachable demonstration calls, documented scope, dependencies. |

### Shared request/authentication contract

All five methods synchronously call `doPost` (`NgaClient.java:142–157`) with **POST** to fixed HTTPS origin **`https://ngabbs.com/`** (`NgaUrl.java:11`). The path and `__lib`, `__act`, optional `_v` are query parameters, built by string concatenation from a `HashMap` (`NgaUrl.java:58–97`); ordering is unspecified and the generic builder does not URL-encode values. Implemented selectors are literal ASCII constants.

Four methods use base form `access_token`, `access_uid`, `app_id` (`NgaClient.java:134–139`) and `t` epoch seconds (`TimeUtil.java:10–11`), plus `sign`. Signature is lowercase MD5 of delimiter-free concatenation **appId + accessUid + accessToken + operationValue + t + secret** (`SignUtil.java:11–35`); operationValue differs per row below. The constructor allows custom app ID/secret or defaults to `app_id=1010` and embedded material (`NgaClient.java:39–59`; `NgaConst.java:4–5`). No login, token refresh, account selection, Cookie extraction, browser flow, or logout implementation exists. Method presence does not prove the server requires every field; these are fields this source sends.

`homeCategoryV2` sends an empty form and no explicit credentials (`NgaClient.java:77–82,145–150`). The underlying client cookie handling is not disabled or configured explicitly, so “no explicit credentials in this request” is stronger evidence than claiming a globally anonymous transport.

### Exhaustive implemented operation ledger

Every row shares the transport/codec/error behavior in the next section. `B` below is the three-field base form described above.

| Product group / operation | Exact request selectors and form fields | Signature operationValue | Expected response and reachability |
| --- | --- | --- | --- |
| Boards / category hierarchy | POST `/app_api.php?__lib=home&__act=category&_v=2`; empty form. | None. | `HomeCategoryV2Response`: root `code,msg,result: List<RootGroup>`, `forum_icon_pre`, `forum_icon_list`, `forum_recommend`, `forum_recommend_lastmodify`. Hierarchy RootGroup → SubGroup → Forum. Implementation `NgaClient.java:77–82`; test `TestNgaClient.java:29–30`; README example line 13. |
| Boards / remote favorites read | POST `/nuke.php?__lib=forum_favor2&__act=get`; form `B,t,__output=12,sign`. | Literal `12`. | `NgaPageResponse<List<Forum>>`, each Forum exposes `fid,name,info,id,is_forumlist,icon` (`dto/Forum.java:11–18`). Implementation `NgaClient.java:86–98`; test line 35. No mutation counterpart. |
| Topic list / forum page | POST `/app_api.php?__lib=subject&__act=list`; form `B,t,fid,sign,page`. | Decimal `fid`. | `NgaPageResponse<SubjectListResponse>`; `result.data` topic rows plus `attachPrefix,subForum,topic_key,sessionPrivilege,header`. Implementation `NgaClient.java:61–73`; test lines 23–25 iterates returned topics. No order/author/collection selectors. |
| Topic search | POST `/app_api.php?__lib=subject&__act=search`; form `B`, optional `fid`, `key,t,sign`. **The declared `page` argument is never sent.** | UTF-8 URL-encoded `key`. | `NgaPageResponse<SubjectSearchResponse>`; `result.data`, `result.msg`, `result.table`. Implementation `NgaClient.java:102–115`; test line 40 uses ASCII `Steam`, null forum, page 0. Interface line 40 says Chinese search does not work. |
| Thread page / replies | POST `/app_api.php?__lib=post&__act=list`; form `B,t,tid,sign,page`. | Decimal `tid`. | `PostListResponse` extends `NgaPageResponse<List<ThreadReply>>`; thread metadata, `hot_post`, `attachPrefix`, `vrows`; reply author/content/attachments/comments/votes. Implementation `NgaClient.java:119–131`; test line 45. Interface lines 51–60 says first entry is topic body. No `pid`, author-only, or explicit per-page request selectors. |

Pagination envelope (`NgaPageResponse.java:11–18`) declares `result,totalPage,total,currentPage,perPage,forumname`. Nullable wrapper fields preserve absence at DTO level, but there is no page identity or range validation. Category response is not this envelope. All five are public library entry points and have test call sites; there is no application UI/use-case caller in this repository. Tests do not establish deployed or successful use.

### All other feature groups: declarations versus supported operations

`NgaUrl.java:26–51` declares unused library names `login,user,app_inner,nga_index,notify,load_topic,ucp` and action names `iflogin,detail,banner_list,get_event_app,load_topic_reply_ladder,recmd_topic,get_block_word`. Whole-source inspection finds no request methods or call sites for them. These are **declaration-only vocabulary**, not seven more APIs. In particular, `notify` must not be silently equated with a working `noti/get_all` implementation in another client.

| Group | Source-supported scope |
| --- | --- |
| Authentication/session | Caller-provided token/UID only; no credential acquisition/renewal or account management. |
| User/profile/location | Author fields embedded in thread DTOs; `user/detail` constants only; no independent profile/location fetch. `ThreadReply.address` is `Object // TODO` at line 14. |
| Posting/reply/edit/comment | No submit or preflight implementation. A `post/list` read and comment DTOs do not constitute write support. |
| Upload/avatar/media fetching | Attachment metadata and URLs only; no network media download/upload, token preflight, or avatar mutation. |
| Favorites/subscriptions | Remote board favorites **read** only; no topic favorites list/add/remove or subscription change. |
| Interactions | Vote counters/markup only; no recommend, vote submit, report, or check-in. |
| Private messages/notifications | No list/read/send/clear implementation; unused `notify` constant only. |
| Settings/block lists/signature | `ucp/get_block_word` constants only; no fetch/set methods. |
| Hot/recommended content | `hot_post` field in thread result; recommendation/category metadata; no separate recommendation/ladder endpoint implementation. |
| Caching/concurrency/retry queues | None in application code. Underlying HTTP library behavior is discussed separately. |

### Encoding, parser, transport, and error behavior

1. **Request encoding:** every body is `UrlEncodedFormEntity(params)` without explicit charset (`NgaClient.java:150`). Search additionally calls `URLEncoder.encode(key, UTF_8)` before the form encoder (`102–110`), so percent signs are encoded again on the wire; spaces become `+` at the inner layer and `%2B` at the outer layer. This source proves two encoding stages, but not whether a server expects either. Chinese search is acknowledged broken; a corrected request cannot be inferred without offline fixtures and an explicit contract. No GBK encoding path exists here.
2. **Response decoding:** every handler reads all entity bytes with unbounded `readAllBytes()`, then `new String(bytes)` using JVM default charset, then Fastjson2 `JSON.parseObject(..., new TypeReference<>() {})` (`71–72,80–81,96–97,113–114,129–130`). No Content-Type/charset interpretation, byte bound, JS wrapper cleanup, malformed-byte rejection, content-type check, schema validation, or raw/HTML separation. Maven source encoding UTF-8 does not make these runtime charset choices explicit.
3. **DTO scope:** typed field names document useful candidate raw contracts, not field validity. `ThreadReply.java:23–45` includes `attachs`, `vote_good`, `vote_bad`, `comments`, `comment_to_id`; `PostListResponse.java:18–20` includes `hot_post` and `attachPrefix`. `Attachment.java:12–24` maps name, URL, thumb, type, path, size. These fields are reachable by deserialization but have no rendering, normalization, or fixture-backed semantic consumers here. `Author.buffs` is suspiciously `List<java.util.Objects>` (`Author.java:34`); other fields remain `Object`. Do not treat model completeness as parser correctness.
4. **Error boundary:** response body is parsed before HTTP status is inspected—there is no explicit HTTP-status handling. `checkResponse` considers app `code=0` success, `5` InvalidSignException, `53` AuditFailedException, else NgaException (`NgaClient.java:160–169`, `NgaResponseCode.java:8–10`). Missing/null response or code can cause NPE on dereference/unboxing; invalid JSON can escape unchecked. `NgaException.java:3–9` stores a private code but provides no getter; subtype/message are accessible but numeric code is not exposed by this class. No specific 401/403/429/redirect/challenge/retry-after outcome. The name AuditFailedException alone does not prove precise server semantics.
5. **Timeouts/lifecycle:** constructor defaults 5/30 seconds (`53–59`), but “connectTimeout” is passed to **connection-request timeout** (pool lease), not explicit socket-connect timeout (`46–50`). Response timeout is set. The reusable CloseableHttpClient is private and never closed/exposed through AutoCloseable. No cancellation API or request deduplication.
6. **Retry/redirect:** no manual retries, rate limiting, domain failover, redirect allowlist, or custom retry policy. `HttpClients.custom().build()` leaves Apache defaults in control; absence of a loop is **not** proof of zero automatic HTTP-library retries or redirects. No dependency-source execution was performed to audit those defaults. Do not import this builder into a no-replay/no-redirect boundary.
7. **Logging:** constructor logs token, UID, app ID, and secret at INFO (`40`); form logger logs every value (`146–148`); signer logs concatenation inputs (`SignUtil.java:28`); response object logged in full (`153–154`). These are direct incompatibilities with the current access/logging rules. No actual credentials are included in this report.

### Concrete lessons for the current project

These are candidates, not approved product changes. Current-fork anchors are local inspected source; the main four-way report should bind them to its current-fork revision/dirty-state ledger.

| Candidate and priority | Evidence / current behavior | Benefit, cost, and compatibility |
| --- | --- | --- |
| **P1 research: extended App thread fields as an offline fixture checklist** | Upstream `PostListResponse.java:18–20`, `ThreadReply.java:23–45`, `Attachment.java:12–24`. Current `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt:6–11,14–64` intentionally leaves standalone attachments, hot posts and parent-comment protocols opaque and projects a narrower validated row shape. | Useful independent evidence to investigate attachments/hot replies/comment relations when a concrete reader need arises. Cost medium/high: establish actual wire names/types and capture lawful fixtures, define relationship semantics, test normalization and renderer integration. Do not assume `attches` and `attachs` spelling equivalence or derive score from vote counters. Current intentional compatibility boundary must be amended before implementation. |
| **P2 research: account-bound remote board favorites import** | Upstream `NgaClient.java:86–98` proves a concrete `forum_favor2/get`, output 12 form/signing path. Current `.../activity/compose/board/ForumBoardRepository.kt:25–34,73–108` persists global local bookmarks with staged-file recovery. | A read-only remote import could reduce setup effort, but is not a drop-in replacement for local favorites. Cost high: separately approved token/session contract, explicit merge/account ownership, malformed/empty result handling, and no auto-overwrite. Upstream has no write API, so do not call it two-way sync. |
| **P2 research: explicit category-version comparison** | Upstream category requests `_v=2` (`NgaClient.java:78`) and models recommendations/icon metadata. Current `ForumBoardRepository.kt:38` uses unversioned `home/category`; current board features already consume remote icon/category data under maintained frontend contract. | Version selector is a precise comparison axis for hierarchy differences. Benefit unproven until fixtures show missing useful data; medium cost to compare both schemas and preserve local identifiers. Do not assume v2 is newer/better or add the selector blindly. |
| **P2 design lesson: operation-local typed application codes** | Upstream separates `5` and `53` in one shared response check, unlike a generic “request failed” surface. Current ordinary/App reader already has typed failure handling and explicit HTTP classification in `ArticleByteClient.kt:18–44`; legacy operations remain separately governed. | Use the idea of retaining verified numeric code plus message for supported endpoints when refactoring legacy responses, not these exact code meanings globally. Low/medium design cost; fixtures required per operation. Upstream is weaker for HTTP/protocol errors and hides its numeric exception code. No reason to replace the current reader error boundary. |
| **Do not adopt: transport, credentials, search, or SDK dependency wholesale** | Charset ambiguity, unbounded reads, secret logging, implicit retry/redirect defaults, omitted search page and unresolved Chinese search; current `ArticleByteClient.kt:43–44,84–113` has disabled redirects/retries and bounded strict decoding. | Existing transport is materially safer and more deliberate. Java 17 + Apache HTTP client dependency adds Android integration cost without demonstrated API benefit. Preserve current Cookie/official identity and operation contracts; signed token APIs are separate source evidence, not a mandatory migration. |

### External references and related specs

- [Pinned README][readme]: provenance, limitations, supported operation list.
- [Pinned POM][pom]: artifact `org.zhd:nga-client:0.0.2`; Java 17; Apache HttpClient5 5.2.1; Fastjson2 2.0.27; Lombok 1.18.26; SLF4J 2.0.7; Logback 1.4.6; JUnit Jupiter API 5.9.2. Library runtime defaults were not independently verified.
- `.trellis/spec/backend/nga-platform-access-rules.md`: source evidence labels, origin/session/encoding/logging boundaries, no live assumptions.
- `.trellis/spec/backend/nga-platform-operation-registry.md`: current operation IDs; existing App compatibility intentionally differs from token-signed NgaClient calls.
- `.trellis/spec/backend/index.md`: contract authority and offline validation checklist.
- Related follow-on contracts: `.trellis/spec/backend/thread-detail-compat-contract.md`, `network-foundation-contract.md`, `json-compatibility-contract.md`; `.trellis/spec/frontend/state-management.md` category/icon behavior. These are navigation references, not claims that all were re-audited in this research.

## Caveats / Not Found

- No live service availability, successful login, token validity, authenticated output, or server version was verified.
- `TestNgaClient.java:14–19` obtains environment token/UID; lines 22–46 send real requests and mostly log/ignore results. They are demonstrations, not offline behavioral assertions. They were deliberately not run.
- No fixtures, request snapshots, parser assertions, independent retry tests, or application integration were found in the complete source tree.
- Typed DTO fields can be absent, malformed, or mapped incorrectly. Attachment/comment/hot-post declarations prove intended parsing coverage, not working product features.
- Search pagination omission and two-stage encoding are source facts; their server-side effects beyond the README/interface warning remain unverified.
- Unused URL constants do not extend the five-operation capability count. No unsupported group was inferred from similarly named constants.

[client]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/NgaClient.java#L31
[interface]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/INgaClient.java#L9
[url]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/common/NgaUrl.java#L11
[constants]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/common/NgaConst.java#L3
[codes]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/common/NgaResponseCode.java#L8
[sign]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/util/SignUtil.java#L11
[time]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/util/TimeUtil.java#L10
[base]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/BaseNgaResponse.java#L10
[page]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/NgaPageResponse.java#L11
[category]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/HomeCategoryV2Response.java#L13
[icons]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/ForumIconList.java#L13
[subjects]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/SubjectListResponse.java#L13
[subject]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/SubjectInfo.java#L13
[search]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/SubjectSearchResponse.java#L12
[posts]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/PostListResponse.java#L13
[reply]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/ThreadReply.java#L13
[attachment]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/Attachment.java#L11
[author]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/dto/Author.java#L14
[tests]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/test/java/TestNgaClient.java#L14
[readme]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/readme.md#L1
[pom]: https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/pom.xml#L7
