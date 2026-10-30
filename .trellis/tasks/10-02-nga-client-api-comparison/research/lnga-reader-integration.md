# Research: LNGA signed HTML integration into the existing Android reader

- Query: Can a future LNGA-style signed HTML read retain this application's reader features and improve failures, and what is the smallest useful prototype?
- Scope: internal feasibility; upstream protocol extraction is a separate research topic.
- Date: 2026-10-02
- Current repository baseline: `fae59cbc477590e98c1c61a7526343eccc4cff3b` (read from `.git/HEAD` and its referenced ref, without a git operation). Anchors below refer to the inspected working-tree files; no code changes made by this researcher.

## Findings

### Conclusion and failure boundary

A third source is feasible because the app already separates query/page identity, transport, parsing, and row rendering. It is **not a URL or output-parameter swap**. Its request, parser, source dispatch, error policy, source lifecycle and cache format all need deliberate integration. Retaining the ordinary and App modes as separate paths is practical.

True HTTP 403 is rejected before response decoding (`ArticleByteClient.kt:25`, `ArticleFailure.kt:52`); it becomes ACCESS with the real status. App fallback is FORMAT-only (`ArticleFailure.kt:17`, `ArticleReaderSession.kt:103`). A new parser cannot repair that response. A different request could have a different server outcome, but static source inspection cannot establish that it will. Current JSON error handling also rejects every leading `<` as ACCESS (`ArticleFailure.kt:74`). A dedicated HTML parser must recognize the narrowly established thread document, separately reject access/challenge documents, and must not globally relax this guard for existing JSON readers.

“Native display” here means the app's existing thread controls/list and per-row rendering, **not** a completely WebView-free body. `ArticleListAdapter.java:549` loads locally formatted per-row HTML into `LocalWebView`; importing a whole upstream page or executing its scripts is a different architecture. Preserve source text, parsed facts and formatted HTML as separate values.

### Files and minimal seams

All abbreviated app paths below are under `nga_phone_base_3.0/src/main/java/sp/phone/`.

| File and anchor | Role and impact |
| --- | --- |
| `mvp/model/ArticleListModel.java:56` | Scoped fetch -> explicit parser -> attach request owner/generation -> main-thread callback. Currently ternary App vs ordinary; third source needs explicit dispatch, not accidental ordinary fallthrough. |
| `mvp/model/ArticleListModel.java:78` | Legacy ordinary URL remains `read.php` output 8. Default/legacy behavior is separate and should remain intact. |
| `mvp/model/thread/ArticleByteClient.kt:57` | Request builder currently implements only ordinary GET and unsigned cookie-based App POST. Future signed operation needs its own proven encoding/headers/credentials and charset decision. Existing bounded byte read, cancellation, no redirect/retry and origin validation are useful seams. |
| `mvp/model/thread/ArticleAccount.kt:8` | Immutable account wrapper exposes only Cookie internally; current source does not have an app-signing token request API. If HTML signing needs UID/CID or another token, add a narrow request-only bridge after upstream evidence, never decode identity from DTOs/cache. |
| `mvp/model/thread/ArticlePage.kt:40` | Two-value source enum, format identifiers, nullable paging metadata, source-independent query and actual PID/floor anchor. New source needs its own identity. |
| `mvp/model/thread/AppArticleParser.kt:20` | Existing example of source-specific identity validation, mapping into Java rows, injectable renderer/blacklist, completeness and unknown paging. Reuse contracts, not its unrelated JSON schema. |
| `mvp/model/convert/ArticleConvertFactory.java:115` | `renderRow` is source-neutral. It builds core `HtmlData` after source projection, attachments, comments, blacklist and author facts. Preserve ordinary-only WP preprocessing upstream of this seam. |
| `mvp/model/thread/ArticleReaderSession.kt:71` | Accepts source/layout changes only foreground, advances generation, invalidates retained layout, hands response to recreated page. Useful common ownership mechanism. |
| `mvp/presenter/ArticleListPresenter.java:191` | Executes current scoped chain; fallback and alignment checks explicitly name APP_API at 204/215. Merely adding enum/request/parser does not make switching correct. |
| `mvp/model/thread/ArticleCache.kt:34` | Layout-source lookup currently defaults nonordinary to App; regex at 41 only permits current two formats. Replay dispatch at 137 and display names at 88 also require explicit third-source handling if persistence is enabled. |
| `ui/adapter/ArticleListAdapter.java:214` | Quotes consume row source, not formatted HTML. Body binding at 549 reuses local WebViews. |
| `ui/fragment/ArticleListFragment.java:263` | Generation changes clear delivered/displayed page and author-location consumers. Existing pager guards at 284 exclude lookup/author/cache context. |

The shared ordinary decoder (`lib_core/.../ReadThreadWireDecoder.kt`) accepts a parsed ordinary `data` object. Do not teach it HTML, networking, signing or fallback. An HTML source-specific extraction/projection boundary belongs alongside the current app parsers initially; a later reusable decoder requires its own proven typed contract.

### Feature parity and explicit unknowns

| Capability | Contract for a future HTML adapter |
| --- | --- |
| Pagination / variable page sizes | Keep requested vs effective page, nullable reported size and total distinct. Do not infer size from row count or impose 20. A short page/floor gap is not alone malformed. Unknown totals mean a window, not a one-page full thread. `ArticlePage.kt:44`. |
| PID-only / reply lookup | Require an actual matching PID; derive resolved tid only from validated data. Retain PID and author restriction throughout the request. Do not guess tid from pid. `ArticleQuery` distinguishes FULL/AUTHOR/LOOKUP at 17. |
| Author filter / show all | Validate returned known authors against filter. `showAll` opens a fresh full query page 1 and clears lookup/filter/cache disposition (`ArticlePage.kt:140`). Never infer global position from lookup-local floor 0. |
| Position during source switch | Same server-page number need not mean same posts. Preserve PID/floor anchor, match actual row, use at most one proven alignment candidate, report loss of position. Current policy/notice/alignment paths are App-specific and must be generalized only deliberately (`ArticlePage.kt:157`, presenter 144/204). |
| Reply / quote / editing | Existing quote paths consume `row.content` (`ArticleListAdapter.java:216`; presenter 392/446). HTML-extracted display markup is not automatically editable NGA source. Demonstrate exact source reconstruction or keep source-dependent actions unavailable. Preserve tags/escaping, attachments and literal dollar/backslash sequences; do not feed rendered full-page HTML to composer. |
| Authors / badges / menus | Populate explicit `ArticleRowPresentation`: known floor, meaningful UID, score availability, row kind, source availability and OP identity. Missing fields stay unknown, not fabricated zeros or anonymous OP badges. |
| Comments / hot replies | Ordinary wire includes recursive comments and raw `17` hot-reply behavior. App parser does not project dedicated attachment/hot/parent extensions. HTML extraction must prove comment parent, own PID, ordering and source; a flattened list is not parity. Unknown metadata may degrade optional controls, but missing mandatory body data must remain visible/incomplete. |
| Media | Prepare attachment list and complete response prefix before `renderRow`; retain current manual-host preference, inline tags, images/video and emoticons. Upstream HTML rendering and platform UI widgets are not portable replacements for core rendering. |
| Blacklist | Feed existing blacklist/author projection before rendering; an upstream hidden/display-only row cannot silently become complete editable data. |
| Cache | Initially disable HTML persistence in any prototype. Production needs explicit format/layout, source replay and owner isolation; cannot label HTML `read_php` or save to legacy `.json` slots. Completeness, selected page/query/generation and description validation remain mandatory. |
| Prefetch | Leave new source foreground-only. `ArticleReaderState.canPrefetch` (session line 16) and presenter 296 currently allow only ordinary full queries. No background fallback, account switching, dialogs or extra scan requests. |
| Author location | An accepted page can use the existing source-neutral enrichment through `AuthorLocationService.Page.deliver`; profile UID validity still matters. Saved/retained replay is cache-only; Activity ownership, cancellation, random 200–500 ms terminal pacing and HTTP503 stop remain unchanged. Keep enrichment out of request reliability measurements to avoid hidden traffic confounding results. |

### Recommended research prototype boundary (not implemented)

1. Freeze the upstream request/parser revision and inventory the exact required input/output fields. Record inferred behavior separately from observed fixtures. Check the upstream licensing before any code transfer.
2. Start with an **offline source adapter**, not a third production fallback: bounded HTML input + immutable query -> typed extraction -> existing `ThreadData`/rows/paging -> injectable existing renderer. Use synthetic or carefully redacted permitted fixtures; preserve a raw/source/render separation. Do not execute fetched scripts to recover data.
3. Build a fake-server request harness for the exact signing/encoding/headers and source-specific charset. Return real HTTP403, HTTP200 valid thread HTML, HTTP200 challenge HTML, malformed/truncated bodies and mismatched identities. Demonstrate distinct outcomes and zero hidden retry/redirect/account changes.
4. Minimum proof is a full-thread first and later page, meaningful PID/TID/floor identity, text/media source, and variable/unknown paging. Treat PID-only/filter/hot/comments/editable-source preservation as explicit gates before calling it a replacement reader. Unsupported operations must remain explicit; never issue an unfiltered/full request and pretend it answered a lookup.
5. Only after those gates consider a manually selected experimental reader source, with ordinary/App and browser preserved. Keep prefetch/cache disabled for this experiment until their separate contracts are implemented; source changes still retire generations. No default source or automatic 403 fallback change is part of this research.
6. Live comparison is a later, separately authorized bounded study. The project access rules require named operation/account, limits, redaction and stop conditions. The current research makes no NGA requests. Confirmed access/challenge/auth/rate-limit responses stop rather than trigger automated alternative credentials or bypass attempts.

### Success criteria and strength of evidence

- **Offline integration success:** same fixture produces correct text, PID/TID, row order, nullable metadata and source-based composer input; malformed or challenge input cannot become a successful empty page. No full document scripts are executed; no ordinary/App fixture behavior changes.
- **Lifecycle success:** source/page-size/account changes reject old responses and READY entries; foreground-only adoption; refresh actually fetches; no duplicate background work; matching anchors or one honest unavailable-position notice.
- **Failure measurement:** record transport status, recognized document class, parser result, completeness and rendered identity separately. Never count “HTTP200” or “some text displayed” as successful read.
- **Stability evidence:** compare allowed equivalent observations under the same account/network/query/time context, with source, request count and failure classes separated. A readable HTML page while ordinary JSON parsing fails supports a format-resilience claim. A different HTTP result needs its own evidence and does not establish universal 403 prevention. Challenge/session/permission failures cannot be claimed fixed by a parser.
- **Regression gates for eventual implementation:** extend existing `ArticleByteClientTest`, `ArticleErrorsTest`, `ArticleReaderSessionTest`, parser suites, `ArticleBodyViewsTest`, page request-state/cache tests where affected. Exercise genuine behavior using fixture/fake transport, not source-text assertions. Follow backend Android combined Debug/lint gate for actual code changes; this document-only investigation did not run it or claim device verification.

## Related specs

- `.trellis/workflow.md` (research during planning; no task activation by this worker).
- `.trellis/spec/backend/index.md` and `.trellis/spec/frontend/index.md`.
- `.trellis/spec/backend/thread-detail-compat-contract.md`.
- `.trellis/spec/backend/ordinary-read-wire-contract.md`.
- `.trellis/spec/backend/thread-page-prefetch-contract.md`.
- `.trellis/spec/backend/thread-page-cache-contract.md`.
- `.trellis/spec/backend/author-profile-location-contract.md`.
- `.trellis/spec/backend/nga-platform-access-rules.md` (error classes, bounded decoding, authorized validation).
- `.trellis/spec/frontend/component-guidelines.md` (article body, navigation, quote source and retained resources).

## External references

Upstream LNGA request/parser findings, revisions and issue evidence belong to the companion upstream research. This report does not independently claim its HTML schema, token rules, page sizes or edit-source fidelity. The local App adapter attribution is Justwen `2becba2acc3f6c85340424cd09bb03fa7d759db0`, GPL-3.0 (`AppArticleParser.kt:12`, `ArticleByteClient.kt:66`); that route is not equivalent to a signed LNGA HTML route.

## Caveats / Not Found

- No live NGA probes, device tests, app code edits, task activation or git operations performed.
- No actual failing user thread/account/network sample was provided; the user's reported 403 cannot be identified as transport 403 vs legacy format failure from recollection alone.
- Upstream HTML success does not prove availability under this client's account/headers/session. Existing browser success is a separate request/context and cannot diagnose the failed request by itself.
- Concrete upstream parsing completeness is deliberately delegated to the upstream parser research, not inferred from this app's existing schema.
