# R4 verification — 2026-09-30

Core implementation independently checked, no outstanding R4 code findings.

- FULL query clears filters/cache; separate targetPid/targetFloor preserves actual reply identity.
- Known global floor selects ordinary20 candidate; unknown floor remains readable at page1; App uses bounded source-size alignment and actual PID verification.
- One failed-position notice; valid posted scroll consumes expected anchor only after lifecycle/reader/data validation.
- ViewModel one-time launch target, retained/cloned-page behavior, account invalidation and monotonic reset generation covered.
- 22 focused R4 tests; full app 619 tests across 63 suites, zero failures/errors/skips.
- assembleDebug and app lint pass; zero Error/Fatal; whitespace check pass.
- R1 integration now reviewed: detach prior Fragment then resetReader for onNewIntent. Final six-item gate pending.
- No live NGA/device operations.

## Final integration gate

All five required Gradle commands passed on the combined R1–R6 tree. XML verification: 724 tests, zero failures/errors/skips; all 13 Android modules zero Error/Fatal. See parent integration-verification.md. Code remains uncommitted pending Phase 3.4 confirmation.

Implementation commit: `9f2acd4bea96eaea96fa668e12737ac2d0b3c83c`. User approved commit/finish-work/push and 6.1.0 release.
