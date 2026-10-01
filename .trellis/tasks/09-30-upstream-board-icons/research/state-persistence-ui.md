# Research: U1 board state, persistence, and UI refresh

- Query: How can U1 add derived `BoardEntity.iconUrl` and handle prefix-only refreshes without losing hierarchy, favorite membership/order, home order, or active drag state?
- Scope: internal; isolated worktree `/home/toph/nga-just-works-upstream-adoption`, existing U1 child only.
- Date: 2026-09-30
- Status: planning evidence and implementation recommendation; no product implementation or functional test run.

## Findings

### Files found

All paths below are relative to the isolated worktree. The `board/` abbreviation in later citations means `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/`; `board-tests/` means the corresponding `src/test/java/gov/anzong/androidnga/activity/compose/board/` directory.

| File | Responsibility |
| --- | --- |
| `board/data/BoardEntity.kt` | Mutable tree node, `id`-only equality, Fastjson metadata. Physical path and package differ: package is `gov.anzong.androidnga.core.board.data`. |
| `board/ForumBoardModel.kt` | Local/default hierarchy, display order overlay, shared lookup map, favorite mutations and persistence guards, remote additions. |
| `board/ForumBoardRepository.kt` | Local and raw remote cache files, staged global favorite file, Fastjson 1 parsing. |
| `board/ForumBoardViewModel.kt` | Singleton model, LiveData publication, asynchronous favorite/home-order persistence, daily remote refresh. |
| `board/ForumBoardView.kt` | Coil-backed board icons, local drawable priority, favorite drag, home tabs, grid contents. |
| `board/HomeBoardOrder.kt` | Pure ID-order reconciliation and separate app-wide preference storage. |
| `nga_phone_base_3.0/src/main/assets/board_list.json` | Canonical nested root/group/node data, including repeated board occurrences and permissive JSON syntax. |
| `board-tests/ForumBoardBookmarkPersistenceTest.kt` | Executable stable-key/order, file recovery, deduplication, and JSON round-trip tests. |
| `board-tests/HomeBoardOrderTest.kt` | Executable home-order resolver tests. |
| `board-tests/HomeBoardOrderContractTest.kt` | Real bundled asset decode plus source-level model/UI contract checks. |
| `lib_base_ui_compose/build.gradle` | Coil 2.5.0 and runtime-livedata dependencies exported to the application. |

### Object identity and traversal

1. `BoardEntity.equals/hashCode` consider only `id`, not children, name, or future icon metadata (`board/data/BoardEntity.kt:46`). Do not change equality to include icon URLs; stable identity and visual content are different contracts.
2. `localBoardList` owns canonical root order; `boardList` is the home display overlay. `boardList` contains the **same root objects** as `localBoardList`, with the separate bookmark root prepended (`board/ForumBoardModel.kt:81`). Reordering tabs replaces only the overlay positions (`board/ForumBoardModel.kt:443`). Persist canonical `localBoardList`, never `boardList` or `boardMap.values`.
3. Favorites are decoded independently from `board_bookmark.json` (`board/ForumBoardRepository.kt:69`), and new favorites are newly allocated nodes (`board/ForumBoardModel.kt:276`). A favorite matching a tree node normally shares its logical `fid + stid`, **not its object reference**. `bookmarkBoard` is also the object at `boardList[0]`.
4. The lookup map indexes both bookmark and tree nodes, in that order, and overwrites matching IDs (`board/ForumBoardModel.kt:90`, `board/ForumBoardModel.kt:96`). It retains only one object per generated ID; it is unsuitable for refreshing every rendered occurrence.
5. The bundled tree itself repeats IDs. Examples: `fid=453` appears twice within the phone/web-game group (`nga_phone_base_3.0/src/main/assets/board_list.json:1043`, `:1205`); `fid=-7678526` appears across three sections (`:99`, `:931`, `:1695`); `fid=310` appears in two Warcraft groups (`:1275`, `:1404`). Therefore recurse over every node in the canonical tree and bookmark tree. Do not use `distinct()`, an ordinary `Set<BoardEntity>`, or `boardMap.values` for this refresh. Repeated visits to the same reference are harmless; if a visited set is needed, use reference identity.
6. The favorite grid intentionally deduplicates and keys by `fid + stid` (`board/ForumBoardRepository.kt:150`, `board/ForumBoardView.kt:283`). Ordinary grids contain repeated IDs and currently use positional lazy items (`board/ForumBoardView.kt:427`). Do not blindly add favorite-style keys there; an occurrence path would be needed if that identity scheme were ever changed.

### Existing persistence contracts

- `board_list.json` stores the canonical nested local roots; `board_list_remote.json` stores raw response text; `board_bookmark.json` stores the app-wide ordered favorites. Home order is a separate `KEY_HOME_BOARD_ORDER` preference containing root IDs only (`board/ForumBoardRepository.kt:18`, `:31`, `:246`, `:280`; `board/HomeBoardOrder.kt:73`).
- Local cache version is `6`. A version mismatch deletes the local board cache and clears the refresh timestamp (`board/ForumBoardRepository.kt:35`, `:56`). Adding a nullable **derived** icon property does not justify that destructive migration. Existing `HomeBoardOrderContractTest` deliberately pins version 6 (`board-tests/HomeBoardOrderContractTest.kt:35`).
- Favorite writes are staged and renamed, with backup recovery; `[]` is an authoritative intentional empty list (`board/ForumBoardRepository.kt:85`, `:158`, `:203`). Preserve these routines and first-occurrence stable-key deduplication.
- Legacy favorite preference migration creates new nodes and preserves `name`, `fid`, `stid`, and `head`; its file-success/legacy-clear sequencing must remain (`board/ForumBoardModel.kt:109`). Icon hydration must also cover this path.
- Current persisted nodes have no icon field. A transient nullable `iconUrl` can load those records unchanged, then be derived from the accepted prefix. Ignore a supplied `iconUrl` during decode as well as during encode: this avoids treating any historical/imported derived URL as authoritative.
- The parser here is Fastjson **1.1.71.android**, not the Fastjson 2 dependency also declared elsewhere (`build.gradle:10`; `board/ForumBoardRepository.kt:4`). The real asset includes numeric strings and a trailing comma near line 1381. Existing `board-tests/HomeBoardOrderContractTest.kt:18` already decodes that asset using the actual parser; extend/reuse this test instead of duplicating or converting the asset to strict JSON as part of U1.

### Compose refresh constraints and recommended minimal state

The current icon path is Coil, not Glide: `rememberAsyncImagePainter` receives the URL (`board/ForumBoardView.kt:177`; dependency `lib_base_ui_compose/build.gradle:46`). A bundled drawable takes priority for non-collection boards (`board/ForumBoardView.kt:168`, `:207`), and group headers always use the default local icon (`:130`). Keep those choices, dimensions, placeholder, and error drawable.

`boardLiveData` and `bookmarkBoardsLiveData` publish shallow lists (`board/ForumBoardViewModel.kt:176`; `board/ForumBoardModel.kt:168`, `:214`). Their Compose consumers use `observeAsState` (`board/ForumBoardView.kt:69`, `:224`). Assigning a new list with the same `id`-equal elements is not a reliable icon-update signal; mutating a plain node property is not snapshot-observable either. LiveData delivery alone does not establish Compose content inequality.

Recommended design:

1. Add nullable `iconUrl` backed by `mutableStateOf` to `BoardEntity`, keeping existing `equals/hashCode` unchanged. Exclude the property explicitly with getter **and** setter Fastjson annotations (`serialize=false, deserialize=false`). Do not use `@JvmField` on a delegated property. Test the actual encoder to ensure neither `iconUrl` nor delegated state appears in JSON.
2. Read that state directly in the remote-image branch of the grid cell; use the existing fixed URL as fallback until hydrated. Changing the URL then invalidates the reading composition and changes Coil's request model without recreating a Pager or grid.
3. Keep one accepted normalized prefix in the model. Hydrate all nodes after initialization/legacy migration, then update all tree and favorite occurrences **in place** when a valid changed prefix is applied. Hydrate newly added favorites, newly decoded favorites, restored snapshots, and newly merged remote nodes with the current prefix. Invalid/missing input does not clear an accepted prefix or usable derived URL.
4. Persist only the accepted prefix for an icon-only change. Do not write either board list or favorite file for this change. This decouples icon updates from favorite order transactions and preserves old file formats. Prefix parsing, fallback order, and refresh scheduling are owned by the main session's companion research/design.

Tradeoff: this adds a narrow Compose-runtime observable property to an existing app-owned mutable display/persistence bean. A larger immutable presentation-model conversion belongs to U3. A separate revision for every icon is possible but needs threading a changed value all the way to lazy cell captures; per-node state is less invasive and directly testable.

### New children need a different notification

Per-node icon state solves prefix-only changes; it cannot make a non-observable `MutableList.children` insertion visible. Currently the remote method mutates children without publishing UI state (`board/ForumBoardModel.kt:412`; `board/ForumBoardViewModel.kt:204`). The root IDs remain unchanged, so merely republishing `boardLiveData` has the same equality problem.

Use a scalar board-content revision for **accepted structural insertions**, observed inside `ForumBoardContent`. On `(root.id, revision)` build a remembered rendering snapshot containing the root children and copied children lists for each group. The current UI renders precisely those two levels (`board/ForumBoardView.kt:418`). Feed those snapshots to the existing lazy grid. This makes the changed nested membership explicit even if compiler/lazy-lambda memoization reuses captures of the original mutable nodes.

Keep the same Pager, lazy-grid state, and drag containers; do not wrap them in `key(revision)` or rebuild the ViewModel. Prefix-only updates do not increment this structural revision or replace favorite/root objects. Converting every `children` list to `SnapshotStateList` would be broader and would require reworking every JSON decode/initialization path.

### Drag/save races and thread boundaries

- Favorite snapshots are shallow lists (`board/ForumBoardModel.kt:168`); begin/move/end use those same node objects (`board/ForumBoardViewModel.kt:79`). In-place icon updates therefore survive ordinary cancel/rollback: restoring order restores references with their current icons.
- External favorite reload can replace those objects while a drag still holds older references (`board/ForumBoardModel.kt:205`). Normalize icons against the current prefix in `restoreBookmarkOrder` and successful conditional restore too; otherwise restoring an older snapshot can reintroduce a stale derived icon. This does not authorize changing existing membership rollback semantics.
- Candidate saves/rollbacks compare only `fid + stid` order (`board/ForumBoardModel.kt:25`, `:193`, `:366`). A prefix-only change intentionally does not supersede an order transaction. With transient icons, even a captured older candidate cannot write an old prefix into favorite JSON. Preserve existing rejection of a write/rollback after a newer membership/order mutation (`board/ForumBoardViewModel.kt:96`).
- Home snapshots contain only root IDs (`board/ForumBoardModel.kt:219`). Keeping the canonical root objects and home-order preference separate lets icon refresh coexist with home drag without resetting its selected board or order.
- Fetch and decode on IO; apply accepted prefix and structural additions against **current** model state on Main, then notify observers. Do not read `boardMap` on the network worker to decide additions and later apply an unvalidated stale list. Existing `loadIncrementalBoardList` reads the map on IO and `mergeBoardList` is unsynchronized (`board/ForumBoardModel.kt:385`, `:412`). A result DTO plus a Main-thread apply step is the narrow correction when touching that path.
- Local saving currently enqueues a lambda that later evaluates `localBoardList.toList()` (`board/ForumBoardModel.kt:421`). That is a shallow, late snapshot; if implementing Main-thread merges, capture the canonical hierarchy structurally before asynchronous serialization/write. Do not serialize `boardMap.values`, which loses roots and repeated occurrences. Use the existing serialized file-write executor; prefix-only updates bypass it.
- `board-tests/HomeBoardOrderContractTest.kt:55` pins the literal old `writeLocalBoardList(..., localBoardList.toList())` source. If replacing it with a safe frozen snapshot, replace that brittle assertion with behavior coverage of canonical root order and nested children, rather than preserving unsafe syntax just to pass the source check.

### Test seams and useful behavior coverage

`ForumBoardModel()` immediately accesses Android global Context/Preferences (`board/ForumBoardModel.kt:81`); the singleton ViewModel also starts this in initialization (`board/ForumBoardViewModel.kt:26`). Current local tests therefore exercise pure resolver/repository seams. No Robolectric, Mockito, or coroutine-test dependency is declared in the app (`nga_phone_base_3.0/build.gradle:214`). Prefer focused production helpers accepting the current tree/bookmarks/prefix and returning apply results; avoid a broad dependency-injection refactor just for U1.

| Test | Required observation |
| --- | --- |
| Old local and favorite JSON without `iconUrl` | Decode succeeds with same fields/order; hydration yields expected current URLs. |
| JSON containing an old `iconUrl` plus encode round-trip | Old URL is ignored; `iconUrl` and snapshot state are absent from encoded JSON; name/head/IDs/hierarchy stay intact. |
| Same-ID independent nodes in two groups plus favorite | Every object gets the new URL; all node references, root/group order, and bookmark keys stay unchanged. Include a same-group duplicate. |
| Prefix-only apply | No insertions; existing ordinary/collection nodes change; invalid or unchanged prefix is a no-op; no list/favorite file save callback. |
| Snapshot observation | An actual Compose snapshot observer reads `iconUrl`; changing it notifies that observer even while the containing list remains equality-equal. This is stronger than asserting a getter string or source import. |
| Favorite drag then prefix update then cancel | Original order restored with the **new** icons; current candidate save retains existing persistence semantics. |
| Reload/recreate favorite then restore old snapshot | Restored nodes are rehydrated from current accepted prefix. |
| Save failure after newer membership/order mutation | Existing supersession/rollback behavior still passes; icon refresh does not create a competing favorite write. |
| Child insertion with same root ID | Revision changes; rendering snapshot includes the new nested child; prefix-only update does not cause structural revision. |
| Home custom order + canonical save/reload | Selected root identity and overlay survive; saved hierarchy keeps default root order and every group/duplicate occurrence. |
| UI wiring review | Local drawable priority, Coil URL model, placeholder/error, favorite stable keys, active drag/Pager gates, and no revision-key remount. |

Reuse `ForumBoardBookmarkPersistenceTest` for actual file/JSON checks and recovery (`board-tests/ForumBoardBookmarkPersistenceTest.kt:66`, `:108`, `:132`), `HomeBoardOrderTest` for overlay rules, and `HomeBoardOrderContractTest` for real asset parsing. The existing failed-write test exercises the pure rollback rule, not an actual concurrent disk failure (`board-tests/ForumBoardBookmarkPersistenceTest.kt:51`); do not describe it as end-to-end race coverage.

Suggested focused iteration checks (not executed during research): app board-package JVM tests and Kotlin compile. The final gate is broader under `.trellis/spec/backend/android-quality-guidelines.md:149`: Debug app assembly plus repository-wide `testDebugUnitTest` and `lintDebug`, inspecting reports from all Android modules for zero failures and zero Error/Fatal issues. `abortOnError false` is configured (`nga_phone_base_3.0/build.gradle:112`), so exit status alone is insufficient. See the implementation plan for the combined commands; do not substitute app-only checks for the final gate. No device or live NGA request is needed for the proposed behavior tests.

### External references and versions

- No external network source was consulted in this research assignment. Upstream commit/URL policy verification belongs to the parent task source index and the main session's companion research.
- Local dependencies: Compose Material/runtime-livedata 1.6.8 and UI 1.7.0 (`build.gradle:3`; `lib_base_ui_compose/build.gradle:48`), Coil Compose 2.5.0 (`lib_base_ui_compose/build.gradle:46`), Fastjson 1.1.71.android (`build.gradle:10`).
- Recommended snapshot-observer test and getter/setter annotation behavior still require execution against these resolved dependencies in implementation; they are not claimed as already verified.

### Related specs

- `.trellis/spec/frontend/state-management.md`: app-wide favorite identity/order, candidate-current save/rollback guards, recovery, Pager arbitration.
- `.trellis/spec/frontend/component-guidelines.md:289`: favorite drag and stable keys; `:300`: fixed bookmark tab, root overlay, selected stable key, terminal cleanup.
- `.trellis/spec/backend/android-quality-guidelines.md:7`: offline/local validation allowed; device operations require a fresh explicit user request. `:631`: local unit tests, static checks, and lint are the developer gate.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: explicit parser → state → storage → UI boundaries.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: reuse existing constants/parsers and avoid duplicated projection rules.
- Backend database and generic frontend quality/type-safety files are templates, not additional executable board-storage contracts.

## Caveats / Not Found

- This research writes only this file. No Git operation, task activation, source change, build, device operation, or network request was performed.
- No full model or Compose UI behavior test currently proves prefix-only reactivity; it is a new acceptance requirement, not covered by existing source-string tests.
- The duplicate scan used a read-only Python parse with trailing-comma normalization to locate occurrences, followed by source line evidence. It is not a replacement for actual Fastjson compatibility tests.
- The lookup map can already miss a new tree occurrence when a same-ID favorite exists. Preserve this as a separately understood existing behavior unless the reviewed U1 design explicitly changes insertion membership rules; the icon updater itself must not depend on that map.
- Persisted prefix format, URL validation, raw-cache bootstrap, daily timestamp semantics, and one-in-flight request policy are owned by companion research. This document recommends state application boundaries without claiming a final decision on those policies.
- Other sessions' in-flight R1–R6 work and archived R7 product repairs remain outside this isolated U1 scope.
