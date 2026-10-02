# Design: Compatibility Reader Display Parity

## Boundaries and Data Flow

Keep `ArticleByteClient` and the shared reader/session machinery unchanged.
Extend `ThreadAppBean`'s consumed projection and `AppArticleParser` mapping:

`App JSON -> validated post/comment/attachment fields -> ThreadRowInfo ->
ArticleConvertFactory -> existing HtmlData builders and ArticleListAdapter`.

IP data remains a separate path:

`accepted ThreadData -> ArticleAuthorIds -> AuthorLocationPage/repository ->
ArticleListAdapter author-detail update`.

The default parser and existing widgets remain the behavioral reference. Use
shared model/rendering seams rather than cloning the normal parser into App.

## Scores

The default reader displays the support count, not net recommendations. The
captured public NGA `js_read.js` assigns support to `score`, opposition to
`score_2`, and separately computes `recommend=max(support-opposition,0)`.
Independent pinned App DTO source names `vote_good` as likeCount and `vote_bad`
as dislikeCount. See the evidence note for anchors and script identity.

Map a valid nonnegative bounded integral `vote_good` directly to `score` and
set `scoreKnown=true`; missing/malformed/negative/out-of-range good values
remain unknown. `vote_bad` is independent and must not suppress or alter a
valid support count, even if missing, malformed, or nonzero. No subtraction,
absolute value, or signed counter conversion is needed. Preserve `vote` as
existing markup/source metadata; do not conflate it with the counters.

The existing score TextView and support/oppose buttons need no new layout.
Voting behavior remains owned by existing presenter/task code.

## Attachments

The confirmed wire spelling is `attches`, a nullable list. Each observed image
item supplies relative `attachurl`, `thumb`, `type`, and other metadata. Map
consumed fields into the existing attachment model before rendering; preserve
response order with a local map if required by the legacy model. Such map keys
are local indexes, never invented server attachment IDs.

Use the existing page prefix and attachment renderer. Forward `thumb` without
inventing a new meaning: observed values include "56" and "120", while the
default builder treats only "1" specially. Do not synthesize alternate thumbnail
URLs from unverified flags. Do not fetch media during mapping. Preserve existing
URL normalization and image-list deduplication. Missing/null lists mean no
attachments. Invalid consumed structures must not crash the whole readable
page; mark unavailable supplemental content/incompleteness appropriately.
Unknown extension fields remain opaque.

## Comments and Post Identity

Read `comments` directly under its parent post. Its nesting is authoritative
for this confirmed payload; no extra request or parent search is necessary.
Map child identity, source, author, time, and rendering fields before rendering
the parent. Set the child's presentation kind to COMMENT from this structural
context, not from the parent's `isTieTiao` value.

`isTieTiao=true` on a post means that the post can contain comments in the
observed sample; it cannot be used to classify that post as a comment. Retain
ordinary root-post identity and floor even when the field is true. Do not
extrapolate unverified standalone comment or bitfield semantics. A nested
`comment_to_id=-1` was observed for the main post; arbitrary positive-parent
relationships are unnecessary when the actual nesting already establishes
ownership.

Keep top-level query-author validation separate from nested comment authors:
comments on an author's post may be written by somebody else. Children must
not inflate page row counts, affect ordinary floor coordinates, or consume
paging slots. Bound processing to the default renderer's one direct-child level. Deeper
comments or independent child attachment blocks receive a supplemental
incompleteness notice and prevent complete-page caching. Maintain source
validity, placeholder, action eligibility, and recursive completeness over
the mapped parent/direct children.
Unknown optional shapes should reduce supplemental completeness rather than
reject a readable page. Validate actual usable child identities without
inventing missing IDs or silently deleting readable child content.

## Shared Display and IP Location

Keep author, avatar, anonymous/OP status, body media, poll markup, signature,
blacklist, date, device and action mappings aligned with the existing default
consumers. Audit these paths for confirmed omissions; do not fabricate metadata
because the default layout can display it.

No new IP-location implementation is required. Both modes already deliver their
accepted pages to the same `AuthorLocationService`. Preserve existing valid-UID,
anonymous, offline-cache, rate-limit, account and view lifetime behavior. The
value is the author's latest observed profile location, not historical post
location. The App `address` field remains unused because its populated meaning
is unverified and observed samples are empty. Nested comments continue to use
the default comment UI without a new location field or extra author queries.

Ignore `hot_post` for presentation. Do not execute optional `html_head_extra`.

## Compatibility, Risks, and Rollback

This deliberately expands the old upstream-derived opaque-field boundary. After
approval, update the compatibility spec to describe the new observed contracts
and replace the incorrect `isTieTiao` rule; preserve all unrelated invariants.
Use synthetic fixtures only, based on recorded field structure. Never commit
cookies, real bodies, authors, attachment filenames/URLs, or raw responses.

Risks are partial supplemental payloads, author-filter interactions, misleading
score arithmetic, thumbnail assumptions, and incomplete comments reaching cache.
Tests must cover their externally visible effects. The new supplemental-availability flag is derived display metadata and is
excluded from Fastjson serialization/deserialization, preserving frozen legacy
bean outputs. No persisted schema or setting migration is planned; saved raw
pages can be reparsed. Rollback is the
adapter/spec/test change, without discarding existing caches or account data.
