# AI and author-location coverage ownership

Boundary: remove redundant test scenarios only in AI, profile, and AI editor tests. No product, spec, build, fixture framework or device changes. Main owns serial measurements and Gradle validation.

## Baseline inventory

| Suite | Baseline methods | Final methods | Baseline lines | Final lines |
| --- | ---: | ---: | ---: | ---: |
| AiConfigRecordTest | 8 | 6 | 172 | 143 |
| AiConfigStoreTest | 12 | 10 | 274 | 252 |
| AiConfigTest | 8 | 5 | 111 | 75 |
| AiModelsClientTest | 25 | 20 | 658 | 536 |
| AiProfilePromptTest | 5 | 2 | 71 | 37 |
| AiResponseParserTest | 20 | 12 | 260 | 180 |
| AiStreamParserTest | 18 | 17 | 327 | 319 |
| AiSummaryClientTest | 25 | 20 | 638 | 550 |
| AiSummaryUiContractTest | 1 | 0 | 73 | 0 |
| NgaProfilePageSourceTest | 36 | 25 | 1126 | 899 |
| ProfileRequestQueueTest | 4 | 4 | 144 | 144 |
| ProfileSummaryLoaderTest | 17 | 11 | 420 | 307 |
| SummaryControllerTest | 24 | 19 | 591 | 515 |
| SummaryInputTest | 12 | 9 | 319 | 249 |
| AuthorLocationPageTest | 18 | 15 | 496 | 446 |
| AuthorLocationRepositoryTest | 39 | 25 | 959 | 710 |
| AuthorLocationStoreTest | 8 | 8 | 185 | 185 |
| ProfileLocationParserTest | 14 | 14 | 197 | 197 |
| ProfileLocationTransportTest | 10 | 10 | 275 | 273 |
| ProfileSessionTest | 3 | 3 | 57 | 57 |
| AiModelEditorStateTest | 12 | 11 | 194 | 175 |
| AiProfilePromptEditorStateTest | 5 | 3 | 104 | 79 |
| AiSettingsContractTest | 4 | 1 | 134 | 71 |
| **Total** | **328** | **250** | **7785** | **6399** |

Assigned paths include 307 AI/profile-domain methods plus 21 AI UI methods (328 total); the task’s 307 grouped count excluded the latter. 23 files become 22.

## Primary retained ownership

- AiConfig: endpoint/key/model validation. AiProfilePrompt: blank/size validation and private diagnostic protection. AiConfigRecord: AES nonce/authentication, v2 style encoding and bounds. AiConfigStore: v1 read/no rewrite/upgrade, lost vs temporarily unavailable keys, atomic save, two independent clear failures and full stored-prompt integration.
- AiResponseParser: bounded shared JSON, typed shape, first text fallback, model IDs and row limit. AiStreamParser: framing/UTF-8 and shared accumulator incremental reasoning/protocol screening; preserve distinct finish/EOF/overflow boundaries.
- AiSummaryClient: POST wire, connection-test shape, stream progress/timeout/cancel, independent decompression bound, cookie/auth/redirect/no-retry and nonstream transport errors. AiModelsClient: derived route, root fallback gate, encoded/custom no fallback, returned-call budget/cancel, isolated key/cookie behavior and bounded decoding.
- ProfileRequestQueue: all four fake-time slot/wakeup/cancellation rules. ProfileSummaryLoader: attempt identity, empty exhaustion, cancellation and synchronous reentrancy. NgaProfilePageSource: actual list wire and viewed author, unavailable entry filtering, typed hostile input, source transport cleanup/cancellation and one paced collection integration.
- SummaryController: configuration gate/revalidation, queued UI progress/copy policy and cancellation generations. SummaryInput: immutable selected row, positive-number COMMENT floor suppression and plain text boundaries. AI editor states: input preservation/cache ownership and cancellation.
- ProfileSession: exact origin and credential identity. ProfileLocationParser: JavaScript extraction, source decoys, supported manual envelope repairs, strict returned identity and bounded plain location. ProfileLocationTransport: status/encoding/size and session request wire. AuthorLocationStore: supported legacy cooldown persistence, key isolation, corruption/expiry and bounded eviction.
- AuthorLocationRepository: distinct stale account/captured credential outcomes, monotonic paced queue, per-owner foreground and retry budget, disk restore/delivery race, scope pause vs key failures. AuthorLocationPage: deferred settlement, old/new subscriber handoff, pending account invalidation, replay intent and synchronous observer reentrancy.

## Retained large suites and infrastructure

Repository and Page suites stay comparatively large because session signal, completion-time recheck, captured account stop, same-UID credential stop, owner pause and synchronous observer reentrancy are distinct transitions with different failure signals. Stream/parser suites keep encoding, framing, structure, privacy and resource bounds rather than treating all malformed input as interchangeable.

MockWebServer is started once per method in AiModelsClientTest/AiSummaryClientTest, with extra destination servers for redirects and second-service credential checks. NgaProfilePageSource uses local servers only in transport methods and pure FakeCalls elsewhere. ProfileLocationTransport has three real loopback server scenarios in one method. Store tests use temporary files or in-memory crypto; no device calls. Helpers are local or intentionally shared: AiConfigRecordTest legacy crypto helpers, AiResponseParserTest JSON fixtures, AiStreamParserTest event helpers, ProfileRequestQueueTest.FakeTime and SummaryInputTest.repeat.

Loop sites are not scenario totals: fixture construction (20 entries), event stress (2000 callbacks), and output assertion loops are one scenario; input matrices and nested branch permutations execute distinct rows. Deletion ledger records removed method and changed matrix rows separately. Baseline matrices include 36 marker/kind/value combinations in blankAndNonStringMarkersRetainNormalAuthorAndContentValidation, 18 custom route/status combinations and 18 malformed response/fallback combinations.

## Realized infrastructure work per complete suite execution

These counts expand `@Before` and nested input loops from the baseline archive and final source. They are expected executions on a passing run, not merely constructor occurrence counts; main owns observed XML/timing results.

| Suite | Baseline server starts | Final server starts | Baseline loopback requests | Final loopback requests |
| --- | ---: | ---: | ---: | ---: |
| AiModelsClientTest | 27 | 22 | 157 | 78 |
| AiSummaryClientTest | 26 | 21 | 39–40 | 28–29 |
| NgaProfilePageSourceTest | 8 | 5 | 29 | 13 |
| ProfileLocationTransportTest | 3 | 3 | 3 | 3 |
| **Total** | **64** | **51** | **228–229** | **122–123** |

Models: one primary MockWebServer per test (25→20), one redirect destination and one other-service server. Summary: one primary server per test (25→20), plus one redirect destination. The disconnect-at-start case allows zero or one server-observed request, hence the request ranges; it still creates exactly one server. Profile source: eight explicit local-server methods become five. Location transport: one ServerSocket in each of three 200/503/429 rows; these are real loopback starts though not MockWebServer. Thus MockWebServer-only starts are 61→48, raw loopback server starts remain 3. No live hosts are contacted.

## Selected expanded scenario accounting

Each row counts input executions, not assertions, fixture entries, callback counts or source loop sites. Rows refer to final behavior even if the ledger records an earlier intermediate reduction before complete method deletion.

| Method / matrix | Baseline cases | Final cases | Distinct retained boundary |
| --- | ---: | ---: | --- |
| Models base/completed/custom URL matrix | 7 | 0 | Config normalization plus actual root/custom/encoded discovery requests |
| Models root 404/405 × endpoint spelling | 4 | 2 | Both path fallback statuses |
| Models HTML document prefixes | 6 | 3 | doctype/BOM, html tag, self-closing delimiter |
| Models partial HTML / nonleading marker | 6 | 4 | invalid delimiters, incomplete token, peek limit |
| Models custom route × fallback trigger | 18 | 9 | explicit version/custom/encoded prefix ×404/405/HTML |
| Models invalid endpoint/key/callback | 18 | 3 | endpoint secret, header injection, null callback wiring |
| Models root nonpath HTML status | 10 | 4 | invalid request/auth/rate/server categories |
| Models failed fallback status | 8 | 2 | 404 and 405 must not produce third request |
| Models redirect status × attempt | 10 | 4 | rewriting vs preserving method, first vs fallback |
| Models malformed/decode response × attempt | 18 | 8 | malformed document, empty HTTP, malformed UTF-8, malformed HTML-prefix UTF-8 |
| Models oversized response × attempt | 8 | 6 | known/chunked/decompressed bytes, first/fallback |
| Summary HTTP failure status | 7 | 5 | each fixed error category |
| Summary redirects | 3 | 2 | method rewrite/preservation |
| Summary malformed stream error with prior progress | 3 | 0 | parser owns malformed/EOF/exhausted; real streaming timeout owns client partial flush |
| Summary maximum preset inputs | 2 | 0 | larger custom input retains prompt-to-wire bound |
| Profile blank/nonstring marker × name × kind | 36 | 8 | blank string vs nonstring, denied/error, topic/reply |
| Profile invalid topic ID | 12 | 6 | missing, zero, canonical digits, injection, length, wrong type |
| Profile reply transport/parser failure cross-product | 6 | 0 | loader owns no partial output; source owns wire error/decode bounds |
| Profile unsupported charset/chunked bytes | 4 | 3 | whitespace spelling duplicate dropped |
| Loader empty recovery kind × attempt number | 4 | 0 | source integration retains second/final retry recovery for both kinds |
| SSE newline × byte fragment size | 9 | 3 | LF/CR/CRLF, strictest one-byte fragmentation |
| Location redirect/access status | 6 | 3 | redirect/auth/not-found |
| Location 503 body type | 6 | 2 | absent or unconditionally unreadable/oversized |
| Location non-200 successful-looking body | 4 | 2 | unexpected 2xx/server failure |
| Summary known-floor snapshot | 2 | 1 | zero floor; positive floor in clicked-row snapshot |

All other deleted whole-method loops are removed with their named method in the ledger. No cases were moved into new loops, renamed as helpers or disabled. Existing incremental tag/protocol split loops remain: split position can cross different recognizer states, so they are not interchangeable examples. Fixture loops generating 20 entries or 2,000 progress callbacks remain one scenario.

## Why the retained suites still exceed a half-size ambition

The final scope retains 249 of 328 methods. Remaining large suites each own concrete independent boundaries:

- **AuthorLocationRepository (25)**: cancelled physical slot; monotonic versus wall clock; delayed-wakeup cancellation; cold restore race; offline cache eligibility; per-key ordinary cooldown; scope rate limit minimum/recreation and longer delay; session rejection revisit; queued stop after invalidation, captured other account, same-UID changed credentials, persisted late rate limit; detached shared consumers; signalled and unobserved account change; foreground owner sharing; one retry at queue tail; pause/resume retry budget; random interval endpoints; reentrant handle publication; paused-owner late server stop. Removing these would sacrifice different outcomes (cross-account data/traffic, unwanted requests, lost pause or extra retry), not merely different inputs to one assertion.
- **NgaProfilePageSource (25)**: viewed-UID/session request; origin rejection; `__P` ownership; wrapped input identity; denied/error reply filtering; nonstring marker validation; visible entry limit; whole-page rejection precedence; bounded/deep/special-key decoding; display-zone dates; topic ID validation; complete two-read collection; unavailable/empty recovery; cancellation while queued; sharing across source instances; factory/enqueue exceptions; blocked read cancellation; real deadline; HTTP rejection; 503 one-request pacing; raw status/retry metadata; charset and byte bounds.
- **SummaryController (19)**: missing/read-failed configuration; prompt/config changes; independent floor input; immutable progress/copy gating; coalescing; successful full answer vs partial error vs reasoning-only error; reentrant terminal delivery; dismiss/retry/changed-target progress generations; replaced input and dismissed queued collection; cleared configuration; failure→retry; synchronous completed handle publication.
- **AiModelsClient (20)**: route fallback and exact eligibility, prefix detection and false-positive rejection, immutable empty result, validator wiring, status no-third-attempt, 503 retry suppression, redirect/key/cookie isolation, fallback cancellation/deadline, distinct peek vs body-read timeout, failed peek, decoder and decompression limits. These are mostly separate client/interceptor branches; matrix examples were aggressively reduced rather than deleting last wire protections.
- **AiSummaryClient (20)**: one-shot POST and nonstream connection shape, before-terminal progress, long stream versus bounded decompression, JSON compatibility, timeout flush and deterministic user cancellation, cookie/auth/redirect/no retry, fixed HTTP errors, nonstream byte/decode errors, isolated client policy, invalid input, maximum custom prompt. Provider billing/key leakage and partial output require distinct tests.
- **AiStreamParser (17)**: SSE newline framing; think-tag/protocol state splits; usage/choice selection; reasoning/tool-only answer rejection; finish versus EOF; pending ordinary text versus serialized envelope on failure; incomplete frame; malformed/server event; UTF-8 prefix retention; independent answer/reasoning budgets and overflow. These are distinct state-machine transitions, not an arbitrary target quota.

Smaller parser/store suites preserve supported v1 configuration upgrade, legacy author-location cooldown extension decoding, fail-closed crypto/corruption, secret diagnostics and session isolation. Further reduction is possible only by explicitly accepting additional coverage loss; this pass does not claim a 50% method reduction in these domains.
