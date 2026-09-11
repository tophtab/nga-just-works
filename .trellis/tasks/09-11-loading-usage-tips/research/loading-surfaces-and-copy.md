# Research: Loading surfaces and verified usage-tip copy

- Query: Locate the removed loading-copy surfaces; define a small local-tips integration and selection cadence; verify current gestures and AI settings before drafting Chinese copy.
- Scope: Internal source, the sibling AI worktree's settings sources, and archived project evidence. Planning only; no product edits, Git operations, builds, devices, or network requests.
- Date: 2026-09-11

## Findings

### Recommended scope

Add one locally bundled usage tip beneath the existing spinner on the legacy initial-loading surfaces. Keep the tip stable for that loading occasion; select a different tip on a later visible occasion, with no timer, artificial delay, network fetch, preference, or new gesture. Use context-qualified copy so a tip about an online thread cannot be mistaken for a notification-page control.

The AI example is verified in the sibling `feature/ai-summary` worktree, but its settings entry is absent from this checkout. Include the verified AI tip only when that implementation is part of the build being shipped. This integration dependency does not block the other eight tips and is not permission to merge the branch.

### Files found and existing surfaces

| File / anchor | Role and implication |
| --- | --- |
| `release-notes/5.5.0.md:9` | Explicitly records removal of random loading sayings. |
| `.trellis/tasks/archive/2026-08/08-10-release-5-5-0/research/release-evidence.md:31` | Archived evidence attributes that removal to `9bdbbc0c`; no further history lookup is required. |
| `nga_phone_base_3.0/src/main/res/layout/list_loading_view.xml:2` | Full-size vertical `LinearLayout`, ID `loading_view`, themed `window_background`, centered spinner ID `progress`. Shared list-loading integration point. |
| `nga_phone_base_3.0/src/main/res/layout/fragment_article_list.xml:29` | Includes the shared loader above the thread content. This is the active `ArticleListFragment` layout (`ArticleListFragment.java:240`). |
| `nga_phone_base_3.0/src/main/res/layout/fragment_topic_list.xml:43`, `fragment_topic_list_board.xml:57` | Shared loader in topic/search and board lists. The additional `fragment_article_list_reply.xml:45` include should retain resource compatibility; its current runtime reachability was not separately established. |
| `nga_phone_base_3.0/src/main/java/sp/phone/view/LoadingLayout.java:23` | Existing reusable vertical, centered widget inflating `include_loading_view`. Currently no copy or selection logic. |
| `nga_phone_base_3.0/src/main/res/layout/include_loading_view.xml:2` | Merge resource with spinner only; suitable shared markup for a tip `TextView`. |
| `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/RecentNotificationFragment.java:80` | Binds `LoadingLayout` in `fragment_recent_reply.xml`; initial notification load is a relevant existing full-screen loading surface. |
| `nga_phone_base_3.0/src/main/java/sp/phone/util/ActivityUtils.java:80` | Legacy `noticeSaying` names remain, but default calls pass empty title/message. Explicit progress messages still exist. These are operation dialogs, not the list-loading widgets. |
| `lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/PullRefreshLazyColumn.kt:48` | Separate Compose empty-list loading branch, used by private-message screens (`lib_bu_message/.../MessageListActivity.kt:66`, `.../detail/MessageDetailActivity.kt:68`). Current implementation is spinner-only. No evidence found that it was a former quotation surface; leave it outside this focused change. |

Do not inject tips into upload/submission progress dialogs, WebViews, image placeholders, or pull-refresh indicators. In particular, preserve explicit `noticeSaying("正在提交...", ...)` semantics instead of replacing operation status with feature copy.

### Lifecycle and minimal integration

1. Reuse `LoadingLayout` for both legacy loaders: retain `list_loading_view.xml` as the wrapper resource with the same `loading_view` ID, background, dimensions, and bottom margin; let the widget inflate one shared spinner-plus-tip layout. This avoids separate catalogs or binding rules for notifications and lists.
2. Keep a small application-local string-resource catalog and selector. Store only the previously selected index in process memory if avoiding immediate repeats; keep the selected tip on its loading-view instance. No account data or persistent storage is needed.
3. Select once when the hosting **view lifecycle is RESUMED and its initial loading overlay is still visible**, not in a constructor, `onCreateView`, `onViewCreated`, or background prefetch callback. Bind via the owning fragments' view lifecycle (`ArticleListFragment`, `TopicSearchFragment`, `RecentNotificationFragment`). A return from background or an offscreen/onscreen transition during the same unfinished overlay must keep its already selected tip.
4. Treat an offscreen page that finishes prefetching as having no visible loading occasion. Do not advance the shared selection merely because its view inflated. If a page becomes selected while prefetch is pending, select then; if data is ready, do not flash a tip.
5. Existing terminal callbacks keep hiding the entire loader, so both spinner and copy disappear together. Do not introduce a separate delayed-hide path or change request state. Existing manual refresh retains content and uses `SwipeRefreshLayout`; it must not resurrect the initial-loading overlay just to display tips.

Evidence for the lifecycle rules:

- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:123` retains two offscreen pages; `.../ui/adapter/ArticlePagerAdapter.java:32` uses `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT`. A created/attached view or `isShown()` alone is not proof that its Pager page is selected.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:209` observes prefetch candidates; `.../mvp/presenter/ArticleListPresenter.java:113` renders and hides the loading overlay on background prefetch success, while `:393` starts/promotes foreground loading on resume.
- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/presenter/ArticleListPresenter.java:54` hides on foreground failure; `:78` already has a 300 ms successful-load hide delay. Preserve that existing behavior; the tips must add no extra waiting.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:349` hides the overlay; `.../TopicSearchFragment.java:180` hides it when refreshing ends and `:225` exposes content; `.../RecentNotificationFragment.java:121` and `:136` handle error/empty/success termination.

### Exact gesture semantics

| Trigger | Implemented behavior | Source |
| --- | --- | --- |
| Short tap on selected online-thread page number | Expand app bar and scroll that page to its top; no refresh. | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:135`, `:153`; `.../ArticleListFragment.java:288` |
| Long press on selected online-thread page number | Refresh the current page once at the platform long-press threshold, then attempt every 5 seconds while held; skip an already refreshing page. No explicit scroll-to-top or page switch. | `ArticleTabFragment.java:76`, `:136`, `:161` at the same directory; `lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/TabLayoutEx.java:93` |
| Long press on another page number | Not accepted as current-page refresh; normal tap navigation remains available. | `lib_base_common/src/main/java/gov/anzong/androidnga/base/widget/TabLayoutEx.java:93`; `.../LongPressRepeater.java:110` |
| Short tap on thread FAB | Opens reply flow; sign-in is checked. Its accessible name is reply, even though the icon depicts a pencil. | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:174`; `nga_phone_base_3.0/src/main/res/layout/fragment_article_tab.xml:49` |
| Long press on thread FAB | Same current-page refresh as the selected page number, repeated every 5 seconds while held. | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:139` |
| Short tap on board-list FAB | Opens new-topic composition. | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicListFragment.java:114` |
| Long press on board-list FAB | Scroll to list top, expand app bar, and reload page 1; repeat every 5 seconds while held, subject to existing enabled/refreshing guards. | `TopicListFragment.java:40`, `:80`, `:102`; `TopicSearchFragment.java:200` in the same directory |

`LongPressRepeater.java:39` and `:110` (under `lib_base_common/.../base/widget/`) stop repeats on release/cancel/detach/failed condition and consume an accepted long press. Tips must not promise a new request exactly every 5 seconds while an old one is still in flight. Cached-thread controls do not have the same online behavior; qualify thread tips with “在线帖子”.

### Verified Chinese tip pool

The first eight strings are verified in the current checkout; the ninth is verified in the sibling AI worktree and depends on its integration. Keep complete instructions readable rather than using a generic “许多地方都有隐藏功能” message that leaves the action unspecified.

| ID | Copy | Source anchor |
| --- | --- | --- |
| `thread_page_top` | 在线帖子中，点击当前页码可回到本页顶部。 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:135` |
| `thread_page_refresh` | 在线帖子中，长按当前页码可刷新本页。 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:136` |
| `thread_reply_refresh` | 在线帖子中，长按右下角的回复按钮可刷新本页。 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:139`; `nga_phone_base_3.0/src/main/res/layout/fragment_article_tab.xml:47` |
| `board_compose_refresh` | 板块主题列表中，长按右下角的发帖按钮可回到顶部并刷新列表。 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicListFragment.java:80`; `.../TopicSearchFragment.java:200` |
| `topic_title_refresh` | 在线主题列表中，点击顶部标题可回到顶部并刷新列表。 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicSearchFragment.java:187`, `:200` |
| `favorite_board_reorder` | 首页“收藏板块”里，长按板块卡片即可拖动排序。 | `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt:326` |
| `home_tab_reorder` | 首页顶部的分类标签可以长按拖动；“收藏板块”固定在最前。 | `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt:98`; `.trellis/spec/frontend/component-guidelines.md:329` |
| `emoticon_reorder` | 表情面板中，长按表情即可在当前分类内拖动排序。 | `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/EmoticonParentAdapter.java:50` |
| `ai_settings` (conditional) | AI 的服务地址、API Key 和模型可在“设置 → AI 设置”中配置。 | Sibling `/home/toph/nga-just-works-ai-summary/nga_phone_base_3.0/src/main/res/xml/settings.xml:150`; `.../res/xml/settings_ai.xml:4`; `.../res/values/strings_ai_settings.xml:3` |

All tips explicitly name their target screen. A single shared pool is therefore sufficient; per-screen catalogs are optional and would add wiring without being needed for correctness. The first three online-thread tips preserve the user's requested examples with the actual UI term “回复按钮” rather than ambiguous “编辑按钮”.

### AI entry-point audit

`nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/SettingsActivity.java:34` creates `SettingsFragment`; `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/SettingsFragment.java:41` loads `res/xml/settings.xml`, and `:172` routes declared child fragments. Neither that preference resource nor `res/xml/settings_lab.xml` declares an AI destination.

The search `\bai\b|openai|anthropic|deepseek|硅基|智能|总结|api[_ -]?(key|token)` over this checkout's app/library Java, Kotlin, XML, and Gradle source (excluding build output) found no matching product feature. AI material in `.trellis/tasks/07-25-nga-android-advanced/research/nga-harmony-ai-context.md` documents external ArkTS source and an Android proposal; it alone is not proof of a shipped feature.

The main session then identified the actual implementation in `/home/toph/nga-just-works-ai-summary`, branch `feature/ai-summary`, HEAD `92a37022687c7b866d79df3c8e72400135f95343` (branch/HEAD supplied by the main session; this researcher performed no Git operation). Read-only verification of that worktree established:

- `nga_phone_base_3.0/src/main/res/xml/settings.xml:150` declares `sp.phone.ui.fragment.SettingsAiFragment`, key `pref_ai_settings`, and `@string/ai_settings_title`.
- `nga_phone_base_3.0/src/main/res/values/strings_ai_settings.xml:3` resolves the exact label to `AI 设置`; `:4`, `:6`, and `:12` label API service address, API Key, and model name.
- `nga_phone_base_3.0/src/main/res/xml/settings_ai.xml:4`, `:12`, `:18`, and `:24` declare those three configuration fields and connection testing.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/SettingsAiFragment.java:76` opens the settings fragment; `:86` inflates its preferences and binds field editors/test action. `:114` calls `saveConfiguration()` from the save menu. This verifies a concrete settings interface, not just a planned string resource.

Eligibility rule: add `ai_settings` to the selectable pool only when the build actually includes the real `pref_ai_settings` entry and destination. Revalidate after integration; do not show it merely because a research artifact or unused string exists. Do not add cross-worktree runtime paths, network probes, or merge/cherry-pick work to this tips task. No AI request or connection test was executed during this audit.

### Offline validation and presentation

- Put copy in Android string resources; keep a normal multiline `TextView`, `sp` text sizing, `wrap_content` height, horizontal padding, and enough width for large fonts/landscape. Do not impose a two-line truncation or marquee.
- Use semantic `@color/text_color` (`nga_phone_base_3.0/src/main/res/values/colors.xml:4`, `values-night/colors.xml:4`) against the existing loader background. Do not use the much dimmer `text_color_disabled` merely because the copy is secondary. Keep existing light/dark/solid-background behavior.
- Treat tips as passive text. No automatic focus, forced announcement, live-region cycling, or duplicate content description; the text should be discoverable by accessibility services without disturbing navigation. No new animation is needed.
- Meaningful selector/lifecycle checks: empty/single-item catalogs do not crash; multiple entries avoid immediate repeats; one loading occasion stays stable across repeated resume/visibility callbacks; hidden/preloaded pages do not advance selection; success/error and already-ready pages show no residual tip; a new view/loading occasion can select again; AI copy is ineligible without its actual settings entry. Prefer testing this small state policy over snapshot tests of literal copy.
- Reuse existing `ArticlePageRefreshContractTest`, `TopicListTitleRefreshContractTest`, `ArticlePageRequestStateTest`, and `ArticlePagePrefetchPlannerTest` when implementation touches their wiring. Keep resource compilation, app JVM tests, and lint as the offline gate; inspect Error/Fatal lint findings, since Gradle exit status alone is insufficient. A resource/code compile can validate this change without packaging an APK.
- The project-wide Android quality gate may require broader module lint/JVM checks during implementation. No checks were run in this research-only pass. Devices/ADB, instrumentation execution, live NGA calls, and APK packaging are outside this dispatch.

### Related specs

- `.trellis/spec/frontend/component-guidelines.md:418`: direct FAB identity/placement; `:478`: single repeat helper; `:505`: page reselect/hold; `:530`: board/article FAB differences; `:107`: Compose Material 2 boundary if scope later expands.
- `.trellis/spec/backend/thread-page-prefetch-contract.md:39`: offscreen retention, foreground promotion, and silent background behavior.
- `.trellis/spec/backend/android-quality-guidelines.md:8`: device operations require explicit opt-in; `:155`: validation and lint-report requirements.
- `.trellis/workflow.md`: planning artifacts and research persistence; this report does not activate implementation.

### External references / versions

No new external dependency or external documentation is needed. Current repository code uses Android Views/XML for the proposed surfaces, legacy `ViewPager` with resume-only-current-page behavior, and Material 2 for the separate Compose widgets. The project's declared Android floor/target are API 29/35 per the quality contract. Archived release notes provide historical evidence; no Git history operation was performed.

## Caveats / Not Found

- AI settings exist in the verified sibling `feature/ai-summary` worktree, not this checkout. Preserve the integration-dependent eligibility of its tip; the research does not authorize merging that branch.
- The exact removed-sayings diff was not inspected; release notes, archived commit evidence, and remaining legacy names are sufficient for this planning recommendation. No evidence establishes the Compose private-message spinner as a former quotation surface.
- The existing 300 ms thread success delay is pre-existing; do not accidentally claim tips remove it or add a second delay.
- Source anchors describe the shared workspace on 2026-09-11. Another task is researching/changing thread compatibility; re-check affected lifecycle anchors before implementation without changing its request/parse contracts.
