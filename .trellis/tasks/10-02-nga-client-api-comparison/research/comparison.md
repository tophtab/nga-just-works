# Four-way NGA API comparison and learning priorities

The user subsequently clarified that read reliability and occasional HTTP403
are the primary concern. See [the focused issue/fix investigation](read-reliability.md);
its priorities supersede the broad feature-adoption order below.

## Conclusion

The best learning targets are operation semantics and response use: reliable
write-state confirmation from compose-nga; favorite folders, social feeds,
messages and compose preflight from LNGA; extended thread field candidates from
NgaClient. None justifies replacing the current reader transport wholesale.
The current project already supports App category and optional App thread reads.

## Reproducible sources

| Project | Branch | Inspected commit | Detailed ledger |
| --- | --- | --- | --- |
| Night-stars-1/compose-nga | master | `8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595` | [compose-nga](compose-nga.md) |
| apap6628114/lnga_harmony | main | `df6cfcc2e4cfc2efa95269aac398707e9d4d3e81` | [LNGA](lnga-harmony.md) |
| ForeverZero/NgaClient | main | `c728a152f3ddffe2e816de6c947362844fe07ab6` | [NgaClient](nga-client.md) |
| tophtab/nga-just-works | main | `fae59cbc477590e98c1c61a7526343eccc4cff3b` | [Current project](current-project.md) |

All findings are source-observed. The ledgers supply request fields, encoding,
parsers, reachable consumers, exceptions, and pinned source anchors. This report
does not infer live availability or field correctness from declarations. The
older nga_harmony repository's relationship to lnga_harmony is not established.

## Symmetric capability matrix

`A` = app_api.php; `N` = nuke.php. `lib/act` names query selectors.
“Not found” means no implemented operation identified in the inspected surface,
not a statement about what NGA's service supports. NgaClient is a library with
five callable reads and demonstration tests, not a complete application.

| Capability | compose-nga | lnga_harmony | NgaClient | Current project |
| --- | --- | --- | --- | --- |
| Authentication | QR -> uid/token; signed forms | Native login/import -> session; signed forms plus Cookie | Caller supplies uid/token; no login | WebView Passport login; Cookie requests; scoped reader captures account |
| Category tree | A home/category | A home/category, `_v=2` | A home/category, `_v=2`, empty form | A home/category, existing validated envelope/cache |
| Topic list | A subject/list | A subject/list with fid/stid, sort/subscription rules | A subject/list, fid/page | thread.php with forum, author, search and favorites selectors |
| Search | A subject/search, forum/search; N search/instant_search | A subject/search, forum/search; user lookup | A subject/search; page omitted, Chinese search documented broken | thread.php search; forum.php GBK board search |
| Hot/pinned/recommended | N app_inter/recmd_topic | A subject/hot, subject/topped | Category recommendation fields only | thread.php recommendation ordering; no equivalent dedicated hot/topped route identified |
| Thread read | A post/list by tid or pid | read.php output17 -> HTML reconstruction; output8 for tracking | A post/list by tid | read.php output8 plus default-off A post/list compatibility |
| User profile/activity | A user/detail, user/subjects | A user/detail, user/subjects, user/replys; N ucp/get and bounded topic fallback | Embedded author DTO; independent user/detail is declaration-only | N ucp/get; HTML location supplement; author topic/reply searches |
| Remote favorite boards | N forum_favor2 get/add/del | N forum_favor2/get; A favorforum/sync full list | N forum_favor2/get only | Local bookmark persistence; separate subforum subscription mutation |
| Topic favorite folders | N topic_favor_v2 list_folder/add; A favor/all | Folder list/create/rename/default/delete, add/del; A favor/all | Not found | Legacy topic_favor add/delete and thread.php favor list |
| Following/social feed | N follow_v2/follow, types1/8 | N follow_v2 follow/get_follow/get_follow_by/get_push_list | Not found | No matching follow_v2 operation identified |
| New/reply/edit/comment | A post/reply and post/quote only | A post/check then new/reply/quote/modify/tietiao | Not found | post.php preflight and new/reply/edit/comment |
| Upload | Not found | Preflight attach_url, image/video multipart | Attachment DTO only | img8 attach.php image upload; separate legacy avatar staging |
| Recommend/oppose | A post/recommend; validated last_state and pending guard | A post/recommend | Counters only | N topic_recommend/add; message result |
| Poll | Rendering only, no submit | A subject/vote selections | Vote markup only | Local bridge to N vote/vote or settle |
| Messages/notifications | Not found | A message list/detail/send/reply and notify/list | Unused notify constant only | N message/message list/read/send and noti get_all/del |
| Other account mutations | Not found in audited API | Check-in; remote logout | Not found | Check-in, report, remote filters, signature, legacy avatar |

## Transport and correctness comparison

| Dimension | compose-nga | lnga_harmony | NgaClient | Current project |
| --- | --- | --- | --- | --- |
| Credential/wire family | Token/uid/app signature, output14 | Signed POST, operation-specific output/inchst, Cookie also sent | Token/uid signature for four reads | Cookie/official identity; ordinary and App reads have different response contracts |
| Codec | UTF-8 forms, Gson response decoding | Explicit UTF-8 vs GB18030 per operation; JSON/HTML sanitizers | JVM-default response charset; search pre-encoding then form encoding | Shared GBK converter; strict charset-aware bounded reader transport |
| Business validation | code=0 common gate; stronger vote-state parser | Stronger shape checks on some operations; missing-code-as-zero on others | code0/5/53 check; null/status gaps | Operation-specific legacy messages; scoped reader typed HTTP/content errors |
| Replay/lifecycle | HTTP defaults; callback bridge lacks cancellation | Shared transport retries writes on I/O; physical cancellation absent | HTTP defaults, synchronous reads | Reader disables retry/redirect and supports cancellation; legacy stack is separate |
| Important limitation | Global auth error can invalidate a newer session | Unsafe write replay and unchecked upload URL credential forwarding | Secret/body logging, incomplete search and unvalidated DTOs | Shared body logging/GBK assumption and legacy write results remain improvement targets |

These findings describe source calls and explicit policies; underlying SDK
defaults and production logger output were not independently executed.

## Prioritized lessons

Priorities rank learning/adoption experiments, not already-approved changes.
P1 is the strongest immediate design lesson; P2 adds capability or needs more
protocol evidence; P3 is deferred because benefit is uncertain or cost is high.

| Priority / candidate | Upstream evidence | Current gap and benefit | Cost, compatibility and next proof |
| --- | --- | --- | --- |
| P1: precise write confirmation and per-target pending state | compose PostVoteResultAdapter, PostVote and PostViewModel; [ledger](compose-nga.md) | LikeTask returns text; adopt validated outcomes to reduce duplicate clicks and inconsistent displayed state | Medium. Establish current endpoint semantics first; last_state is upstream's interpreted previous state, not a universal new-state field. Preserve input on unknown outcome; do not copy automatic retries |
| P2: favorite folders | compose demonstrates folder selection/content/add; LNGA covers complete topic_favor_v2 CRUD; [ledger](lnga-harmony.md) | Current legacy favorites have no equivalent folder contract; enables explicit organization | Medium/high. Preserve existing pid bookmarks: LNGA wrapper uses pid0 and compose omits pid. Define default folder, missing folder, delete effects and token/account scope before migration |
| P2: editing and upload preflight | LNGA ThreadWriteApi post/check -> prepared text/auth/attach_url/existing attachments | Current post.php preflight primarily extracts auth; richer preflight is evidence for server-prepared quotes and preserving original attachments during edits | High. Compare edit roundtrip fixtures and escaping; validate upload host before credentials; retain unknown outcomes and explicit submission confirmation |
| P2: App messages and notifications | LNGA MessageApi, notification parser and actual page/store callers | Alternatives to current JS-wrapped N message/noti routes; potentially clearer typed lists/details and unread states | High. No proven feature parity or stability advantage yet. Compare recipient/thread identity, pagination, unread transitions, error unions and private-data handling |
| P2: richer discovery and user activity | compose instant_search; LNGA subject hot/topped, user/replys, follow feed | Concrete optional capabilities beyond existing thread.php searches | Medium/high. Validate pagination separately per operation; never copy LNGA's page1-only fallback as full pagination. Suggestions need cancellation/bounds; feeds are a product-scope addition |
| P2: extended App thread fields | NgaClient PostListResponse hot_post, ThreadReply attachs/comments/comment_to_id; [ledger](nga-client.md) | Current adapter intentionally leaves several fields opaque; supplies a focused fixture research checklist | High until examples prove semantics. DTO names/types and attachment spellings are not validated; do not infer score, parent relation or renderer behavior from them |
| P2: account-bound remote board import | All three upstreams read forum_favor2/get; LNGA adds whole-list sync | Current bookmarks are local; import could reduce setup effort | High. Define merge and account ownership; start with read-only import. Whole-list sync can overwrite remote state; it is not equivalent to one add/delete |
| P3: typed profile location | compose user/detail exposes ipLoc; LNGA has richer profile route | Potential alternative to current bounded HTML supplement | High. Freshness, permissions and completeness are unverified; no evidence it saves requests by itself. Keep shipped location behavior until parity is demonstrated |
| P3: alternate HTML reader/category v2 | LNGA read output17; LNGA/NgaClient category `_v=2` | Additional format/schema references | High reader cost, uncertain category benefit. Current App fallback already exists; require missing useful data before expanding source count |

## Practical adoption order

1. Apply the write-state lesson to a narrowly selected current interaction using
   offline response fixtures and existing transport rules.
2. If a new capability is wanted, favorite folders have the clearest combined
   upstream request-to-UI evidence; specify floor-bookmark compatibility first.
3. Investigate editing/preflight and messages independently. They have different
   compatibility and mutation risks and should not be bundled as a total API swap.
4. Treat richer DTO fields and alternative read formats as research leads until
   examples establish value and correctness.

No runtime tests, account actions, NGA requests or application changes occurred.
Static document checks and source tracing are the validation performed. Upstream
tests were inspected as evidence; they were not run, and some are live-service
demonstrations. Research is complete for this planning pass; product adoption
remains outside the task's authorized scope.
