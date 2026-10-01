# Account-dependent failure on a different topic

Date: 2026-09-30. This continues R7; no new Trellis task was created.

## User evidence and correction

The user confirmed the following in the same App: account A cannot read a
post normally, account B can read it natively, and switching immediately back
to A still fails. The user then clarified that this is a DIFFERENT post from
47440211. Its URL has not yet been supplied. 47440211 now works under the
original account. The user subsequently confirmed that this other post has
also recovered under account A, before its URL or response could be captured.
The user confirmed that the existing `.temp/cookies.txt` belongs to account A.
This is an account-mapping confirmation, not a new two-account capture.

The assistant initially associated the account comparison with 47440211;
that attribution was incorrect and has been removed from its research record.
The [47440211 truncation evidence](47440211-truncated-response.md) remains valid
for that earlier sample, but is not evidence of this new post's failure cause.

The user also reports a general pattern of repeated failures during a period,
followed by recovery. Neither that timing pattern nor similar UI fallback
proves that all affected posts share one failure mechanism.

## Established and unknown

- Established by the user: account/session correlation for the other post's
  reading behavior; same-App A-B-A rules against a simple reset-only or
  coincidental one-way temporal-recovery explanation.
- Unknown: new post identity, actual HTTP status, response completeness,
  parser/render failure boundary, and both accounts' actual request source.
- No paired response capture exists. Do not label the new case as JSON
  truncation, a particular cache failure, a permission denial or rate limit.
- Confirmed by the user: the already authorized local Cookie file corresponds
  to account A. No credential values were sent in chat or additionally read
  during this clarification. Account B credentials remain unavailable.

## Source inspection relevant to interpreting account changes

Scoped request construction keeps query/origin/UA fixed and uses an explicit
account Cookie. Account fingerprint changes also reset reader generation,
source and handoff. The legacy compatibility-disabled chain can make one
existing next-account Cookie attempt after ServerException; the displayed
account alone is not proof of every outgoing request's credentials. No such
account rotation was exercised or expanded in this investigation.

Account/session-specific response generation, cache partitioning or routing
are possible explanations to examine after obtaining the new response, not
established causes. The current user observation does not authorize automatic
identity rotation as an application recovery mechanism.

## Next evidence

Both reported posts are now recovered; there is no currently supplied failing
URL to probe. The existing Cookie-to-account-A mapping is confirmed and must
not be asked again. Preserve the captured 47440211 failure for offline analysis.
If the user later supplies a link during recurrence, reproduce account A's
ordinary request and inspect status, completeness and parser/render stages.
A bounded comparison with another explicitly supplied/selected account can
then distinguish upstream response differences when appropriate. Do not
silently inspect other saved accounts or request Cookie contents in chat.

No live requests or product changes were made for this clarification. This
record is independent of the recovered 47440211 sample. Do not repeatedly
probe recovered topics, poll for recurrence, or require further account
switching merely to fill the missing response pair. Recovery does not invalidate
the separately saved 47440211 incomplete-response evidence.
