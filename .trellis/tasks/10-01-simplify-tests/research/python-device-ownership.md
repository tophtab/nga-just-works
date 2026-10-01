# Python and device coverage ownership

Audit boundary: the three Python test files and one Android instrumentation
class. Product scripts, workflow, signing and device execution are unchanged.
The issue is repeated executions of identical Bash branches and parser fixtures;
the behavioral owners already exist. Main session owns all execution evidence.

## ReleaseWorkflowTest (all 26 baseline methods reviewed)

| Method (`test_` prefix omitted) | Decision / owned invariant |
| --- | --- |
| channel_identities_and_staged_assets | Retain main, feature, stable branches; identity, manifest and checksum integration |
| only_reachable_stable_tags_set_the_preview_base | Retain graph reachability and newly reachable version advancement |
| invalid_tags_and_non_push_or_non_publication_refs_fail_identity | Retain malformed tag, numeric overflow, non-publication ref and non-push event classes |
| missing_stable_base_fails_identity | Retain fail-closed missing base |
| arbitrary_legal_branch_names_stay_literal_and_make_bounded_assets | Retain shell substitution/backtick/quote, mixed ASCII, Unicode fallback and truncation; actual Bash create/patch safety |
| same_sha_and_colliding_slugs_keep_independent_releases | Retain representative equal-slug pair; raw-name hash and mutual cleanup isolation |
| branch_channel_remains_the_same_across_commits | Retain real Git commit advancement, stable channel and old-tag deletion |
| publication_and_cleanup_preserve_other_channels | Primary cleanup/migration owner: main, exact AI and lookalike branch; malformed suffix fixture boundaries retained |
| only_the_original_ai_branch_migrates_its_legacy_tags | Remove duplicate of preceding exact-AI/lookalike channel outcomes |
| same_commit_rerun_validates_then_replaces_the_same_assets | Retain representative rerun; no duplicate create, exact upload and clobber |
| wrong_sha_or_non_prerelease_blocks_replacement | Retain both conflicts on representative feature channel |
| api_and_publication_failures_keep_the_previous_preview | Retain all six command failure sites and partial-output failures on AI channel with legacy release |
| release_title_is_passed_as_literal_data | Remove; arbitrary legal branches execute literal create and patch with shell sentinels |
| stable_publication_uses_validated_notes_and_skips_cleanup | Retain valid notes and missing-file fail-closed publication |
| staging_rejects_incorrect_manifest_values | Retain all six distinct manifest fields |
| signature_verification_failure_stops_staging | Retain signer fail-closed |
| publication_rejects_missing_extra_or_corrupt_assets | Retain all four asset defects |
| cleanup_lookup_failure_does_not_delete_partial_results | Retain partial output failure before destructive calls |
| cleanup_stops_at_a_failed_deletion | Retain stop after failed deletion, later tags survive |
| sdk_setup_avoids_retired_tools_and_installs_required_packages | Retain historical SDK regression and actual Bash argument recorder |
| trigger_keeps_all_branch_pushes_tags_and_documentation_skips | Remove low-risk YAML snapshot; explicit loss of trigger glob/documentation-ignore assertion |
| ref_key_hashes_the_exact_full_ref_without_a_checkout | Retain case pair, branch/tag pair, shell and Unicode; two attempts prove SHA/run independence |
| ref_key_pre_job_gates_build_concurrency_without_permissions_or_secrets | Retain narrow permission/secret/concurrency wiring exception |
| cache_is_writable_only_for_the_exact_main_identity | Retain exact main, Main, feature and stable; case-insensitive expression interpreter |
| case_only_branch_names_keep_independent_preview_releases | Retain Foo/foo and main/Main release isolation |
| publication_selection_and_branch_concurrency | Retain branch/tag and non-push/non-publication outcomes; delete same-branch spelling variants |

Every baseline Git fixture starts five Git subprocesses in setUp, writes three
executable stubs and creates an isolated directory. Each derive executes actual
Bash, Git graph queries and the version CLI; each stage executes metadata/signer
stubs and checksums; publish/cleanup starts gh fixture processes and actual jq.
These boundaries remain real offline executions. No server fixtures are used.
The meaningful reduction is scenario rows (and their repeated process trees),
not solely the three removed methods. No new harness is introduced.

## Version and release notes

All eight version methods were reviewed. Stable arithmetic, preview-slot
arithmetic and upper-field arithmetic examples overlap the retained CLI output
and stable/preview ordering boundary; keep explicit field-max boundary and both
CLI success/failure methods. Invalid version shape variants share one regexp
failure; keep one shape representative plus both independent field overflows,
both slot endpoints, zero result and exact Android maximum/maximum+1.

All three notes methods were reviewed. Keep one committed-file CLI success and
missing-file CLI failure. All eight invalid-section rows have distinct parsing
or required-format relevance (duplicate, indented duplicate, order, blank,
empty item, indented code, fenced code, malformed heading); retain them via
existing `validate_release_notes(Path)` entry point. This reduces process starts
without claiming those eight scenarios were deleted.

## Device (all three methods retained unchanged)

- `keystoreCiphertextReopensAndClearDeletesBothSides`: actual Android
  non-exportable key, encrypted-file reopen, prompt roundtrip and deletion.
- `interruptedAtomicReplacementPreservesCommittedConfiguration`: real
  AtomicFile interrupted transaction recovery.
- `lostKeystoreKeyCannotRestoreOrReuseCiphertext`: real key deletion invalidates
  ciphertext and removes unusable storage without recreating a key.

JVM fakes do not own these platform guarantees. Device execution: not run per
project policy; no device operation is required for handoff.
