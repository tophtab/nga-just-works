# Reader behavior audit

Inspected main at 1a8413d9 on 2026-09-30. Source inspection only: no device reproduction, live NGA traffic, or product edits. The public GitHub script explicitly linked by the user was fetched for comparison.

## R1: web entry

- nga_phone_base_3.0/src/main/AndroidManifest.xml:121 declares exported ArticleListActivity for HTTP/HTTPS read.php on NGA hosts; no custom protocol filter.
- ArticleListActivity.java:46 reads tid/pid/authorid/page/searchpost. A nonzero pid/searchPost selects ArticleSearchFragment.
- A myscheme resource containing ngacn is not a registered protocol.
- Implicit custom-scheme intents may resolve to multiple apps, depending on browser/default behavior. An Intent explicitly targeting the official package cannot select this package merely through added filters.
- Android 12+ normally sends unverified web links to the browser unless user association permits otherwise. The project does not control NGA assetlinks.json.
- Follow-up evidence resolved the browser/URI: Via supplied nga://openType=2?page=1&tid=47649154& and saved Android JS supports type 2/5 without an explicit package restriction. See followup-script-links-editor.md; actual device chooser behavior remains unverified.

## R2: endpoint

Reference: https://github.com/DelCrona/NGA_checkInfo/blob/main/checkInfo.js, getUserInfo at lines 198–221 of fetched main.
The script GETs /nuke.php?func=ucp&uid=<uid>, parses an inline __UCPUSER object and uses ipLoc.
ProfileLocationTransport.java:61 uses the same endpoint and data. It explicitly supplies captured Cookie, UA, X-User-Agent and Referer. Browser session/environment can differ from the app.
Manual profile loading retains its existing JSON route.
The displayed value is the author's latest publicly observed profile location, not the IP/location of a historical reply.
The script uses DOMParser and regex; missing __UCPUSER may leave its Promise unresolved. Its implementation does not establish a better retry policy.

## R2: queue and cache

- ArticleAuthorIds.fromPage takes distinct positive nonanonymous authors from every delivered row, not only visible holders.
- Accepted online current and prefetched pages submit work. Current-plus-two prefetch excludes final-page prefetch; roughly 60 authors is an estimate, not a queue cap.
- AuthorLocationRepository.java:23/:275 shares one physical request across consumers. First eligible request may start immediately; every terminal callback enforces a new 500 ms pause.
- ProfileLocationTransport: 10 s connect/read, 20 s call timeout, 256 KiB response bound, strict GBK, no redirects or connection retry. Prevents OkHttp 503 + Retry-After: 0 follow-up.
- Cache key: normalized origin + viewing-account UID + author UID. Success AND valid empty: 24 h. Ordinary failure: 10 min. Store: at most 1,000 records / 1 MiB.
- Disk restore precedes dispatch. Saved pages and retained-view replay are cache-only. TTL expiry does not itself schedule a request.
- Ordinary I/O, timeout, located-but-malformed profile object, invalid location or oversized/undecodable response: no immediate retry; cool down that author and continue others after pacing.
- 429: pause origin/account at least 30 min or longer valid Retry-After; expiry alone does not resume traffic.
- 503, other 300–499, rejected/mismatched profile or missing/unrecognized __UCPUSER: stop the captured session in memory for the process lifetime. Even empty 503 stops all remaining work. Same-session refresh does not clear the stop.
- Session stops include origin/account credentials/UA; persisted 429 pauses are distinct. Do not propose identity switching as stop recovery.
- Empty ipLoc on a valid profile is cached as a valid empty for 24 h, not retried as a failure.
- Missing/unrecognized profile causes a conservative local session stop, which does not prove a server challenge.
- No runtime evidence identifies the user's specific intermittent failure.

## R2: exit behavior

- ArticleListFragment.java:405 closes the location consumer in onDestroyView.
- AuthorLocationService.Page closes only on owner destruction; no onStop suspension.
- AuthorLocationRepository.Subscription.close (:102) removes the subscriber and prunes unsent authors only when no other online consumer needs them.
- Closing a page does NOT cancel inFlight; already-sent work may finish into valid cache. AuthorLocationRepositoryTest.java:500 explicitly pins this.
- Back navigation that destroys the thread clears remaining unsent work. Opening another Activity/thread/browser/profile or backgrounding can retain the view and continue old work.
- Same-thread offscreen pages remain intentional online consumers. Child Fragment RESUMED alone is the wrong ownership boundary for preserving prefetch.
- Account/session invalidation cancels the physical call and holds the slot until its terminal callback. It is a separate lifecycle path.

## R3: error messages

- ArticleFailure.kt:14 maps ACCESS to “站点要求访问验证，请稍后手动重试”.
- classifyHttp (:27) includes both 403 and every 3xx; bodyText (:38) includes any body starting with <, even an HTTP 200 deletion/error HTML page.
- knownMessage (:70) also recognizes validation/access words; nonexistent/no-permission messages become BUSINESS and lose their detail in a fixed toast.
- ArticleByteClient.kt:25 checks HTTP status before decoding. A 403 body is not currently passed to the parser.
- The access toast includes 403 but is not equivalent to it. Browser deletion output does not establish the native request's status/body/account.
- Proposed wording: real 403 -> “无法访问帖子（HTTP 403）”; explicit deleted/missing -> accurate site cause; actual validation -> validation message; unknown HTML -> neutral unavailability.
- Preserve stop/no-fallback behavior when improving wording; HTML reclassification must not authorize alternate requests.

## R4: reply navigation

- TopicSearchFragment.java:280 forwards pid/authorId/searchPost for reply searches.
- ArticleSearchFragment.java:40 -> ArticleListFragment.java:307 -> ArticleNavigation.showAll.
- ArticlePage.kt:123/:128 keeps resolved tid/title/description but resets page=1 and loses reply location.
- Keeping pid as a query parameter would select a lookup window again, not the full topic. Navigation target must be separate from filtering parameters.
- ArticleAnchor (:104) matches pid before actual floor; ArticlePagingInfo.candidatePage (:58) only estimates trustworthy full-thread layouts.
- ArticleListFragment.consumePendingAnchor (:330) checks actual row, generation and current data before scrolling.
- Search-result page is not the full-thread page. Compatibility source page sizes may differ from 20; missing/deleted targets must degrade explicitly.

## R5: emoticons

- EmoticonUtils.java:60 has 闪光 -> ac43.png; :65 has 赞同 -> ac42.png.
- EmoticonChildAdapter.java:96–127 obtains both preview and outgoing code from the same reordered entry.
- TopicPostPresenter.java:40 inserts the code with a preview image.
- Active rendering: ArticleConvertFactory.java:222 -> HtmlConvertFactory -> ForumDecoder -> ForumEmoticonDecoder.
- ForumEmoticonDecoder.java:16–32 instead maps 赞同 -> ac43.png and 闪光 -> ac42.png.
- Legacy StringUtils.buildEmoticonImage (:504) agrees with the picker.
- Static comparison of six categories / 238 picker entries found exactly these two name/file differences.
- Confirmed source defect consistent with the report; not a reproduction of the user's exact chosen emoji. Other token or asset-specific problems remain unverified.
- Use one category+name mapping to prevent table drift; preserve filename-based custom order. Do not swap asset bytes or modify published posts.
