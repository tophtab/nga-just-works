# Implementation plan

## Ordered checklist

1. **Eligibility predicate** — `TabletSplit.kt`
   - Add an internal pure function
     `canSplit(screenWidthDp: Int, screenHeightDp: Int): Boolean` returning
     `screenWidthDp >= MIN_WIDTH_DP && minOf(screenWidthDp, screenHeightDp) >= MIN_SMALLEST_WIDTH_DP`.
   - Add `isTabletDevice(context: Context): Boolean` using
     `resources.configuration.smallestScreenWidthDp >= MIN_SMALLEST_WIDTH_DP`
     for the settings switch visibility.
2. **Gate rule registration** — `TabletSplit.apply(context, configuration)`
   - Compute `active = isEnabled() && canSplit(configuration.screenWidthDp, configuration.screenHeightDp)`.
   - Keep the `splitSupportStatus` guard.
   - Record the last applied signature (`"off"` or `"on:$color"`); if unchanged,
     return without touching `RuleController`.
   - When inactive call `clearRules()`; when active build and `setRules(...)`
     exactly as today.
3. **Re-evaluate on configuration changes** — `NgaClientApp.onConfigurationChanged`
   - Call `TabletSplit.apply(this, newConfig)` on every configuration change
     (the signature check makes it a no-op when nothing relevant changed).
   - Keep updating `mLastUiNightMode` only for the day/night colour path if it
     is still needed; the divider colour is now covered by the signature.
4. **Hide the switch on phones** — `SettingsFragment`
   - Add a helper that sets `findPreference(KEY_TABLET_SPLIT)` visible to
     `TabletSplit.isTabletDevice(requireContext())`.
   - Call it from `configPreference()` and `onResume()`.
5. **Remove summaries** — settings XML
   - `settings.xml`: drop `android:summary` from 预测返回手势, 平板分栏, and
     重置表情顺序; change 首页布局 from `ListSummaryPreference` to
     `ListPreference`.
   - `SettingsFragment.onPreferenceChange`: skip the
     `setSummary(getEntry())` update for `KEY_HOME_LAYOUT`.
   - `settings_ai.xml`: drop `android:summary` from the test entry;
     `SettingsAiFragment.invalidateConnectionTest` sets the summary to `null`
     instead of the idle string.
   - Delete the now-unused strings `setting_predictive_back_summary`,
     `setting_tablet_split_summary`, `ai_settings_test_summary`.
6. **Tests** — `nga_phone_base_3.0/src/test/java/.../ui/TabletSplitEligibilityTest.kt`
   - Phone portrait (393×852) → false.
   - Phone landscape (852×393) → false.
   - Tablet portrait (800×1280) → false.
   - Tablet landscape (1280×800) → true.
   - Boundary (840×600) → true; (839×600) and (840×599) → false.
7. **Validation**
   - `./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests '*TabletSplit*' --console=plain`
   - `./gradlew testDebugUnitTest lintDebug --continue --console=plain`
   - Device check only after explicit maintainer authorization; otherwise
     report "not run per project policy".

## Notes / risks

- `Application.onConfigurationChanged` must be confirmed to fire for the
  activities that declare `configChanges`; if the device check shows stale
  rules after rotation, add `TabletSplit.apply(this)` to
  `BaseActivity.onResume()` as a fallback.
- Clearing rules while a split is visible on a tablet could close the split;
  this is acceptable and matches rotating to an ineligible configuration.
- Keep `apply()` idempotent and side-effect free when the signature is
  unchanged, so repeated calls from settings and configuration changes do not
  cause activity relaunches.

## Rollback

- Single commit; `git revert` restores the previous unconditional
  registration, the setting visibility, and the five summaries.
