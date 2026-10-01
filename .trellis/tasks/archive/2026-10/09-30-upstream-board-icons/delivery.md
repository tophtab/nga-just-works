# U1 implementation delivery

Date: 2026-10-01. Worktree: `/home/toph/nga-just-works-upstream-adoption`.
Implementation baseline: `557f7bea`; branch: `feat/upstream-adoption`.
Status: implementation and independent review complete; all required Debug/unit/lint gates pass after the reviewer fix. See `check.md`.

## Change boundary

The behavior gap was category metadata being ignored unless boards were added, with no observable icon/child-list change. The owning path is BOARD.CATEGORIES → repository → synchronized model → entity/Compose grid. Product edits remain in `compose/board/`, its JVM tests, the new `PreferenceKey.BOARD_ICON_URL`, and corrected Coil comments in `ApiConstants`.

No SDK/JSON dependency, reader/editor, manifest, account, attachment-host, local cache-version, timestamp-key or signed-build changes. Existing board/favorite storage and home-order overlay remain authoritative.

## Implemented

- `BoardIconUrlResolver`: strict known-directory prefix normalization, default HTTPS authority from ApiConstants, collection priority, signed fid, structural-node empty URL.
- `ForumBoardRepository`/`ForumsListBean`: raw object + array-valued result validation, including rejection of null/non-object category/group/forum members before any model mutation; malformed/non-string icon metadata isolated before Fastjson DTO coercion; shared safe decoder for remote and old raw cache; cancellation propagates; no new raw network/parser logging.
- `BoardEntity`: `iconUrl` backed by Compose mutable state, excluded from JSON getter and setter; actual serialization tests prove exclusion.
- `BoardIconState`/`ForumBoardModel`: preference → cache → default choice, delayed-cache retirement after any valid remote acceptance, recursive actual-object hydration, current-index insertion, bookmark restore hydration and frozen canonical tree save.
- `BoardRefreshGate`/`ForumBoardViewModel`: Main-owned one-in-flight/daily attempt gate, IO-only fetch/parse, Main apply; success/failure/cancellation retain the daily throttle and cancellation never applies results.
- `ForumBoardView`: reads entity icon state; nested render-list snapshots update only when structural revision changes. Existing local drawables, Coil placeholder/error, 48dp size, Pager identity and favorite/home gesture wiring remain.
- `HomeBoardOrderContractTest`: removed obsolete unsafe shallow-save source assertion; new behavioral test covers the full root hierarchy and delayed-save isolation instead.

## Acceptance evidence

| Acceptance | Executed evidence |
| --- | --- |
| A1/A2 | `BoardIconUrlResolverTest` verifies known legacy normalization, alternative HTTPS origin, ordinary/negative/collection IDs, invalid types/protocols/directories/origins; envelope tests isolate bad metadata. |
| A3/A4 | `ForumBoardIconRefreshTest` refreshes separate equal tree/favorite objects and duplicates with no structural change or list-save callback; actual `SnapshotStateObserver` receives change once and no equal-prefix notification. |
| A5 | Production merge helper preserves group/order, filters existing/favorite-only/duplicate IDs and invalid/missing-parent entries; render snapshot exposes added child while prior snapshot remains unchanged. |
| A6 | Real Fastjson reads old string-number JSON and ignores injected iconUrl; round-trip excludes derived fields; raw-cache file tests cover absent/corrupt/valid data and preference type/read exceptions. |
| A7 | Production restore seam delegates existing candidate-current rollback and hydrates old snapshot references after reload/new prefix; late failure cannot replace newer membership. Existing 10 bookmark persistence tests remain green. |
| A8 | Prefix-only path records zero tree writes; insertion captures ordered full roots before later live mutation. Existing six home-order behavior and six home contract tests remain green. |
| A9 | Production gate with controllable clock/request suspension exercises not-due, due, duplicate in-flight, success, failure, cancellation and next normal attempt; delayed raw cache cannot override remote acceptance. |
| A10 | Source wiring assertions + diff review retain local priority, size, placeholders, click, stable bookmark keys and accessibility callbacks. Existing Pager/home transaction contract tests pass. No runtime visual claim. |

## Validation

See `check-evidence.md` for final commands, XML counts and limitations. No device, live NGA, CDN probe, release/signing or publication was run. Offline tests do not establish device appearance, CDN reachability, or identical-URL CDN content invalidation.
