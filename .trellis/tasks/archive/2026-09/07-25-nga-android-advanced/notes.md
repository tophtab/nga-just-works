# Implementation continuation — 2026-09-06

## Restored approval

- The requested Codex session `01a075fa-2e07-7ca0-abc2-472dbcf7b45a` is a fork of
  `01a073b7-aedb-7400-b924-22c1b2a5b538` and contains only a continuation request
  followed by a provider error.
- The parent records the explicit instruction: “继续 07-25-nga-android-advanced，
  按已批准方案开始实施。” Its task artifacts are the approved BYOK settings,
  clicked-floor summary, and viewed-user summary plan.
- This session resumed the existing task with `task.py start`; no new task was
  created. There were no AI product code changes before this implementation.

## Change boundary

- Core owns one validated configuration, Keystore-backed encrypted storage,
  and a dedicated cancellable Chat Completions client.
- Settings owns the existing first-level navigation entry and a second-level
  page with safe secret input, save/clear, and connection testing.
- Summary owns immutable inputs, bounded first-page profile reads, the shared
  dialog/controller, and thin integrations with the floor/profile menus.
- Pure Java boundaries support offline JVM tests; Android UI/storage adapters
  remain in the app module. No shared NGA transport refactor is required.
- There were 51 dirty paths at session start: 39 Trellis runtime/template paths
  and 12 task planning/research paths. They are outside the product-code commit.
  The proposed documentation commit explicitly lists the inherited task files;
  the unrelated 39 Trellis paths stay outside all proposed commits.

## Integration findings

- `ArticlePagerAdapter` uses `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT` with two
  retained offscreen pages. Cancel summary work in the source fragment's
  `onPause`; `onStop` alone misses page switches.
- `TOPIC.LIST` already carries first-page reply text in `__T.<row>.__P.content`
  with its author and date. Read this bounded response directly; no whole-topic
  fetch or shared converter/logger change is needed.
- Retrofit declares OkHttp 3.12, but both `debugRuntimeClasspath` and
  `debugUnitTestRuntimeClasspath` resolve OkHttp 4.12 through other production
  dependencies. Match MockWebServer to the resolved runtime. A `503` with
  `Retry-After: 0` needs one-shot request semantics as well as disabled
  connection retries; assert a single server request and preserve its status.

## Verification boundary

- Use fabricated text/credentials, fake sources and local MockWebServer only.
- Run app build/JVM tests, all-module lint (inspect XML Error/Fatal counts),
  and repository debug unit-test diagnostics.
- No ADB or device validation was requested; record it as not run per project
  policy. No remote API/NGA request or release publishing is part of this work.

## Review and handoff

- App JVM gate: 228 tests, no failures/errors/skips; Debug App and Android test
  APKs built. All 13 module lint reports have no Error/Fatal issues.
- Repository diagnostics reproduced the documented `lib_bu_statistics` JUnit
  and `lib_module_debug` KAPT example-test failures; those modules are unchanged.
- Independent review verified the NGA timeout and malformed Content-Type fixes,
  and AAPT2 proved the XML settings entry keeps its reflected class name.
- The new AI contract records callback ownership, bounded input/response parsing,
  one-shot model POSTs, charset handling, and retained-pager pause cleanup.
- `verification.md` records exact evidence and limits. `commit-plan.md` lists
  the two work commits and the excluded dirty paths. The user replied “行”,
  approving the whole batch and subsequent archive/journal. Product code is
  committed as `edd69f21`; task status stays `in_progress` until archive.
- The six inherited research documents remain dated source evidence. Earlier
  system/local-model and chat suggestions are superseded by the approved PRD;
  preserving them does not add those features or revalidate outside services.
