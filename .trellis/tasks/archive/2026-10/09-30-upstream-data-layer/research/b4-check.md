# B4 independent re-review — PASS

Final review: 2026-10-01; B3 base `758aa6b9`, frozen corrected B4 in the dedicated
`feat/upstream-adoption` worktree. The initial blocked review is retained below
as history; all five findings are now resolved. This accepts B4 only, not the
pending actual application R8/U3-A7 gate or the later B5/U4 work.

## Findings (fixed)

- File: `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/ReadThreadBeanFallbacks.kt`
- Issue: the finite inventory used `_isInBlackList`, but the actual JSON2
  FieldReader metadata for the exact setter key is `_IsInBlackList`. With no
  user overlay, that exact key produced true in the actual B3 facade and false
  in the new facade in both modes. The lowercase smart alias still worked.
- Fix: use the exact metadata name. Added
  `blacklistBeanNamesSurviveBothFacadeModes` to ReadThreadFallbackParityTest;
  it executes the real bean conversion and both public facade modes for the
  exact capital-I name and both existing aliases. No baseline was regenerated.
- Reproduction: `probes/b4-reviewer/AliasProbe.java` and
  `alias-probe-result.txt` retain the pre-fix host observation. Compiled the
  archived real B3 facade and invoked both implementations with the resolved
  application test classpath; no duplicated old mapping algorithm.

## Findings (not fixed)

None remain in B4. The prior five findings were independently checked:

1. Nonempty canonical vote overrides bean aliases; empty/null falls back.
2. Every primitive alias assignment is converted before later overwrite;
   malformed earlier numeric/boolean assignments retain failure semantics.
3. Attachment container aliases preserve usable typed attachments; invalid
   raw-map artifacts have independent real production-projection evidence.
4. Bean hotReplies survives absent/null literal17; the original literal17
   override and splitting order are preserved.
5. App-only bean fallbacks use actual metadata and a finite inventory; core
   receives only invalid structural row paths for conversion-error priority.
   No app bean, opaque JSON output or renderer callback crosses into core.

Read all task artifacts/check.jsonl references and the updated ordinary-reader
wire contract. Inspected the complete DTO/decoder/mapper/fallback/facade paths,
including count coercion, sparse traversal, source validity, comments, user
and group overlays, topic aliases, and attachment rendering order. Enumerated
actual JSON2 field readers for ThreadRowInfo, ThreadPageInfo and Attachment
using the compiled production classes; all fields have an explicit protocol,
app fallback, or existing override responsibility after the local fix.
NormalArticleParser and AppArticleParser remain unchanged against B3.

Verified the original156 input/output hashes are unchanged with exactly the
five previously approved allowances. Additional104 cases retain their frozen
hashes and four specifically named malformed-attachment no-op artifacts;
72 additional real-attachment-projection outcomes compare exactly. The actual
archived B3 oracle differs from git source only by class/self-reference rename
and the approved nonnull String.isEmpty host equivalence. Final tests contain
no regeneration branch or dependency on retained private legacy helpers.

## Verification

- Lint: **pass**. Fresh implementer full lint ran538/538 tasks. After the reviewer
  fix, app lint reran its affected analysis; independently parsed all13 current
  module XML reports, zero Error/Fatal.
- TypeCheck: **pass**. Java/Kotlin Debug compilation and app assembleDebug pass.
- Tests: **pass**,812 total (app699/core31), zero failures/errors/skips in all13
  module report directories. Includes the new exact-key regression and all
  immutable facade snapshots; unchanged modules reuse the fresh full gate.
- Reviewer command: `./gradlew :nga_phone_base_3.0:assembleDebug
  :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:lintDebug
  --continue --console=plain`; successful in52s,554 tasks (18 executed).
  Log: `/tmp/b4-review-fix-gate.log`.
- `git diff --check`: pass. No reviewer commit or unrelated product edits.
- Actual app R8 remains pending U4; no claim of minified runtime. Device/NGA/
  external media/signing/publication operations were not run.

---

# B4 independent review — BLOCKED

Reviewed frozen B4 against B3 `758aa6b9`, the task contracts, fixed156-case baseline and real retained legacy helpers. Existing snapshot allowances are bounded and supported by the production attachment projection; they do not cover the additional defects below.

## Findings (fixed)

None. Product files remain unchanged by reviewer. Temporary host probe was archived outside test sources after execution.

## Findings (not fixed)

1. **Canonical vote override lost** — `ReadThreadWireDecoder.post` maps only bean-selected vote; `ReadThreadLegacyMapper.post` omits the old `buildRowVote` nonempty canonical override. `{"vote":"canonical","Vote":"alias"}` returns canonical in the old helper and alias in the new facade, in both modes. Restore the explicit canonical override while retaining bean fallback for missing/empty canonical values.
2. **Earlier malformed alias suppressed** — `beanField` converts only the last matching key. `{"pid":"bad","Pid":9}` throws in the old bean/helper (outer-null) but succeeds as pid9 in both new modes. Actual bean conversion validates assignments as they occur; last-assignment precedence alone is insufficient. Restore conversion failure semantics across all numeric/boolean/topic/attachment aliases, not just pid.
3. **Attachment container smart alias dropped** — `post` reads literal `attachs` only. `{"Attachs":{"0":{"attachurl":"x"}}}` retains a typed attachment in old rows but drops it in new rows, both modes. Unlike the invalid-map no-op artifact already allowed, this is a usable attachment and affects real rendering. Preserve bean alias conversion for the container.
4. **Fallback hot reply list dropped** — `{"hotReplies":["1","2"]}` survives old bean mapping when literal17 is absent; new mapper always derives the list solely from17. Both modes lose the list. Preserve typed fallback then apply literal17 override in original order.
5. **Additional bean fields need an explicit boundary decision** — probes show `comments:[{content:"nested"}]` and `isInBlackList:true` survive original bean mapping absent later overrides, but are dropped by new mapping. The former differs from protocol `comment`; the latter conflicts with the intended app-owned blacklist boundary. Do not silently claim all bean inputs retain parity. Main should decide supported protocol scope and capture exclusions or typed fallback semantics. These are design/interface questions; reviewer did not introduce raw display state into core.

The common alias-policy defect and fallback additions span the typed boundary and deserve coordinated implementation/regression coverage. No partial product fix was made while these related changes await main review.

## Reproduction evidence

`research/probes/b4-reviewer/B4ReviewerProbeTest.kt` calls the unchanged retained private production `buildThreadRowList` via reflection with deterministic renderer/blacklist, then compares the current public facade. `probe-results.xml` contains12 actual old/new outputs (six inputs × both modes). No copied legacy algorithm or regenerated frozen baseline. The temporary executable test was removed from application sources so B5 can delete the old helpers without a reflective dependency. Convert confirmed cases into immutable baseline regression assertions during correction.

## Verification

- Lint: existing fresh13 reports independently inspected; zero Error/Fatal.
- TypeCheck/Debug: frozen implementation gate passed; no reviewer product edits.
- Tests: frozen806-test gate passed; reviewer reproduction executed successfully and exposed the differences above (diagnostic output, not a parity-pass assertion). Full suite restored after removal of the temporary probe; see `/tmp/u3-b4-reviewer-tests.log`.
- Actual application R8 remains pending U4/U3-A7. No new device/network/signing evidence.

B4 cannot pass until findings1–4 are resolved and finding5 is explicitly reconciled with the boundary contract. Do not perform B5 helper deletion before correction evidence is captured.
