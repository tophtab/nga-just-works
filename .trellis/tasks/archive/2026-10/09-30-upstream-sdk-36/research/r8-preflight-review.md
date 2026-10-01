# R8/Preview preflight review (2026-10-01)

Read-only preparation by independent check agent while U3 B4 correction runs.
This is cached AGP bytecode/source evidence, not the final U4 task graph or a successful build.

- Current application `build.gradle` signing guard examines all graph tasks and matches `packageReleaseResources`; U3 actual rejected dry-run is recorded in `research/b2-r8-guard.log` in the data-layer task.
- Cached AGP 8.10.1 `MergeResources$CreationAction` PACKAGE branch produces `PACKAGED_RES`; `ParseLibraryResourcesTask$CreateAction` consumes it to produce `LOCAL_ONLY_SYMBOL_LIST`, consumed by `GenerateLibraryRFileTask$CreationAction`. Preview compilation therefore also needs the resource producer.
- No complete existing Release/Preview classes, R.jar and merged keep outputs exist to supply a standalone equivalent run. Debug classes and the bean classfile fixture are partial evidence only.
- Renaming/duplicating variants or removing graph dependencies would bypass the existing guard and is not the approved solution.

After all other authorized implementation/checks, inspect the actual new-toolchain dry-run graph. If still blocked, the smallest additional authorization is privately loading existing signing configuration solely for the unchanged guard while running app `minifyReleaseWithR8`, `compilePreviewKotlin`, `compilePreviewJavaWithJavac`, and `processPreviewMainManifest`. The graph must contain no actual APK/AAB packaging or signing task. This is a configuration-access request, not a request for local signed APK output.

No Gradle invocation, credential access, guard edit, device operation or product mutation occurred during this independent preparation. Actual app R8 and necessary Preview compilation remain uncompleted until run successfully.
