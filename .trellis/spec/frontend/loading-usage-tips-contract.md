# Legacy Loading Usage Tips

## 1. Scope / Trigger

Use this contract when changing `LoadingLayout`, its bundled tip catalog,
`include_loading_view.xml` / `list_loading_view.xml`, or their fragment bindings.
It covers existing initial article/topic/search loading and recent notifications.
It does not extend to operation dialogs, content-preserving pull refresh,
WebView/media placeholders, or Compose private-message spinners.

Tips describe already reachable features in local Chinese resources. They are
passive instructions, with no remote copy, quotations, new gestures, or required
display duration. Location prefetch has different foreground requirements; see
the [author-location contract](../backend/author-profile-location-contract.md).

## 2. Signatures

```java
void LoadingLayout.bindToLifecycle(LifecycleOwner owner)
int LoadingTipState.update(boolean loadingVisible, boolean hostResumed,
        boolean visibleToUser, IntSupplier chooseTip)
void LoadingTipState.reset()
int LoadingTipSelector.next(List<Integer> eligibleTips)
List<Integer> LoadingTipCatalog.eligibleTips(boolean aiSettingsAvailable)
boolean LoadingTipCatalog.isAiSettingsEntry(String preferenceKey,
        String fragmentName, Predicate<String> isBundledFragment)
```

`LoadingTipState`, `LoadingTipSelector`, and `LoadingTipCatalog` have executing
host-JVM tests. `BundledLoadingTips` owns the process-level selector and reads
actual bundled settings metadata once on first foreground demand.

## 3. Contracts

### View lifetime and selection

- Bind `LoadingLayout` to `getViewLifecycleOwner()` after view-field binding:
  ButterKnife in `ArticleListFragment` / `TopicSearchFragment`, and
  `findViewById` in `RecentNotificationFragment`. A Fragment lifecycle can
  outlive its view.
- Selection requires the loader's own visibility, a RESUMED view owner,
  attachment, `isShown()`, and a visible window. Construction/attachment alone
  cannot distinguish a retained offscreen pager page from the foreground one.
- Select once per unfinished loading occasion. Repeated visibility, attachment,
  and resume callbacks must reuse the selected tip. Pausing or hiding an
  ancestor/window hides the text while preserving that occasion's selection.
- Hiding the loader itself ends the occasion, including when already detached.
  Destroying or replacing the bound view lifecycle removes its observer and
  resets view-owned selection. The next eligible loading occasion can choose
  again.
- Hidden prefetch never consumes a tip. A page completed before entering the
  foreground must not select or briefly display a tip on entry. Tips neither
  initiate requests nor change page loading/prefetch state.
- The selector remembers the last resource ID process-wide. If multiple tips
  are eligible, exclude that immediately previous ID. An empty pool returns
  resource ID `0`; a single-item pool safely returns its only item. Catalog
  entries are unique, nonzero resource IDs in immutable lists.
- Use existing completion/error visibility transitions. Preserve the existing
  300 ms article-success transition; add no ticker, timeout, pacing, or minimum
  tip duration.

### Layout and copy

- Keep `loading_view`, the shared spinner `progress`, wrapper dimensions,
  background, and 8 dp bottom margin compatible with their callers.
- Put passive multiline `loading_tip` text below the spinner: wrap-content
  height, 16 sp, semantic `text_color`, centered text, and horizontal padding.
  Do not ellipsize instructions, steal focus, or force announcements.
- `loading_tip_strings.xml` is the copy source. Screen qualifiers matter:

| Control / surface | Actual advertised behavior |
| --- | --- |
| Online article, current page tap | Return to this page's top |
| Online article, current page long press | Refresh this page |
| Online article, lower-right reply button long press | Refresh this page |
| Board topic list, compose button long press | Return to top and refresh the list |
| Online topic list, title tap | Return to top and refresh the list |
| Favorite-board card long press | Drag to reorder |
| Home category-tab long press | Drag to reorder; favorite boards stay first |
| Emoticon long press | Reorder within the current category |

The current-page and article reply-button refresh actions do not scroll to top
or change pages. Do not copy the board's different refresh behavior into their
instructions. Gesture ownership remains with the existing handlers documented
in [component guidelines](./component-guidelines.md).

### Optional AI guidance

The ninth tip describes endpoint, API Key, and model configuration under
`设置 → AI 设置`. It is eligible only when `R.xml.settings` actually includes
`android:key="pref_ai_settings"` with
`android:fragment="sp.phone.ui.fragment.SettingsAiFragment"`, and that class is
a loadable AndroidX Fragment. Load the class without initializing or creating
the screen. An absent/broken optional entry excludes only this tip.

Do not infer capability from the tip string, an AI model helper, or a sibling
worktree. The baseline branch has eight eligible tips; a build containing the
verified real settings entry has nine. Recheck both cases when integrating AI
work. There is no new user preference, network request, or persistent tip state.

## 4. Validation & Error Matrix

| State / input | Required outcome |
| --- | --- |
| Initial loader visible, owner RESUMED, attached and shown | Choose one tip |
| Same unfinished occasion receives repeated callbacks | Keep that selection |
| Owner paused / ancestor hidden / window hidden | Hide text; preserve selected occasion |
| Loader's own visibility becomes GONE or INVISIBLE | Clear the occasion and text |
| Background prefetch completes before becoming foreground | No selector call or tip flash |
| Lifecycle destroyed or replaced | Remove obsolete observer and reset state |
| Later visible loading occasion, multiple eligible tips | Avoid the previous resource ID |
| Empty / single-entry catalog | No tip / sole tip, without failure |
| AI key missing, wrong destination, or class absent | Eight-tip pool; do not advertise AI |
| Verified AI entry and Fragment present | Include the ninth instruction |
| Success / empty / error transition | Spinner and tip leave together through the existing host path |

## 5. Good / Base / Bad Cases

- **Good**: an initial load selects one tip, an app pause/resume keeps it, and
  normal completion hides it. A later load avoids the most recent selection.
- **Base**: the current build has no AI settings destination, so it selects only
  the eight instructions for available features.
- **Bad**: selecting in the constructor/onAttach, rotating every few seconds,
  keeping ready content covered to make text readable, or advertising an
  unmerged AI feature merely because its resource string exists.

## 6. Tests Required

- `LoadingTipSelectorTest`: empty/single/multiple pools, immediate-repeat
  avoidance across views and pool changes, and resource-ID identity.
- `LoadingTipStateTest`: background exclusion, once-per-occasion selection,
  pause/ancestor-hide reuse, own-hide reset, destruction/reset, and empty pools.
- `LoadingTipCatalogTest`: immutable eight/nine-entry pools and the actual
  key/destination/class eligibility decision.
- `TopicPagePrefetchContractTest.retainedPageLoadingTipsUseThePageViewLifecycle`:
  pin the retained article page's view-owner binding after field binding.
- Compile resources/Java/Kotlin, inspect app and all-module lint XML, and keep
  existing prefetch/current-page/FAB/title-refresh regressions green. Review
  multiline/sp/semantic-color layout attributes; offline checks do not prove a
  device's rendered layout. Device operations follow the Android quality policy.

## 7. Wrong vs Correct

Wrong in a retained article page:

```java
mLoadingView.bindToLifecycle(this); // Fragment can outlive the bound view.
```

Correct after fields have been bound:

```java
mViewBindings = ButterKnife.bind(this, view);
mLoadingView.bindToLifecycle(getViewLifecycleOwner());
```

The widget owns selection/visibility only. Keep foreground loading hints
independent of supplementary location queries, which are also required for
offscreen pages delivered by existing prefetch.
