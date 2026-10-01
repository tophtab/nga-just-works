# Research: U4 SDK 36 platform and toolchain evidence

- Query: Identify the supported, smallest-scope SDK 36 adoption, actual platform-sensitive code, and local verification boundary before the combined U1–U4 review.
- Scope: mixed; isolated worktree source, read-only local caches and existing APK, official Android/Kotlin/Gradle documentation.
- Date: 2026-09-30
- Worktree: /home/toph/nga-just-works-upstream-adoption
- Status: planning evidence only; no product edits, task activation, Gradle build, SDK installation, signing secret access, ADB or NGA request performed.

## Findings

### 1. Recommended concrete version set

| Component | Current source/environment | Planned version/action | Evidence and reason |
| --- | --- | --- | --- |
| compileSdk | 35, root build.gradle:123 | 36 (base API 36, not 36.1) | All 13 Android modules inherit the root property. |
| targetSdk | 35, root build.gradle:122 | 36 | Final application and test manifests checked; lib_base_ui omits its own targetSdk and does not need a redundant library declaration. |
| minSdk | 29, root build.gradle:121 | Preserve 29 | No user authorization to raise the installation floor. |
| AGP | 8.6.1, gradle/libs.versions.toml:2 | 8.10.1 | API support table states API 36 minimum AGP 8.9.1. AGP 8.9 release page still says maximum API 35; AGP 8.10 page explicitly supports API 36. Choose the 8.10 bugfix release rather than rely on that discrepancy. |
| Gradle wrapper | 8.7, gradle/wrapper/gradle-wrapper.properties:3 | 8.11.1, verified distribution checksum | AGP 8.10 minimum/default Gradle 8.11.1. Do not jump to Gradle 9. |
| Kotlin, KGP, Compose compiler plugin | Shared 2.0.21, catalog:3 and plugins:8–9 | Shared 2.2.21 | KGP 2.0.21 does not document support for AGP 8.10 or Gradle 8.11.1. KGP 2.1.21 stops at AGP 8.7.2; KGP 2.2.0–2.2.10 table stops at AGP 8.10.0. KGP 2.2.20–2.2.21 explicitly supports AGP through 8.11.1 and Gradle through 8.14, covering the selected exact versions. Shared catalog reference already aligns Kotlin Android, KAPT, stdlib/reflect, and Compose compiler. |
| JDK / JVM bytecode | JDK 17.0.20.1 installed; Java/Kotlin target 17 across modules | Preserve JDK 17 / target 17 | AGP 8.10 minimum/default JDK 17; no JDK 21 requirement. CI already selects Temurin 17. |
| Android SDK platform | Only android-35 installed locally | Provision platforms;android-36 after implementation approval | API 36 platform is absent; an offline SDK 36 build cannot run yet. |
| Android Build Tools | 34.0.0 and 35.0.0 locally; CI pins 35.0.0 | Preserve 35.0.0 | AGP 8.10 explicitly has minimum/default Build Tools 35.0.0; compileSdk and Build Tools versions need not be equal. Android 16 setup guide suggests 36.x but is generic and still contains preview-era instructions; it does not override AGP's supported minimum. |
| AndroidX and business libraries | See inventory below | No blanket upgrade | Activity 1.10.1 already supports dispatcher back. Current insets APIs and native payload do not require newer AndroidX. Re-evaluate only a concrete compiler/lint/dependency failure. |
| NDK / native build | No NDK installed; no externalNativeBuild/CMake/ndkVersion or source .so | No NDK install/configuration change | App consumes three prebuilt native libraries; AGP's default NDK version is not a requirement for this project. |

Do not add android.suppressUnsupportedCompileSdk=36, enable R8 full mode, switch Kotlin DSL, enable built-in Kotlin/AGP 9, change all Jetpack versions, or add pageSizeCompat to hide unsupported native code.

Official-document distinction: upstream 22ba3082501bcbb08f52a66d787f970f59c2dda7 only changed compile/target 35→36 plus upstream version 5.0.2/5002. It contains no toolchain/runtime adaptation evidence. The live docs fetched on 2026-09-30 contain later maintenance and QPR information, and are independent of upstream's fixed August commit. Do not copy its minSdk 30 or versions.

### 2. Exact existing dependency/build inventory

- Root build.gradle:3–14: Compose Material/runtime 1.6.8, Compose UI 1.7.0, Activity Compose 1.10.1, declared core-ktx 1.7.0, AppCompat 1.7.1, Material Components 1.12.0, lifecycle 2.6.2, Paging 3.3.0, ARouter 1.5.2, RxJava 2.2.6; root:129–131 Room 2.4.1 and Preference 1.1.1.
- App build.gradle:148–151 adds Material3 1.3.2 and Compose animation 1.6.8; :167–177 uses OkHttp 4.12.0, Retrofit 2.6.0 and Glide 4.11.0. lib_base_ui_compose/build.gradle:56 onwards exposes Coil 2.5.0 and Accompanist systemuicontroller 0.16.0. lib_base_ui adds preference-ktx 1.2.1.
- Declared versions are not the resolved graph: local cache contains core 1.13.1, Activity 1.10.1, graphics-path 1.0.1. Capture debug/release runtime dependencyInsight after approval; do not claim core resolves to the root's 1.7.0 merely from that declaration.
- AGP 8.10.1 includes an updated D8/R8 and Kotlin 2.2 compatible shrinker generation. The official Kotlin/D8 support page lists R8 8.10.21 for Kotlin 2.2; retain the version bundled by AGP rather than independently overriding R8.
- gradle.properties:36 has android.enableR8.fullMode=false; preserve it. App build.gradle:89 enables Release minification with proguard.cfg; Preview :101–105 inherits Release but explicitly sets minifyEnabled=false. Preview is not the minified release gate.
- Proguard cfg:23–29 keeps annotations/signatures/Parcelable/Serializable; :36–48 protects ARouter and both Fastjson engines; :57–58 protects RetrofitService. U3's new DTO/cache/Parcel contracts must be included in the Release review after toolchain replacement; do not globally keep every model to hide a missing rule.

### 3. Required product/configuration changes

1. Root SDK pair; catalog AGP and shared Kotlin/compiler versions; Gradle wrapper distribution/checksum.
2. .github/workflows/build.yml:139 platform installation 35→36; :191 final APK target SDK assertion 35→36. Keep Build Tools 35.0.0, :133 Java 17, :147–168 signing, application ID :187, minSdk :190 and version/release routing unchanged.
3. scripts/test_release_workflow.py:256 synthetic manifest target-sdk 35→36; run its local test suite. Existing build-tools/apksigner fixture path :177 stays 35.0.0. Include mismatch coverage so a target-35 artifact fails the new workflow gate.
4. Migrate legacy back interception in PostActivity.java:95–100 and LauncherSubActivity.java:48–53 to lifecycle-owned OnBackPressedCallback via getOnBackPressedDispatcher(). Android 16 target 36 stops delivering legacy onBackPressed / KEYCODE_BACK; this is a proven applicable behavior change, not speculative cleanup.
5. During the main-session spec update, revise current SDK declarations and runtime labels in .trellis/spec/backend/android-quality-guidelines.md:65–115 to target 36. Keep all fresh device-authorization rules and 'not run per project policy' nonblocking handoff semantics.

Back handling contract: preserve PostActivity → TopicPostFragment.onBackPressed (:99–102) → ToolbarContainer.onBackPressed (:162–167), which closes the active emoticon/formatting panel before leaving the activity. LauncherSubActivity keeps its optional legacy fragment delegation. On a non-consumed event, temporarily disable that callback, delegate once to the dispatcher, and restore enablement in finally; never recurse with an enabled callback and never call the removed legacy super.onBackPressed path. Null/unattached/destroyed fragment view must delegate safely. Use the existing Kotlin BaseActivity.kt:33–43 callback pattern as interoperability evidence, not a wholesale base-class rewrite. If panel visibility is exposed, enable the Post callback only while it can consume so system predictive animations remain available otherwise; do not make custom predictive animations part of this task. Existing Compose drawer BackHandler and Kotlin WebView history callback remain the owners of their own back behavior.

Tests for this boundary must exercise consumed/non-consumed/fallback-once, fragment unavailable, and destruction behavior, plus compile UI test sources. Pure JVM dispatcher tests can use lifecycle + AndroidX dispatcher and a no-network fake consumer; Android UI instrumentation may be authored/compiled but is not automatically executed. Do not call instrumentation a passed test merely because its APK compiled.

### 3a. Insets and back ownership by entrypoint

| Entry point | Current owner/evidence | U4 action |
| --- | --- | --- |
| MainActivity / home drawer / U1 boards | MainActivity.java:29–34 sets toolbar+Compose flags and explicitly enables edge-to-edge; Java base skips its legacy inset branch for Compose; HomeNavigationDrawer.kt:145 owns drawer BackHandler; board composable owns navigation-bar padding. | Preserve existing ownership; verify U1/drawer regression fixtures. Do not add the Java base's bottom padding around Compose. |
| TopicList / ArticleList / cached lists / Profile / Settings / notifications / ImageZoom / SignPost | Each opts into Java BaseActivity toolbar handling (for example ArticleListActivity.java:78, ImageZoomActivity.java:56, SettingsActivity.java:21); Java base applies navigation bottom padding and status overlay. Fragment XML may independently use fitsSystemWindows. | No new target36 opt-out removal to implement. Preserve theme backgrounds and existing inset owners; avoid duplicated status/padding. |
| PostActivity | Java base toolbar inset handling plus compatActivityAdjustResize at PostActivity.java:45; ToolbarContainer owns panel visibility and legacy back consume. | Mandatory system-back callback migration. Preserve existing IME behavior; a separate IME/window rewrite is not justified by target36 removing an unused opt-out. |
| LauncherSubActivity | Java base toolbar insets; optional old BaseFragment back delegation. | Mandatory system-back callback migration with nullable consumer and one fallback; no change to dynamically selected fragment/route contract. |
| AvatarPostActivity | Extends Java BaseActivity but does not call setToolbarEnabled; installs its own inflated layout at :138 and declares adjustResize. | Record that this route is not covered by the base toolbar-inset branch. This was already true under target35 with no opt-out; do not represent it as newly verified or silently rewrite the avatar product flow. |
| Login / message / profile Compose / Search / Debug activities | Extend lib_base_ui_compose BaseComposeActivity.kt:18–38 with explicit enableEdgeToEdge and ScaffoldApp; standard dispatcher remains available via FragmentActivity. | No legacy back override in these activities. Preserve their existing content and Insets; Login is not the same route as generic WebViewFragment history handling. |
| Generic Kotlin FragmentTemplateActivity / WebViewFragment | Kotlin lib_base_ui BaseActivity owns insets and dispatcher; BaseFragment.kt:17–28 callback and WebViewFragment.kt:61–68 own history. | Retain existing dispatcher/history behavior, no duplicate Activity interception. |
| TemplateComposeActivity | Extends FragmentActivity directly and mounts BaseComposeFragment from a local class name (:7–21), not BaseComposeActivity. | Preserve its fragment-owned layout/back contract; it had no edge opt-out at target35. Compile and inspect route rather than assume it inherits the Compose Activity base. |
| AboutActivity | Extends third-party MaterialAboutActivity (:28); its own status inset listener at :41–57 adjusts the app bar and requests insets. | Preserve its explicit status inset fix; no shared-base rewrite. Existing AboutActivity contract tests remain in the full Debug gate. |

This inventory proves where handling exists; it does not assert every legacy layout is visually flawless. Target36 changes no edge-to-edge opt-out in any of these paths because none existed. Insets/IME/resizing runtime is explicitly unrun, while back-entrypoint migration is independently required by the documented new event dispatch.

### 4. Native payload: direct binary evidence

No checked-in .so/CMake/native source exists. Scanned 171 cached AARs; exactly these three contain native libraries and match the existing original-workspace Debug APK arm64 inventory. App build.gradle:61–62 filters to arm64-v8a.

| Dependency | Packaged library | ELF PT_LOAD p_align values | Decision |
| --- | --- | --- | --- |
| com.tencent.bugly:crashreport:4.1.9.3 | libBugly_Native.so | 65536, 65536 | Meets ≥16384 segment alignment; retain. |
| com.umeng.umsdk:asms:1.8.7.2 | libumeng-spy.so | 65536, 65536 | Meets ≥16384 segment alignment; retain. |
| androidx.graphics:graphics-path:1.0.1 | libandroidx.graphics.path.so | 16384, 16384, 16384 | Meets ≥16384 segment alignment; retain. |

The existing original-workspace Debug APK stores all three uncompressed at offsets divisible by 16384. This is baseline evidence only, not a U4 build result. Machine-readable artifacts: native-arm64-inventory.json (AAR ELF headers), native-existing-debug-apk.json (baseline zip offsets and per-library hashes). No native libraries appeared in the existing androidTest APK.

AGP ≥8.5.1 supports correct uncompressed library zip alignment; current 8.6.1 already meets that packaging threshold. New Debug APK must still pass Build Tools 35.0.0 zipalign -c -P 16 -v 4 and a fresh ELF/ABI inventory. PT_LOAD and zip alignment do not prove arbitrary native allocation or 16 KB device runtime correctness. No device run is authorized; record it as not run per project policy. Do not set android:pageSizeCompat to suppress a warning or use 'NDK upgraded' as a substitute for inspecting third-party binaries.

### 5. Local environment and feasible validation

Read-only inspection on 2026-09-30:

- JAVA_HOME, ANDROID_HOME, ANDROID_SDK_ROOT, GRADLE_USER_HOME are unset in this process; java resolves to OpenJDK 17.0.20.1.
- Isolated worktree has no local.properties. Original /home/toph/nga-just-works/.android-sdk has only platforms/android-35; build-tools 34.0.0 and 35.0.0; cmdline-tools/latest; no NDK.
- Windows SDK path has no platform/build-tools packages; no device enumeration was attempted.
- ~/.gradle wrapper cache contains 8.7 and 8.9, AGP cache only 8.6.1, Kotlin Gradle plugin cache only 2.0.21. Selected toolchain is not cached.

After approval, provision a task-specific SDK/tool cache outside original source, or explicitly configure the existing shared SDK without altering original tracked/untracked project files. Set ANDROID_HOME/ANDROID_SDK_ROOT for the build invocation or task-owned ignored local.properties. Provision platforms;android-36 and build-tools;35.0.0, Gradle 8.11.1, AGP 8.10.1 and Kotlin 2.2.21 from official/trusted existing artifact repositories. Dependency downloads are build preparation; they do not authorize any app launch or NGA traffic. Once artifacts resolve, repeat the actual validation with --offline to demonstrate the build/test gate itself is network-independent. Do not claim current offline feasibility before provisioning.

Local validation commands for the eventual implementation (run in the isolated worktree; report not yet run now):

- ./gradlew :nga_phone_base_3.0:assembleDebug
- ./gradlew :nga_phone_base_3.0:testDebugUnitTest
- ./gradlew testDebugUnitTest --continue
- ./gradlew :nga_phone_base_3.0:lintDebug
- ./gradlew lintDebug --continue --rerun-tasks --console=plain
- ./gradlew :nga_phone_base_3.0:minifyReleaseWithR8
- ./gradlew :nga_phone_base_3.0:compilePreviewKotlin :nga_phone_base_3.0:compilePreviewJavaWithJavac :nga_phone_base_3.0:processPreviewMainManifest
- ./gradlew :nga_phone_base_3.0:assembleDebugAndroidTest plus relevant library androidTest compilation for newly authored tests (no connected task).
- python3 scripts/test_release_workflow.py
- Inspect every module lint XML and require zero Error/Fatal; abortOnError=false means the Gradle exit status alone is insufficient. Inspect test XML nonzero counts and actual failures/skips.
- Inspect Debug APK and Release/Preview merged manifests: target 36, min 29; Debug ID com.github.tophtab.ngajustworks.debug and debuggable=true; Release/Preview ID com.github.tophtab.ngajustworks, debuggable=false/true respectively. Existing version sources stay intact; no new release number assigned.
- Inspect Release R8 mapping/usage/seeds outputs for U3 DTO, Parcel, JSON reflection, ARouter routes and JS interfaces and run the U3 offline corpus. A successful shrinker task is not runtime execution of the minified binary.
- Fresh Debug APK native alignment and ABI checks as above.

Signing guard in app build.gradle:25–31 inspects this app's tasks whose names match assemble/bundle/package plus release/preview. Direct minifyReleaseWithR8 and compile/process Preview commands do not themselves match; review the dry-run task graph after the new AGP resolves and ensure no guarded packaging task appears. If AGP wiring adds one, report that specific constraint and use the remaining unsigned compile checks; do not weaken the guard or load credentials implicitly. Signed Release/Preview APK packaging remains available via the existing explicitly authorized signed-build path, not a newly mandatory follow-up. No publish/install/ADB action belongs to this implementation plan.

The exact proposed variants are compile36/target36 Debug, Release-R8 and Preview-compile. A temporary compile36/target35 checkpoint can isolate compiler/toolchain failures before enabling target behavior, but must not be delivered as the final U4 state. Device scenarios, only if separately explicitly requested, are API29 floor, API35 prior runtime and API36 target36 with gesture/3-button navigation, sw<600 and sw≥600, IME, local WebView/media and content URIs. All are currently 'not run per project policy', not delivery blockers or maintainer tasks.

### 6. Source files found and related contracts

| Source | Responsibility |
| --- | --- |
| build.gradle; gradle/libs.versions.toml; gradle/wrapper/gradle-wrapper.properties; gradle.properties | SDK/toolchain/version policy and R8 mode |
| nga_phone_base_3.0/build.gradle | Application identity, build types, signing guard, ABI and dependencies |
| .github/workflows/build.yml; scripts/test_release_workflow.py | SDK provisioning and produced APK manifest contract |
| nga_phone_base_3.0/src/main/AndroidManifest.xml; lib_*/src/main/AndroidManifest.xml | Components, external deep links, permissions and platform attributes |
| PostActivity.java; LauncherSubActivity.java; TopicPostFragment.java; ToolbarContainer.java | Legacy back interception needing migration |
| Java BaseActivity.java; Kotlin lib_base_ui BaseActivity.kt; lib_base_ui_compose BaseComposeActivity.kt; ScaffoldApp.kt | Existing edge-to-edge/insets/back foundation |
| lib_bu_statistics/build.gradle and cached AARs | Actual Bugly/Umeng native dependency evidence |
| .trellis/spec/backend/android-quality-guidelines.md | All-module lint/JVM gates, default device prohibition |
| .trellis/spec/backend/local-android-signing.md | Release/Preview identity, private key handling and build authorization |
| .trellis/spec/frontend/android-migration-architecture.md | Preserve navigation, formats, version identity; bounded interoperability instead of wholesale rewrite |
| .trellis/spec/backend/nga-platform-access-rules.md | No live NGA operation as an SDK check |

### 7. External references and snapshot scope

- Android 16 all-app behavior: https://developer.android.com/about/versions/16/behavior-changes-all (fetched 2026-09-30, page last updated 2026-09-16).
- Target 36 behavior: https://developer.android.com/about/versions/16/behavior-changes-16 (same dates); complete applicability map is android16-applicability-matrix.md.
- Exact minimum API tools table: https://developer.android.com/studio/releases#api-level-support (API36 minimum AGP8.9.1; API36.1 requires8.13.0 and is excluded).
- AGP8.10 supported SDK/Gradle/JDK/Build Tools and8.10.1 fixes: https://developer.android.com/build/releases/past-releases/agp-8-10-0-release-notes#compatibility
- Historical AGP8.9 ambiguity: https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes#compatibility
- KGP support table: https://kotlinlang.org/docs/gradle-configure-project.html#apply-the-plugin
- D8/R8 and Kotlin: https://developer.android.com/build/kotlin-support
- Compose compiler plugin: https://developer.android.com/develop/ui/compose/compiler
- SDK setup guide: https://developer.android.com/about/versions/16/setup-sdk (contains preview-era setup wording; use versioned AGP compatibility for Build Tools selection).
- 16KB requirements and zip/ELF checks: https://developer.android.com/guide/practices/page-sizes#elf-alignment and #agp_version_851_or_higher.
- Gradle checksum source: https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256 (saved separately). Verified SHA-256: f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6.

## Caveats / Not Found

- Current official pages are live and not frozen to the upstream commit. In particular, QPR2 automatic themed icons and newer privacy guidance are labelled separately in the applicability matrix. The generic /build/releases/gradle-plugin URL currently resolves to AGP9.4; it is not evidence that U4 should adopt AGP9.4.
- No product build or runtime compatibility claim is made from this planning research. Binary checks used cached AARs and an already-existing original-workspace APK, not newly-built U4 artifacts.
- No evidence warrants a broad AndroidX, Bugly/Umeng, WebView, storage framework or architecture migration. Old storage-permission gating and cached keyboard height are pre-existing source risks; they are not proven new target36 regressions and must not silently absorb the parked R7 or unrelated fixes.
- New compiler/lint/R8 failures, if found in implementation, require bounded source fixes and recorded evidence. The plan already includes the relevant gates and concrete minimum dependencies; this is not an instruction to postpone design until later.
