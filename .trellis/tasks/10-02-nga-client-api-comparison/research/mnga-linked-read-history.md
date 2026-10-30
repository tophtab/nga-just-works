# Research: MNGA's linked read-failure history

- Query: Follow the MNGA issue linked from Justwen #90; identify actual mitigation, merged fixes and user confirmation without confusing XML corruption with HTTP403.
- Scope: external; MNGA #104 body/all 34 comments/timeline, directly linked #114/#118, and targeted 2025 reader commits surrounding the reported improvement.
- Date: 2026-10-02.

## Findings

### A real reported improvement, but not elimination

[MNGA #104](https://github.com/BugenZhao/MNGA/issues/104), “常出现xml解析错误,” opened 2022-03-11, closed 2025-10-15. Original report concerns certain threads failing XML parsing, temporarily alleviated by Wi-Fi/mobile switching. No HTTP403 is established by the issue text. All comments were read.

- March 2022: maintainer suggested 178 host + desktop identity. Users initially confirmed improvement, including the reporter's 24-hour success report. By March 17–24 the same reporters said failures still occurred or the workaround no longer helped: [initial report](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-1066051537), [same reporter's regression](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-1076993658). Therefore a short successful run did not establish a durable fix.
- [March 24 maintainer comment](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-1077668419) explicitly describes official API blocking as a **guess**, and links Justwen's in-app browser workaround commit `a39d6eba112d61acf976c185e20915f04a2cb629`.
- [May 18 maintainer comment](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-1130064419) links merged PR #114, planning automatic browser fallback in 1.1. [May 19 user](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-1131260161) says the new beta appears to open the browser automatically. Subsequent June/August 2022 and April 2023 comments still report parsing problems. Fallback improved accessibility rather than repairing the underlying reader response.
- [2025-09-20 maintainer](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-3314605003): TestFlight v1.2.0 (iOS26) “显著改善了封禁问题”. [User confirmation](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-3314611188): “确实大有改进”.
- [2025-10-15 maintainer](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-3406285506): released as App Store 2.0; issue closed then. [October16 user](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-3408849052) says XML failures much less frequent. [October28 user](https://github.com/BugenZhao/MNGA/issues/104#issuecomment-3458237435): “还是会偶尔遇到XML解析错误，不过已经很少了”. This is user-confirmed **reduction**, not complete resolution or measured HTTP403 performance.

### Linked and contemporaneous implementation evidence

| Change | Actual status and behavior |
| --- | --- |
| [PR #114](https://github.com/BugenZhao/MNGA/pull/114) | Merged 2022-05-18. `app/Shared/Views/TopicDetailsView.swift` diff at added `xmlParseErrorMain` (around line427) exposes Open in Browser when XML error and no response; `onError` (around line617) opens in-app Safari after 0.75 seconds if preference enabled. Explicit browser fallback, not HTML-to-native reconstruction. |
| [PR #118](https://github.com/BugenZhao/MNGA/pull/118) “resolve banning by changing topic request api” | **Closed unmerged** 2022-07-06 (`merged_at=null`). `logic/service/src/topic.rs`, added `get_topic_details_json` around line491, signs app_api post/list with app_id1010, uid/token, tid and timestamp. Author says used for a while, but then notes rich-text differences and missing fields; [admission of incomplete parsing](https://github.com/BugenZhao/MNGA/pull/118#issuecomment-1159956909). This is not evidence that released MNGA adopted a complete App reader or solved 403. |
| [PR #154](https://github.com/BugenZhao/MNGA/pull/154), commit `b3d77213312b7d1f609e15365cb0c052a6c64c31` | September2025 history identifies native TLS and trying multiple formats. Follow-up #156 gives exact final combined implementation below. |
| [PR #156](https://github.com/BugenZhao/MNGA/pull/156), merged 2025-09-13, commit `713553c03adf744842c59a0c4687a81d377b2f5f` | PR body: prefer WindowsPhone UA, remove random UA because it seemed worse, add proxy URL to try different IP. `logic/service/src/fetch.rs:25` defaults read.php identity to WindowsPhone unless custom; `:177` combines normal/proxy origin with output alternatives, preserves first attempt, shuffles later attempts, delays100–300ms and rebuilds client on failed parse. XML alternatives at `:265` are lite=xml and output10. JSON-to-XML conversion is **commented out**, not functioning final code. `logic/service/src/constants.rs:6` adds maintainer-owned `https://nga.bugenzhao.com`. No HTML bridge shown. |
| [PR #176](https://github.com/BugenZhao/MNGA/pull/176), commit `99acf0105b178a86c186410c6643050c061ee606` | Later September2025 `fetch.rs:223` limits retry condition to parse errors or HTTP status errors; other business errors stop. Nonempty error responses are parsed to expose server messages. This refines #156 and is not itself proof which component produced the user-reported gains. |

The September2025 changes are contemporaneous source evidence for the version reported improved in #104. The issue does not isolate their individual effects. Do not claim that changing only UA, only XML output, or only the IP replicates the reported improvement.

### Implication for our project

This linked case is stronger than “no issues found”: it has multi-year failures, a merged mitigation and several users confirming fewer failures. Its successful claim concerns XML/read availability, not confirmed HTTP403 elimination. It supports treating alternate request mechanisms as experiments, with browser fallback as a separate recovery UX. It does not justify importing a third-party proxy or automatic access-error retries into our existing reader.

Related local specs: `.trellis/spec/backend/thread-detail-compat-contract.md` requires HTTP403 ACCESS stop and distinguishes response format failures; `.trellis/spec/backend/ordinary-read-wire-contract.md` separates wire decoding from network policy. Our query/identity/source contracts differ from MNGA's XML reader.

## Caveats / Not Found

- No controlled success-rate measurements, actual status/body pair, or independent reproduction. Screenshots/video were not analyzed for HTTP status; XML label alone is not HTTP evidence.
- No proof that the historical failure was specifically JSON truncation, nor that the 2025 release guarantees no errors. Latest relevant comment explicitly says failures remain rare.
- No HTML-to-native solution established in this narrow linked thread; the App API experiment remained unmerged and format-incomplete. No NGA requests, proxy requests, live-service tests, or repository mutations were made.
