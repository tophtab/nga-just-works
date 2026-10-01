# Favorite Board State Contract

## Scenario: App-Wide Favorite Reorder Transaction

### 1. Scope / Trigger

Use this contract when changing favorite membership/order, the favorite grid,
drag gestures, accessibility reorder actions, Pager interaction, or
`board_bookmark.json` persistence. Favorite state crosses Compose UI,
`ForumBoardViewModel`, `ForumBoardModel`, and `ForumBoardRepository`.

### 2. Signatures

```kotlin
fun bookmarkStableKey(board: BoardEntity): String
fun ForumBoardViewModel.beginBookmarkReorder(): List<BoardEntity>
fun ForumBoardViewModel.moveBookmark(from: Int, to: Int): Boolean
fun ForumBoardViewModel.cancelBookmarkReorder(snapshot: List<BoardEntity>)
fun ForumBoardViewModel.commitBookmarkReorder(snapshot: List<BoardEntity>)
fun ForumBoardRepository.writeBookmarkBoard(context: Context, boardList: List<BoardEntity>)
```

The enclosing pager accepts an explicit scroll gate:

```kotlin
TabLayoutWithPager(..., userScrollEnabled: Boolean = true)
```

### 3. Contracts

- Favorite membership/order is App-wide and is not keyed by the active NGA
  account. Login or account switching must not replace or reset it.
- Stable identity is `fid + stid`; list index is only a transient position.
- A short press opens the board. A long press on a favorite card activates
  direct drag without a separate sorting mode.
- Capture a snapshot before moving. Publish candidate moves immediately, then
  persist on the IO dispatcher.
- Persist only when the candidate is still current. Roll back only when that
  same candidate is still current, so an older failed write cannot overwrite a
  newer add/remove/reorder.
- Disable Pager user scrolling only while drag is active. Restore it on end,
  cancel, disposal, and rollback.
- Provide TalkBack move up/down/top/bottom actions; pointer drag is not the only
  reorder path.
- Write JSON to a staging file before replacing the primary file. A valid
  staging/backup file may recover a damaged primary. `[]` is an intentional
  empty list, not a missing source.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Invalid or no-op move index | Return false; do not persist |
| Valid move | Publish new order and schedule persistence |
| Drag cancelled | Restore the captured snapshot and re-enable Pager |
| Save fails and candidate is still current | Restore snapshot and republish |
| Save fails after a newer mutation | Do not overwrite or roll back newer state |
| Primary JSON malformed, backup/staging valid | Recover valid ordered data |
| All candidate files malformed | Return an empty safe list without deleting recovery evidence |
| Duplicate `fid + stid` entries | Preserve first stable occurrence only |
| Account changes | Favorite order remains unchanged |

### 5. Good/Base/Bad Cases

- **Good**: long press captures `[A,B,C]`, moving B after C publishes
  `[A,C,B]`, the same candidate persists, and Pager is restored on release.
- **Base**: short horizontal movement before long-press activation remains a
  Pager gesture; no reorder transaction begins.
- **Bad**: key items by index, save on the UI thread, disable Pager for every
  touch, or let a failed old write restore over a newer order.
- **Bad**: scope `board_bookmark.json` by account or require a network/session
  abstraction to load local favorites.

### 6. Tests Required

- Stable keys distinguish identical `fid` values with different `stid` values.
- Move covers forward, backward, same-index, and out-of-range positions.
- JSON/file round trips cover empty and duplicate data.
- Recovery covers malformed primary with valid staging/backup.
- Persistence failure covers rollback and newer-mutation protection.
- UI/static review covers short-click navigation, long-press drag activation,
  Pager restoration on every terminal path, and TalkBack reorder actions.

### 7. Wrong vs Correct

#### Wrong

```kotlin
itemsIndexed(boards, key = { index, _ -> index }) { ... }
pagerEnabled = false // for every pointer down
```

Index keys lose identity during reorder, and disabling paging before drag
activation breaks ordinary category swiping.

#### Correct

```kotlin
items(boards, key = ::bookmarkStableKey) { board -> ... }
TabLayoutWithPager(userScrollEnabled = !isBookmarkDragging)
```

The stable key follows the board while the explicit drag state arbitrates only
the active gesture.


## Scenario: Derived Board Icons and Category Refresh

### 1. Scope / Trigger

Apply when changing `BOARD.CATEGORIES`, board initialization, derived icons,
category insertion, or favorite restore paths. Icons cross the repository,
model, Compose entity state, and Coil without changing favorite identity.

### 2. Signatures

```kotlin
BoardIconUrlResolver.normalize(value: Any?): String?
BoardIconUrlResolver.resolve(prefix: String, fid: Int, stid: Int): String
BoardEntity.iconUrl: String
snapshotBoardTree(roots: List<BoardEntity>): List<BoardEntity>
```

The wire field is nullable `forum_icon_pre`; the preference key is
`board_icon_url`. `board_remote_request_time` retains its one-day interval.

### 3. Contracts

- Accept a prefix only from a top-level JSON object whose actual `result` is
  an array, including an empty array. Missing/null/non-array `result` cannot
  supply an icon update. Validate every category/group/forum array member as
  an object before DTO conversion or model mutation; a null/non-object member
  rejects the response, preventing partial insertion followed by an exception.
  Optional groups/forums may remain absent or null. Invalid icon metadata alone
  must not discard otherwise valid forums.
- Normalize absolute HTTPS DNS origins and the exact
  `/ngabbs/nga_classic/f/app/` directory. Only legacy HTTP `img4.nga.cn` is
  upgraded. Reject credentials, query/fragment, non-default ports, IP/local or
  retired hosts, and unknown directories. This resolver does not use post
  attachment-host settings or add account headers to image requests.
- `stid` takes precedence and uses `/proxy/cache_attach/ficon/<stid>v.png`;
  otherwise preserve signed `fid` in `<prefix><fid>.png`. Structural nodes
  with both IDs zero have no remote URL. Default URLs come from ApiConstants.
- Initialize from valid preference, then valid raw remote cache, then default.
  Preference type/read failures are local to this choice. A delayed cache
  result cannot replace a valid remote prefix already accepted by the model.
- `iconUrl` is observable Compose state and excluded from both JSON writing
  and reading. Hydrate every actual tree/favorite object recursively, including
  repeated equal entities and objects restored after a drag or file reload.
  Do not deduplicate this traversal through the ID map.
- Prefix-only updates persist the prefix alone: no tree/favorite file writes,
  key changes, Pager remount, or reorder transaction. Built-in drawable icons
  retain priority; Coil retains 48dp sizing and placeholder/error handling.
- Apply category responses against the current model on Main. Keep a single
  request in flight and the existing daily completion-attempt throttle;
  cancellation propagates and releases the guard without applying results.
- Insert supported categories only into existing valid parents, using the
  current map (including favorites) to filter known IDs. Preserve old order.
  Structural revision changes only for insertion; render child-list snapshots
  expose new members without keying the page by revision.
- Freeze the full ordered canonical root hierarchy before scheduling IO.
  Neither `boardMap.values` nor a deferred shallow root copy is a safe saved
  tree. Derived icon updates do not alter candidate-current/rollback rules.

### 4. Validation & Error Matrix

| Input/action | Required result |
| --- | --- |
| Empty result with changed valid prefix | Update every actual icon, no list writes |
| Bad prefix with valid forums | Keep prior prefix; apply valid additions |
| Missing/null/object result | Do not accept prefix |
| Null/non-object category/group/forum member | Reject before any prefix/tree mutation |
| Broken preference or cache | Fall back without deleting recovery files |
| Delayed cache after remote acceptance | Keep remote prefix |
| Same normalized prefix | No redundant observable icon change |
| Insertion during queued tree save | Saved snapshot retains its original hierarchy |
| Drag cancel or stale save failure after icon refresh | Keep latest icons and existing order protections |

### 5. Good/Base/Bad Cases

- Good: an empty remote category array changes the CDN origin and all existing
  ordinary/collection objects update in place.
- Base: no usable cache exists; default icons remain until the ordinary daily
  refresh becomes due.
- Bad: change the throttle key to force refresh, serialize derived URLs,
  traverse only the map, or rebuild the Pager to make icon changes visible.

### 6. Tests Required

Exercise the production resolver and state operations for valid/invalid origins,
negative IDs, collection priority, raw-envelope validation, old JSON read/write
exclusion, repeated entities, SnapshotStateObserver notifications, insertion
visibility, deep snapshots, restore/stale writes, and cancellation/throttling.
Retain the bookmark persistence and home-order suites. JVM and static UI checks
do not establish device rendering or CDN availability.

### 7. Wrong vs Correct

Wrong: update `boardMap.values` and asynchronously persist that flattened list.

Correct: hydrate the actual canonical roots and favorites recursively; when
membership changes, freeze `snapshotBoardTree(localBoardList)` within the model
boundary, then hand that snapshot to the existing IO writer.
