# Bounded live observation plan

The maintainer explicitly approved this exact scope in the current conversation
on 2026-09-12: reuse the previously supplied, locally retained NGA session for
the same saved target profile, with at most four read-only requests: one first
topic list and up to three original-topic detail pages. Stop on access limits.

- Origin: `https://bbs.nga.cn`, port 443.
- Operations: `TOPIC.LIST` with the production `buildRequest` path, followed
  by `THREAD.PAGE` using production `buildTopicRequest` for accepted list TIDs.
- Session: one fixed locally retained Cookie; values never enter output or
  task artifacts. Target IDs remain local and do not enter tracked reports.
- Transport: isolated OkHttp 4.12; no Cookie persistence, authenticators,
  redirects, connection retry, or cache. A network-interceptor budget permits
  at most four transmissions and rejects duplicate URLs, including internal
  HTTP follow-ups. Deadlines are 15/30/45 seconds for connect/read/call.
- Stop immediately on non-success HTTP, redirect, HTML/challenge, transport
  failure, or the first reproduced parser failure. No account changes, bypass,
  retry, or extra reply-list request.
- Decode with the production `readBody` method and parse with the production
  list/body parser classes compiled by the passing JVM baseline.
- On a detail failure, test an in-memory control-character-escaped copy with
  the same production parser. This comparison sends no additional traffic and
  is a diagnostic experiment, not a shipped fix.
- Output contains only status, sizes, control-character counts, fixed errors,
  and pass/fail metadata. No response body, original content, reasoning, Cookie,
  key, user ID, or topic ID is persisted by the probe.
- The harness uses a fixed Android-browser User-Agent rather than a UA read
  from the maintainer's phone. It retains the production `X-User-Agent` header.
  This is a production-parser/transport probe, not APK/UI verification.

The local harness is `.temp/ai-profile-content-format/LiveFormatProbe.java`.
Its explicit authorization flag and existing-output guard prevent an accidental
second live run. There are no device operations or model requests.
