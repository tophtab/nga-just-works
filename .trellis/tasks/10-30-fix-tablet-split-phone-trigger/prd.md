# Stop phones from entering the tablet split path, and trim settings summaries

## Goal

On a phone (reported on Xiaomi 15 in portrait), with 「平板分栏」 enabled,
tapping a board on the home page first plays a split transition — the board
slides in from the right while the home page stays visible — then the screen
flashes and the board page takes the full screen. The device can never satisfy
the split rules, so the app must not hand any split rules to the system there.
The switch must also be hidden on non-tablet devices, and the explanatory
subtitles listed below must be removed.

User value: opening the next page on a phone is a normal single-screen
navigation with no split animation or flash; tablet users keep the existing
two-pane behaviour and can still turn it off.

## Background and confirmed facts

- Tablet split is implemented with Activity Embedding in
  `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/ui/TabletSplit.kt`.
- The rules are registered unconditionally for every device at
  `NgaClientApp.onCreate` (`NgaClientApp.java:73`). `TabletSplit.apply()`
  only checks `isEnabled()` and `SplitController.splitSupportStatus`
  (`TabletSplit.kt:57-66`); it never checks whether the current device or
  window can actually split.
- The rule constraints (`minWidthDp=840`, `minSmallestWidthDp=600`,
  portrait disallowed, `TabletSplit.kt:136-139`) are launch-time constraints.
  On the reported phone the system still starts the embedding transition
  before falling back to full screen, which produces the visible slide-in and
  flash. Preventing the rules from being registered on such devices is the
  reliable fix.
- `minSmallestWidthDp` is evaluated against the parent bounds'
  `min(width, height)` (verified in the `androidx.window` 1.5.0
  `SplitRule.checkParentBounds` bytecode), not against a device category.
- `NgaClientApp.onConfigurationChanged` re-applies the rules only when the
  system day/night mode changes (`NgaClientApp.java:95-105`), so rotation,
  fold/unfold, and window-size changes do not re-evaluate eligibility.
- Settings visibility is configured in `SettingsFragment.configPreference()`.
- The three new entries came from `eda3eb8e` (6.3.2). 「首页布局」 uses
  `ListSummaryPreference`, whose `onBindViewHolder` always shows the selected
  entry as the summary, and `SettingsFragment.onPreferenceChange` also sets
  the entry as summary for every `ListPreference`.
- 「重置表情顺序」's summary is a literal in `settings.xml`;
  `ai_settings_test_summary` ("测试当前配置") is declared in `settings_ai.xml`
  and restored by `SettingsAiFragment.invalidateConnectionTest`.

## Requirements

- R1: Register split rules only when the current configuration can support a
  split: `screenWidthDp >= 840` and
  `min(screenWidthDp, screenHeightDp) >= 600`. Otherwise clear any registered
  rules.
- R2: Re-evaluate eligibility whenever the relevant configuration changes
  (rotation, fold/unfold, window size), not only when the day/night mode
  changes. Skip `setRules`/`clearRules` when the desired state (registered or
  cleared, plus the divider colour) has not changed.
- R3: Show the 「平板分栏」 switch only on tablet-class devices
  (`smallestScreenWidthDp >= 600`); hide it on phones. Re-evaluate the
  visibility when the settings screen resumes so a fold/unfold is reflected.
- R4: Remove the subtitle text under these settings entries:
  - 预测返回手势
  - 首页布局 (the current-entry text)
  - 平板分栏
  - 重置表情顺序
  - AI 设置 › 测试连接's idle description ("测试当前配置")
- R5: No regression for tablets: on a landscape tablet that meets the
  thresholds, the existing split pairs and draggable divider still work, and
  the switch still turns them off.
- R6: The AI connection test keeps its transient progress/result feedback
  (正在测试连接 / 连接成功 / 失败); only the idle description is removed.

## Acceptance Criteria

- [ ] AC1 (R1, R2): On a phone with `smallestScreenWidthDp < 600`, no split
  rules are registered in any orientation; opening a board from home uses a
  normal single-screen transition with no split slide-in or flash. Verified on
  a device when the maintainer authorizes it.
- [ ] AC2 (R1, R2, R5): On a landscape tablet meeting the thresholds, rules are
  registered and the documented split pairs work. Rotating to portrait (or any
  ineligible configuration) clears the rules; rotating back restores them.
- [ ] AC3 (R3): 「平板分栏」 is absent from 外观设置 on a phone and present on a
  tablet.
- [ ] AC4 (R4, R6): The five listed entries show no summary subtitle; the AI
  test still shows progress and result text while running and after it ends.
- [ ] AC5: A JVM unit test covers the eligibility predicate (phone portrait,
  phone landscape, tablet portrait, tablet landscape, 840/600 boundaries).
  `./gradlew testDebugUnitTest lintDebug --continue --console=plain` passes.

## Constraints

- Do not change the split pairs, split ratio, divider behaviour, or
  `FinishBehavior` for eligible devices.
- Keep the manifest embedding properties unchanged unless device evidence shows
  they trigger the transition on their own.
- Per project policy, ADB and device operations require a fresh, explicit
  maintainer request. Without it, report device verification as "not run per
  project policy".

## Out of scope

- Redesigning the tablet layout or adding new split pairs.
- Moving the settings UI to Compose or restyling unrelated entries.
- Changing the AI connection test itself.

## Open questions

- If configuration callbacks prove insufficient for window changes while the
  app is in the background, also re-apply on activity resume; decide from
  device evidence.
