# Confirmed current failure: raw tabs in topic-detail strings

Date: 2026-09-12. Baseline: `feature/ai-summary@9a8113a9`.
Authorization and request limits: `live-plan.md`.

## Result

The current production parser reproduced the maintainer's exact visible error
on the first original-topic detail. The request itself succeeded with HTTP 200.
There were exactly two network transmissions. The probe stopped after the
failure; no later topic, reply-list, model, or device request was made.

| Observation | Topic list | First topic detail |
| --- | --- | --- |
| Operation | `TOPIC.LIST` | `THREAD.PAGE` |
| HTTP status | 200 | 200 |
| Response media type | `text/html` | `text/javascript` |
| Declared charset | None; production GBK fallback | GBK |
| Decoded UTF-16 characters | 11,406 | 17,617 |
| Raw controls inside quoted strings | None | 53 × U+0009 (TAB) |
| Current production parser | Success; 15 accepted topics | `NGA 内容格式异常，请稍后重试` |
| Same response with in-memory string-control escaping | Not needed | Success; verified original body, 141 characters |

Content lengths were unknown (`-1`) rather than oversized. The real production
byte decoder accepted both responses. The failure was therefore localized to
structure parsing after GBK decoding, not HTTP, missing credentials, or a
transport-size limit. Although the list labels itself `text/html`, its body
was the expected parseable data rather than an HTML access challenge.

The post-comparison changed only literal controls in quoted strings to JSON
escapes. It retained all response fields and the same production TID/author/
explicit-floor validation. This successful comparison used the same response
in memory and incurred no extra network request. It is diagnostic evidence,
not a modification of the application or proof that a shipped APK is fixed.

## Causal chain

1. `0482795c` added original-topic `read.php` collection to profile analysis.
2. `NgaTopicBodyParser.normalize` copies quoted strings unchanged.
3. `SafeJsonParser.checkNesting` rejects every literal character below U+0020
   inside a quoted string. Its strictness is intentional for model responses.
4. The native NGA detail contained raw TAB characters, so the shared preflight
   rejected it before original-post projection.
5. `NgaProfilePageSource` mapped that rejection to the reported fixed error and
   stopped collection. The reply-list and model stages were never reached.

The probe counted string controls without retaining their containing fields
or text. Do not claim that today's TAB characters occurred specifically in
`alterinfo`; that field is used only for the synthetic differential example.

## Evidence handling and limitations

The local Java harness used the production request builders, byte decoder,
topic-list parser, and topic-body parser compiled during the passing baseline.
It used a fixed Android-browser UA and the previously supplied local session.
It did not execute an APK or inspect the maintainer's phone. Raw responses,
Cookies, names, target IDs, and topic IDs were neither logged nor persisted by
this probe. The retained local result is sanitized JSONL at
`.temp/ai-profile-content-format/live-results.jsonl`.

The observation verifies this target, session, response, and date. It does not
establish that every NGA response has this shape or that correcting it resolves
all possible profile errors. The concrete reported format failure is reproduced.
