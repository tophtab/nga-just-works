# 47440211: ordinary response truncation reproduced

Date: 2026-09-30. User-supplied URL:
`https://bbs.nga.cn/read.php?page=1&tid=47440211`.
User-supplied expected title: `每日全a拥挤度分享`.

## Result

This example reproduces a real ordinary-reader failure from a live response.
The first ordinary response ends inside an attachment object's field name,
leaving an open string and six unclosed JSON containers. It fails at JSON
decoding before row conversion or body rendering. The compatibility response
is complete and renders 19 rows. A later ordinary response is also complete
and renders 19 rows.

Crucially, **the entire first response is an exact prefix of the later complete
ordinary response**: 11,915 characters out of 17,596, with the remaining 5,681
characters absent. This is observed truncation, not an inference from a generic
FORMAT error or an optional-field mutation.

The complete ordinary topic ID and every App row's topic ID match 47440211.
Both complete responses' topic titles match the title supplied by the user.

## Authorized requests and boundaries

The user supplied this link immediately after discussing reproduction with App
requests, continuing the existing R7 investigation and existing authorization
to use the local Passport Cookie pair. Three manual diagnostic requests were
made: one ordinary read, one compatibility comparison, and one further ordinary
read to inspect compressed transport. Each used production
`ArticleByteClient` request construction and decoding, the same selected
Passport account, the same host, and the same mobile-browser UA.

Requests used `X-User-Agent: Nga_Official` and OkHttp's gzip behavior. The UA
was the investigation's Android 14 / Chrome 120 string; the phone's exact UA
and network environment remain unknown. Each request had a 20-second call
timeout, 4 MiB decoded-body limit, and one network-exchange cap. No automatic
retry, redirect, account rotation, alternate host, or browser navigation was
performed. No device operation or product change occurred. No further live
requests are needed to establish this sample's truncation.

The third request is a later observation, not a replay of the first compressed
stream. Its network interceptor copied at most 4 MiB + 1 bytes of compressed
data with `peekBody`; the original response still went through the production
byte client. Private response files were created with mode 0600 and are not
embedded in this task.

| UTC request time | Source | HTTP / declared encoding | Decoded characters | Parser and rendering result |
| --- | --- | --- | --- | --- |
| 11:57:24 | Ordinary GET | 200 / GBK / gzip | 11,915 | Legacy null; scoped FORMAT; same failures with rendering omitted |
| 11:57:55 | App POST | 200 / UTF-8 / gzip | 26,401 | 19 rows, contentComplete=true; full rendering passes |
| 12:01:39 | Ordinary GET, wire inspection | 200 / GBK / gzip | 17,596 | Legacy and scoped both accept 19 rows; full rendering passes |

Ordinary request: `/read.php?&page=1&__output=8&noprefix&v2&tid=47440211`.
App request: `/app_api.php?__lib=post&__act=list`, POST `page=1&tid=47440211`.

## Failure localization

A temporary copy of the current `ArticleConvertFactory` was compiled outside
the repository. Only its swallowed-exception catch was instrumented to print
the exception class and project/library stack frames, without messages or
response text. Replaying the private failed response produces:

```text
com.alibaba.fastjson.JSONException
JSONLexer.scanSymbol
DefaultJSONParser.parseObject
JSON.parseObject
ArticleConvertFactory.parseJsonThreadPage:75
```

The failure occurs at initial JSON decoding, before metadata, DTO, author,
comment, attachment conversion or rendering. The original received string is
already incomplete. It contains no `/*error fill content` marker, so the
separate wrapper-truncation defect found in synthetic research is not the
cause of this response.

The same incomplete string passed to the unchanged legacy entry returns null;
the scoped entry returns FORMAT. With the currently inspected presenter, an
unrecognized legacy null leads to ServerException and default browser handling,
while enabled scoped FORMAT permits one App fallback. The live App response
passes. This establishes a concrete failure path consistent with the reported
browser-versus-compatibility symptom; it is not a phone UI recording.

## Transport check and remaining uncertainty

The later ordinary response contained 6,122 compressed bytes. Its HTTP body
reported unknown length (`-1`). Independent Python gzip decompression validated
the compressed stream and produced 18,794 bytes of GBK data. Independent strict
GBK decoding exactly matches the production client's string, and its roundtrip
encoding exactly matches the decompressed bytes.

The first string represents 12,530 GBK bytes; the later complete response is
18,794 GBK bytes. Production reading returned normally for the first request,
but its original compressed bytes were not retained. Therefore the later gzip
integrity result must NOT be claimed as a direct validation of the first stream.
The third request also changes time and introduces compressed-body peeking;
it cannot by itself attribute recovery to a particular server or transport cause.

Evidence establishes an incomplete ordinary response arriving at the parser
and success with a complete response. It does not distinguish NGA response
generation, an upstream cache/intermediary, or another transport boundary as
the origin of the truncation. It also does not prove that all earlier examples
share this cause. No server-side logs or failing phone capture were obtained.

## Offline synthetic reproduction

A synthetic page with one readable row and one attachment was cut inside the
attachment's `thumb` field name, retaining an exact prefix just like the live
failure. Current parser/renderer behavior:

```text
truncated legacy=NULL
truncated scoped=FORMAT
complete legacy=OK
complete scoped=OK
```

This gives a privacy-safe regression case for a future recovery policy. Do not
repair the fixture by appending delimiters and accepting a partial page: missing
reply content cannot be reconstructed from a truncated prefix.

## Revised next step

### Correction: the account comparison concerns a different topic

The user clarified that the A-B-A account comparison was performed on another
post, not 47440211. The earlier supplied 47440211 post now works under the
original account. Do not attribute account-dependent failure to this captured
sample or treat its truncated response as the cause of the other post's failure.
See [the separate account-dependent observation](account-dependent-observation.md).

### User-reported sustained failure window

The user subsequently clarified that the usual pattern is a period during
which repeated opens all enter the internal browser, followed by ordinary
reading working again later. This is qualitative user evidence; it does not
establish the duration, request count, or byte equality of those failed responses.
Our one failed and one later successful ordinary capture establish two points
about four minutes apart, not that the failure lasted the entire interval.

This timing is consistent with an upstream response-generation/cache/path
problem persisting for a while, but does not prove a CDN or cache defect.
The inspected ordinary Retrofit and scoped OkHttp builders do not configure
an HTTP response cache. `ArticlePageRequestState.failForegroundLoad` returns
to IDLE when there is no prior data; explicit refresh starts a new request.
The inspected reader has no timer that locks a failed topic for several minutes.
Successful source adoption persists within one reader, but that does not explain
repeated fresh opens failing and later fresh ordinary reading succeeding.

Consequently, one immediate ordinary retry is not an evidence-backed solution
for this report. Recovery design should prioritize maintaining readable native
content through the existing eligible App source during an ordinary-source
failure window. Any preference/default or browser-navigation change remains
a product-design decision, not something this research silently implements.

To distinguish persistent upstream bad responses from intermittent truncation,
the next targeted observation during an actual failure window would compare
a small bounded set of failed-response lengths and local hashes, and safe
cache/freshness header metadata such as Age, Cache-Control and cache-hit status.
Repeatedly receiving the same incomplete bytes would support persistent bad
response delivery; it would not by itself identify which cache or server caused
it. No extra requests were made for this user clarification, and no polling,
cache-busting query, host/account rotation, or new mobile diagnostic feature is
required merely to record this observation.

R7-A phone diagnostics are no longer a prerequisite for investigating or
designing a correction for this sample. They remain useful for other failures
that cannot be reproduced from a link.

The next review should focus on incomplete-response recognition and bounded
recovery. A possible policy is one additional foreground read of the same
ordinary query/account/origin after a proven incomplete 2xx response, followed
by the existing enabled compatibility path if necessary. This is a design
candidate, not an implemented or validated retry policy. The observed success
was about four minutes later; it does not prove that an immediate retry would
succeed. The user's sustained-failure report further weakens immediate retry
as the primary recovery strategy. Define pacing and a shared request budget
before implementation; avoid delaying a permitted working native App fallback
with repeated ordinary requests.

Any recovery must preserve access/auth/rate-limit stops, generation and account
checks, cancellation, foreground ownership, and incomplete-cache prevention.
Do not add retries for every FORMAT error or expand legacy account rotation.
Keep browser UX choices separate from data recovery. No claim of a shipped fix
or an improved device success rate follows from this investigation.

## Local reproduction artifacts

Temporary code: `/tmp/nga-reader-replay/R7LinkProbe.java`, `R7WireProbe.java`,
`R7TruncationReplay.java`, and `/tmp/nga-reader-trace-47440211/LocalTrace.java`.
Safe summaries: `/tmp/nga-reader-47440211-{normal,app,wire-normal}-summary.json`,
`/tmp/nga-reader-47440211-comparison-summary.json`, and
`/tmp/nga-reader-replay/r7-truncation-replay.txt`.
Private raw response and gzip files must not be printed, committed, or copied
into tests. Do not rerun network probes merely to reproduce this report; use
saved responses locally or the synthetic truncation case.
