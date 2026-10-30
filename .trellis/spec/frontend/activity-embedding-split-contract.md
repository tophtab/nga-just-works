# Activity Embedding Tablet Split

## 1. Scope / Trigger

Use this contract when changing `TabletSplit`, the split-rule pairs or
attributes, `android.window.PROPERTY_ACTIVITY_EMBEDDING_*`, or the 「平板分栏」
preference. It covers the Android 12L+ two-pane layout implemented with
Jetpack Activity Embedding.

The trigger for the eligibility gate is user-visible: registering split rules
on a configuration the system will reject still starts the embedding
transition first and then falls back to full screen, so a phone shows the next
page slide in as a right pane and flash back. Gating registration is what
prevents that, not the rule's own `minWidthDp` / `minSmallestWidthDp`.

## 2. Signatures

```kotlin
object TabletSplit {
    fun apply(context: Context)
    fun apply(context: Context, configuration: Configuration)
    fun isEnabled(): Boolean
    fun isTabletDevice(context: Context): Boolean   // @JvmStatic
    internal fun canSplit(screenWidthDp: Int, screenHeightDp: Int): Boolean
}
```

`apply` is called from `NgaClientApp.onCreate`, from
`Application.onConfigurationChanged`, and after the preference changes in
`SettingsFragment`.

## 3. Contracts

### Eligibility (must mirror all three `SplitRule` bounds)

- `screenWidthDp > screenHeightDp` — landscape. `SplitRule.checkParentBounds`
  treats `height >= width` as portrait, and the rule sets
  `maxAspectRatioInPortrait(ALWAYS_DISALLOW)`, so portrait (including a square
  window) never splits.
- `screenWidthDp >= 840` — `setMinWidthDp`.
- `min(screenWidthDp, screenHeightDp) >= 600` — `setMinSmallestWidthDp`,
  evaluated against the parent bounds' short side, not the device category.

These are the same bounds the framework checks at launch. The comment at
`TabletSplit.kt`'s `canSplit` must stay in sync if the rule builder changes.

### Registration

- Only register when `isEnabled() && canSplit(...)`; otherwise call
  `clearRules()`.
- Do not use device model/brand checks. A foldable unfolds into an eligible
  configuration and folds back out of one.
- Re-evaluate on **every** `Application.onConfigurationChanged` (rotation,
  fold/unfold, window size, day/night). Do not gate the callback on a single
  configuration dimension.
- Keep the applied state in a signature (`"off"` or `"on:<dividerColor>"`) and
  return early when unchanged. This keeps `RuleController` calls
  side-effect-free and avoids activity relaunches from repeated `/`-setting
  toggles or unrelated configuration changes.

### Settings visibility

- Show 「平板分栏」 only when
  `configuration.smallestScreenWidthDp >= 600` (`isTabletDevice`); hide it on
  phones. Re-evaluate in `SettingsFragment.onResume` so a fold/unfold on the
  settings screen is reflected.
- Show 「首页布局」(平铺 / 紧凑) by the same tablet check. On phones the home
  grid always uses the regular full-width code path; `ForumBoardView` ignores
  the stored value there, so the preference never has an effect on a phone.
- The preference stays default-on. On a phone the gate clears the rules even
  when the stored value is true.

### Manifest

- `PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED=true` is required for app rules
  on Android 15 / WindowManager extensions 5.
- `PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE=false` keeps vendor
  reverse rules (e.g. HyperOS 平行窗口) from stacking. Do not change either
  property without device evidence.

## 4. Validation & Error Matrix

| Configuration | Enabled | Result |
|---|---|---|
| Phone portrait, e.g. 393×852 | true | `clearRules()`; no split transition |
| Phone landscape, e.g. 852×393 | true | `clearRules()`; short side < 600 |
| Tablet portrait, e.g. 800×1280 | true | `clearRules()`; portrait disallowed |
| Wide tablet portrait, e.g. 1024×1366 | true | `clearRules()`; portrait disallowed |
| Square window, 900×900 | true | `clearRules()`; `height >= width` is portrait |
| Tablet landscape, e.g. 1280×800 | true | `setRules(...)` |
| Any eligible configuration | false | `clearRules()` |
| Unchanged signature | any | no `RuleController` call |

## 5. Good / Base / Bad Cases

- Good: tablet landscape, rules registered once; rotating to portrait clears
  them; rotating back registers them again without a flash.
- Base: phone with the preference stored true never registers rules in any
  orientation.
- Bad: rules registered at startup on every device, relying on the rule's
  dimension checks to reject the launch after the transition has begun.

## 6. Tests Required

`nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/ui/TabletSplitEligibilityTest.kt`
asserts `canSplit` for:

- phone portrait (393×852) and landscape (852×393) → false;
- tablet portrait (800×1280) and wide portrait (1024×1366) → false;
- square window (900×900) → false;
- tablet landscape (1280×800) and unfolded foldable (1000×700) → true;
- exact bounds (840×600) → true; one dp below either bound → false.

Device scenarios (AC1–AC4 in the owning task) still require explicit
maintainer authorization; report them as "not run per project policy"
otherwise.

## 7. Wrong vs Correct

### Wrong

```kotlin
// Registered for every device; the system starts the split transition, then
// rejects the rule and flashes back to full screen on phones.
fun apply(context: Context) {
    RuleController.getInstance(context).setRules(rules)
}
```

### Correct

```kotlin
val active = isEnabled() &&
    canSplit(configuration.screenWidthDp, configuration.screenHeightDp)
val signature = if (active) "on:${pageBackground(app, configuration)}" else "off"
if (signature == lastAppliedSignature) return
lastAppliedSignature = signature
if (active) RuleController.getInstance(app).setRules(rules)
else RuleController.getInstance(app).clearRules()
```
