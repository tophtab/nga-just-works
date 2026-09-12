# Exact staged product validation

Date: 2026-09-12. The authoritative workspace is
`/home/toph/nga-just-works`, on main, with pending merge parent
`f5bcec31202635a5992d4fbf81537c71c93a86d4`.

## Scope

The final product snapshot is tree
`151737f3029ceac5b869dd1bdf332ba844af49cb`, based on main at
`16cc185bb6969c8914039f5cc710024fca1cf84f`. The IP integration contains
18 changed paths, 867 insertions, and 193 deletions against that main base.
The snapshot includes the reviewer's Page replay correction and its two
regression tests, the restored IP feature, synchronized specifications,
and the separately committed reader fix.

An earlier snapshot, `2d02a35d33a348cabf2fde85b8cc166e3c106ee8`, also passed
620 tests and lint before the reader session committed its existing WIP.
When main advanced, the coordinator regenerated the final snapshot using
main plus only the 18 IP index paths, excluding another session's staged
archive files. The product difference was exactly the three previously
reviewed reader/spec files; their working bytes had not changed. Main's later
reader archive and journal commits only add Trellis bookkeeping.

Using a separate temporary Git index, the coordinator materialized exactly
this tree in `/home/toph/nga-just-works-release-6.0.0` for validation. All tracked
files matched the snapshot. The main index and working files were unchanged.
The validation directory's name is historical; no stable release is being
prepared or published. No commit is made from that directory.

This snapshot excludes independent unstaged AI changes. The reader fix is
already committed on main and included in the final scope. The main-worktree
test result of 633 includes 13 additional AI WIP tests; the committed scope
has 620 tests.

## Final gate

```bash
./gradlew testDebugUnitTest lintDebug --continue --max-workers=2 --console=plain > /tmp/nga-ip-merge-staged-reader-gate.log 2>&1
```

The command exited 0: `BUILD SUCCESSFUL in 1m 20s`, with 603 actionable tasks
(28 executed, 575 up-to-date). Application Java compilation, unit tests, and
lint analysis/report tasks executed. Unchanged compilation and library
results were reused from the previously successful full gate.

All Debug JUnit XML results were parsed: **620 tests in 77 classes across
all 13 modules, with zero failures, errors, or skipped tests**. The app has
535 tests, including 211 AI tests and 8 author-location integration contracts.
Both added regressions executed:

- `readyResumeReplaysRestoreCurrentMetadataWithoutReplacingThePageSubscription`
- `nullDeliveryAlwaysClearsReplayIdentityAndCloseReleasesTheResponse`

Every module declared in `settings.gradle` has a Debug lint XML report.
All 13 reports show **zero Error/Fatal issues**, with 835 warnings and
4 information findings in total. No lint baseline or suppression was added.

| Module | JVM tests | Lint Error/Fatal | Warnings | Information |
| --- | ---: | --- | ---: | ---: |
| lib_bu_statistics | 1 | 0 / 0 | 6 | 0 |
| nga_phone_base_3.0 | 535 | 0 / 0 | 729 | 1 |
| lib_core | 5 | 0 / 0 | 8 | 1 |
| lib_base_logger | 1 | 0 / 0 | 8 | 0 |
| lib_base_common | 62 | 0 / 0 | 33 | 0 |
| lib_core_data | 1 | 0 / 0 | 1 | 0 |
| lib_bu_message | 1 | 0 / 0 | 11 | 2 |
| lib_base_network | 1 | 0 / 0 | 7 | 0 |
| lib_base_service_api | 1 | 0 / 0 | 2 | 0 |
| lib_bu_account | 1 | 0 / 0 | 6 | 0 |
| lib_base_ui_compose | 9 | 0 / 0 | 12 | 0 |
| lib_base_ui | 1 | 0 / 0 | 8 | 0 |
| lib_module_debug | 1 | 0 / 0 | 4 | 0 |
| **Total** | **620** | **0 / 0** | **835** | **4** |

The coordinator compares the final index against this snapshot, allowing
only Trellis task/journal documentation differences. Every product blob must
remain identical to the tested snapshot at commit time. The earlier gate's
log remains at `/tmp/nga-ip-merge-staged-gate.log`.

The earlier Python release/workflow/version regression suite passed all
36 tests. No workflow, version, or release-notes file changed in the final
integration. Device tests were not run per project policy; no APK packaging,
ADB/device actions, live NGA requests, or CI polling were performed.
