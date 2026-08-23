# Implement — 长按发帖按钮刷新当前页

Ordered checklist. Steps 1–2 are a behavior-preserving refactor; steps 3–4 are
the new feature. Keeping that split makes a failed manual test attributable.

## Step 1 — Add `LongPressRepeater`

- [ ] Create
      `lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/LongPressRepeater.java`
      per `design.md` §Component 1.
- [ ] Constructor takes `(long repeatIntervalMillis, OnLongPressRepeatListener)`;
      reject a null listener and a non-positive interval.
- [ ] `setRepeatCondition`, `setRepeatIntervalMillis`, `attach(View)`,
      `detach(View)`, `stop()`, `stop(View)`.
- [ ] `onLongClick`: return `false` when the condition rejects; otherwise stop
      any active repeat, record the active view, fire once, re-post when still
      attached and pressed, return `true`.
- [ ] Repeat runnable bails out unless the view is still the active one, still
      attached, still pressed, and still passes the condition.
- [ ] Javadoc states it is the project's single press-and-repeat implementation.

## Step 2 — Migrate `TabLayoutEx` onto it (no behavior change)

- [ ] Delete `mLongPressedTabView`, `mLongPressedTabPosition`,
      `mRepeatCurrentTabLongPressRunnable`,
      `mCurrentTabLongPressRepeatIntervalMillis`, `startCurrentTabLongPress`,
      `stopCurrentTabLongPress`.
- [ ] Add the `LongPressRepeater` field plus `isCurrentTab(View)` and
      `dispatchCurrentTabLongPress(View)`, resolving position through
      `getChildAdapterPosition(view)`.
- [ ] Set the repeat condition once, at construction.
- [ ] `onCreateViewHolder` → `attach(holder.itemView)`.
- [ ] `onViewRecycled` → `stop(holder.itemView)` (view-scoped, not `stop()`).
- [ ] `onDetachedFromWindow` → `stop()`.
- [ ] `setOnCurrentTabLongPressListener(listener, intervalMillis)` keeps its
      signature and its `IllegalArgumentException` guard; forwards the interval
      to the repeater when the listener is non-null.
- [ ] Confirm `ArticleTabFragment`'s two existing call sites still compile
      untouched.

**Gate before continuing:** `:nga_phone_base_3.0:assembleDebug` compiles and the
existing `ArticlePageRefreshContractTest` failures are only the three scheduling
assertions that intentionally moved to the helper.

## Step 3 — Board page

- [ ] `TopicSearchFragment`: extract `onTitleClick()`'s body into
      `protected void scrollToTopAndRefresh()`, moving the cached-list comment
      with it; leave `onTitleClick()` as a delegate.
- [ ] `TopicListFragment`: add
      `private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;`
      and `private LongPressRepeater mFabRefreshRepeater;`.
- [ ] In `onViewCreated`, after `super.onViewCreated(...)`, construct with
      `view -> scrollToTopAndRefresh()` and `attach(mFab)`.
- [ ] Add `onDestroyView()` that detaches and nulls the repeater before `super`.

## Step 4 — Article page

- [ ] `ArticleTabFragment`: add `private LongPressRepeater mFabRefreshRepeater;`.
- [ ] In `onViewCreated`, next to the tab wiring, construct with
      `view -> refreshCurrentPage()` using
      `CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS` and `attach(mFab)`.
- [ ] In `onDestroyView`, `detach(mFab)` next to the existing
      `setOnCurrentTabLongPressListener(null, 0L)` line.
- [ ] Leave `refreshCurrentPage()`, `reply()`, and `scrollCurrentPageToTop()`
      untouched.
- [ ] Do **not** touch `setRefreshPage` / `ArticleShareViewModel` — deferred.

## Step 5 — Update the contract test

In `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticlePageRefreshContractTest.kt`:

- [ ] Add sources for `LongPressRepeater.java` and `TopicSearchFragment.java`.
- [ ] Remove
      `assertFalse(articleTabSource.contains("@OnLongClick(R.id.fab_post)"))`.
- [ ] Move the scheduling assertions (`isPressed()`, `postDelayed(`,
      `removeCallbacks(`) from the `TabLayoutEx` test onto `LongPressRepeater`;
      keep the current-tab guard, `onDetachedFromWindow()`, and
      `onViewRecycled(ViewHolder)` assertions on `TabLayoutEx`.
- [ ] Assert `TabLayoutEx` no longer carries its own loop: no
      `mRepeatCurrentTabLongPressRunnable`, and it references
      `LongPressRepeater`.
- [ ] New test for the FAB wiring: both fragments construct the repeater, pass
      the right action, use the 5s constant, `attach(mFab)`, and detach in
      `onDestroyView`.
- [ ] New assertion: `TopicSearchFragment.onTitleClick()` delegates to
      `scrollToTopAndRefresh()`.
- [ ] New assertion: `LongPressRepeater.onLongClick` has both `return true;` and
      the condition-reject `return false;`.
- [ ] Keep every remaining existing assertion; do not relax any of them.

## Step 6 — Update the spec

In `.trellis/spec/frontend/component-guidelines.md`:

- [ ] Replace "Keep the post/reply FAB single-purpose. Do not attach refresh to
      its click or long-press behavior." with the new contract: short tap stays
      composition-only; long press refreshes with a 5s repeat; board adds
      scroll-to-top, article does not.
- [ ] Record `LongPressRepeater` as the project's single press-and-repeat
      mechanism, and that widget-specific constraints go in as a
      `RepeatCondition` rather than a second loop.
- [ ] State the layering rule explicitly: the mechanism is shared, the refresh
      action is not — each trigger calls its own screen's `loadPage`.
- [ ] Note the deferred convergence of the article screen's two refresh routes.
- [ ] Extend the *Verification* scan list: the `fab_post` scan now also shows the
      two repeater call sites, and add a scan asserting `LongPressRepeater` is
      the only `postDelayed` repeat loop.

## Validation gates

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests "*ArticlePageRefreshContractTest*"
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :lib_base_common:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
```

Then report the built APK path so the developer can install it.

## Review gate

- [ ] AC1–AC8 each traceable to a concrete diff hunk.
- [ ] AC9 gates green, with real output pasted into the report.
- [ ] AC10 — hand over the APK path plus the M1–M12 checklist from `prd.md`, and
      say that device confirmation is pending the developer's manual pass. Do not
      describe the gesture as verified.

## Rollback points

- Step 1 alone leaves dead code; reverting that one file is clean.
- Step 2 is the only risky edit (touches shipped behavior). If M9–M11 regress,
  revert Step 2 alone — steps 3 and 4 do not depend on it beyond the helper
  existing.
- Steps 3 and 4 are independent of each other; either page can be reverted alone.
- Step 3's `TopicSearchFragment` extraction is the only edit to shared board-list
  code; if the title tap regresses (M5), revert steps 2–3 together.
