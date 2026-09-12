# Implementation results

## Product changes

- `ArticleListAdapter.java` now uses the page-owned `ArticleBodyViews` owner.
  Fresh responses reuse applicable row WebViews within the accepted query,
  generation, source, page-size, owner, effective-page and resolved-topic context.
  Binding still refreshes HTML and image metadata; the existing `LocalWebView`
  equal-HTML guard decides whether Chromium actually loads another document.
  Attachments use the view's actual parent, preserving the container's static
  XML children. Removed/incompatible views and view destruction release resources.
- `ArticleBodyViews.java` provides lazy, variable-length resource ownership.
  Positive topic/PID identities and the known PID-zero original post survive
  reordering; missing or duplicate identities are conservative fresh allocations.
  It does not restore the former fixed 20-row limit or reuse views across a
  retired page context.
- Adapter data replacement retains the valid author snapshot. Null reset still
  clears it, and epoch/TTL checks remain authoritative. Author payloads retain
  generation/UID/holder validation and bind only details. The final detail text
  is compared with the holder's actual text before assignment, including after
  snapshot invalidation; no separate snapshot-comparison cache can miss a clear.
- `AuthorLocationPage.java` now owns the production delivery algorithm;
  `AuthorLocationService.Page` retains Android lifecycle, LiveData and session
  scheduling. Pending replacement sequence and active output generation are
  independent, preserving invalidation delivery through the deferred gap.
  The first synchronous new-consumer snapshot retires the previous consumer
  before dispatch, preserving cached text and pruning removed queued authors.
  READY replay retains its initial online/cache-only intent. Weak delivery and
  settlement ownership avoid an app-repository path that retains the UI.
- A main-approved package-private `AuthorLocationRepository.subscribe`
  registration hook installs the Page's handle before initial publication.
  A synchronous close/null output can therefore retire it before any request
  dispatch. Only still-active online consumers enter enqueue/dispatch afterward.
  The public subscription API, transport, parser, cache TTLs, 500 ms pacing and
  session-stop policy are unchanged.

## Files

Product:

- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleBodyViews.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationService.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationPage.java`
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/AuthorLocationRepository.java`

Tests:

- `nga_phone_base_3.0/src/test/java/sp/phone/profile/AuthorLocationPageTest.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleAuthorLocationContractTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/fragment/ArticleReaderUiContractTest.kt`
- `nga_phone_base_3.0/src/test/java/sp/phone/ui/adapter/ArticleBodyViewsTest.java`
  (written by the separately assigned body-test implementer)

## Focused verification

Final focused command passed, including assembly after the registration-hook
correction:

```sh
./gradlew :nga_phone_base_3.0:testDebugUnitTest \
  --tests sp.phone.ui.adapter.ArticleBodyViewsTest \
  --tests sp.phone.profile.AuthorLocationPageTest \
  --tests sp.phone.profile.AuthorLocationRepositoryTest \
  --tests sp.phone.ui.fragment.ArticleAuthorLocationContractTest \
  --tests sp.phone.ui.fragment.ArticleReaderUiContractTest \
  --tests sp.phone.ui.fragment.ArticlePageRefreshContractTest \
  --tests sp.phone.mvp.presenter.ArticlePageRequestStateTest \
  :nga_phone_base_3.0:assembleDebug --console=plain
```

Log: `/tmp/reader-refresh-focused.log`. Result: BUILD SUCCESSFUL in 19 seconds,
302 actionable tasks (14 executed, 288 up-to-date). All seven expected JUnit XML
reports were inspected: **103 tests, zero failures/errors/skips**.

| Suite | Tests |
| --- | ---: |
| ArticleBodyViewsTest | 25 |
| AuthorLocationPageTest | 18 |
| AuthorLocationRepositoryTest | 30 |
| ArticleAuthorLocationContractTest | 8 |
| ArticleReaderUiContractTest | 7 |
| ArticlePageRefreshContractTest | 7 |
| ArticlePageRequestStateTest | 8 |

`git diff --check` also passed. An earlier app-only assembly passed in
26 seconds (`/tmp/reader-refresh-assemble.log`); the combined command above
validates the newer registration-hook implementation too.

## Handoff and limits

Implementation and Gradle ownership released at **2026-09-12 13:04:22 UTC**.
No commit or push was performed by this agent. Main owns independent review,
the final repository-wide JVM/all-module lint gate, specs, commit, finish-work
and push. No unrelated product changes were made.

Device/ADB, installation, connected tests and live NGA requests were not run
per project policy. Host behavior tests and Android wiring/build checks
establish resource/delivery behavior, not a measured frame-by-frame smoothness
comparison. Real HTML changes and conservatively unmatched identities still
require loading their content.
