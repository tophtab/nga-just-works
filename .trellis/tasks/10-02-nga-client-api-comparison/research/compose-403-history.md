# Research: compose-nga HTTP 403 and reader reliability history

- Query: Has compose-nga encountered and solved the user's intermittent thread-read HTTP 403, and is its request mechanism more reliable?
- Scope: mixed; public GitHub issues/comments/PRs/discussions/commit history and pinned source comparison.
- Date: 2026-10-02.
- Source: `Night-stars-1/compose-nga`, default branch `master`, `8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595` (2026-08-12); existing clone `/tmp/nga-api-comparison-WnvQGx/compose-nga`.

## Findings

### Bottom-line evidence

No public report found establishing this project had, reproduced, and fixed HTTP 403 during thread reading. It does contain a real request-signature correction. That is an implementation fix, **not evidence of a 403 cure or higher success rate**. A signed App request is a useful experimental comparator to our Cookie-authenticated App compatibility request, but source inspection cannot establish server acceptance for the user's affected posts.

### Search coverage

- Enumerated all public issues with `GET repos/Night-stars-1/compose-nga/issues?state=all&per_page=100 --paginate`: five issues, all closed (#1, #3, #4, #5, #6). Read every body and all nine repository issue comments. None reports thread HTTP 403 or access denial.
- GraphQL `pullRequests(states:[OPEN,CLOSED,MERGED])`: totalCount 0. `hasDiscussionsEnabled=false`, discussions totalCount 0. There is no public PR/discussion repair thread to claim.
- Enumerated default-branch commits through initial commit on 2025-01-22, reviewing all messages for HTTP errors, signatures, reading, compatibility and browser changes; fetched full relevant signature/error diffs below. No commit message explicitly claims a thread HTTP 403 fix.
- Searched current network/repository/models/interceptor source for `403|forbidden|retry|fallback|captcha|验证|兼容`: only unrelated model compatibility comments matched. This narrow search is supplementary to tracing the actual request/error paths.
- Search was read-only via authenticated GitHub API. No NGA traffic or GitHub messages were sent.

### Public conversation and actual fixes

| Evidence | Date/status | What it establishes |
| --- | --- | --- |
| [Issue #1](https://github.com/Night-stars-1/compose-nga/issues/1), including [maintainer response](https://github.com/Night-stars-1/compose-nga/issues/1#issuecomment-2609176551) | Opened 2025-01-23, closed 2026-08-13 | Contributor expresses concern about nonpublic API usage. Maintainer replies many third-party clients use mobile APIs and NGA could restrict either web/mobile. This is an opinion about policy, not a reported 403 or test result. |
| [4bb247a: fix signature error](https://github.com/Night-stars-1/compose-nga/commit/4bb247a24f0eedab9c31c3610c277cc27172c7c3) | Committed 2026-08-11; present in inspected head; no commit comments | Previously selected one value from uid/tid/id/pid; now constructs separate tid/pid and uid/id slots, with pid included only when tid is nonempty/nonzero. Actual API-signing change. No issue link, HTTP status, failing example, or reporter confirmation appears in commit metadata/diff. |
| [5290caf: include HTTP status/error detail](https://github.com/Night-stars-1/compose-nga/commit/5290caf108ca78a32170c2425b73a88cf7ee2a8a) | Committed 2026-08-12; present in inspected head; no commit comments | Replaces generic `response body is null` on non-2xx with `HTTP <status> <up-to-200-character-body>`. Commit explicitly mentions diagnosing update checks among other interfaces. Improves diagnosis, not success rate, fallback, authentication, or 403 recovery. |
| [8a97a91: directly load reply by pid](https://github.com/Night-stars-1/compose-nga/commit/8a97a9129062f88b2e7b454fd7c2492431ee10bb) | Committed 2026-08-11; present in inspected head | Reply links open a PID reader with `tid=0`, consuming returned reply and resolving containing thread rather than merely resolving and opening the whole thread. Navigation/identity fix; no demonstrated HTTP403 association. |
| [eef54cd: fix signing failure](https://github.com/Night-stars-1/compose-nga/commit/eef54cd55d329eb0f67bc8c16446f99f14cbf999) | Committed 2025-02-06 | **False positive:** sets Android build tools version in CI APK signing; not API authentication or read repair. |

The signature correction's potential effect must be read carefully: for a simple tid-only read, the old uid fallback held tid, so its string position could produce the same concatenation as the new explicit tid slot. The change materially distinguishes combinations and pid-only requests; it is not automatically a fix for ordinary tid-only reading.

### Files and request mechanisms

Paths below are relative to the compose-nga clone; source links pin the inspected revision.

- [`app/src/main/java/com/srap/nga/logic/network/ApiService.kt:41`](https://github.com/Night-stars-1/compose-nga/blob/8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595/app/src/main/java/com/srap/nga/logic/network/ApiService.kt#L41): normal reader POSTs `app_api.php?__lib=post&__act=list`, form tid/page. Line 51 defines PID read with pid/page/tid=0.
- [`app/src/main/java/com/srap/nga/logic/network/NetworkModule.kt:30`](https://github.com/Night-stars-1/compose-nga/blob/8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595/app/src/main/java/com/srap/nga/logic/network/NetworkModule.kt#L30): fixed `https://ngabbs.com`; lines 66–70 install sign/auth interceptors. No explicit alternate-origin or reader fallback policy.
- [`app/src/main/java/com/srap/nga/utils/interceptor/SignInterceptor.kt:34`](https://github.com/Night-stars-1/compose-nga/blob/8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595/app/src/main/java/com/srap/nga/utils/interceptor/SignInterceptor.kt#L34): browser-shaped Android User-Agent plus `X-USER-AGENT: Nga_Official/90941(Xiaomi;Android 14)`. Lines 40–85 add app_id=1010, access_uid, access_token, timestamp seconds, MD5 signature, output14; lines 128–137 define operation-sensitive signing payload. QR login supplies token identity, distinct from passport Cookie credentials.
- [`app/src/main/java/com/srap/nga/logic/repository/NetworkRepo.kt:225`](https://github.com/Night-stars-1/compose-nga/blob/8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595/app/src/main/java/com/srap/nga/logic/repository/NetworkRepo.kt#L225): non-2xx becomes an exception/display error, with no automatic browser or alternate reader switch. No status-specific 403 recovery found.
- [`app/src/main/java/com/srap/nga/utils/interceptor/AuthInterceptor.kt:15`](https://github.com/Night-stars-1/compose-nga/blob/8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595/app/src/main/java/com/srap/nga/utils/interceptor/AuthInterceptor.kt#L15): parses JSON auth codes 46/2048 to clear session; this does not solve an HTTP-layer denial.

Our current App compatibility contract uses the same post/list route with Cookie and official identity headers, without this signed token form. Therefore “we already use App API” does not prove wire equivalence; equally, “they sign requests” does not prove immunity to server restriction. A useful later experiment would compare the same tid/page/account permissions and network across ordinary read, Cookie App read and valid-token signed App read. API code failures, HTTP403 and parser failures must remain distinct in results. This research does not authorize implementation or such live tests.

### Related specs

- `.trellis/spec/backend/thread-detail-compat-contract.md`: current Cookie App request, default-off compatibility, source transitions and HTTP403 ACCESS stop; no automatic fallback for auth/challenge/rate-limit.
- `.trellis/spec/backend/ordinary-read-wire-contract.md`: decoder/data boundary; parsing fixes cannot change HTTP authorization outcomes.
- Existing [compose source ledger](compose-nga.md) and [four-way comparison](comparison.md) supply wider authentication/parser details. This 403-focused finding changes the research priority, not the established wire contracts.

## Caveats / Not Found

- Five issues constitute a very small public sample. Missing reports do not imply missing failures; private chats, deleted reports and other branches were not covered.
- No reporter-confirmed 403 fix, paired same-post comparison, service success metric, or present-day NGA response was obtained.
- Issue closed status applies only to those actual issues, not to an imagined 403 defect. Commit inclusion establishes shipped source intent, not independently verified production behavior.
- Error bodies/images linked in unrelated issues were not used to infer a thread status. No application code, account state, specs, or Git history was modified.
