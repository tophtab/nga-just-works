# Validation Record

## Implemented Boundary

`NgaProfilePageSource.parsePage` now excludes outer rows with nonblank string
`denied`/`error` markers before ordinary author validation. Reply collection also
checks the nested `__P` object. The original author, required-content, whole-page
rejection, first-page, and accepted-item-limit behavior remains in place.

The production change is confined to this parser. The profile loader already
supports partial availability, so only its regression coverage changed.

## Evidence

- The four authorized live reads recorded in `author-mismatch.md` established
  the mixed available/unavailable response shape and the original failure path.
  No additional live requests were needed for implementation.
- A bounded offline inspection of the four synthetic snapshots confirmed that
  every remaining available row has the requested synthetic author and every
  remaining available reply has a string body. Counts agree with the recorded
  live observations. This inspects response structure; it does not execute the
  Java parser or an Android app.
- Seven parser regression methods cover mixed topics, outer/nested reply
  denial and prompt exclusion, unmarked foreign/malformed rows, blank and
  non-string markers, all-unavailable pages, the 20 accepted-item limit after
  filtering, and whole-page rejection. These use synthetic values only.
- One loader regression covers each kind being empty while the other kind
  remains available. Existing coverage checks the both-empty error.
- The implementer and an independent Trellis check agent reviewed the full
  source/spec diff. Neither found an unresolved defect; `git diff --check`
  passed. The reviewer confirmed that the regressions distinguish the intended
  filtering, identity, prompt-exclusion, and partial-availability behavior.

## Execution Limits

The user explicitly declined local builds. No Gradle command, Java compilation,
JVM test, lint, APK assembly, device operation, or model request was run for this
repair. Added regressions have not been executed. Previous task test results do
not validate this change.

The existing feature-branch push workflow assembles a preview APK remotely; it
does not run these JVM regressions. Remote build status is checked after the
authorized commit, task archive, journal record, and push.

## Prevention

The original parser assumed that every listed record was available activity;
its author check therefore conflated an unavailable placeholder with another
user's post. A reply placeholder could pass the author check and leak denial
text into summary input. Prior fixtures missed this mixed-availability shape.

The owning AI contract now specifies exact item-marker types, validation order,
accepted-item counting, and the separate whole-page error boundary. Regression
fixtures exercise both readable activity and unavailable items in the same page.
No new abstraction or broader platform fallback is required.
