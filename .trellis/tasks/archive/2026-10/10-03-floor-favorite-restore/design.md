# Original floor favorite restoration

## Approved boundary

This is a small restoration at the existing menu-to-handler boundary. The smallest behavior gap is that the already implemented selected-post favorite operation cannot be invoked from either floor overflow menu.

The user approved the original behavior after review. The historical research report retains alternative navigation ideas as findings only; they are excluded from this implementation.

## Files and data flow

1. `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu.xml`: restore `menu_favorite`, title “收藏”, between `menu_vote` and `menu_show_this_person_only`.
2. `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu_with_tid.xml`: restore the same ID/title between `menu_ban_this_one` and `menu_vote`.
3. `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`: restore the `BookmarkTask` import and `menu_favorite` switch case calling `BookmarkTask.execute(tidStr, pidStr)`.
4. `.trellis/spec/frontend/component-guidelines.md`: replace only the obsolete floor-favorite prohibition and describe the restored wiring.

Existing popup handling captures the clicked `ThreadRowInfo` and passes it to the handler. Existing `tidStr/pidStr` already derive from that row. Reuse this flow without new helpers, state, conditions, API changes, or refactors.

## Compatibility

The original title, placement, `TOPIC_FAVOR.ADD` request and response toast are preserved. Whole-thread favorites continue using their existing `execute(int tid)` caller. Favorite deletion, identity, menu visibility outside this action, cache/prefetch, and navigation remain untouched.

Do not revert `6203dad5`: it also contains unrelated menu cleanup and cache fixes. The user's restoration request supersedes the old spec prohibition only for floor favorites.

## Verification and rollback

Review both menu branches and clicked-row identity, compile the app, run the repository Debug test/lint gate, and inspect reports. No new source-spelling tests or fake arithmetic tests are warranted for restoring these existing few lines. Add tests only if a real behavioral gap beyond this wiring restoration emerges, and keep it within scope.

No NGA or device calls are needed. Reverting the three product-file additions rolls back the restoration without a migration or server-data change.
