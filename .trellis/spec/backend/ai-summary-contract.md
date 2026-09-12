# BYOK AI Settings and Summaries

## 1. Scope / Trigger

Apply this contract to `sp.phone.ai`, the AI settings page, the floor/profile
summary menus, and their shared dialog. The implemented boundary is one
user-configured OpenAI-compatible Chat Completions service, with streamed floor
and profile summaries. There is no hosted project key, system model, local model,
chat page, or pagination.

The approved `07-25-nga-android-advanced` design explicitly keeps the existing
Java/Preference/XML navigation for this slice. Its Java-callable configuration,
transport, and controller boundaries are an interoperability exception to the
general migration plan, not evidence that M1/M2 or a broader UI migration is
complete.

## 2. Signatures

```java
new AiConfig(String endpoint, String apiKey, String model);
new AiConfig(String endpoint, String apiKey, String model, AiProfilePrompt profilePrompt);
String AiConfig.getEndpoint(); // Complete normalized Chat Completions URL.
String AiConfig.getApiKey();   // In-memory use only; never log/serialize the object.
String AiConfig.getModel();
AiProfilePrompt AiConfig.getProfilePrompt();
// The three-argument constructor uses AiProfilePrompt.DEFAULT (FORUM_ROAST).
// AiProfilePrompt.Style: FORUM_ROAST, DETAILED, CUSTOM.
AiProfilePrompt.Style AiProfilePrompt.getStyle();
String AiProfilePrompt.getCustomText();
String AiProfilePrompt.getInstructions();

new AiConfigStore(Context context);
AiConfig AiConfigStore.load() throws AiConfigStore.StorageException; // Nullable.
void AiConfigStore.save(AiConfig config) throws AiConfigStore.StorageException;
void AiConfigStore.clear() throws AiConfigStore.StorageException;

Call AiSummaryClient.summarize(AiConfig config, String prompt, AiSummaryClient.Callback callback);
Call AiSummaryClient.testConnection(AiConfig config, AiSummaryClient.Callback callback);
// AiSummaryClient.Callback: default onProgress(String answer, String reasoning),
// onSuccess(String answer), and onError(AiError error).
Call AiSummaryClient.listModels(String endpoint, String apiKey,
        AiSummaryClient.ModelsCallback callback);
// ModelsCallback: onSuccess(List<String> models) / onError(AiError error).
// Asynchronous transport callbacks run on the transport thread.

JSONObject SafeJsonParser.parseObject(String json);
FloorSummaryInput FloorSummaryInput.fromRow(String title, ThreadRowInfo row);
boolean FloorSummaryInput.hasFloor(); // Frozen ArticleRowPresentation.hasFloor(row).
int FloorSummaryInput.getFloor(); // Display only when hasFloor() is true.
SummaryController.Cancelable ProfileSummaryLoader.load(
        String uid, String userName, SummaryController.Callback callback);
SummaryController.Cancelable ProfileSummaryLoader.load(
        String uid, String userName, AiProfilePrompt profilePrompt,
        SummaryController.Callback callback);
String ProfileSummaryInput.toPrompt(AiProfilePrompt profilePrompt);
// The compatibility overloads use the default forum-roast style.
String ProfileSummaryInput.Entry.getBody();
// MAX_BODY_CHARS is 1200; MAX_REPLY_CHARS/getReply() remain compatibility aliases.
// Internal to the summary package; null means explicitly unavailable:
String NgaTopicBodyParser.parse(String raw, String uid, String tid)
        throws NgaProfilePageSource.PageException;
SummaryController.Cancelable SummaryController.InputSource.load(
        AiConfig config, SummaryController.Callback callback);
// SummaryController.Callback: default onProgress(String answer, String reasoning),
// onSuccess(String text), and onError(String message).
// Progress carries cumulative model-message projections; input sources do not emit it.
void SummaryController.start(String target, SummaryController.InputSource source);
void SummaryController.cancel();
void SettingsAiFragment.open(Context context);
```

`SummaryController` receives `ConfigSource`, `Model`, a UI `Executor`, a
`Supplier<String>` for the current target, and a `Listener`. Its immutable
state exposes `IDLE`, `LOADING`, `SUCCESS`, or `ERROR`, `getAnswer()`,
`getReasoning()`, `getErrorMessage()`, and `getCopyText()`. `getText()` remains
the compatibility projection (error message in ERROR, answer otherwise).
`getCopyText()` returns the complete received answer only after success/error;
loading and answerless states return an empty string.

## 3. Contracts

### Configuration and secret storage

- Normalize HTTP or HTTPS endpoints with OkHttp `HttpUrl`, including LAN hosts
  and custom ports. Reject credentials in the URL, query/fragment components,
  control characters, and other schemes.
  Remove trailing slashes and append `/chat/completions` only when absent.
  Preserve the supplied version/custom prefix; never invent `/v1` for a bare
  host. An endpoint ending in `/v1` and its complete `/v1/chat/completions`
  form resolve identically.
- Endpoint, Key, and model limits are 2,048, 4,096, and 256 characters. Keys
  contain printable non-space ASCII; models and keys must be nonblank.
  Validation errors contain fixed messages, never rejected values or causes.
- Persist the whole configuration in `noBackupFilesDir/ai-config.bin` with
  `AtomicFile`, under a shared store transaction lock. Ordinary preferences,
  settings exports, and backup data contain no plaintext Key.
- New records are `NGAI` + version byte `2`, a random 12-byte nonce, and
  AES-GCM ciphertext with a 128-bit tag. Authenticate the actual record header
  as AAD. The payload uses `DataOutputStream.writeUTF` fields in this order:
  endpoint, Key, model, style ID, custom text. Stable style IDs are
  `forum_roast`, `detailed`, and `custom`; reject unknown IDs and trailing data.
  The v2 record limit is 48 KiB, sufficient for all validated fields with
  worst-case modified UTF encoding of the 8,192-unit custom text.
- Decode existing v1 three-field records with their original 16 KiB limit and
  supply `AiProfilePrompt.DEFAULT` without changing endpoint, Key, or model.
  Reading does not rewrite the file; the next deliberate toolbar Save writes
  v2 atomically. Android Keystore retains the 256-bit AES key under the existing
  alias `sp.phone.ai.config.v1`; encryption obtains its nonce from the provider.
  Do not rotate the alias merely because the record schema changed.
- Loading never creates a missing Keystore key. Corrupt records or lost keys
  fail closed and discard invalid material; there is no cached/plaintext
  fallback. Clear attempts both file deletion and key deletion, even if one
  operation fails. A failed write must not mix old and new configuration fields.
- The Key row is a plain, nonpersistent `Preference`. Its transient password
  editor disables view/parent state saving, autofill, content capture, and IME
  learning. It never pre-fills a saved Key or writes it into a `Bundle`.
  Dismiss clears editor text; a successful save or destroying the view clears
  the pending replacement. A blank Key edit retains the current Key.
- Connection tests use the current draft and do not save it. Edits, saves,
  and page pause cancel the test and invalidate its callback generation.

### Settings interaction and model discovery

- There is one AI configuration. The child settings screen contains API
  address, API Key, model, profile-prompt, and connection-test rows. Do not add instruction,
  current-configuration, Save, or Clear preferences. The top-right toolbar Save
  action reuses `btn_ic_save` and has an accessible Save title; it persists the
  complete draft through the existing store transaction.
- The `查成分提示词` row shows the selected style and opens three choices:
  `论坛锐评风格` (default), `详细分析风格`, and `自定义`. Custom input is
  multiline. Selecting a preset retains the previously entered custom text;
  only the selected style determines the next profile prompt. The editor's
  positive action accepts its local draft, while Cancel, Back, and lifecycle
  dismissal discard it. The toolbar Save persists prompt settings together
  with the service configuration. Empty custom instructions cannot be accepted;
  validation must keep the editor open without replacing the previous draft.
- Keep endpoint copy to a short example. The Key dialog is a direct password
  input; do not add encryption/local-storage explanations or echo a saved Key.
  Retain the secret-handling safeguards above. Load/save errors use fixed concise
  feedback, not a second configuration-status panel.
- Opening the model editor starts `listModels` with the draft endpoint and
  pending Key (falling back to the saved Key). It must work before a model is
  chosen or configuration is saved. Share field validators with `AiConfig`;
  never create a placeholder model solely to issue the request.
- Derive the model URL by replacing the normalized endpoint's final
  `/chat/completions` with `/models`. Preserve custom/version paths, host, and
  port. Send GET with the configured Bearer Key through the isolated transport.
  Decode only the compatible `data[].id` shape through the shared bounded JSON
  decoder. Return validated, trimmed model IDs in provider order with duplicates
  removed, using an immutable list and a maximum of 1,024 response rows.
  A wrong `data` shape or any invalid row/ID fails the whole response; do not
  publish a partial list. An empty `data` array is a successful empty result.
- Offer a scrollable model list plus `自定义`. Empty or failed discovery keeps
  manual entry available. Keep a previous successful list only in the current
  view/session and only while endpoint/Key are unchanged; failed refresh must
  not discard it. Changing endpoint or Key clears this cache. Do not persist the
  model list or make opening the editor save any field.
- Results must not overwrite a model selected or custom text entered while
  discovery was loading. Discovery has a separate Call and generation from
  connection tests. Dismissal, another editor, Save, pause, or view destruction
  cancels it and invalidates callbacks. Only the active resumed dialog may
  consume the matching generation on the main thread.

### Model transport

- Use the dedicated OkHttp 4.12 client. Do not use NGA Retrofit, its Cookie
  provider, encoding converter, interceptors, or body logger. Use
  `CookieJar.NO_COOKIES`, `Authenticator.NONE` for both origin and proxy,
  no cache, and no redirects. Ordinary system proxy routing remains available.
  Its connection specs include `MODERN_TLS` and `CLEARTEXT`; configured HTTP
  services use the same isolated path as HTTPS services.
- Send UTF-8 JSON containing only `model`, one user `messages` entry, `stream`,
  and `max_tokens: 10000`. Summaries use `stream: true` and accept
  `text/event-stream` with ordinary JSON completion compatibility; the short
  connection test uses `stream: false` with the same 10,000-token ceiling.
  Thinking and body generation share this ceiling; input tokens are separate.
  Omit `max_completion_tokens`, `max_output_tokens`, and thinking/reasoning
  overrides. Only the configured API receives `Authorization: Bearer <key>`.
  The user explicitly selected 10,000 after comparing the reference userscript.
  Report exhaustion rather than disabling thinking or silently changing the cap.
- Disable connection retries **and** mark the POST body `isOneShot() == true`.
  Disabling connection retries alone does not stop an OkHttp follow-up for
  `503` with `Retry-After: 0`. Preserve the first HTTP failure, without a second
  billed send. Match MockWebServer to the resolved production OkHttp version.
- Limit prompts to 64 Ki characters, decompressed summary transport to 8 MiB,
  and each answer/reasoning projection to 256 Ki characters. A single SSE event
  or fallback JSON object is bounded at 512 Ki characters by the shared decoder.
  Resource overflow is an explicit failure, never substring clipping or false
  success. The 1,000-character prompt instruction is not a transport/UI limit.
  Summary total/connect/read/write deadlines are 180/15/60/15 seconds. Model
  discovery and the connection test retain their 256 KiB response bound and
  60/15/45/15-second deadlines. Keep the local fake-server timeout seam.
- `AiStreamParser` decodes strict incremental UTF-8 and frames CR/LF/CRLF SSE
  lines, blank delimiters, multiline `data`, comments, and optional event/id
  metadata. Usage-only chunks contribute no message text. Select choice index
  zero; when no indexes are supplied, retain ordinary first-choice compatibility.
  Never substitute a later choice, tool arguments, or usage metadata for text.
- `AiMessageAccumulator` owns one transient message with answer/reasoning
  projections. Read body deltas from `delta.content` and reasoning from
  `delta.reasoning_content` (or the compatible `reasoning` string). The JSON
  fallback uses the corresponding `message` fields. Extract leading `<think>`
  and `<thinking>` sections across chunk boundaries before publishing answer
  progress; unclosed matched sections never become copyable text. Literal tags
  inside already-started prose/code remain text.
- An SSE completion needs a valid first-choice finish event or `[DONE]`.
  `length` means `OUTPUT_EXHAUSTED`, a completed answerless message means
  `EMPTY_RESPONSE`, and EOF or a network break before completion means
  `INTERRUPTED_RESPONSE`. Ordinary JSON responses remain compatible without
  `finish_reason`. Preserve already received text on error and flush the final
  cumulative snapshot before terminal delivery. A call-timeout cancellation
  must not suppress that final snapshot; the controller rejects user-cancelled
  generations. Keep timeout distinct from a generic interrupted stream.
- Process valid decoded event prefixes before reporting a later UTF-8 error.
  A bulk reader must not discard an earlier complete event when malformed bytes
  follow it in the same read. On failure, also screen and release buffered
  ordinary text such as a reply beginning with `{` or `data:`; only recognized
  serialized envelopes remain withheld.
- Recognized serialized Chat Completions/SSE envelopes inside `content` are
  protocol errors, even if nonblank. Hold potential protocol prefixes until
  classified, so raw envelopes cannot flash in progress or enter clipboard text.
  Ordinary prose/code containing braces or `data:` stays text. Never display raw
  protocol or replace an empty answer with reasoning.
- Never log configurations, authorization, prompts, response bodies, or raw
  parser/network exception messages. Fixed `AiError` values cross into the UI;
  HTTP error bodies are not displayed or decoded as model results.
- Shared JSON decoding caps text at 512 Ki characters and nesting at 48.
  It uses an independent parser configuration with special-key detection
  disabled: `@type` and `$ref` remain ordinary data. It is a bounded object
  decoder, not a complete strict-RFC validator; the existing Fastjson grammar
  accepts limited forms such as unquoted field names. Required response field
  types still must match. Do not add another parser merely to reject harmless
  legacy grammar variants.

### Summary inputs and NGA reads

- Freeze the clicked `ThreadRowInfo` into `FloorSummaryInput`. Use only its
  thread title, known floor number, author, and plain-text body (up to 12,000
  characters). Do not traverse `ThreadData`, other floors, signatures, account
  credentials, or additional network content. Preserve quote attribution;
  convert media/emoticons to text placeholders without resolving their URLs.
- Snapshot floor availability with `ArticleRowPresentation.hasFloor(row)`.
  Compatibility rows can omit `lou`, and explicit comments do not expose a
  floor even when their raw `lou` is numeric. For unknown floors, omit the
  prompt's floor line and use the neutral `AI 总结` dialog title. Known floors,
  including floor zero, keep their existing labels. The raw target identity
  stays unchanged; later row/presentation mutations cannot alter the snapshot.
  A row without source text cannot start a floor summary.
- Profile summaries use the viewed `mProfileData.uid`, never the active
  account's UID. `AiSummarySources.profile` defers all session lookup and reads
  until the controller confirms a valid AI configuration.
- Input sources receive the controller's initial configuration snapshot.
  Profile collection carries its immutable prompt selection through the list
  reads, topic-body enrichment, and composition; it does not independently
  reload settings. Before sending, the controller retains its second
  configuration validation and compares the prompt selection and retained
  custom text as well as endpoint,
  model, and Key. A changed configuration rejects the collected input; an
  explicit retry captures the newly saved selection. Floor input and connection
  tests keep their own instructions.
- `NgaProfilePageSource` implements the existing `TOPIC.LIST` wire operation:
  `GET thread.php?authorid=<uid>&page=1&lite=js&noprefix`, adding `searchpost=1`
  for replies. Run the topics operation before replies, each capped at 20
  accepted entries; skipped unavailable records do not consume this allowance.
  Retain valid topic IDs privately and enrich the topic sample with at most one
  `THREAD.PAGE` GET per retained topic:
  `read.php?page=1&__output=8&noprefix&v2&tid=<tid>`. Project only the verified
  original post into the topic entry. Never request later activity pages or
  use topic details to obtain the reply sample, which already has `__P.content`.
- Capture one Cookie/UA snapshot for all list and detail reads. Permit only
  HTTPS port 443 on the explicit NGA host set in the source, with no userinfo,
  query, fragment, custom base path, or redirects. There is no application retry or
  account rotation. Connection retries are disabled; an internal idempotent
  HTTP follow-up can still repeat the same page. There are at most 22
  application-scheduled reads (two lists plus 20 topic details), not a claim
  about the number of underlying network transmissions. Topic enrichment is
  sequential and retains the list order; its single cancel handle covers the
  list and all detail calls, including synchronous callbacks and late results.
- Read a maximum of 512 KiB per NGA response. Honor a valid declared charset;
  default to pinned GBK when absent. A present but unparsable Content-Type,
  invalid charset, or invalid encoded bytes is a protocol error, not a reason
  to silently use GBK. Keep the raw header's presence distinct from
  `ResponseBody.contentType() == null`.
- Normalize the known JS prefix once, then use the shared bounded decoder,
  including for a string-valued `parent`. Require `data.__T`, its row count,
  and the correct author on available records. Replies come from each row's
  `__P.content`, `__P.authorid`, and `__P.postdate`.
  Format dates in the device display timezone. Extract only title, board,
  date, and the selected public body into the prompt, not raw JSON, fetch IDs,
  or private profile fields.
- Topic detail parsing requires matching topic identity, an explicit original
  floor (`lou == 0`), and that original's matching TID and viewed author ID.
  Array position and Java bean defaults are not proof of an original post.
  `NgaTopicBodyParser` owns that projection; its null result means explicitly
  unavailable, distinct from a valid empty body. Entry serialization labels
  either case as an application notice, never as a server-authored claim.
  Other floors and user tables are not source material for the prompt. Keep
  source-backed NGA envelope normalization local; recognize JS/error-fill
  markers only outside quoted text. Do not execute the response or invoke the
  legacy renderer/raw-response logger.
- Native `THREAD.PAGE` strings may contain literal TAB (U+0009), LF (U+000A),
  or CR (U+000D). The authorized 2026-09-12 response contained 53 raw TABs;
  the shared strict JSON preflight rejected it before original-post selection.
  `NgaTopicBodyParser` converts these three raw string characters to equivalent
  JSON escapes before shared decoding. Preserve their decoded text and every
  existing valid escape, including the distinction between an escaped tab and
  literal backslash-plus-`t`. A lone backslash followed by a raw TAB/LF/CR and
  all other raw C0 controls remain format errors. Keep this representation
  adapter local to topic details; do not relax `SafeJsonParser` or model JSON.
  Controls in ignored metadata receive the same normalization, without making
  that metadata source material for the prompt. Count escape expansion toward
  the existing 512-Ki-character normalized-response limit; overflow fails
  explicitly. Identity, floor, and original-content checks still run afterward.
- Both topic and reply bodies retain at most the first 1,200 cleaned UTF-16
  code units, preserving the existing surrogate-boundary handling and 64,000
  source-processing bound. The common prompt describes this prefix limit.
  Keep metadata bounds and the 65,536-code-unit whole-prompt client limit.
  Both built-in styles must fit with maximum retained samples; exceptionally
  large custom instructions plus metadata still receive the explicit existing
  over-limit error, without silent extra body/custom truncation.
- Authorized first-page reads on 2026-09-11 contained unavailable placeholders
  with nonblank string `denied` or `error` fields. Skip an outer row with either
  marker before author/content validation; for replies, also inspect `__P` for
  these markers. An unavailable topic can have a nonmatching author, while an
  unavailable reply can still have a matching author and string content. Neither
  form contributes text to the prompt. Blank/whitespace strings and other value
  types do not establish unavailability; retain ordinary validation for them.
- An all-unavailable page contributes an empty sample. Available entries from
  the other kind still permit a summary; if both samples are empty, retain the
  existing no-visible-content error. Missing/malformed page structures, root
  `error`, `data.__MESSAGE`, challenge responses, and unmarked malformed or
  foreign-author records remain collection errors. Do not generalize item
  filtering into a fallback for arbitrary author mismatches or page rejection.
- An explicitly unavailable original body can retain its visible topic
  metadata with an application-owned unavailable-body notice. Server denial
  text is not authored text. Unmarked missing/ambiguous original posts,
  mismatched IDs, malformed structures, and whole-page errors terminate
  collection rather than silently succeeding with titles alone.

### Profile composition prompt

- `AiProfilePrompt` owns the preset instruction definitions and the selected
  style. `ProfileSummaryInput` appends the bounded public activity once, using
  the selection passed with the request. The no-argument `toPrompt()` uses
  `FORUM_ROAST`, including for existing callers and configurations without a
  stored selection.
- `FORUM_ROAST` requests roughly 200–350 Chinese characters in three short
  sections, `画像`, `标签`, and `一句锐评`: a forum-style portrait, supported
  interest/style tags, and a sharp evidence-grounded punchline. `DETAILED` uses a neutral tone
  and five plain-text sections: `兴趣关注`, `主要观点`, `发言风格`, `成分总结`,
  and `标签`. Both analyze expressed interests, views, and wording through
  their selected style instructions.
- `CUSTOM` replaces the preset's style and output-format instructions with
  the user's exact multiline text. Never append an inactive preset or silently
  trim/truncate the saved text. The common sample framing and the application's
  data boundary still apply. Custom text is retained when a preset is selected,
  but it is not included in a preset request. Accept at most 8,192 UTF-16 code
  units; blank custom instructions and oversized retained text are validation
  errors, not reasons to select a different style silently.
- Include the actual retained topic and reply counts after bounded copying.
  Number entries independently as `[主题1]`, `[主题2]`, and `[回复1]`, `[回复2]`.
  These organize the input, not mandatory citations in the model's answer.
  The fixed paragraph from `每条实质观察须附输入中的[主题N]或[回复N]编号`
  through `不作人格评价。` is removed for every style. Do not reintroduce its
  citation/quotation, contradiction, scoring, attribute, or character rules as
  a hidden common suffix. The forum-roast preset also no longer demands
  numbered short-quotation support for its punchline. Stored custom text stays
  verbatim, including instructions the user independently writes there.
- Topic entries contain metadata plus `主题正文：`; reply entries contain
  metadata plus `回复正文：`. Each body is limited to the first 1,200 cleaned
  characters. A reply's enclosing topic title need not express the reply
  author's opinion. Preserve quote attribution and distinguish self-reported
  experience from independently verified facts. Source content remains data,
  not instructions to the model.
- In the forum-roast preset, describe the voice through general traits: direct
  forum phrasing, brisk short sentences, rhetorical questions, short analogies,
  and satire grounded in actual
  wording. Do not request a named living author's individual style. Evidence and
  clarity take priority over humor; do not invent motives, experiences, or
  contradictions for a punchline.
- The detailed preset requests at most two observations in each of its first three sections, a one-
  or two-sentence synthesis, and 3–5 supported interest/style tags (fewer when
  evidence is sparse). Use plain text, without BBCode, Markdown tables, or code
  fences. Both built-in profile presets and the floor prompt contain exactly
  `回复正文在1000字以内`.
  This constrains the requested reply body, not thinking, and is prompt guidance
  only. Receive, display, and copy answers above 1,000 characters completely
  within the independent operational resource bounds. The floor-summary prompt
  keeps its independent single-floor scope.

### Dialog and cancellation

- Floor menus retain `AI 总结`; the profile menu entry and profile result-dialog
  title use `AI查成分`. These labels share the existing summary dialog and do not
  alter either prompt or its bounded input scope.
- Both floor menus and the profile overflow menu use `AiSummaryDialog` at
  their source page. Provide scrolling, loading/result/error states, retry,
  copy, and close. The result and prompt do not go into saved instance state.
- Render reasoning in an initially collapsed section with an accessible
  expand/collapse button. Keep the user's fold choice across streaming updates;
  reset it on retry/dismiss. Show answer, reasoning, status, and error in separate
  views. Copy reads `getCopyText()` directly, independent of fold state, and
  excludes reasoning, status/error labels, and protocol metadata. On failure,
  retain a partial answer and permit copying it. Do not announce every token
  through accessibility live regions. Dismissal clears all transient text.
- Transport and controller callbacks carry cumulative snapshots. The controller
  coalesces model progress into one queued UI update, keeps the latest reasoning
  on success and both projections on error, and rejects updates after a terminal
  event. Close/retry/target replacement invalidate queued snapshots as well as
  network calls. The UI does not parse raw events or infer content type.
- This adapts Cherry Studio's typed text/reasoning projections, stable fold state,
  answer-only copy, and request-owned stream snapshots. It introduces no persistent
  chat/output store or provider SDK dependency.
- Check configuration before loading input and again before sending to the
  model. If cleared, guide to settings without sending. If replaced during
  collection, require a new action instead of silently switching services.
- Each request is owned by its target and generation. Replacement, target
  change, close, or source-page pause cancels work; late input/model callbacks
  cannot overwrite a new request, including a retry for the same target.
- Cancel article dialogs on `onPause`, not merely `onStop`: the retained
  offscreen pages use `BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT` and remain
  STARTED. Refresh/data replacement also invalidates the floor snapshot.
- Reject stale reader deliveries before cancelling or replacing the current
  AI snapshot. Reader generation changes invalidate it; author-location-only
  metadata updates do not. Both starting a summary and accepting its callbacks
  require the displayed page to remain current.
- A network deadline may set `Call.isCanceled()` internally. The NGA adapter
  tracks deliberate cancellation separately so a timeout always terminates
  loading. The model client classifies `InterruptedIOException` as timeout
  before checking `Call.isCanceled()`.

## 4. Validation & Error Matrix

| Condition | Required outcome |
| --- | --- |
| Summary action with no saved config | Open AI settings; no input/model network requests |
| Existing v1 configuration or a new three-argument `AiConfig` | Default to forum roast while retaining endpoint, Key, and model |
| Confirm a prompt editor draft, then use toolbar Save | Persist style and retained custom text with the complete AI configuration |
| Cancel/Back/dismiss prompt editor | Preserve the previously accepted settings draft |
| Switch custom to a preset and back | Retain custom text; use only the selected style for requests |
| Custom is blank or retained text exceeds 8,192 UTF-16 code units | Reject without closing the editor or clipping text |
| Prompt selection/custom text changes during profile collection | Reject stale input before a model request; retry uses the new configuration |
| Model editor opened with draft address/Key and no model | GET the derived `/models` endpoint; no save or summary request |
| HTTP LAN endpoint or HTTPS endpoint | Normalize and use the configured scheme and port |
| Model list is empty or discovery fails | Manual entry stays available; retain same-service cached choices |
| Custom text/selection changes while discovery runs | Preserve the user's draft when results arrive |
| Model editor dismissed or draft service changes | Cancel/invalidate discovery; stale results cannot reach another editor |
| Lost key, corrupt record, failed atomic write | Fixed storage error; no plaintext/cache fallback |
| 401/403 from model | Authentication error |
| 3xx/404 from model | Address error; do not follow the redirect |
| 429 / 5xx / other 4xx | Rate-limit / server / invalid-request error |
| Connection/read/call timeout | Terminal timeout state, never a stuck spinner |
| Manual close or stale target/generation | Cancel and discard callbacks |
| Readable row has no known floor, or is an explicit comment | Neutral `AI 总结` title; omit the floor line without losing the selected body |
| Row source is unavailable | Disable the AI action and reject attempts to start it |
| Bad UTF-8, wrong response shape, oversized response | Fixed response error; no raw body |
| Reasoning/content SSE chunks | Incremental separate projections; reasoning starts folded |
| Usage-only or reasoning-only completion | `EMPTY_RESPONSE`; no reasoning/protocol substitution |
| First-choice finish reason `length` | `OUTPUT_EXHAUSTED`; preserve partial answer/reasoning |
| EOF/network break before a complete SSE terminal event | `INTERRUPTED_RESPONSE`; retain partial text |
| Serialized Chat Completions/SSE inside content | Reject without publishing the envelope as answer |
| Reply body exceeds the 1,000-character prompt guidance | Display and copy completely; no client clipping |
| Final progress queued immediately before success/error | Preserve the latest projections on terminal delivery |
| Fold toggled during streaming | Retain fold choice; copying still reads answer only |
| Error after a partial answer | Separate error label; copying returns only the received answer |
| Malformed NGA Content-Type or unmarked record with wrong author UID | Stop collection; do not send a model request |
| Nonblank string `denied`/`error` on an outer row or reply `__P` | Skip the unavailable item before author/content validation; retain the accepted-item allowance |
| Blank or non-string item marker | Apply normal author/content validation |
| All items unavailable on one page | Empty sample; use available activity from the other kind |
| Both samples empty, root `error`, or `data.__MESSAGE` | Report the existing collection error; do not send a model request |
| Profile source lists exceed retained limits | Prompt counts and input labels describe only the retained entries |
| A topic or reply body exceeds 1,200 cleaned characters | Retain only its prefix without splitting a surrogate pair; framing states the limit |
| Topic detail includes later or foreign-author floors | Include only the verified original belonging to the requested topic and viewed UID |
| Literal TAB/LF/CR in a quoted topic-detail body or ignored metadata field | Escape locally, decode equivalent text, and apply all original-post checks |
| Existing escaped control or literal backslash-plus-letter text in a topic detail | Preserve their distinct decoded values |
| Lone backslash followed by raw TAB/LF/CR, or another raw C0 string character | Existing NGA format error; no model request |
| Topic-detail escape expansion exceeds the normalized-response limit | Existing NGA format error; no clipping or partial sample |
| Raw control character in model-service JSON | Preserve the shared decoder's existing invalid-response result |
| Original body explicitly unavailable | Keep visible topic metadata with a fixed unavailable-body notice; no denial text or substitute floor |
| Unmarked original missing/ambiguous or topic/original identity disagrees | Terminal collection error; no model request |
| Cancellation during topic enrichment | Cancel the active call, schedule no later detail/reply reads, discard late callbacks |
| Custom instructions plus retained sample exceed the whole-prompt bound | Existing explicit input error; no partial model request or custom-text clipping |
| Any selected profile prompt style | Compose the same sample framing without the removed fixed rule paragraph; presets do not require source IDs/quotes |
| Second page-source invocation throws synchronously | Terminal collection error, still retryable |
| `503 Retry-After: 0` from model | One POST only; preserve server error |

## 5. Good / Base / Bad Cases

- Good: summarize a readable compatibility comment without claiming its raw
  `lou` is a real floor. A normal floor-zero post still displays floor zero.
- Bad: showing `第 -1 楼` or inventing a floor for a comment because its raw
  row contains a numeric value.
- Good: while logged in as user A, summarize viewed user B's first-page public
  activity using B's UID and the captured A session, with no credentials in
  the model prompt. Switching pages cancels the old operation.
- Good: a page mixes visible activity with explicitly unavailable placeholders;
  accept only visible entries, validating their actual topic/reply authors.
- Good: a topic entry contains its verified original body's first 1,200 cleaned
  characters, while a reply entry contains the viewed user's `__P` body from
  the reply list. A later floor never substitutes for the original.
- Good: a native detail with raw tabs in ignored metadata decodes successfully,
  while only its verified original contributes text to the profile sample.
- Base: ordinary JSON and already escaped whitespace keep their existing
  decoded body and identity checks.
- Bad: feeding the native detail directly to the shared strict preflight,
  deleting whitespace to make it parse, or accepting the same raw characters
  in model responses by weakening the shared decoder.
- Good: save a multiline custom prompt, select detailed analysis, and later
  return to custom; the same text is restored and only the selected instructions
  appear before the public sample.
- Base: an existing encrypted configuration loads with forum roast selected;
  opening and cancelling the editor changes neither the draft nor stored data.
- Bad: accepting a custom prompt but composing the request with a hard-coded
  default, losing custom text when selecting a preset, or saving prompt settings
  separately from the configuration transaction.
- Good: reasoning streams into a folded section, then a 1,200-character answer
  streams into the body and is copied in full; a prompt instruction is not a
  substring boundary.
- Bad: setting a small output token limit that reasoning alone exhausts,
  interpreting usage-only SSE as a successful answer, or copying the rendered
  dialog's reasoning and error text along with the body.
- Bad: rejecting a whole mixed page because an unavailable placeholder has a
  foreign author, or accepting denial text because its reply author matches.
- Base: an unconfigured floor action opens settings and sends no request;
  settings can test a draft before saving it.
- Good: a user enters `http://192.168.1.10:1234/v1` and a Key, opens the model
  editor, and selects a fetched ID or a custom one before saving from the toolbar.
- Base: a compatible service without `/models` still supports a manually entered
  model, including after a failed refresh of a previously available list.
- Bad: reading all rows to summarize one floor, carrying the global NGA
  Cookie into a model request, or relying on `persistent=false` alone to keep
  an `EditTextPreference` secret out of Fragment saved state.
- Bad: requiring a configured model to list models, showing choices from a
  different endpoint/Key, or replacing custom text with an asynchronous result.

## 6. Tests Required

- `AiConfigTest`, `AiConfigRecordTest`, and `AiConfigStoreTest`: endpoint
  normalization, fixed validation errors, real AES-GCM round trips/tampering,
  atomic failure, reload, lost-key, and partial-clear behavior. Cover v1
  migration to the forum default, both preset selections, exact multiline
  custom round trips, retained custom text under presets, worst-case UTF
  encoding, and an atomic failure preserving all previous fields.
- `AiProfilePromptTest` and `AiProfilePromptEditorStateTest` cover distinct presets, selected
  instructions, exact custom text, blank/overlong validation, local draft
  confirmation/cancellation, and custom-to-preset-to-custom transitions.
- `AiResponseParserTest` and `AiSummaryClientTest`: bounded/type-safe parsing,
  special keys, UTF-8, request JSON, auth/Cookie isolation, status errors,
  redirects, 503 request count, explicit cancellation, and transport deadlines.
  Assert summaries stream and both summaries and connection tests send exactly
  `max_tokens: 10000`, without other token aliases or thinking overrides.
  Fake-server progress must arrive before the
  terminal event. Include partial stream failure, timeout flush, JSON fallback,
  and valid SSE larger than the old 256 KiB response limit.
  `AiStreamParserTest` covers split UTF-8, frame/tag boundaries, multiline and
  CR/LF framing, index-zero choice selection, usage/DONE, empty/exhausted/
  interrupted results, inline thinking, literal protocol-like prose, serialized
  envelopes, operational bounds, and full replies above 1,000 characters.
  Include valid content followed by malformed UTF-8 in one read, interrupted
  ordinary protocol-like prefixes, and explicit index zero after an unindexed
  row. Indexless fallback is allowed only when every choice is unindexed.
  `AiModelsClientTest` additionally covers HTTP production transport, base/full/custom
  URL derivation, draft-only validation, model ID types/bounds/deduplication,
  immutable and empty results, and malformed/oversized list responses.
- `SummaryInputTest`, `SummaryControllerTest`, `ProfileSummaryLoaderTest`,
  and `NgaProfilePageSourceTest`: frozen rows, correct UID, two first-page
  list operations plus bounded original-body reads, topic/reply text, limited
  content, date boundaries, charset errors,
  timeout versus user cancellation, synchronous failures, and late callbacks.
  Floor snapshots cover missing floor metadata, explicit comments with a
  numeric raw floor, known floor zero, and later row/presentation mutations;
  unknown floors never appear as fabricated numbers in the prompt.
  Profile parser regressions also cover mixed visible/unavailable topics,
  outer and nested reply markers, both marker names, blank/non-string markers,
  unmarked foreign authors, all-unavailable pages, unchanged whole-page errors,
  and the 20 accepted-item cap after filtering. Loader tests cover an empty
  topic sample with available replies and the both-empty error.
  Topic-body coverage verifies explicit floor/TID/author identity, unavailable
  originals, wrong/missing/ambiguous originals, request bounds/order, body-only
  projection, envelope handling, and cancellation/late callbacks during
  enrichment. Inject literal TAB/LF/CR after fixture serialization: a
  `JSONObject.toJSONString()` fixture alone escapes them and cannot reproduce
  the native wire failure. Cover controls in the original and ignored metadata,
  escaped counterparts versus literal backslash text, escaped quotes and
  backslashes, malformed lone-backslash/control pairs, other raw C0 rejection,
  and normalization expansion at and beyond the limit. Exercise the complete
  list/detail/reply composition path with that raw-wire detail, and retain the
  strict model-decoder regression. `SummaryInputTest` covers both body prefixes at 1,199/1,200/1,201
  characters and surrogate boundaries. `AiSummaryClientTest` sends maximum
  samples with each built-in style without clipping and verifies that an
  oversized custom input fails before any request.
  `SummaryInputTest` also verifies retained counts and independent input
  numbering across truncation, null entries, and empty/partial samples; prose
  and tone instructions are source-reviewed rather than duplicated as a string
  snapshot test. Assert both built-in profile presets and the floor prompt
  contain `回复正文在1000字以内`. Exercise saved custom instructions through
  the configuration-aware source/controller/model path and reject a prompt
  configuration change before sending.
  Controller cases include progress coalescing, terminal flush, late progress
  after close/same-target retry/target replacement, retained partial errors,
  separate reasoning, and complete answer-only copying above 1,000 characters.
- `AiSettingsContractTest`, `DefaultSettingsContractTest`, and
  `AiSummaryUiContractTest`: settings hierarchy/navigation, Key state-saving
  precautions, both floor menus, loaded-profile visibility, shared dialog,
  and pause/refresh cleanup.
  Settings coverage also asserts the toolbar save icon, model-editor wiring,
  the fifth profile-prompt row, the three choice labels, multiline custom input,
  and saving the complete prompt-aware draft through the existing store.
  Shared-dialog coverage asserts separate views, default folding and reset,
  accessible toggle, answer-only copy, and clearing transient text on dismiss.
  `AiModelEditorStateTest` covers model list/custom/error interaction, same-service
  cache retention/invalidation, late callback rejection, and user-edit preservation
  during discovery.
- Follow the Android quality gate for app JVM/build and all-module lint.
  `AiConfigStoreInstrumentedTest` exercises real Keystore/AtomicFile with
  isolated test aliases/files. Build its APK by default, but run it only with
  current device authorization; otherwise report not run per project policy.

## 7. Wrong vs Correct

```java
// Wrong: native NGA strings can contain raw tabs that strict JSON rejects.
JSONObject data = SafeJsonParser.parseObject(rawNgaDetail);

// Correct (summary package): normalize the native representation locally,
// then preserve the strict decoder and verified-original projection.
String body = NgaTopicBodyParser.parse(rawNgaDetail, viewedUid, requestedTid);
```

```java
// Wrong: compatibility rows and explicit comments may not have a known floor.
String title = context.getString(R.string.ai_summary_floor_title, input.getFloor());

// Correct: use the frozen presentation fact without inventing a floor number.
String title = input.hasFloor()
        ? context.getString(R.string.ai_summary_floor_title, input.getFloor())
        : context.getString(R.string.ai_summary_action);
```

```java
// Wrong: response position alone does not establish the requested original post.
String body = scalar(object(rows.get("0")).get("content"));

// Correct (summary package): verify topic, explicit floor, and author first.
String body = NgaTopicBodyParser.parse(raw, viewedUid, requestedTid);
ProfileSummaryInput.Entry enriched = metadata.withBody(body);
// A null result produces an application-owned unavailable-body notice.
```

```java
// Wrong when saving an edited settings draft: silently resets the prompt selection.
AiConfig config = new AiConfig(endpoint, apiKey, model);

// Correct: service credentials and the accepted prompt draft share one atomic save.
AiConfig config = new AiConfig(endpoint, apiKey, model, promptEditor.getPrompt());
configStore.save(config);
```

```java
// Wrong: reasoning can exhaust the cap before an answer; clipping can lose body text.
payload.put("max_tokens", 1024);
String copyText = result.substring(0, Math.min(result.length(), 1000));

// Correct: apply the chosen shared generation cap and copy the full answer projection.
payload.put("stream", true);
payload.put("max_tokens", 10000); // No separate reasoning budget or thinking override.
String copyText = controller.getState().getCopyText();
```

```java
// Wrong: a deadline can cancel the Call internally and strand the UI in Loading.
if (!call.isCanceled()) {
    callback.onError("读取失败");
}

// Correct: explicit user cancellation is distinct from a transport deadline.
if (!userCanceled.get()) {
    callback.onError(safeFailureMessage(failure));
}
```

The controller still rejects stale generations after either path. Network
cancellation alone is not a substitute for UI ownership checks.

```java
// Wrong: first-time model discovery would fail because no model is selected.
AiConfig config = currentConfiguration();
client.listModels(config.getEndpoint(), config.getApiKey(), callback);

// Correct: discovery validates only the fields the operation needs.
client.listModels(draftEndpoint, currentApiKey(), callback);
```

```java
// Wrong: this hides genuine author mismatches as empty/partial success.
if (authored == null || !uid.equals(scalar(authored.get("authorid")))) {
    continue;
}

// Correct: recognize explicit unavailability before validating available data.
if (hasUnavailableMarker(row)) {
    continue;
}
JSONObject authored = kind == ProfileSummaryLoader.Kind.REPLIES
        ? object(row.get("__P")) : row;
if (kind == ProfileSummaryLoader.Kind.REPLIES && hasUnavailableMarker(authored)) {
    continue;
}
// Keep the existing author and required-content checks after these guards.
```
