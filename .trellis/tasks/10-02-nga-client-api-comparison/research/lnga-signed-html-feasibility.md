# LNGA signed HTML-to-native reading: feasibility decision

Date: 2026-10-02. LNGA baseline:
`df6cfcc2e4cfc2efa95269aac398707e9d4d3e81`; current Android baseline:
`fae59cbc477590e98c1c61a7526343eccc4cff3b`.

Latest live comparison: [four-route test](lnga-live-47659652-four-routes.md)
found both signed and unsigned App JSON successful while default parsing failed.
This weakens the need for a new HTML parser for this case; first investigate
existing compatibility integration. The original feasibility analysis follows.

## Decision

Technically feasible and worth an offline prototype. Existing selected-account
Passport uid/cid can supply the source-observed signing inputs without a new
login/token-exchange flow. Existing Android reader controls and row renderer can
be retained. The necessary new component is a distinct signed-HTML reader source,
with an operation-specific request/envelope codec and faithful data extraction.

It is not ready for a wholesale production replacement. The upstream parser's
defaults, sparse row representation, source fidelity and sample coverage do not
meet all current reader contracts. No live reliability gain or HTTP403 fix was
established. A parser can address valid HTML versus damaged JSON; a differently
authenticated request might change server behavior, but those are separate claims.

## How it works

```text
Selected Passport uid/cid snapshot
  -> signed UTF-8 POST https://ngabbs.com/read.php (__output=17)
  -> JSON Article envelope containing HTML
  -> validate transport/envelope and recognize a thread document
  -> extract literal post/user/attachment data and body markers
  -> typed rows, thread identity and page metadata
  -> existing reader projection and local body renderer
```

LNGA's production code does not evaluate embedded JavaScript for this extraction.
It scans calls such as `commonui.postArg.proc(...)` and user/attachment literals,
plus body markers. No remote page scripts or WebView navigation are required to
perform that extraction. Our current body renderer uses local per-row WebViews;
retaining it preserves the app's reader rather than displaying the full upstream
page. This design does not perform browser challenges or gain browser session state.

## Transport contract and reuse

- Source request is signed POST to a fixed HTTPS host. Business fields include
  tid/pid/authorid/page and `__localres=1`; public fields include app identity,
  uid/token/time/signature, output17 and UTF-8 inchst. The reader's extra signing
  parameter string is empty. Do not substitute the current unsigned Cookie App
  post/list request or simply change an output parameter.
- LNGA assigns `access_uid=uid`, `access_token=cid` and sends the same Passport
  Cookie. Its local session token is only a registry key. Existing Android
  credentials are structurally reusable; acceptance of a particular cid remains
  a server-side question. Guest reading is not established by the reachable path.
- Retain the current Android explicit account snapshot, bounded bytes, strict
  decoding, cancellation, origin validation and HTTP-status classification.
  Add operation-specific UTF-8 decoding instead of inheriting the ordinary
  missing-charset GBK default. Do not import the LNGA client's retries or logging.
- Normal envelope code521/read can contain a successful article; code521/std_error
  can contain a business error. Neither is HTTP521. Preserve actual HTTP403 as
  ACCESS before decoding; do not hide status to make reported failures disappear.
- LNGA has duplicate differently cased identity header keys in a JavaScript
  object. HTTP names are case-insensitive; actual SDK serialization is unverified.
  An implementation must choose and test one intentional header contract.

Full fields, source anchors and limits: [transport study](lnga-signed-html-transport.md).

## Parser findings that determine feasibility

| Finding | Why a direct port would be insufficient | Required adapter behavior |
| --- | --- | --- |
| Rows keyed by absolute floor; declared count equals maximum floor plus one | A second page with floor20 can declare21 without slots0–19; current ordinary decoder requires indexed declared slots | Use a separate HTML extraction type; retain actual row order/count and original floor independently |
| Missing body marker returns empty text | Missing/truncated content can masquerade as a valid empty reply | Track presence and completeness; unavailable source stays visibly incomplete and cannot enter edit/cache |
| First closing p/span terminates body extraction | Nested tags or alternate quoting can truncate or miss content | Test balanced/nested containers and exact source preservation; recognize unsupported structure rather than silently truncate |
| Several topic flags default to zero; missing OP can fall back to first page-row author | Unknown lock/type/OP facts become fabricated display/action facts | Preserve unknown metadata; establish OP through actual topic identity |
| Attachment/comment/hot-reply extraction depends on particular markers and same-page rows | Off-page originals, escaped quotes and changed literal styles can lose information | Explicit fixtures for independent references; no guessed parent relationships or silently complete caches |
| Native BBCode renderer is rich, but extraction is partial | Renderer capability does not prove the original text reached it unchanged | Verify text, structure, links/media and source-dependent actions separately |

The upstream HTML/JSON comparison has five App fixture pairs from two thread IDs.
Its visible-text acceptance threshold is 90%, with selected strict metadata checks.
This is useful regression material, not proof of complete rendering or editable
source parity. An older floor-shift claim in fixture notes was explicitly retracted
after PID comparison; only the demonstrated sparse-row mismatch is retained here.

Full chain, fixture inventory and limitations: [parser study](lnga-signed-html-parser.md).

## Smallest useful future experiment

This is a proposed sequence, not implementation approval or a change to defaults.

1. **Offline request and source adapter.** Fixed-clock synthetic signing inputs,
   a fake transport, bounded envelope/HTML inputs, and typed extracted rows.
   Reuse the existing renderer through an injectable boundary. Do not edit shared
   ordinary decoding to accept invented LNGA JSON shapes.
2. **Minimum readable-page gate.** First and later full-thread pages, exact
   TID/PID/row ordering, sparse floors, known versus unknown page metadata, text,
   inline media and incomplete-source handling. Invalid/error HTML must fail
   distinctly instead of yielding successful empty data.
3. **Parity gate for richer navigation/actions.** PID-only and author-filtered
   pages, comment/hot reply identity, quote/edit source, attachments and polls.
   Reject unsupported query kinds explicitly until their tests pass; never drop
   an author/PID restriction or substitute a full-thread response.
4. **Optional experimental reader integration.** Give the source an explicit
   identity; preserve generation/account/query ownership and ordinary/App modes.
   Initially foreground-only, without persistent HTML cache or prefetch. Existing
   source dispatch uses two-way branches, so request, parse, lifecycle and cache
   fallthroughs all need auditing even if persistence stays disabled.
5. **Later bounded live comparison.** Only after separately scoped authorization:
   compare equivalent account/thread/page/network observations. Count a read as
   successful only when identity, completeness and rendering pass. Separate
   HTTP status, document type, parse failure and content loss; exclude hidden
   prefetch/location requests from the measurement. A handful of successes does
   not demonstrate a durable 403 fix.

Exact current files and feature contracts: [integration study](lnga-reader-integration.md).
Existing automatically triggered App fallback remains FORMAT-only. Researching a
new explicitly chosen source does not imply automatic source rotation on HTTP403.

## Acceptance matrix for an offline prototype

| Input | Observable result |
| --- | --- |
| Valid code521/read first/later page | Native rows preserve actual identity/order/content; no remote scripts execute |
| HTTP403/401/429/redirect | Status retained; zero automatic extra requests |
| HTTP200 code521/std_error or unrelated HTML | Business/access/protocol outcome as evidenced, never a successful empty thread |
| Missing/nested/truncated body markers | Incomplete or rejected source, no fabricated editable text |
| Sparse floors and unknown page size | Actual floors retained; no array-index floor or guessed20 substitution |
| PID-only/author filter unsupported or mismatch | Explicit unsupported/content result; no wrong-thread success |
| Account/source change during read | Old completion cannot alter rows, title, paging or loading state |
| Oversized body or cancelled request | Bound enforced and work cancelled without logging raw content |
| Media/comment/hot-reply/quote fixture | Structure and source/link targets preserved, or explicit capability loss |

## Scope, provenance and validation

The root project and LNGA declare GPL version2 in LICENSE; any later translated
or copied source needs pinned provenance and notices in the existing source
ledger. No source was imported in this research. ArkTS taskpool, session stores
and UI widgets are platform-specific and need not be carried into Android.

Validation performed: pinned source tracing, checked-in fixture/test inspection,
direct verification of sparse row/count and body-marker behavior, local document
links/manifest checks. No upstream tests, builds, device tests or NGA requests
were executed. Thus the result is a concrete feasibility assessment and prototype
boundary, not tested Android implementation or measured reliability evidence.
