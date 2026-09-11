# Model Response Evidence

## Observations

All service credentials and captured content remain in ignored .temp, never
in this document. No model reasoning text is retained here.
The authorized first-page TOPIC.LIST reads yielded 33 topic rows (20 explicitly
unavailable, 13 accepted) and 19 accepted replies. All accepted authors matched.

| Probe | Result |
| --- | --- |
| DeepSeek, ordinary JSON, 1,024-token limit | Upstream HTTP 403; no successful answer |
| GLM, ordinary JSON, 1,024-token limit | HTTP 200; 1,024 output tokens, 1,014 reasoning tokens; content empty; finish length |
| GLM, normal thinking, 4,096-token limit | HTTP 200; 3,781 output tokens, 3,436 reasoning tokens; 454-character answer; finish stop; about 45 seconds |
| GLM, streaming, no token parameter, earlier probe | HTTP 200 SSE; 2,680 input tokens, 4,096 output tokens; 10,997 reasoning characters and no body; finish length; 53.2 seconds |
| GLM, streaming, no token parameter, user-requested final probe | HTTP 200 SSE; 2,680 input tokens, 3,876 output tokens; 617-character body; finish stop and DONE; 49.05 seconds |

The final probe used `glm-5.3-flash`, the current prompt sentence, the same
captured public sample, and the App's endpoint normalization without inserting
a version prefix. Earlier exploratory harness calls inserted `/v1`; do not
claim that those routes or raw requests were identical. No redirect/retry or
additional NGA read occurred in the final probe. It received 3,824 SSE events
and 787,423 bytes. First reasoning arrived at 12.62 seconds; first answer text
arrived at 45.72 seconds. Only the body is retained in the private result file;
reasoning was counted in characters without being logged.

The final recorded usage contains input/output totals but no separate
`reasoning_tokens`. Their arithmetic sum is 6,556 tokens; do not fabricate a
reasoning/body token split from the character counts. The successful request
had no token cap. After seeing the result, the user selected `max_tokens: 10000`
for the implementation, replacing the prior no-cap choice. This successful
probe is not a live verification of the subsequently chosen explicit cap.

An exploratory thinking-disabled comparison was sent before the user rejected
that direction. It still reported reasoning/exhaustion. It is not the selected
fix or an acceptance case; production must preserve default thinking.
The user copied a serialized usage-only SSE event and DONE from the App result.
An exhausted comparison also put a serialized upstream message envelope into
message.content. Nonblank protocol text must not count as a successful reply.

## Limits and Required Cases

The Python live harness mirrors source request construction and normalizes the
bounded real sample; username is omitted. It does not execute the APK or Java
parser. Do not claim byte-identical APK or UI verification.
The previous 500-character sentence was prompt guidance. The directly reproduced
failure is exhaustion of the application's small token budget. No new character
clipping rule follows from it.
Streaming overhead alone exceeded the former 256 KiB whole-response bound in
both streaming probes. Frame, total transport, and projected-text bounds must
remain distinct. Removing a client cap cannot guarantee a complete answer:
provider defaults and variable reasoning consumption can still exhaust output.

Regressions must cover split UTF-8/events, multiline SSE data, first-choice
selection, reasoning versus answer, usage-only/DONE, normal JSON fallback,
serialized empty envelopes, exhaustion, missing completion, partial failure,
answers over 1,000 characters, and late progress after cancel/retry/target change.
