# Live comparison: default JSON failure and LNGA HTML success

Date: 2026-10-02. User-supplied thread47659652/page1; same authorized credentials
from .temp/cookies.txt. Four read requests total: initial three-path comparison,
then one explicitly announced diagnostic read using the production JSON stage.
No retry, redirect, image fetch, identity switch or raw-response persistence.

| Local time Asia/Shanghai | Request | Result |
| --- | --- | --- |
| 21:52:30 | Ordinary GET bbs.nga.cn, output8/noprefix/v2 | HTTP200, JavaScript/GBK, 12,556 bytes; invalid standard JSON, no closing brace/topic marker after bounded cleanup |
| 21:52:38 | LNGA signed POST ngabbs.com, output17 | HTTP200, UTF-8, 61,753 bytes; code521/template read; actual upstream HTML extractor confirms matching tid/page1, 19 rows with 19 nonempty bodies, main row present |
| 21:52:43 | Ordinary GET ngabbs.com, same default fields | HTTP200, JavaScript/GBK, 12,556 bytes; same structural failures |
| 21:54:29 | Ordinary GET bbs.nga.cn, production JSON-stage diagnostic | HTTP200, 12,556 bytes; JSONException after the exact current ArticleConvertFactory preprocessing and Fastjson2 2.0.59.android8 JSON.parseObject |

## Meaning

The default response fails the application's actual JSON parse stage, not only
a stricter Python parser. The source method catches this exception and returns
null; this is consistent with the user's generic backend-error toast when no
recognized business error is found. ErrorConvertFactory and the entire Android
rendering pipeline were not executed by the harness. The production stage was
extracted directly from current ArticleConvertFactory.java and compiled against
the installed Gradle Fastjson artifact; exceptions were redacted to class name.

The alternate request produced parsable matching thread data during the same
short interval. It is concrete evidence that LNGA-style reading helps this
specific default-read failure. It does not establish POST alone caused success,
long-term reliability, complete rendering/quote/media parity, or HTTP403 recovery:
all four HTTP statuses were200. Both default hosts failed, so changing only to
ngabbs.com did not resolve this observation.

HTML output has 19 actual row slots but declared current-row count20 and total26;
sparse floor semantics are already a documented LNGA adapter gap. Do not assert
all content is preserved or invent a missing floor; only19 nonempty parsed rows
and expected identity were verified. Body byte length is not a content-equivalence
metric because the two formats differ. Invalid JSON suggests corruption or an
incomplete/nonstandard representation, but the precise server-side cause is not
proven. Raw private content was never written to disk or tool output.

Transport used desktop Python requests and a source-compatible browser UA,
not the installed Android client's exact UA/OkHttp stack. This limits attribution
while the direct production-JSON-stage rejection strengthens the parsing finding.

Evidence: lnga-live-47659652-results.json and
lnga-live-47659652-parser-check.json contain redacted metadata only.
