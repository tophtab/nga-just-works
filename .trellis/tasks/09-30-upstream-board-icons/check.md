# U1 independent review

Date: 2026-10-01. Reviewer: Trellis check agent. Worktree: `/home/toph/nga-just-works-upstream-adoption`; baseline `557f7bea`.

## Findings (fixed)

- File: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt`.
- Issue: Fastjson 1.1.71 preserves null members in typed category/group/forum arrays despite Kotlin non-null element declarations. An offline JShell probe against the resolved Fastjson jar and compiled production DTO confirmed a valid forum followed by null remains `[Forum, null]`. The new Main merge could append the valid forum, then throw on the null. The gate catches the exception, skipping hydration, canonical save, and structural revision after live mutation. The earlier IO-only incremental builder failed before mutation. This violated malformed-response preservation.
- Fix: validate every category/group/forum member as a raw JSON object before DTO conversion. Reject malformed arrays or members before accepting any prefix/tree state. Preserve missing/null optional groups/forums and ignore invalid icon metadata independently as designed.
- Regression: `malformedMembersAreRejectedBeforeAnyLiveTreeOrPrefixMutation` exercises 14 malformed shapes through the production decoder and refresh gate/application callback, checking no insertion, prefix/icon change, save callback, or revision. It also checks optional-list compatibility. No network or Android device is involved.
- Main-session spec sync: `state-management.md` now records the validation boundary; `nga-platform-access-rules.md` replaces obsolete Glide/literal-board-URL claims with the current Coil/resolver path.

## Findings (not fixed)

None. No remaining demonstrated in-scope product defect found. No architecture/public-interface change or scope expansion was needed.

## Acceptance review

| Criteria | Independent code-path evidence |
| --- | --- |
| A1/A2 | Resolver uses ApiConstants defaults, exact board directory and collection path, signed fid and stid priority. Strict envelope/member checks precede DTO coercion; bad icon metadata alone leaves valid forums usable. Offline URI probes found no bypass in abbreviated, octal, hexadecimal or single-label numeric IP examples; URI host rejection and resolver DNS/numeric checks compose as intended. |
| A3/A4 | Model applies on Main through BoardIconState, recursively hydrating canonical roots and the separate bookmark tree. Traversal does not deduplicate by ID/equality. Cells read the original entity's observable iconUrl; SnapshotStateObserver test covers notifications and equal-prefix no-op. |
| A5 | Model rebuilds the current map including favorites before merge; each accepted insertion updates that index. Existing order and supported-category/missing-parent rules remain. Structural revision reaches remembered root/group membership snapshots without page re-keying. |
| A6 | Preference read/type exceptions fall back locally; raw cache uses the same validated decoder and never repopulates the user's tree. Getter/setter JSON exclusions are tested using the real Fastjson serializer and old/injected JSON. |
| A7 | Actual Model restore and conditional restore delegate to the tested state helper and preserve BookmarkOrder's current-candidate semantics. Added/reloaded/restored favorites are hydrated. Icon-only work neither changes favorite keys nor writes the favorite file. |
| A8 | Prefix-only apply calls only prefix persistence. Insertions freeze every field and descendant of the ordered canonical roots inside the synchronized model boundary before the existing serial IO writer. Home overlay and favorite root cannot enter that saved root list. |
| A9 | Navigation trigger and original key/day interval remain. Main-owned gate coalesces in-flight requests, checks cancellation before apply, records terminal attempts including cancellation, and releases ownership. Valid remote acceptance retires delayed cache even when the prefix equals the default. |
| A10 | Diff and actual View path retain built-in drawable priority, Coil placeholder/error and 48dp size; favorite stable keys, click, long-press, accessibility actions, Pager/tab identity and terminal drag cleanup remain intact. |

The review covered tracked and new U1 product/test files, full task artifacts and check context, applicable specs and current call sites. The copied historical task directories are intentional planning provenance, not additional product implementations. No SDK/JSON dependency/Manifest/media/reader edits were introduced.

## Verification

- Focused board tests: PASS, 35 tests after reviewer fix (`/tmp/u1-review-focused.log`).
- TypeCheck/Debug build: PASS, `:nga_phone_base_3.0:assembleDebug` with the complete Debug unit gate (`/tmp/u1-review-debug.log`, BUILD SUCCESSFUL in 43s).
- Tests: PASS, all 13 modules, 740 tests (app 651), zero failures/errors/skips; XML reports independently inspected.
- Lint: PASS, forced `lintDebug --continue --rerun-tasks --console=plain`, 536 tasks executed, BUILD SUCCESSFUL in 46s (`/tmp/u1-review-lint.log`). All 13 XML reports independently inspected: 0 Error / 0 Fatal.
- `git diff --check`: PASS after the fix.
- Verdict: PASS after the local fix; no unresolved findings.
- Device checks: not run per project policy. No live NGA/CDN, account operation, signing/release/preview graph, installation, screenshot, push or publication. Offline state/serializer and static UI evidence does not prove on-device pixels/gestures, full Android lifecycle execution, or CDN availability.
