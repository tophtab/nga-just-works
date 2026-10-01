# Ordinary Reader Wire Boundary

## 1. Scope / Trigger

Apply when changing ordinary-read field decoding or its legacy Java adapter.
The shared decoder lives in core, protocol DTOs in core_data, and source/query/
account/cache/errors/rendering policy stays in the app. The compatibility App
reader retains its separate existing parser.

## 2. Signatures

```kotlin
ReadThreadWireDecoder.decode(data: JSONObject, mode: ReadDecodeMode, invalidBeanRows: Set<String> = emptySet()): ReadThreadDecodeResult
ReadDecodeMode.SCOPED
ReadDecodeMode.LEGACY
ReadField<T>(kind: ReadValueKind, value: T?, valid: Boolean)
```

The decoder accepts the already-parsed `data` object, not raw network text.
Wire families are ReadThreadWire, ReadPostWire, ReadUserWire, ReadTopicWire and
ReadAttachmentWire. Result failures distinguish ROW_MAP_OR_COUNT, INDEXED_ROW,
and UNREADABLE_VALUE for the app's existing error/null branches.

## 3. Contracts

- No raw reparsing, wrapper/token repair, network, account, cache-path, host
  preference or rendering side effects in core. Do not return app ThreadRowInfo
  or opaque JSONObject fields disguised as wire data.
- ReadField keeps missing, null, original JSON kind, decoded value and conversion
  validity distinct. A missing/null/structured body must not become invented
  valid source text. `isSourceScalar` alone does not prove field presence or
  editability; the app retains its existing source-completeness rules.
  Keep scoped text projection separate: the old strict reader removes only
  invalid canonical source keys before bean mapping, so a valid smart alias
  can still supply text. Literal `lou` presence remains independent of aliases.
  Preserve the bean's matching-input-key assignment order, not a fixed
  preferred-key list. Every matching value is converted before a later alias
  overwrites it; an earlier malformed numeric value must not become valid
  merely because the final alias converts successfully.
- Current-row traversal uses `__R__ROWS`; total `__ROWS` is separate metadata.
  Preserve the existing primitive conversion/unboxing behavior where it decides
  an outer-null result. Do not use tolerant zero defaults to erase failures.
- A scoped count getter conversion exception reports UNREADABLE_VALUE, as it
  reaches the facade's existing outer catch. A successfully decoded null,
  negative/out-of-range count or invalid row map reports ROW_MAP_OR_COUNT.
  A missing/non-object
  declared indexed row reports INDEXED_ROW. Preserve conversion/validation
  order so nested comments or bad primitive/attachment values do not change
  which existing failure path wins. Legacy mode keeps its existing bad-row
  skipping and outer failure semantics.
- A non-object `__T` fails its original cast outside the local optional-topic
  catch and reports UNREADABLE_VALUE. Conversion of an object to the topic bean
  may fail independently and yield optional null. Keep the raw topic author ID
  separate for owner association even when optional topic conversion fails.
  Do not manufacture a valid tid-zero topic description.
- Decode users once per UID per invocation. Do not cache across calls/accounts.
  Retain typed user/group shape information and original row fallback fields;
  absent users/groups must not erase available row metadata or reject a page.
  Preserve the original getJSONObject conversion for groups, matching group
  entries and buffs (including encoded JSON-object strings and null results for
  arrays/scalars); this is different from a strict cast. Retain original field
  kind even when the accessor decodes a string object.
- Preserve complete attachment fields and ordered recursive comments, client
  details, normal-only WP input, raw `17` hot replies, anonymous/owner facts,
  topic navigation/reply metadata and current fallback precedence. Wire values
  do not contain authoritative rendered HTML, image lists or blacklist output.
  Preserve invalid attachment conversion as typed evidence until the original
  consumption phase so a preceding nested-comment CONTENT error retains
  priority. Do not leak an opaque JSONObject through the typed boundary merely
  to reproduce an invalid raw map seen only by a no-op test renderer.
- Keep legacy display-bean fallbacks in the app's ReadThreadBeanFallbacks
  projection, using the actual bean field metadata and a finite field inventory.
  These include bean `comments` (distinct from protocol `comment`), hot-reply
  fallback lists, blacklist and rendered/image presentation fallback fields.
  They are not authoritative core output. Apply user/protocol/render overrides
  in their original order; nonempty canonical `vote` and literal `17` retain
  their explicit precedence over bean aliases/fallbacks.
  Pass only invalid row paths to core when an app-bean conversion failure must
  precede a later nested-comment shape error. `ReadPostWire.sourcePath` links
  the app projection to each row; no app object, raw JSON or callback crosses
  this boundary. Projection traversal must preserve numeric-count coercion and
  remain bounded by present row slots before core validates the count.
- The app adapter owns existing public Java models, source-specific failures,
  blacklist/author presentation, attachments-prefix resolution, WP preprocessing
  and renderer calls. Populate attachments/comments before rendering. Preserve
  the facade's public signatures and the separate App parser path.
  ReadThreadLegacyMapper maps typed wire values back into existing Java models;
  it does not rewrite NormalArticleParser or AppArticleParser policies.
- Successful facade results and render/lookup order must match frozen baseline
  snapshots. Decode-first rejection can avoid discarded render/lookup work on
  invalid pages; document exact cases and verify there are no delivered UI or
  network effects. Skipping a rejected lookup may defer lazy filter-store
  initialization; it must not introduce a new write or action.

## 4. Validation & Error Matrix

| Shape | Required result |
| --- | --- |
| Scoped invalid row map or decoded null/negative/out-of-range count | Existing FORMAT mapping through ROW_MAP_OR_COUNT |
| Count getter conversion exception | Existing outer-null via UNREADABLE_VALUE |
| Scoped missing/non-object indexed row | Existing CONTENT mapping through INDEXED_ROW |
| Other historically unreadable casts/conversions | Existing outer-null branch via UNREADABLE_VALUE |
| Legacy bad row slot | Existing skip behavior, no newly guessed row |
| Non-object topic vs failed object-to-bean conversion | Outer-null for original cast failure; optional null only for bean conversion |
| Missing user/group or row fallback metadata | Preserve row fallback and original precedence |
| Structured/absent/scalar body | Preserve distinct source validity for the app |
| Two parses with same UID | Independent user state; no cross-call cache |

## 5. Good/Base/Bad Cases

- Good: decode once into typed protocol data, then map into the existing Java
  models with the same source/error/render order.
- Base: an optional topic cannot convert; display data and independent author
  association retain the original applicable behavior.
- Bad: use total rows as a loop bound, flatten every problem into FORMAT,
  stringify structured content into editable source, or add a core fallback.

## 6. Tests Required

Exercise complete fields, nullable/absent/wrong kinds, strict and legacy modes,
validation order, nested attachments/comments, owner/user/group fallback,
per-invocation association, and separate total/current counts through the real
shared decoder. During facade wiring compare existing ordinary/legacy/App
reader suites, source capabilities, precise errors, identity/paging/cache,
blacklist/render ordering and U1/U2 regressions. Core tests alone do not prove
app adapter behavior. Keep full Debug/lint gates at stable integration points.

## 7. Wrong vs Correct

Wrong: reparse/repair raw text in core, return a simplified replacement display
model, and map all decoder failures to a generic formatting error.

Correct: decode the app's parsed data once, retain typed field evidence and
specific shape results, and let the existing app facade/mapper preserve Java
interfaces and its source-specific error/rendering policy.
