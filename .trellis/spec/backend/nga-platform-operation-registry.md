# NGA Platform Operation Registry

## Registry Contract

The original-operation tables describe the untouched Justwen snapshot at
`5d807617f8058950f7ea81dda405e38fb0cc37ec`. Their source anchors are relative to
`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-Justwen`. Every networked
record is `original-source-observed`; none is live-verified or backed by a
meaningful original operation test/fixture. Later source-derived additions are
listed separately with their own commit and current-fork contract.

Unless a row says otherwise, requests use the selected NGA base host, the
shared browser UA and active-account Cookie injection, and scalar responses
decoded as GBK. Those are original behaviors, not recommendations. Apply
[NGA Platform Access Rules](./nga-platform-access-rules.md) before migrating an
operation and use [Network Foundation Contract](./network-foundation-contract.md)
for transport/session signatures.

Delta is a `current-fork-delta` label seeded from the 2026-07-26 worktree and
updated by later task-owned contracts where stated. It does not change the
original contract. An `unchanged` operation-owner label does not cancel a
shared transport delta. The current fork's
`RetrofitHelper.createOkHttpClientBuilder()` still sends
`X-User-Agent: Nga_Official`, as required by the network foundation contract;
the earlier registry claim that this header had been removed was stale.

## Authentication And Session

| Operation | Original request/session/encoding | Original response and local effect | Evidence | Delta |
| --- | --- | --- | --- | --- |
| `AUTH.WEB_LOGIN` | WebView GET `https://ngabbs.com/nuke.php?__lib=login&__act=account&login`; JavaScript/open windows; unrestricted navigation. | Exact login URL plus JS-confirm text `登录成功 是否返回首页`, or activity finish, triggers Cookie polling. Substring parser requires `ngaPassportUid`, `ngaPassportCid`, and double-GBK-decoded `ngaPassportUrlencodedUname`; `UserManager.addUser` persists. No typed HTTP/challenge failure. | `lib_bu_account/.../login/LoginActivity.kt:20-92`; `.../LoginViewModel.kt:13-77` | `restored`: the current login Activity and ViewModel are source-equivalent to the pinned Justwen files apart from final newlines. |
| `SESSION.SELECT_ACCOUNT` | Local ordered Room users and preference active index; Cookie wire string is `ngaPassportUid=<uid>; ngaPassportCid=<cid>`. | Add/update/remove/select changes the process-global active user. Login add does not explicitly select the new record; removal does not clear Web Cookies. | `lib_bu_account/.../UserManager.kt:12-149`; `nga_phone_base_3.0/.../NgaClientApp.java:101-105` | `modified`: fork adds explicit add-and-select. |

The dormant Retrofit `getByForum(...)`, `login(...)`, CAPTCHA-image,
`getAuthCodeService()`, and cookie-less `getDefault()` methods have no pinned
production caller and do not define original operations
(`RetrofitService.java:29-30,46-66`, `RetrofitHelper.java:159-173`).

## Reads And Preferences

| Operation | Original method, path, and fields | Parser, effect, and failure | Evidence | Delta |
| --- | --- | --- | --- | --- |
| `BOARD.CATEGORIES` | GET `app_api.php?__lib=home&__act=category`. | Fastjson `ForumsListBean`; caches raw response; exception returns null. | `nga_phone_base_3.0/.../compose/board/ForumBoardRepository.kt:15-24,94-125` | `modified`: wire unchanged; fork changes local bookmark persistence in same file. |
| `TOPIC.LIST` | GET `/thread.php`; optional `authorid`, `searchpost`, `favor`, `content`, GBK `author`, `stid|fid`, UTF-8 `key`, `fidgroup`; `page`, `lite=js`, `noprefix`; optional recommendation ordering. | `TopicConvertFactory`; `ErrorConvertFactory` for null/site errors. Multi-page aggregation sends sequential reads. | `nga_phone_base_3.0/.../mvp/model/TopicListModel.java:130-192,220-265` | `unchanged` |
| `THREAD.PAGE` | GET `/read.php?page&__output=8&noprefix&v2` plus optional `tid`, `pid`, `authorid`; caller may override Cookie header. | `ArticleConvertFactory`; site/parser/network errors to callback. Server parse failure automatically retries with next account, then may open WebView. | `nga_phone_base_3.0/.../mvp/model/ArticleListModel.java:49-113`; `.../presenter/ArticleListPresenter.java:83-142` | `modified`: ordinary wire fields and the normal converter remain. The compatibility-enabled chain uses operation-local bytes and a fixed account/origin; the default-off legacy chain retains its transport with a count-before-next-Cookie guard. Reader generation, explicit query/page metadata and ordinary-only non-final prefetch are governed by the [reader](./thread-detail-compat-contract.md) and [prefetch](./thread-page-prefetch-contract.md) contracts. The [AI profile collector](./ai-summary-contract.md) separately uses bounded page-1 GETs to project verified original bodies, without renderer logging, account rotation, or WebView fallback. |
| `BOARD.SEARCH` | Cleartext GET `http://bbs.nga.cn/forum.php?&__output=8&key=<GBK>`. | Parses `data.0.fid/name`; all failures become null. | `nga_phone_base_3.0/.../task/SearchBoardTask.java:18-57` | `unchanged` |
| `USER.PROFILE` | GET `nuke.php?__lib=ucp&__act=get&lite=js&noprefix&uid|username`; profile Referer; username GBK encoded. | Removes JS/comments and repairs numeric tokens; parses profile; raw parse failure logged; active avatar URL updated locally. | `nga_phone_base_3.0/.../task/JsonProfileLoadTask.java:47-114`; `.../activity/ProfileActivity.java:125-151,511-515` | `modified`: the manual profile reader retains its JSON route and `ProfileEnvelopeParser` normalization without raw parse-failure logging. On `experiment/auto-ip-query`, delivered online thread pages supplement latest public `ipLoc` using GET `/nuke.php?func=ucp&uid=<author>`, the same Web profile Referer, and the GBK HTML's `__UCPUSER` object. Its dedicated bounded transport retains explicit identity headers, account cache, one call in flight, an app-wide 500 ms pause after completion, and a body-independent HTTP 503 stop. The `main` reader keeps automatic enrichment disconnected. See [the author-location contract](./author-profile-location-contract.md). |
| `FILTER.GET_REMOTE` | POST `nuke.php`; form `__lib=ucp`, `__act=get_block_word`, `__output=8`, active `uid`; profile Referer. | Fastjson `data` lists or `error.0`; missing account fails locally. | `nga_phone_base_3.0/.../compose/filter/FilterWordModel.kt:107-121` | `unchanged` |
| `FILTER.SET_REMOTE` | POST `nuke.php`; form `__lib=ucp`, `__act=set_block_word`, `__output=8`, GBK URL-encoded CRLF `data`; hard-coded Host/Origin/length/charset headers. | Fastjson `data.0` or `error.0`; encoded user/word list logged. Mutation result is unknown after transport loss. | `nga_phone_base_3.0/.../compose/filter/FilterWordModel.kt:53-104` | `unchanged` |
| `POST.TOPIC_CATEGORY` | GET `nuke.php`; `__lib=topic_key`, `__act=get`, `fid`, `__output=8`. | Parses `data.0` category labels; exception to callback error. | `nga_phone_base_3.0/.../mvp/model/TopicPostModel.java:118-149` | `unchanged` |

## August Source-Derived Thread Compatibility

This addition is observed in Justwen commit
`2becba2acc3f6c85340424cd09bb03fa7d759db0`; it is not attributed to the July
snapshot above. Upstream's parser demonstration depends on an untracked
`tem.json` and prints output without assertions. The fork's synthetic/fake
tests exercise its own adapter contract, not real-service availability.

| Operation | Source request | Source mapping and limits | Evidence at the August commit | Current-fork integration |
| --- | --- | --- | --- | --- |
| `THREAD.PAGE.APP_COMPAT` | POST `/app_api.php?__lib=post&__act=list`; form `page` plus nonzero `tid`, `pid`, `authorid`; selected origin and identity headers. No new `searchpost` or `perPage` request field. | `ThreadAppBean` / `ThreadInfoAppParse` map body, subject, author, client, vote markup and basic thread metadata into the native reader. Upstream does not project standalone attachment/hot-reply/parent-comment protocols or calculate score from vote counters. | `nga_phone_base_3.0/.../mvp/model/ArticleListModel.java`, `loadPageWithAppApi`; `lib_core_data/.../data/bean/ThreadAppBean.kt`; `lib_core/.../parse/ThreadInfoAppParse.kt` and `ThreadParseUtils.kt`. | Default-off `pref_show_with_app_api`. Reuses source mapping with explicit query/variable-page handling, complete image prefixes and the existing renderer. An eligible foreground normal-format failure may try App once with the same account/origin; successful source selection persists for that reader. No App prefetch or account rotation. See the [compatibility reader contract](./thread-detail-compat-contract.md). |

The operation-local `ArticleByteClient` is shared by the enabled ordinary and
App stages so errors and credentials have one boundary. It uses explicit
Cookie/browser UA/official identity, validated HTTPS project origins, bounded
strictly decoded bytes, cancellation, and no automatic redirects or connection
retry. It does not replace the shared Retrofit stack for other operations.
Source/raw/HTML remain distinct, and owned page replay uses the [cache
contract](./thread-page-cache-contract.md) without issuing network requests.

## Posting And Uploads

| Operation | Original method, path, and fields | Parser, effect, and failure | Evidence | Delta |
| --- | --- | --- | --- | --- |
| `POST.PREFLIGHT` | Body-less POST `post.php?fid&lite=js` plus optional `action`, `pid`, `tid`, `stid`. | JS wrapper to `data.auth`; token held in `PostParam`. Failure disables upload but does not block text submission. | `nga_phone_base_3.0/.../mvp/model/TopicPostModel.java:71-115` | `unchanged` |
| `POST.SUBMIT` | Cookie `HttpURLConnection` POST `post.php?`; `step=2`, GBK `post_content`; optional `pid`, `tid`, `action`, GBK `post_subject`, `fid`, `anony`, attachments/checks, `stid`. Covers new topic, reply, and edit. | Reads GBK HTML title. Only `发贴完毕` or reminder-limit text counts as success. 4xx is rejection; 5xx/network is failure; after-send loss is not distinguished and must migrate to `UnknownOutcome`. | `nga_phone_base_3.0/.../param/PostParam.java:140-169`; `.../task/TopicPostTask.java:61-129` | `unchanged` |
| `POST.COMMENT` | Cookie POST `post.php`; GBK `post_content`, `tid`, `pid`, `fid`, `nojump=1`, `step=2`, `action=reply`, `comment=1`, `lite=htmljs`, optional `anony`. | HTML-embedded JS `data.__MESSAGE`; success requires code 200 and `发贴完毕`; I/O failures swallowed. After-send loss must be `UnknownOutcome`, never auto-retried. | `nga_phone_base_3.0/.../task/PostCommentTask.java:50-150` | `unchanged` |
| `ATTACHMENT.UPLOAD` | Multipart POST `https://img8.nga.cn/attach.php?`; `attachment_file1`, `attachment_file1_url_utf8_name`, `fid`, preflight `auth`, `func=upload`, `v2=1`, `lite=js`, image options, `origin_domain=bbs.ngacn.cc`. | JS JSON `data.attachments`, `attachments_check`, `url`. `error_code=9` auto-retries once with compression. Raw failure response logged; upload/token outcome may be unknown. | `nga_phone_base_3.0/.../mvp/model/TopicPostModel.java:157-267` | `unchanged` |
| `AVATAR.STAGE_UPLOAD` | Cleartext multipart POST external `http://app.myauth.us/api/attach.php?`, no NGA Cookie; image plus `v2`, `fid=-7`, `func=upload`, `origin_domain`, `lite=js` and options. | GBK JSON `error/errorinfo/data`; URL placed in UI. External legacy service is unsupported for migration by default. | `nga_phone_base_3.0/.../task/AvatarFileUploadTask.java:29-46,94-105,112-253` | `unchanged` |
| `AVATAR.APPLY` | Cookie cleartext POST `http://nga.178.com/nuke.php?`; `lite=js`, `noprefix`, `func=avatar`, GBK `icon`, and literal `__ngaClientChecksum=null` because its setter has no caller. | JS `data.0|error.0`. UI always toasts success, but finishes only on exact recognized success. Rejection/network/unknown outcome are not typed. | `nga_phone_base_3.0/.../activity/AvatarPostActivity.java:62,275-307,348-449`; `.../param/AvatarPostAction.java:6-38` | `unchanged` |

## Interactions And Account Mutations

All operations below use the active Cookie and are non-idempotent unless a
future authorized contract proves otherwise.

| Operation | Original method, path, and fields | Original result and migration warning | Evidence | Delta |
| --- | --- | --- | --- | --- |
| `RECOMMEND.SET` | POST form `nuke.php`: `__lib=topic_recommend`, `__act=add`, `raw=3`, `__output=8`, `tid`, `pid`, `value=1|-1`. | Displays `data.0`; parse failure becomes network error. Preserve reject/challenge/rate-limit/unknown separately. | `nga_phone_base_3.0/.../task/LikeTask.java:23-61` | `unchanged` |
| `TOPIC_FAVOR.ADD` | Body-less POST URL `nuke.php?__lib=topic_favor&lite=js&noprefix&__act=topic_favor&action=add&tid[&pid]`. | Delimiter-extracted message only; no typed success. Do not infer server favorite state from toast text. | `nga_phone_base_3.0/.../task/BookmarkTask.java:12-47` | `unchanged` |
| `TOPIC_FAVOR.REMOVE` | POST form `nuke.php`: topic-favor lib/act, `__output=8`, `action=del`, `page`, `tidarray=tid[_pid]`. | Any body containing `操作成功` is success; cache removal is separate. Require confirmed success and rollback. | `nga_phone_base_3.0/.../mvp/model/TopicListModel.java:55-63,105-127,195-217` | `unchanged` |
| `BOARD.SUBSCRIPTION.SET` | Body-less cleartext POST `http://bbs.ngacn.cc/nuke.php`; `__lib=user_option`, `__act=set`, `raw=3`, `type`, `__output=8`, parent `fid`, `<add|del>=child`; type 1 reverses action meaning. | Any `成功` substring accepted. Preserve operation subtype and server rejection; no automatic repeat. | `nga_phone_base_3.0/.../task/SubscribeSubBoardTask.java:31-102` | `unchanged` |
| `VOTE.SUBMIT` | Vote HTML calls `ProxyBridge`; Cookie POST sends identical query/body to `nuke.php`: `__lib=vote`, `raw=3`, `lite=js`, `__act=vote|settle`, `tid`, comma `voteid`; wager type 1 alternates option names and entered values. | JS `data.0|error.0`; success prefix normalized for toast; raw parse failure logged. Bind bridge to trusted local content and treat send loss as unknown. | `nga_phone_base_3.0/src/main/assets/vote/vote.js:124-182`; `.../util/FunctionUtils.java:149-185`; `.../proxy/ProxyBridge.java:36-120` | `unchanged` |
| `REPORT.POST` | POST `nuke.php`; duplicated query/form `__lib=log_post`, `__act=report`, `__output=8`, `charset=gbk`; form `pid`, `tid`, `info`. | `data.0|error.0`; sample error includes retry delay. Preserve report text, rate limit, and unknown outcome; do not retry. | `nga_phone_base_3.0/.../ui/fragment/dialog/ReportDialogFragment.java:38-71`; `.../task/ReportTask.java:14-77` | `unchanged` |
| `CHECK_IN.POST` | Body-less POST `nuke.php?__lib=check_in&__act=check_in&lite=js`; optional automatic startup invocation when local preference/day allows. | Text `签到成功` or `今天已经签到` advances local timestamp. Make automation opt-in/bounded and never repeat after unknown outcome. | `nga_phone_base_3.0/.../task/CheckInTask.java:28-65`; `.../NgaClientApp.java:130-132` | `unchanged` |
| `SIGNATURE.SET` | POST form `nuke.php`: `__lib=set_sign`, `__act=set`, `raw=3`, `lite=js`, `charset=gbk`, active `uid`, GBK `sign`. | Any `操作成功` substring accepted; local duplicate-in-flight suppression. Preserve draft and distinguish reject/challenge/network/unknown. | `nga_phone_base_3.0/.../task/SignPostTask.java:21-93` | `unchanged` |

## Private Messages And Notifications

| Operation | Original method, path, and fields | Parser, effect, and failure | Evidence | Delta |
| --- | --- | --- | --- | --- |
| `MESSAGE.LIST` | GET `nuke.php`: message lib/act, `act=list`, `lite=js`, `page`. | Repairs JS/comment/non-standard JSON; Paging error on empty. Raw private response may be logged on parse failure. | `lib_bu_message/.../compose/MessageRepository.kt:26-59`; `.../MessageConvertFactory.java:29-109` | `unchanged` |
| `MESSAGE.READ` | GET `nuke.php`: message lib/act, `act=read`, `lite=js`, `mid`, `page`. | Same repairs plus invalid `__P` removal; title/recipients written to global mutable fields; private parse failures may be logged. | `lib_bu_message/.../compose/detail/MessageDetailRepository.kt:14-72`; `.../MessageConvertFactory.java:111-219` | `unchanged` |
| `MESSAGE.SEND` | POST `nuke.php`; query message lib/act, `lite=js`, `charset=gbk`, mutable `act`; form duplicates query plus `mid`, GBK `to`, optional GBK `subject`, required GBK `content`. | Exact legacy message strings are success; `error.0` or generic failure otherwise. Preserve draft; no auto retry after send; do not share mutable request map. | `lib_bu_message/.../compose/post/MessagePostRepository.kt:9-67` | `unchanged` |
| `NOTIFICATION.LIST` | GET `nuke.php?__lib=noti&__output=8&__act=get_all`. | JS wrapper to reply/message arrays and unread state; parse errors become empty list. Foreground and 30-second-throttled background callers. | `nga_phone_base_3.0/.../task/ForumNotificationTask.java:23-89`; `.../ForumNotificationFactory.java:14-107`; `.../NotificationController.java:36-85` | `unchanged` |
| `NOTIFICATION.CLEAR` | Body-less POST `nuke.php?__lib=noti&raw=3&__act=del`. | Response only logged; no confirmation or rollback. Must migrate to confirmed/denied/challenge/rate-limit/network/unknown states. | `nga_phone_base_3.0/.../task/ForumNotificationTask.java:91-104` | `unchanged` |

## Mutation Outcome Matrix

Every state-changing ID in this registry must expose these outcomes during
migration, even when the original code collapsed them:

| Outcome | Required local behavior |
| --- | --- |
| Confirmed success | Apply exactly one operation/account-bound side effect. |
| Confirmed rejection | Keep drafts/input; show bounded server reason; no success state. |
| Challenge/CAPTCHA/access control | Stop and surface; never bypass or rotate accounts. |
| Rate limit | Surface retry metadata if parsed; never automatically replay a write. |
| Network/protocol failure before send | Retry only when proven safe for that operation. |
| `UnknownOutcome` after possible send | Preserve input, avoid replay, and reconcile or require explicit user choice. |

Original text checks (`成功`, `操作成功`, HTML titles, reminder strings) are
recorded compatibility behavior, not stable success contracts.

## WebView, Media, And Exclusions

- The local `ProxyBridge` is network-bearing only for `VOTE.SUBMIT`. The
  `LocalWebView` `action` interface returns emotion size and is local-only.
- The generic forum WebView is a browser fallback, not a typed NGA operation;
  original substring host checks and unrestricted `loadUrl` must not migrate.
- Decoder/profile/deep-link/static-shortcut URLs are link-only. They map to
  `TOPIC.LIST`, `THREAD.PAGE`, or `USER.PROFILE` when actually opened.
- Generic image/audio/video fetches are external media. Never attach account
  Cookies; block cleartext and validate exact media origins during migration.
- `BaseRxTask` is dormant. `HttpUtil.selectServer2` is dormant because its host
  array is empty, and `HttpUtil.getHtml(uri, cookie)` is dormant because it has
  no caller. GitHub and other product links are non-NGA exclusions.
- `AVATAR.STAGE_UPLOAD` is retained for data-flow completeness but its external
  cleartext service is `unknown-or-unsupported` for new implementation.

The exhaustive source-to-operation mapping is preserved in the task-local
`research/original-justwen-nga-network-inventory.md` ledger.
