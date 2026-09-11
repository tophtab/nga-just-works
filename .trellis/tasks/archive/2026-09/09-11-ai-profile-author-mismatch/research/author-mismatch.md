# Unavailable Activity Records

## Evidence and Root Cause

`authorized-live-verified`, 2026-09-11, operation `TOPIC.LIST`: four sequential
first-page GETs to `https://bbs.nga.cn/thread.php`, using the user-provided account
snapshot, the pinned identity headers, no redirects, and bounded GBK decoding.
All four returned HTTP 200 and the expected JSON object shape.

| Target / kind | Returned rows | Explicitly unavailable | Available |
| --- | ---: | ---: | ---: |
| Supplied account / topics | 5 | 0 | 5 |
| Supplied account / replies | 19 | 3 | 16 |
| Requested profile / topics | 35 | 28 | 7 |
| Requested profile / replies | 20 | 14 | 6 |

The requested profile's second topic row is already an unavailable placeholder.
Those topic records have nonblank string `denied` / `error` markers and a
nonmatching author; the current parser throws at the ordinary author check.
Unavailable reply records can still have a matching `__P.authorid`, a string
`__P.content`, and nonblank `__P.denied` / `__P.error`. Consequently author and
content-type validation alone is insufficient.

The raw content and credentials were not logged or retained as fixtures. The
ignored `.temp/nga-profile-debug-4mov_egz/` directory holds only structural
metadata and synthetic payloads with replaced authors, titles, bodies, and IDs.

## Narrow Compatibility Contract

- Recognize the observed nonblank string `denied` and `error` item markers.
  Empty/whitespace markers do not mark an item unavailable. Do not guess new
  numeric, Boolean, or container-shaped marker semantics.
- Skip an explicitly unavailable outer topic record before ordinary author
  validation. For reply collection, also inspect the `__P` record and skip it
  when explicitly unavailable. No denial text/body enters `ProfileSummaryInput`.
- Continue rejecting unmarked malformed records and unmarked foreign authors;
  this is not a general filter that silently discards arbitrary author mismatch.
- Skipped records do not consume the accepted-entry cap. Stay on the current
  first page and stop after 20 accepted records, without loading another page.
- An all-unavailable page is an empty activity sample. Existing whole-page
  `error` / `__MESSAGE` / challenge handling remains an error, not empty success.
- `ProfileSummaryLoader` already accepts an empty topic page followed by visible
  replies and reports no usable content only if both samples are empty.

## Regression Cases

1. A visible topic, an unavailable foreign-author placeholder, and another
   visible topic: accept only the visible topics.
2. Denial on the outer reply row or `__P`: omit the item, even when its author
   matches and its content is a string.
3. Foreign-author rows without a denial marker: retain the error.
4. Empty markers: retain ordinary author/body validation and valid entries.
5. All-unavailable page: successful empty sample; whole-page rejection still
   fails. Partial availability across the two pages remains usable.
6. Many unavailable records before visible records: accept up to 20 visible
   entries without treating skipped records as the limit.

The user declined local builds. Add the focused Java regressions without invoking
Gradle or assembling an APK locally; record the actual static/live verification
performed and leave remote build validation to the authorized push workflow.
