# Source-preserving editor media previews

## 1. Scope / Trigger

Use for TopicPostFragment/Presenter, InlineMediaEditText/Decorator/Source and
InlineMediaLoadQueue. This is display/editing behavior; POST.SUBMIT and
ATTACHMENT.UPLOAD protocols are unchanged. See the shared emoticon contract in
[component guidelines](./component-guidelines.md#shared-emoticon-rendering-map).

## 2. Signatures

- `InlineMediaSource.parse(String source, String attachmentsPrefix)` returns
  tokens with UTF-16 start/end, exact source, resource and image flag.
- `InlineMediaSource.expand(int start, int end, List<int[]> displayedRanges)`
  expands selection/replacement to complete displayed tokens.
- `InlineMediaSource.Revision.accepts(long expected, Token token, CharSequence source)`
  validates source revision, view lifetime and exact range contents.
- `InlineMediaDecorator.suspend()/resume()/close()` own preview lifetime.
- `InlineMediaDecorator.registerLocalImage(String source, Uri uri)` reuses the
  selected local file after an explicitly requested upload completes.

## 3. Contracts

The Editable retains original BBCode; ImageSpan overlays never replace it.
Recognize supported `[s:category:name]` through EmoticonUtils.resolveAssetPath,
and supported `[img]...[/img]` through NgaImageHost attachment prefix and legacy
host normalization. Unknown tokens stay text. Do not put formatted HTML into the
editor or persist bitmaps in drafts.

Use source restoration priority: retained view draft, saved fragment state,
explicit draft argument, initial post source. An empty draft is a valid draft.
Initialize once in Fragment; Presenter must not append the initial body again.
Preserve title, anonymous state (including legacy `anoay` read compatibility),
selection and plain source across view recreation. Saving/sending serializes
plain text; merely opening or decorating media cannot call upload.

InlineMediaEditText expands selections and Editable replacements only across
successfully displayed ImageSpans. Deletion/replacement removes the complete
marker, retaining surrounding text. Unknown, loading and failed tokens remain
ordinary editable text. Inserted media uses the same source/decorating path.

One view owns Glide targets and callbacks. At most two loads are active; bitmap
bounds are 384 px for images and 96 px for emoticons. Honor
PhoneConfiguration.isImageLoadEnabled for image loads, use existing Glide cache,
and attach no account credentials. Local uploaded previews may reuse the source
URI without uploading again. Pause retires targets; resume rebuilds; destroy
closes the revision and clears callbacks. Each character edit invalidates pending
callbacks immediately, before the posted rebuild. Repeated identical markers
have distinct offset/ticket identity. Glide callback completion posts the next
pump instead of synchronously starting or clearing a Glide request.

## 4. Validation & Error Matrix

| Event | Required behavior |
| --- | --- |
| Open edit / restore draft | Same original source, one initialization |
| Successful image/emoticon load | Span only; source/selection unchanged |
| Unknown, disabled or failed image | Source remains editable |
| Selection or deletion overlaps displayed token | Expand to full token |
| Text changed / view paused / destroyed | Reject stale delivery |
| Repeated identical markers | Decorate their own ranges; no cross-token result |
| Retired load completes after queue reset | Cannot release newer ticket slot |

## 5. Good / Base / Bad Cases

Good: source stays `[s:ac:赞同]` while its preview uses ac/ac42.png.
Base: an unsupported image URL remains literal text.
Bad: substitute rendered HTML, reinsert original content over an edited draft,
or send an attachment upload simply because an existing image is visible.

## 6. Tests Required

Execute mixed/repeated/unknown token parsing, source roundtrip, reversed and
partial token selection/deletion, source revision/close rejection, two-slot queue,
failure progress and retired-ticket identity tests. Review actual Fragment
restoration and save/send/upload wiring, not only helper behavior. Build/test/lint
the app plus the repository quality gate. IME/device interactions require explicit
device authorization; JVM/source tests do not establish device runtime results.

## 7. Wrong vs Correct

Wrong: replace the source range with a bitmap placeholder, or call setText from
an asynchronous bitmap callback.
Correct: validate view/revision/range, then set an ImageSpan over unchanged source.
