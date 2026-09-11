# Research: foreground fallback and operation boundary

> Revision note: preserve the source/transport evidence below. The selected design now accepts independently valid data despite an uninterpreted code, binds subsequent pages/refreshes to the reader's source generation, and permits at most one source-transition anchor-alignment read. The old blanket unknown-code veto, per-load normal-first recommendation, and full-thread-only admission are superseded by ../design.md and the query/content revision reports.

- Query: How can the App API be added without replacing the current prefetch presenter or inheriting upstream account/transport/error defects?
- Scope: current source and the existing August audit; no live requests, accounts, device operations, or product edits.
- Date: 2026-09-11
- Current baseline: `5bb92cf033aa32d749d10e1a497bc05173cd2955`, including `6203dad5`.
- Upstream feature: `2becba2acc3f6c85340424cd09bb03fa7d759db0`; audited source at `22ba3082501bcbb08f52a66d787f970f59c2dda7`.

## Confirmed integration points

| Owner | Evidence | Consequence |
| --- | --- | --- |
| Existing normal request | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:49` and `:75` | Preserve GET URL/fields and `ArticleConvertFactory`; its overload already accepts explicit headers. |
| Model origin | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/BaseModel.java:18` and `:34` | `mDomain` is captured at model construction. App fallback must use that same model origin, not independently reread changed preferences. |
| Existing retry | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:88` and `:137` | Current `ServerException` leads to one `getNextCookie()` retry. This is an existing behavior, not a requirement for the new operation. |
| Account edge case | `lib_bu_account/src/main/java/com/justwent/androidnga/bu/UserManager.kt:69` and `:146` | Zero accounts produce index -1 before array access; count must be checked before asking for the next Cookie. A single account gives the same Cookie again. |
| Account access adapter | `nga_phone_base_3.0/src/main/java/sp/phone/common/UserManagerImpl.java:118`, `:123`, `:158` | Current-user object, Cookie and count can be read through the existing boundary at operation construction; no new account manager is needed. |
| Prefetch | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:97`, `:158`, `:170`, `:190` | Preserve the separate silent callback, promoted-request reuse and ON_PAUSE demotion. |
| Foreground entry | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:393` | `onResume` starts automatic foreground load; retain READY reuse and explicit-refresh behavior. |
| Unified UI completion | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:54` | Existing errors clear loading before considering WebView. New App fallback must run before terminal completion, and all terminal branches must still clear it. |
| Browser fallback | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:199` and `:212` | It routes to `ForumWebFragment`, does not forward request headers, and currently drops page/author filtering. Preserve exact target context in the new fallback. |
| Generic transport | `lib_base_network/src/main/java/com/justwen/androidnga/base/network/retrofit/RetrofitHelper.java:103` and `converter/JsonStringConvertFactory.java:38` | Global Cookie injection, raw logging, unconditional GBK and default redirects must not be reused for the new endpoint. |
| Existing request error parser | `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ErrorConvertFactory.java:18` | Recognizes a limited set of NGA errors. `ServerException` alone does not prove that fallback is permitted; it can also represent unidentified HTML/challenge output. |

## Recommended policy

### Toggle and attempts

- Add the upstream preference key `pref_show_with_app_api`, title `帖子详情兼容模式`, default false. Keep the existing WebView setting independent.
- Each explicit or automatic foreground load begins with normal `read.php`. Do not import upstream's sticky presenter routing flag: explicit refresh must recheck the preference and preserve the current normal-first refresh contract.
- When enabled, snapshot the cloned parameters, selected account identity/Cookie (empty Cookie for anonymous), and model origin before the first request. Pass the explicit Cookie on that normal request and on its App fallback. Do not add next-account retry to this enabled chain.
- When disabled, retain the legacy foreground chain, with only the necessary zero/single-account guard and no retry when the next Cookie is empty or identical. No changes to account selection or persistence.
- A foreground attempt has one normal stage and at most one App stage, with an identity/generation check. App never retries normal, rotates accounts or recursively retries itself.
- Snapshot credentials remain transient. DTOs, cache envelopes, diagnostics and exception text must never contain a Cookie/cid. Cache ownership needs only a non-secret account ID/scope.

### Eligible errors

Keep classification local to thread reading rather than creating a project-wide error framework. A failure may start App fallback only when it is an unrecognized normal response-format failure and the page is still foreground, the preference is still enabled, the attempt is current, and the selected identity still matches its snapshot.

Known site/business messages, authentication rejection, HTTP 401/403, challenge/HTML access pages, rate limits including HTTP 429, cancellation, empty body and transport failures are terminal. Unknown HTML must not be presumed a harmless parser mismatch. Preserve a bounded safe site message for display where available; do not stringify entire JSON bodies or parser exceptions containing raw input.

App responses require an accepted data structure, not just HTTP 200. The upstream nullable `code` field has no proven success-code mapping: do not guess `0`, `1` or `200`. Missing/null code may enter structural validation; an unrecognized nonnull code remains a rejection/unknown response even when it carries `result`. Unknown fields that may carry content require the parser's supported-subset policy, not silent truncation.

| End condition | Native/UI result | Automatic next action |
| --- | --- | --- |
| Normal accepted page | Existing data rendering and request-state completion | None |
| Eligible format failure, App enabled | Keep foreground loading while attempting App once | Same-account App request |
| Accepted App page | Existing data rendering, complete loading, one compatibility-success notice | None |
| Unsupported App layout/content or protocol result | End loading with a useful error | Existing internal WebView if its setting is enabled |
| Auth, access/challenge or rate limit | End loading with the classified error | Stop; manual browser/account action remains available |
| Network failure/cancelled/stale identity | End loading; cancellation/stale callbacks are silent | None |
| Any failure after page has paused | End local request state without foreground UI side effects | No App/Toast/WebView |

No offscreen prefetch initiates the new operation. A promoted prefetch still uses its one normal in-flight request; if it fails while foreground it starts the ordinary foreground attempt through the existing entry point. ON_PAUSE removes promotion. DETACH keeps Rx cancellation. A late App success may be retained only for its same page/account; it must not deliver into a new request or another account.

### New operation transport

Register the source-observed alternate read separately (proposed ID `THREAD.PAGE.APP_COMPAT`), citing the August full SHA rather than pretending it exists in the July pinned contract.

Use an endpoint-specific Retrofit/OkHttp client returning response bytes (`Response<ResponseBody>`), outside the legacy String converter/interceptor chain. Reuse existing Retrofit/Rx dependencies and browser UA accessor; do not upgrade networking or JSON libraries for this task.

- POST `/app_api.php?__lib=post&__act=list` with form `page`, and nonzero `tid`, `pid`, `authorid`, exactly as observed. Actual supported invocation contexts are constrained by the pagination design.
- Validate the model's selected HTTPS origin before sending: exact hosts `bbs.nga.cn`, `bbs.ngacn.cc`, `nga.178.com`, `nga.donews.com`, `ngabbs.com`; default HTTPS port only; no userinfo, query, fragment or non-root path. These hosts come from `lib_base_common/src/main/res/values/arrays.xml:3`. Reject malformed preferences, including the existing trailing quote in one array value; do not silently change domains.
- Set explicit snapshot Cookie, browser `User-Agent`, and `X-User-Agent: Nga_Official`. No global Cookie-provider lookup, automatic authentication, Cookie jar, request/body logging or credential forwarding to another origin.
- Disable redirects, SSL redirects and connection retry for the single App attempt. A 3xx becomes a classified stop; do not inspect/follow Location as a new authenticated operation.
- Read and close bodies once on I/O threads, bound retained bytes (design limit: 4 MiB), and reject oversized/truncated input. Account for both declared length and streaming overflow.
- Decode a valid declared charset strictly. Support UTF-8 and the GBK/GB2312/GB18030 family; absent charset uses GBK solely as the explicit source-observed compatibility default for this endpoint. This is not current-service evidence. Unsupported/invalid charset or decoding failure is a protocol error; never guess successive encodings or turn it into an empty success.
- Bind both I/O and parsed UI delivery to DETACH, and suppress stale callbacks at the presenter. No request/account/body values in logs; diagnostics may include only operation, coarse reason and HTTP status.

## Likely files

- App `ArticleListPresenter`, `ArticleListModel`, and its narrow contract surface for the new operation.
- One small app-local request/error policy and an endpoint-specific client/service; an extra shared network helper is unnecessary unless implementation reveals actual reuse.
- App `settings_lab.xml`, app strings, common `donottranslate.xml` for the upstream preference key.
- Backend operation registry, prefetch/cache contract deltas and feature-specific tests after implementation approval.

## Offline validation

- Four preference combinations; accepted/rejected normal/App results; exactly one App attempt; explicit refresh starts normal again.
- 0/1/2 accounts; no `getNextCookie` with fewer than two; same-identity snapshot; user/session changes during an attempt; no credentials in persisted objects or messages.
- Prefetch/background/promoted/pause/detach and stale generation cases, preserving `ArticlePageRequestStateTest` and existing pager tests.
- A fake transport verifies exact URL/method/form/headers, origin rejection, redirects, retry policy, bounded byte reads, declared/missing/invalid charset, HTTP/network/error bodies and cancellation. It must never contact NGA.
- Error cases with JSON data/error ambiguity, nonnull unknown code, blank/HTML/malformed input, challenge/auth/rate-limit markers; no success/caching side effects for rejected output.

## Related specs and caveats

Read `.trellis/spec/backend/network-foundation-contract.md`, `nga-platform-access-rules.md`, `nga-platform-operation-registry.md`, `thread-page-prefetch-contract.md` and `android-quality-guidelines.md`.

This is a scoped recommendation for the new flow. Existing legacy normal transport and old caches remain known compatibility behavior; this task is not a global account/transport/login rewrite. Actual App API availability, missing-charset behavior and success code semantics remain unverified. The unsupported-subset and default-off switch are part of the delivery limits, not claims that all upstream responses are covered.
