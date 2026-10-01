# Research: U1 upstream, URL, and refresh boundary

- Query: Recheck the upstream icon patch and the current cache/refresh path before designing U1.
- Scope: Local source and pinned Git objects; no network or device observations.
- Date: 2026-09-30
- Fork baseline: `1a8413d9` in `/home/toph/nga-just-works-upstream-adoption`.

## Confirmed source facts

| Source | Finding |
| --- | --- |
| `6a78543086cf439aa695d28c56aeca1f8ae8bdd4` | Adds nullable `forum_icon_pre`, `PreferenceKey.BOARD_ICON_URL = "board_icon_url"`, a derived per-board `iconUrl`, and ordinary/collection URL construction. The ordinary directory is `/ngabbs/nga_classic/f/app/`; replacing it with `/proxy/cache_attach/ficon/` yields the collection directory. `stid != 0` takes precedence over `fid != 0`. |
| Same patch, `ForumBoardModel.saveData` | Changes root-list persistence to `boardMap.values.toList()`. Reject this hunk: the map contains roots and descendants and does not preserve root order. |
| `750871b31b533dd74059ade146a084f396020cbe` | Changes the remote timestamp key to `board_remote_request_time_v2`. No new periodic refresh mechanism. |
| `ForumBoardViewModel.kt:184-213` | Navigation triggers the existing daily request; only a nonempty added-board list triggers merging. Completion timestamps are written even when the repository returns null. There is no in-flight guard or UI publication after the merge. |
| `ForumBoardRepository.kt:254-284` | Fetches `BOARD.CATEGORIES` through the existing Retrofit GBK path, parses with fastjson 1, and writes the original response to `board_list_remote.json`. An unused `loadRemoteBoardList` already reads that file. It can contain `forum_icon_pre` even though the old DTO does not expose it. |
| `ForumBoardRepository.kt:35-66` | Local list version is 6; changing it deletes the local list and resets remote refresh. U1 must not bump it. |
| `ForumBoardView.kt:48,169-184,197-214` | Actual consumer is Coil `rememberAsyncImagePainter`, with bundled drawable priority. Old research and `ApiConstants` comments saying Glide are stale. Keep the actual Coil path and local drawable behavior. |
| `ApiConstants.java:12,23` | Current HTTPS defaults serve ordinary and collection paths separately. Preserve these as the fallback authority. |
| `ForumsListBean.kt:9-12` | No icon prefix field yet. The old `sp.phone.mvp.model.ForumsListModel` is not a consumer of this Compose repository. |
| `HomeBoardOrderContractTest.kt:17-39`; asset `board_list.json:1381` | Existing real-asset test uses fastjson. The bundled file has string numeric fields and a trailing comma, so a strict Python JSON parser rejects it. Do not rewrite the asset or change JSON libraries for U1. |

Paths above without directories refer to `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/`, except `ApiConstants` under `sp/phone/common/`, the DTO under `data/`, and tests under the matching `src/test/java/` package.

## Reuse and adaptations

1. Reuse the upstream field/preference/construction pipeline and `stid` priority. Keep the current HTTPS defaults and a board-specific resolver; `NgaImageHost` owns the distinct attachment path and user setting and must not be used here.
2. Make icon state derived and exclude it from fastjson persistence. Store the accepted prefix, then reconstruct icons from stable numeric identities on initialization and changes. Old list JSON remains readable and unchanged in shape.
3. Read validated saved preference first, then the existing raw remote cache, then the fixed default. Catch corrupt cache/metadata locally. A failed cache read must not abort initialization or alter favorites.
4. Keep the existing timestamp key/day interval and navigation trigger. No one-time forced refresh is necessary for cache format migration: cached raw metadata can be reused; otherwise fixed defaults remain until the next eligible request (remaining interval at most one day in normal clock operation). This is a deliberate adaptation of `750871b3`, not an omitted migration.
5. Return prefix metadata independently of board additions. Apply both against current model state on Main after IO fetch/parse. A single in-flight guard prevents duplicate requests/responses; ordinary attempt-completion throttling stays unchanged, including failure.
6. Accept only a well-formed prefix for the known board-icon directory; derive the collection directory by an exact path replacement. Unknown directory layouts have no source-backed collection mapping and retain the last valid/default prefix. No arbitrary string replacement or attachment-host routing.
7. HTTPS is the output scheme. Preserve the documented compatibility of `http://img4.nga.cn` by normalizing that known board origin to HTTPS; do not blindly upgrade unknown cleartext origins. Reject credentials, non-default ports, query/fragment, malformed host/path, and retired image hosts. Proposed normalization and observable cases are fixed in `design.md` for final review.

## Relevant contracts

- `.trellis/spec/backend/nga-platform-operation-registry.md`: `BOARD.CATEGORIES`.
- `.trellis/spec/backend/nga-platform-access-rules.md`: path-family separation, media without account cookies, offline verification.
- `.trellis/spec/backend/network-foundation-contract.md`: preserve request identity/encoding; do not add fallback or raw diagnostics.
- `.trellis/spec/frontend/state-management.md`: stable identity and reorder transactions.
- `.trellis/spec/backend/android-quality-guidelines.md`: Debug build/unit/lint gate; no device operation by default.
- `.trellis/spec/backend/database-guidelines.md` is an unfilled template, not an active persistence contract; use real source/tests and the favorite state contract instead.

## Caveats

- No current server prefix was requested. Pinned upstream code proves field/path behavior, not present service availability.
- No build/test was run during planning. The strict Python asset inspection failed on the known trailing comma; it is not an Android parser failure or a product regression.
- This worktree excludes ongoing R1–R6 product edits. Final integration must include their eventual reviewed commits; U1 research does not verify those in-flight changes.
