# Design notes — six-item plan for review; implementation in a new session

> Task organization (2026-09-30): R7 was detached as an [archived research deliverable](../archive/2026-09/09-30-reader-native-loading-research/delivery.md). This parent owns R1–R6 only; further R7 repair/removal work remains deferred.

## Boundaries

The parent coordinates six implementation deliverables; implementation belongs to its six child tasks. R7 research is archived independently and is not part of this task tree.
R1–R6 product choices have converged; present the final summary for review. Do not activate tasks in this planning-only session.
Detailed evidence is in research/current-behavior.md.

## R1: link intake

Use a small pure parser at the external Intent boundary, preserving query fields already understood by ArticleListActivity. The supplied Via URL and saved js_read.js establish nga://openType=2?... for topics and type 5 for replies. Register nga intake for these validated cases; the type is in the authority, not a query field. Normalize and validate host/path/identifier values before native routing.
An implicit scheme may admit this app as a handler; an explicit official-package intent cannot. Android web-link defaults remain a platform constraint. Do not rename the application package or claim domain verification.
The user has supplied sufficient link evidence; no live page/device probe is needed for protocol implementation. Chooser/default behavior remains a runtime limitation, not a missing protocol fact.

## R2: thread-owned location work

Separate thread visibility from per-page subscribers so same-thread offscreen prefetch continues while the containing thread is active. An activity/thread-level owner gates all its page consumers. A hidden thread cannot start new supplemental calls.
When no eligible owner needs an in-flight author, request cancellation and retain the physical slot until terminal completion. Distinguish cancellation from author lookup failure: do not persist a failure cooldown for leaving a screen. Preserve late 429/session-rejection signals for the captured identity.
The user confirmed one initial attempt plus one tail-of-queue retry for ordinary network failure. The user selected a 30-second author cooldown after the second network failure: expiry alone never schedules a third attempt; a later fresh eligible online demand is required. The user subsequently explicitly selected this duration and automatic resume on return. Non-network failures retain their existing cache/stop policy. Distinguish cancellation, transient transport failure and malformed data. Migrate persisted old failure records without dropping valid observation or rate-limit records.

Unsent work can remain represented by the owning thread's accepted author set; it must not remain dispatcher-eligible while hidden. On return, automatically resume outstanding online demand, retaining the attempt count of its original round. A return must not clear 429/session stops or promote disk-cache-only pages to online.
Reuse repository pacing, cache isolation and session invalidation, replacing fixed 500 ms with the user-selected random 200–500 ms terminal-completion gap. Inject randomness for deterministic tests. Do not re-enable a stopped session through lifecycle churn.

## R3: precise errors

Retain evidence fields for HTTP status and a bounded recognized business cause instead of collapsing transport facts into one display string. Keep recovery/stop decisions independent of user-facing wording.
403 without additional evidence yields a status-based message. Nonexistent/deleted/no-permission causes require explicit structured error data or a narrowly recognized error-page container. Unknown HTML is not proof of validation. Do not search arbitrary normal post prose for these keywords.
The current HTTP boundary rejects before decoding, so body-aware 403 explanations require a bounded error-body path. This design does not require a speculative parser for unseen live pages; status-only fallback remains valid.
Do not enable compatibility/account fallback when improving error categories.

## R4: navigation target

Carry target pid and optional trustworthy floor separately from query pid/author/search filters. Resolve the full tid from accepted reply data. The new reader must be a FULL query.
Use the source's validated page layout to choose a candidate and existing ArticleAnchor to verify the actual row before scrolling. If no trustworthy page exists, use bounded current-source resolution with explicit failure handling; never scan indefinitely or hardcode compatibility page size.
Keep target lifecycle bound to the reader generation/account; consume once. Search page number is not a full-thread coordinate.

## R5: mapping consistency

Use one category/name-to-file mapping for picker and main decoder; preserve immutable built-in entries and filename-based user order. The existing wrong AC pair must resolve as 赞同 -> ac42.png and 闪光 -> ac43.png.
Audit the legacy decoder and retain all existing supported aliases before removing duplicate arrays. Do not swap bitmap files, mutate posted BBCode, reset preferences or refactor unrelated formatting.

## R6: edit media presentation

Keep the editor source as the authority and overlay image spans on recognized tokens. Reconstruct previews on modify/draft restoration; asynchronously load thumbnails with bounded size and editor/token-generation checks. R5 supplies one corrected emoticon map. Serialize the original BBCode on send/save; opening an editor must not upload attachments. Preserve unknown tokens and failed images, and make media deletion operate on complete token ranges. No full HTML document or remote JavaScript enters the editor.

## Detached research

See the [R7 research delivery](../archive/2026-09/09-30-reader-native-loading-research/delivery.md) for captured failures and upstream comparison. It is outside this parent; no R7 repair, diagnostic switch or fallback removal belongs to R1–R6.

## Integration and rollback

R2 and R4 both touch ArticleListFragment; sequence their integration or give one worker exclusive file ownership. R1 may share launch-parameter plumbing with R4 and should reuse the resulting API.
R3 is independent but must keep failure behavior in the R4 flow. R5 spans lib_base_common/lib_core and the legacy app utility. R6 depends on R5 mapping and owns composer files separately.
Each child remains independently revertible; final review checks lifecycle, account ownership, source handoff, and normal reader behavior.

## Review status

R2 return behavior and the network-failure cooldown are now selected. No product-choice question remains for R1–R6. Final review is of the six-item summary and linked child artifacts; the user will open a new session for implementation. Archived R7 research is not an R1–R6 dependency or approval to implement its deferred product goal.

## Release workflow repair

The setup action accepts a packages input whose v3 default is tools platform-tools. Explicit packages: platform-tools avoids the removed legacy tools package while the subsequent pinned SDK install step still installs platforms;android-35 and build-tools;35.0.0. No continue-on-error, broad SDK upgrade or signing change.
