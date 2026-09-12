# Fix Profile AI Author-Matching Failures

## Goal

Let `AI查成分` use a profile's available public activity when its first-page
results also contain unavailable records. Preserve the boundary that prevents
another user's activity or an access-denial message from entering the prompt.

## Background

The user reports a persistent author-mismatch error, including after Retry.
Authorized live `TOPIC.LIST` reads reproduced it for the subsequently supplied
profile: its topic page contains records explicitly marked unavailable whose
author does not match the requested UID. `NgaProfilePageSource.java:275-278`
checks those records as ordinary posts and aborts the whole page.

Reply pages can also contain unavailable records whose author still matches;
those must not become summary input merely because the author check succeeds.
Current shape evidence and nonsecret counts are in
`research/author-mismatch.md`. No broader response-shape or UID change is needed.

## Requirements

- **R1 — Unavailable items:** exclude records explicitly marked unavailable by
  NGA and continue collecting the same page's available activity.
- **R2 — Correct author:** continue rejecting ordinary, unmarked records whose
  actual topic/reply author does not match the viewed UID. Do not substitute the
  logged-in account or enclosing topic owner for a reply author.
- **R3 — Partial availability:** an all-unavailable page contributes no entries;
  available activity from the other kind still permits a summary. If neither
  page contributes anything, keep the existing no-visible-content outcome.
- **R4 — Existing behavior:** retain first-page-only reads, at most 20 accepted
  entries per kind, text bounds, secret isolation, and cancellation. Keep labels,
  prompts, settings, and navigation unchanged.
- **R5 — Validation and delivery:** honor the user's no-local-build instruction;
  add regression coverage, review the parser, verify observed structures, then
  commit, finish work, and push under the existing session authorization.

## Acceptance Criteria

- [x] R1: a synthetic page preserving the observed unavailable-record shape
  reproduces the old error and permits only available records after the fix.
- [x] R1/R2: unavailable reply/body placeholders are excluded; an unmarked
  foreign author still produces an error and cannot reach the prompt.
- [x] R3: all-unavailable pages become empty input without bypassing whole-page
  access/challenge errors or suppressing available data from the other page.
- [x] R4: skipped items do not consume the 20-entry allowance; existing request,
  prompt, and lifecycle boundaries remain intact.
- [x] R5: regression cases and validation limits are recorded; no local Android
  or Gradle build is started, and no credential or real forum content is committed.

The completed criteria reflect the reviewed implementation, live response-shape
evidence, and added regression coverage. JVM regressions were not executed under
the user's no-local-build instruction; see `research/validation.md` for the exact
checks and execution limits.

## Authorized Live Check

The user explicitly supplied a Cookie, requested ignored `.temp` storage, and
named an additional profile to test. Use the supplied account snapshot and exact
HTTPS NGA origin for `TOPIC.LIST` first-page GETs only. Four initial requests
cover the account's own topics/replies and the requested profile's topics/replies;
allow two further first-page verification reads if needed. Stop on authentication
rejection, challenge, or rate limit. No redirects, account rotation, NGA writes,
device operations, or transfer of captured forum content to a model is authorized.
Retain only synthetic structures and nonsecret diagnostic metadata.

## Scope and Decisions

- Worktree: `/home/toph/nga-just-works-ai-summary`; branch: `feature/ai-summary`.
- This is a lightweight parser fix; the existing interfaces and stored data do
  not change. Research records the observed wire contract.
- The user delegated task decisions and requested direct testing. The repair is
  limited to the reproduced defect, with no unresolved product or UX decision.
- Unrelated workflow-test failures, broader access changes, pagination, and
  account storage changes are outside this task.
