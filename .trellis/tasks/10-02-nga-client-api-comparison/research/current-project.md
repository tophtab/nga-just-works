# Current project API baseline

## Revision and evidence boundaries

- Repository: https://github.com/tophtab/nga-just-works
- Default/local branch: `main` (remote HEAD checked with `git ls-remote --symref`).
- Inspected revision: `fae59cbc477590e98c1c61a7526343eccc4cff3b`.
- Product worktree was clean; the only untracked changes were this planning task.
- Source inspection only; no NGA traffic or runtime availability verification.
- Below, `A` means `nga_phone_base_3.0/src/main/java`, `N` means
  `lib_base_network/src/main/java/com/justwen/androidnga/base/network`, and
  `M` means `lib_bu_message/src/main/java/com/justwen/androidnga/module/message`.
  Anchors resolve against this revision, not the older Justwen registry snapshot.
  [Pinned tree](https://github.com/tophtab/nga-just-works/tree/fae59cbc477590e98c1c61a7526343eccc4cff3b).

## Shared request and parsing behavior

`N/retrofit/RetrofitHelper.java:46` selects the configured NGA origin and rebuilds
Retrofit on preference changes. At line 103 its interceptor supplies the current
account Cookie unless explicitly supplied, browser UA and `X-User-Agent:
Nga_Official`. A later interceptor decodes/rebuilds bodies containing
`charset=gbk`; request logging remains. `N/retrofit/converter/JsonStringConvertFactory.java:38`
decodes all String responses as GBK, invokes body logging, and returns an empty
string on IOException. These are source-level logging calls; build-time logger
behavior was not audited. Neither typed operation errors nor immutable account
snapshots apply universally to this stack.

The reader has a separate, stronger boundary. `A/sp/phone/mvp/model/thread/ArticleByteClient.kt:40`
validates exact HTTPS origins, takes explicit account credentials, disables
redirects/connection retries, cancels with its subscriber, caps bodies at 4 MiB,
and strictly decodes the declared supported charset (GBK when absent). Request
construction at line 58 supports BOTH ordinary read.php and App API. The
production caller is `ArticleListModel.java:51`; parsing dispatches to ordinary
or App adapters. `ArticleListPresenter.java:59,163,213` owns the default-off
compatibility preference and bounded same-account/source fallback. The legacy
path still has account retry at line 281. Do not describe the whole current
project as either a legacy-only client or universally hardened.

Authentication remains WebView login and Passport Cookie extraction:
`lib_bu_account/src/main/java/com/justwent/androidnga/bu/login/LoginViewModel.kt`.
The exact restoration contract in `network-foundation-contract.md` applies;
native-password declarations in Retrofit are not evidence of an active login flow.

## Operation map

Unless noted, shared Retrofit requests use the selected origin, account Cookie,
GBK String conversion and operation-specific JSON parsing. `nuke.php` selectors
below name `__lib / __act`; paging and IDs are caller inputs, not fixed values.

| Group | Request / relevant inputs | Response owner and current source anchor |
| --- | --- | --- |
| Board categories | GET `app_api.php`, `home/category` | `A/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt:38,259,290`; parsed envelope and disk cache; `ForumBoardViewModel.kt:220` calls it |
| Topic list, search, favorites, author posts | GET `thread.php`; `authorid,searchpost,favor,content,author,stid/fid,key,fidgroup,page,lite=js,noprefix`; recommendation ordering optional | `A/sp/phone/mvp/model/TopicListModel.java:193`; GBK author but UTF-8 keyword; TopicConvertFactory maps list/errors |
| Ordinary thread page | GET `read.php`; `page,__output=8,noprefix,v2,tid/pid/authorid` | `ArticleListModel.java:83,105` and reader adapters; scoped path uses ArticleByteClient |
| App thread page | POST `app_api.php?__lib=post&__act=list`; form `page,tid/pid/authorid` | `A/sp/phone/mvp/model/thread/ArticleByteClient.kt:65`; `ArticleListModel.java:61`; existing optional compatibility feature, not a missing endpoint |
| Board search | GET `http://bbs.nga.cn/forum.php`; `__output=8,key` (GBK) | `A/sp/phone/task/SearchBoardTask.java:24,58`; `data.0.fid/name`, errors collapse to null |
| User profile | GET `nuke.php`, `ucp/get`, `lite=js,noprefix,uid/username`, Web profile Referer | `A/sp/phone/task/JsonProfileLoadTask.java:46,83`; ProfileEnvelopeParser; current owner avoids raw parse-failure logging |
| Author location | GET `nuke.php?func=ucp&uid=...` | `A/sp/phone/profile/ProfileLocationTransport.java:63,94`; GBK HTML `__UCPUSER` parser, account-scoped cache and bounded queue; 503 stops work, no general retry migration implied |
| Remote filters | POST `nuke.php`, `ucp/get_block_word` or `set_block_word`; `__output=8`, uid or encoded CRLF data | `A/gov/anzong/androidnga/activity/compose/filter/FilterWordModel.kt:53,107`; Result of data/error; ViewModel line 73 calls remote read |
| Post preflight and categories | Bodyless POST `post.php?fid&lite=js`, optional action/pid/tid/stid; GET `nuke.php`, `topic_key/get,fid,__output=8` | `A/sp/phone/mvp/model/TopicPostModel.java:71,118,239`; `data.auth` and `data.0` labels |
| New topic/reply/edit | Cookie POST `post.php`, `step=2,post_content,pid/tid/action,post_subject,fid,anony,attachments,attachments_check,stid` | `A/sp/phone/param/PostParam.java:140`; `TopicPostModel.java:147` -> `A/sp/phone/task/TopicPostTask.java:61`; GBK fields and HTML-title success strings at line 115 |
| Comment | POST `post.php`; `post_content,tid,pid,fid,nojump,step=2,action=reply,comment=1,lite=htmljs,anony` | `A/sp/phone/task/PostCommentTask.java:50,125`; HTML JS message, called by PostCommentDialogFragment line 40 |
| Attachment upload | Multipart POST `https://img8.nga.cn/attach.php`; file, UTF-8 filename, fid, preflight auth, func=upload,v2=1,lite=js,image options,origin_domain | `TopicPostModel.java:157,255,283`; attachments/check/url; compression retry remains operation-specific |
| Recommend | POST `nuke.php`, `topic_recommend/add`, `raw=3,__output=8,tid,pid,value` | `A/sp/phone/task/LikeTask.java:36,63`; `data` parser; ArticleListPresenter line 427 calls it |
| Favorite add/delete | Bodyless POST add URL `topic_favor/topic_favor,action=add,tid,pid,lite=js`; form POST delete `action=del,page,tidarray,__output=8` | `A/sp/phone/task/BookmarkTask.java:14`; TopicListModel favorite delete; text-based confirmation, not typed favorite groups |
| Subscription | Bodyless POST `http://bbs.ngacn.cc/nuke.php`, `user_option/set,type,fid,add/del,raw=3,__output=8` | `A/sp/phone/task/SubscribeSubBoardTask.java:88`; BoardSubListFragment line 45; success substring |
| Report / signature | POST nuke `log_post/report` with pid/tid/info; `set_sign/set` with uid/sign and GBK | `A/sp/phone/task/ReportTask.java:77`, ReportDialogFragment line 60; SignPostTask lines 31–57, SignPostActivity line 94 |
| Check-in / vote | nuke `check_in/check_in`; vote bridge `vote/vote` or `vote/settle`, tid,voteid | Existing task/bridge routes catalogued in operation registry; not newly validated server contracts |
| Avatar | External cleartext staging then NGA avatar application | AvatarFileUploadTask, AvatarPostAction and AvatarPostActivity; UI reaches upload at line 214; legacy support is not an adoption recommendation |
| Messages list/read | GET `nuke.php`, `message/message,act=list/read,lite=js,page,mid` | `M/compose/MessageRepository.kt:29`, `detail/MessageDetailRepository.kt:39`; MessageConvertFactory normalizes legacy wrappers |
| Messages send/reply | POST nuke `message/message,act,lite=js,charset=gbk`; mid,to,subject,content | `M/compose/post/MessagePostRepository.kt:13,26,43`; shared mutable query map, GBK field encoding, exact success strings/data-error parsing |
| Notifications | GET nuke `noti/get_all,__output=8`; bodyless POST `noti/del,raw=3` | `A/sp/phone/task/ForumNotificationTask.java:29,92`; ForumNotificationFactory maps arrays; clear lacks typed confirmation |

## Comparison implications

1. App category and App thread APIs already exist. Adoption suggestions must
   identify richer operation parameters or response use, not merely endpoint names.
2. Generic Retrofit and older writes/messages remain plausible improvement
   targets; scoped reader transport already solves several analogous concerns.
3. Prefer reusing current strict decoding/account/error boundaries when learning
   richer upstream contracts. Do not transplant permissive upstream parsing,
   automatic write retries or a native password flow.
4. Existing specs are a navigation aid, not proof that every pinned original
   parser is unchanged. For example, current category-envelope validation and
   extracted mutation decoders differ from the older registry anchors.
5. This inventory is source-observed. Existing tests were located but not run;
   no API here is labelled live-tested or currently server-supported.
