# LNGA live request check

2026-10-02, 13:48:33–13:48:43 UTC (21:48:33–21:48:43 Asia/Shanghai).
User explicitly authorized testing thread 47660355 page1 with .temp/cookies.txt.
Three reads, five-second gaps, no retry/redirect/media requests. Same credential
snapshot; raw responses stayed in memory and were not persisted.

- LNGA signed POST ngabbs.com: HTTP200, UTF-8 JSON envelope code521/template read,
  60,145 bytes. Actual upstream HTML extractor returned matching tid/page1,
  19 rows, 19 nonempty bodies, main row present, numeric PIDs.
- Ordinary GET ngabbs.com: HTTP200, JavaScript/GBK, 16,163 bytes.
- Ordinary GET bbs.nga.cn: HTTP200, JavaScript/GBK, 16,163 bytes.
- Ordinary responses did not pass the probe's limited standard-JSON check.
  The production Fastjson/legacy repair/parser was NOT run on these responses;
  this is not evidence that the application would fail to read them.
- Ordinary browser UA was source-compatible but not read from the installed
  application's preference. Desktop Python HTTP transport is not Android OkHttp.
- The user reported that the thread had recovered during preparation. Therefore
  the failure window was missed; this experiment proves candidate request and
  extraction worked at this instant, not improved recovery or durability.
- No full Android rendering, attachments, quote/edit or source fidelity test was
  performed on this live page. Do not equate 19 nonempty bodies with complete
  rendering parity.

Offline upstream tests were built in an isolated temporary copy using npm ci
--ignore-scripts and npm run build --ignore-scripts, then the app-html-parser and
app-html-mode-coverage suites ran: 25 tests, 7 suites, 25 passed, zero failed or
skipped. These limited fixtures do not establish live reliability.

The user's clarified objective is intermittent default-reader failure, not
necessarily HTTP403. The toast at ArticleListModel.java:122 means conversion
returned null and no specific business error was recognized. Future reproduction
should prioritize immediate minimal capture during the failure window using the
actual application parser, rather than further broad research or repeated probes
once the thread has recovered.
