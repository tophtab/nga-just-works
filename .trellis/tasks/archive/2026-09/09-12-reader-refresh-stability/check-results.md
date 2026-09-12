# Independent check and final gate

Outcome: accepted. The independent checker reviewed the product and behavioral
tests, ran the repository gate, and audited the JUnit/lint reports. Main recorded
the final input comparison and this consolidated report. No product changes were
made after the focused implementation handoff.

## Review

- Accepted legacy/prefetch page contexts are present before body retention.
  Resource identity follows the row and accepted page, including reordering,
  variable counts and conservative handling of ambiguous identities.
- Moving a retained WebView detaches it from its actual parent and preserves
  the row container's static XML children. READY-entry and view-destruction
  contracts remain intact.
- Metadata handoff keeps invalidation observable while replacement is pending,
  retires removed queued authors before dispatch, and preserves weak UI ownership.
  Actual displayed-text comparison and payload identity guards were reviewed.
- The checker identified an idle-repository synchronous close/null race.
  The registration callback now gives the page its handle before publication,
  and only an active online subscription dispatches afterward. The final fix
  and the idle-slot regressions passed independent review.
- The 25 body tests execute the production owner with identity/disposal
  counters; they do not simulate or claim to measure WebView rendering.
- No further product or spec finding remained after that correction.

## Commands

```sh
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
```

Both commands passed with exit code 0. The debug/JVM command took 41 seconds
(360 tasks: one executed, 359 up-to-date). Forced lint took 51 seconds
(536 tasks, all executed). All 13 lint XML reports were generated during the
gate. Every expected test suite had a nonempty report; there were no JUnit
failures, errors or skips and no lint Error/Fatal findings.

| Module | Suites | Tests | Lint warnings | Lint information |
| --- | ---: | ---: | ---: | ---: |
| `lib_bu_statistics` | 1 | 1 | 6 | 0 |
| `nga_phone_base_3.0` | 61 | 592 | 729 | 1 |
| `lib_core` | 2 | 5 | 8 | 1 |
| `lib_base_logger` | 1 | 1 | 8 | 0 |
| `lib_base_common` | 4 | 62 | 33 | 0 |
| `lib_core_data` | 1 | 1 | 1 | 0 |
| `lib_bu_message` | 1 | 1 | 11 | 2 |
| `lib_base_network` | 1 | 1 | 7 | 0 |
| `lib_base_service_api` | 1 | 1 | 2 | 0 |
| `lib_bu_account` | 1 | 1 | 6 | 0 |
| `lib_base_ui_compose` | 3 | 9 | 12 | 0 |
| `lib_base_ui` | 1 | 1 | 8 | 0 |
| `lib_module_debug` | 1 | 1 | 4 | 0 |
| **Total** | **79** | **677** | **835** | **4** |

Warnings/information are reported as observed; this is not a warning-free gate.
No unrelated warning cleanup was added. Focused suite counts are recorded in
[implementation-results.md](./implementation-results.md).

## Input consistency

- Before: `2026-09-12T13:15:16.421994+00:00`.
- After: `2026-09-12T13:32:17.351322+00:00`.
- Input files: **1198**; changed paths: **0**.
- Identical manifest SHA-256: `676c75d88d550fc0aa39fe36856c97cd626da02e4317b8523fdd17b2f17b1a27`.

The manifest was generated for this gate using `git ls-files --cached --others
--exclude-standard`, followed by filtering for all module sources, resources,
tests, dependencies, Gradle/wrapper/buildSrc inputs and module lint rules.
It includes all four new production/test files, even before staging. Content
hashes and file modes match before and after the gate. Task/spec documentation
and generated build output were excluded.

Logs, the capture script and machine-readable summaries: `/tmp/reader-refresh-final-check-jmxo9fkb/`.
The committed counts and hash above preserve the result if temporary logs
are later removed. No device execution or live NGA request was performed.
