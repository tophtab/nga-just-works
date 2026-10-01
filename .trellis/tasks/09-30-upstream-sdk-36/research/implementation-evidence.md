# U4 implementation evidence

Baseline: `c2131fe9`, including the merged `c768a3cb` reply-search fix. Implementation is in `/home/toph/nga-just-works-upstream-adoption` only.

## Changes

- All 13 Android modules inherit compileSdk 36; app targetSdk 36/minSdk 29. AGP 8.10.1, Gradle 8.11.1 (official SHA-256 pinned), Kotlin/KAPT/Compose compiler 2.2.21. JDK/bytecode 17, Build Tools 35.0.0, business/native dependencies and app identity remain unchanged.
- PostActivity and LauncherSubActivity register the same `FragmentBackCallback` with their Activity lifecycle. Current nonnull/added/view-present fragment consumes first; otherwise callback disables itself, delegates once to dispatcher, restores prior enabled state in finally. Legacy Activity overrides removed; fragment consumer methods, editor drafts, toolbar Up, SwipeBack, drawer and WebView history remain unchanged.
- Four tests execute production callback logic with fake availability/consumer/fallback. They cover consumed, declined, view-unavailable and throwing fallback. Android lifecycle/predictive animation execution is not claimed.
- CI installs platform 36 and asserts target 36. Python workflow fixture rejects explicit target 35 (and 34). Existing mirrored JVM `ReleaseWorkflowContractTest` SDK assertions/name were also updated after the first test run identified that remaining hardcoded contract.

## Validation

- Separate dependency-only online preparation (`help` and temporary `u4Prefetch` init task) fetched new toolchain artifacts. Initial offline attempts exposed lazy AAPT2 `8.10.1-12782657:linux`, Kotlin build-tools `2.2.21`, and lint-gradle `31.10.1`; these were prefetched without an online full build. Temporary prefetch variant-attribute errors concerned the diagnostic resolver, not product source.
- Final `assembleDebug testDebugUnitTest --continue --offline --console=plain` passed. 820 tests in 13 modules, zero failure/error/skip; app 707, including 4 new callback tests. Per-module counts: `tests-debug.json`. Log: `/tmp/u4-debug.log`.
- `python3 -m unittest discover -s scripts`: 37 passed, including the real workflow Bash synthetic target35 rejection; `/tmp/u4-python.log`.
- Fresh `lintDebug --continue --rerun-tasks --offline --console=plain` passed: 538 tasks executed, all 13 XML reports present with 0 Error/Fatal (826 warnings). `lint-debug.json`, `lint-gate.log`.
- Fresh Debug APK metadata: `debug-apk-badging.txt`. APK 50,127,573 bytes, SHA-256 `4ba2a71bcf6bedf85a5a4e4daebafd22d720cd2132b837368a464183f1ed227b`. Application ID `com.github.tophtab.ngajustworks.debug`, version fallback 4.5.0/4050, min29/target36, debuggable. CI retains tag-derived publication versions.
- Native inspection: `check-native-apk.py` and `native-debug-apk.json` assert exact three arm64 libraries, ELF64 AArch64 LOAD alignment/congruence and uncompressed 16KB ZIP offsets. Bugly/Umeng LOAD alignment 64KB; graphics-path 16KB. Build Tools 35.0.0 `zipalign -c -P 16 -v 4` passed (`zipalign-debug.log`). No native dependency change.

## Release/Preview guard and final actual gates

Actual new-toolchain dry-run graphs are preserved in `u4-r8-dry-run.log` (300 tasks) and `u4-preview-dry-run.log` (230 tasks). The unchanged signing guard rejects respectively `:nga_phone_base_3.0:packageReleaseResources` and `:nga_phone_base_3.0:packagePreviewResources`. Neither graph contains APK/AAB packaging, signing or validateSigning tasks. At that initial preflight no signing credentials were accessed; the guard was not modified/bypassed.

Standalone `processReleaseMainManifest` + `processPreviewMainManifest` dry-run and execution passed without credentials/guard exceptions. `manifest-identities.json` confirms min29/target36 and unchanged production IDs/debuggable false/true; `u4-manifest-dry-run.log` and `u4-manifests-classpath.log` capture commands' graphs and effective compile36/Java17 across 13 modules (lib_base_ui still intentionally has no targetSdk declaration).

New AGP bundled R8 **8.10.24** production-bean classfile probe passed all 6 immutable cases. Reused U3 `research/probes/b2-r8/run.py`, `ReflectionProbe.java`, `cases.json` unchanged, with current test runtime classpath and AGP8.10.1 builder jar (SHA-256 `b15ca05ef1d85fb2d69a7dae912ab93548b1cb6d7d1f190626a235694fa77bb4`). Original input beans were excluded from the post-shrink runtime libraries. Exact result/mapping/configuration: `r8-bean/`. Debug/Release runtime dependencyInsight confirms only fastjson2 2.0.59.android8; old JSON remains only the documented test oracle/processor use.

After the main session's concrete configuration-only validation request and the user's continuation, the existing signing configuration was privately loaded solely into the validation process environment. No values were printed/copied; no credentials, keystore, or local signed APK entered this worktree. Both configured dry-runs were repeated and asserted to contain no APK/AAB packaging, signing or validateSigning tasks (`u4-r8-configured-dry-run.log`, `u4-preview-configured-dry-run.log`); their graphs remained 300 and 230 tasks respectively. The existing signing guard passed without any edit or fake configuration.

- Actual `:nga_phone_base_3.0:minifyReleaseWithR8 --offline --console=plain` **passed in 3m 7s**, 261 actionable tasks (176 executed, 45 from cache, 40 up-to-date). Log: `u4-r8-app.log`. Existing vendor common 9.8.7 bytecode emits missing stack-map warnings; R8 completed with no product source or keep-rule changes.
- Actual `compilePreviewKotlin compilePreviewJavaWithJavac processPreviewMainManifest --offline --console=plain` **passed in 13s**, 193 actionable tasks (14 executed, 2 from cache, 177 up-to-date). Log: `u4-preview-compile.log`. Preview remains non-minified; no Release/Preview APK was packaged.
- `check-app-r8.py` inspects the actual app mapping/merged configuration and output DEX. All 8 selected protocol/storage beans retain their class names. Actual DEX contains JSONField annotations on all 10 ThreadPageInfo alias getters/setters and BoardEntity.getIconUrl. Merged rules retain JavaBean, Parcelable, JSON2, ARouter, Signature and annotations. `app-r8-outputs.json` records output paths/sizes/SHA-256 plus compact annotation findings; the 64 MB full mapping remains in build outputs.
- Final three merged manifests were re-inspected after these real tasks; `manifest-identities.json` records correct IDs/min29/target36/debuggable values. These static checks and the separate 6-case classfile reflection fixture do not claim minified Android runtime execution.

U4-A7/U3-A7 now have actual app R8 and Preview compilation evidence. No product code changed during these final gates; the already-passing Debug820/Python37/lint13/native checks remain applicable.

## Runtime boundary

No device/ADB/install/instrumentation execution, NGA/CDN traffic, account login, local signed APK, or publication occurred. Android 15 is the primary compatibility target; shared back strategy/source/build regression preserves its known behavior, not a claim of device validation. Predictive gesture animation/cancellation, picker, IME/rotation/large-screen and 16KB native runtime were not run per project policy.
