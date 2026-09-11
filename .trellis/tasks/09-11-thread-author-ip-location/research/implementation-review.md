# Implementation Review: Supplemental Profile Location Reads

Date: 2026-09-12. Reviewer: `implement_ip_location_resume`.

Resolution update: F1-F3 below describe the initial recorded snapshot. All are
fixed and independently reverified in `request-control-fix-validation.md`, then
checked with permanent tests in the parent's `research/combined-check-report.md`.
Their original evidence and observed line anchors are retained for the audit.

The parent reassigned this agent to read-only review after confirming that an
earlier implementation writer was still producing files. This report is the
reviewer's only workspace edit. Product code, build configuration, and existing
task artifacts were not modified. No Gradle or live NGA/device operation ran.

## Inspected snapshot

Files below are under `nga_phone_base_3.0/src/main/java/sp/phone/profile/`.
They were still being implemented; line anchors identify the observed version.

| File | SHA-256 |
| --- | --- |
| `ProfileLocationParser.java` | `c5eb57498dcf338c9b108e90be4efcb3f20db5b54e012e2b4f56018d970abdd8` |
| `AuthorLocationRepository.java` | `b5f7c53e4b70f8cb0831487e8c0358062da6fe438e29c71ca924c2b2213c9899` |
| `ProfileLocationTransport.java` | `1e6ce40609df9a128296e95fad8f9b5f5dad786b806c4594e23089d8222c2f3b` |

Temporary standalone Java probes copied these source files into disposable
directories outside the worktree, compiled with the locally cached project
dependencies, and exercised only synthetic data or a loopback fake HTTP server.
The temporary directories were deleted on completion. These probes substantiate
the findings below; they do not replace the feature's permanent test suite or
the final Android gate.

## F1: Non-profile site rejection can continue the author queue

Priority: P1. Status: confirmed, awaiting owner fix.

`ProfileLocationParser.parse` at lines 19-27 detects HTML only before wrapper
normalization, and returns ordinary `FAILURE` for every JSON parse exception.
Nonempty plain-text site rejection and known-wrapper-prefixed HTML therefore
do not pause supplemental requests. Repository `complete` treats `FAILURE` as
an individual-key cooldown and immediately dispatches the next UID.

Executed reproductions with Fastjson `1.1.71.android`:

```java
ProfileLocationParser.parse("Access denied", 17).kind
// Actual: FAILURE
ProfileLocationParser.parse("/*$js$*/<html>Access denied</html>", 17).kind
// Actual: FAILURE
```

Expected: these non-profile responses stop work for the affected session. This
comes from the approved design's unknown site-error/non-profile pause rule, not
from an invented NGA error code. Normalize the supported wrapper before deciding
whether a bounded nonempty response is an HTML/site response. Keep malformed
profile syntax and genuinely empty/network failures distinct as the operation
contract requires. Add parser fixtures and a repository test proving that another
author does not start after these inputs.

Related transport boundary: `readResponse` at lines 97-99 exits before reading
any non-200 5xx body. A 503 response containing a challenge page cannot reach
the parser's stop classification. Include a bounded 503 challenge fixture in
the fix/verification instead of assuming challenges always use 200 or 403.

## F2: A late stop signal is discarded after same-session invalidation

Priority: P1. Status: confirmed, awaiting owner fix.

Repository `complete` at lines 253-257 rejects an obsolete UI epoch before
processing rate-limit or session-rejection results. A valid stop response already
queued on the main executor can therefore be lost when an account notification
invalidates consumers but the settled immutable request session is unchanged.
The next page's queued work then starts immediately for the rejected session.

Executed reproduction with a fake transport, a constant fake clock, and a queued
completion executor:

1. Create one valid synthetic session and restore an empty repository cache.
2. Subscribe online authors `[17, 18]`; the transport starts author 17.
3. Deliver `SESSION_REJECTED` (or `RATE_LIMIT` with a future retry timestamp)
   from the fake transport, leaving repository completion pending on its executor.
4. Call `invalidateSession()` as an account signal would; keep `sessionSource`
   returning the same immutable identity and credentials.
5. Subscribe a newly delivered page with author 19.
6. Drain the original completion.

Observed for both result kinds:

```text
same_session_late_SESSION_REJECTED_request_starts=2
same_session_late_RATE_LIMIT_request_starts=2
```

Expected: one request total; the selected session's stop still applies. UI/data
epoch validity must remain separate from transport stop ownership. Record a stop
against the operation's captured session/scope before excluding stale UI delivery,
without applying an old account's response to a new account or allowing old data
to update new consumers. Add the queued-completion/account-signal interleaving as
a regression, including switching away and returning to the same session.

## F3: OkHttp can automatically repeat a 503 despite connection retries being off

Priority: P1. Status: confirmed, awaiting owner fix.

`ProfileLocationTransport.newClient` at lines 41-49 sets
`retryOnConnectionFailure(false)`, but OkHttp `3.12.0`'s HTTP follow-up handling
still repeats a 503 response carrying `Retry-After: 0`. This bypasses the
supplemental repository's result classification and no-retry policy.

Executed a loopback-only fake server with these two responses:

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 0
Content-Length: 0
Connection: close

HTTP/1.1 200 OK
Content-Length: 0
Connection: close
```

One `newClient().newCall(request).execute()` produced:

```text
retry_disabled=true
visible_status=200
physical_request_count=2
```

The test used project-compatible locally cached OkHttp `3.12.0` and Okio
`1.15.0`. The HTTP loopback URL was solely the client's offline fixture; it did
not relax or exercise the production session origin allowlist.

Expected: one physical request and the original rejection response reaches the
operation's classifier. Disable the client's automatic 503 follow-up for this
operation explicitly, while preserving 429 `Retry-After` handling. Keep a local
fake-server request-count test against the actual configured client; testing only
the `retryOnConnectionFailure()` flag or a fake `Call.Factory` misses this path.

## Additional observations

- `rejectedSessions` currently survives `invalidateSession`, so a stop already
  processed before a notification is preserved; F2 is specifically about a
  response whose completion was queued before that notification.
- The existing immutable session validates exact HTTPS origin, credentials and
  headers before construction. The transport does not use legacy logging/global
  Cookie interceptors, and its body read enforces a 256 KiB decompressed bound.
- `AuthorLocationService.captureSession` reads the settled user list/index,
  avoiding the known stale `getActiveUser()` removal behavior. Page/UI integration
  and the full repository/cache acceptance matrix remain the parent's review
  responsibility.
