# Execution coordination and review notes

This is a chronological coordination log. The checkpoints below retain their
original status; the implementer's completed verification is in
[check-results.md](check-results.md), and the independent review owns any later
product/test fixes and reruns.

## Workspace and approval

- Revised scope approved by the user on 2026-09-12 after terminology clarification.
- Product implementation and all checks run in /home/toph/nga-just-works-compat-mode on feature/thread-detail-compat-mode, starting at 5bb92cf0 (includes 6203dad5).
- Root main and the existing AI-summary worktree are not implementation targets.
- Only sdk.dir was copied into the ignored worktree local.properties. Gradle 8.7 / JDK 17 are available; no device or NGA operation ran.
- Implement agent owns product/test edits; main owns metadata/spec synchronization and subsequent independent review. No product commits before the concrete Phase 3.4 commit plan is reviewed.

## Source facts to cover in final review

- ArticleListAdapter also retains a fixed 20-element LocalWebView array. Variable response sizes require dynamic view retention as well as normalized page counts and actual PID/lou lookup.
- The implementer proposes an endpoint-local byte transport shared by enabled/scoped normal and App requests. Preserve normal wire/header/parser semantics and ordinary-only prefetch; this must not silently replace the global/default-off normal path. Record the actual boundary and tests when implemented.
- New source/page-layout identity must reach presenter READY/in-flight reuse, activity count updates, cached-page storage and list/open/delete; a DTO field alone does not prevent old data from landing in a new page.
- Actual comment kind, unknown score/UID, source/raw/HTML separation and unavailable-body states must reset correctly when row views are recycled.

## Spec synchronization map

After actual interfaces stabilize, update executable contracts (English):

1. Add the feature operation/source/paging contract without attributing August App behavior to the July pinned snapshot.
2. Update the operation registry's current-fork THREAD.PAGE delta and add a separately sourced THREAD.PAGE.APP_COMPAT record.
3. Extend cache signatures/layout/error matrix for owner/source/page-layout entries while retaining the original raw layout and repaired metadata preparation.
4. Extend prefetch state/key/eligibility documentation without changing next-two and final-page exclusion.
5. Add native reader/quote/comment/UID/cache UI behavior and preserve current long-press/tab/menu controls.

No check result is asserted by this note. The code gate and independent check are still pending.

## In-progress review follow-ups

- The first synthetic App parser run passed. The implementer then added a
  malformed object/array `content` plus valid `subject` regression and is
  rebuilding the parser/settings tests; no full feature gate is claimed yet.
- App author absence must not create `[uid=0]` quote links or an automatic
  mention of the neutral display name. Preserve source-based reply capability
  and the existing anonymous convention while fixing all three quote builders.
- A posted anchor scroll must still belong to the displayed response and a
  resumed view, including same-generation explicit refreshes. A generation
  check alone does not protect a captured adapter index after data replacement.
- The registry's old header-removal statement contradicted current source and
  the other network specs. It is corrected: ordinary Retrofit still sends
  `X-User-Agent: Nga_Official`; the App client preserves this source identity.
- `adoptPage` publishes LiveData synchronously. A scoped reader may consume the
  new generation's handoff before the old callback returns; that old callback
  must not then reset the new READY/loading state.
- An already loaded floor remains locally addressable even in an author query
  or when page size is unknown. Only cross-page estimation needs the full-query
  floor/page mapping; do not disable local lookup with that stronger condition.

## Parallel test ownership

- Main owns only `ArticleByteClientTest.kt` in addition to specs/task metadata,
  with explicit implementer agreement. It verifies the stable operation-local
  client using synthetic fake Calls, without Android/global-account access or
  sockets. Product/client code and all other tests remain implementer-owned.
- Main does not start Gradle while the implementer is building. The new tests
  join the implementer's next focused/full gate.
- The earlier focused XML reports contain 8 App parser tests and 9 settings
  tests (17 total), correcting the implementer's initial estimate of 19.

## Verification checkpoint before independent review

- The required app/common unit invocation passed. Main inspected its XML:
  app 198 tests in 31 suites, common 62 tests in 4 suites; zero failures,
  errors or skips. All 11 fake byte-client tests passed.
- The implementer reported cache/store tests 7 and metadata/preparation tests
  14 passing earlier. Build, all-module lint and broad debug diagnostics are
  still in progress; the checkpoint is not the final quality verdict.
- Original App row source is retained. Reply-header normalization now applies
  to renderer and temporary quote input, preserving edit content. This is a
  refinement of the research's normalized-source suggestion, consistent with
  the accepted source/HTML/raw separation requirement.
- Independent review should exercise real synthetic parsers through owned cache
  replay, and verify that source handoff without a reliable position emits the
  promised position notice. Parser-only and fake-decoder dispatch assertions do
  not by themselves establish those integration behaviors.

## Independent-review handoff

- The implementer froze product/tests and released Gradle after the final
  app/common unit, debug build and app lint pass. Main independently inspected
  app 199/common 62 passing tests and all 13 Android lint XML reports with
  zero Error/Fatal. The broad debug diagnostic has only the two unchanged
  fixture compilation failures documented in check-results.md.
- The independent checker now owns product/test fixes and Gradle; main owns
  specs, metadata, the adoption ledger and the concrete commit plan. A
  retained-data refresh regression was found in the actual presenter path
  and is being fixed with a regression test.
- Main reconciled handoff feedback, synchronous LiveData adoption, original
  editable source, and bounded legacy ZIP import/export contracts. The
  prefetch contract now explicitly prevents old retained data from marking an
  active foreground refresh READY.
- Root-main task PRDs contain only worktree handoff pointers. Current feature
  specs, implementation artifacts and product code remain in the isolated
  worktree. The root's other untracked tasks are unrelated and untouched.
- The checker reproduced BOM-prefixed HTML and BOM-only content incorrectly
  classifying as FORMAT, and scoped normal object/array content being coerced
  to a complete string response. Main approved the narrow scoped-normal
  completeness fix under existing R3/R5/R6: preserve trustworthy rows and
  valid source cases, block source actions/cache for malformed core content,
  and leave default-off/legacy acceptance unchanged. No new protocol or
  user-scope decision is needed for these contract fixes.
- Main reviewed the proposed damaged-comment whole-page error against the
  actual synthetic child identity and R6. The child/parent identities are
  known, so the approved correction is local incomplete display, not whole-page
  rejection. `HtmlCommentBuilder`'s unconditional `indexOf("[/b]") + 4` is
  repaired only for complete recognized headers, as already allowed by design
  §5. Core unit/lint join the final gate; no core module migration is involved.
- Main also confirmed that readable independent COMMENT/UNKNOWN rows with a
  real own PID retain source-based reply/quote actions. The approved row-kind
  rule hides inappropriate comment/author-filter actions, not all source
  actions. Unknown parent fields are not a substitute for the row's own PID.

## Final reviewed checkpoint

- Independent review is complete, with no unresolved in-scope findings.
  Product/test and Gradle ownership returned to main; no further product edits
  were made during the documentation wrap-up.
- Final app/common/core gate passed 211 + 62 + 5 = 278 tests in 39 suites;
  main independently read the XML and final/focused command logs. Debug build
  passed; all 13 Android lint XML files exist with zero Error/Fatal.
- Core comment-header handling, incomplete child display and own-PID reply
  actions are included in the final tests. Their actual signatures and
  regression requirements are synchronized in specs.
- Final details: [delivery.md](delivery.md),
  [independent-check.md](independent-check.md). The concrete work-commit plan
  is [commit-plan.md](commit-plan.md); user confirmation is still required for
  execution. No commit, push, merge, task archive or journal update has run.
