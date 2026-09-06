# Local signing verification — 2026-09-06

This record preserves the completed build and installation from Codex session
`01a076d6-1a25-7e41-84ac-2b428ad11b98`, plus the private-storage verification
performed during its closeout. Build and device results below are historical;
this closeout did not repeat the build or operate the device.

## Build and APK

| Check | Observed result |
| --- | --- |
| Source | `feature/ai-summary@c52e045c658815cfb8dbc423b316c48401149d94` |
| Gradle target | `:nga_phone_base_3.0:assemblePreview` |
| Build | `BUILD SUCCESSFUL in 1m 44s`; 293 tasks: 208 executed, 85 from cache |
| Package | `com.github.tophtab.ngajustworks` |
| Variant | Signed, debuggable `preview` |
| Version | `5.6.1-debug.6` / `50601006` |
| APK size | 48910397 bytes |
| Signature | `apksigner` verification passed; local key, installed APK, and new APK certificates matched |

APK SHA-256:

```text
845265dfce76e07b177f5a9fac2d40e1bf8a8beed190ea29b8e216cdfcf724ca
```

Public certificate SHA-256:

```text
e944475ac92ee7ab99c1da790dc1bbda4332db1c3c332033f32693cc9b53993c
```

## Authorized installation

The original session authorized Windows ADB on the maintainer's Xiaomi
24129PN74C / API 35. `install --no-streaming -r -t` returned `Success`
with exit code 0.

| Check | Before | After / result |
| --- | --- | --- |
| Version name | `5.6.1` | `5.6.1-debug.6` |
| Version code | `50601000` | `50601006` |
| Application UID | Existing installation | Unchanged |
| Data directory | Existing installation | Unchanged |
| First-install time | Existing installation | Unchanged |
| Debuggable flag | — | Enabled, as required for `preview` |

These checks confirm an in-place update preserving the existing data directory;
they do not independently verify every item of user data or runtime behavior.
The app was not launched and no live NGA or AI service integration was tested.
Device serials and credential values are omitted from this public record.

## Private signing storage

| Check | Result |
| --- | --- |
| Repository | [https://github.com/tophtab/nga-just-works-signing](https://github.com/tophtab/nga-just-works-signing) |
| Owner / visibility | `tophtab` / `private`, verified through the GitHub API |
| Signing-material commit | `95bcbc08261517c3922f2429e234f5594bdad77e` |
| Stored files | Original `nga-just-works-release.p12` and `credentials.env` |
| Remote verification | A fresh remote clone matched both original files byte-for-byte |
| Key verification | The cloned PKCS#12 opened with its stored credentials, contained the private-key entry, and matched the certificate above |
| Automation | Actions disabled for this storage repository |

The application repository retains its existing Actions signing Secrets.
This public repository contains only documentation and verification metadata.

## Workflow and closeout scope

- The original workflow change at `c52e045c` passed `actionlint`, as recorded in
  the resumed session. Its signed branch APK was an Actions artifact retained
  for seven days. The separate publication task subsequently changed the
  feature branch to labelled GitHub prereleases in `3ed2a4d1`; its current
  download instructions are maintained on that branch.
- GitHub build results were left for the maintainer to inspect, as requested;
  this record does not claim a monitored CI pass.
- Validation sources were the original `build-context.json`, `build.log`,
  `apk-verification.json`, `install-result.json`, and `verification.json` under
  the session's temporary evidence directory. Their non-sensitive results are
  preserved above so the journal does not depend on temporary files surviving.
- The local signing contract records the verified paths, usage, certificate,
  and private repository location. Computer-migration instructions were removed
  at the maintainer's request.
