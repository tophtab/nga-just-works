# Research: U4 planning artifact precision review

- Query: Review the main session's U4 prd.md, design.md and implement.md for exact toolchain/platform facts, callback behavior, CI consistency and executable validation boundaries.
- Scope: internal planning review against already-persisted official and source evidence; no code, spec or other task changes.
- Date: 2026-09-30

## Findings

The proposed toolchain and scope are technically coherent. No architectural blocker was found. Four small precision fixes should be made in the planning artifacts before the combined review; none requires implementation or a new task.

### F1 — Use an XML parser for uncompiled merged manifests

- Location: implement.md:44.
- Current text groups Debug APK and Release/Preview merged manifests under apkanalyzer/aapt inspection.
- Problem: apkanalyzer manifest commands and aapt dump badging/XML-tree operate on APK/compiled resources; the selected unsigned compile/R8 path produces plain merged AndroidManifest.xml files and no Release/Preview APK. Passing that XML file to the APK inspection command is not the planned equivalent check.
- Fix: inspect the Debug APK with apkanalyzer or aapt; inspect the produced Release/Preview merged manifest files with an XML parser (Android namespace), checking package, uses-sdk minSdkVersion/targetSdkVersion, and application debuggable. Missing debuggable means false for Release. Verify actual AGP output paths after the declared manifest tasks; do not invent an APK or run package tasks to satisfy the check. compileSdk=36 is verified from effective module configuration/build evidence, not solely from uses-sdk XML.
- Severity: medium, concrete verification-command mismatch; no design change needed.

### F2 — Separate actual callback strategy tests from Android lifecycle/cancel runtime evidence

- Locations: design.md:45 and implement.md U4-A3 at :21.
- Current design already correctly states fake consumer/fallback may test production strategy and registration/lifecycle wiring is source/compile review. However, the preceding statement groups destruction and cancellation with tested consume/fallback behavior, and the acceptance row says lifecycle unregister without naming the evidence level.
- Fix: make the evidence split explicit: a JVM test executes the shared production callback strategy with fake consume/fallback and proves consumed/no-fallback, non-consumed/one-fallback, disabled-before-delegate, restoration, and unavailable-consumer behavior. Activity/Fragment view availability checks, lifecycle owner registration/removal and absence of side effects during predictive-back start/progress/cancel are statically reviewed and compiled; they are not runtime-tested by a Boolean fake. If an Android instrumentation test is added, compilation is recorded separately from execution, and device tests remain not run per project policy.
- The chosen minimal Activity-lifecycle callback is valid. ToolbarContainer.java:162–167 has private active-panel state and no observer contract. No panel-state event system or custom predictive animation is needed to preserve the existing consume-first semantics. The always-enabled callback can intercept system predictive animations on these two activities; do not claim full visual predictive-animation support merely because committed Back behavior is migrated.
- No instruction to change toolbar Up, finish(), SwipeBackHelper, drawer BackHandler or generic WebView history ownership.
- Severity: low, avoid overstating what a local fake proves.

### F3 — Qualify the platform version for legacy back suppression

- Location: design.md:36.
- Current shorthand says target36 stops legacy onBackPressed/KEYCODE_BACK delivery.
- Official scope: this suppression is for target36-or-higher apps running on Android16-or-higher, not the same APK on Android10–15. AndroidX dispatcher adoption supplies the compatible entry point across the supported min29 range.
- Fix: add '运行于 Android16/API36 及以上时' to that sentence. Source: android16-target-official.txt section predictive-back, canonical https://developer.android.com/about/versions/16/behavior-changes-16#predictive-back.
- Severity: low, official fact transcription precision.

### F4 — Remove ambiguity about running post-R8 beans in JVM tests

- Location: implement.md:46, phrase '运行U3缩减后的bean fixture'.
- Problem: this can mean running ordinary JVM fixture tests against R8-minified Android DEX, which the selected commands do not do. The following disclaimer helps, but the phrase remains ambiguous.
- Fix: say '在普通 JVM/Debug 测试类路径运行 U3 的 DTO/JSON fixture；另外检查 Release R8 mapping/合并 keep 配置/可用 usage 输出。两者都不构成 minified Android 运行验证。' If '缩减' instead means a smaller corpus, identify that corpus and retain the full U3 regression suite in the repository-wide test gate.
- Severity: low, result/evidence clarity.

### Verified aspects requiring no change

- Exact versions: AGP8.10.1 + Gradle8.11.1 + KGP/Kotlin/Compose compiler2.2.21 + JDK17 fall within the captured support matrix. Build Tools35.0.0 is the AGP8.10 supported minimum/default. The 8.9 API36 documentation discrepancy is accurately disclosed; no AGP9/Gradle9/API36.1 or AndroidX-wide upgrade is implied.
- Wrapper SHA-256 exactly matches the official saved checksum: f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6.
- CI scope correctly includes platform install35→36, target APK assertion35→36 and the local synthetic manifest fixture plus old-target rejection. Build Tools/apksigner35.0.0, Java17, min29, ID, signing, version and release routing stay intact.
- The no-signing Release R8/Preview compile plan correctly inspects the dry-run task graph before execution and does not weaken the task-name signing guard. It does not label Preview as minified.
- The two real legacy Activity entry points are PostActivity.java:95–100 and LauncherSubActivity.java:48–53. Legacy Fragment consumer methods can remain. The proposed disable/delegate/finally-restore sequence avoids dispatcher self-recursion and keeps one fallback.
- Main-source Activity/Fragment guard conditions are appropriately included; ToolbarContainer's actual consumed event only hides the panel and does not post/submit network content.
- Per-entry Insets limitations are accurately retained, including AvatarPostActivity's missing base toolbar branch, TemplateComposeActivity's direct FragmentActivity base, AboutActivity's own appbar listener and Login's separate ownership. No target36 edge opt-out is present to remove.
- Native binary evidence is correctly labelled as baseline and the plan requires a new APK ELF/ZIP check. No NDK/vendor upgrade follows merely from target36.
- All 13 settings.gradle modules currently have src/test Java/Kotlin files (counts verified by file scan), so checking test reports for all 13 is grounded in this baseline. Test counts can change through U1–U3; compare to the final task graph rather than hard-code today's counts.
- Documents consistently state planning status and do not claim a U4 build, test run, device operation, signature check or NGA request has happened.
- The combined --offline Debug/build/test/lint gate is reasonable after dependency provisioning; full online validation need not be repeated just to warm a cache. New toolchain task dependencies and lint data must be fetched before offline success is claimed.

## Files found

- ../prd.md — requirements and current planning-only status.
- ../design.md — exact versions, callback ownership, applicability conclusions and rollback.
- ../implement.md — ordered changes and verification task/manifest/native gate.
- platform-toolchain-evidence.md — supporting version/source/environment/binary evidence.
- android16-applicability-matrix.md — full official behavior-to-source mapping.
- kotlin-compat-official.txt, agp810-official.txt, android16-target-official.txt, gradle-8.11.1-sha256.txt — authoritative version/behavior/checksum excerpts.
- App PostActivity.java, LauncherSubActivity.java, TopicPostFragment.java and ToolbarContainer.java — actual legacy delegation/consume semantics.

## Related specs

- .trellis/spec/backend/android-quality-guidelines.md — all-module checks and nonblocking 'not run per project policy' device language.
- .trellis/spec/backend/local-android-signing.md — signed packaging identity and secret boundary.
- .trellis/spec/frontend/android-migration-architecture.md — bounded Java interoperability, existing routes/state preservation.

## Caveats / Not Found

This review edits only research/planning-review.md. It does not execute Gradle, sign or build an APK, run device instrumentation, fetch new dependencies, access signing material or send NGA traffic. The selected SDK36/toolchain remains unavailable locally until the authorized implementation preparation. All four findings are planning wording/verification precision fixes; they do not authorize product implementation or require new tasks.
