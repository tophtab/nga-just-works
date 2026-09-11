# Design: Loading Usage Tips

Status: approved for implementation on 2026-09-12 (user: “确认吧。开干吧。”).

## Scope and component reuse

Use the existing `sp.phone.view.LoadingLayout` as the single legacy
spinner-plus-tip component. Keep `list_loading_view.xml` as the compatible
wrapper resource, with its existing `loading_view` ID, background, dimensions,
and margins. It should inflate the same `include_loading_view.xml` contents
used by the notification loader.

This covers initial thread/topic/search lists and recent notifications. Leave
operation dialogs, content-preserving SwipeRefreshLayout indicators, WebViews,
media placeholders, and Compose private-message loading unchanged.

## Catalog and presentation

Use Android string resources for the eight current-checkout tips plus the
conditional AI tip recorded in `research/loading-surfaces-and-copy.md`.
Keep copy screen-qualified, so one shared pool works across the covered loaders.
Examples:

- 在线帖子中，点击当前页码可回到本页顶部。
- 在线帖子中，长按当前页码可刷新本页。
- 在线帖子中，长按右下角的回复按钮可刷新本页。
- 板块主题列表中，长按右下角的发帖按钮可回到顶部并刷新列表。
- 表情面板中，长按表情即可在当前分类内拖动排序。
- AI 的服务地址、API Key 和模型可在“设置 → AI 设置”中配置。

Place a passive multiline TextView below the existing spinner. Use the current
semantic text color, sp size, wrap-content height, and horizontal padding.
Do not ellipsize complete instructions, add a ticker/animation, steal focus, or
force accessibility announcements.

## Selection and lifecycle

A small selector holds only the previous selection in application-process
memory and chooses from the currently eligible pool. Avoid the immediately
previous item when there is a choice. Empty/single-item pools remain safe.

The loading-view instance owns its current selection:

1. Bind to its host fragment's view lifecycle.
2. Select only when the host reaches RESUMED and the initial loader is visible.
3. Keep that selection when pausing/resuming or moving offscreen/onscreen during
   the same unfinished loading occasion.
4. Hide spinner and copy together through existing terminal callbacks.
5. A new loading-view occasion can select again. A hidden prefetch completion or
   already-ready page never consumes a tip or briefly shows one.

Use explicit lifecycle binding in `ArticleListFragment`,
`TopicSearchFragment`, and `RecentNotificationFragment` if needed.
Construction/attachment and `isShown()` alone are not sufficient in a retained
ViewPager. Do not start requests or alter page-request state from this widget.

Keep existing timing intact, including the pre-existing 300 ms thread success
transition in `ArticleListPresenter`; add no extra delay or display timer.

## AI availability

The verified AI implementation is in the sibling `feature/ai-summary`
worktree at commit `92a37022687c7b866d79df3c8e72400135f95343`.
Its real entry is `pref_ai_settings` in `res/xml/settings.xml`, targeting
`SettingsAiFragment`, with label `AI 设置`.

Catalog eligibility must derive from the actual local settings entry/destination
included in the build. It can inspect the bundled settings metadata once, without
opening the settings screen, network calls, persistent user options, or any
cross-worktree runtime dependency. Do not merely test whether tip text exists.

Current checkout: eight eligible tips. Once the verified AI destination is
integrated into a build: nine eligible tips. This task does not merge or
cherry-pick AI work. Revalidate both eligibility cases during integration.

## Compatibility and rollback

Keep legacy resource IDs, widget casts, layouts, notification transitions,
gesture handlers, and page-prefetch contracts compatible. Both this child and
the location child may modify `ArticleListFragment`; implement sequentially
or agree explicit file ownership.

Rollback removes the tip catalog/selection and TextView while retaining the
existing spinner and visibility transitions. No stored data migration is needed.

## Verification

Test selection and one-occasion state with plain JVM inputs: empty/single/multiple
pools, repeated lifecycle callbacks, hidden prefetch, terminal hide, new occasion,
and AI presence/absence. Do not create literal-copy snapshot tests.

Compile resources and run relevant existing page-refresh/prefetch tests plus
the parent Android quality gate. Static layout/lint review covers semantic
colors, readable multiline sizing, padding, and accessibility behavior.
