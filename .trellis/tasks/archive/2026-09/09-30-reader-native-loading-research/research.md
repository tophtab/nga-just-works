> 当前状态（2026-09-30）：已整理为独立调研成果，详见 [delivery.md](delivery.md)。下文按调查阶段保留，出现的“恢复研究/下一步”不是当前执行授权；产品修复仍搁置。

# Native-reader failure investigation

Account clarification: the confirmed same-App A-B-A observation concerns a
different, not-yet-linked post; 47440211 has recovered under the original
account. See [separate account evidence](research/account-dependent-observation.md).
Do not merge the two samples or assume the new case is truncated JSON.

Latest live result: [47440211 ordinary response truncation](research/47440211-truncated-response.md)
reproduces JSON failure from a user-supplied link. The failed ordinary response
is an exact prefix of a later complete response; App and the later ordinary
response both render 19 rows. Phone diagnostics are no longer a prerequisite
for this sample's recovery design. Earlier observations below remain historical.

Latest: the user resumed R7 research on 2026-09-30. See
[resumed offline investigation](research/resumed-offline-investigation.md) for
96 settings/source replays, synthetic failure cases, source persistence, and
the distinction between confirmed triggers and the unobserved device cause.
The observations below are retained as the earlier investigation record.

Date: 2026-09-30. Main code remains unchanged. Examples supplied by user: tid=47647197, 47649423, 47649534 and 47649545, page=1 on bbs.nga.cn.

## Static flow

ArticleListPresenter.requestForegroundLoad checks pref_show_with_app_api:
- false (default): legacy Retrofit read -> ArticleConvertFactory.getArticleInfo -> success or error conversion. Unclassified null becomes ServerException. The preserved legacy path can try another saved-account Cookie once before browser handling; this is existing behavior, not authorization to reproduce or expand it.
- true: captured account -> ArticleByteClient normal read -> NormalArticleParser. ArticleAttemptPolicy only permits one App API fallback for FORMAT. Auth/access/rate/empty/network failures do not permit it.
- APP_API results are rendered through the normal native reader/adapter; the compatibility notice describes a source change.
- Scoped terminal FORMAT/CONTENT/PROTOCOL pass allowsBrowser=true. Browser preference defaults true, and showWithWebView closes the native activity after opening ForumWebFragment.
- Therefore automatic browser routing can be a client decision after format/content/protocol failure; it is not proof that the remote page is intrinsically browser-only.

## Loss of diagnostic cause

ArticleConvertFactory.parseJsonThreadPage wraps wrapper handling, object conversion, metadata/user/comment parsing, and renderer calls in one try block. Non-ArticleFailure exceptions return null; NormalArticleParser changes null to FORMAT.
Examples requiring focused investigation include nullable/type-changing __ROWS/__R__ROWS, direct JSONObject casts for comment/user/vote fields, sparse comment keys, DTO field coercion and renderer exceptions. These are identified code risks, not demonstrated causes of this user's sample.
NormalArticleParser also rejects target tid/pid/author or row identity inconsistencies as CONTENT; this routes differently from FORMAT. Do not loosen identity checks without evidence.
Existing tests mostly use synthetic small fixtures. Authorized samples have now been captured privately and replayed below; the device's actual failing response remains unavailable.

## Bounded observation for user-provided example

User requested diagnosis of this specific link. Performed one anonymous normal data read with no Cookie or credentials, no redirect handler follow-up, no retry, 15 s request timeout and 4 MiB body bound:
GET https://bbs.nga.cn/read.php?&page=1&__output=8&noprefix&v2&tid=47647197

Observed:
- HTTP 403
- Content-Type: text/javascript; charset=GBK
- Body length: 736 bytes
- No raw body/headers were printed, persisted to the task, or used to infer deletion.
- No App fallback, second request, other host, or account switch was performed.

Limits:
- Different account and environment from the user's App.
- Not a device reproduction or logged-in server result.
- 403 is classified before parser invocation in the scoped client, so this probe does not demonstrate a normal-parser FORMAT failure.
- This anonymous observation alone cannot identify the user's exact cause; subsequent device reports and authenticated probes are recorded below.

## User-confirmed behavior and exact trigger

The user twice clarified that the current App automatically opens its internal browser. They then supplied the legacy “NGA后台抽风了，请尝试…使用内置浏览器打开” toast and stated that the App version matches the current project. Do not ask these questions again.

The only matching production string is ArticleListModel.java:122. The actual chain is:

1. Legacy loadPage receives the response and calls ArticleConvertFactory.getArticleInfo.
2. The parser returns null and ErrorConvertFactory cannot extract a recognized site error.
3. ArticleListModel throws ServerException with the reported toast.
4. ArticleListPresenter.LegacyCallback.onError (:267–270) permits browser handling for this exception. Existing account fallback may precede it; no identity rotation was exercised in this investigation.
5. showWithWebView (:346–354) obeys the default-true preference, opens ForumWebFragment and finishes the native page.

With the current code this identifies the compatibility-disabled legacy path; the scoped path cannot emit this literal. It is not the legacy Retrofit HTTP 403 error, nor evidence of a deleted post or a proven server fault. Which specific response/parse/render event caused null on the phone remains unknown.

### Same-device switch comparison

- After enabling compatibility, the user reports 47649423 displays normally in the native page. They did not explicitly state whether it showed a compatibility notice, so do not infer its successful source.
- For 47649534 the user explicitly reports the compatibility notice.
- For 47649545 the user explicitly compared both settings: disabled automatically opens the internal browser; enabled displays in native compatibility mode.
- Given a fresh reader and current code, the latter notice means a normal-source FORMAT result was followed by a successful App-source read. HTTP 403, unknown HTML, transport/network errors and identity CONTENT failures are not eligible for this source fallback. This narrows the current observed transition to the ordinary parser/row/render boundary; it does not name the swallowed exception.
- No additional user-supplied URLs or repeated questions about browser navigation are needed. Capturing the device's bounded failure stage is the useful next step.

## Authorized two-source observations

The user explicitly requested that App endpoints be read with cookies from .temp. Read only the authorized cookies.txt header, selected ngaPassportUid and ngaPassportCid in memory to match App requests, and never printed their values. After the second URL was supplied, the same authorization was used for that sample too.

For each sample: one GET normal read and one POST App read, no redirects, retries, host changes or account changes, 20 s timeout and 4 MiB bound. A mobile browser UA and X-User-Agent: Nga_Official were used; the exact phone UA is unknown. Requests stop on access/rate/business error rather than using the other endpoint to bypass it.

| tid | source | HTTP | Content-Type | bytes | elapsed |
| --- | --- | --- | --- | --- | --- |
| 47647197 | read.php, __output=8&noprefix&v2, page=1 | 200 | text/javascript; charset=GBK | 19132 | 137 ms |
| 47647197 | app_api.php?__lib=post&__act=list, POST page=1&tid | 200 | text/json; charset=UTF-8 | 28366 | 172 ms |
| 47649423 | read.php, __output=8&noprefix&v2, page=1 | 200 | text/javascript; charset=GBK | 5284 | 135 ms |
| 47649423 | app_api.php?__lib=post&__act=list, POST page=1&tid | 200 | text/json; charset=UTF-8 | 6885 | 137 ms |
| 47649534 | read.php, __output=8&noprefix&v2, page=1 | 200 | text/javascript; charset=GBK | 8748 | 143 ms |
| 47649534 | app_api.php?__lib=post&__act=list, POST page=1&tid | 200 | text/json; charset=UTF-8 | 11773 | 425 ms |
| 47649545 | read.php, __output=8&noprefix&v2, page=1 | 200 | text/javascript; charset=GBK | 2413 | 140 ms |
| 47649545 | app_api.php?__lib=post&__act=list, POST page=1&tid | 200 | text/json; charset=UTF-8 | 2802 | 185 ms |

No deletion/access error was demonstrated in these authenticated responses. Eight authenticated Python requests total. Payloads are temporary mode-0600 files outside the repository, not task fixtures, and are not embedded in documentation.

One additional diagnostic request for 47649545 used production ArticleByteClient, production request construction, the same authorized Cookie and UA, and production OkHttp defaults (including gzip), with a one-network-exchange cap and 20 s timeout. It returned HTTP 200, gzip, GBK, and passed NormalArticleParser plus full HTML rendering. It contained 3 rows rather than the earlier 2; responses were not byte-identical, so this is a later sample, not an exact transport replay of the original payload. Nine authenticated network requests total across all samples, plus the earlier one anonymous request. No redirect/account switch/retry was used. This reduces a Python-only transport explanation; it still does not reproduce the phone environment.

## Offline replay and limits

- First normal response contains literal TAB within a JSON string; Python strict JSON rejects it. The project's Fastjson accepts it, so that observation is not a demonstrated App defect.
- Existing project debug runtime classpath was resolved offline. Old ArticleConvertFactory, NormalArticleParser and AppArticleParser accept the captured data with rendering omitted: 20 rows for the first sample, 6 for the second; scoped contentComplete=true.
- All six production body decoder stages and comment/attachment/signature/vote builders pass. The initial JVM TextUtils stub exception was a harness limitation; a temporary isEmpty implementation removed that limitation.
- A fuller replay recompiles current ArticleConvertFactory.java, HtmlConvertFactory.java and ForumDecoder.java in /tmp, calls actual renderRow, uses production HTML assets and full builder/decoder chains, and supplies temporary Android configuration/asset adapters. Both sources produce complete HTML for all 20/6 rows. Blacklist is empty and standard sizes/light theme/image-enabled/signature-disabled settings are used.
- The same old/new parser and full-render replay accepts both sources of 47649534 (11 rows) and 47649545 (2 rows). The later production-client response passes with 3 rows. Thus no current captured sample reproduces the phone's FORMAT failure.
- This is host replay, not an Android device or minified-release reproduction. Actual SharedPreferences, request UA/domain/account identity, lifecycle, concurrent rendering, and phone network responses are not reproduced. Successful host replay cannot establish that the user's App is working.
- Normal data uses GBK, matching the legacy JsonStringConvertFactory; App data is UTF-8 and goes through its separate byte-client parser. No encoding mismatch was demonstrated in the captured normal response.
- The configured request host comes from ForumUtils/BaseModel, not necessarily the input link. Default index 1 is bbs.nga.cn. The user did not supply a literal settings value.
- DTOs implementing JavaBean have an existing consumer ProGuard keep rule; do not infer a missing keep rule solely from release minification being enabled.

Temporary reproducibility artifacts: /tmp/nga-reader-authorized-probe.py, /tmp/nga-reader-47649423-probe.py, /tmp/nga-reader-test-classpath.txt, and /tmp/nga-reader-replay/{ReaderReplay,RenderReplay,FullReaderReplay}.java. They are investigation artifacts, not shipped tests. Safe summaries are alongside them; raw response files must never be printed or committed.

## Next evidence

The requested same-device switch comparison is complete. Propose a reviewed diagnostic change that preserves fixed failure stage/source and a bounded allowlisted reason around wrapper parsing, DTO conversion and rendering, without raw message/body logging. Report the original normal failure even when App fallback succeeds. Only after device evidence identifies the cause should the plan claim a specific parsing fix. Keep the desired native-reading outcome and automatic-browser UX decision distinct.
