# Android Quality and Instrumentation

This project is an Android multi-module application. The rules below are
implementation contracts for device tests and the validation gate; they are
not optional CI hints.

## Global device-operation authorization policy

This policy takes priority over every device-testing, release, diagnostic, and
validation instruction below:

- ADB and device operations are opt-in. Run them only when the maintainer makes
  a fresh, explicit request for the specific device test or operation in the
  current task or conversation. That authorization is limited to the requested
  scope and does not change the default for later tasks or operations.
- Without that authorization, do not invoke `adb`, `adb.exe`, `adb devices`,
  `connected*AndroidTest`, device installation or uninstallation, device
  shell, logcat, port forwarding, instrumentation, or physical-device/emulator
  E2E flows. Do not probe for a device, start or switch an ADB server, wait for
  a connection, or ask the maintainer to connect a device.
- A device being online or otherwise available is not authorization. A device
  gate merely inherited from an existing task, PRD, design or implementation
  plan, checklist, or report is not fresh maintainer authorization, even when
  that artifact or task is still active or its gate is copied into a current
  task. Require a current maintainer request before running it. A genuinely
  explicit maintainer request recorded in the current conversation or in a
  current/new task remains valid.
- Device-independent Gradle work remains part of the default gate. It is
  permitted to compile `androidTest` sources or build test APKs without
  installing or executing them. Continue to run applicable builds, JVM unit
  tests, lint, static checks, and offline tests.
- Report an unauthorized device test as "not run per project policy." This is
  neither a failure nor a delivery blocker, and it must not become a follow-up
  task that the maintainer must complete before handoff.
- After explicit authorization, the exact-serial, Windows ADB transport,
  traffic-safety, security, and per-device reporting rules below apply. Never
  trigger real NGA traffic. These rules constrain an authorized run; they do
  not authorize one.

## Scenario: Library instrumentation tests

### 1. Scope / Trigger

Any Android library module that owns `src/androidTest` must declare the same
AndroidX runner used by the application. A library test APK is installed and
run independently, so the application module's runner does not propagate to
it.

### 2. Signatures

In each library's `android { defaultConfig { ... } }` block:

```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

The application uses the identical fully-qualified runner name.

### 3. Contracts

- Test package startup must use `androidx.test.runner.AndroidJUnitRunner`.
- JUnit 4 tests are discovered from the Android test APK and report their
  actual count; a zero-test result caused by a runner startup failure is not a
  pass.
- The release declarations are `minSdk 29`, `compileSdk 35`, and
  `targetSdk 35`. For explicitly authorized device validation, API 35 is the
  primary runtime gate. API 29 is the declared installation-floor smoke
  target; API 36 may run the target-35 APK as a separately labelled
  forward-compatibility check.
- For explicitly authorized local device verification, prefer the physical
  device named by the maintainer. Always pass its exact `ANDROID_SERIAL`; do
  not silently substitute an emulator when the physical device disconnects.

### 4. Validation & Error Matrix

The device-runtime rows in this matrix apply only after the maintainer has
explicitly authorized the corresponding operation.

| Condition | Required result |
| --- | --- |
| Runner is absent in a library module | Fix the module Gradle config; do not accept the platform fallback |
| UTP reports `android.test.InstrumentationTestRunner` | Treat as configuration failure |
| Process crashes/ANRs before test discovery | Inspect logcat and fail the quality gate |
| API 35 primary test fails | Fix or explicitly document an external blocker; never mask/skip it |
| API 29 floor smoke fails | Fix before claiming Android 10 support, or explicitly narrow the published installation floor |
| Target-35 APK fails on API 36 | Record a forward-compatibility finding; do not claim target-36 certification |
| Test report has zero tests unexpectedly | Investigate runner/package wiring |
| A physical device disappears during install or test | Classify the run as an ADB/environment blocker, preserve the partial report, and stop without waiting for or requesting reconnection; do not relabel it as a product pass |

### 5. Good/Base/Bad Cases

- **Good**: every test APK uses `AndroidJUnitRunner`. In an explicitly
  authorized device matrix, API 35 reports the full expected suite, API 29
  completes the minimum install/core-flow smoke, and an API 36 run is labelled
  `target35-on-api36`.
- **Base**: A module without `src/androidTest` may omit the runner until it
  gains device tests.
- **Bad**: Relying on the default `android.test.InstrumentationTestRunner`.
  On API 35 this can leave the instrumentation process in startup until the
  system kills it as an ANR.
- **Bad**: Running a broad connected-device task while both a developer phone
  and an emulator are attached; results can be attributed to the wrong device.

### 6. Tests Required

- When the maintainer explicitly authorizes the API 35 instrumentation matrix,
  run it against the exact authorized serial and assert every module reports a
  finished test count with zero failures. Follow the Windows ADB rules below;
  do not assume a Gradle `connected*AndroidTest` task is an allowed transport.
- When the maintainer explicitly includes API 29 release-device validation,
  run a focused install and core-flow smoke there. This replaces the abandoned
  API 26 matrix; a release event by itself does not authorize the run.
- When the maintainer explicitly includes API 36 forward-compatibility
  validation, run the target-35 APK there and label the report
  `target35-on-api36`; device availability alone does not authorize this, and
  it does not replace a future target-36 gate.
- For every library test APK, inspect the UTP configuration or manifest when
  diagnosing a startup failure; the runner must be AndroidX.
- Keep a focused regression test for each security-sensitive UI policy (for
  example, the Web login origin allowlist).
- For local runs, record the device API/model and keep one XML report per
  serial (for example, `device-reports/api33-real-phone/`); never overwrite a
  physical-device report with a later emulator run.

### 7. Wrong vs Correct

#### Wrong

```kotlin
android {
    defaultConfig {
        minSdk = 29
        // No runner: the library test APK falls back to android.test.*
    }
}
```

#### Correct

```kotlin
android {
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
```

## Validation gate

Before handing off an Android product change, run:

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue
```

The app restores upstream `abortOnError false` and disables
`MissingTranslation`, so a zero lint process exit is not sufficient by itself:
inspect every Android module's generated lint report and require zero `Error`
and zero `Fatal` issues. An application-only report does not cover independent
library findings. The inherited 11-error app baseline and the remaining
library error were explicitly remediated on 2026-08-10; do not reclassify a new
error as accepted upstream debt. Warning count is diagnostic and may change
independently of this zero-error contract.

```bash
python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
from pathlib import Path

settings = Path("settings.gradle").read_text(encoding="utf-8")
modules = re.findall(r"include\s+['\"]:([^'\"]+)['\"]", settings)
reports = {
    module: Path(module) / "build/reports/lint-results-debug.xml"
    for module in modules
}
missing = [module for module, report in reports.items() if not report.is_file()]
blocking = {
    module: [
        issue for issue in ET.parse(report).getroot().findall("issue")
        if issue.get("severity") in {"Error", "Fatal"}
    ]
    for module, report in reports.items()
    if report.is_file()
}
blocking = {module: issues for module, issues in blocking.items() if issues}
if missing:
    raise SystemExit(f"Missing Android lint reports: {missing}")
if blocking:
    raise SystemExit(
        "Android lint blocking issues: "
        + ", ".join(f"{module}={len(issues)}" for module, issues in blocking.items())
    )
print(f"Verified {len(reports)} Android modules: 0 Error / 0 Fatal")
PY
```

If a pinned legacy layout intentionally keeps a shape that a generic Lint rule
cannot model, preserve runtime attributes and use only a documented,
element-local `tools:ignore`. Do not disable the rule for the file, module, or
project merely to keep the report green.

Use the repository-wide `testDebugUnitTest --continue` task as the unit-test
gate rather than the aggregate `test` task: the latter enters
release/preview unit-test task graphs and trips the release-signing guard even
though no local signed APK packaging is authorized. The Debug gate must pass;
historical example-test failures are no longer accepted baseline exceptions.

Every module whose `src/test` sources import JUnit 4 must declare its own JUnit
`testImplementation` dependency, for example
`testImplementation 'junit:junit:4.13.2'`. A dependency on another project does
not inherit that project's test dependencies. Keep JUnit on the
test classpath; do not add it as a product dependency or disable test variants
to hide a missing dependency.

The `lib_bu_statistics` Java example and `lib_module_debug` Kotlin example had
the same missing-JUnit cause. Java reported `package org.junit does not exist`;
KAPT represented the unresolved `org.junit.Test` annotation as
`@error.NonExistentClass()`, then failed with
`NonExistentClass cannot be converted to Annotation`. For this KAPT symptom,
inspect the source annotation and `debugUnitTestCompileClasspath` before
changing processor configuration. Restoring the module's test dependency
allows the existing example to compile and execute without a KAPT workaround.
If an incremental build retains the unresolved stub after the classpath is
corrected, rerun the affected `testDebugUnitTest` tasks with
`--rerun-tasks --no-build-cache` and confirm the generated annotation becomes
`@org.junit.Test()`; do not edit generated stubs.

When repairing a test-compilation failure, require the existing tests to
execute and inspect their XML reports for nonzero test counts and zero
failures/errors/skips, then run the repository Debug gate. A missing report is
not a pass. For favorite/FAB changes, the focused regression class must also
pass.

By default, do not query ADB or run `connectedDebugAndroidTest`, installation,
instrumentation, or another device gate. Record these checks as not run per
project policy; they do not block handoff and are not a maintainer follow-up.
If the maintainer explicitly authorizes a device operation for the current
task, use the exact serial and Windows ADB transport, avoid real NGA traffic,
and keep API 29 floor and API 36 forward checks separately labelled.

## Scenario: Windows ADB From WSL

### 1. Scope / Trigger

After explicit authorization under the global policy, apply this rule to every
physical-device or emulator operation started from this repository's WSL
workspace. The maintainer has selected the Windows Android SDK ADB as the
single device transport. The WSL SDK may remain installed for build tooling,
but its `platform-tools/adb` must not be used.

### 2. Signatures

The workspace device executable is:

```text
/mnt/c/Users/inter/AppData/Local/Android/Sdk/platform-tools/adb.exe
```

Example invocation:

```bash
WINDOWS_ANDROID_ADB=/mnt/c/Users/inter/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$WINDOWS_ANDROID_ADB" devices -l
"$WINDOWS_ANDROID_ADB" -s <serial> install --no-streaming -r -t <apk>
```

### 3. Contracts

- All package listing, install, uninstall, shell, logcat, port-forward, and
  instrumentation commands use the executable above with the exact serial for
  the explicitly authorized device.
- Do not invoke `.android-sdk/platform-tools/adb`, `adb` resolved from the WSL
  `PATH`, or any other Linux ADB binary for device operations.
- Do not uninstall the WSL ADB merely because Gradle downloaded it; it is an SDK
  component. The rule is to avoid using it as the device transport.
- A WSL Gradle `connected*AndroidTest` task normally resolves the Linux SDK ADB
  and therefore violates this workspace rule. Build test APKs with Gradle, then
  install and invoke instrumentation explicitly through Windows `adb.exe`,
  unless the build is later configured and verified to use the Windows binary.
- Device unavailability does not authorize starting another ADB server or
  switching transports. Preserve the result and stop without waiting for or
  requesting reconnection.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Windows `adb.exe devices -l` lists the target | Continue with that executable and exact serial |
| Windows ADB reports no devices | Stop device operations; do not fall back to WSL ADB |
| A command or Gradle task starts Linux ADB | Stop it and rerun the equivalent operation through Windows ADB |
| Test APK must be exercised | Build it locally, install with Windows ADB, then run `am instrument` with Windows ADB |
| WSL ADB is present on disk | Leave it installed and unused for device transport |

### 5. Good/Base/Bad Cases

- **Good**: Gradle builds an APK, Windows ADB installs it on the named device,
  and Windows ADB records the resulting package or instrumentation status.
- **Base**: no device is visible to Windows ADB; JVM/build checks continue, while
  the device gate remains unavailable.
- **Bad**: start WSL ADB because the Windows device is temporarily absent, run a
  broad connected task that silently selects Linux ADB, or uninstall SDK files
  to enforce the convention.

### 6. Tests Required

- Within an explicitly authorized device operation, run the Windows executable
  with `devices -l` and record the exact serial before acting on the device.
- After install or uninstall, query the package with Windows ADB and verify the
  expected presence or absence.
- After removing DevTools forwards, run Windows ADB `forward --list` and verify
  the task-owned ports are absent.

### 7. Wrong vs Correct

#### Wrong

```bash
adb install app-debug.apk
./gradlew connectedDebugAndroidTest
```

#### Correct

```bash
WINDOWS_ANDROID_ADB=/mnt/c/Users/inter/AppData/Local/Android/Sdk/platform-tools/adb.exe
"$WINDOWS_ANDROID_ADB" -s <serial> install --no-streaming -r app-debug.apk
```

## Scenario: Physical-device APK installation authorization

### 1. Scope / Trigger

Use this diagnostic only when the maintainer explicitly authorizes it for the
current task and a real Android device is visible to ADB but a connected test
fails while installing its instrumentation APK, especially on Flyme/Meizu
devices or through USB/IP.

### 2. Signatures

```text
ANDROID_SERIAL=<serial> ./gradlew :<module>:connectedDebugAndroidTest --stacktrace
adb -s <serial> install --no-streaming -r -t <instrumentation-apk>
```

### 3. Contracts

- The APK transfer and package installation are separate gates. A successful
  transfer does not prove that the device accepted installation.
- `INSTALL_FAILED_USER_RESTRICTED` is a device authorization/configuration
  blocker. Keep the exact serial and preserve the command output; do not
  reinterpret it as a test assertion result.
- The user must unlock the device, enable its USB-install/developer approval
  control, and accept any visible install confirmation. The project must not
  disable or bypass that security control programmatically.
- After approval, rerun the ordered module gate from the first module and keep
  one report directory per device/attempt.

### 4. Validation & Error Matrix

| Observation | Classification / action |
| --- | --- |
| APK transfer succeeds, install returns `INSTALL_FAILED_USER_RESTRICTED` | Physical-device authorization blocker; request Flyme/Android USB-install approval |
| UTP reports `device '<serial>' not found` during install | ADB/USB/IP environment blocker; preserve XML/UTP logs and stop without waiting for or requesting reconnection |
| Device remains `device` after a manual rejected install | Confirms the install gate is distinct from transport loss; do not claim a test ran |
| APK installs and instrumentation reports a non-zero test count | Continue the ordered module gate and evaluate assertions normally |

### 5. Good/Base/Bad Cases

- **Good**: The user approves USB installation, the instrumentation APK
  installs, and the report contains the actual discovered test count.
- **Base**: A physical device is online but approval is pending; record the
  blocker and pause the device gate without switching to an emulator.
- **Bad**: Changing package-verifier settings, auto-confirming prompts, or
  reporting a zero-test/installation failure as a passing run.

### 6. Tests Required

- During the explicitly authorized diagnostic, before retrying, record
  `adb devices -l`, the exact serial, device API/model, and the install
  error/output.
- If UTP fails at installation, run the non-streaming diagnostic only as a
  transport/authorization probe; it is not a substitute for instrumentation.
- After user approval, assert that each ordered module emits a report with a
  non-zero/expected test count and no infrastructure error.

### 7. Wrong vs Correct

#### Wrong

```text
adb install test.apk
# INSTALL_FAILED_USER_RESTRICTED
# Treat the failed install or zero-test XML as a product test result.
```

#### Correct

```text
adb -s REDACTED_SERIAL_MEIZU install --no-streaming -r -t test.apk
# Preserve INSTALL_FAILED_USER_RESTRICTED, ask the user to enable USB install,
# then rerun the ordered connected test with the same explicit serial.
```

## Scenario: Signed GitHub release APK

### 1. Scope / Trigger

Apply this contract whenever a task changes the application identity, version,
release signing configuration, or GitHub APK publishing workflow. Eligible
pushes to every branch publish previews; exact `X.Y.Z` tag pushes publish
stable releases. The maintainer expanded the former main/AI allowlist to all
branches on 2026-09-12.

### 2. Signatures

```text
ANDROID_SIGNING_STORE_FILE=<absolute path outside the repository>
ANDROID_SIGNING_STORE_PASSWORD=<secret>
ANDROID_SIGNING_KEY_ALIAS=<secret>
ANDROID_SIGNING_KEY_PASSWORD=<secret>
CI_VERSION_NAME=<X.Y.Z or X.Y.Z-debug.RUN_NUMBER>
CI_VERSION_CODE=<1..2100000000>
RELEASE_TAG=<publication tag; stable X.Y.Z for Gradle verification>
ASSET_SUFFIX=<empty for main/stable, or -<readable branch slug>>
PREVIEW_TAG_PREFIX=<debug- or branch-<slug>-<branch hash12>-, empty for stable>
LEGACY_PREVIEW_TAG_PREFIX=<preview- for main, branch-feature-ai-summary- for exact AI, otherwise empty>
GITHUB_SHA=<triggering commit SHA>
GITHUB_REF=<refs/heads/<any branch> or refs/tags/X.Y.Z>
GITHUB_REF_NAME=<original branch name or X.Y.Z>
GITHUB_RUN_NUMBER=<long-lived build.yml workflow sequence>
ref-identity.outputs.ref_key=<full lowercase SHA-256 of exact GITHUB_REF, no newline>
python3 scripts/derive_android_version_code.py <stable X.Y.Z base> <build slot 0..999>
```

The publication identities are:

```text
debug tag       = debug-<first 12 characters of GITHUB_SHA>
branch hash12   = first 12 hex characters of SHA-256(original branch name UTF-8)
branch tag      = branch-<branch slug>-<branch hash12>-<first 12 characters of GITHUB_SHA>
debug version   = <newest reachable stable X.Y.Z tag>-debug.<GITHUB_RUN_NUMBER>
stable version  = <exact X.Y.Z trigger tag>
versionCode     = major*10_000_000 + minor*100_000 + patch*1_000 + build_slot
stable slot     = 0
debug slot      = first-parent commit distance from stable tag to GITHUB_SHA + 1
APK             = NGA-Just-Works-<CI_VERSION_NAME>.apk
branch APK      = NGA-Just-Works-<CI_VERSION_NAME>-<branch slug>.apk
checksum        = <APK filename>.sha256
stable copy     = NGA-Just-Works.apk (+ .sha256), staged only for stable releases
```

Every non-main branch uses Release title
`NGA Just Works <CI_VERSION_NAME> (Debug, <original branch name>)`. The readable
slug preserves ASCII letters, digits, dots, underscores, and hyphens; slashes
and other UTF-8 bytes become hyphens. Limit it to 80 ASCII bytes and use
`branch` when no ASCII letter or digit remains. Existing AI and compatibility
filenames retain suffixes `-feature-ai-summary` and
`-feature-thread-detail-compat-mode`. The branch label is Release/asset metadata;
Android `CI_VERSION_NAME` stays `X.Y.Z-debug.N`. The raw-name hash is part of
the internal Release tag, so equal readable slugs do not share publications.

The decimal layout is `Mmmppbbb`: minor and patch are `0..99`, and the final
three digits reserve `0` for a stable tag and `1..999` for Debug previews. The
already-published `5.5.0` APK keeps legacy versionCode `4069`; the new semantic
base for `5.5.0` is `50,500,000`, so every new Debug preview remains an upgrade.
`5.5.1` starts at `50,501,000`, exactly one above the largest possible
`5.5.0` preview. Reject a field/slot overflow or a final value outside Android's
`1..2,100,000,000` range instead of spilling into another semantic version.

The release applicationId is `com.github.tophtab.ngajustworks`; the source and
resource namespace remains `gov.anzong.androidnga` until a separately scoped
package migration is approved.

### 3. Contracts

- Release packaging must read all four signing values from the environment.
  Missing or blank values must fail before an unsigned release APK is emitted.
- Signing files and credentials stay outside the public application repository
  and release assets. GitHub restores the keystore only in runner temporary
  storage.
- The maintainer also has a verified local PKCS#12 keystore and credential file
  outside the worktree. Before declaring local signing unavailable, follow
  [Local Android Signing](./local-android-signing.md) for the known paths,
  private storage location, certificate fingerprint, and credential-loading
  commands.
- The root Gradle build keeps local fallback values and accepts stable
  `X.Y.Z` or Debug `X.Y.Z-debug.N` names together with `CI_VERSION_CODE` only
  as a complete, validated pair. Legacy `X.Y.Z-preview.N` is not a valid new
  publication identity.
- A non-documentation-only `main` push derives its base from the newest stable
  `X.Y.Z` tag reachable from `GITHUB_SHA`, builds and signs one `preview`
  variant APK, verifies it, then publishes a `debug-<sha12>` GitHub
  prerelease titled with `(Debug)`.
- Every eligible non-main branch push uses the same signed `preview` variant
  and publishes the APK/checksum directly as a GitHub prerelease. Do not use
  expiring Actions artifacts as the download channel. Add the branch label to
  the Release title and asset filenames. Put tags in the separate
  `branch-<slug>-<branch hash12>-<sha12>` namespace. A branch tag must not start
  with `debug-` or `preview-`, because older main workflows clean those prefixes.
  Distinct original branch names, including `feature/a-b` and `feature/a/b`,
  retain distinct Release channels even when their slugs and commit SHA match.
- Branch checkout keeps the complete commit/tag graph (`fetch-depth: 0`) required
  by `git tag --merged` and first-parent distance, but uses partial clone
  `filter: blob:none` so historical file contents are not prefetched. Checkout
  still materializes every file in the current worktree before the build.
- Pushes containing only `.trellis/**` and Markdown files do not publish.
  `workflow_dispatch` remains disabled; publication is driven by branch/tag
  pushes rather than a separate manual invocation. That prohibition governs
  the publication workflow. A separate dispatch-only workflow may answer a build
  question, but it must not sign, package, publish, or read repository
  secrets, and it is deleted once its question is answered.
- Use `push.branches: ["**"]` and allow every `push` event on `refs/heads/`
  through identity derivation and publication. A single `*` does not include
  slash-separated branch names.
- GitHub concurrency groups compare case-insensitively. The `ref-identity`
  pre-job has `permissions: {}`, no checkout, and no secrets; it hashes the
  exact complete `GITHUB_REF` and exports `ref_key`. The build job requires
  that job and groups by workflow name plus `needs.ref-identity.outputs.ref_key`.
  This separates `feature/Foo` from `feature/foo` and branch refs from tag refs.
  Cancel superseded runs of the same branch, leaving stable-tag runs uncancelled.
- A branch push reads the workflow from that branch. Distribute the shared CI
  change to existing maintained branches; new branches inherit it from an
  updated base. Do not merge feature implementations merely to distribute CI.
  Branches recreated from an older commit still need the CI change.
- A stable tag must match `X.Y.Z` exactly. The same workflow checks out that
  tag with `fetch-depth: 1` and `filter: blob:none`, uses it as
  `CI_VERSION_NAME`, builds and signs once, verifies the APK, and creates a
  normal GitHub Release directly from the current job's `dist/`. It must not
  query or download an earlier Actions artifact.
- Stable tags pass build slot `0` to
  `scripts/derive_android_version_code.py`. Main and feature previews compute
  `git rev-list --first-parent --count <stable>..<sha>` and add one before
  invoking the same script. The same commit therefore keeps the same
  versionCode on rerun, while an append-only main history increases it. More
  precisely, a first-parent distance of 999 or greater makes the `+1` Debug
  slot exceed `999` and must fail; force-pushing/replacing main history is not
  a supported publication action.
- Each publication job starts Gradle exactly once. The identity step emits one
  controlled task list — `:nga_phone_base_3.0:assemblePreview` for Debug,
  `verifyReleaseTag` plus `:nga_phone_base_3.0:assembleRelease` for stable —
  and the build step splits it into a Bash array for a single `./gradlew`
  call. Staging and publication must not start Gradle. That task list comes
  only from the workflow's own constant branches, never from a tag, commit
  message, or other input.
- Because the stable tag check shares the build's task graph, a tag/version
  mismatch fails that one invocation and stops staging and publication before
  anything is released. Moving the check after `gh release create`, or
  replacing it with a shell string comparison, is not equivalent.
- The staged APK filename comes from the already-validated `CI_VERSION_NAME`,
  with a filename-safe branch suffix only for feature previews. The APK's own
  `versionName` and `versionCode` are still verified from the
  built manifest, so dropping a separate version-printing Gradle invocation
  removes a process start, not a version check.
- Every stable tag must have a matching `release-notes/<X.Y.Z>.md` in the tagged
  source. The complete body contains exactly one `## 新增`, `## 删除`, and
  `## 修复` heading in that order, and every section contains a non-empty
  Markdown list item. Indented code and list-looking text inside fenced code
  blocks do not count as list items. Use `- 无` when a section has no changes.
  Validate the file before `gh release create` and publish it with
  `--notes-file`; only Debug prereleases may use `--generate-notes`.
- The published `preview` build type uses the production applicationId and
  release signing configuration, sets `debuggable=true`, and disables
  minification without adding an applicationId suffix. The ordinary local
  `debug` variant keeps its `.debug` suffix and is never published.
- Stable tag builds use the same production applicationId and signing key,
  remain `debuggable=false`, and keep release minification enabled. A Debug
  prerelease therefore upgrades the stable app without clearing login,
  settings, or data. The next stable patch/minor semantic base must be higher
  than every reserved Debug slot so it can upgrade the prerelease in place.
- Before replacing a same-SHA Debug prerelease, an existing matching tag must resolve to
  `GITHUB_SHA` and any existing Release must be a prerelease. A rerun keeps the
  same run number and asset names, so `gh release upload --clobber` may replace
  those Debug assets. Stable Release assets are immutable; a fix requires a
  new stable version and versionCode.
- Delete old previews only after the new prerelease is published, and only
  within its channel. Main cleanup selects its current `debug-` prefix and
  legacy `preview-` prefix. Feature cleanup selects its exact
  `branch-<slug>-<branch hash12>-` prefix followed by exactly twelve
  lowercase hexadecimal SHA characters. Main and legacy prefixes require the
  same exact SHA suffix; a longer branch slug sharing any prefix must not
  match. Exclude the current tag and delete each selected tag with its Release.
  Main/other-branch previews, stable releases, and unrelated prereleases stay
  outside feature cleanup.
  Lookup or deletion failures must fail visibly; never clean up after a failed
  publication. Pass dynamic titles/tags to shell scripts as environment data.
- Only the exact original ref `refs/heads/feature/ai-summary` may additionally
  remove old `branch-feature-ai-summary-<sha12>` prereleases, and only after a
  new preview is published successfully. This is the known legacy AI channel
  migration; do not derive unhashed cleanup prefixes for arbitrary branches.
  Legacy tags must also have exactly twelve lowercase hexadecimal SHA characters.
- Build-performance switches are decided from CI measurements, not local ones.
  The local machine's core count and background load differ from the
  `ubuntu-latest` runner, and a contended local A/B can invert the verdict:
  parallel execution measured 25% slower locally under concurrent load and
  −2.1% (inside noise) on the runner. Time candidates interleaved inside one
  job so a pair shares a runner, use a mode-matched `clean`, disable the build
  cache, and decide on medians.
- Keep `org.gradle.parallel=true` unless a repeated CI median regresses by more
  than 5%, or it causes OOM, races, output differences, or build/test/lint
  failures. Do not claim a speedup that the samples do not support: this
  project's release build is dominated by the single non-parallelizable
  `minifyReleaseWithR8` task, so cross-subproject parallelism has little room
  to help until the module graph changes.
- The publishing build job requires `contents: write`; it does not require `actions: read`.
  Gradle caching remains owned by `setup-gradle@v4`, with main allowed to write
  and feature/tag refs read-only. Do not layer another Gradle User Home cache
  action.
- GitHub expression string comparisons also ignore case. Derive cache access
  from controlled identity outputs: read-only when `prerelease != 'true'` or
  `asset_suffix != ''`. The Bash identity branch compares the original ref
  case-sensitively, so only exact `main` can write; `Main` and `MAIN` remain
  ordinary read-only branch previews.
- APK packaging and release-signing verification default to GitHub Actions CI.
  A request to commit, finish work, push, or wait for CI does not authorize a
  local `assemblePreview` or `assembleRelease`. Run either packaging task
  locally only when the maintainer explicitly asks for a local APK build;
  otherwise use focused unit/static checks before push and the CI job as the
  build/signing gate.
- Before publication, require exactly the APK and SHA-256 sidecar in `dist/`
  (stable builds additionally stage the version-free `NGA-Just-Works.apk` pair
  so the README's `releases/latest/download` link stays version-independent:
  2 files per preview, 4 per stable), verify the checksum, applicationId,
  versionName, versionCode, signer, and merged manifest `debuggable` value for
  the selected channel.
- Local unit tests, static checks, and lint are the developer quality gate. APK
  packaging and signing verification run in GitHub Actions unless explicitly
  authorized for a local build. Pushing `main` or a stable tag ends the task:
  do not run `gh run watch`, poll `gh run list`, or otherwise wait on the
  publication job. The maintainer monitors releases and reports a failure; a
  workflow result is inspected only when they ask for it. Installation and
  device-based functional acceptance occur only when the maintainer explicitly
  requests them under the global policy; they are not default automation or
  handoff gates.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Any signing environment value is absent or blank | `assembleRelease` fails; no unsigned fallback |
| Only one of `CI_VERSION_NAME` / `CI_VERSION_CODE` is present | Gradle configuration fails |
| CI versionName is not stable or `X.Y.Z-debug.N` | Gradle configuration fails |
| CI versionCode is nonnumeric, outside Android's range, or not greater than the installed build | Fail before publication; correct the semantic base/build-slot derivation |
| version minor/patch exceeds 99, stable slot is not 0, or Debug slot is outside 1..999 | Fail identity derivation before Gradle starts |
| Branch commit has no reachable stable `X.Y.Z` tag | Fail during preview identity derivation |
| Branch checkout lacks the complete commit/tag graph needed by tag lookup or first-parent distance | Restore `fetch-depth: 0`; do not guess the base/slot |
| Trigger tag is not exactly `X.Y.Z` | Fail before build/publication |
| Stable `RELEASE_TAG` differs from effective Gradle versionName | Tag verification fails before publication |
| Matching stable notes are missing, duplicated, out of order, malformed, or contain an empty required section | Fail before `gh release create`; do not fall back to generated notes |
| Existing Debug tag resolves to another SHA | Fail without replacing the tag or Release |
| Existing `debug-<sha12>` Release is not a prerelease | Fail without replacing its assets |
| Release/tag lookup API fails | Fail visibly; do not treat the error as absence |
| APK or sidecar is missing, checksum fails, or `dist/` has extra files | Reject before `gh release create` |
| APK applicationId, versionName, versionCode, or signer differs | Block publication |
| Debug build/publication fails | Keep the prior Debug/legacy preview Release and tag available |
| Cleanup sees a stable, non-prerelease, or unrelated-prefix Release | Leave it untouched |
| Main cleanup sees any `branch-*` preview | Leave it untouched, including legacy AI previews |
| Two branch names normalize to the same readable slug | Hash the original names; their Release tags and cleanup sets remain distinct |
| Two legal refs differ only in case | Hash the complete raw refs before grouping jobs; neither cancels the other's build |
| A branch is named `Main` or `MAIN` | Treat it as a non-main branch for publication and cache access |
| A branch name contains quotes, shell syntax, Unicode, or many path segments | Keep values as quoted environment/argument data; emit a bounded single-component asset name |
| A different branch resembles the legacy AI slug | Do not apply legacy AI cleanup to it |
| Feature cleanup sees a main preview, another branch, or a longer slug sharing its prefix | Leave it untouched; match the exact branch prefix plus twelve hex characters |
| A feature preview uses a branch suffix in `CI_VERSION_NAME` | Reject the invalid Android version; put the suffix in asset metadata instead |
| Preview APK is not debuggable or uses `.debug` applicationId/debug key | Reject before publication |
| Stable APK is debuggable or release minification is disabled | Reject the stable build |
| A `.trellis`/Markdown-only branch push occurs | Skip the workflow; mixed pushes remain eligible |
| Keystore/private-key material is tracked or packaged | Remove it from the release path and rotate if exposed |
| The task requests commit/push/CI but not a local APK build | Do not run local `assemblePreview` or `assembleRelease`; push and inspect CI |
| `main` or a stable tag has just been pushed | Report the pushed refs and stop; do not watch or poll the Actions run |

### 5. Good/Base/Bad Cases

- **Good**: A main commit two first-parent steps after reachable `5.5.0` uses
  Debug slot `3`, builds a signed, production-ID, debuggable preview with
  versionCode `50,500,003`, publishes `debug-<sha12>` as a prerelease, then
  removes only older `preview-*`/`debug-*` prereleases and tags. A later
  `5.5.1` tag builds a non-debuggable, minified stable APK with versionCode
  `50,501,000`.
- **Base**: Rerunning the same Debug run validates its existing tag and
  prerelease, retains the same version values, and replaces the same-named APK
  and checksum. No additional Debug Release is created.
- **Good**: A feature build publishes
  `NGA-Just-Works-5.6.1-debug.51-feature-ai-summary.apk` under
  `branch-feature-ai-summary-<branch hash12>-<sha12>`, retaining Android
  versionName `5.6.1-debug.51`. It replaces only older previews for that branch; ordinary
  main previews and stable downloads remain available.
- **Good**: A stable tag validates `release-notes/<tag>.md` and publishes that
  exact file, including explicit `- 无` items for empty change categories.
- **Bad**: Publishing the ordinary `.debug` variant, signing the preview with a
  debug key, using a fixed mutable tag without SHA validation, deriving a
  Debug version from an unreachable/future stable tag, deleting the old
  prerelease before the new one exists, or reusing a prior main artifact for
  stable publication. Falling back to generated stable notes when the matching
  file is absent or invalid is also forbidden. Treating permission to push and
  wait for CI as implicit permission to run a local release package is also
  out of scope.

### 6. Tests Required

- Parse the workflow YAML and all modified Bash blocks, then run
  `git diff --check`.
- Run `python3 -m unittest discover -s scripts`. `test_release_workflow.py`
  executes the actual workflow Bash against local Git/APK fixtures and a stub
  GitHub CLI, including partial output from failed API calls.
- Run the release-notes validator against committed valid notes and missing,
  duplicate, out-of-order, blank-section, malformed-heading, indented-code,
  and fenced-code pseudo-list cases. Assert stable publication uses the
  validated tag-addressed file with `--notes-file` while Debug publication
  alone retains `--generate-notes`.
- Exercise identity derivation for main and feature commits with a reachable
  stable tag, an exact stable tag, an invalid tag, and a commit without a stable
  base. Verify main/feature identities differ even for the same SHA and branch
  asset names retain one path component without changing Android versionName.
  Include nested, long, non-ASCII and shell-looking legal branch names. Verify
  `feature/a-b` and `feature/a/b` have different tags and cleanup sets despite
  equal readable suffixes; hash identity must use the original branch name.
- Execute the ref-key Bash and assert deterministic keys for repeated exact
  refs, distinct keys for case-only refs, and distinct branch/tag keys. Validate
  the build job's dependency and concurrency wiring. Test cache access for
  exact `main`, `Main`, `MAIN`, feature branches, and stable tags using GitHub's
  case-insensitive expression comparison semantics.
- Run `scripts/test_derive_android_version_code.py`. Assert `5.5.0/slot 0`
  gives `50,500,000`, slot `999` is exactly one below `5.5.1/slot 0`, two-digit
  minor/patch fields work, and malformed fields, field overflow, slot overflow,
  a zero final versionCode, and Android-limit overflow fail.
- Assert local Gradle defaults, valid Debug/stable CI overrides, a partial
  override pair, malformed versionName, out-of-range versionCode, matching
  stable `RELEASE_TAG`, mismatched/invalid tags, and rejection of the legacy
  `X.Y.Z-preview.N` naming form.
- Assert the first new `5.5.0` preview is greater than legacy published code
  `4069`, same-commit reruns keep their code, a stable patch orders after all
  preceding preview slots, and a later preview derives its versionName/code
  base from the newly reachable stable tag.
- Assert missing signing values fail release packaging. In CI with signing
  values, run `apksigner verify --print-certs` and inspect APK applicationId,
  versionName, versionCode, `debuggable`, app label, static shortcuts, and
  `assets/easygo.json` as applicable to the task.
- Exercise Debug publication selection with no prior tag/Release, a matching
  same-SHA prerelease, a tag pointing to another SHA, a non-prerelease Release,
  and a failed API call. Assert reruns replace only current preview assets.
- Exercise cleanup selection against the current Debug tag, older `debug-*`,
  legacy `preview-*`, a stable Release, an unrelated prerelease, and a partial
  deletion failure. Include current/older feature tags and similarly prefixed
  branch names; assert main and feature cleanup cannot delete each other's
  publications. Include legacy AI migration from its exact original ref and
  rejection for lookalike refs. Cleanup starts only after successful
  publication. Use offline fixtures; never exercise deletion against live releases.
- Run focused local unit/static checks and lint before push. Do not run local
  APK packaging unless the maintainer explicitly requests it. Everything that
  gates a release happens before the push: remote ref unmoved, target tag not
  already present, `release-notes/<X.Y.Z>.md` passing the validator, and the
  touched modules compiling. After push, report the pushed refs and stop. Do
  not routinely download or install the published APK; watching the workflow,
  diagnosis, installation, and device-based functional acceptance each require
  an explicit maintainer request and are not handoff gates.

### 7. Wrong vs Correct

#### Wrong

```yaml
publish-release:
  steps:
    - uses: actions/download-artifact@v4
      with:
        run-id: ${{ steps.main-build.outputs.run_id }}
    - run: gh release create "$GITHUB_REF_NAME" dist/*
```

#### Correct

```yaml
permissions:
  contents: write

jobs:
  build-and-publish:
    steps:
      - env:
          GRADLE_TASKS: ${{ steps.release.outputs.gradle_tasks }}
          RELEASE_TAG: ${{ steps.release.outputs.tag }}
        run: |
          set -euo pipefail
          read -r -a gradle_tasks <<< "$GRADLE_TASKS"
          test "${#gradle_tasks[@]}" -ge 1
          ./gradlew "${gradle_tasks[@]}" --no-daemon
      - env:
          GH_TOKEN: ${{ github.token }}
          RELEASE_TAG: ${{ steps.release.outputs.tag }}
        run: |
          set -euo pipefail
          (cd dist && sha256sum -c ./*.sha256)
          release_notes="release-notes/${GITHUB_REF_NAME}.md"
          python3 scripts/validate_release_notes.py "$release_notes"
          gh release create "$RELEASE_TAG" dist/* \
            --notes-file "$release_notes" \
            --verify-tag
```

#### Wrong

```bash
gh release delete "$OLD_DEBUG" --cleanup-tag --yes
gh release create "$NEW_DEBUG" dist/* --prerelease
```

#### Correct

```bash
gh release create "$NEW_DEBUG" dist/* --target "$GITHUB_SHA" --prerelease
# Only after creation succeeds:
gh release delete "$OLD_DEBUG" --cleanup-tag --yes
```

#### Wrong

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
# GitHub folds group case, so feature/Foo and feature/foo collide.
```

#### Correct

```yaml
build-and-publish:
  needs: ref-identity
  concurrency:
    group: ${{ github.workflow }}-${{ needs.ref-identity.outputs.ref_key }}
    cancel-in-progress: ${{ startsWith(github.ref, 'refs/heads/') }}
# The no-permission pre-job derives ref_key from the exact complete ref.
```

#### Wrong

```bash
release_tag="debug-feature-ai-summary-${short_sha}"
# Older main cleanup selects every debug-* prerelease and deletes this too.
```

#### Correct

```bash
branch_digest="$(printf '%s' "$branch_name" | sha256sum)"
release_tag="branch-${branch_slug}-${branch_digest:0:12}-${short_sha}"
# Hash the ORIGINAL branch name; cleanup also requires an exact SHA suffix.
```

#### Wrong

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: 0
- run: echo "CI_VERSION_CODE=$((4043 + GITHUB_RUN_NUMBER))" >> "$GITHUB_ENV"
```

#### Correct

```yaml
- uses: actions/checkout@v4
  with:
    fetch-depth: ${{ startsWith(github.ref, 'refs/tags/') && 1 || 0 }}
    filter: blob:none
- run: |
    version_code="$(python3 scripts/derive_android_version_code.py \
      "$version_base" "$build_slot")"
    echo "CI_VERSION_CODE=$version_code" >> "$GITHUB_ENV"
```
