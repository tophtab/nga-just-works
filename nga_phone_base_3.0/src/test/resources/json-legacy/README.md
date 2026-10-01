# fastjson1 storage goldens

Synthetic data only. These semantic JSON snapshots were asserted against actual
production bean/codec writers using `com.alibaba:fastjson:1.1.71.android` at
`88ce8be3` plus B0 tests on 2026-10-01. They are immutable migration oracles,
not files copied from a device. Object whitespace/key order is irrelevant;
array order, keys, scalar values/types and null omission are intentional.

`LegacyStorageGoldenTest` owns topic-history (all ThreadPageInfo/ReplyInfo
persisted properties), attachment (all ten fields), filter-keywords (compiled
Pattern excluded), filter-users (old public `m*` plus bean names, no credentials),
legacy-boards (BoardKey emits `{}`), string-list, and board-tree. The sparse
second topic/user/keyword records fix old default/null omission. Board parentId
and U1 iconUrl are derived and absent. Existing U1 tests cover injected iconUrl.

`ArticleCacheStoreTest.frozenOldWindowEnvelopeKeepsNullNumericTypesAndRawBytes`
uses owned-window through the production codec. Its explicit null pageSize is
required. `AuthorLocationStoreTest.frozenOldWriterIncludesLongTimesAndOptionalNetworkExtension`
reads/writes author-locations through the actual file store: null location is
omitted, timestamps are Long, network failure retains v1 FAILURE and the optional
networkExpires extension. Do not make location null explicit to resemble the
owned-cache writer: these formats intentionally use different writer options.

B2 must read these old snapshots with the migrated production paths, compare
new writes to them semantically, and run a test-only old-library reader on the
new outputs. B0 itself proves old→old only, not any JSON2 or R8 result.
