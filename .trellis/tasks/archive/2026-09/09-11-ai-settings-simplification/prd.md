# Simplify AI Settings and Improve Model Selection

## Goal

Make the single AI configuration quick to edit by removing repeated explanations,
discovering models automatically, and putting Save in the existing toolbar.

## Background

The user supplied seven concrete UI changes, explicitly requested a Trellis task,
and subsequently instructed the session to continue. The existing task was created
in `main` before interruption; the product implementation is on
`feature/ai-summary` in `/home/toph/nga-just-works-ai-summary`. The task now lives
with that implementation. The current screen has one configuration, manual model
entry, and an HTTPS-only client; see `research/current-boundary.md` for evidence.

## Requirements

- **R1 — Remove instructions:** remove the `使用说明` row.
- **R2 — Single configuration:** remove the `当前配置` row; no profile selector
  or duplicate status panel is needed.
- **R3 — Endpoint:** keep a short address hint and a concise editor. Accept both
  HTTP and HTTPS endpoints, including LAN addresses, without an HTTPS-only rule.
- **R4 — Key:** show a direct password input without encryption/storage prose.
  Keep a masked summary and the existing secret-handling behavior.
- **R5 — Models:** opening `模型名称` automatically fetches models using the
  current draft address and Key, without requiring a saved model. On success,
  offer the returned models and `自定义`. On failure or an empty result, allow
  manual entry; a previously fetched list for the same address/Key stays usable.
  Fetching must not overwrite a selection or custom text being edited.
- **R6 — Remove clear action:** remove the settings page's `清除配置` action.
- **R7 — Toolbar save:** replace the Save preference with a top-right action
  using the image viewer's existing save icon. Save the one whole configuration
  and show concise success/error feedback.
- **R8 — Profile action name:** rename the profile menu entry and its result
  dialog title to `AI查成分`. Keep floor summary labels and both existing prompts,
  input scopes, and dialog behavior unchanged.

## Acceptance Criteria

- [x] R1/R2/R6/R7: the page has only address, Key, model, and connection-test rows;
  the toolbar exposes the accessible Save icon.
- [x] R3/R4: endpoint and Key editors have no long help blocks or HTTPS-only,
  encryption, or local-storage explanation; HTTP and HTTPS work in the client.
- [x] R5: opening the model editor starts one discovery operation, even before a
  model is configured; selecting a returned model or entering a custom model
  updates the draft without saving automatically.
- [x] R5: failed/empty discovery leaves manual input available and retains any
  models from the same draft service; changing address/Key clears stale models.
- [x] R5: closing/pausing the editor cancels discovery and discards late results;
  arriving results never overwrite input or selection made during loading.
- [x] R4/R7: blank Key edits keep the current Key, saved secrets never appear in
  editor state, and Save persists the normalized address, Key, and model together.
- [x] R8: the profile menu and result dialog use `AI查成分`, while floor menus
  and titles retain their existing summary labels.
- [x] Existing AI configuration, transport, parser, and settings checks pass.
  Record completed checks and baseline failures; honor the user's explicit stop
  on further local builds instead of requiring the remaining Gradle/lint gates.

## Current User Instructions

- The user explicitly authorized a bounded live check of a supplied LAN model
  service using a temporary Key. Exercise model discovery and the short connection
  test only; do not persist the Key or put it in logs/task artifacts.
- The user declined further local builds. Stop remaining local Gradle build,
  lint, and test runs; retain already completed results and source review.
- After the requested changes, commit, finish work, and push `feature/ai-summary`.
  This is explicit authorization for those delivery steps.

## Out of Scope

Multiple configurations, new AI providers/protocols, changes to summary prompts
or encrypted storage, a Compose migration, manual release publication, and device
work are outside this task. Keep the existing connection-test action with concise
copy. The requested push uses the feature branch's existing remote workflow.

## Compatibility and Validation Limits

Some compatible services do not expose `/models`; manual entry is a supported
outcome. Existing HTTPS settings continue to load without a storage migration.
Offline/fake-server checks remain the regression evidence, supplemented by the
explicitly authorized LAN model-service smoke check. Device work and live NGA
traffic are not requested.
