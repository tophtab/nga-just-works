# Research: Broader GitHub evidence for NGA HTTP 403 fixes

- Query: Has anyone solved NGA thread-reading HTTP 403, and does that support LNGA signed HTML?
- Scope: external, with local transport/spec context
- Date: 2026-10-02

## Findings

There are concrete fixes for particular 403 causes. They do not establish POST or HTML as inherently more reliable, nor identify this user's cause.

### 1. Login restored reading: direct user confirmation (2019)

[ymback issue 131](https://github.com/ymback/NGA-CLIENT-VER-OPEN-SOURCE/issues/131), created 2019-08-29, closed 2020-03-07, explicitly reports HTTP 403 Forbidden on Android, while web access works. The Australian reporter said a China VPN did not help.

- [Original reporter, 2019-09-02](https://github.com/ymback/NGA-CLIENT-VER-OPEN-SOURCE/issues/131#issuecomment-527004415): login became possible, and normal use resumed after login.
- [Second user, 2019-11-24](https://github.com/ymback/NGA-CLIENT-VER-OPEN-SOURCE/issues/131#issuecomment-557905830): previously showed logged-in 403, resetting the app did not solve it; logging in and then accessing the board restored board and thread opening.
- No linked code fix or proven root cause. Historical domain/certificate observations in the second comment are observations, not current advice or a demonstrated cause.

### 2. RSSHub authenticated cookies: merged thread-reading partial fix

[PR 5747](https://github.com/DIYgod/RSSHub/pull/5747), opened 2020-09-28, merged 2020-10-03, explicitly titled `(semi-fix) login to NGA to prevent 403 error`. Body describes rejection when opening thread content with HTTP 403. [Merge b538f75](https://github.com/DIYgod/RSSHub/commit/b538f75ef60fba88daa9528f1acd573435e7e0f2).

Code diff `lib/routes/nga/post.js:11-22`: retained **GET HTML read.php**, but added configured Passport UID/CID cookies in place of a guest cookie when both are available. Therefore a real merged 403 fix that did not switch GET to POST or switch away from HTML. No explicit independent user success confirmation in PR comments.

[Issue 17303](https://github.com/DIYgod/RSSHub/issues/17303), created 2024-10-25, closed 2026-09-26, includes exact read.php HTTP403 log.

- [Closing explanation, 2026-09-26](https://github.com/DIYgod/RSSHub/issues/17303#issuecomment-5845989651): response said guests cannot access directly; instructs configuring valid Passport cookies. Closed as site restriction, not a route bug. No later reporter confirmation.
- [Earlier comment, 2024-11-12](https://github.com/DIYgod/RSSHub/issues/17303#issuecomment-2470579376): another participant reported curl/browser worked and a second retry worked in RSSHub. Their HTTP/2 explanation is explicitly speculation, with no linked merged fix. Do not recommend blind automatic 403 retry from this anecdote.

### 3. MNGA XML to JSON: confirmed fixed, but NOT thread-reading failure

[Issue 377](https://github.com/BugenZhao/MNGA/issues/377), 2026-03-20, closed 2026-03-21. Explicit 403 Forbidden after login, browser works. [PR 376](https://github.com/BugenZhao/MNGA/pull/376), merged 2026-03-20, says XML output on app_api.php and nuke.php returns 403. [Merge 15fd60f](https://github.com/BugenZhao/MNGA/commit/15fd60f4803fa56b8c10113021b5ee917ade12c4).

- [Reporter confirmation](https://github.com/BugenZhao/MNGA/issues/377#issuecomment-4098988234): TestFlight fixed it.
- [Release confirmation](https://github.com/BugenZhao/MNGA/issues/377#issuecomment-4102389123): App Store 2.2.2 / TestFlight 2.3 (122).
- Crucial [contemporaneous comment](https://github.com/BugenZhao/MNGA/issues/377#issuecomment-4098142694): threads still refreshed and browsed normally despite the issue.
- Diff changes forum/profile/message/favorite operations from XML `fetch_package` to `fetch_json_value`; `logic/service/src/fetch.rs:501` uses `__output=8`. This is strong evidence that output contract can matter, not proof for LNGA signed HTML or fixing read.php.

### 4. HTML to GET JSON: merged bot-parser fix, author-reported success only

[Anyuluo996/nonebot-plugin-parser PR21](https://github.com/Anyuluo996/nonebot-plugin-parser/pull/21), opened and merged 2026-07-16; [merge e09d69c](https://github.com/Anyuluo996/nonebot-plugin-parser/commit/e09d69c48a96327d303d1c0e30270e2a6d795911).

Author reports old HTML retrieval returning HTTP403 and two sample posts working after switching to **GET read.php lite JSON** with mobile App request identity. The patch also changes host, HTTP client/TLS behavior, headers, response parsing and proxy handling. Therefore cannot isolate output format or UA as the cause. Source `src/nonebot_plugin_parser/parsers/nga.py` adds `_fetch_thread_json`, which calls `session.get`; no POST and no LNGA-style signing.

PR comments provide no independent success confirmation. The implementation renders the main post and first four replies, not full-featured native-client parity. Its own comment expects the alternate HTTP client fallback may still receive 403. This is a bounded author-reported fix, not universal stability. Most importantly it contradicts assuming HTML is always the safer format.

### 5. Proposed fallbacks are not completed fixes

[27149/MNGA issue 1](https://github.com/27149/MNGA/issues/1) and [issue 4](https://github.com/27149/MNGA/issues/4) propose native-rendered HTML fallback for API403/412/451. Both remain open. Their acceptance criteria/checklists are not evidence of implementation or successful resolution.

## Files found / local context

- `nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/thread/ArticleByteClient.kt:40`: current scoped byte transport; ordinary GET URL and authenticated request construction. Existing logged-in transport means a crawler's missing-cookie fix is not automatically applicable.
- `.trellis/tasks/10-02-nga-client-api-comparison/research/current-project.md`: prior source inventory.
- Related specs: `.trellis/spec/backend/ordinary-read-wire-contract.md`; `.trellis/spec/backend/nga-platform-access-rules.md` (evidence labels, response classification, no implicit identity rotation or automated challenge bypass).

## Query coverage

Used GitHub REST search via `gh api`, including issues and PRs, then issue comments and PR file diffs. Queries: `NGA 403 in:title,body`, date-limited pre-2026 variant, `"NGA" "403" in:title`, `"nga" "Forbidden" in:title,body`, `"read.php" "403" in:body`, `"nga.178.com" "403" in:body`, `"ngabbs" "403" in:body`, MNGA scoped 403, NgaLite scoped title/body/comments403 (zero results). Broad NGA searches have substantial unrelated acronym/language noise and were not exhaustive. Repository discovery found `fhyxz001/NgaLite`; Nger NGA did not identify an authoritative client. Three guessed historical repository paths rejected search with422 and are not treated as audited. Previously researched Justwen90/95/154 and initial three repos excluded from this assignment.

## General search-engine coverage

The coordinating agent also attempted general web searches for NGA403, NGA
reading403 solutions, read.php403 and site-scoped discussions through Google,
Bing, DuckDuckGo, Baidu and Yahoo. Google required JavaScript, DuckDuckGo/Baidu
returned verification pages, Yahoo failed, and Bing repeatedly returned broad
irrelevant results despite exact terms. No snippets from those results were
accepted as factual evidence. The substantiated findings here are public GitHub
issues, full comments and merged diffs, not an exhaustive survey of the web.

## Implication for research priority

Signed HTML remains a candidate, but new evidence argues against prioritizing
it solely because it returns HTML or uses POST. First separate current request
credential delivery, actual403 status and response-format failure. Investigate
request-contract differences before undertaking a full HTML parser migration;
GET/JSON has also fixed specific reported cases. Do not import all of PR21
transport changes or claim a single parameter reproduces its author-reported gain.

## Caveats / Not Found

No live NGA requests, account tests, code edits, or posted comments. Dates/states above are GitHub observations on research date. No controlled same-account/thread/network comparison. No evidence of a universal 403 fix, no proof POST is more reliable, and no independent proof that LNGA HTML cures this user's issue. Distinguish exact transport status from parser failure and a JSON business error whose numeric code also happens to be403.
