# U2 independent implementation review

Reviewed 2026-10-01 in `/home/toph/nga-just-works-upstream-adoption`, branch
`feat/upstream-adoption`, against U1 commit `4ccd7564`. Applied the local
`trellis-check` skill directly; no nested implement/check dispatch.

## Findings (fixed)

None. No product changes were needed.

## Findings (not fixed)

No U2 code or specification defects found. Reviewed the full PRD, design,
implementation plan, check.jsonl references (large specs read in chunks),
research fixture matrix, new reader-media contract and frontend index link.

- Candidate matching requires literal `./`, excludes brackets/CR/LF, and uses
  URI validation plus raw path segments with retained trailing empties.
  Unicode whitespace/control and mixed-case encoded dot segments are rejected.
- Invalid matches do not advance the append cursor; following valid matches
  retain all intervening text. Nested valid spans, adjacent tags, dollars,
  quotes, ampersands, query/fragment and source preservation have executing
  decoder fixtures. HTML escaping covers the complete URL; replacement never
  interprets URL contents as regex replacement syntax.
- Existing typed video/audio and absolute Flash rules remain unchanged.
  The new rule uses the full resolved page prefix. Existing image-host tests
  cover manual/default/img9/custom precedence; the new consumer tests cover
  page isolation, fallback and HTTP preservation. Attachment lists remain links.
- Normal/App parsers default to ArticleConvertFactory.renderRow, which passes
  source-neutral text and page HtmlData to HtmlConvertFactory/ForumDecoder.
  HtmlCommentBuilder and HtmlSignatureBuilder call that same decoder with
  the same HtmlData. These two Android-dependent builder paths were traced in
  source; they were not represented as newly executed browser/device tests.
- Only the two planned product files changed: ForumBasicDecoder.java and
  shared style.css. No transport, editor, settings, template or WebView API
  changes are required. The new English media contract matches implementation.

## Verification

- Lint: **pass**. Independently parsed all 13 Android module lint XML reports:
  zero Error/Fatal. Warnings remain diagnostic.
- TypeCheck: **pass** via Java/Kotlin Debug compilation and app assembleDebug.
- Tests: **pass**. Independently parsed every module's Debug test XML:
  749 tests, zero failures/errors/skips. Focused classes contain media 6,
  page-prefix 4, normal-parser 7 and App-parser 9 tests.
- Reused implementer's unchanged valid full gate; no duplicate Gradle run.
  `/tmp/u2-full-gate.log` ends `BUILD SUCCESSFUL`, 646 tasks executed.
  Durable totals: [quality-summary.json](evidence/quality-summary.json).
- `git diff --check`: pass.
- Browser layout: **pass**, Chromium 147.0.7727.55. Independently checked
  [results.json](evidence/layout/results.json) and the harness: 3 widths
  (320/360/768) × 2 stylesheet themes × 12 cases = 72 passing cases.
  Local generated VP8 media metadata supplies intrinsic landscape/portrait/
  small dimensions. All cases fit their containing block, preserve ratio,
  avoid upscaling and occupy their own line. Every small video remains
  120 × 90; collapsed cases hide before expansion; no page overflow.
  Audio stays 300 × 54 and inline across the six pages. No external requests.
- Copied common/light/dark stylesheet bytes exactly match production assets.
  Reviewed dark-320 screenshot and dimension records. The browser fixture
  loads actual theme CSS but does not reproduce the native WebView background;
  the dark screenshot therefore has a white body outside dark quote blocks.
  Metadata/layout evidence is not decoded playback or Android visual evidence.
- Android device/ADB/playback/fullscreen: **not run per project policy**.
  No NGA/external media, signing or publication operations were performed.

No blocking findings. U2 is ready for the parent integration sequence; retain
these media/prefix regressions through U3 and the final U4 gate.
