# Frozen B4 pre-wiring facade parity

Captured by running ReadThreadFacadeParityTest against production commit
758aa6b9, plus one host-executable equivalence only: in buildRowHotReplay,
`!TextUtils.isEmpty(hot)` → `!hot.isEmpty()` for nonnull split String entries.
No decoder/mapper wiring existed at capture. No Android stub-default bypass.

78 synthetic inputs, both modes:156 complete actual-facade outcomes. Snapshots
include all serializable Java row/topic fields with explicit nulls, nested
comments/attachments, presentation, renderer-entry snapshots with ordered
attachment keys, deterministic renderer output and blacklist lookup trace.
Raw response equality is asserted directly. This is old production execution,
not a separate implementation of expected mapping. Empty/delimiter-only and
ordered duplicate17 inputs cover the narrow TextUtils equivalence.

Snapshots are immutable; never regenerate them to make the new mapper pass.
Baseline log: /tmp/u3-b4-baseline.log (2026-10-01; successful before wiring).

inputs.json SHA256 39fe06fe1343cc081e3d332018f7023870f31d2e751862ab9e798c5f2e994a7d
baseline.json SHA256 a84f626342d272950ae5d849d61a133f9d6397e6ae9ea52dd66e7bf91724f678
