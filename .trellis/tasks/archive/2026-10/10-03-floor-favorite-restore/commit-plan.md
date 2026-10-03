# Proposed commit batch

Status: user confirmed commit/archive and stable 6.2.5 publication. Work committed as `090dce71`; final Debug gate, release-notes validator and local release-tag/version identity check all passed. Archive/journal and main/tag push follow.

## Work commit

`fix: restore floor favorite action`

- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu.xml`
- `nga_phone_base_3.0/src/main/res/menu/article_list_context_menu_with_tid.xml`
- `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/ArticleListFragment.java`
- `.trellis/spec/frontend/component-guidelines.md`
- `release-notes/6.2.5.md` (added under the subsequent explicit release authorization)

Restores the existing favorite action in both original menu positions and reconnects the unchanged selected-row tid/pid call. Synchronizes the active menu spec. No navigation, API, persistence or success-feedback change.

## Bookkeeping after the work commit

After commit approval, follow the project finish workflow: archive only `10-03-floor-favorite-restore` with its completed research/planning/verification artifacts, then record this session using the work commit hash. These produce the normal separate archive and journal commits.

## Excluded pre-existing work

`.trellis/tasks/10-02-nga-client-api-comparison/` was untracked when this session began and belongs to another task. It is excluded from the batch and archive operation.

## Confirmation requirement

`.trellis/workflow.md`, Phase 3.4 step 5: “Present the plan once, ask for one-shot confirmation”. The original-action implementation has already been approved; this remaining confirmation applies only to the local commit batch.

## Validation

562 JVM tests passed; all 13 lint reports have zero Error/Fatal; Debug APK packaging passed. Evidence: [check-report.md](check-report.md).

## Stable release authorization and order

After work commit and finish bookkeeping, fast-forward local main to the completed branch, ensure remote main is still the inspected 0e5ef39d5cc16d6bf2ff4fd8192fad9c52583a77 and 6.2.5 is absent, create annotated 6.2.5 at final main, and atomically push main plus the tag. Stop after successful push per android-quality-guidelines.md; no CI polling or device installation.
