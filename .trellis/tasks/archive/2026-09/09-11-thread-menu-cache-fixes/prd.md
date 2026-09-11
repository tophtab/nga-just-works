# 帖子阅读菜单与缓存交互修复

## Goal

Make thread reading consistent by simplifying floor menus, matching cached-page
tab spacing to ordinary threads, and preserving the current-page cache action
after opening a full thread from a recent reply.

## Background

- The user requested all three changes together on 2026-09-11 and approved
  creating this task and entering planning.
- This is one bounded maintenance change to the existing Android thread reader,
  with three acceptance groups. The ordinary online reader is the reference.

## Requirements

- R1 (UI simplification): Remove `支持`, `反对`, `收藏`, and `查看签名` from
  the floor-level `更多` menu wherever those entries currently appear.
  The normal and single-reply variants are selected in
  `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java:153`;
  their resources are `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu.xml:11`
  and `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu_with_tid.xml:12`.
- R2 (layout defect): Cached threads must use the ordinary online page-tab
  distribution: one through five cached pages divide the available strip equally;
  more pages use the existing scrollable sizing. The online rule exists at
  `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:103`,
  but is missing from
  `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ArticleCacheActivity.java:58`.
- R3 (functional defect): Following `最近被喷` → a reply → `显示全部` must
  provide a usable `缓存本页` action in the full thread toolbar's `更多` menu.
  Full-thread caching must work without prior entry through a topic list.
  `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleSearchFragment.java:39`
  passes only the thread ID and title, while
  `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleTabFragment.java:297`
  hides caching without launch-time metadata and
  `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/ArticleListModel.java:119`
  rejects a save without it.

## Acceptance Criteria

- [x] AC1 / R1: None of the four removed entries appears in a floor's overflow
  menu in the normal reader, cached reader, or reply/detail entry path. The
  remaining floor actions keep their relative order and behavior.
- [x] AC2 / R2: One, two, three, four, and five cached pages use equal-width
  tabs; two pages each occupy half the strip. Six or more retain ordinary
  scrollable sizing. Sparse cached page numbers still select the correct files.
- [x] AC3 / R3: After a successful full-thread load reached through a recent
  notification and `显示全部`, `缓存本页` is visible and saving the selected
  page succeeds without requiring a visit to the board's topic list.
- [x] AC4 / R3: A newly cached thread appears with its title in the cache list,
  and its saved page can be reopened using the existing cache reader. Existing
  topic-list entry and previously saved caches remain compatible.
- [x] AC5 / R3: Missing or unusable page metadata must not produce a success
  message or an invalid cache description. Single-reply and author-filtered
  views must not become eligible for saving as full thread pages.

Acceptance was verified with offline metadata behavior tests and source/resource
tracing through the unchanged cache writer/reader. Device/UI playback was not
run per project policy; see `validation.md` for exact evidence and limits.

## Compatibility Constraints

- Keep the standalone floor voting controls, thread-level bookmarking, and
  remaining menu actions.
- Keep the cache directory/file format and the existing normal-reader refresh,
  prefetch, navigation, and page-index behavior.
- Reuse loaded thread data; the repair requires no extra NGA request.

## Scope Boundaries

- No reader redesign, Kotlin/Compose migration, API/transport changes, cache
  storage migration, cache sorting cleanup, or unrelated navigation repairs.
- Validation uses local builds, JVM tests, lint, and offline cache fixtures.
  Device operation and live service testing are not part of this task.
