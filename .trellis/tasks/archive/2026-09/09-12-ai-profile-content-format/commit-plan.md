# Work and integration commit plan

The maintainer explicitly approved the reviewed repair and requested merging
it into `main`. The work commit and normal branch integration are part of that
requested delivery. Preserve all unrelated working-tree changes and published
history; use no amend, reset, or force push.

## Work commit

Proposed message: `fix(ai): normalize native topic string whitespace`

- `nga_phone_base_3.0/src/main/java/sp/phone/ai/summary/NgaTopicBodyParser.java`
- `nga_phone_base_3.0/src/test/java/sp/phone/ai/summary/NgaProfilePageSourceTest.java`
- `.trellis/spec/backend/ai-summary-contract.md`
- This task's PRD, design, execution plan, manifests, investigation reports,
  bug retrospective, implementation/check evidence, and validation report.

Stage only these owned paths. Ignored `.temp` harnesses, build output, and
private session/provider files are excluded. Reinspect all dirty paths before
committing and verify the quality gate and independent review have passed.

## Integration and finish order

1. Commit the verified work in `feature/ai-summary`, based on current
   `main@7cb9e50b`.
2. Recheck main's tip and working tree, then fast-forward main to the work
   commit when possible. If concurrent main changes exist, integrate them
   without losing work and rerun checks justified by the changed product tree.
3. Verify main includes the fix before marking the task completed.
4. Archive only this task through the normal task script, producing its
   `chore(task): archive ...` commit.
5. Record the session journal with the work commit, producing `chore: record
   journal`; keep bookkeeping after the work commit.
6. Bring main forward to include the archive/journal commits. Verify final
   branch ancestry, the unchanged checked product tree, and clean worktrees.

The feature baseline already includes main's earlier AI merge and library
test-dependency fixes. No unrelated branch reconstruction is necessary.
