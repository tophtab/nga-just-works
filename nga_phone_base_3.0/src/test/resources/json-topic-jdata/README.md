# Topic jdata regression fixtures

Byte-identical copies of the six synthetic archived fixtures from
`09-11-upstream-august-2026-review/research/probes/fixtures`.
No raw response or private data. `TopicConvertFactoryDecodeTest` invokes the
production bean seam with each file and asserts forum name/fid, time and topic map.

These are normalized payloads. The caller retains its existing leading
`window.script_muti_get_var_store=` removal; the decoder does not add wrapper
or ordinary-reader numeric-token repair.

| File | SHA-256 |
| --- | --- |
| escaped_quote.json | `f6f0a40c02568e1a3afe78b92f74e87351ce75b549c8860483c7b37bb5ebdcd7` |
| hex_spaced_field.json | `09c8abc748c49f294c30a282de6165f819932a4e46b87ea34066dec6ddb2b4d9` |
| hex_unknown_first.json | `bd3203d2a4f806d15387840f3fa49e8870c61ddb2c0cd61b2beb20489526936c` |
| hex_unknown_last.json | `f49320d0e18fc5b8226a62309394aa84041fcd227c61f4a60348c1d0f1eac0b0` |
| nested_hex.json | `b83c23804bfb5841a1c42fa02d3d301b185e5789c20c7a56987679cc8d95d548` |
| valid_control.json | `2c5acb40ec737c4bb40847e66b06468b8b6943819da1f56f63e0fdc1e8ccd3f7` |
