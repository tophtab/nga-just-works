# Research execution and review plan

## Planning research (authorized)

- [x] Recover original session scope and task-creation approval.
- [x] Inspect pinned checkouts and current clean product baseline.
- [x] Dispatch independent upstream research and inspect current operation owners.
- [x] Consolidate four-way matrix and ranked learning candidates.
- [x] Verify local links, revisions, evidence caveats and document whitespace.
- [x] Prepare concrete findings and planning artifacts for presentation.

- [x] Investigate issue/fix history for thread reliability and HTTP403.
- [x] Trace LNGA signed HTML request, extraction and current-reader integration.
- [x] Document feasibility, concrete parity gaps and proposed offline gates.

## Subsequent phase boundary

The task remains `planning` during this source research. Do not run `task.py start`
or modify product code on the basis of the comparison. If a later execution phase
is requested, review the latest artifacts and receive approval for that specific
scope. Any API adoption needs explicit desired behavior and offline acceptance
fixtures; it is outside this report's requirements.

## Review commands

```sh
git diff --check
git status --short
python3 .trellis/scripts/task.py validate .trellis/tasks/10-02-nga-client-api-comparison
```

Also parse JSONL manifests and verify referenced files exist. The task validator
checks context manifests, not the truth of source-derived API claims. Review the
individual evidence ledgers for that. Do not execute upstream demonstration tests
that issue real NGA requests.
