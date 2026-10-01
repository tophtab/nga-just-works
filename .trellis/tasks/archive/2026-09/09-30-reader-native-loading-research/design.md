> 历史方案：原 R7 已拆为独立调研成果归档；本文不是执行清单。普通读取修复及兼容/浏览器移除仍搁置。当前范围以 prd.md 和 delivery.md 为准。

# Ordinary-reader repair and feature removal — design in research

> 2026-09-30 用户最新决定：R7 全部搁置，停止研究、诊断及实施，待用户另行恢复。保留“先修复正常读取，再移除兼容模式和帖子内置浏览器”的目标与已有取证；未修改 R7 产品代码。

## Authoritative product direction

The user requires ordinary native reading to work first, followed by removal
of compatibility reading and the internal forum browser. Hidden App API
fallback is explicitly rejected. This supersedes all earlier automatic-App
recovery proposals. Task status remains planning; there is no validated full
ordinary-only repair yet and no product change in this session.

Historical proposals are in research/superseded-fallback-diagnostics-plans.md;
they are not implementation instructions. Research reports retain evidence,
but their earlier recovery recommendations are superseded by this direction.

## Ordinary data flow and unresolved defect

The intended online flow is selected-account ordinary request -> bounded
response decoding -> ordinary JSON/row conversion -> native reader. Genuine
failure remains a native error state with explicit retry. No online App API
source or dedicated forum-browser branch remains in the final flow.

The saved 47440211 failure reaches the JSON decoder as an incomplete prefix.
Current evidence cannot attribute truncation to a particular upstream stage.
The later complete response renders correctly; manufacturing missing content
or relaxing query identity is not a repair. Immediate ordinary retry is not
proven to help the user's sustained failure windows. The different post's
account-dependent failure is unclassified and has now recovered too.

First local research should distinguish framing/decompression failures from
a complete transport body containing incomplete JSON, using synthetic streams
and the saved payloads. A future authorized failing capture should preserve
bounded wire bytes, declared/actual lengths and safe cache/framing metadata
at the FIRST failure, without logging credentials or response text. Do not
repeat recovered-topic requests solely to obtain a negative result.

Any proposed request correction must be grounded in the ordinary operation's
observed contract. Do not substitute an invented endpoint, automatic account
rotation, hidden App fallback, arbitrary host switching or fabricated success.
If the upstream ordinary response remains unavailable, accurately surface
that limit; do not claim client code can restore absent remote bytes.

## Affected boundaries

- ArticleListPresenter: compatEnabled currently chooses two ordinary transport
  paths as well as enabling App fallback. Preserve account/generation ownership
  and foreground cancellation while unifying ordinary reading; deleting only
  the preference would revert to the legacy path rather than repair it.
- ArticleListModel / ArticleByteClient: remove online App request/dispatch after
  the ordinary fix is validated. Keep source-derived ordinary URL fields,
  explicit selected-account context and bounded decoding. Audit the legacy
  next-account Cookie retry; it must not silently substitute identity.
- ArticleConvertFactory / NormalArticleParser / ArticleFailure: preserve safe
  failure stage and correctly distinguish incomplete JSON, optional-field
  defects, transport and site rejection. Repair only evidenced behavior;
  retain source/query validation and original editable content.
- ArticleReaderSession / ArticlePage / ArticleListFragment: remove online App
  source adoption, fallback notices and compatibility-setting generation logic
  while retaining normal query, page, account and pending-anchor ownership.
- ArticleTabFragment and article_list_option_menu.xml: remove the manual
  internal-browser menu and click handler, alongside presenter's automatic
  showWithWebView path. Remove ForumWebFragment only after checking remaining
  callers. Account-login WebViews and local row HTML rendering remain separate.
- settings_lab.xml, strings.xml and shared preference keys: remove both feature
  settings and obsolete text/keys. Stale stored values must have no effect;
  do not clear unrelated settings.
- ArticleCache.kt / source metadata: existing saved App pages need a deliberate
  offline migration/read-compatibility boundary to preserve saved content.
  An offline legacy-format decoder must never authorize a new App request or
  expose a live compatibility mode. Determine migration mechanics before deletion.

## Integration and validation

R3/R4 and other parent-task work may already be modifying these files. Inspect
current changes and coordinate ownership before implementation; do not revert
other work to the earlier investigation snapshot. Existing compatibility specs
explain current code, but the latest user direction supersedes their retention
requirement for this scoped removal. Update those contracts with the final change.

Required behavior checks include complete/incomplete ordinary responses,
selected-account isolation, known site stops, valid/invalid wrappers, foreground
and background ownership, refresh/pagination/anchors, old preference values,
no online App requests, no browser launch from failure or the removed menu,
and preserved historical saved posts. Use synthetic data and local fakes.
The earlier successful App response is a control observation, not acceptance
for the new ordinary-only reader.

The project offline Android build/unit/lint gate applies after product edits.
No device operation, packaging/publication or unbounded live probing is implied.
Do not claim the final design is ready while the normal-path correction and
cache migration are still unspecified.
