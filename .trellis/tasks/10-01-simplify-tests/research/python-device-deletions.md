# Python/device deletion ledger

Baseline: 37 Python methods, 3 files; final: 32 methods, 3 files. Device: 3
methods, 1 file retained unchanged. This ledger counts input rows separately
from methods; making the notes parser cheaper is not counted as deletion.

## Removed methods

| File / method (`test_` omitted) | Deleted rows | Reason and remaining owner |
| --- | ---: | --- |
| test_derive_android_version_code / semantic_stable_bases | 3 | Repeated decimal arithmetic; stable/preview adjacency test plus actual workflow stable and newly reachable tag identity own arithmetic |
| test_derive_android_version_code / preview_slots_use_the_stable_semantic_prefix | 3 assertions | Repeated slot arithmetic; exact CLI output for 5.10.12/345 and maximum-slot adjacency retained |
| test_release_workflow / only_the_original_ai_branch_migrates_its_legacy_tags | 4 | Exact AI, hyphen lookalike, slash lookalike and longer branch; cleanup matrix retains exact AI migration and hyphen lookalike refusal, with longer-prefix tags protected. Slash/longer ref spelling variants lose direct assertions |
| test_release_workflow / release_title_is_passed_as_literal_data | 1 | Arbitrary legal branch test already executes create AND patch for dollar substitution, backticks and quotes, asserting literal title and absent sentinels |
| test_release_workflow / trigger_keeps_all_branch_pushes_tags_and_documentation_skips | 1 | Low-risk source snapshot; exact YAML trigger globs and documentation ignores lose direct assertion. Actual publication/cancellation conditions remain tested |

## Removed rows in retained methods

| File / method | Before → after rows | Exact removed inputs; retained owner |
| --- | ---: | --- |
| version / invalid_versions_and_field_overflow_are_rejected | 8 → 5 | `5.5`, `v5.5.0`, `5.-1.0`; same regexp rejection as retained `5.5.0-debug.1`. Both field overflow and both slot boundaries unchanged |
| version / android_version_code_range_is_enforced | 3 invalid → 2 invalid | `211.0.0/0`; same final upper-bound rejection as `210.0.0/1`; exact max and zero retained |
| notes / committed_release_notes_are_valid | 2 → 1 | `4.9.0` committed formatting duplicate; `4.10.0` CLI success retained |
| workflow / channel_identities_and_staged_assets | 6 → 3 | compat, IP-location and nested review branch; representative AI feature, main and stable integration retained; nested/mixed ASCII separately exercised by arbitrary branch flow |
| workflow / invalid_tags_and_non_push_or_non_publication_refs_fail_identity | 7 → 4 | rc tag duplicate malformed tag; pull_request branch and dispatch stable duplicate non-push outcome; malformed tag, field overflow, pull ref and dispatch main remain |
| workflow / same_sha_and_colliding_slugs_keep_independent_releases | 4 pairs → 1 pair | AI/hyphen, truncated long-name pair, Unicode pair; representative `feature/a-b` versus `feature/a/b` proves raw-name hashing. Truncation/Unicode fallback remain in arbitrary names; AI/hyphen isolation remains in cleanup matrix |
| workflow / publication_and_cleanup_preserve_other_channels | 5 → 3 | compat and nested positive publication rows; main, exact AI and AI lookalike still run complete cleanup. All 59 seeded release fixture entries remain to prove foreign/malformed/non-prerelease retention, including 6 prefixes × 6 malformed suffixes and 6 stable flags |
| workflow / same_commit_rerun_validates_then_replaces_the_same_assets | 3 → 1 | main and nested ref duplicates; AI same-commit rerun retains exact identity/assets and PATCH/upload checks |
| workflow / wrong_sha_or_non_prerelease_blocks_replacement | 6 → 2 | main and nested × both conflicts; both independent conflicts retained on AI channel; no publication/cleanup/state changes asserted |
| workflow / api_and_publication_failures_keep_the_previous_preview | 12 → 6 | nested branch × tags/releases/detail/patch/create/upload; all six actual failure sites retained on AI with legacy release protection and partial API stdout |
| workflow / ref_key_hashes_the_exact_full_ref_without_a_checkout | 9 → 6 refs | main/Main/MAIN; Foo/foo retains case boundary. Two attempts each remain (18 → 12 executions); branch/tag, shell and Unicode retained |
| workflow / cache_is_writable_only_for_the_exact_main_identity | 7 → 4 | MAIN, feature/main, numeric branch; Main retains case sensitivity and AI feature retains general branch read-only path; main write and stable read-only retained |
| workflow / case_only_branch_names_keep_independent_preview_releases | 5 → 4 | MAIN duplicate of Main; main/Main plus Foo/foo retain channel and hash boundaries |
| workflow / publication_selection_and_branch_concurrency | 14 → 6 | Main, MAIN, Foo, foo, AI, compat, feature/other, nested review all share branch-prefix path. Main, stable, dispatch branch/tag, pull_request pull-ref and push pull-ref remain |

## Infrastructure and unchanged rows

- Invalid notes: all 8 rows retained using existing `validate_release_notes(Path)`
  instead of launching Python. CLI success and missing-file failure still execute
  as real subprocesses. Notes process starts: 11 → 2. Version CLI starts: 2 → 2.
- Workflow methods: 26 → 23, so setup Git subprocesses: 130 → 115 (five each),
  isolated Git directories: 26 → 23 and written executable stubs: 78 → 69.
  Each remaining boundary retains actual Bash, Git, checksums, jq and gh stubs.
- Workflow input rows use one per outer matrix item (collision pair counts as
  one; nested conflicts/failure sites count separately; ref-key retry attempts
  are reported separately above). Singleton methods count as one regardless
  of internal sequential transitions. Under that explicit convention workflow
  rows decrease 109 → 65. Notes rows decrease 11 → 10. Version rows decrease
  21 → 11 (the three independent slot examples count separately; assertions
  composing one boundary transition do not). Total input rows: 141 → 86.
  These are audit input rows, not unittest's reported method count or an
  assertion counter; e.g. every collision pair still publishes both branches.
- Unchanged critical rows: 6 arbitrary legal branch cases; 6 staging fields;
  4 asset defects; all missing base, reachability, commit advancement, signature,
  partial cleanup lookup, failed deletion, stable notes and SDK smoke outcomes.
- No dependency removal: every existing Python import/helper still has users.
  Removed orphan `long_prefix` fixture variable. No test assertions were changed
  for remaining workflow scenarios; only repeated inputs/methods were removed.
- No production or device source changed. Main session owns passing-suite and
  benchmark evidence; this worker did not run Gradle, Python suites or devices.
- Python source lines: version 91 → 75, notes 75 → 77, workflow 869 → 796;
  total 1,035 → 948. AST parsing of all three changed files passed; no test suite
  was invoked. Main measures direct process starts against the snapshot and
  final sources; the setup/notes counts above describe known local call sites,
  not all transitive shell/gh/jq subprocesses.
