# Research: LNGA signed HTML transport and existing-account compatibility

- Query: Can LNGA's signed HTML request improve native reading reliability, and what must an isolated Android adapter preserve?
- Scope: mixed; pinned LNGA source and current Android source, offline inspection only.
- Date: 2026-10-02
- LNGA snapshot: `apap6628114/lnga_harmony`, `df6cfcc2e4cfc2efa95269aac398707e9d4d3e81`, local `/tmp/nga-api-comparison-WnvQGx/lnga_harmony`.
- Android snapshot: `tophtab/nga-just-works`, `fae59cbc477590e98c1c61a7526343eccc4cff3b`; current workspace files inspected. No git operations performed.

## Findings

### Concrete conclusions

1. This is a third reader transport, distinct from both ordinary `GET read.php?__output=8` and existing compatibility `POST app_api.php?__lib=post&__act=list`. It requests a JSON envelope containing HTML, then parses that HTML into native reader data. It does not launch a browser.
2. Existing passport uid/cid are structurally sufficient credentials: LNGA directly copies them to `access_uid/access_token`, also sends the passport Cookie, and computes the signature locally. There is no second login or token-exchange requirement in this request path. This is source compatibility, not a live guarantee that every existing cid is accepted.
3. Its request differs in host, POST fields, App headers, signature and response format simultaneously. These are credible reasons for different server behavior, but inspection cannot attribute an improvement to HTML alone or prove HTTP 403 disappears.
4. LNGA does not implement an HTTP-403 recovery algorithm here. Its low-level response carries status, but its JSON request layer does not classify status before parsing. Network errors retry; a completed HTTP 403 response does not itself trigger that retry loop. Fewer visible “403” messages therefore would not by itself prove fewer rejected requests.
5. Do not copy LNGA's entire HTTP client. The current Android byte client has stronger status, origin, size, cancellation and redirect boundaries that should be retained for any experiment.

### Files found and reference convention

LNGA paths below are relative to `entry/src/main/ets/` at the pinned SHA:

| File | Purpose |
| --- | --- |
| `service/api/ThreadApi.ets` | Read request fields, output17 envelope extraction, session lookup, native data handoff and PID location |
| `service/NgaClient.ets` | Signed form/header construction, fixed host, HTTP executor and JSON decoding |
| `common/security/NgaSigner.ets` | UTF-8 MD5 and signature composition; secret constants intentionally omitted here |
| `common/utils/CodecUtils.ets` | Form escaping and UTF-8 decoder |
| `common/CookieUtils.ets` | Passport Cookie extraction |
| `store/SessionRegistry.ets` | App-local token to uid/cid session mapping |
| `common/concurrency/Throttler.ets` | Per-host request pacing |
| `common/constants/NgaDomains.ets` | Three-domain list controlling retry loop count |
| `common/utils/TimeoutUtils.ets` | Promise timeout without underlying request cancellation |

Android paths below are relative to `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/`:

| File | Purpose |
| --- | --- |
| `ArticleAccount.kt` | Validated transient selected-account snapshot and operation context |
| `ArticleByteClient.kt` | Existing ordinary/App form requests, bounded bytes and strict decoding |
| `ArticleFailure.kt` | Typed HTTP classification with actual status preserved |

### Exact source-observed read request

Anchors: `ThreadApi.ets:67-82,191-203`; `NgaClient.ets:261-283,295-331,395-399`; `CodecUtils.ets:400-407`.

| Component | LNGA value/behavior |
| --- | --- |
| Method/origin/path | `POST https://ngabbs.com/read.php` |
| URL query | Empty; no `__lib`, `__act`, or query `__inchst` |
| Business form fields | `tid`, `pid`, `topid=''`, `authorid`, `opt=''`, `page` (supplied or `'1'`), `__localres='1'` |
| Public form fields | `__output='17'`, `__inchst='utf-8'`, `app_id`, `access_uid`, `access_token`, `t`, `sign` |
| Timestamp | Decimal Unix seconds, captured once for fields and signature |
| Signature | Lowercase hex MD5 of UTF-8 concatenation `app_id + access_uid + access_token + signParams + t + secret`; for this reader `signParams=''` |
| Form encoding | Each field name/value receives `encodeURIComponent` once, joined with `&` |
| Content-Type | `application/x-www-form-urlencoded;charset=UTF-8` |
| Cookie | `ngaPassportUid=<same uid>; ngaPassportCid=<same cid>` |
| User-Agent | Full Android 10 / NOH-AN00 browser UA ending `Nga_Official/7.17.17` (`NgaClient.ets:298`) |
| App headers | `X-Requested-With=gov.pianzong.androidnga`; `X-USER-AGENT=Nga_Official/7.17.17(HUAWEI NOH-AN00;Android 10)`; `X-NGA-CHANNEL=official`; `X-NGA-VERSION-NAME=7.17.17`; `X-NGA-VERSION-CODE=71717` |
| Referer | `https://ngabbs.com/` |
| Response decode | UTF-8 before JSON preprocessing/parsing; not the ordinary reader's missing-charset GBK rule |

No signature/checksum secret value or real account value is retained in this report. `__ngaClientChecksum` is not enabled on this path. No sorting of business parameters or hashing of the entire encoded form occurs. Changing signing input to include tid/page would not match this source.

Header caveat: `executeWithRetry` first puts `X-User-Agent: Nga_Official` into a case-sensitive object, then adds `X-USER-AGENT` from signed headers (`NgaClient.ets:175-185,300`). These are the same HTTP header name despite distinct JavaScript keys. The exact SDK serialization of this duplication is not proven by inspection. An Android implementation must choose a single intentional header and verify its constructed request offline rather than reproduce a casing accident.

### Existing-account compatibility

`ThreadApi.ets:284-294` resolves an app-local session token to `session.uid/session.cid`; `SessionRegistry.ets:4-15,22-43` makes clear this token is merely a process registry key. It is not the network `access_token`. `NgaClient.ets:265-274` copies cid into the network access token without conversion, and `common/CookieUtils.ets:36-37` extracts the same passport Cookie names as Android.

Android already captures `activeUser.userId/cid` in `ArticleAccount.kt:30-34`, validates them at `14-24`, and compares exact credentials at `10,47-48`. Thus a future adapter can reuse the selected account without adding a login UI. Current `ArticleAccount` keeps only a private Cookie string, however: implement an internal transient signing-credential capability or retain validated uid/cid privately in that same snapshot. Do not reread the global account halfway through signing and do not recover cid through an unrelated global Cookie interceptor. Retain redacted `toString`, owner/generation checks and no credential cache fields.

LNGA refuses its reader if no session exists (`ThreadApi.ets:286-288,314-317`). Although generic signing accepts missing cookies, that does not establish working guest reading. Guest output17 support remains unverified.

### HTTP status, envelope code and business errors are different

`httpReq` returns `responseCode` at `NgaClient.ets:109-121`; `ngaRequest:448-490` proceeds directly to body decoding/wrapper repair and does not use `response.status`.

`ThreadApi.ets:86-97,154-178` documents and handles an unusual application envelope: HTTP 200 with `code=521` can mean either a normal `template='read'` article or a `template='std_error'` business error. This is **not HTTP 521**. Do not require envelope code zero and do not interpret code 521 alone as either success or outage.

LNGA's extractor accepts a nonempty string `html` unless:

- `template == 'std_error'`; or
- a page error reason exists with the error-code comment marker, and HTML lacks `commonui.postArg.proc(`.

It extracts the first `msginfostart/msginfoend` segment, strips tags, falls back to title when the error-code marker exists, then uses envelope `msg` if needed. It does not require `template='read'`; later parsing must establish usable rows (`191-203,215-233,326-335`). A nonempty HTML string alone is not successful native reading.

For a future Android adapter, preserve HTTP rejection first (existing `ArticleFailure.kt:52-65`: 401 AUTH, 403 ACCESS, 429 RATE_LIMIT, 3xx ACCESS). Then parse a typed bounded envelope with business rejection preceding residual HTML. Do not copy arbitrary page-title-to-cause mapping or scan normal post content for challenge/permission words. Evidence supports extracting a server message, not automatically assigning a precise permission/auth subtype to every `std_error`. Unknown nonarticle HTML remains neutral failure. Keep raw bodies out of failures and diagnostics.

### Retry, limits and lifecycle

| Boundary | LNGA source behavior | Android implication |
| --- | --- | --- |
| Network timeout | Connect 15 s/read 30 s, ARRAY_BUFFER (`NgaClient:98-105`) | Set explicit per-call/deadline policy for experiment; do not infer a whole-operation bound from per-attempt timeouts |
| Network retry | Initial callback error triggers at most two retries because DOMAINS has three entries (`NgaClient:205-235`; `NgaDomains:9-16`) | Signed `baseUrl` stays fixed at ngabbs.com, so this is three attempts to the same host, not alternate-domain healing |
| HTTP 403/429 | Callback success passes body onward; status is not classified | Retain current Android stop/status classification; no automatic 403 fallback chain |
| Signature across retry | Form/signature constructed before executor | Retries reuse timestamp/signature, not recomputed per attempt |
| Pacing | Six concurrent requests per host, 150 ms dispatch interval (`Throttler:26-43`) | Not evidence of measured server-safe rates; existing controlled foreground pacing is preferable |
| Cancellation | HTTP object destroyed when callback completes; no caller cancellation handle (`NgaClient:91-124`) | Retain Rx disposal to `Call.cancel` (`ArticleByteClient:20-35`) |
| PID helper timeout | 15 s `Promise.race` around reads (`ThreadApi:377-378,426-427`; `TimeoutUtils:11-20`) | This stops waiting, not the underlying network request; do not copy as cancellation |
| Redirect | No redirect policy configured in inspected HttpRequestOptions | SDK default/effective credential forwarding unverified; retain explicit Android redirects disabled |
| Size/charset | Entire ArrayBuffer then string; no explicit app body cap in these functions; UTF-8 decoder without fatal option (`CodecUtils:57-59`) | Retain 4 MiB declared/streamed limit, strict decoder and truncation checks (`ArticleByteClient:84-122`); SDK defaults must not be mistaken for proven app bounds |
| Logging | Verbose body chunks, selective credential sanitization (`NgaClient:52-95,452-453`) | Do not port raw request/response logging, which can include user content |

### Query and page identity

The request preserves independent tid, pid and authorid; it sends empty values for absent IDs. It supports numeric pages and `page='e'` for final-page discovery (`ThreadApi:67-82`). There is no `searchPost` or page-size request field. `getThread` only checks that tid or pid is supplied before fetching (`321-326`); the request builder itself does not prove returned identity.

LNGA's optional `locatePidPage` (`369-446`) performs a last-page read plus binary search using monotonic end-of-page PID. It clamps page size to at least 20, and can infer one-page totals from current rows when metadata is missing. Those are LNGA assumptions, not necessary parts of signed HTML transport and not acceptable replacements for current query/PID validation and unknown-size semantics. Do not import this extra read loop into a minimal adapter. Keep original PID/author constraints through a single requested-page read; verify returned thread/PID/author and actual anchors after parsing. A readable PID lookup is not proof of a global floor/page location.

### Smallest isolated future adapter

This is a design recommendation, not code implemented or approved for release:

1. Define a separate source identity for signed HTML. Preserve ordinary and existing App readers and their current default behavior. Start with explicit foreground opt-in for full-thread positive tid/page reads, one selected account and the source-observed fixed host; reject unsupported query kinds explicitly rather than silently dropping pid/author filters. Extend filtered/PID queries only after parser identity fixtures pass.
2. Add the source-specific request codec, internally accessing the same validated immutable uid/cid snapshot. Pin host and App header contract; construct fields once and encode once. No Cookie jar, redirects, account rotation or automatic transport retry.
3. Reuse/factor existing bounded byte transport without changing ordinary/App decoding defaults. Signed HTML needs its own source-observed UTF-8 policy when charset is absent; existing `ArticleByteClient.charset` defaults to GBK and cannot be reused unchanged. Test explicit conflicting/malformed declarations; do not cycle through encodings until one parses.
4. Classify HTTP first, then envelope business error, then bounded HTML-to-data parser; never execute received scripts or fetch scripts merely because the returned HTML includes them. Preserve incomplete-content and invalid identity as distinct failures/capability loss.
5. Attach resulting data to the existing owner/query/generation/source metadata, and make cache/prefetch policy explicit. An experimental source should not enter ordinary full-thread cache or background prefetch by accident.
6. Offline validation: fixed-clock synthetic signature vectors (no real secrets), exact request fields/escaping, missing and conflicting charset, HTTP403/429/redirect, code521/read versus code521/std_error, challenge/nonarticle HTML, absent/malformed html, oversized/truncated body, cancellation/account change, query mismatch and unsupported PID/author query rejection. The parser's richer feature/identity coverage is a separate research stream.

### Related specs read

- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/thread-detail-compat-contract.md`: source/account/query continuity, finite fallback, positive body/identity validation and no inferred pagination.
- `.trellis/spec/backend/network-foundation-contract.md`: scoped account and transport boundary.
- `.trellis/spec/backend/nga-platform-access-rules.md`: exact authenticated origin, no implicit retries/identity rotation, bounded data, privacy and offline-first validation.
- `.trellis/workflow.md`: existing task remains planning; this artifact does not authorize activation or implementation.

## External references

- [Pinned ThreadApi](https://github.com/apap6628114/lnga_harmony/blob/df6cfcc2e4cfc2efa95269aac398707e9d4d3e81/entry/src/main/ets/service/api/ThreadApi.ets#L67)
- [Pinned NgaClient](https://github.com/apap6628114/lnga_harmony/blob/df6cfcc2e4cfc2efa95269aac398707e9d4d3e81/entry/src/main/ets/service/NgaClient.ets#L261)
- [Pinned signature algorithm](https://github.com/apap6628114/lnga_harmony/blob/df6cfcc2e4cfc2efa95269aac398707e9d4d3e81/entry/src/main/ets/common/security/NgaSigner.ets#L174)
- [Current Android transport](https://github.com/tophtab/nga-just-works/blob/fae59cbc477590e98c1c61a7526343eccc4cff3b/nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleByteClient.kt#L19)

## Caveats / Not Found

- No live NGA requests, user credentials, APK traffic capture or runtime SDK experiments were used. Source comments marked “实测” are upstream authors' reports, not independently reproduced observations.
- No same-post/same-account/same-network success-rate comparison; no evidence identifying the user's exact 403 cause. This research supports feasibility of another explicit native-reading source, not a claim of fixing access rejection.
- Exact Harmony SDK redirect/header-collision/body-size defaults were not verified; the absence of explicit settings is documented rather than replaced by guessed defaults.
- Fixed host and App headers may matter independently of HTML. Existing account compatibility does not guarantee all host/session policies accept each credential.
- Public signature material is an observed compatibility dependency and may change server-side. This report omits its value and does not claim an official stable API contract.
- Research scope excluded full HTML parsing/rendering parity, upstream issue chronology and license analysis; parallel research owns those findings.
