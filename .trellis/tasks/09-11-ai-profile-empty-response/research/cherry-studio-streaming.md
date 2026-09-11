# Cherry Studio Streaming Reference

Source: `CherryHQ/cherry-studio`, pinned main revision
`60ab560f672f70497d29c544d4843f9b168a83c5`, inspected 2026-09-12.
Public source snapshots are under ignored `.temp/cherry-studio-source/`.

## Verified Pattern

- `src/main/ai/streamManager/pipeStreamLoop.ts:43-103` broadcasts UI message
  chunks while `readUIMessageStream` accumulates one message snapshot. Its
  callback publishes updated snapshots; it retains error/abort information
  separately from the accumulated message.
- `src/renderer/utils/message/partsHelpers.ts:23-39` projects text and reasoning
  independently by filtering typed message parts (`text` versus `reasoning`).
  These are parts of one message, not separate conversations or output files.
- `src/renderer/components/chat/messages/blocks/ThinkingBlock.tsx:74-104`
  initializes expansion from `thoughtAutoCollapse` and retains component state
  across streaming updates. Lines 116-167 provide a keyboard-accessible toggle
  with expanded-state semantics and a separate hidden/visible reasoning body.
- `src/renderer/components/chat/messages/frame/messageMenuBarActions.tsx:184-200`
  copies a text projection instead of scraping the rendered message DOM.
  `src/renderer/utils/message/find.ts:77-92` explicitly distinguishes answer-only
  naming/clipboard text from reasoning and other export-only content.
- `src/main/ai/runtime/aiSdk/params/features/reasoningExtraction.ts:10-51`
  normalizes inline reasoning tags through extraction middleware for compatible
  endpoints; native reasoning remains separate. Extract before rendering/copy.
- `src/renderer/services/aiTransport/ExecutionStreamOverlayService.ts:670-711`
  coalesces pending snapshots and validates epoch/reader versions before applying
  them. Terminal handling flushes the last snapshot (lines 640-648).

Permalinks use:
`https://github.com/CherryHQ/cherry-studio/blob/60ab560f672f70497d29c544d4843f9b168a83c5/<path>`.

## Android Adaptation

Keep one request-owned message state with typed answer/reasoning projections.
The callback's two cumulative strings are the narrow Java projection needed by
this two-content-type dialog; they do not introduce two persisted outputs.
The controller owns one immutable snapshot and coalesces UI updates. Folding
changes presentation only, and copy reads the answer-only projection directly.

Normalize native `reasoning_content`/reasoning deltas at the transport boundary.
Support a leading inline `<think>` or `<thinking>` section across chunk splits,
moving it to reasoning before any copyable answer is published. An unclosed
matched thinking section must not leak into answer text. Ordinary prose/code
that contains such tags after substantive answer text stays literal; this is a
narrow summary-client adaptation, not an entire generic provider middleware.

Cherry's multi-tool, persisted chat, Electron IPC, and React/AI-SDK machinery is
not needed for this transient Android summary surface. This implementation
follows the verified data flow, folding, copy, and snapshot ownership patterns
without copying third-party implementation code or adding those dependencies.
