# Approved repair: normalize NGA topic-detail string controls

## Status

Diagnosis is complete and the failure is reproduced offline and against the
authorized native response. The maintainer approved this repair and requested
merging it into `main` in the subsequent message on 2026-09-12.

## Behavior gap and owning boundary

The new original-topic reader routes NGA's JSON-like `read.php` representation
through a shared strict JSON preflight. A raw TAB within a quoted field makes
an otherwise usable detail fail before the original is selected. The fix
belongs in `NgaTopicBodyParser`'s local response normalization, immediately
before `NgaProfilePageSource.parseData`, where native representation repairs
already live. It does not belong in the model decoder, UI, or error handler.

## Proposed correction

- Convert literal TAB, LF, and CR string characters into equivalent
  JSON escapes while scanning quoted regions of a topic-detail response.
  Preserve the decoded text rather than deleting whitespace or whole fields.
- Preserve existing escaped quotes, backslashes, JSON escape sequences,
  numeric-body repairs, and quoted text that resembles envelope markers.
  Distinguish an escaped tab from the literal backslash-plus-`t` text. A lone
  backslash immediately followed by a raw TAB/LF/CR remains malformed. Do not
  reinterpret an unknown malformed escape as a successful response or extend
  the repair to other raw U+0000–U+001F characters without evidence.
- Keep expansion within the existing response/decoder limits. Malformed
  envelopes, invalid charset, missing original, and genuine identity conflicts
  retain their existing failure paths.
- Keep `SafeJsonParser` strict: model/service JSON with raw control characters
  must still fail its existing regressions.
- Retain exact requested-topic/viewed-author/explicit-floor checks, the
  sequential read budget, cancellation, and prompt/body length limits.

## Expected change boundary

- `NgaTopicBodyParser.java`: local representation normalization only.
- `NgaProfilePageSourceTest.java`: raw-wire regression cases injected after
  fixture serialization; paired escaped/control cases and collection coverage.
- Task-owned documentation and `.trellis/spec/backend/ai-summary-contract.md`:
  record the source-backed compatibility rule and regression requirements
  after the implementation is verified.

No shared decoder relaxation, silent title-only fallback, retry, pagination,
provider changes, prompt redesign, or unrelated reader refactor.

## Main integration

`main@7cb9e50b` already contains the prior AI feature. The existing feature
worktree was fast-forwarded to that base before repair so the complete quality
gate includes main's current code and its corrected library test dependencies.
After the work and bookkeeping commits, merge the feature branch into `main`
with a fast-forward when possible. Recheck main for concurrent changes before
integration; if its code advances, resolve the integration and rerun affected
checks. Do not overwrite unrelated work or rewrite published history.

## Repair acceptance

1. The captured structural class (literal U+0009 inside a string) decodes
   successfully and yields only the verified original.
2. Corresponding LF/CR compatibility and escaped forms preserve expected text;
   unknown malformed escapes still fail.
3. Raw TAB/LF/CR in ignored metadata cannot abort an otherwise valid original,
   and that metadata remains absent from model input. Other raw controls and
   malformed escape sequences remain errors.
4. The loader proceeds through its existing reply and composition stages for
   a valid synthetic list/detail/reply sequence.
5. Existing strict model-parser, identity, size, cancellation, and full relevant
   regression checks pass. A fake-server run is sufficient; there is no standing
   authorization for another live run after this two-request diagnosis.

## Rollback and compatibility

The change should remain confined to the local normalizer and its tests, so it
can be reviewed or reverted without changing persisted settings or model wire
configuration. No storage migration or rollout feature flag is needed.
