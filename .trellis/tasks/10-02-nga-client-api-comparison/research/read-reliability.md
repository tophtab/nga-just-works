# Read reliability: issue history, fixes, and relevance to HTTP 403

Research date: 2026-10-02. This follow-up prioritizes the user's actual concern:
occasional thread failures requiring compatibility/browser settings. The user
has not yet distinguished HTTP 403 from other read failures or compared modes
under identical conditions. No live NGA request or application change occurred.

Follow-up: the user approved focused LNGA research; see the
[completed feasibility study](lnga-signed-html-feasibility.md) for concrete
credential reuse, parser gaps and prototype boundaries.

Further evidence: [broader public403 fix research](global-403-github.md) finds
case-specific fixes that retain GET/HTML or switch HTML to GET/JSON. This limits
the earlier LNGA-first recommendation: neither HTML nor POST is inherently more
reliable; establish request/session/format differences before a broad migration.

## Decision supported by the evidence

LNGA's signed HTML reader is the strongest candidate among the three requested
repositories for a focused reliability experiment. It has a concrete migration
history addressing web JSON degradation and preserves native rendering. None of
the three has a public, reporter-confirmed demonstration that it permanently
eliminates thread HTTP 403. Do not equate few issues with low failure rate.

| Repository | Public evidence inspected | Related change | What it establishes |
| --- | --- | --- | --- |
| compose-nga | All 5 issues, 9 comments, 0 PRs; Discussions disabled | API signature correction 4bb247a on 2026-08-11 | A real correctness fix; no confirmed 403 fix. The tid-only concatenated signature may be unchanged, so it is not proof of better ordinary thread reads |
| lnga_harmony | All 9 issues and 4 PRs/comments; Discussions disabled; default-branch and reader history | Signed output11 JSON + output17 HTML migration, then HTML-only read path on 2026-08-22 | Deliberate alternative to incomplete/error-prone web JSON. Source/docs motivation, not measured HTTP403 elimination |
| NgaClient | 0 issues, 0 PRs; Discussions disabled; all 14 default-branch commits | postList added April 2023; latest commit May 2023 | Working API intent only; no reliability/fix feedback, README explicitly declines reliability guarantee |

Detailed evidence and query limitations: [compose history](compose-403-history.md),
[LNGA history](harmony-403-history.md), [NgaClient history](ngaclient-403-history.md).
Counts describe public records at research time, not user populations or incident rates.

## Closest history in this project's original upstream

These older reports establish that similar symptoms have existed, not that the
user's present failure has the same cause. They also separate confirmed reports
from HTTP-status evidence:

- [Justwen #90](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/90),
  opened 2022-03-18, remains open. Reports describe random failures, normal
  official-app/browser reads, and truncated JSON. A participant attributed it
  to API restrictions after inspecting traffic; that attribution is a hypothesis,
  not an independently established server policy. The maintainer confirmed the
  client requests JSON rather than the full webpage, and introduced browser
  fallback. Later comments report both improvement and continuing failure.
- [Justwen #95](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/95),
  opened 2022-04-09, explicitly describes frequent random failures and the prompt
  to use the built-in browser. The maintainer's
  [2022-05-16 closing comment](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/95#issuecomment-1127501828)
  says there is no fundamental solution and lists browser/account switching.
  **Closed does not mean fixed at the root cause.** Neither #90 nor #95 proves
  every incident was HTTP403; malformed/truncated bodies are a different failure.
- [Justwen #154](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/154),
  2023-03-12, explicitly reports HTTP403 for two boards despite an official UA.
  The reporter [confirmed recovery after clearing all app data](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/154#issuecomment-1465183046).
  This establishes recovery for that case, not its precise cause; clearing data
  changes several variables and is not a recommended diagnostic first step.
- [Justwen #119](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/119),
  2022-10-17, explicitly reports board403; a commenter says the board was
  temporarily closed. No independently verified service-status record found.
- [Justwen #161](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/issues/161),
  June–July 2023, includes increased thread403 reports alongside posting errors.
  Closed after uploading the release; no commenter-confirmed 403 resolution.

Coverage here was targeted GitHub searches for 403, compatibility and reading,
followed by full issue/comments reads for #90/#95/#119/#154/#161. It was not an
exhaustive audit of every Justwen issue or historical release.

## Why LNGA is a meaningful candidate

1. [7d84cd3](https://github.com/apap6628114/lnga_harmony/commit/7d84cd3009359aaba7dc2e3fd62dddc99f8fdf96)
   (2026-08-22 07:39 UTC) changes GET read.php output8 into signed POST read.php:
   initially output11 JSON plus output17 HTML fallback. Incomplete JSON and
   unexpected HTML responses are explicitly handled.
2. [166791c](https://github.com/apap6628114/lnga_harmony/commit/166791c6849fe4ffb35a7a32d4390c5a81cd250e)
   (08:10 UTC) removes the JSON route/debug switch and uses signed output17 HTML
   as the reading path. HTML is parsed into native thread models; it does not
   require replacing the screen with a browser.
3. [Its design documentation](https://github.com/apap6628114/lnga_harmony/blob/df6cfcc2e4cfc2efa95269aac398707e9d4d3e81/docs/FORUM_API_DESIGN.md)
   explicitly motivates signed channels as mitigating web JSON throttling. The
   topic-list section is partly historical; this is an upstream assertion.
4. Current requests combine fixed ngabbs.com, POST forms, account-derived
   access_uid/access_token, signature/time/app identity, official-version headers,
   and output17 HTML. Merely changing output8 to output17 is not an equivalent
   migration. Current project's Cookie-only App post/list is also not identical.
5. Current LNGA still parses error pages for permissions/locked/pending threads,
   and tests account for a body described as HTTP403/unauthenticated. Its later
   polling reduction also acknowledges frequency-related risk. No immunity is
   demonstrated. JSON wrapper code521 is not HTTP521 or HTTP403.

## Additional linked evidence: an improvement users actually confirmed

Justwen #90 links [MNGA #104](https://github.com/BugenZhao/MNGA/issues/104),
an XML read-failure report. In September–October 2025, several users confirmed
that its new release substantially reduced failures; the latest relevant comment
still reports occasional XML errors. Related merged changes combine request
identity, output-format alternatives, retry/client changes and a maintainer
proxy. No individual factor was isolated, and no HTTP403 elimination was shown.
An earlier App API experiment (PR118) was closed unmerged with rich-text
compatibility gaps. See [the detailed linked history](mnga-linked-read-history.md).
This is stronger evidence of possible improvement than absent issue reports,
but does not justify copying a proxy or automatic access-error retries.

## What this can and cannot address

| Failure observed | Mechanism that may help | Evidence limit |
| --- | --- | --- |
| HTTP200 with broken/truncated JSON, while HTML works | Obtain supported HTML representation and reconstruct native rows | Closest match to historical reports; requires robust parser and content completeness checks |
| Rejection specific to request identity/route | Correct signed request contract with expected headers and session | Plausible from differing request shapes; must compare the same authorized account/thread/time/network |
| Expired/missing login or insufficient forum permission | Correct session handling or legitimate account access | HTML/App endpoints do not establish additional permission |
| IP/rate restrictions or temporary site problems | Request pacing, cancellation and reduced duplicate/prefetch traffic where appropriate | No endpoint guarantees availability; don't infer cause merely from status403 |
| Client parsing/rendering defect | Parser fix for the actual returned format | Suppressing HTTP checks or swallowing errors does not improve successful reads |

The current reader intentionally permits automatic App fallback only for FORMAT;
HTTP403 is ACCESS and stops that chain (`ArticleFailure.kt:14`,
`ArticleReaderSession.kt:106`). Researching a new primary reader differs from
blindly retrying denied requests. Preserve account, origin, content identity,
pagination and explicit failure semantics in any later design.

## Suggested next decision

Prioritize a small, optional LNGA-style native HTML reader experiment over a
wholesale client/API migration. First establish the user's actual failure class
with minimal non-sensitive diagnostics: source, HTTP status, host, response
format/recognized error category and timing; never store credentials or raw
private content. Compare the existing modes and candidate under the same account
and network, recording content completeness and page identity as well as success.
Such live validation and product implementation remain separately scoped; neither
was performed or authorized by this research request. Until comparison exists,
the conclusion is promising mechanism, unproven fix for this user's HTTP403.
