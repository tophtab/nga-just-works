# Compatibility Reader Display Parity

## Goal

Make compatibility-mode thread reading match the current default reader as
closely as the available data permits, without a separate hot-reply section.
The user approved task creation, planning, and the final implementation plan
on 2026-10-02 ("好，开干吧").

## Background

The compatibility response already supplies information that the current
adapter omits. A main-post sample also disproves its interpretation of
`isTieTiao`. Evidence and code anchors are consolidated in
`research/display-parity.md`; this document defines the intended outcomes.

## Requirements

- R1 — Use the current default reader's existing layouts, metadata, attachment
  and comment rendering, and applicable actions as the presentation baseline.
  Preserve content, inline images/audio/video, polls, author/avatar/OP markers,
  anonymous identity handling, signatures, blacklist behavior, time, floor
  identity, and device labels where the response supplies usable information.
- R2 — Show a verified equivalent score in the existing single score position
  with the existing support/oppose actions. Do not introduce separate positive
  and negative counter widgets. Unknown or invalid counts must not become a
  fabricated zero or a guessed score.
- R3 — Display valid independent attachments using the default attachment
  presentation, page-specific image host selection, and image viewer behavior.
  Preserve inline media and original editable source.
- R4 — Display returned nested comments beneath their owning post using the
  default comment presentation. An ordinary post containing comments stays an
  ordinary post and retains its floor, main-post identity, and applicable
  reply/quote/vote/comment/filter actions. Damaged supplemental content must
  not discard readable parent posts or be silently treated as complete.
- R5 — Preserve the shared author IP-location and post-count display. Both
  modes use the same author-profile lookup, cache, account/lifecycle rules,
  and fallback behavior. Anonymous or unavailable authors do not gain an
  invented location. Nested comments follow the default comment presentation,
  which does not have a separate author-detail/location row.
- R6 — Do not display a dedicated hot-reply section or duplicate `hot_post`
  items in the ordinary page. Keep pagination, query identity, source switching,
  saved-page ownership, and request behavior consistent with existing contracts.

## Acceptance Criteria

- AC1 (R1, R4): A synthetic equivalent of the observed main post with
  `isTieTiao=true` and one nested comment renders as a main post plus its
  comment; floor zero and applicable actions remain available.
- AC2 (R2): Verified scores, including valid zero, render at the default score
  position. Missing, malformed, or semantically unverified counters do not
  display a guessed value. Existing vote actions retain their targets.
- AC3 (R1, R3): Independent image/media attachments reach the default attachment
  renderer and image list with the correct page prefix; original body/media
  source is preserved and image-list duplication is avoided as in default mode.
- AC4 (R4): Nested author, time, and content are present before parent rendering.
  Incomplete comments retain an unavailable-content indication and make the
  page ineligible for complete-page caching; readable parents survive.
- AC5 (R5): Compatibility author IDs feed the same location pipeline; available
  locations produce the same detail text, while anonymous/missing/failed/cache-
  only cases retain the default behavior without extra query paths.
- AC6 (R1, R6): Default-mode behavior and the existing query/page/source/raw-text
  invariants remain unchanged. Populated `hot_post` does not add visible rows.
- AC7 (all): Focused offline parser/rendering regressions and the repository
  Debug unit-test/lint gate pass, with zero Error/Fatal lint findings.

## Out of Scope and Limits

- No new reader UI, hot-reply feature, IP source, transport/signature scheme,
  automatic browser fallback, or new posting/voting protocol.
- No forced parity for absent or unverified data. Preserve readable content
  and explicit missing-data behavior; document remaining protocol limitations.
- No APK publication, installation, device testing, or real vote/post actions.
- This is one cohesive adapter-to-renderer change, not a general reader rewrite.

## Completion and Release

Implementation and independent review are complete; all acceptance criteria
passed the offline quality gate recorded in `verification.md`. The user
subsequently explicitly authorized commit, push, finish-work and a new stable
release. The next patch after remote 6.2.3 is 6.2.4; publication uses the existing
GitHub Actions tag workflow with version-specific release notes.
