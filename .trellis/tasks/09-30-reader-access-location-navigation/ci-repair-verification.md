# 6.1.1 CI repair

Both main run 36725855723 and stable run 36725855362 failed before compilation.
setup-android@v3 defaults to `tools platform-tools`; sdkmanager rejected `tools`.
Upstream action.yml and input-reading code independently confirmed the cause.

Explicit `packages: platform-tools` removes obsolete installation while retaining
`platforms;android-35` and `build-tools;35.0.0` in the subsequent install step.
No application, signing, SDK-level or R7 changes.

Validation: 37 script tests, YAML parse, all 8 Bash block syntax checks, and
whitespace check passed. Regression rejects removed override, obsolete tools,
missing API35 and missing build-tools35. actionlint unavailable; not claimed run.
6.1.1 release notes validated; versionCode 60101000. No local release packaging.
Actual CI result remains pending after push; keep existing 6.1.0 tag unchanged.
