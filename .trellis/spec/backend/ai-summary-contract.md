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
String AiConfig.getEndpoint(); // Complete normalized Chat Completions URL.
String AiConfig.getApiKey();   // In-memory use only; never log/serialize the object.
String AiConfig.getModel();

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
SummaryController.Cancelable ProfileSummaryLoader.load(
        String uid, String userName, SummaryController.Callback callback);
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
- The record is `NGAI` + version byte `1`, a random 12-byte nonce, and
  AES-GCM ciphertext with a 128-bit tag. The header is authenticated as AAD.
  Reject records above 16 KiB. Android Keystore owns the 256-bit AES key under
  alias `sp.phone.ai.config.v1`; encryption obtains its nonce from the provider.
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

- There is one AI configuration. The child settings screen contains only API
  address, API Key, model, and connection-test rows. Do not add instruction,
  current-configuration, Save, or Clear preferences. The top-right toolbar Save
  action reuses `btn_ic_save` and has an accessible Save title; it persists the
  complete draft through the existing store transaction.
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
  thread title, floor number, author, and plain-text body (up to 12,000
  characters). Do not traverse `ThreadData`, other floors, signatures, account
  credentials, or additional network content. Preserve quote attribution;
  convert media/emoticons to text placeholders without resolving their URLs.
- Profile summaries use the viewed `mProfileData.uid`, never the active
  account's UID. `AiSummarySources.profile` defers all session lookup and reads
  until the controller confirms a valid AI configuration.
- `NgaProfilePageSource` implements the existing `TOPIC.LIST` wire operation:
  `GET thread.php?authorid=<uid>&page=1&lite=js&noprefix`, adding `searchpost=1`
  for replies. Run the topics operation before replies, each capped at 20
  accepted entries; skipped unavailable records do not consume this allowance.
  Never request later pages or whole topics to obtain reply text.
- Capture one Cookie/UA snapshot for both operations. Permit only HTTPS port
  443 on the explicit NGA host set in the source, with no userinfo, query,
  fragment, custom base path, or redirects. There is no application retry or
  account rotation. Connection retries are disabled; an internal idempotent
  HTTP follow-up can still repeat the same page. The page limit is not a claim
  of at most two underlying network transmissions.
- Read a maximum of 512 KiB per NGA response. Honor a valid declared charset;
  default to pinned GBK when absent. A present but unparsable Content-Type,
  invalid charset, or invalid encoded bytes is a protocol error, not a reason
  to silently use GBK. Keep the raw header's presence distinct from
  `ResponseBody.contentType() == null`.
- Normalize the known JS prefix once, then use the shared bounded decoder,
  including for a string-valued `parent`. Require `data.__T`, its row count,
  and the correct author on available records. Replies come from each row's
  `__P.content`, `__P.authorid`, and `__P.postdate`; cap each reply at 800 characters.
  Format dates in the device display timezone. Extract only title, board,
  date, and public reply text into the prompt, not raw JSON or private profile
  fields.
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

### Profile composition prompt

- `ProfileSummaryInput.toPrompt()` requests a qualitative portrait of public
  discussion in five plain-text sections: `兴趣关注`, `主要观点`, `发言风格`,
  `成分总结`, and `标签`. Analyze expressed interests, views, and wording; do not
  assign scores, rankings, or a personality/credibility total.
- Include the actual retained topic and reply counts after bounded copying.
  Number entries independently as `[主题1]`, `[主题2]`, and `[回复1]`, `[回复2]`.
  These are local evidence identifiers, not links or additional NGA metadata.
  Each substantive observation cites an identifier and a short quote or concrete
  paraphrase. Missing evidence stays unknown; a contradiction needs both
  comparable statements and their references.
- Topic inputs contain titles, not their bodies. A reply's enclosing topic title
  need not express the reply author's opinion. Preserve quote attribution and
  distinguish self-reported experience from independently verified facts. Do not
  infer sensitive personal attributes or real-world identity, income, location,
  health, or character from these bounded samples. Source content remains data,
  not instructions to the model.
- Describe the voice through general traits: deadpan black humor, brisk short
  sentences, occasional technical metaphors, and satire grounded in actual
  wording. Do not request a named living author's individual style. Evidence and
  clarity take priority over humor; do not invent motives, experiences, or
  contradictions for a punchline.
- Request at most two observations in each of the first three sections, a one-
  or two-sentence synthesis, and 3–5 supported interest/style tags (fewer when
  evidence is sparse). Use plain text, without BBCode, Markdown tables, or code
  fences. Both profile and floor prompts contain exactly `回复正文在1000字以内`.
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
- A network deadline may set `Call.isCanceled()` internally. The NGA adapter
  tracks deliberate cancellation separately so a timeout always terminates
  loading. The model client classifies `InterruptedIOException` as timeout
  before checking `Call.isCanceled()`.

## 4. Validation & Error Matrix

| Condition | Required outcome |
| --- | --- |
| Summary action with no saved config | Open AI settings; no input/model network requests |
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
| Profile source lists exceed retained limits | Prompt counts and evidence IDs describe only the retained entries |
| Profile sample has no support for a view or trait | State insufficient evidence; no score or invented personal conclusion |
| Second page-source invocation throws synchronously | Terminal collection error, still retryable |
| `503 Retry-After: 0` from model | One POST only; preserve server error |

## 5. Good / Base / Bad Cases

- Good: while logged in as user A, summarize viewed user B's first-page public
  activity using B's UID and the captured A session, with no credentials in
  the model prompt. Switching pages cancels the old operation.
- Good: a page mixes visible activity with explicitly unavailable placeholders;
  accept only visible entries, validating their actual topic/reply authors.
- Good: a profile observation cites `[回复2]` and its concrete wording, with
  restrained satire about the statement rather than an invented personal story.
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
  atomic failure, reload, lost-key, and partial-clear behavior.
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
  operations, reply text, limited content, date boundaries, charset errors,
  timeout versus user cancellation, synchronous failures, and late callbacks.
  Profile parser regressions also cover mixed visible/unavailable topics,
  outer and nested reply markers, both marker names, blank/non-string markers,
  unmarked foreign authors, all-unavailable pages, unchanged whole-page errors,
  and the 20 accepted-item cap after filtering. Loader tests cover an empty
  topic sample with available replies and the both-empty error.
  `SummaryInputTest` also verifies retained counts and independent evidence
  numbering across truncation, null entries, and empty/partial samples; prose
  and tone instructions are source-reviewed rather than duplicated as a string
  snapshot test. Assert both prompts contain `回复正文在1000字以内`.
  Controller cases include progress coalescing, terminal flush, late progress
  after close/same-target retry/target replacement, retained partial errors,
  separate reasoning, and complete answer-only copying above 1,000 characters.
- `AiSettingsContractTest`, `DefaultSettingsContractTest`, and
  `AiSummaryUiContractTest`: settings hierarchy/navigation, Key state-saving
  precautions, both floor menus, loaded-profile visibility, shared dialog,
  and pause/refresh cleanup.
  Settings coverage also asserts the toolbar save icon and model-editor wiring.
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
