# Reader Video Rendering Contract

## 1. Scope / Trigger

Apply when changing shared article media decoding or video CSS. Normal and
compatibility readers, comments, and signatures reuse the existing HtmlData /
ForumDecoder chain; source text and editable content remain separate outputs.

## 2. Signatures

```java
ForumBasicDecoder.decode(String content, HtmlData htmlData)
```

The added wire form is case-insensitive `[flash]./relative/path[/flash]`.
The common stylesheet is `lib_core/src/main/assets/html/style.css`.

## 3. Contracts

- Recognize the literal `./` prefix and a nonempty relative path. A candidate
  cannot span brackets or CR/LF. Validate with `java.net.URI`; reject scheme,
  authority, whitespace/control characters, illegal percent escapes, empty path
  segments, and dot/dot-dot segments (including mixed-case encoded dots).
  Preserve Unicode, valid query/fragment, percent spelling, and extensionless
  paths. Do not trim, repair, or infer missing prefixes.
- Convert valid spans independently; leave malformed/unknown spans readable.
  A valid inner tag may convert while malformed outer text remains. This is
  not a general nested BBCode parser.
- Use the existing complete `attachmentsPrefix` plus `relative.substring(1)`.
  Do not use URI.resolve or reduce the prefix to a host. Respect page-scoped
  auto selection, manual host preferences, and the HTTP-only img9 option as
  specified in [platform access rules](../backend/nga-platform-access-rules.md).
- Escape the complete URL as an HTML attribute, including ampersands and both
  quote types. Replacement is literal-safe: `$1` in a URL is not a regex group.
  Output has `controls` and no autoplay, event handlers, or new player.
- Keep preexisting typed video/audio and absolute Flash rules unchanged. The
  new rule runs after them. Attachment-list audio/video remain links; source
  and editor text retain the original tags.
- Common video CSS is `width:auto; max-width:100%; height:auto; display:block`.
  Preserve intrinsic ratio, keep small video at its natural width, allow tall
  portrait video, and use a separate line. Do not impose 16:9, fixed height,
  cropping, or full-width stretching. Audio/theme rules remain unchanged.

## 4. Validation & Error Matrix

| Input/action | Required result |
| --- | --- |
| Valid relative tag | One controls video with complete resolved prefix |
| Adjacent valid and invalid tags | Convert valid spans without losing surrounding text |
| Empty/absolute/traversal/unknown typed candidate | New rule does not guess a URL |
| Single quote, ampersand or dollar in valid URL | Attribute DOM value preserves literal URL |
| Different page prefixes or manual host | Each page uses its own authoritative prefix |
| Wide/portrait/small video | Container width cap, intrinsic ratio, no forced upscaling |
| Missing metadata, codec failure or remote outage | No claim of playback from layout/HTML tests |

## 5. Good/Base/Bad Cases

- Good: `[flash]./mon_example/video?x=1&amp;y=2[/flash]` renders with the
  page's full attachment prefix and a single layer of attribute escaping.
- Base: an unknown or malformed tag stays text; other valid tags still render.
- Bad: use an unescaped regex dot, greedy cross-tag matching, guessed CDN,
  URI.resolve that drops a prefix path, or an interpolated regex replacement.

## 6. Tests Required

Run the production decoder with nonempty HtmlData, including adjacent/nested
malformed spans, Unicode, query/fragment, encoded dots, quotes/dollars,
typed/audio/absolute baselines and source preservation. Keep normal/compat
parser and complete attachment-prefix flow regressions. Offline browser tests
load actual CSS and local media at 320/360/768 CSS px in normal, quote, table,
and expanded collapsed content, checking dimensions, ratios, line breaks,
small-video width, audio, and both themes. Block external requests. Browser
layout is not Android device playback/fullscreen or CDN availability evidence.

## 7. Wrong vs Correct

Wrong: match `.(.*?)` as a relative prefix and substitute the captured URL into
an unescaped regex replacement or raw single-quoted HTML attribute.

Correct: require literal `./`, validate each candidate path, append untouched
text between valid spans, and HTML-escape the entire complete URL before adding
it with StringBuilder.
