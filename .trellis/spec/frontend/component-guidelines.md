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
- Each refresh attempt resolves `mPagerAdapter.getCurrentFragment()` at call
  time, skips while that fragment is already refreshing, and otherwise reuses
  its existing `loadPage()` path. Do not change the selected page, scroll to the
  top, or invoke reply composition.
- Keep the post/reply FAB single-purpose. Do not attach refresh to its click or
  long-press behavior.
- The gesture inherits the existing article loading, error, fallback, and
  reading-position behavior. Changes such as retaining stale content on failure
  or restoring by `pid` plus pixel offset require a separate task.

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
```

The first scan must have no active matches. The second scan must have no
matches; the unused `ScrollAwareFabBehavior` class itself may remain while no
layout attaches it. The third scan should show one direct action per relevant
layout, retained pull-to-refresh wiring, article-only clearance, and the
current-page long-press refresh wiring. The fourth scan must have no matches.
The fifth scan must have no matches. The sixth scan must show reads only — no
assignment into the emoticon tables outside `EmoticonUtils` itself. The seventh
scan must show one binding per topic-list toolbar and the matching handler, and
no listener attached to a `Toolbar` rather than its title view.
