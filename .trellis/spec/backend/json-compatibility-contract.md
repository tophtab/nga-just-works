# JSON Runtime and Compatibility Contract

## 1. Scope / Trigger

Apply when editing JSON dependencies, a network parser, reflected bean, or local
JSON storage. The U3 migration separates runtime-library compatibility from the
later ordinary-reader wire/mapper refactor. Keep public Java display models and
existing source/account/paging/error behavior.

## 2. Signatures

- Runtime library: `com.alibaba.fastjson2:fastjson2:2.0.59.android8`, exposed
  through `lib_base_common`; use operation-local reader/writer options.
- `TopicConvertFactory.decodeTopicList` receives already-unwrapped text and
  converts a generic tree to the existing TopicListBean.
- `SafeJsonParser` and `ProfileWebUserParser` retain their bounded pure parser
  entrypoints and script-extraction boundaries.
- Reflected application beans implement the existing `JavaBean` marker and
  inherit production consumer keep rules. `JSONField` is the JSON2 annotation.

## 3. Contracts

- No fastjson1 APK runtime fallback. A fixed old-library test oracle is allowed;
  distinguish test and annotation-processor dependencies from app runtime.
- Use explicit names or local `SupportSmartMatch` where legacy bean aliases
  require it. Preserve old storage keys; an alias fix must not silently change
  writer names, defaults, or unknown-field behavior. ThreadPageInfo's legacy
  aliases are declared on both getter and setter: getter-only alternateNames
  lost authorId in the JSON2 probe. Keep canonical writer names and test aliases
  again after shrinking. Never globally enable
  smart matching, FieldBased, AutoType, or class-name loading.
- Topic parsing uses local generic tree parsing with `DisableReferenceDetect`,
  then bean conversion. Keep unused jdata fields in the parsed input and
  support the six archived hex/quote/control fixtures without a removal regex.
  Existing wrapper normalization remains in the operation owner.
- NGA and external-avatar upload decoders retain distinct envelopes. Enable
  `AllowUnQuotedFieldNames` only at evidence-backed compatible entrypoints.
  Keep compression selection and callbacks in their existing owners; JSON
  migration does not add retries or reinterpret other business errors.
- `NOTIFICATION.LIST` uses local `AllowUnQuotedFieldNames` in
  `ForumNotificationFactory.decodeNotificationData`. Without it, JSON2 stores
  bare numeric keys such as `0:` differently from quoted `"0":`, so existing
  string-key lookups lose the envelope or row fields and the legacy catch
  returns an empty list. Preserve bare keys in offline regression inputs;
  `ForumNotificationGoldenTest` checks both list entrypoints, reply-before-message
  order, PID slots `7`/`8`, and read/unread state against the quoted baseline.
- AI/profile parsers locally allow existing unquoted fields while using
  `DisableReferenceDetect` and `DisableSingleQuote`. Preserve depth/size/
  character/script guards, BigDecimal precision and full-document consumption.
  `@type` and `$ref` remain ordinary data, including nested values; no class
  loading or reference substitution. Do not enable `IgnoreCheckClose`.
- Keep operation-specific null/index/coercion, error precedence, cancellation,
  and wrapper behavior. Pure test seams expose existing decode/selection only;
  they do not unify unlike envelopes or move transport/UI side effects.
- Preserve all local paths, preference keys, Room version/columns, and Bundle
  keys. Raw responses and supplied valid topicInfo remain exact strings.
  Arrays retain order, timestamps retain Long semantics, and owned-cache
  pageSize null remains explicit via the appropriate JSONWriter option.
- Do not serialize cache handles, compiled Pattern, derived board parent IDs
  or icon URLs. Cache handles and icon URLs are excluded from deserialization
  too. Retain Annotation/Signature metadata and production JavaBean keep rules,
  including report and avatar response beans. Do not use keep-all application
  rules or fixture-only keeps to hide missing markers.
- The active private-message parser is
  `lib_bu_message/.../module/message/MessageConvertFactory.java`, used by both
  message repositories. The unused app-package copy is retired; do not restore
  it as a fallback or remove the active parser during consumer cleanup.
- Preserve U1 icon observation/order/recovery and U2 source/media/prefix tests.
  JSON changes do not authorize R7 reader recovery or browser-policy changes.

## 4. Validation & Error Matrix

| Boundary | Required observation |
| --- | --- |
| Old stored JSON → new reader | Same fields, null/type/order/owner semantics |
| New writer → old reader / new reader | Compatible existing schema, no data clearing |
| Topic jdata variants | Consumed fields survive; malformed/truncated input not fake success |
| AI/profile special keys | Literal values, no class loading/reference replacement |
| Alias-shaped wire fields | Same IDs/client/topic fields rather than silent zero/null |
| Owned cache wrong owner/layout/version | Existing rejection, no fallback to unrelated data |
| R8 reflected beans | Same required fields/annotations survive actual production keep rules |
| App runtime dependency graphs | JSON2 only; test/kapt old-library use separately labelled |

## 5. Good/Base/Bad Cases

- Good: immutable old-writer snapshots are read by the new code, newly written
  data is decoded by the old oracle, and current round trips preserve semantics.
- Base: Android preference/Activity orchestration is source-reviewed while
  actual codec/store seams execute; reports distinguish both evidence levels.
- Bad: regenerate legacy goldens after switching libraries, turn on global
  permissive flags, clear user data, or claim minified runtime from Debug alone.

## 6. Tests Required

Cover every storage row and network operation in the task's complete consumer
inventory. Reuse reader/source/account/error/cache/AI/profile/board/media suites;
add real operation-local success/error tests for previously Android-bound
parsing. Preserve fixed B0 old-format resources and B1 jdata/alias/upload cases.
Identify any deliberately changed characterization, such as safe treatment of
unused Topic special-key extensions, rather than silently weakening assertions.

Run Debug build/all-module tests and inspect all module lint XMLs for zero
Error/Fatal. Inspect debug/release runtime and relevant processor dependencies.
Run a production-bean R8 classfile reflection fixture with dynamic entry names
and actual keep rules; separately run the app's unsigned minify task after
checking its dry-run graph. Do not bypass signing guards. Inspect current
mapping/merged rules; old artifacts and classfile fixtures do not replace actual
app R8. If app R8 is pending, U3 and the parent remain unaccepted until U4 supplies
that evidence. None of this establishes minified Android device execution.

## 7. Wrong vs Correct

Wrong: replace imports, enable global smart/auto-type parsing, and assume a
successful build proves old storage and reflection compatibility.

Correct: migrate each consumer with its local options, compare immutable old
formats and actual semantic round trips, execute operation errors, and verify
production bean metadata with both focused shrinker and current app R8 evidence.
