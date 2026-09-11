# Design

## Change Boundary

The behavior belongs to the existing Java Preference fragment and standalone AI
client. Retain the child Activity, theme, encrypted store, and summary call sites.
The user explicitly replaces the old HTTPS-only and explanatory-copy rules;
update the owning AI spec after implementation.

## Settings and Save

Remove instruction/status/save/clear preferences and their dead resources and
handlers. Use an AI-specific toolbar menu with `btn_ic_save`, a Save title for
accessibility, and the existing whole-config save transaction. Load/save failures
use fixed toasts instead of the removed status row. Preserve Key state-saving,
autofill, IME, capture, and dismissal protections while removing explanatory copy.
Endpoint help should be one short example; connection-test copy remains concise.

The subsequently requested profile rename changes only profile-specific menu/title
resources to `AI查成分`. Reuse the existing profile title resource where suitable;
floor labels, summary prompts, data collection, and result rendering retain their
current behavior.

## Model Discovery Contract

The client will expose:

```java
public Call listModels(String endpoint, String apiKey, ModelsCallback callback);
public interface ModelsCallback {
    void onSuccess(List<String> models);
    void onError(AiError error);
}
```

Callbacks run on the transport thread. Discovery validates endpoint and Key using
the same validators as configuration; it does not invent a placeholder model.
From the normalized Chat Completions endpoint, replace the final
`/chat/completions` with `/models`, preserving custom/version paths and port.
Send GET with the configured Bearer Key using the isolated existing transport.
Decode the OpenAI-compatible `data[].id` response through the shared bounded JSON
decoder, require string IDs valid as configured models, deduplicate them while
preserving provider order, and return an immutable bounded list. Empty lists are
valid and lead to custom entry. Fixed errors, strict UTF-8, response size caps,
timeouts, no redirects, and no NGA cookies also apply to this operation.

The model editor starts discovery each time it opens. Show loading briefly and
allow custom entry without waiting. A successful response offers a scrollable
list plus `自定义`; an error/empty response exposes manual entry with concise
status. Keep the last successful list only in view/session memory and only while
address and Key are unchanged. A refresh failure must not discard that list.
Preserve user edits/selection made while fetching. Do not persist discovery data.

Discovery has its own cancelable Call and generation, independent from the
connection test. Opening another editor, changing service identity, saving,
dismissing, pausing, or destroying the view cancels and invalidates it. Only the
active, resumed editor consumes results on the main thread. If a small pure-Java
state helper improves test coverage for selection and late callbacks, keep it
specific to this editor rather than introducing a general settings architecture.

## HTTP Compatibility

Allow only HTTP/HTTPS schemes while retaining rejection of URL credentials,
query/fragment, invalid ports, and control characters. Enable CLEARTEXT alongside
MODERN_TLS in the isolated AI client. The Android network-security configuration
already permits cleartext, so no global networking change is necessary. Keep all
POST single-use/retry and credential-isolation behavior intact.

## Files and Ownership

- UI: `SettingsAiFragment.java`, `settings_ai.xml`, `strings_ai_settings.xml`, a
  new toolbar menu and only necessary model-editor resources/helper, plus the
  settings/model-state tests.
- Transport: `AiConfig.java`, `AiSummaryClient.java`, `AiResponseParser.java`, and
  their focused tests for HTTP, discovery, errors, and normalization.
- Coordination: this task's artifacts and `.trellis/spec/backend/ai-summary-contract.md`.

## Rollback

The store format is unchanged. Reverting this task restores the prior UI and
HTTPS validation; an HTTP setting entered after this change would then require
re-entry as HTTPS. Verification does not write persisted app configuration or
change existing accounts. The user-authorized model-service check uses a temporary
Key in process memory only.
