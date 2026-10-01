# Research: U1 technical planning review

- Query: Does the draft U1 design/implementation plan preserve current state and persistence contracts, and are any parser/migration boundaries missing before user review?
- Scope: internal, read-only review of `design.md`, `implement.md`, companion research, and cited production code in the isolated worktree.
- Date: 2026-09-30
- Reviewed state: design explicitly pending user approval; no task activation or implementation.

## Findings

### Overall assessment

The draft addresses the demonstrated high-impact state hazards: ID-only entity equality, separate favorite/tree objects, repeated tree occurrences, shallow drag snapshots, unchanged-root child insertions, and asynchronous root-tree persistence. No remaining demonstrated state/persistence contradiction requires a different architecture. Three small boundary decisions should be made explicit before final approval; these are plan precision requests, not claims of an observed production regression.

### R1 — Define the recognizable category envelope precisely

- Files: `design.md:63`, `implement.md` acceptance cases U1-A2/A3/A6/A9; `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/data/ForumsListBean.kt:9`.
- Evidence: the current DTO defaults `code` to 0 and `result` to null, and the current repository accepts any non-null parsed bean (`ForumBoardRepository.kt:258`). Therefore "recognizable envelope" cannot safely mean merely a non-null DTO or default code value.
- Recommendation: specify a raw JSON object with an actual array-valued `result` as the minimum structural requirement for accepting prefix metadata. An empty `result: []` can still represent a valid prefix-only update. Missing/null/object/scalar `result`, and an unrelated object containing only `forum_icon_pre`, do not update icons. Do not infer new meanings for `code` without pinned evidence.
- Add fixtures distinguishing a valid empty result from a malformed result, and a valid category payload with non-string icon metadata. The latter must retain usable category additions while discarding just the invalid prefix. Preserve string numeric IDs/permissive Fastjson handling elsewhere.
- Classification: medium precision gap in a new parser boundary; no live response was observed.

### R2 — State the insertion deduplication key set separately from refresh traversal

- Files: `design.md:27`, `design.md:62`, `design.md:81`; `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt:90`, `:394`, `:412`.
- Evidence: current `boardMap` contains favorite nodes as well as the local tree. Consequently a favorite-only board ID currently prevents a same-ID remote entry from being added to the tree. Tree refresh must visit all repeated occurrences, but this is a different operation from insertion eligibility.
- Recommendation for minimal behavior preservation: re-evaluate the existing known-ID set on Main when applying a result, include current favorite-only IDs as the old map does, and update the set after each accepted insertion so duplicate incoming entries do not create duplicates in one pass. Preserve already-present duplicate tree occurrences. If the intended decision is instead to deduplicate only against local tree nodes, explicitly document that small behavior change and add a favorite-only fixture; do not let the choice emerge accidentally from helper implementation.
- Add a case with the same remote ID repeated in one result, and one with a matching favorite but no tree occurrence. This makes the revised IO/Main split behavior reviewable.
- Classification: demonstrated current behavior plus ambiguous draft wording; not a recommendation to repair unrelated product behavior.

### R3 — Include malformed saved-prefix preference reads in fallback behavior

- Files: `design.md:53`, `implement.md` acceptance U1-A6; `lib_base_common/src/main/java/gov/anzong/androidnga/base/util/PreferenceUtils.java:64`, `:88`.
- Evidence: `PreferenceUtils.getData(key, String)` directly delegates to `SharedPreferences.getString`; it has no local exception fallback. A stored value of the wrong preference type throws instead of reaching URL normalization. This new key is expected to be a String, so this is not evidence of an existing corrupted installation.
- Recommendation: treat a failed/wrong-type saved-prefix read like an invalid string: proceed to raw-cache/default initialization without clearing favorite or local-board data. Add it to the existing startup precedence test seam. Keep this handling local to the new prefix store; no global preference migration is needed.
- Classification: low-cost completeness improvement for the promised malformed-metadata fallback, not a high-severity discovered incident.

### Verified alignment and cases already covered

- `design.md:67` excludes derived state from both JSON directions; U1-A6 covers old JSON and injected disk values. Real Fastjson encoder/decoder assertions remain necessary because annotation spelling alone is not runtime evidence.
- `design.md:69` covers actual tree/favorite traversal and `:73` preserves ordinary-grid occurrence behavior. No equality or stable-key migration is planned.
- `design.md:70` includes restore/reload hydration, so even an older drag snapshot restored after external favorite reload uses the latest prefix.
- `design.md:72` separates structural revision from icon observability without revision-based remounts. The tests explicitly require actual snapshot notification and nested child visibility.
- `design.md:82` freezes canonical nested data before async IO; the implementation plan replaces the brittle `localBoardList.toList()` source assertion with behavior coverage rather than retaining the race.
- Prefix-only updates do not write favorite/local-list files. Favorite candidate-current semantics and home-ID overlay remain independent of derived metadata.
- No local board cache version bump, timestamp key migration, forced startup request, asset rewrite, JSON-library migration, or R7 change is planned.
- The implementation plan's repository-wide Debug unit/lint gate matches `.trellis/spec/backend/android-quality-guidelines.md:149`, including report inspection. The state research has been clarified to distinguish focused iteration checks from that required final gate.

### Related specs and references

- `.trellis/spec/frontend/state-management.md`: stable favorite identity, conditional persistence/rollback, app-wide membership.
- `.trellis/spec/frontend/component-guidelines.md:289`: favorite gestures; `:300`: home tabs and fixed favorite page.
- `.trellis/spec/backend/android-quality-guidelines.md:149`: final Debug quality gate; `:7`: no device operations without current authorization.
- `research/state-persistence-ui.md`: detailed current-source evidence, dependencies, and test seams.
- `research/upstream-refresh-and-url.md`: pinned upstream sources and URL/refresh adaptation decisions.
- No external documentation/network source was consulted for this review.

## Caveats / Not Found

- This is a plan review, not implementation approval or a passing product check. No code, build, JSON serializer experiment, device operation, or network request ran.
- The mutableState/JSON annotation and snapshot-observer combination must be proven against the resolved Compose/Fastjson dependencies during implementation.
- R1–R3 are precise local choices the main session can incorporate; they do not require creating a new task or expanding into R7.
