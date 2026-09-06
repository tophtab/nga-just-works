# BYOK AI Settings and Summaries

## 1. Scope / Trigger

Apply this contract to `sp.phone.ai`, the AI settings page, the floor/profile
summary menus, and their shared dialog. The implemented boundary is one
user-configured OpenAI-compatible Chat Completions service. There is no hosted
project key, system model, local model, chat page, streaming, or pagination.

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
// AiSummaryClient.Callback: onSuccess(String text) / onError(AiError error).
// Asynchronous transport callbacks run on the transport thread.

JSONObject SafeJsonParser.parseObject(String json);
FloorSummaryInput FloorSummaryInput.fromRow(String title, ThreadRowInfo row);
SummaryController.Cancelable ProfileSummaryLoader.load(
        String uid, String userName, SummaryController.Callback callback);
// SummaryController.Callback: onSuccess(String text) / onError(String message).
void SummaryController.start(String target, SummaryController.InputSource source);
void SummaryController.cancel();
void SettingsAiFragment.open(Context context);
```

`SummaryController` receives `ConfigSource`, `Model`, a UI `Executor`, a
`Supplier<String>` for the current target, and a `Listener`. Its immutable
state exposes `IDLE`, `LOADING`, `SUCCESS`, or `ERROR` and display text.

## 3. Contracts

### Configuration and secret storage

- Normalize HTTPS endpoints with OkHttp `HttpUrl`. Reject credentials in the
  URL, query/fragment components, control characters, and non-HTTPS schemes.
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
- Settings tests use the current draft and do not save it. Edits, saves,
  clears, and page pause cancel the test and invalidate its callback generation.

### Model transport

- Use the dedicated OkHttp 4.12 client. Do not use NGA Retrofit, its Cookie
  provider, encoding converter, interceptors, or body logger. Use
  `CookieJar.NO_COOKIES`, `Authenticator.NONE` for both origin and proxy,
  no cache, and no redirects. Ordinary system proxy routing remains available.
- Send UTF-8 JSON with `model`, one user `messages` entry, `stream: false`,
  and `max_tokens` (1,024 for summaries; 8 for connection tests). Only the
  configured API receives `Authorization: Bearer <key>`.
- Disable connection retries **and** mark the POST body `isOneShot() == true`.
  Disabling connection retries alone does not stop an OkHttp follow-up for
  `503` with `Retry-After: 0`. Preserve the first HTTP failure, without a second
  billed send. Match MockWebServer to the resolved production OkHttp version.
- Limit prompts to 64 Ki characters, decompressed responses to 256 KiB, and
  result text to 32 Ki characters. Total/connect/read/write deadlines are
  60/15/45/15 seconds. Decode response bytes strictly as UTF-8 and consume only
  the first choice's nonblank string `message.content`.
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
  entries; never request later pages or whole topics to obtain reply text.
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
  and the correct author. Replies come from each row's `__P.content`,
  `__P.authorid`, and `__P.postdate`; cap each reply at 800 characters.
  Format dates in the device display timezone. Extract only title, board,
  date, and public reply text into the prompt, not raw JSON or private profile
  fields. Missing, challenge, rejected, malformed, or mismatched data is not
  an empty success.

### Dialog and cancellation

- Both floor menus and the profile overflow menu use `AiSummaryDialog` at
  their source page. Provide scrolling, loading/result/error states, retry,
  copy, and close. The result and prompt do not go into saved instance state.
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
| No saved config | Open AI settings; no input/model network requests |
| Lost key, corrupt record, failed atomic write | Fixed storage error; no plaintext/cache fallback |
| 401/403 from model | Authentication error |
| 3xx/404 from model | Address error; do not follow the redirect |
| 429 / 5xx / other 4xx | Rate-limit / server / invalid-request error |
| Connection/read/call timeout | Terminal timeout state, never a stuck spinner |
| Manual close or stale target/generation | Cancel and discard callbacks |
| Bad UTF-8, wrong response shape, oversized response | Fixed response error; no raw body |
| Malformed NGA Content-Type or wrong author UID | Stop collection; do not send a model request |
| Second page-source invocation throws synchronously | Terminal collection error, still retryable |
| `503 Retry-After: 0` from model | One POST only; preserve server error |

## 5. Good / Base / Bad Cases

- Good: while logged in as user A, summarize viewed user B's first-page public
  activity using B's UID and the captured A session, with no credentials in
  the model prompt. Switching pages cancels the old operation.
- Base: an unconfigured floor action opens settings and sends no request;
  settings can test a draft before saving it.
- Bad: reading all rows to summarize one floor, carrying the global NGA
  Cookie into a model request, or relying on `persistent=false` alone to keep
  an `EditTextPreference` secret out of Fragment saved state.

## 6. Tests Required

- `AiConfigTest`, `AiConfigRecordTest`, and `AiConfigStoreTest`: endpoint
  normalization, fixed validation errors, real AES-GCM round trips/tampering,
  atomic failure, reload, lost-key, and partial-clear behavior.
- `AiResponseParserTest` and `AiSummaryClientTest`: bounded/type-safe parsing,
  special keys, UTF-8, request JSON, auth/Cookie isolation, status errors,
  redirects, 503 request count, explicit cancellation, and transport deadlines.
- `SummaryInputTest`, `SummaryControllerTest`, `ProfileSummaryLoaderTest`,
  and `NgaProfilePageSourceTest`: frozen rows, correct UID, two first-page
  operations, reply text, limited content, date boundaries, charset errors,
  timeout versus user cancellation, synchronous failures, and late callbacks.
- `AiSettingsContractTest`, `DefaultSettingsContractTest`, and
  `AiSummaryUiContractTest`: settings hierarchy/navigation, Key state-saving
  precautions, both floor menus, loaded-profile visibility, shared dialog,
  and pause/refresh cleanup.
- Follow the Android quality gate for app JVM/build and all-module lint.
  `AiConfigStoreInstrumentedTest` exercises real Keystore/AtomicFile with
  isolated test aliases/files. Build its APK by default, but run it only with
  current device authorization; otherwise report not run per project policy.

## 7. Wrong vs Correct

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
