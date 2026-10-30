# Centered narrow window at launch, then a parallel-window split

## Question (2026-10-30)

The user wants, on a landscape tablet:

1. Opening the app should first show a **centered narrow window** — roughly half
   the screen wide with blank space on *both* sides — instead of covering the
   whole tablet.
2. The first navigation that opens a second page should then use the existing
   **parallel window** (left | right two-pane) layout.

This note records what that behaviour is called, what the current app actually
does, which mechanisms can produce it, and what must be verified before any
implementation is planned. It is research only; nothing here is an approved
design.

## Confirmed reference (user, 2026-10-30)

The user confirmed that the behaviour they saw is the **vendor parallel-window
feature's single-pane state**: the app starts as a centered narrow window, and
opening a page switches that same feature into its parallel view (平行视界 /
平行窗口) two-pane form. It is one vendor capability with two states, not two
separate features the user combined. The exact device model was not named.

Consequence: the target experience already exists on supported vendor tablets,
but this project explicitly opts out of vendor rules
(`ALLOW_SYSTEM_OVERRIDE=false`, `AndroidManifest.xml:40-42`). The app therefore
gets neither state from the vendor; it only reproduces the second state with its
own `SplitPairRule`s.

Answer to the original question: **yes, the thing exists** — it is the
parallel-window feature's single-pane state, not a separate "centered window
mode". The app currently has only the second half of it.

## Terminology

| Layer | Name |
| --- | --- |
| Centered, floating, half-width window | **自由窗口 / 浮动窗口** (freeform window). Vendor names: 小米「小窗 / 窗口化」, 三星「弹出视图」, 华为「悬浮窗 / 自由多窗」. The user's single-pane state belongs to the parallel-window feature; whether that vendor implements it as a freeform window internally is **not verified** |
| Two panes after navigating | **列表-详情（list-detail）双窗格**, implemented with Android **Activity Embedding** (`SplitPairRule`). Vendor names: 小米「平行窗口」, 华为「平行视界」, OPPO「平行视窗」 |
| The combined "narrow first, then split" experience | No standard industry name; vendors market it under the parallel-window feature name |

Two mechanisms must not be confused:

- **Placeholder split** (`SplitPlaceholderRule`) fixes the app to one half and
  puts a placeholder activity in the other half. The app pane is left or right,
  never centered, and the empty area is a real pane.
- **Freeform window** is a window floating above the screen; the space around it
  is whatever is behind the app, not a placeholder pane.

## Current app behaviour (verified at `a365d284`)

- Split is implemented with Activity Embedding only. `TabletSplit.kt:99-113`
  registers seven `SplitPairRule`s; there is **no `SplitPlaceholderRule`**
  anywhere in the app.
- Rules are applied only when `isEnabled() && canSplit(...)`
  (`TabletSplit.kt:85-97`); `canSplit` requires landscape, width ≥ 840dp and
  short side ≥ 600dp (`TabletSplit.kt:64-67`).
- Embedding splits need a **second activity in the same task**. A cold launch
  starts `MainActivity` alone (launcher at `AndroidManifest.xml:43-60`), so it
  fills the tablet. The split appears only when the next-level activity starts;
  both panes are 50/50 (`SPLIT_RATIO`, `TabletSplit.kt:49`).
- `PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE=false`
  (`AndroidManifest.xml:40-42`) also prevents vendor rules (HyperOS 平行窗口)
  from being added.

Conclusion: **today the app opens full screen; the centered narrow first state
does not exist.** The second state (two panes after navigation) already works.
A resumed task that still holds `Main | TopicList` opens already split, but that
is task restoration, not a cold launch.

## Mechanism options

### A. Vendor parallel-window rules (re-enable system override) — closest to the reference, device-limited

This is the mechanism that produces the exact reference behaviour: one vendor
feature owning both the single-pane state and the two-pane transition. It was
deliberately turned off because vendor rules stacked on top of the app's rules;
re-enabling it would mean **choosing** vendor rules over the app's own
`SplitPairRule`s on supporting devices, not running both.

Limitations: only works on OEMs that implement the feature; single-pane styling,
transition timing and back behaviour differ per vendor; and it would replace
rather than extend the app's split pairs. It is still the cheapest route to the
exact UX on supported tablets, and the only route to a genuine single-pane state
without drawing one in-app.

### B. `SplitPlaceholderRule` — does not meet requirement 1

It produces app | placeholder (left half or right half plus a pane), not a
centered window with space on both sides. Rejected for the described
single-pane state.

### C. Real freeform window requested by the app — not generally available

- Freeform windowing is device/OEM-controlled
  (`FEATURE_FREEFORM_WINDOW_MANAGEMENT`, desktop/DeX-style modes). The public
  `ActivityOptions` API has no way to force an activity into freeform; the
  windowing-mode setters are not public app API.
- A freeform task and an Activity Embedding split are different window modes.
  Transitioning from a freeform window into an embedded split is not something
  normal app code controls.
- Confidence: high on the public-API constraint. The user's single-pane state
  belongs to the vendor parallel-window feature; whether that vendor uses
  freeform internally is **not verified**.

### D. In-app centered narrow "window" (vendor-independent fallback)

- Keep the activity full screen; when the configuration is an eligible tablet
  landscape, render the home content inside a centered container at roughly
  half width, with window styling (surface, rounded corners, elevation/shadow,
  scrim around it).
- On the first navigation covered by a `SplitPairRule`, the existing embedding
  split takes over at 50/50 — which is exactly requirement 2.
- It reuses `canSplit` as the single eligibility gate, so the launch
  presentation and the split can never disagree (the same class of bug as the
  phone flash fixed in `10-30-fix-tablet-split-phone-trigger`).
- Limitation: this is a **visual** window. The area around it is the app's own
  background, not the home screen or another app. Whether that is acceptable is
  the main product question.

### E. Combination

D for the launch state with the embedding split unchanged for the second state.
No change to the rule pairs is required for requirement 2.

## Impact on existing code, if D is chosen

- `TabletSplit` rules stay as they are; requirement 2 already works.
- A launch-time layout branch is needed in the home screen (a centered
  container gated by the same `canSplit` check), plus a transition when the
  first split starts.
- The centered state would follow `canSplit`, i.e. only landscape tablets
  ≥ 840dp wide with a ≥ 600dp short side. Portrait tablets keep full width and
  never split, as today.

## Open questions for a later implementation (not blocking this note)

1. ~~Which device and app was the reference behaviour seen on?~~ Answered
   2026-10-30: it is the vendor parallel-window feature's single-pane state.
   The device model is still unknown; it matters only for verifying option A.
2. Must the space around the narrow window show the desktop / other apps (a
   true system window), or is a centered card on the app's own background
   enough? If a true window is required, option C must be investigated on the
   target device first, and the transition to embedding may be impossible.
3. Should the narrow window be draggable/resizable, or fixed centered?
4. Transition design: animate expand-and-split, or switch immediately?
5. Behaviour in portrait, folded/unfolded foldables, and Android multi-window.
6. Does the existing 「平板分栏」 preference also govern the centered launch
   state, or should the two be separable?

## Evidence / repro

- `git show --stat eda3eb8e` — the split feature.
- `git show --stat a365d284` — the phone-trigger gate and the system-override
  decision.
- `TabletSplit.kt`, `.trellis/spec/frontend/activity-embedding-split-contract.md`,
  `AndroidManifest.xml:33-42`.
- `grep -rn "PlaceholderRule" nga_phone_base_3.0` → no app usage.

## Verification needed before calling this settled

- Device evidence of the current launch state on a landscape tablet (expected:
  full screen).
- Device evidence of the reference behaviour on the user's actual tablet: the
  vendor single-pane state, and whether the space around it shows the launcher
  or the app's own background.
- Visual and transition checks on a real tablet; JVM tests cannot judge layout.
