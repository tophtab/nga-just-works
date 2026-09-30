# Inline media edit design — review scope

## Source and display
Existing new-compose ImageSpan overlays the BBCode source; edit/draft reload inserts a String. Reconstruct display spans from recognized source tokens without replacing that source. Keep source-content input separate from rendered HTML, per the frontend contract.

## Parsing and resource ownership
Use a pure parser for category/name emoticons and supported image tokens, producing source ranges and descriptors. Share R5 category/name mapping. Resolve relative attachment paths through existing NgaImageHost rules without changing stored text.
Use editor-view lifecycle ownership, bounded asynchronous bitmap/thumbnail loading, caching, and token-generation checks. Preserve cursor selection and user changes when a load completes. No upload or extra post-content fetch is needed.

## Editing and recovery
Deleting/selecting a rendered media preview targets the complete original marker: a selection overlapping a preview expands to its source boundaries for replacement/deletion. Unsupported or failed source fallback remains editable text; do not silently delete or rewrite it. Pasting/typing and restored drafts must be decorated without duplicate insertions or recursive text-change loops.
Image failures and unsupported tokens preserve editable original source. Save/send continue using plain source serialization. Do not persist bitmap objects in drafts.

## Scope and trade-offs
Restore image/emoticon presentation; other BBCode stays text. Animated playback, raw-source toggles, and richer toolbar redesign are not assumed. These extensions are outside the current review scope.

## Dependency
R5 supplies the corrected shared mapping. No dependency on link intake or IP queries.
