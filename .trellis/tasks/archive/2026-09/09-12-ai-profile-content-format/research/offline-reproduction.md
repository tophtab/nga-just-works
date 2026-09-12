# Offline differential reproduction

The parent compiled a small synthetic Java harness against the existing
production classes with the resolved Fastjson `1.1.71.android`, OkHttp `4.12.0`,
Okio `3.6.0`, and Kotlin stdlib `2.0.21` jars. No production or test sources were
changed. No network or device was used for these cases.

The fixture has a synthetic matching topic TID (7300), original TID (7300),
viewed author (42), and explicit floor zero. Only one wire characteristic
changes per case. The source-derived minimal Java payload is recorded in
`topic-body-compatibility.md`.

| Case | Current `NgaTopicBodyParser` | Shared decoder | Legacy Fastjson decoder |
| --- | --- | --- | --- |
| Ordinary JSON | Success, 23 body characters | Success | Success |
| Escaped newline in unused metadata | Success, 23 body characters | Success | Success |
| Literal newline in unused metadata | Exact reported format error | Error | Success |
| Literal tab in unused metadata | Exact reported format error | Error | Success |
| Literal newline in original body | Exact reported format error | Error | Success |
| Newline outside strings, between tokens | Success, 23 body characters | Success | Success |
| Foreign author control | Expected identity error | Success | Success |

The legacy comparison invokes its JSON decoder, not the Android renderer or
the whole `ArticleConvertFactory`. Do not imply an APK/UI execution or claim
that every historical malformed NGA response works in the legacy path.

Local harness: `.temp/ai-profile-content-format/FormatRepro.java`.
Local output: `.temp/ai-profile-content-format/offline-results.jsonl`.

The 204-test green baseline and these failing synthetic cases are compatible:
the existing detail fixtures end in `JSONObject.toJSONString()`, which escapes
TAB/newline characters. Their output never exercised this native NGA wire form.
