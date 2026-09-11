# Loading Usage Tips

## Goal

Help readers discover existing interactions through concise Chinese instructions
shown during initial loading, without restoring quotations or adding waiting time.

## Background and Evidence

Parent: `../09-11-thread-ip-location-loading-tips/`.
`list_loading_view.xml:2` covers legacy thread/topic/search initial loads;
`LoadingLayout.java:23` and `include_loading_view.xml:2` cover the reusable
notification loader. The former sayings were removed in release 5.5.0.

`research/loading-surfaces-and-copy.md` records the reachable surfaces, lifecycle
constraints, exact gesture semantics, nine source-backed strings, and the
`feature/ai-summary` integration dependency for AI configuration guidance.

## Requirements

- R1: Show one locally bundled usage tip below the existing spinner on those
  legacy initial-loading surfaces, with the same loader/background behavior.
- R2: Select once per foreground loading occasion; keep the tip stable through
  repeated visibility/resume callbacks, and avoid immediate repeats between
  occasions when multiple eligible strings exist. Background prefetch must not
  consume tips. Do not introduce a cycling timer or a minimum display duration.
- R3: Cover current-page tap-to-top, current-page long-press refresh, thread reply
  FAB long-press refresh, and board compose FAB long-press top-and-refresh.
  Additional verified tips cover title tapping and existing drag reorder actions.
  Use actual UI names and qualify online-only gestures.
- R4: Include the verified `设置 → AI 设置` instruction only when that build
  contains the real settings entry and destination. The separate AI feature's
  merge or implementation is not part of this task.
- R5: Hide the tip with the existing loading overlay on completion/error. Existing
  content-preserving pull refresh remains unchanged. Keep multiline text readable
  in light/dark themes, large fonts, and landscape, without forced focus or
  accessibility announcements.

## Acceptance Criteria

- [x] AC1/R1-R3: A visible initial load shows one accurate local tip and a later
  occasion can choose another; repeated callbacks do not change the current tip.
- [x] AC2/R2: Hidden/preloaded pages do not advance selection. Ready pages do not
  flash a tip, and tips add no delay beyond existing loading transitions.
- [x] AC3/R3: Copy describes the correct control and screen; current-page long
  press does not claim to change page or return to top.
- [x] AC4/R4: AI copy is absent from the selectable pool without the real settings
  entry, and eligible once that verified feature is integrated.
- [x] AC5/R5: Success, empty, and error states leave no residual tip. Refresh
  gestures, notification handling, and existing progress messages still behave.
- [x] AC6/R5: Resource compilation and lint pass; layout uses semantic colors,
  scalable multiline text, padding, and no truncation or automatic announcements.

Final combined verification (2026-09-12): Debug assembly, 223 app JVM tests
(including 18 loading policy/catalog tests and the retained-page lifecycle
regression), and all 13 module lint reports passed with zero Error/Fatal.
Copy and layout/lifecycle bindings were source-reviewed; no device UI run was
performed. The current build selects eight tips and excludes the AI instruction
until the real settings destination exists. See the parent's
`research/combined-check-report.md` and `research/final-validation.md`.

## Out of Scope

Quotations, remote copy, onboarding dialogs, new gestures or AI capabilities,
AI branch merging, operation-progress dialogs, WebView/media placeholders,
Compose private-message spinners, and a global UI redesign.
