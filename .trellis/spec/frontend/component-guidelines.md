# Android Component Guidelines

The pinned Justwen layouts, navigation, themes, and screen structure are the UI
baseline. Do not introduce a parallel UI architecture or broad visual redesign
while making compatibility fixes.

## Legacy Activity edge-to-edge insets

Activities that inherit a third-party screen base (for example,
`MaterialAboutActivity`) do not receive the inset handling implemented by the
project `BaseActivity` classes. After the library calls `setContentView`, attach
the status-bar listener to the library's top app-bar container and request
insets explicitly:

```java
final int initialPaddingTop = appBar.getPaddingTop();
ViewCompat.setOnApplyWindowInsetsListener(appBar, (view, insets) -> {
    Insets statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
    view.setPadding(
            view.getPaddingLeft(),
            initialPaddingTop + statusBars.top,
            view.getPaddingRight(),
            view.getPaddingBottom());
    return insets;
});
ViewCompat.requestApplyInsets(appBar);
```

Always retain the original padding and recompute from it on every dispatch.
Adding the inset to the current padding accumulates space when the system
redispatches insets. If a resource belongs to a non-transitive library, use the
library's fully-qualified `R` class.

### Common Mistake: Assuming the shared BaseActivity handles every screen

**Symptom**: A legacy Activity's toolbar is drawn under Android 15 status-bar
icons while Compose and project-base screens look correct.

**Fix**: Apply the local app-bar inset after the third-party layout is inflated,
and add a source contract test for ordering and idempotent padding.

## System navigation bar and Android 15 edge-to-edge

The application targets SDK 35, so Android 15 enforces edge-to-edge and may
make the system navigation region transparent. A window's default white
navigation area is not a reliable theme background.

- Configure navigation-bar color and icon appearance for both day and night
  themes. Do not gate this behavior on `isNightMode()` or on whether a screen
  uses Compose; the home Activity enables Compose before its base `onCreate`.
- Disable the Android Q+ navigation contrast scrim when the app provides its
  own themed background, and set the decor background to the active surface.
- Compose screens must set the navigation-bar color from
  `MaterialTheme.colors.background` and use `MaterialTheme.colors.isLight` for
  icon appearance. Setting only the status bar color is incomplete.
- Inset listeners must capture the original padding and recompute from it on
  every dispatch. Update navigation-bar height each time, and mutate an
  existing status placeholder's layout params instead of replacing its parent-
  specific `LayoutParams` subtype.

Required regression coverage must assert that the old
`isNightMode() && !mComposeEnabled` gate is absent and that Java, Kotlin, and
Compose paths configure the navigation background and icon appearance.

### Swipe back owns the decor background when it is active

`SwipeBackActivityHelper.onActivityCreate()` clears the window and decor
backgrounds so the activity underneath shows through while dragging. That
directly contradicts the decor-background rule above, so the two must not both
run on the same Activity.

- Legacy `BaseActivity` attaches swipe back only when
  `DeviceUtils.hasNavigationButtons()` is true. Gesture-navigation devices keep
  the unmodified decor path, so their rendering must stay byte-identical.
- When swipe back is attached, skip `decorView.setBackgroundColor(...)` and
  paint `R.color.background_color` on the content root after
  `attachToActivity` instead. Do not fall back to the theme
  `android:windowBackground`: it resolves to `#FF202020` at night while the
  app-wide surface is `background_color` (`#080C10`).
- `SystemThemeContractTest` asserts on the literal source text
  `getWindow().getDecorView().setBackgroundColor(backgroundColor)`. Wrapping
  that call in a condition is fine; rewriting it (for example extracting a
  `decorView` local) silently breaks the contract.
- Navigation-mode detection must stay synchronous. The swipe-back layout is
  attached during `onCreate`, before window insets are dispatched, so an
  insets-based check (`tappableElement().bottom == 0`) cannot drive it.
- Swipe back has no user-facing preference. Navigation mode alone decides it,
  so the app never carries two parallel interaction models at once.

#### Revealing the page below needs `Activity#setTranslucent`

`me.imid.swipebacklayout` reveals the previous screen by reflecting on the
hidden `Activity#convertToTranslucent`. That member has been blocked since
Android 9 and the library swallows the failure in its own `try/catch`, so the
drag silently exposes a black gap instead of the previous page. Verified on a
Xiaomi API 35 device: `SwipeBackHelper` must register its own
`SwipeListener` and call the public `Activity#setTranslucent(true)` (API 30) on
`onEdgeTouch`.

`android:windowIsTranslucent` in the theme is necessary but **not** sufficient
— the activity below still is not drawn without the explicit call, so do not
drop the listener on the grounds that the theme already declares translucency.
Guard the call for API 29, which is the install floor and predates the API.
Do not convert back to opaque when the drag settles: the next drag would then
start against a stopped activity and flash black again.

## Compose Material theme generation boundaries

The shared `AppTheme` is a Material 2 theme (`androidx.compose.material`). A
screen hosted under it must use Material 2 content components when it expects
default text/icon colors:

```kotlin
import androidx.compose.material.Icon
import androidx.compose.material.Text
```

Do not import `androidx.compose.material3.Text` or `Icon` into such a screen
without an explicit Material 3 theme boundary or explicit content colors.
Material 3 composition locals do not inherit the Material 2 palette; the
result can be black default content on the dark NGA background. Explicit gray
helper copy is a separate visual hierarchy and should not be recolored merely
to mask this theme-generation mismatch.

For the legacy font/avatar size screen, `SeekBarEx` uses
`@color/text_color` for the completed track and `@color/text_color_disabled`
for the remaining track. The night resources therefore resolve to a light
completed segment and a gray remaining segment without hard-coding a
night-only layout.

### Semantic colors at mixed Material boundaries

Some legacy Compose surfaces, such as the home drawer gesture shell, still
use Material 3 container primitives while the application theme is Material 2.
That is a compatibility boundary, not a second theme. Any label, icon, input,
or metadata content rendered inside that surface must receive an explicit
Material 2 semantic color (`MaterialTheme.colors.onBackground`,
`onSurface`, or a derived alpha). Do not rely on Material 3 defaults, because
they do not inherit the shared Material 2 palette.

Legacy text editors follow the same rule through Android resources: define a
semantic background, text, and hint color in both `values/` and
`values-night/`, then reference those names from the layout. Do not use global
`@color/black` or `@color/white` for an editor that can be shown on a themed
surface.

## Home navigation drawer

### 1. Scope / Trigger

Use this contract when changing the home board Pager, the favorite page's
leading direction, drawer gestures, or favorite reorder arbitration. The drawer
is logically adjacent to the favorite page, but it is an overlay rather than a
Pager page. It follows a leading drag that begins inside the favorite Pager
content.

### 2. Signatures

```kotlin
data class PagerInteractionState(
    val settledPage: Int,
    val isScrollInProgress: Boolean,
)

TabLayoutWithPager(
    pagerModifier: Modifier = Modifier,
    onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null,
)

ForumBoardView(
    pagerModifier: Modifier = Modifier,
    onPagerInteractionChanged: ((PagerInteractionState?) -> Unit)? = null,
    onFavoriteReorderActiveChanged: (Boolean) -> Unit = {},
)

HomeNavigationDrawer(
    drawerState: HomeDrawerState,
    gestureState: HomeDrawerGestureState,
    drawerContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
)
```

`TopAppBarData` still defaults to `TopAppBarNavigationIcon.Back`. The home
screen explicitly selects `TopAppBarNavigationIcon.Menu` with the accessible
label `打开侧边栏`.

### 3. Contracts

- Attach `pagerModifier` directly to `HorizontalPager`. Report its bounds in
  the same root coordinate space as the home drawer; the toolbar and tab row
  must remain outside the opening region.
- At pointer down, snapshot whether the Pager is settled on page `0`. A stream
  that begins on a later page or while the Pager is moving remains content-owned
  even if page `0` is reached before that stream ends.
- Observe at the Initial pass. Keep the stream undecided through small jitter,
  leave vertical-dominant and physical trailing movement unconsumed, and latch
  leading horizontal movement to the drawer. In LTR, physical right is leading;
  reverse the physical direction in RTL.
- Drive drag, release settlement, Menu open, scrim close, dismiss, and Back
  through one home-only `AnchoredDraggableState`. Its anchors are
  `Closed = -sheetWidth` and `Open = 0`; preserve the current target explicitly
  when measurement replaces anchors.
- Once the drawer owns a stream, enter one `anchoredDrag(UserInput)` transaction,
  apply the full displacement accumulated during direction classification, then
  consume and apply subsequent deltas. The stationary home content must not
  move; the sheet offset and scrim opacity derive from the same state.
- Settle a valid release at 50% distance or 400dp/s leading velocity with a
  256ms snap animation. A consumed release, `ACTION_CANCEL`, tracked-pointer
  loss, active reorder, or owner teardown rolls back to the stable value
  captured at down. Drain remaining pointers before accepting a new gesture;
  teardown must reset in non-cancellable cleanup so a half-open offset cannot
  survive coroutine cancellation.
- Active favorite reorder owns its stream and keeps Pager scrolling disabled.
  If reorder activates while an opening candidate exists, cancel and roll back
  the drawer transaction.
- Place the sheet with an absolute physical offset. Align it to start in LTR
  and end in RTL, but mirror the logical offset exactly once. Clear closed-sheet
  semantics; while visible, retain pane/dismiss semantics, scrim click, Back,
  Menu open, and horizontal drag close.
- Do not use a 24dp edge band, `systemGestureExclusion`, Material internal APIs,
  reflection, or a recomposition-time Boolean handoff.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Settled favorite page, leading drag inside Pager bounds | Sheet and scrim follow during the same stream |
| Leading release reaches 50% or 400dp/s | Settle open |
| Leading release below both thresholds | Animate closed |
| Opposite direction, vertical dominance, later page, or unsettled start | Content/Pager behavior only |
| Consumed release, cancellation, pointer loss, or teardown | Restore the captured stable anchor |
| Another pointer remains pressed | Cancel and drain before another gesture |
| Favorite reorder active | Reorder only; no drawer or Pager transition |
| Drawer visible | Horizontal drag, scrim, dismiss, Menu state, and Back share the same anchors |
| Sheet width or layout direction changes | Preserve target and mirror physical placement exactly once |

### 5. Good/Base/Bad Cases

- **Good**: a rightward LTR drag from anywhere inside favorite content exposes
  the left sheet before release, then settles from the same offset.
- **Base**: a leftward LTR drag still moves from favorites to `网事杂谈`; a
  later page returns through normal Pager order before a new stream may open
  the drawer.
- **Bad**: waiting for `UP` before showing the sheet, observing the entire home
  surface, consuming vertical/trailing movement, double-mirroring RTL, or
  allowing cancellation to leave a partial offset.

### 6. Tests Required

- Unit-test Pager-bound eligibility, LTR/RTL direction and offset, settled page
  zero, same-stream later pages, jitter/vertical classification, the 50%
  distance and 400dp/s velocity thresholds, first accumulated delta, measured
  anchor replacement, cancellation reset, consumed release, and remaining
  pointers.
- Assert the shared Back default, the home Menu label, stationary content,
  progressive scrim, closed semantics, and visible Back/scrim/dismiss paths.
- Compile, unit-test, and lint both `lib_base_ui_compose` and
  `nga_phone_base_3.0`; scan for `DrawerEdgeWidth`, `isWithinDrawerEdge`,
  `systemGestureExclusion`, obsolete completion callbacks, Material internal
  APIs, and reflection in affected sources.
- Keep physical device/emulator playback as the final gate for continuous
  pixels, first-frame ownership, Pager/reorder interaction, scrim/Back, and RTL.

### 7. Wrong vs Correct

#### Wrong

```kotlin
onRelease = { if (distance >= width / 2) drawerState.open() }
Modifier.systemGestureExclusion { /* 24dp edge */ }
```

This provides no follow-finger feedback and reintroduces an undiscoverable,
system-gesture-conflicting edge target.

#### Correct

```kotlin
HorizontalPager(modifier = pagerModifier)
anchoredState.anchoredDrag(MutatePriority.UserInput) {
    dragTo((anchoredState.requireOffset() + delta).coerceIn(minAnchor(), maxAnchor()))
}
```

The Pager supplies the eligible region, and one shared anchor transaction
produces continuous sheet and scrim progress while keeping content stationary.

## Favorite board grid

- A short press opens the selected board.
- A long press on a favorite card starts direct drag reorder; there is no
  page-level sorting mode or separate reorder entry.
- Disable `HorizontalPager` user scrolling only after the long press becomes
  an active drag. Restore paging on end, cancellation, disposal, or rollback.
- Identify grid items by `fid + stid`, never by list index or historical `id`.
- Provide TalkBack custom actions for move up, move down, move to top, and move
  to bottom. Pointer drag cannot be the only reorder mechanism.

## Home board tab order

### 1. Scope / Trigger

Use this contract when changing the shared tab Pager API or the order of the
home board sections after the fixed bookmark page.

### 2. Signatures

```kotlin
TabLayoutWithPager(
    tabKeys: List<String> = tabs,
    reorderableTabRange: IntRange? = null,
    onTabReorderStart: ((tabKey: String) -> Unit)? = null,
    onTabReorderMove: ((fromIndex: Int, toIndex: Int) -> Boolean)? = null,
    onTabReorderCommit: (() -> Unit)? = null,
    onTabReorderCancel: (() -> Unit)? = null,
    onTabReorderActiveChanged: (Boolean) -> Unit = {},
)
```

### 3. Contracts

- Reorder is disabled unless stable unique keys, a range, and the complete
  start/move/commit/cancel callback set are supplied. Existing callers retain
  ordinary `ScrollableTabRow` click and Pager behavior.
- The home range starts at index `1`; `bookmark` at index `0` receives no drag
  modifier or reorder accessibility actions.
- A short press still calls `animateScrollToPage`. Long press activates drag,
  performs haptic feedback, consumes later movement, and disables Pager input
  until end, cancel, or disposal.
- Resolve every move from the dragged stable key against a synchronous
  gesture-local key order. Update that local order immediately after each
  accepted move so consecutive events before recomposition use fresh indices
  and cannot move a different board.
- Resolve the pointer's target index from the currently rendered tab keys and
  bounds as positional slots, then apply that index to the gesture-local order.
  Never use gesture-local keys to look up bounds that may still reflect the
  previous render, because that can move the tab back across the same slot.
- Edge dwell moves one tab at a time and makes the dragged tab the scroll-row
  target, allowing movement through off-screen tabs.
- Retain the selected stable key while order changes. Both the indicator and
  the Pager relocation use that key's new index. Key the Pager page content by
  the same stable key so remembered grid/page state moves with the logical
  board instead of staying attached to its former index.
- TalkBack actions are `左移`, `右移`, `移到最前`, and `移到最后` within the
  configured range.
- Favorite-card and home-tab reorder states both gate Pager input, but only the
  favorite-card state is forwarded to home drawer gesture arbitration.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| No reorder callbacks or invalid/duplicate keys | Existing click/Pager behavior; no reorder modifier |
| Long press on bookmark tab | No reorder transaction |
| Long press then horizontal drag on another tab | Candidate order publishes; Pager input is disabled |
| Consecutive events before recomposition | Rendered bounds choose a positional target slot; the synchronous gesture-local order moves the same stable key with no stale index or bounce |
| Edge dwell | Move one position repeatedly and keep the dragged tab visible |
| End | Commit, clear active state, restore Pager |
| Cancel or disposal | Restore snapshot, clear active state, restore Pager |
| Persist failure for the current candidate | Model rolls back without overwriting newer state |

### 5. Good/Base/Bad Cases

- **Good**: drag `other` across two tabs; callbacks resolve `other` by key for
  both moves, the selected logical board stays selected, and release persists.
- **Base**: tap a non-bookmark tab and retain the existing animated page change.
- **Bad**: reuse a pre-recomposition `fromIndex`, install drag on bookmark, or
  forward home-tab reorder as `favoriteReorderActive` to the drawer.

### 6. Tests Required

- Unit-test consecutive stable-key moves without recomposition while the
  rendered order/bounds remain at the previous frame.
- Source-contract test optional defaults, long-press activation, terminal
  cleanup, Pager gating, edge movement, stable selection and Pager page keys,
  and all four accessibility actions.
- App contract tests must pin the index-1 range, bookmark-relative model
  indices, separate favorite/tab states, and `网事杂谈` as the page adjacent to
  favorites.

### 7. Wrong vs Correct

#### Wrong

```kotlin
onMove(staleFromIndex, targetIndex)
reorderableTabRange = 0..tabs.lastIndex
```

#### Correct

```kotlin
val targetIndex = resolveRenderedTabTargetIndex(
    renderedOrder = renderedOrder,
    tabBounds = tabBounds,
    reorderableRange = allowedRange,
    pointerX = pointerX,
) ?: return
val move = resolveStableTabMove(gestureOrder, draggedKey, targetIndex)
onMove(move.fromIndex, move.toIndex)
reorderableTabRange = 1..tabs.lastIndex
```

## Home drawer terminology and placement

- The App-wide local list of bookmarked boards is displayed as `收藏板块` on
  the home Pager. Its drawer action is `清理收藏板块`, and the confirmation copy
  must use the same term.
- `收藏夹` / `已收藏的主题` is the separate server-side topic-favorite screen.
  Do not place the board-cleanup action on that screen or add a topic cleanup
  button as part of the board-bookmark workflow.
- `关于` is the final drawer item and is anchored to the bottom with flexible
  space after the primary drawer actions.
- Source contract tests must assert these labels, the absence of the ambiguous
  `清空我的收藏` copy, and the ordering of the bottom spacer before `关于`.

## Contextual floating action buttons

- A topic list contains one Material `FloatingActionButton` that directly
  opens new-topic composition. It remains visible while scrolling and never
  changes icon, label, or click behavior based on scroll direction.
- An article view contains one Material `FloatingActionButton` that directly
  opens reply composition. It also remains visible while scrolling and never
  changes semantics. The cached article activity hides that button.
- Both FABs additionally carry a hold-to-refresh gesture; see
  *Post/reply FAB long-press refresh*. That gesture never alters the tap action,
  the icon, or the label.
- Do not restore `FloatingActionsMenu`, `fab_refresh`,
  `ScrollAwareFamBehavior`, or the bundled `floatingactionmenu.aar`.
- Do not attach `ScrollAwareFabBehavior` or another nested-scroll hide/show or
  action-swapping behavior to either direct-action FAB. The legacy behavior
  class may remain unused while other callers are audited.
- Topic-list and article floating action buttons use their layout-default
  `end|bottom` placement. Do not add a handedness preference or runtime
  gravity override.
- Live article lists add `article_list_reply_fab_clearance` (80dp) to their
  existing bottom padding and set `clipToPadding=false`, allowing the final
  floor's controls to scroll above the persistent reply FAB. Apply this in
  `ArticleListFragment` only when `loadCache` is false. Do not add matching
  clearance to topic lists, and do not turn the clearance into a list item or
  include it in floor/page calculations.
- Article pages always use `fragment_article_tab`, with the page tabs at the
  top. Do not add a bottom-tab preference or a second bottom-tab layout.
- Retain the existing pull-to-refresh behavior.

## Article floor overflow menus

- Neither `article_list_context_menu.xml` nor
  `article_list_context_menu_with_tid.xml` exposes support, oppose, floor
  favorite, or signature actions (`支持`, `反对`, `收藏`, `查看签名`). Keep
  the remaining entries in their current relative order.
- The row adapter's standalone support/oppose listeners remain active. The poll
  dialog (`menu_vote`) is a separate action and stays in both menus.
- Remove a deleted floor action's local handler together with its menu entry,
  but search for shared IDs first: `menu_favorite` still belongs to the topic
  list menu, and `menu_add_bookmark` still bookmarks the whole thread.

## Cached article page tabs and cache action

- Online and cached article tabs use the same sizing rule: one through five
  available pages divide the strip equally; more pages use the existing
  scrollable min/max widths.
- In `ArticleCacheActivity`, call
  `setTabOnScreenLimit(count <= 5 ? count : 0)` before `setUpWithViewPager`.
  Derive `count` from the number of cached entries, not the highest page number
  or the server's total replies. Two cached pages such as `[2, 7]` still take
  half the strip each and retain their actual file/page labels.
- The full-thread toolbar's `缓存本页` action depends on
  `ArticlePageCache.isCacheableContext`, not on launch-time `topicInfo`.
  Notification → reply → `显示全部` is a valid full-thread entry path even
  though it never passed through a topic list. The parent pager's initial page
  may be zero; cache saves use its selected, 1-based child page.
- Follow the [thread-page cache contract](../backend/thread-page-cache-contract.md)
  for metadata preparation, filtered-view exclusions, and compatibility with
  existing cache files.

## Press-and-repeat long press

`LongPressRepeater`
(`lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/LongPressRepeater.java`)
is the project's **only** implementation of "long press, fire immediately, then
repeat on an interval while held". Never write a second `postDelayed` repeat
loop of this shape; attach this one instead.

- The helper owns *when* to fire. The caller owns *what* fires. A widget's own
  precondition goes in as a `RepeatCondition`, never inside the helper.
- Repetition stops when the view loses its pressed state (release, cancel,
  drag-out, parent interception), when it detaches from the window, or when the
  condition stops holding. `View.postDelayed` is not cancelled on detach, so the
  runnable re-checks `isAttachedToWindow()` on every tick — keep that check.
- `onLongClick` returns `true` when it accepts the press, which is what stops
  `performClick()` from also firing and suppresses the platform tooltip. It
  returns `false` when the `RepeatCondition` rejects, leaving the caller's normal
  click path intact.
- Use `stop(View)` when cancelling from a recycler callback so an unrelated
  recycled view cannot cancel an active hold elsewhere; `stop()` is the
  unconditional form.
- Callers that attach for a fragment view's lifetime must `detach(view)` in
  `onDestroyView`.

Current consumers: `TabLayoutEx` (page tabs), `TopicListFragment` and
`ArticleTabFragment` (post/reply FAB).

## Article current-page refresh

- Do not expose refresh in `article_list_option_menu.xml`; the article overflow
  starts with the existing go-to-floor action.
- A short reselect of the current page tab remains bound only to
  `scrollCurrentPageToTop()`.
- Long-pressing the selected page tab refreshes the current page once when the
  platform long-press threshold is reached. While the same tab remains pressed,
  repeat the refresh attempt every 5 seconds. Losing the pressed state through
  release or cancellation, changing pages, tab recycling, view detachment, or
  fragment view destruction must prevent any further refresh.
- `TabLayoutEx` delegates that scheduling to `LongPressRepeater` and supplies
  "the pressed view is still the selected tab" as its `RepeatCondition`. It
  resolves the tab position live through `getChildAdapterPosition(view)`; do not
  reintroduce a cached pressed-view or pressed-position field.
- Long-pressing a tab that is not the selected one must stay unconsumed, so the
  existing tap-to-switch behavior still runs.
- Each refresh attempt resolves `mPagerAdapter.getCurrentFragment()` at call
  time, skips while that fragment is already refreshing, and otherwise reuses
  its existing `loadPage()` path. Do not change the selected page, scroll to the
  top, or invoke reply composition.
- The gesture inherits the existing article loading, error, fallback, and
  reading-position behavior. Changes such as retaining stale content on failure
  or restoring by `pid` plus pixel offset require a separate task.

## Post/reply FAB long-press refresh

The single direct-action FAB carries two gestures. A **short tap** stays
composition-only — new topic on a board, reply in an article — and must never
refresh. A **long press** refreshes what is currently on screen, repeating every
5 seconds while held, via `LongPressRepeater`.

- Board (`TopicListFragment`): each cycle runs the full
  `TopicSearchFragment.scrollToTopAndRefresh()` — scroll to the top **and**
  reload page 1 — so the list stays pinned at the top for the whole hold. Its
  existing `isEnabled()` / `isRefreshing()` guards are the throttle; do not add
  another.
- Article (`ArticleTabFragment`): each cycle runs `refreshCurrentPage()` only.
  Do not scroll, expand the app bar, change the selected page, or open reply
  composition.
- `onTitleClick()` remains a delegate to `scrollToTopAndRefresh()`, so the
  toolbar title tap and the FAB hold cannot drift apart.
- Reuse the mechanism, not the action. There is no cross-screen `RefreshHelper`
  or `Refreshable` abstraction, and the FAB gesture does not call into the tab
  gesture's code. Each trigger lands on the `loadPage` entry point its own screen
  already owns — the two refreshes differ in host, parameters, and lifecycle, and
  unifying them would produce a class that branches on screen type.
- This reverses an earlier rule that kept the FAB single-purpose. The legacy
  `FloatingActionsMenu` 「刷新」 button (`fab_refresh`) stays deleted; the hold
  gesture is not a route to bringing that menu back.

**Known duplication, deferred:** the article screen has two refresh routes that
both end at `ArticleListFragment.loadPage()` — the
`ArticleShareViewModel.setRefreshPage()` LiveData broadcast used after posting a
reply, and the direct `getCurrentFragment().loadPage()` used by both long-press
gestures. Converging them is a separate task; do not fold it into a feature
change. Neither call site is the re-entrancy barrier: every refresh funnels
through `ArticlePageRequestState` in `ArticleListPresenter`, which drops a
foreground load while one is already in flight. The `isRefreshing()` check on
the long-press path is a redundant second guard, and the broadcast path's lack
of one causes no duplicate request. The asymmetry is cosmetic, not a defect.

## Topic list title tap

Tapping the toolbar title of a topic list returns to the top of the list and
reloads the first page. Apply this contract when touching `ToolbarUtils`, the
topic list fragments, or their toolbars.

- Bind the listener to the Toolbar's title `TextView`, never to the `Toolbar`
  itself. Blank toolbar space, the navigation icon, and the overflow button are
  not part of the gesture.
- `Toolbar` exposes no accessor for that view, so `ToolbarUtils` finds it by
  matching child text against `Toolbar.getTitle()`. Never index children
  positionally.
- `sp.phone.ui.fragment.BaseFragment` only stores the title and writes it to the
  Activity in `onResume`, so the title view may not exist yet at
  `onViewCreated`. Bind immediately and retry once through `Toolbar.post`. One
  binding is enough: `Toolbar.setTitle` reuses the same `TextView` instance, so
  a later title change keeps the listener.
- Both topic list hierarchies wire it. `TopicSearchFragment` covers the board,
  search, favorite, and cache screens; `TopicListSimpleFragment` covers the
  digest and 24-hour lists.
- Skip the reload and scroll only when pull-to-refresh is disabled or a load is
  already in flight. `TopicCacheFragment` disables refresh once its data is in,
  and the initial load already runs behind the loading view.
- `TopicHistoryFragment` binds the same tap for the scroll only. It is a local
  list with nothing to reload, and its toolbar belongs to the hosting
  `LauncherSubActivity`, so it resolves `R.id.toolbar` from the Activity rather
  than from its own view.
- The board toolbar carries `SCROLL | ENTER_ALWAYS`, so returning to the top
  must also expand the `AppBarLayout`. That already lives in the
  `TopicListFragment` `scrollTo` override the title tap reuses; do not duplicate
  it.

## Article author metadata

On main, thread-floor detail shows post count alone; automatic author-location
queries are disabled, and level/reputation no longer appear there. The separate
`experiment/auto-ip-query` branch retains public profile IP metadata. Follow the
[author-location contract](../backend/author-profile-location-contract.md) for
the main integration boundary and the retained helper behavior. Any experimental
async metadata must use generation/author/holder-checked payloads that only bind
`tv_detail`; never reload body WebViews through a full-list notification.

## Initial-loading usage tips

Legacy article/topic/search lists and recent notifications share `LoadingLayout`.
Follow the [loading-tip contract](./loading-usage-tips-contract.md) for view-owner
binding, stable foreground-only selection, and conditional AI guidance. The
widget's visibility policy must not alter existing page loading, refresh
gestures, or completion timing.

## Article body rendering path

The article body is **always** a `LocalWebView`. The native `tv_content`
`TextView` in `fragment_article_list_item.xml` is dead in this path:

- `HtmlConvertFactory.convert()` always returns `String.format(sHtmlTemplate,
  style, html)`, so it is never empty.
- `ArticleConvertFactory.buildRowContent()` calls it unconditionally for every
  row and writes the result to `formattedHtmlData`.
- `ArticleListAdapter.getItemViewType()` therefore always returns
  `VIEW_TYPE_WEB_VIEW`, and that branch sets `contentTextView` to `GONE`.

Do not attach article body behavior to `tv_content`. Release 4.10.0 shipped a
selection-menu customization bound to that `TextView`; it never executed on any
device, on any vendor ROM. Before wiring behavior to a view, confirm the branch
that makes it visible is actually reachable.

`LocalWebView.setLocalMode()` calls `setLongClickable(false)`. That does **not**
suppress long-press text selection: modern Chromium WebView handles the gesture
in its content layer, outside the `View` long-click path.

### Retained article page entry

`ArticleListPresenter` redelivers READY data on resume, including a response
already rendered by offscreen prefetch. In `ArticleListFragment`, reuse the
body when the accepted response is the same instance as `mDisplayedData` for
the current view and the displayed topic-owner value is unchanged. A newly
discovered/changed topic owner still needs a rebind to update OP badges.
Calling `notifyDataSetChanged()` on every unchanged entry causes full
row binding and another `loadDataWithBaseURL()` for each body WebView, producing
an unnecessary reload even though no page request was made.

Keep foreground title/menu updates, topic-owner metadata and pending anchors
independent of body binding. Validate reader/account/generation identity before
reusing content. A new response must render even when its page number is the
same. Use the displayed response as the view-lifetime marker: `mDeliveredData`
can survive view destruction, but `mDisplayedData` must be cleared in
`onDestroyView()` so `onViewCreated()` binds retained data into the new adapter.

In-place row edits must publish their own update. For a blacklist toggle,
notify only the clicked row after its flag changes, using its index in the
currently displayed response. A stale menu whose row is no longer displayed
must not rebind a replacement row. Do not rely on a later page resume to make
the changed nickname badge visible.

Review ordinary/cached resume, completed and in-flight prefetch, explicit
refresh, view recreation and stale-generation rejection. Existing offline
request/UI checks do not establish device-visible smoothness.

## Compatibility reader navigation and row presentation

### 1. Scope / Trigger

Use this section when rendering a normal/App page or changing its native
navigation. The [compatibility reader contract](../backend/thread-detail-compat-contract.md)
owns parsing and source selection; UI code consumes its typed facts.

### 2. Signatures

```java
ArticleReaderSession ArticleShareViewModel.initializeReader(ArticleListParam param)
LiveData<ArticleReaderState> ArticleShareViewModel.getReaderState()
void ArticlePagerAdapter.updateReaderState(ArticleReaderState state)
int ArticlePagerAdapter.getActualPage(int position)
int ArticlePagerAdapter.positionOfPage(int page)
ArticleListParam ArticleListFragment.fullThreadParam()
void ArticleListAdapter.releaseWebViews()
boolean ArticleRowPresentation.canReply(ThreadRowInfo row)
```

### 3. Contracts

- Apply a launch page before the first read. A PID entry uses the scoped reply
  screen; `显示全部` uses the response's resolved tid and a fresh full-query
  parameter at page 1. Clear PID/author/search/cache disposition together.
- Pager position is not a server page or global floor. Known totals permit
  numbered pages; unknown totals display the obtained window under its actual
  page label. Do not report that the full thread has one page merely because
  one window is available. Cache tabs retain their independently stored page
  numbers and one-through-five equal sizing rule.
- Generation changes retire old page Fragments and READY data. A pending
  `ArticleAnchor` belongs to a generation/page; scroll only after finding its
  actual PID or floor in that response. A posted scroll must still refer to
  the same displayed response and a resumed view. No modulo-based index or
  silent page scan is allowed.
- Use `ArticleNavigation.handoffNotice(data, anchor)` before consuming an
  anchor for a newly adopted page. App data without a reliable matched target
  says the reading position could not be retained; incomplete-content feedback
  must not suppress that position information.
- `ArticleRowPresentation` carries known floor/identity/score/source facts.
  Hide missing floor/score labels instead of showing `-1` or an invented zero.
  Restore visibility and clickability on every bind so an App/unknown row does
  not affect a subsequently recycled ordinary row.
- Compare meaningful UIDs for the OP badge. Unknown/anonymous identities must
  not create a badge, profile/filter link or UID-0 quote attribution. A readable
  body can still be quoted with neutral author text when identity is absent.
- Explicit comments hide `贴条` and `只看此人`; sparse ordinary rows must not
  become comments merely because they lack an avatar. Keep the previously
  removed floor menu actions removed. Source-unavailable rows show an explicit
  incomplete-content message and disable source-dependent actions.
- Readable standalone comment/unknown-kind rows with a real own PID retain
  reply and quote actions. Those actions depend on source/PID availability,
  not on a blanket ordinary-post kind check. Missing UID still uses neutral
  attribution; unknown parent metadata cannot replace the row's own PID.
- Keep source-based composer input separate from `formattedHtmlData`. Preserve
  original App source for editing; apply the narrow upstream reply-header
  normalization to renderer/temporary quote input. Do not send the fully
  rendered document to the composer.
- Retain `LocalWebView` instances according to the actual row list, including
  pages larger than 20. Release the previous page's instances when replacing
  data and on view destruction. Body selection, gestures, FAB behavior and
  ordinary HTML/image-prefix preparation keep their existing contracts.

### 4. Validation & Error Matrix

| Input / transition | Visible result |
| --- | --- |
| PID-only entry resolves to tid T | Show the matched reply; `显示全部` opens T at page 1 |
| Reported App size is 30 or 40 | Render every returned row without a 20-view array limit |
| Page has floors 61, 64 and 69 | Locate actual floor 64 at index 1, not `64 % pageSize` |
| Page size or total is unavailable | Keep readable content; disable only navigation requiring missing facts |
| Known-score row follows an unknown-score row in a recycled holder | Restore the score label and its real value |
| Comment or missing identity/source | Hide only the inapplicable actions; do not fabricate identity/content |
| Independent comment/unknown kind, readable source and known own PID | Allow existing reply/quote actions with their actual source and PID |
| Pending jump outlives response, generation or resumed view | Do not scroll the replacement/background view |

### 5. Good / Base / Bad Cases

- **Good:** a PID lookup retains its original floor while the response supplies
  the tid needed to open the full thread.
- **Base:** normal full-thread tabs, direct reply FAB and existing long-press
  refresh gestures retain their behavior.
- **Bad:** use a filtered list position as a global floor, label an unknown
  score zero, or index a 40-row response into a 20-element WebView array.

### 6. Tests Required

Use synthetic page/row contracts for full, PID and author queries; unknown and
variable page sizes; actual target lookup; source/generation retirement;
UID/score/comment facts and quote attribution. Retain the existing menu, tab,
refresh and image-prefix regressions. Android builds and lint are required;
offline/source checks are not a claim of device UI verification.

### 7. Wrong vs Correct

```java
// Wrong: a server floor is not an index into a sparse/filtered list.
list.scrollToPosition(floor % 20);
// Correct: use the validated anchor and handle an absent target explicitly.
int index = anchor.find(data.getRowList());
if (index >= 0) list.scrollToPosition(index);
```

## Article WebView text selection

- WebView has no `setCustomSelectionActionModeCallback`. The only
  application-level hook is `startActionMode`, which Chromium calls on its
  container view. Override **both** overloads on `LocalWebView` and wrap the
  incoming callback. `View.startActionMode(callback)` dispatches to the
  two-argument overload, so guard against double wrapping with an `instanceof`
  check on the wrapper type.
- The wrapper must extend `ActionMode.Callback2` and forward `onGetContentRect`
  to the wrapped callback. Skipping it breaks floating toolbar positioning.
- `onPrepareActionMode` must rebuild unconditionally and always return `true`.
  Chromium repopulates the menu on every `invalidate()`, and vendor-injected
  entries arrive through the same `Menu`, so a single build at create time is
  not enough.
- Rebuild the menu with exactly Copy, Select all, and Search, in that order,
  using ids declared in `res/values/ids.xml`. Do **not** reuse
  `android.R.id.copy` / `android.R.id.selectAll`: Chromium binds its own
  handlers to ids inside the WebView APK that the app cannot reference, so a
  rebuilt menu owns all three actions. Mark all three as
  `SHOW_AS_ACTION_ALWAYS` and keep the `AlwaysShowAction` lint suppression
  scoped to the menu-building method.
- Read the selection with `evaluateJavascript`; this couples the toolbar to
  `LocalWebView` keeping JavaScript enabled. Copy writes to `ClipboardManager`,
  Select all runs `selectAllChildren(document.body)` without ending the mode,
  and Search passes the nonblank selection as `SearchManager.QUERY` in an
  `Intent.ACTION_WEB_SEARCH`, catching `ActivityNotFoundException`.
- Vendor-injected entries arrive through the same `Menu`, so the rebuild clears
  them too. Confirmed on HyperOS / Xiaomi 15 with 4.11.0: no share entry and no
  vendor overlay survives. Xiaomi patches the framework more heavily than the
  other major OEMs, so a clean result there is good evidence the takeover holds
  broadly — WebView itself is a Mainline module and is not vendor-modified.
- Do not push these overrides down into `lib_base_common`'s `WebViewEx`. It is a
  shared base class and future subclasses would inherit the behavior silently.
- Keep the source contract test synchronized with the override pair, the
  double-wrap guard, `Callback2` conformance, menu membership and order, the
  per-action guards, and the absence of any share or `ACTION_PROCESS_TEXT` path.

`String.trim()` only removes characters up to U+0020 and is not a valid blank
check for selected forum text. Iterate by code point and combine both Unicode
predicates so no-break and ideographic spaces are rejected as blank:

```java
if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
    return false;
}
```

Keep this logic, and the decoding of `evaluateJavascript` JSON results, in a
class free of `android.*` imports. The module has JUnit only — no Robolectric
and no `returnDefaultValues` — so anything touching framework classes cannot be
covered by an executing unit test.

## Emoticon picker order

The post/reply emoticon panel (`EmoticonControlPanel` → `EmoticonParentAdapter`
→ `EmoticonChildAdapter`) lets users reorder emoticons inside a category by
long-press drag. Apply this contract when touching the panel adapters, the
emoticon tables, or the order preference.

- A short press inserts the emoticon; a long press starts drag reorder. There is
  no edit mode and no separate reorder entry. Reorder is within a category only:
  the category tabs themselves are not sortable.
- `EmoticonUtils.EMOTICON_LABEL` and `EMOTICON_URL` stay read-only constants.
  The custom order is a separate index permutation over the built-in table, held
  by the adapter and persisted on its own. Never write user preference back into
  the static tables: they are process-wide constants, mutating them would add a
  startup initialization dependency, and reset would lose its reference point.
- Identify an emoticon by its image file name (`EMOTICON_URL[c][i][1]`), never by
  array index. Indices shift whenever a release adds or removes emoticons.
  `EmoticonUtilsContractTest` pins the file-name-uniqueness premise; if it fails,
  re-plan the identity choice rather than patching the key in place.
- Column 0 is the emoticon name and column 1 is the file name. `getFilePath()`
  reads column 0 while treating it as a URL, which is why `getPathByURI()`
  always returns `null` today. Do not copy that column choice; use
  `EmoticonUtils.getFileNames(int)`.
- Merge saved order against the built-in table on every read: drop entries the
  app no longer ships, drop duplicates, and append newly shipped emoticons at the
  end in built-in relative order. Corrupt data falls back to the built-in order
  and must never crash the panel.
- Preference key is `key_emoticon_order_<categoryId>` holding a JSON array of
  file names. A category matching the built-in order stores nothing.
- Grid drag flags must include `LEFT | RIGHT` as well as `UP | DOWN`, otherwise
  items cannot swap within a row. Swipe stays disabled.
- Never call `RecyclerView.requestDisallowInterceptTouchEvent(true)` to protect a
  drag from the hosting `ViewPager`. That override notifies every registered
  `OnItemTouchListener` first, and `ItemTouchHelper` is one of them: its handler
  runs `select(null, ACTION_STATE_IDLE)` and cancels the drag outright, after
  which the horizontal gesture falls back to the pager and turns the page.
  `ItemTouchHelper.select()` already requests disallow on
  `mRecyclerView.getParent()` when a drag starts, so the pager conflict needs no
  extra handling. Verified against `androidx.recyclerview:recyclerview:1.1.0`
  bytecode; the failure was reproduced on a device on 2026-07-29.
- Persist on `clearView` (drag end), not on each `onMove`.
- Keep the insert payload byte-identical: `[s:<id>:<name>]-<id>/<fileName>`.
  The adapter derives it from the emoticon at the dragged position, so a custom
  order must not change any emitted string.
- Adding a settings entry changes `DefaultSettingsContractTest`. Update the
  pinned key list deliberately; never relax the assertion.

## Verification

```bash
rg -n "FloatingActionsMenu|fab_refresh|ScrollAwareFamBehavior|floatingactionmenu" \
  nga_phone_base_3.0
rg -n 'layout_behavior=.*ScrollAwareFabBehavior' \
  nga_phone_base_3.0/src/main/res
rg -n "fab_post|SwipeRefreshLayout|article_list_reply_fab_clearance|setOnCurrentTabLongPressListener" \
  nga_phone_base_3.0/src/main
rg -n "item_refresh" \
  nga_phone_base_3.0/src/main/res/menu/article_list_option_menu.xml \
  nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java
rg -n "left_hand|bottom_tab|isLeftHandMode|isShowBottomTab|fragment_article_tab_bottom" \
  lib_base_common nga_phone_base_3.0/src/main
rg -n "EMOTICON_URL|EMOTICON_LABEL" lib_base_common nga_phone_base_3.0/src/main
rg -n "setOnTitleClickListener|onTitleClick" nga_phone_base_3.0/src/main
rg -n "postDelayed" lib_base_common/src/main nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment
rg -n "LongPressRepeater" lib_base_common/src/main nga_phone_base_3.0/src/main
```

The first scan must have no active matches. The second scan must have no
matches; the unused `ScrollAwareFabBehavior` class itself may remain while no
layout attaches it. The third scan should show one direct action per relevant
layout, retained pull-to-refresh wiring, article-only clearance, and the
current-page long-press refresh wiring. The fourth scan must have no matches.
The fifth scan must have no matches. The sixth scan must show reads only — no
assignment into the emoticon tables outside `EmoticonUtils` itself. The seventh
scan must show one binding per topic-list toolbar and the matching handler, and
no listener attached to a `Toolbar` rather than its title view. The eighth scan
must show no press-and-repeat loop outside `LongPressRepeater` — other
`postDelayed` hits are fine as long as none of them re-post themselves on a
press. The ninth scan must show the single helper plus its three consumers:
`TabLayoutEx`, `TopicListFragment`, `ArticleTabFragment`.
