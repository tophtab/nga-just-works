# 长按发帖按钮刷新当前页

## Goal

Give the post/reply floating action button a second, hold-only gesture that
refreshes what the user is currently reading, mirroring the already-shipped
"long-press the selected page tab" refresh on article pages.

- Board page (`TopicListFragment`, `fragment_topic_list_board`): long-pressing
  the new-topic FAB returns the list to the top **and** reloads page 1.
- Article page (`ArticleTabFragment`, `fragment_article_tab`): long-pressing the
  reply FAB reloads the currently selected page, without changing pages or
  scroll position.
- Both gestures repeat on a fixed interval while the finger stays down, using
  the same repeat contract as `TabLayoutEx.setOnCurrentTabLongPressListener`.

## Background

Two mechanisms already exist and are the reference implementations:

1. `TabLayoutEx` + `ArticleTabFragment.refreshCurrentPage()` — long-press the
   selected page tab, refresh immediately at the platform long-press threshold,
   repeat every 5 seconds while that tab remains pressed, stop on release,
   cancel, page change, recycle, detach, or `onDestroyView`.
2. `TopicSearchFragment.onTitleClick()` — tap the toolbar title to scroll the
   topic list to the top and reload the first page.

This task reuses both rather than inventing new refresh paths.

### Prior art: the deleted `fab_refresh`

The upstream Justwen client had a `FloatingActionsMenu` whose second entry was a
「刷新」 button (`fab_refresh`):

- board — `mFam.collapse(); mPresenter.loadPage(1, mRequestParam);` (reload page
  1, **no** scroll to top);
- article — `getActivityViewModel().setRefreshPage(currentPage); mFam.collapse();`

That menu was removed from this fork and the spec forbids restoring it. The tab
long-press copied its *semantics* but not its implementation: it bypasses the
`setRefreshPage` broadcast and calls `getCurrentFragment().loadPage()` directly.
This task is the third consumer of the same underlying refresh, and deliberately
follows the tab long-press route rather than reviving the broadcast one.

## Explicit spec reversal

`.trellis/spec/frontend/component-guidelines.md` currently states, under
*Article current-page refresh*:

> Keep the post/reply FAB single-purpose. Do not attach refresh to its click or
> long-press behavior.

The user has explicitly asked to reverse the long-press half of that rule. The
**click** half stays: a short tap on either FAB must still open composition and
nothing else. `component-guidelines.md` must be updated in the same change so
the spec and the code do not disagree.

## Requirements

### R1 — Board page long-press

- Long-pressing `R.id.fab_post` in `TopicListFragment` scrolls the topic list
  back to position 0, expands the app bar, and reloads page 1 through the
  existing `onTitleClick()` path.
- The refresh is skipped when `mSwipeRefreshLayout` is disabled or a refresh is
  already in flight — inherited from `onTitleClick()`, not reimplemented.
- The gesture fires once as soon as the platform long-press threshold is
  reached, then repeats every 5 seconds while the FAB remains pressed.
- Each repeat performs the full scroll-to-top **and** refresh, not refresh
  alone.

### R2 — Article page long-press

- Long-pressing `R.id.fab_post` in `ArticleTabFragment` calls the existing
  `refreshCurrentPage()`.
- It must not change the selected page, scroll the list, expand the app bar, or
  open reply composition.
- Same timing contract as R1: immediate at threshold, then every 5 seconds while
  pressed.
- The existing tab long-press refresh keeps working unchanged and independently.

### R3 — Short tap is unchanged

- A short tap on the board FAB still opens new-topic composition
  (`startPostActivity()`).
- A short tap on the article FAB still opens reply composition (`reply()`).
- A long press must **not** additionally fire the click handler, and must not
  surface a system tooltip.

### R4 — Repeat lifecycle

Repetition must stop, with no further refresh attempts, on any of:

- finger release or touch cancellation (FAB leaves the pressed state);
- the FAB being detached from the window;
- fragment view destruction (`onDestroyView`).

No pending callback may outlive the fragment view or hold a reference that keeps
it alive.

### R5 — One implementation of the mechanism, project-wide

"Long-press, fire immediately, repeat on an interval while held" is a generic UI
interaction capability, not a property of any one widget. It must exist exactly
once, in `lib_base_common`, and every caller must go through it:

- both FAB call sites use it;
- `TabLayoutEx` is migrated onto it and deletes its private copy of the loop
  (`mLongPressedTabView`, `mLongPressedTabPosition`, its repeat `Runnable`, and
  its `postDelayed`/`removeCallbacks` bookkeeping).

Widget-specific constraints are supplied to the helper as a pre-fire condition,
not baked into it. After this task, grepping for a second `postDelayed` repeat
loop of this shape must find nothing.

### R5a — `TabLayoutEx` behavior is preserved bit for bit

The migration is a refactor, not a behavior change. All of these stay exactly as
they are today:

- the public API `setOnCurrentTabLongPressListener(listener, repeatIntervalMillis)`,
  including the `IllegalArgumentException` on a non-positive interval with a
  non-null listener, and `(null, 0L)` as the teardown call;
- long-pressing a tab that is **not** the current one does nothing and does not
  consume the event, so the existing tap-to-switch path still runs;
- repetition stops on release, cancel, page change, tab recycling, and view
  detachment;
- `OnTabReselectedListener` (tap current tab → scroll to top) is untouched.

### R5b — The action layer is *not* pooled into a shared helper

Reuse applies to the mechanism only. Each trigger routes into the refresh entry
point its own screen already owns:

- board: `TopicListPresenter.loadPage(1, param)`, reached through
  `TopicSearchFragment`'s scroll-to-top-and-reload method;
- article: `ArticleListFragment.loadPage()`, reached through
  `ArticleTabFragment.refreshCurrentPage()`.

Do not introduce a cross-screen `RefreshHelper`/`Refreshable` abstraction. The
two refreshes differ in host, parameters, and lifecycle; unifying them would
produce a glue class that branches on screen type. The FAB long-press must not
call into the tab long-press code either — both simply land on the same
`loadPage()`.

### R6 — Interval

Both pages use 5 seconds, matching
`ArticleTabFragment.CURRENT_PAGE_REFRESH_REPEAT_INTERVAL_MS`. The value is
defined in one place per call site as a named constant; no magic numbers in
the wiring.

## Non-goals

- No change to pull-to-refresh, the toolbar title tap, or the observable
  behavior of the tab long-press.
- No new menu entries, settings, toasts, haptics, or FAB icon/label changes.
- No restoration of `FloatingActionsMenu`, `fab_refresh`, or
  `ScrollAwareFamBehavior` — the legacy expandable FAB menu that once carried a
  「刷新」 button stays deleted. This task gives the surviving single FAB a
  hold gesture; it does not bring the old menu back.
- No `ScrollAwareFabBehavior`, hide-on-scroll, or action-swapping behavior
  (still forbidden by the spec).
- No change to loading, error, empty, or reading-position behavior; each refresh
  reuses the page's existing load path verbatim.
- **Deferred to a separate task:** the article screen has two coexisting refresh
  routes that both end at `ArticleListFragment.loadPage()` — the
  `ArticleShareViewModel.setRefreshPage()` LiveData broadcast used after posting
  a reply, and the direct `mPagerAdapter.getCurrentFragment().loadPage()` used by
  the tab long-press. Converging them is real cleanup but is a refactor of
  `onActivityResult`, `ArticleShareViewModel`, and `ArticleListFragment`'s
  observer, with its own edge cases (page matching, off-screen pages). Keeping it
  out of this change keeps manual device testing able to attribute a regression
  to one cause.
- The cached-article and cached-topic screens keep hiding or omitting the FAB;
  nothing is wired there.

## Constraints

- Java sources, Butter Knife bindings, `nga_phone_base_3.0` + `lib_base_common`
  module boundary.
- `nga_phone_base_3.0` has no Robolectric; Android-facing behavior is pinned by
  source/XML contract tests (`ArticlePageRefreshContractTest`), which must be
  extended rather than relaxed.
- No ADB device is reachable from this session, so the agent-side gates are the
  JVM contract tests plus a real compile. **Device verification is done by the
  developer manually on their own phone**, so the deliverable includes an
  installable debug APK and a written manual test checklist (see below). The
  final report must not claim device-verified behavior on its own.

## Manual device checklist (developer runs this)

Board page (进入任意板块):

- [ ] M1 — 短按发帖 FAB → 打开发新帖，行为和以前一样
- [ ] M2 — 长按发帖 FAB 不松手 → 立刻回到列表顶部并刷新，之后每约 5 秒再刷新一次
- [ ] M3 — 松手 → 停止，不再有额外刷新
- [ ] M4 — 长按后不松手直接返回上一页 → 不崩溃，回来后没有残留刷新
- [ ] M5 — 点标题栏标题 → 仍然回顶并刷新（老功能未被破坏）

Article page (进入任意主题):

- [ ] M6 — 短按回复 FAB → 打开回复，行为和以前一样
- [ ] M7 — 长按回复 FAB 不松手 → 当前页刷新，之后每约 5 秒再刷新一次；页码不变、列表不跳顶
- [ ] M8 — 松手 → 停止
- [ ] M9 — 长按页码 Tab（旧功能，本次被重构过）→ 刷新并每 5 秒重复，松手停止
- [ ] M10 — 长按**非当前**页码 Tab → 仍然是切页，不刷新
- [ ] M11 — 点当前页码 Tab → 仍然只回顶，不刷新
- [ ] M12 — 长按 FAB 期间左右滑动切页 → 不崩溃，行为可接受

## Acceptance Criteria

- [ ] AC1 — `TopicListFragment` attaches a long-press listener to `fab_post`
      that runs scroll-to-top + reload page 1, repeating every 5s while pressed.
- [ ] AC2 — `ArticleTabFragment` attaches a long-press listener to `fab_post`
      that runs `refreshCurrentPage()`, repeating every 5s while pressed, and
      does not scroll, change pages, or open composition.
- [ ] AC3 — Both FABs keep their existing `@OnClick` composition actions, and
      the long-press consumes the event so no click fires.
- [ ] AC4 — `LongPressRepeater` in `lib_base_common` is the only press-and-repeat
      loop in the project: both fragments use it, `TabLayoutEx` uses it, and
      `TabLayoutEx` no longer contains its own runnable/`postDelayed` bookkeeping.
- [ ] AC5 — Repetition stops on release, cancel, detach, and `onDestroyView`;
      the fragments detach the helper in `onDestroyView`.
- [ ] AC6 — No cross-screen refresh abstraction was added; each trigger calls the
      `loadPage` entry point its screen already had.
- [ ] AC7 — `ArticlePageRefreshContractTest` is updated: the assertion that
      forbids FAB long-press is replaced by assertions pinning the new wiring on
      both fragments, and the scheduling assertions that used to point at
      `TabLayoutEx` now point at `LongPressRepeater` while `TabLayoutEx` keeps
      its current-tab guard and teardown assertions. Assertions about FAB
      visibility, direct actions, reply clearance, and the overflow menu still
      pass unchanged.
- [ ] AC8 — `.trellis/spec/frontend/component-guidelines.md` is updated to
      describe the new FAB long-press contract, to stop forbidding it, and to
      record `LongPressRepeater` as the single mechanism, with the verification
      scan list adjusted to match.
- [ ] AC9 — `./gradlew :nga_phone_base_3.0:testDebugUnitTest` and
      `:lib_base_common:testDebugUnitTest` pass, and
      `./gradlew :nga_phone_base_3.0:assembleDebug` produces an APK.
- [ ] AC10 — The completion report hands over the APK path and the M1–M12
      checklist, and states that device confirmation is pending the developer's
      manual pass.

