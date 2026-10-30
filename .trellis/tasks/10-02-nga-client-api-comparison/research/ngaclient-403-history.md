# Research: NgaClient 403 reports and read reliability

- Query: Has ForeverZero/NgaClient encountered and fixed thread-read HTTP 403 failures, and does its request mechanism justify higher reliability?
- Scope: mixed; public GitHub issue/PR/release/history APIs and pinned local source; no NGA requests.
- Date: 2026-10-02

## Findings

### Conclusion

**No public evidence of a reported or resolved 403 incident was found in this repository. This is absence of evidence, not evidence that the client avoids 403.** The entire public issue/PR inventory is empty, Discussions are disabled, and the latest source commit is from 2023-05-23. Its token-signed App API request is an alternative wire shape worth comparing, but does not establish superior current availability. This is a small Java library, not a maintained full reader application with browser recovery or token lifecycle handling.

### Exhaustive search coverage

Read-only `gh api` calls against `ForeverZero/NgaClient` on the research date:

| Surface | Exact scope | Result |
| --- | --- | --- |
| [Repository metadata](https://api.github.com/repos/ForeverZero/NgaClient) | Repository object | `archived=false`, `has_discussions=false`, `open_issues_count=0`, `pushed_at=2023-05-23T07:45:35Z`; default branch `main`. |
| [All issues](https://api.github.com/repos/ForeverZero/NgaClient/issues?state=all&per_page=100) | `state=all`, paginated; includes PR records if any | Exact response `[]`; zero open or closed issues, not merely zero keyword hits. |
| [All PRs](https://api.github.com/repos/ForeverZero/NgaClient/pulls?state=all&per_page=100) | `state=all`, paginated | Zero open, closed or merged PRs. |
| Discussions | Repository metadata | Disabled; no Discussions content to inspect. |
| [Commits](https://api.github.com/repos/ForeverZero/NgaClient/commits?per_page=100) | Entire default-branch history, paginated; detailed changed-file lists for all commits and relevant client patches | 14 commits, 2023-04-12 through 2023-05-23. No 403 fix or failure investigation. |
| [Branches](https://api.github.com/repos/ForeverZero/NgaClient/branches?per_page=100) | Enumerated branch inventory | Only `main`, at `c728a152f3ddffe2e816de6c947362844fe07ab6`. |
| [Tags](https://api.github.com/repos/ForeverZero/NgaClient/tags?per_page=100), [releases](https://api.github.com/repos/ForeverZero/NgaClient/releases?per_page=100) | Full inventories | One tag/release `0.0.1`, published 2023-04-17 01:50:31 UTC, empty release body. No fix claim. |

Because there are no issues or PRs, there are no conversations, reporter confirmations, linked fixes, or closure reasons to inspect. No keyword search is needed to establish this repository-level absence. This scope excludes forks, deleted/private reports and third-party discussion sites.

### Relevant changes and their actual meaning

| Date (UTC) | Reference | Evidence and interpretation |
| --- | --- | --- |
| 2023-04-12 | [Initial implementation](https://github.com/ForeverZero/NgaClient/commit/8d4109d434589d2c9305438e8eb405ccf9e2388a) | Added transport, signing utility, signature exception and initial APIs. No incident or recovery claim. |
| 2023-04-14 | [Reply DTO / audit exception](https://github.com/ForeverZero/NgaClient/commit/bce055fd869cbc9f1e263ebfeb817df391037d46) | Added `AuditFailedException` classification in `checkResponse`; this throws an exception for an application response code. It is not an HTTP 403 repair. |
| 2023-04-17 | [postList implementation](https://github.com/ForeverZero/NgaClient/commit/d803e75a620403ded42ced88dc4a689cbf930c3d) | Added the signed thread/reply read and a direct live-service test invocation. No assertions demonstrating 403 recovery. The request implementation remains unchanged at the latest SHA. |
| 2023-05-23 | [Latest commit](https://github.com/ForeverZero/NgaClient/commit/c728a152f3ddffe2e816de6c947362844fe07ab6) | Added topic DTO fields. The other two May commits likewise only touch topic fields/POM, not read transport or authentication. |

### Files found and source-observed mechanism

Local source root: `/tmp/nga-api-comparison-WnvQGx/NgaClient`; pinned SHA `c728a152f3ddffe2e816de6c947362844fe07ab6`. Paths below are relative to that checkout.

- [`readme.md:1`](https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/readme.md#L1): API origin and limitation. Explicitly says `根据安卓APK反编译和网络抓包获得接口，不保证可靠性`; lines 6–7 say login is unimplemented and callers must obtain credentials themselves.
- [`src/main/java/org/zhd/ngaclient/NgaClient.java:119`](https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/NgaClient.java#L119): thread read POST to `/app_api.php?__lib=post&__act=list`; form includes `access_token`, `access_uid`, `app_id`, `t`, `tid`, `sign`, `page` (base form at lines 134–139). Signature includes app ID, UID/token, topic ID, timestamp, secret. This differs from ordinary `read.php` and cannot be presumed identical to another client's App compatibility request.
- [`src/main/java/org/zhd/ngaclient/common/NgaUrl.java:11`](https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/common/NgaUrl.java#L11): fixed `https://ngabbs.com/` origin; no application domain failover.
- [`src/main/java/org/zhd/ngaclient/NgaClient.java:142`](https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/main/java/org/zhd/ngaclient/NgaClient.java#L142): HTTP POST transport. Handler at lines 128–130 reads/parses body without explicit HTTP status classification. `checkResponse` at 160–169 interprets JSON application codes, including signature/audit exceptions, but offers no repair, refreshed token, browser handoff or alternate reader. No explicit application-level 403 recovery exists. Apache client defaults were not audited, so this does not claim zero library-level retry/redirect behavior.
- [`src/test/java/TestNgaClient.java:43`](https://github.com/ForeverZero/NgaClient/blob/c728a152f3ddffe2e816de6c947362844fe07ab6/src/test/java/TestNgaClient.java#L43): direct `postList` call; no availability comparison, failure fixture or demonstrated sustained success.

### Implications for the user's intermittent failures

**Supported inference:** App-specific routing and token authentication could produce different results than a browser/Cookie `read.php` request, so the mechanism is a candidate comparison path.

**Unsupported inference:** An app signature prevents HTTP 403, a lack of issues means users never see failures, an exception handler fixes rejection, or this 2023 library is more stable than the current reader. None follows from the available evidence. The project provides no population of reports or paired requests with which to estimate relative failure rate. It also requires a token acquisition/renewal solution that it does not implement.

Distinguish HTTP 403 from application `code=5` / `code=53` or parser failures. They occur at different boundaries; catching the latter is not proof of handling the former. A successful browser or compatibility attempt on the same thread would establish that another request path can work under those particular conditions, but the user's current recollection does not isolate the deciding difference.

### Related specs

Read `.trellis/workflow.md`, `.trellis/spec/backend/index.md`, and `.trellis/spec/backend/nga-platform-access-rules.md`. They require separating source-observed behavior from live availability and preserve distinct HTTP, session, protocol and access-control outcomes. Prior exhaustive source inventory: `research/nga-client.md` in this task. No product implementation or spec update was performed.

## Caveats / Not Found

- No reporter-confirmed fix, maintainer claim of 403 resolution, current successful request or availability benchmark exists in the inspected public surfaces.
- A repository's last push is not proof the protocol stopped working; it means no newer maintenance evidence was found here.
- No NGA requests, credential acquisition, tests invoking live endpoints, GitHub writes or git operations were performed. All external calls were read-only GitHub API queries.
