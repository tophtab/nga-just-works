# U3 B1 — operation-local decode seams on fastjson1

Date: 2026-10-01. Worktree `/home/toph/nga-just-works-upstream-adoption`.
Baseline B0 checkpoint `05c2602e`. B0 checker improvement (author-location
writer uses a separate output file) is preserved. B1 remains fastjson1; no
library, dependency, global parser configuration or storage change.

## Change boundary

The behavior gap was inability to execute the JSON decoding portion without
constructing Android/network owners. Only three planned production files change:

- `TopicConvertFactory.decodeTopicList`: package-local static typed bean decode,
  used by the existing public facade. Leading window-prefix removal remains
  in `getTopicListInfo`, as do subboard mapping, topic mapping, sort/filter,
  and the existing null/error boundary. The seam adds no envelope defaults or
  numeric-token repair.
- `TopicPostModel.decodeUploadResponse`: package-local pure JSON and compression
  selection, taking the existing compressed-attempt flag. A small immutable
  result contains compression selection or decoded attachment/check/URL values.
  The existing `onNext` still normalizes the wrapper, shows the same toast,
  starts the same compressed upload only on its existing branch, appends
  attachments, invokes the callback, and catches errors with the same message.
  No constructor, network, multipart, scheduling or lifecycle changes.
- `AvatarFileUploadTask.decodeUploadResponse`: package-local static decode of
  its distinct external `error/errorinfo/data` bean. The nested class is now
  package-visible so tests can inspect actual fields; JavaBean marker remains
  B2 work. The existing `onPostExecute` retains its flag/callback/dismissal
  behavior, including its unguarded malformed decode. It does not borrow NGA's
  `error_code` contract or add wrapper removal.

The new pure result is manually constructed, not reflectively decoded. It does
not need a JavaBean marker or keep rule. Java remains appropriate for these
existing Java callback owners. SafeJsonParser and ProfileWebUserParser were
already pure and are unchanged; their existing tests execute directly.

## Executable evidence

`TopicConvertFactoryDecodeTest` (5 tests):

- Six archived jdata fixtures copied byte-for-byte into test resources (hashes
  in `json-topic-jdata/README.md`): first/last/space/nested hex escapes, quote
  and control. All retain synthetic forum name/fid, time and empty topic map.
- Real TopicListBean/DataBean/CUBean/FBean/TBean/PBean fields, underscore keys,
  numeric author IDs coerced to strings, page counts, parent and sub_forums
  object-to-string compatibility, topic style/misc/date/reply fields.
- Missing/null/error envelopes remain absent data, rather than newly invented
  valid empty beans. This test does not call Android display/filter policy or
  claim that bean decode is whole-operation success.
- Malformed/truncated/tail input and a still-wrapped payload throw. Wrapper
  removal remains solely in the original caller.
- Old typed Topic decoding rejects the unused unknown-type extension fixture.

`TopicPostUploadDecodeTest` (5 tests):

- Unquoted data retains attachments/check/Unicode URL exactly.
- Numeric or string code9 on first attempt selects compression before reading
  absent/malformed/residual data. Compressed code9 without data fails; code9
  with data on the compressed attempt follows the old data path.
- Other error codes with no data fail; other codes with data retain their old
  data selection. Empty data still returns null fields and numeric scalars
  still become strings. These are existing semantics, not endorsed new wire
  guarantees or authorization to retry a different business error.
- Error envelope, malformed JSON/type, null error_code, nonnumeric code and
  unremoved wrapper throw to the existing callback catch.

`AvatarUploadDecodeTest` (4 tests):

- Unquoted external success uses string data; error flag/message win in the
  caller even if data is present (the decoder preserves all fields).
- Absent fields retain false/null defaults. `error_code:9` is not an NGA
  compression command for this external bean. JSON null remains null.
- Malformed/tail/wrapped input still throws at the original unguarded boundary.

Existing AiResponseParserTest and ProfileLocationParserTest execute their
bounded pure parsers, including unquoted data, @type/$ref, depth/size, grammar,
profile extraction and error redaction. B0 decimal/nested-key tests remain.
The full Debug regression includes B0 storage goldens, reader identity/source/
errors, U1 icons and U2 media.

## Observed old behavior and approved B2 delta

The initial 49-test focused run had one failed *new expectation*: unlike
SafeJsonParser/ProfileWebUserParser, fastjson1's typed TopicListBean reader
throws JSONException (caused by NPE) for an unknown `@type` in an unused
extension alongside `$ref`. Production code was not changed to satisfy the
assumption. B1 now records this actual old rejection. Main explicitly agreed
that B2's already-approved local tree + DisableReferenceDetect conversion
should accept the unused extension and preserve consumed fields. B2 must
update that one labelled characterization to the approved new expectation;
never relax the existing AI/profile ordinary-data/no-class-loading assertions.

No other golden expectation required changes. No existing test was weakened.
No Android stub, returnDefaultValues switch, mocking framework or raw-network
fixture was introduced. All sample tokens/text are synthetic.

## Remaining obligations — not waived acceptance

- B2 owns operation-local success/error coverage for the B0 action/notification/
  message-send/remote-filter gaps. Before expanding extraction, report the
  concrete owner and why its existing boundary cannot be executed. Use minimal
  pure seams only; do not introduce a global JSON framework or Android stub
  bypass. Current planned B1 scope did not include those owners.
- Android callback execution, compression/file IO, notifier/toast delivery,
  preference/DataStore managers and Activity recreation remain source-reviewed,
  not host-runtime or device evidence. Existing wrapper/callback/transport
  sections are unchanged in this diff.
- B2 owes all JSON2 consumers/annotations, local options, complete storage
  three-direction compatibility, actual dependency inspection and R8 evidence.
  Nonpublic/non-marker avatar bean remains an explicitly known B2 keep obligation.
- B3/B4 own the ordinary wire/mapper boundary. B1 does not move it or change
  source, query, account, paging, fallback or rendering policy.

## Validation

Executed commands:

```text
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests '*TopicConvertFactoryDecodeTest' --tests '*TopicPostUploadDecodeTest' --tests '*AvatarUploadDecodeTest' --tests '*AiResponseParserTest' --tests '*ProfileLocationParserTest' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
git diff --check
```

The initial focused run's new Topic expectation failure is explained above.
After pinning the actual old behavior, the full Debug gate passed (44 seconds):
**778 tests, zero failures/errors/skips across 13 modules**; app 683 tests in
77 suites, including all 14 new B1 tests. Unchanged module tests may be up-to-date;
the app suite executed. All nine B0 storage goldens remain green.

Fresh repository lint passed (35 seconds, all 536 tasks executed) and all
13 generated XML reports contain **0 Error / 0 Fatal**. This exceeds the narrow
B1 iteration gate and satisfies the product-checkpoint quality guidance; no
older lint report is presented as current. Diff whitespace check passed.
Six jdata resource bytes match the archived originals exactly.

No NGA/device, signing, publication or Git commit operation was performed.
Only the permitted Debug assembly was built. Code/tests are frozen for the
main session's independent check and checkpoint commit before B2.
