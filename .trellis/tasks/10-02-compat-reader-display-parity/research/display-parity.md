# Display Parity Evidence

## Current Code Anchors (main a7d7edb0)

- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ThreadAppBean.kt:10`
  describes unused attachment/hot-post/parent extensions; Result currently does
  not consume `attches`, `comments`, or vote counters.
- `.../thread/AppArticleParser.kt:61` incorrectly classifies an entire root post
  as COMMENT when `isTieTiao=true`; line 65 sets `scoreKnown=false`.
- `.../convert/ReadThreadLegacyMapper.kt:51` maps ordinary wire `score`; line 64
  retains hot-reply IDs, and line 66 maps nested comments before rendering.
- `.../convert/ArticleConvertFactory.java:125` builds HtmlData; lines 141–156
  project attachments/comments into the existing builders. No hot-reply display.
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/adapter/ArticleListAdapter.java:509`
  displays one known score; lines 513–521 select ordinary-post actions and author
  detail visibility. Line 527 formats the shared location/post-count text.
- `.../ui/fragment/ArticleListFragment.java:498` accepts current pages before
  rendering and delivering them to AuthorLocationService at line 513, without
  a normal-vs-App location restriction.
- `nga_phone_base_3.0/src/main/java/sp/phone/profile/ArticleAuthorIds.java:17`
  collects positive nonanonymous top-level authors regardless of source.
  `AuthorLocationPage.java:49` uses those IDs through the shared repository.
- `lib_core/src/main/java/gov/anzong/androidnga/core/corebuild/HtmlAttachmentBuilder.java:47`
  joins the page prefix and relative URL; only thumb "1" gets the historical
  suffix. Lines 67–105 build the existing independent attachment section.
- `AppArticleParserTest.kt:131` currently encodes the disproven marker meaning;
  its opaque-extension test must be narrowed when attachments are consumed.

Paths starting with `.../thread` or `.../convert` abbreviate
`nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/`; `.../ui` abbreviates
`nga_phone_base_3.0/src/main/java/sp/phone/`.

## Authorized Live Observations

The user supplied topic 47659017 as a known main-post-comment sample and asked
for continued compatibility investigation. Same saved account, unsigned App
POST to `https://ngabbs.com/app_api.php?__lib=post&__act=list`, tid/page only,
browser UA and `X-User-Agent: Nga_Official`, no redirects/retries/media downloads.
Earlier reads at 2026-10-02 22:54 Asia/Shanghai returned HTTP200/code0/page1:

- Main post: PID0/floor0, `isTieTiao=true`, nested `comments` list of length1.
- Child: PID883526131, type1, floor0, `comment_to_id=-1`; author object,
  date and nonempty content supplied; no `isTieTiao` field on the child.
- `hot_post` is a separate list of four ordinary posts; their floors also occur
  in the ordinary page. It must not be appended to that page for this task.

One supplementary read at 23:22 Asia/Shanghai, under the continued request,
confirmed main-post `attches` is a two-item image list. Each item supplies:
`attachurl` string, `size` integer, `type` string, `subid` integer,
`url_utf8_org_name`, `dscp`, `path`, `name`, `ext`, `thumb`, `hash` strings.
`attachurl` is relative; observed ext is jpg, thumb values are "56" and "120".
All twenty root rows supply integral `vote_good`/`vote_bad`; all observed bad
values are zero. There is no root-row `score`. All `address` objects are empty.

Metadata-only observations are in ignored `.temp/compat-47659017-*.json` files.
This note records the portable schema evidence; do not copy real bodies, author
identities, attachment paths or credentials into fixtures or artifacts.
Earlier topic47659652 evidence is in the separate comparison task's
`research/compat-field-availability.md` and confirms counters/attachments too.

## Independent Source and Limits

ForeverZero/NgaClient revision c728a152f3ddffe2e816de6c947362844fe07ab6,
`src/main/java/org/zhd/ngaclient/dto/Attachment.java`, independently declares
`attachurl`, thumb, type, path and other matching metadata. Its ThreadReply DTO
uses the mismatching list key `attachs`; actual live `attches` takes precedence.
These DTOs do not establish score arithmetic, marker meaning, or thumbnail flags.

Positive comment-parent IDs outside nesting and populated App `address` remain
unverified. Verified nested structure already establishes comment ownership;
shared profile enrichment already supplies the requested IP presentation. Score
semantics were independently resolved below during implementation research.

## Intended Changes Versus Existing Contracts

The old compatibility spec intentionally prohibited these previously unused
mappings. The approved task will expand that boundary for observed fields and
correct the marker rule; it will not change transport, default parsing, source
separation, identity, page mapping, or account/cache boundaries. Update that spec
before implementation after final planning approval.

## Resolved Score Semantics (2026-10-02 implementation research)

The existing user-supplied `.temp/bbs.nga.cn.har` contains the public resource
`/common_res/js_read.js`. Static source (no new NGA traffic) establishes:

```javascript
// recommend tuple: 推荐值,支持,反对
m.score = (i[1]|=0)
m.score_2 = (i[2]|=0)
m.recommend = i[1]-i[2]>0 ? i[1]-i[2] : 0
```

The same function labels `score` as 支持 and `score_2` as 反对 in a moderator
label. The extracted public script SHA-256 is `e245e742ca16dddc3538db16c5e50bec3c00a3cc21f9be21119a0e4d6d63a923`.
This is public executable source, not a retained forum body or credential.

The current default `ReadThreadLegacyMapper.kt:51` reads wire `score`, and
`ArticleListAdapter.java:511` displays that value directly. The legacy
`references/nga-clients/NGA-CLIENT-VER-OPEN-SOURCE-ymback/.../ArticleListAdapter.java:438`
labels that same getter as 顶. None of these default paths displays `recommend`
or subtracts `score_2`.

Independent App source:
[Night-stars-1/compose-nga PostResponse.kt lines 76–81](https://github.com/Night-stars-1/compose-nga/blob/8ac09f45fdaab5032d09cf5aa2b2aad5b7e8e595/app/src/main/java/com/srap/nga/logic/model/PostResponse.kt#L76)
names `vote_good` as `likeCount` (点赞数量) and `vote_bad` as `dislikeCount`
(点踩数量). This supports mapping `vote_good` directly into the default support
score, independent of `vote_bad`. The previously cautious bad==0 restriction
is therefore unnecessary. Invalid/missing good remains unknown, never zero.
This resolves the planned technical evidence gate without changing product scope.
