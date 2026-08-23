# Design — 长按发帖按钮刷新当前页

## Summary

Extract the "long-press, fire immediately, repeat while held" loop out of
`TabLayoutEx` into a standalone `LongPressRepeater` in `lib_base_common`, then
drive three call sites from it: the page tabs (migrated, behavior unchanged),
the board FAB, and the article FAB.

No layout changes. No new resources. No presenter, ViewModel, or network changes
— every trigger lands on a `loadPage` entry point that already exists.

## Layering decision

| Layer | Shared? | Why |
| --- | --- | --- |
| Mechanism — press-and-repeat scheduling | **Yes, exactly one copy** | Depends only on `android.view.View`. Nothing about it is specific to tabs, FABs, or refreshing. A second `postDelayed` loop of this shape is a defect. |
| Action — "refresh what I'm looking at" | **No shared abstraction** | Board and article refreshes differ in host, parameters, and lifecycle. Each trigger calls the entry point its own screen owns. A cross-screen `Refreshable` would branch on screen type. |

This is plain strategy/delegation: the helper owns *when* to fire, the caller
owns *what* fires.

## Existing pieces reused

| Piece | Location | Role here |
| --- | --- | --- |
| `TabLayoutEx` repeat loop | `lib_base_common/.../widget/TabLayoutEx.java` | Source of the extraction; becomes a consumer |
| `ArticleTabFragment.refreshCurrentPage()` | `.../ArticleTabFragment.java:154` | Article refresh action, unchanged |
| `TopicSearchFragment.onTitleClick()` | `.../TopicSearchFragment.java:191` | Board scroll-to-top + reload page 1 |
| `TopicListFragment.scrollTo(0)` override | `.../TopicListFragment.java:82` | Expands the app bar when scrolling to top |

## Component 1 — `LongPressRepeater`

New file:
`lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/LongPressRepeater.java`

Placed in `base/widget`, next to its consumers, because it is a view-interaction
helper whose only dependency is `android.view.View`. `base/util` holds
app-level utilities with no view coupling.

### Contract

```java
public final class LongPressRepeater implements View.OnLongClickListener {

    public interface OnLongPressRepeatListener {
        void onLongPressRepeat(View view);
    }

    /** 每次触发前检查；返回 false 则本次不触发，并停止重复。 */
    public interface RepeatCondition {
        boolean canRepeat(View view);
    }

    public LongPressRepeater(long repeatIntervalMillis, OnLongPressRepeatListener listener);

    public void setRepeatCondition(RepeatCondition condition);
    public void setRepeatIntervalMillis(long repeatIntervalMillis);

    public void attach(View view);   // 内部 setOnLongClickListener(this)
    public void detach(View view);   // 停止 + 摘掉监听器
    public void stop();              // 无条件停止当前重复
    public void stop(View view);     // 仅当正在重复的就是该 view 时停止
}
```

One instance tracks one active press (`mPressedView`), because one finger
produces one long press. `attach` may be called on many views — the tab layout
attaches to every tab holder — and whichever one is long-pressed becomes the
active view.

### Behavior

`onLongClick(view)`:

1. If a `RepeatCondition` is set and rejects `view` → return `false`, leaving the
   event unconsumed so the caller's normal click path still runs.
2. Otherwise stop any active repeat, record `view` as active, fire the listener
   once, and — if the view is still attached and still pressed — `postDelayed`
   the repeat runnable. Return `true`.

Repeat runnable, before every fire: the view must still be the active one, still
attached, still pressed, and still pass the condition. Any failure stops the loop
without firing.

`detach(view)` and `stop()` are idempotent.

### Why `isPressed()` rather than touch tracking

`View` clears the pressed flag on `ACTION_UP` and `ACTION_CANCEL`, covering
release, drag-out, and interception by a parent `CoordinatorLayout` or
`RecyclerView`. This is the signal `TabLayoutEx` already ships with, so the
extraction inherits behavior that has run on real devices rather than inventing
new gesture handling.

### Why the runnable still re-checks attachment

`View.postDelayed` targets the attach-info handler and is not cancelled when the
view detaches, so a pending repeat can outlive its view. The runnable's
`isAttachedToWindow()` check plus `removeCallbacks` in `stop()` close both paths.

### Why returning `true` preserves the tap

`View.onTouchEvent` skips `performClick()` on `ACTION_UP` once
`mHasPerformedLongPress` is set, which happens exactly when the long-click
listener returns `true`. Consuming the event satisfies "long press must not also
post" with no extra flag in the fragments, and also suppresses the platform
long-press tooltip.

## Component 2 — Migrating `TabLayoutEx`

Deleted: `mLongPressedTabView`, `mLongPressedTabPosition`,
`mRepeatCurrentTabLongPressRunnable`, `mCurrentTabLongPressRepeatIntervalMillis`,
`startCurrentTabLongPress`, `stopCurrentTabLongPress`.

Added: one `LongPressRepeater` field, plus two private methods it is wired to.

```java
private final LongPressRepeater mCurrentTabLongPressRepeater =
        new LongPressRepeater(DEFAULT_REPEAT_INTERVAL_MS, this::dispatchCurrentTabLongPress);
// constructor: mCurrentTabLongPressRepeater.setRepeatCondition(this::isCurrentTab);

private boolean isCurrentTab(View tabView) {
    int position = getChildAdapterPosition(tabView);
    return mOnCurrentTabLongPressListener != null
            && mViewPager != null
            && position != NO_POSITION
            && position == mViewPager.getCurrentItem();
}

private void dispatchCurrentTabLongPress(View tabView) {
    mOnCurrentTabLongPressListener.onCurrentTabLongPress(getChildAdapterPosition(tabView));
}
```

Resolving the position live via `getChildAdapterPosition(view)` — `TabLayoutEx`
extends `RecyclerTabLayout` extends `RecyclerView` — is strictly more accurate
than the cached `mLongPressedTabPosition` it replaces: a rebind during the hold
can no longer report a stale index.

Wiring changes:

- `onCreateViewHolder`: `mCurrentTabLongPressRepeater.attach(holder.itemView)`
  replaces the inline `setOnLongClickListener`.
- `onViewRecycled`: `mCurrentTabLongPressRepeater.stop(holder.itemView)` — the
  view-scoped form, so recycling an unrelated off-screen tab cannot cancel an
  active hold on a different tab.
- `onDetachedFromWindow`: `stop()`.
- `setOnCurrentTabLongPressListener(listener, intervalMillis)`: keeps its exact
  current signature and its `IllegalArgumentException` guard; internally it now
  calls `stop()`, assigns the listener, and forwards the interval to the
  repeater when the listener is non-null.

The public API and every observable behavior are unchanged, which is what makes
manual checks M9–M11 a regression test for this migration.

## Component 3 — Board FAB wiring

`TopicSearchFragment`:

- Extract `onTitleClick()`'s body into `protected void scrollToTopAndRefresh()`,
  carrying the existing cached-list comment.
- `onTitleClick()` becomes a one-line delegate, preserving the title-tap contract
  and the spec's `rg -n "setOnTitleClickListener|onTitleClick"` scan.

`TopicListFragment`:

- `private static final long CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS = 5_000L;`
- `private LongPressRepeater mFabRefreshRepeater;`
- In `onViewCreated`, after `super.onViewCreated(...)` — which is where
  `TopicSearchFragment` runs `ButterKnife.bind`, so `mFab` is bound by then —
  construct the repeater with `view -> scrollToTopAndRefresh()` and
  `attach(mFab)`. No `RepeatCondition`.
- New `onDestroyView()` calling `detach(mFab)` before `super`.

Each cycle runs the full scroll-to-top **and** refresh, per the requirement: the
list stays pinned at the top for the whole hold.

`fab_post` exists only in `fragment_topic_list_board.xml`, which only
`TopicListFragment` inflates, so the sibling `TopicSearchFragment` subclasses
(cache, favorite, history) are untouched and cannot hit a missing view.

## Component 4 — Article FAB wiring

`ArticleTabFragment`:

- Reuse the existing `CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS`, so the tab
  gesture and the FAB gesture cannot drift apart.
- `private LongPressRepeater mFabRefreshRepeater;`, constructed in
  `onViewCreated` with `view -> refreshCurrentPage()` and attached to the
  already-bound `mFab`. No `RepeatCondition`.
- In `onDestroyView`, `detach(mFab)` next to the existing
  `setOnCurrentTabLongPressListener(null, 0L)` teardown.

`refreshCurrentPage()` is not modified: it resolves the current fragment at call
time, skips while that fragment is refreshing, and calls `loadPage()`. The tab
and FAB gestures are independent — each schedules its own repeat, and
`isRefreshing()` prevents a duplicate load if both somehow fire.

## Deferred, deliberately

The article screen keeps two refresh routes that both end at
`ArticleListFragment.loadPage()`: the `ArticleShareViewModel.setRefreshPage()`
broadcast (used by `onActivityResult` after posting) and the direct
`getCurrentFragment().loadPage()` (used by both long-press gestures). Converging
them is genuine cleanup, but it is a refactor of three more files with its own
edge cases, and mixing it into a feature change would make a failed manual test
ambiguous. Recorded in `prd.md` §Non-goals as follow-up work.

## Testing strategy

`nga_phone_base_3.0` has no Robolectric and `lib_base_common` has no Android
unit-test harness, so the project pattern is source/XML contract tests.
`ArticlePageRefreshContractTest` already reads across both modules and is
extended rather than duplicated.

Assertions that must **move** (not be dropped) as part of the extraction —
today they point at `TabLayoutEx`, afterwards at `LongPressRepeater`:

- `isPressed()` guard, `postDelayed(`, `removeCallbacks(`.

Assertions that **stay** on `TabLayoutEx`:

- the current-tab guard, `onDetachedFromWindow()`, `onViewRecycled(ViewHolder)`,
  and the unchanged `setOnCurrentTabLongPressListener` signature.

Assertions that are **new**:

- both fragments: the repeater construction, the action passed, the 5s interval,
  `attach(mFab)`, and `detach(mFab)` in `onDestroyView`;
- `TopicSearchFragment`: `onTitleClick()` still delegates to
  `scrollToTopAndRefresh()`;
- `LongPressRepeater`: `onLongClick` returns `true` when accepted and `false`
  when the condition rejects.

Assertion that is **removed** because the rule it encodes is being reversed:

- `assertFalse(articleTabSource.contains("@OnLongClick(R.id.fab_post)"))`.

Compile coverage comes from `:nga_phone_base_3.0:assembleDebug`, the only check
that the Butter Knife bindings and the new cross-module import actually resolve.
Device coverage is the developer's manual M1–M12 pass.

## Risks

| Risk | Mitigation |
| --- | --- |
| The extraction silently changes tab long-press behavior | Public API and guards preserved verbatim; contract test keeps the current-tab and teardown assertions; M9–M11 exercise it by hand |
| A pending repeat fires after the fragment view is gone | `detach` in `onDestroyView` plus the runnable's attached/pressed/condition guards |
| Long press accidentally opens composition | Listener returns `true`; contract test pins it; M1/M6 confirm |
| Repeated `loadPage(1)` hammers the network while held | 5s interval plus the existing `isRefreshing()` / `isEnabled()` guards inside the actions |
| Recycling an unrelated tab cancels an active hold | `stop(View)` is view-scoped rather than unconditional |
| Spec and code disagree about FAB long-press | `component-guidelines.md` updated in the same change |
