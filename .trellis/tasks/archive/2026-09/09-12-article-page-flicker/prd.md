# Retain article content when switching pages

## Goal

Remove the unnecessary body reload that makes an already loaded topic page
briefly flash when the user swipes forward or back.

## Background

- The user observes a refresh-like flash when swiping between topic pages.
  The preceding conversation presented the retained-content fix; the user
  delegated the task/workflow decision with “你自己判断”.
- Resuming a READY page redelivers its retained response and unconditionally
  rebinds every row. This redundant list-binding path is confirmed in source;
  device-visible causality is unverified. Follow-up investigation in
  `09-12-reader-refresh-stability` corrected the original reload explanation:
  `LocalWebView` skips equal HTML, so binding does not necessarily reload it.
  Fresh-response destruction of those views was a separate remaining cause
  of unnecessary body loading during manual refresh.
- This is a lightweight bug fix with settled requirements and no unresolved
  product decisions. A PRD and curated research are sufficient.

## Requirements

- R1: Re-entering a retained online or cached page, including a page rendered
  by background prefetch, must preserve its displayed body and reading
  position when the accepted response and known topic owner are unchanged.
- R2: Initial delivery, a fresh response after explicit refresh, and restoring
  retained data into a recreated view must still render normally.
- R3: Foreground title/menu updates and pending floor/PID navigation must work
  on entry. Old account/source/generation data must remain rejected, and new
  responses must still invalidate stale AI summary/menu ownership. A blacklist
  toggle must update its affected row without relying on later page entry.
- R4: Request reuse, refresh indicators, loading-tip completion, foreground
  errors, and final-page prefetch exclusion must retain their behavior.

## Acceptance Criteria

- [x] Repeated entry with the same accepted response, topic owner and surviving
  view does not trigger a full body-list rebind (R1).
- [x] An offscreen-rendered prefetched page remains intact on selection (R1);
  a fresh response and a recreated view both render (R2).
- [x] Foreground title, menus and anchors remain correct; stale reader data
  remains rejected, and a changed blacklist flag updates its current row (R3).
- [x] Existing page-request/prefetch, article UI, AI lifecycle and loading-tip
  regressions pass (R3, R4).
- [x] Debug build and repository Debug unit tests pass; all Android lint XML
  reports have zero Error/Fatal issues. Verification distinguishes offline
  evidence from device-visible behavior.

## Scope and Constraints

Limit edits to retained article-page rendering and appropriate regression
coverage, plus its spec/task record. Do not redesign the pager, change network
or cache freshness, alter HTML/loading animations, introduce a test framework,
or touch unrelated AI edits. Device operations, live NGA traffic, publication
and push are outside this task.
