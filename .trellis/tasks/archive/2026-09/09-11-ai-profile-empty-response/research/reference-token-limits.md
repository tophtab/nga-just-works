# Reference Token Limits

## Cherry Studio

Inspected revision: `60ab560f672f70497d29c544d4843f9b168a83c5` of
`https://github.com/CherryHQ/cherry-studio`.

- `src/shared/data/types/assistant.ts:102-112` stores `maxTokens: 4096` with
  `enableMaxTokens: false`. The number is not an active default output cap.
- `src/main/ai/contextBuild/resolveOutputReservation.ts:28-41` resolves an
  explicit call override first, then a custom numeric parameter, then an
  enabled assistant limit. With none of those, it returns the model ceiling
  only for Anthropic Messages; ordinary OpenAI-compatible Chat Completions
  returns `undefined`.
- `src/main/ai/runtime/aiSdk/params/buildAgentParams.ts:221-227,640-647` uses
  that resolver and removes `maxOutputTokens` when undefined.
- `src/main/ai/utils/modelParameters.ts` adjusts an explicit Anthropic output
  ceiling for its thinking budget. This endpoint-specific requirement is not
  a reason to impose a cap on the App's Chat Completions requests.

Therefore Cherry supports a user-controlled limit but leaves it disabled by
default for this endpoint type. A provider may still impose its own default.
These statements describe the pinned revision, not every published release.

## NGA Userscript

Source: `https://greasyfork.org/zh-CN/scripts/595323/code`, version `2.4.12`.
The inspected current source is held only in ignored `.temp`.

- Main analysis (`:782`), history analysis (`:2067`), and the floor-related
  call (`:2227`) explicitly send `max_tokens: 10000`.
- `callLuna` (`:664-674`) serializes its payload directly with `JSON.stringify`;
  it does not remove that setting before sending.
- The `history.slice(0,12000)` at `:2067` bounds input history; it is not
  evidence of clipping the generated reply at that length.

## Project Decision

After inspecting both references and a successful no-cap live request, the user
chose `max_tokens: 10000`, matching the userscript. This final choice supersedes
the earlier no-cap request and the intermediate 12,000-token suggestion.
Summary and connection-test payloads contain only `model`, `messages`, `stream`,
and `max_tokens`. Thinking and body generation share the 10,000-token ceiling;
input tokens are separate. Do not disable thinking or set a separate reasoning
budget. Cherry's implementation remains the reference for streaming, typed
content, folding, and copy, not the selected numerical limit.
Both summary prompts say `回复正文在1000字以内`; this is prompt guidance only.
Preserve all received body text within independent operational resource bounds.
