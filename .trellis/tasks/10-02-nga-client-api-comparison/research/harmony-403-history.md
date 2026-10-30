# Research: LNGA Harmony read failures and 403 history

- Query: Has apap6628114/lnga_harmony experienced and fixed HTTP 403/thread-reading failures, and does its request mechanism offer better reliability?
- Scope: mixed; public GitHub issues/comments, default-branch commit history, existing local source checkout. No live NGA requests.
- Date: 2026-10-02
- Repository: https://github.com/apap6628114/lnga_harmony
- Current source revision: df6cfcc2e4cfc2efa95269aac398707e9d4d3e81 (see correction below).

## Findings

### Strongest supported conclusion

The repository provides explicit evidence of addressing web-JSON throttling and incomplete/error responses through signed App requests and HTML extraction. It does **not** provide a public issue documenting HTTP 403 reproduced and permanently fixed. This is a promising alternative request path to evaluate against the user's actual failed thread, not evidence of being immune to access rejection.


### Public issues: coverage and actual resolved problem

Enumerated all open/closed issue and pull-request records with `GET /repos/apap6628114/lnga_harmony/issues?state=all&per_page=100`, plus all issue comments. There were 13 records: 9 issues and 4 PRs; all closed. No issue title/body/comment explicitly reported HTTP 403 or failure to load the thread body. PRs concerned UI/fonts/anonymous author-only filtering/reply content, not 403. Repository metadata reports `has_discussions=false`.

The closest network-related issue is [#5, 最近打开帖子无法加载图片了](https://github.com/apap6628114/lnga_harmony/issues/5), opened 2026-08-05, closed 2026-08-06. Reporter said another third-party app ANGA could display images while this app could not. [Maintainer comment on 2026-08-05](https://github.com/apap6628114/lnga_harmony/issues/5#issuecomment-5191362123): old NGA domain stopped working and release 1.0.2 switched to supported domains. This is maintainer-reported remediation, not reporter-confirmed 403 recovery; thread image loading must not be conflated with thread HTTP 403.

Related concrete source changes:

- [3b13f5f, 2026-08-05](https://github.com/apap6628114/lnga_harmony/commit/3b13f5fc2f27299f1fb19a90c46274d9b225287b): replaces static-resource domains `img.nga.178.com` with `img.nga.cn`.
- [90324a9, 2026-08-07](https://github.com/apap6628114/lnga_harmony/commit/90324a97bc1313fafcbfdb29a3b3eb8da9ba884d): follow-up normalizes legacy attachment image domains.

Old `apap6628114/nga_harmony` URLs appear in issue #7; querying that repository name redirects to the same `apap6628114/lnga_harmony`, not a separate independent project/history.

### Documented migration and verified implementation history

[FORUM_API_DESIGN.md lines 1–19](https://github.com/apap6628114/lnga_harmony/blob/df6cfcc2e4cfc2efa95269aac398707e9d4d3e81/docs/FORUM_API_DESIGN.md#L1-L19) explicitly describes the App signed channel as mitigating throttling of the web JSON channel. It calls `thread.php?lite=js` a throttling trouble spot. This is upstream documentation, not an independent reliability measurement. That document's topic-list discussion is partially historical: current lists later migrated again to subject/list; its explicit throttling rationale remains relevant historical evidence.

Read-path changes inspected through GitHub commit patches:

1. [7d84cd3, 2026-08-22 07:39 UTC](https://github.com/apap6628114/lnga_harmony/commit/7d84cd3009359aaba7dc2e3fd62dddc99f8fdf96): previous GET read.php `__output=8` + plain HTML fallback replaced by signed App POST. Initially signed `__output=11` was JSON primary; signed `__output=17` was HTML fallback. The removed comments identify incomplete responses (`Unterminated string`, unexpected EOF, HTML error page) as the reason for fallback. The new code also detects output11 responses containing HTML but no data, avoiding falsely successful empty pages. These are related failures but not explicit HTTP 403 evidence.
2. [166791c, 2026-08-22 08:10 UTC](https://github.com/apap6628114/lnga_harmony/commit/166791c6849fe4ffb35a7a32d4390c5a81cd250e): removes JSON channel, forced-HTML debug switch, and dual-channel logic; only signed output17 HTML remains.
3. [ebf9c17, 2026-08-22](https://github.com/apap6628114/lnga_harmony/commit/ebf9c17c6afa20ab76d8779fe6dde5949f930dc5): notification/follow foreground polling reduced from 60 to 300 seconds explicitly to reduce risk-control exposure. Demonstrates developers still considered request frequency relevant after App migration.
4. [b3d04a0, 2026-09-22](https://github.com/apap6628114/lnga_harmony/commit/b3d04a0669bb842bc54ee0c92c7d7d1b7027201c): extracts server error-page reasons instead of reporting generic HTML parser failure. This improves diagnosis of locked/pending/permission-restricted threads; it does not make them readable.

### Current code patterns and files

Paths below are relative to `/tmp/nga-api-comparison-WnvQGx/lnga_harmony`, revision above.

- `entry/src/main/ets/service/api/ThreadApi.ets:1–12,67–82`: signed App `POST /read.php`, `__output=17`, empty signParams, `__localres=1`, thread/pid/page/author arguments. This is an App response containing HTML, not an embedded-browser-only reader.
- `entry/src/main/ets/service/NgaClient.ets:250–283`: form fields carry app_id, access_uid, access_token, timestamp, signature, output, inchst. Session uid/cid used as access identity/token.
- `entry/src/main/ets/service/NgaClient.ets:286–331`: full official-version User-Agent, X-Requested-With, X-USER-AGENT, X-NGA channel/version headers and Referer; fixed HTTPS ngabbs.com. Comment says incomplete official headers may cause rejection/signature error. No independent test performed here.
- `entry/src/main/ets/service/NgaClient.ets:188–189`: includes Passport Cookie as well as signed fields.
- `entry/src/main/ets/service/NgaClient.ets:91–122,162–230`: transport exposes HTTP status; retry triggers on request exceptions, not a dedicated 403 branch. Signed requests pin the host; generic domain rotation does not apply to that path. Do not claim automatic cross-domain 403 recovery.
- `entry/src/main/ets/service/api/ThreadApi.ets:85–166`: output17 successful HTML wrapper may have JSON `code=521`, while HTTP is 200. Error pages may have the same code with `template=std_error`. Do not confuse JSON code 521 with HTTP 521, or either with HTTP 403. Locked, pending moderation, and insufficient permission still produce failures.
- `docs/THREAD_TRACK_DESIGN.md:23–47,135–136`: newer low-volume tracking uses signed output8 with GB18030; 400 ms gap between tracked threads. Document acknowledges output8 is not the App's own chosen output and may need future fallback. Not proof that output8/8 can no longer be throttled.

### Explicit 403 fixture evidence

`entry/src/test/AppThreadUnit.test.ets:349–353` contains a test comment identifying an unauthenticated **HTTP 403 body**, with `error[0]="1:未登录"`, a login-link HTML element, and a `parseNgaError` assertion. This is direct source evidence that the project accounts for a 403-shaped login failure. It is a fixture/comment, not a recorded live trace, user issue, or successful fix demonstration; it cannot establish which current signed request produced it or that signed requests eliminate it. The earlier negative finding is strictly about public issues and commit-message matches, not the absence of 403 awareness in the source.

### Implication for the user's app

The useful candidate is the **complete combination** of signed POST, required headers, fixed host and output17 Article HTML parsing while retaining native rendering. Merely changing `__output`, HTML parsing, or a UA independently is not equivalent. It could improve service-path-specific rejection or web JSON degradation; cannot establish that it fixes account restrictions, IP policies, session problems, or the user's unobserved failure without comparing the actual failing request. Source/history supports investigating it; no measured failure-rate ranking or guarantee is available.

## Related Specs

Read `.trellis/spec/backend/nga-platform-access-rules.md` and `.trellis/spec/backend/thread-detail-compat-contract.md`. Their account-bound requests, domain/credential scoping, existing identity contracts, and explicit fallback classification must be considered if a future implementation is approved. This research makes no changes to those contracts.

## Caveats / Not Found

- No live NGA HTTP requests or account actions; no runtime reproduction; upstream claims remain upstream claims.
- GitHub default-branch commits were paginated in full and filtered for 403/throttling/risk-control/official channel/thread errors/domain migration; ThreadApi path history was also enumerated, and relevant patches inspected. Historical branches and deleted/private issues are outside coverage.
- All 13 issue/PR bodies and all public issue comments read; embedded issue screenshots were not independently OCRed. No standalone explicit 403 text was found.
- Small issue sample and absence of 403 reports do not establish absence of the problem.
