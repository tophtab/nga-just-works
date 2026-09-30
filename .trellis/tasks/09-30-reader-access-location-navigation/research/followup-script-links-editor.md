# Follow-up research: scripts, deep links, and inline edit media

Research date: 2026-09-30. Product code unchanged. Public userscript sources were fetched and compared with archived SHA-256 values. Web button evidence comes from the user's actual Via link and a locally saved static JS response; no live NGA requests or device operations were performed.

## Confirmed user decisions and new scope

- The requested request interval is random 200–500 ms instead of fixed 500 ms. Keep one physical request in flight; apply the randomized gap after terminal completion, as with the existing pacing boundary.
- Latest decision: network failure receives one tail retry, then 30-second author suppression if both attempts fail; expiry does not trigger another request. Returning to a thread automatically resumes its outstanding online work. These choices were explicitly selected by the user after the original audit.
- R4 original-reply positioning is explicitly accepted as product behavior.
- R5 is acknowledged as a bug.
- R6 is new: show inline image/emoticon previews while editing previously published topics/replies, consistent with composing new content.
- R3 user asks whether errors are split; answer: proposed accurate presentation by actual response, not an implemented change yet.

## R1: real Via link and independent saved-JS evidence

User provided:
- Browser: Via.
- Web topic: https://bbs.nga.cn/read.php?page=1&tid=47649154
- APP link: nga://openType=2?page=1&tid=47649154&

The link is a custom scheme with authority openType=2, query page/tid and a harmless trailing ampersand. It does not itself contain an Android package restriction. Never normalize it as a conventional HTTPS URL or mistake openType for a query parameter.

Local .temp/bbs.nga.cn.har contains a previously saved /common_res/js_read.js response. Only the public static JavaScript was extracted to /tmp/nga-saved-js_read.js; no cookies, personal bodies or response headers were copied into task artifacts.

Saved JS lines 4058–4075 define the APP阅读 button:
- Copy numeric uid/stid/fid/tid/page/pid from location.search.
- With pid, use openType=5; otherwise tid -> 2, stid -> 3, fid -> 1.
- Return nga://openType=<type>?<parameters>.
- Android branch at 4095–4101 installs a timeout fallback to //app.nga.cn/ and cancels it on window blur; this branch contains no explicit official-package selection.
- Harmony branch rewrites to nga://ngabbs.com/...; this is distinct from the user-provided Android format.

This matches the actual Via topic link. The missing Android nga scheme registration is a confirmed intake gap, not merely a hypothetical protocol.
Proposed MVP: nga topic type 2 and reply type 5 only; existing HTTPS behavior unchanged. Type 1/3 board/subforum routes can remain outside this reader task.
Use a pure bounded parser, read raw authority/type and validated numeric parameters, accept trailing &, reject conflicting/unsupported types and malformed IDs. Internal activity routing must retain page/tid/pid without turning arbitrary links into authenticated loads.
The app can become an eligible handler. Whether Via/system displays a chooser or uses an existing default remains untested device behavior, not a guarantee.
No additional user material is needed to implement this supported format. Runtime chooser troubleshooting, if requested later, depends on Via/system defaults.

## R2: exact previously referenced userscript

The previous reference is NGA UserInfo Enhance 2.0.10, not just the newly supplied checkInfo plugin:
- https://update.greasyfork.org/scripts/416741/NGA%20UserInfo%20Enhance.user.js
- SHA-256: 77b654c7cce6b498679a107a1909a805345942bd592fda7271b82188b77ecdca
- Pinned NGA Library 1.1.3 revision 1414880:
  https://update.greasyfork.org/scripts/486070/1414880/NGA%20Library.js
- SHA-256: 0dd63c80b3193ee3e90ec70ef34c2ddcac6f84a7de9595dce04c2574dd82d42f
Both fetched bytes match archived research exactly.

Call chain:
1. Enhance:1480 invokes api.getIpLocations(uid) when the location field is enabled.
2. Library:1812 checks USER_IPLOC_CACHE by UID. A fresh one-hour record returns immediately.
3. On miss/expiry, getUserInfo (:1771) checks a separate one-hour USER_INFO_CACHE.
4. If needed, request (:1664) fetches nuke.php?func=ucp&uid=<uid>, parses __UCPUSER from HTML, and caches parsed profiles.
5. A truthy ipLoc moves that location to the front of a persistent history with an observation timestamp. On absent ipLoc it can return previously retained history without recording a new success.

Behavior differences:
- Both cache freshness windows are one hour; this app's valid-observation cache is 24 h.
- No 500 ms gap, no 200–500 ms random gap, no global location request limit.
- No negative-cache duration for failed fetch/parse; another invocation may request again. There is no explicit same-call automatic second attempt.
- No per-UID in-flight promise sharing. Concurrent cold calls can duplicate work before cache writes finish.
- A Queue class with a five-minute pause exists elsewhere in the library, but this location path does NOT use it. Do not confuse it with a location failure cooldown.
- No location-specific AbortController/caller cancellation on SPA navigation or visibility changes. Full browser document teardown is separate platform behavior.
- No explicit location-path 429/Retry-After/503 session-stop policy.
- Persisted old location history can survive expiry and be returned when parsing yields no current location; this app deliberately stores only the latest observation.
The one-hour cache is not a strict per-user hourly quota. The 500 ms and 10-minute policies are app-local decisions, not inherited requirements of the reference script.

## R2: confirmed network-failure policy and implementation constraints

Confirmed: ordinary transient transport failure skips the author initially; while its owning thread remains foreground, one tail-of-queue retry may occur. If that fails, suppress only that author for 30 seconds. No timer-only background retries or unbounded loops.
Keep malformed-data failures separate from transient transport errors if only transient failures receive a tail retry. Current ProfileLocationResult collapses both into FAILURE, so this requires a typed distinction.
429/server-rejection/session-stop rules remain distinct and are not reduced to a 30-second author cooldown. A 503 is not presumed ordinary transient retry under the existing project contract.
Random pacing must inject a controllable random source for deterministic boundary/cancellation tests; choose a new 200–500 ms delay after each terminal callback and preserve it across queue/session handoffs.
Changing FAILURE_MILLIS requires a persisted-cache migration decision: AuthorLocationStore.java:134–136 currently validates the exact 10-minute duration and rejects the entire file on mismatches. Preserve valid success/rate-limit records and deliberately discard or shorten old failure records; do not accidentally drop everything.
The user subsequently confirmed first attempt plus one tail retry. They asked whether 30 seconds triggers another retry: it does not; it only suppresses new requests until expiry, and a later eligible demand is required. The user subsequently explicitly selected the 30-second network-failure cooldown and automatic resume of outstanding work on return. Other failure/stop classes retain the existing rules unless separately specified.

## R6: why new compose shows images but edit shows text

- TopicPostPresenter.java:40–55 inserts a SpannableString containing original [s:category:name] text and an ImageSpan from a local asset.
- TopicPostPresenter.java:207–239 applies an ImageSpan to the original [img]./path[/img] text after a successful upload, when a local image path is available.
- ArticleListFragment.java:111–123 passes source content as prefix for modify, not formattedHtmlData.
- PostActivity.java:73–97 places prefix into PostParam.
- TopicPostPresenter.java:81–87 inserts that existing source as a plain string, with no span reconstruction.
- TopicPostFragment.java:82–84 restores saved drafts as plain strings. onSaveInstanceState and send intentionally serialize getText().toString(), losing presentation spans while preserving source.
- TopicPostModel.getPostInfo retrieves upload auth only, not a replacement edit body. Reconstruct previews from retained source; do not add a duplicate post-content fetch.

Proposed approach:
- Parse recognized emoticon/image tokens without rewriting their bytes; overlay spans on the existing editor text.
- Emoticons use the corrected shared mapping from R5; images use thumbnail/cache loading with current NGA attachment-prefix rules.
- Download/decode away from the UI thread, bound image sizes, and attach callbacks to current editor/token identity so cursor edits, deletions and view destruction cannot restore stale media.
- Serialize the same BBCode on draft/save/send. Merely opening/editing must not re-upload existing attachments.
- Keep unknown tags, unsupported image forms and failures losslessly editable, with a clear placeholder or source fallback.
- Deleting/selecting an inline media token should act on its full source range; do not leave half a [img] or [s] tag.
- Restore previews after draft/view recreation too, since the same plain-text path causes the same symptom.
- Scope is inline images/emoticons, not full rich-text rendering of all BBCode. Animated playback and a source-mode toggle require separate UX decisions if requested.
