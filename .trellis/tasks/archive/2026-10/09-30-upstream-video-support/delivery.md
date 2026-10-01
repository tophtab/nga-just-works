# U2 implementation and validation — 2026-10-01

Implemented on `feat/upstream-adoption`, following U1 commit `4ccd7564`.

## Delivered behavior

- `ForumBasicDecoder` now recognizes case-insensitive untyped `[flash]./path[/flash]` after the existing typed rules. URI validation rejects whitespace/control characters, illegal URI characters/percent escapes, empty segments and literal/encoded dot segments. Valid Unicode, query/fragment and extensionless paths remain unchanged.
- Independent span assembly preserves adjacent/malformed text; the complete page prefix plus relative path is HTML-attribute escaped with literal-safe StringBuilder insertion. Existing typed video/audio and absolute Flash behavior is unchanged.
- Shared video CSS adds only `width:auto`, `max-width:100%`, `height:auto`, `display:block`.
- Production decoder fixtures and normal/compat parser seams cover HTML output, editable/raw source preservation and page-local prefixes. Attachment builder tests confirm audio/video remain links. Existing shared comment/signature decoder call sites were checked; their Android-dependent builders were not executed in a JVM substitute.
- Main session added the reader-media spec; it matches this implementation.

## Validation

Focused command:

```sh
./gradlew :lib_core:testDebugUnitTest --tests 'gov.anzong.androidnga.core.decode.ForumBasicDecoderMediaTest' :nga_phone_base_3.0:testDebugUnitTest --tests 'sp.phone.mvp.model.convert.PageAttachmentPrefixFlowTest' --tests 'sp.phone.mvp.model.thread.NormalArticleParserTest' --tests 'sp.phone.mvp.model.thread.AppArticleParserTest' --console=plain
```

Passed 26 methods (6 core media, 4 prefix, 7 normal, 9 compat), with many table-driven malformed/valid input fixtures. First attempt found a test expectation using double-quoted attachment hrefs; existing output is single-quoted. The assertion was corrected without changing product attachment behavior.

Full gate:

```sh
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest lintDebug --continue --rerun-tasks --console=plain
```

Passed in 1m 11s, all 646 tasks executed. All 749 JVM tests passed, zero failures/errors/skips. All 13 module lint XMLs exist and contain zero Error/Fatal. Warning counts are retained in [quality-summary.json](evidence/quality-summary.json). Raw logs: [focused.log](evidence/focused.log), [debug-gate.log](evidence/debug-gate.log). `git diff --check` passed.

## Offline browser layout

Chrome `147.0.7727.55` used the byte-identical production stylesheets, whose SHA-256 values are saved in [css-sha256.json](evidence/layout/css-sha256.json). [index.html](evidence/layout/index.html) generates synthetic local VP8 WebM via canvas/MediaRecorder; the generated [landscape](evidence/layout/landscape.webm), [portrait](evidence/layout/portrait.webm), and [small](evidence/layout/small.webm) samples are also saved. No external samples or NGA requests were used.

[check-layout.js](evidence/layout/check-layout.js) blocks every external request and measures 3 video shapes × 4 containers × 3 widths × 2 stylesheet themes = 72 cases. [results.json](evidence/layout/results.json) records all dimensions: all fit their content box, preserve intrinsic ratio, avoid upscaling, and occupy a separate line. All six viewport/theme combinations have no body overflow and correctly hide then expand collapsed content. At 320px, normal landscape is 304×171, nested-quote landscape 260×146.25, portrait approximately 304×540.44. At 768px, large samples retain 640×360 and 360×640. Small samples remain exactly 120×90 in every case. Audio remains 300×54 inline.

[check-dom-and-save.js](evidence/layout/check-dom-and-save.js) additionally verifies detached DOM parsing retains the apostrophe, `$1`, ampersand and fragment in a single src attribute and compares audio dimensions with/without the video rule. [dom-results.json](evidence/layout/dom-results.json) passed.

Six full-page screenshots are named `light-{320,360,768}.png` and `dark-{320,360,768}.png` under evidence/layout. These test stylesheet themes; the harness does not reproduce the native WebView background. Samples reached loaded metadata (intrinsic sizes recorded); screenshots may show media controls/spinners. This establishes browser geometry, not Android playback, fullscreen, codec availability or remote media reachability. The harness initially had a JavaScript quote error and a stylesheet load wait; both harness issues were corrected before the successful recorded run. The dedicated browser/server are closed; transient CLI artifacts moved to `/tmp/u2-playwright-runtime`.

To reproduce, serve evidence/layout at `127.0.0.1:18762`, open a dedicated `playwright-cli -s=u2-media open --browser=chrome` session and run the two saved scripts from the repository root. The first generates screenshots/results; the second saves the generated samples and DOM/audio checks.

Device/ADB/instrumentation: **not run per project policy**. No signed APK, real NGA request, publication or commit was performed by this implementer. U3 must rerun media/prefix fixtures after its migration; U4/final integration retain the full gate.
