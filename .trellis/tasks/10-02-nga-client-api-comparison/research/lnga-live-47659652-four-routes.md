# Four reader routes: authorized live comparison

2026-10-02 21:59:35–21:59:51 Asia/Shanghai. Same user-authorized account from
.temp/cookies.txt, thread47659652, page1. Four sequential read requests, at least
five seconds apart, no automatic retries/redirects/media downloads. No raw
responses or credentials persisted. Metadata: lnga-live-47659652-four-routes.json.

| Route | HTTP / application | Data check |
| --- | --- | --- |
| User-table official-style signed App POST, ngabbs.com app_api.php post/list | HTTP200, code0, UTF-8, 25,500 bytes | 19 rows, all TID match, all PID numeric, 19 nonempty contents; currentPage1, perPage20, totalPage2 |
| Current-compatible unsigned App POST, same host/path | HTTP200, code0, UTF-8, 25,500 bytes | Same validated counts/page fields; not a byte-equivalence claim |
| LNGA signed HTML POST, ngabbs.com read.php output17 | HTTP200, code521/template read, UTF-8, 61,754 bytes | Upstream extractor matches TID/page1; 19 rows with19 nonempty bodies; main row present; declared slots20, total26 |
| Ordinary GET bbs.nga.cn read.php output8/noprefix/v2 | HTTP200, JavaScript/GBK, 12,556 bytes | App preprocessing + Fastjson2 2.0.59.android8 JSON.parseObject throws JSONException |

## Request fidelity

Official-style request follows the user's supplied table: app_id1010, time,
access_uid/access_token, tid/page/sign, no Cookie, User-Agent version9.9.68,
Referer https://ngabbs.com. Signature tid concatenation is independently evidenced
by the pinned ForeverZero/NgaClient postList implementation. Optional pid/uid/opt
were omitted for this full-thread first-page case; no extra output selector added.
Model/OS in UA were representative Xiaomi/Android14, not captured from an actual
installed official APK. This is table-reconstructed behavior, not proof every
current official-client request matches that table.

Unsigned App POST sends Cookie, browser UA, bare X-User-Agent Nga_Official and
only tid/page, no Referer/signature. Its response was decoded using returned
UTF-8. This validates the wire and consumed shape, not the legacy global GBK
converter or full Android AppArticleParser/rendering pipeline.

## Conclusion and limits

All three alternative routes retrieved structurally valid matching page content
while the default JSON parse still failed in the same brief observation window.
The authenticated unsigned App route succeeded too: no evidence signing is
required for this case, and no demonstrated advantage for a new HTML parser over
existing App JSON. Existing compatibility integration is therefore a lower-change
candidate to investigate before adopting another parser. Do not infer universal
signature dispensability, guest eligibility or long-term reliability.

Both App routes have same size/count/page fields, but bodies were not retained
or compared; do not claim identical content. Nineteen nonempty bodies do not prove
rich-text/media/comment/edit-source parity. LNGA declared20 slots vs19 actual rows
is consistent with its sparse representation, not proof of a missing visible row.
No user-agent/transport/host factor was isolated, and Python desktop networking
is not Android OkHttp. All statuses200: these results concern intermittent default
parse failure, not actual HTTP403 recovery. Stop further traffic after this batch.
