# BYOK AI implementation context

## Product decision

This task implements only three visible capabilities, in this order:

1. one AI settings screen;
2. one-floor AI summary from the floor overflow menu;
3. viewed-user AI summary from the profile overflow menu.

The current release uses only an API configured by the user. System/on-device models, hosted project keys, generic chat, streaming, search, tools, multiple provider profiles, and unrelated advanced features are not part of this task.

## Source observations

- The historical `nga_harmony` implementation used an OpenAI-compatible endpoint, a user-entered API Key, and a model name. Its one-floor action sent the thread title, floor number, author, and plain-text body.
- Its user action collected the target UID's first page of topics and first page of replies. The Android implementation keeps this bounded input but presents it as a summary, not as a sensitive or exhaustive profile.
- The current LNGA main branch has narrowed its AI path to DeepSeek but still requires the user's Key. It does not provide a free project API or a phone system-model call.
- No source code or UI assets are copied from the HarmonyOS app. It is behavior evidence only.

Detailed historical evidence remains in `research/nga-harmony-ai-source-audit.md`. System-model research is intentionally not implementation context for this task.

## Current Android anchors

- Settings: `nga_phone_base_3.0/src/main/res/xml/settings.xml` and `sp.phone.ui.fragment.SettingsFragment`. Follow the existing root-level “实验室” `PreferenceScreen` pattern to open a dedicated `SettingsAiFragment` backed by `settings_ai.xml`.
- Floor menu: both `article_list_context_menu.xml` files; `ArticleListFragment` already binds the clicked `ThreadRowInfo` to the popup menu.
- Profile menu: `menu_user_profile.xml`; `ProfileActivity` already gates actions on loaded `mProfileData` and exposes its target UID.
- Network: OkHttp is already available, but the NGA `RetrofitHelper` is not suitable for an AI endpoint because it owns NGA-specific Cookie, encoding, and logging behavior.

## Minimum implementation boundary

- Persist one config: HTTPS endpoint, model, and an encrypted Key reference.
- Protect the Key with Android Keystore-backed local encryption; never place plaintext in ordinary preferences, backup, logs, crash output, or resources.
- Keep endpoint, Key, model, test, and clear controls on the level-two AI settings page. A password-style preference must bypass default plaintext `EditTextPreference` persistence and write only to the secure store.
- Use a dedicated UTF-8, no-Cookie client for a non-streaming OpenAI-compatible Chat Completions request.
- Bind every floor request to the clicked row snapshot and every profile request to the viewed `mProfileData.uid`.
- Cancel in-flight work on exit or replacement and reject late results whose object identity no longer matches.
- Use fakes and MockWebServer for automated tests; do not use a real user Key or real forum content.

## Expected UI behavior

- Missing or cleared configuration routes the user to AI settings without sending a request.
- Both summary actions stay on their source screen and reuse one scrollable summary dialog with `Idle -> Loading -> Success | Error`, retry, copy, and close-to-cancel behavior.
- The settings page states that summary text is sent to the configured API service.
- There is no standalone AI home or chat page.
