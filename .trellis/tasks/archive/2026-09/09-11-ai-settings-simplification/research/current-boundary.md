# Existing Implementation and Approved Delta

## Evidence

- Worktree `/home/toph/nga-just-works-ai-summary`, branch `feature/ai-summary`,
  began clean at `3aa02c2a`. Main has no AI settings implementation.
- `SettingsAiFragment.java:35` retains the existing `BasePreferenceFragment`
  navigation; `:72` maps `settings_ai.xml`; `:135` opens transient field editors;
  `:217` builds configuration; `:230` saves atomically; `:254` tests a draft.
- `settings_ai.xml:4` contains the instruction row, followed by configuration
  status, three fields, Save, Test, and Clear. These user-requested removals have
  matching source assertions in `AiSettingsContractTest.java:25`.
- `strings_ai_settings.xml:11` has HTTPS-only copy, and `:19` contains the Key
  explanation. Remove the explanations while retaining transient secret hygiene.
- `AiConfig.java:49` enforces HTTPS; `AiSummaryClient.java:196` permits cleartext
  only for its local-test seam. `network_security_config.xml:10` already allows
  cleartext at Android level. Supporting LAN HTTP requires changing both validators
  and the isolated client, not merely the labels.
- `AiSummaryClient.java:73` currently supports only Chat Completions POSTs.
  `AiResponseParser.java:14` owns the safe/bounded response decoding boundary.
- `menu_image_zoom.xml:6` and `btn_ic_save.xml:1` identify the requested save icon.
  `TopicHistoryFragment.java:50` and `:107` demonstrate Fragment toolbar menus;
  `LauncherSubActivity.java:16` already sets up a toolbar for Preference screens.

## Required Invariants

Reuse existing error classification, size/deadline bounds, parser, isolated client,
and single-use POSTs. Do not require a saved configuration/model to GET models.
Use current draft address/Key with saved-Key fallback, matching Test and Save.
Clear discovered models when service identity changes; failed refresh keeps the
same-service list. Dialog dismissal must cancel requests and reject stale results.
Preserve pending custom input while the automatic discovery result arrives.

The existing spec's HTTPS-only and visible explanatory-copy requirements are
superseded by this task's explicit user requirements. All storage and credential
isolation requirements remain. The shared legacy editor colors are
`editor_text_color`, `editor_hint_color`, and `editor_background`; use the active
dialog theme for list and status content and retain a scrollable bounded dialog.

## Validation Scope

The initial scope was fake-server/JVM checks and the project compile/lint gates.
In the resumed conversation, the user authorized a temporary-Key LAN model-service
smoke check, then explicitly stopped further local builds. Preserve completed
validation and source review; do not start further Gradle/lint/test runs. No
device work or live NGA traffic is requested. The subsequent profile label rename
is resource-only, and commit/finish-work/push are explicitly authorized.
