# R7 resumed investigation: native reading and fallback triggers

Date: 2026-09-30. Inspected product revision: `1a8413d9`.

Subsequent evidence: [the newly supplied 47440211 link](47440211-truncated-response.md)
reproduced ordinary-response truncation with production requests. Its diagnosis
does not require phone diagnostics first. The negative replay results and
remaining-evidence statements below describe the earlier four-sample pass.

The user explicitly resumed investigation of why posts fail in the native reader
and trigger compatibility or the internal browser. This continues the existing
R7 task; it does not activate product implementation or approve the proposed
R7-A diagnostic preference. No new task, product edit, live request, device
operation, build packaging, or credential access was performed in this pass.

## What the two fallbacks mean

| Path | Request and rendering | Trigger |
| --- | --- | --- |
| Ordinary reader | GET `read.php?&page=...&__output=8&noprefix&v2&tid=...`; convert data and render rows locally | Initial source for a new reader |
| Compatibility reader | POST `app_api.php?__lib=post&__act=list`; different response adapter, same reader and body renderer | Compatibility enabled, current foreground ordinary request fails with FORMAT; at most one App attempt |
| Internal browser | Open the ordinary web URL in `ForumWebFragment`, then finish the native reader activity | Eligible terminal failure plus the default-true browser preference |

Compatibility defaults off. Enabling it also selects a different ordinary
transport/parser path: the legacy Retrofit/global converter is replaced with
`ArticleByteClient` and `NormalArticleParser`. It is not a force-App switch.

After successful App adoption, that reader's subsequent pages and refreshes
retain the App source. A new reader or account/compatibility environment reset
starts ordinary reading again. Seeing compatibility repeatedly within one
reader therefore does not prove every page independently failed ordinary parsing.

Source anchors:

- `ArticleListPresenter.java:116`: preference selects legacy/scoped request flow.
- `ArticleListPresenter.java:205`: FORMAT-only App fallback and terminal handling.
- `ArticleFailure.kt:22`: only FORMAT permits App; FORMAT/CONTENT/PROTOCOL permit browser.
- `ArticleReaderSession.kt:31/:62`: environment reset and adopted source state.
- `ArticleListPresenter.java:346`: browser preference check, navigation and native-page finish.
- `res/xml/settings_lab.xml:4`: compatibility false, browser true.

The internal browser uses a web-page response and site JavaScript instead of
the ordinary JSON-to-row pipeline. Its success does not establish that the
native request received the same response or that a post requires a browser.
App compatibility also projects different fields: the normal adapter processes
attachments and nested comments that the current App adapter does not map in
the same way. Shared final rendering does not imply equivalent parser inputs.

## What is established for the user's examples

The prior same-device comparison for `47649545` remains the strongest evidence:
compatibility off automatically opens the browser; enabled reading succeeds
with a compatibility notice. For a fresh reader running the inspected code,
this establishes ordinary FORMAT followed by App success. It does not establish
which operation produced FORMAT.

HTTP 403, unrecognized HTML, authentication, rate limiting, empty responses,
network failure and identity CONTENT failure do not trigger that App fallback
in the scoped flow. They cannot explain this particular transition merely by
being common reasons for other failed reads. These are source-code conclusions,
not a new capture of the phone's failing response.

The reported legacy “NGA后台抽风了” message is emitted when conversion returns
null and `ErrorConvertFactory` cannot extract a recognized error. The null can
come from JSON conversion, optional field processing, or body rendering; the
message does not establish an NGA server fault.

One hypothesis was checked and excluded: the legacy converter catches body-read
IOException and returns an empty string, but `ErrorConvertFactory.java:17`
recognizes the empty string as a network error. That path alone does NOT produce
the reported ServerException/browser trigger. Do not conflate the two paths.

## New offline reproduction results

The existing private response files were reused locally. Current
`ArticleConvertFactory`, `HtmlConvertFactory` and `ForumDecoder` sources were
recompiled into the temporary replay harness. Other dependencies use the
existing debug classpath. The harness supplies Android settings/assets adapters
and an empty blacklist; it does not substitute new parser logic.

Matrix: 4 topics × 3 entry paths (legacy normal, scoped normal, App) × 8 settings
combinations (signature on/off, images on/off, dark/light) = **96 accepted page
replays, 0 failures**. Every accepted main row had generated HTML.

This expands the earlier single-configuration replay. It does not test the
phone's actual preferences implementation, account state, minified release,
concurrent page rendering, or exact failing response. These 96 replays are
neither new network observations nor a measured device success rate.

Separately, a synthetic readable page was mutated one field at a time:

| Synthetic case | Legacy result | Scoped normal result | Relevant source |
| --- | --- | --- | --- |
| Baseline, valid row/body/identity | OK | OK | Control |
| `__ROWS` is string `"1"` | null | FORMAT | Direct Integer cast, ArticleConvertFactory:92 |
| `__R__ROWS` is string `"1"` | null | FORMAT | Direct Integer cast, :125, even after strict getInteger validation |
| Optional `__T` is an array | null | FORMAT | Cast outside local metadata catch, :111 |
| Row `comment` is an empty array | null | FORMAT | Direct JSONObject cast, :301 |
| Author `buffs` is an empty array | null | FORMAT | getJSONObject outside local user-field catches, :351 |
| Valid body contains literal `/*error fill content` | null | FORMAT | Unscoped substring truncation, :63–64 |
| Renderer deliberately throws a synthetic exception | null | FORMAT | Renderer inside whole-page catch, :189/:218/:103 |

These cases prove coarse failure collapse and sensitivity to field shapes. The
type-changing cases do not prove that the server supplies those shapes for the
user's failing requests, or that every malformed field should be accepted.
The renderer case is injected to verify propagation, not evidence of a naturally
occurring renderer exception.

The body-marker case demonstrates an independent client defect using valid
JSON and string body content. The parser searches the entire raw response for
a presumed trailing server marker and cuts at its first occurrence, including
inside quoted post text. A later repair must distinguish an actual wrapper
suffix from a string literal and preserve ordinary post text. No saved sample
failed this replay, so this defect is NOT established as the frequent device
failure's root cause.

Safe synthetic baseline (no captured user content):

```json
{"data":{"__ROWS":1,"__R__ROWS":1,"__T":{"tid":100001,"authorid":42},"__U":{"42":{"username":"synthetic"}},"__R":{"0":{"tid":100001,"pid":0,"lou":0,"authorid":42,"content":"readable body"}}}}
```

Temporary local artifacts:
`/tmp/nga-reader-replay/R7OfflineAudit.java` and
`/tmp/nga-reader-replay/r7-offline-audit.txt`. The latter contains only case
labels, outcomes, and aggregate counts. Private original response files must
not be copied into task fixtures or printed. Temporary paths are not guaranteed
to survive a new environment; the baseline and mutation table above preserve
the synthetic experiment independently.

## Result and next evidence

The automatic fallback mechanism is established. The exact cause of the
phone's ordinary FORMAT remains unobserved. Successful later host responses
cannot disprove the reported device failure or establish a repair.

The useful next evidence remains a request-local fixed diagnostic stage/reason
at the failing conversion, retained even after App success. Distinguish wrapper
cleanup, JSON decoding, page counters/metadata, row/author/comment conversion,
and body rendering. Do not retain raw exceptions, response text, cookies or
account identifiers. The existing R7-A proposal is the implementation boundary
for obtaining that evidence; it remains proposed, not implemented by this research.

Once the failing stage is known, repair that specific contract and verify it
with synthetic regression inputs. Optional display defects should be isolated
where evidence supports it while preserving query/account/core-content checks.
Any later change to automatic browser navigation is a separate product choice;
it cannot substitute for successful native content loading.
