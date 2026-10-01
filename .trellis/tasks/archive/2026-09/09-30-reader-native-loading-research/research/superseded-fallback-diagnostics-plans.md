# Superseded fallback and diagnostics proposals

The user clarified on 2026-09-30 that R7 must repair ordinary reading first,
then remove compatibility reading and the internal forum-browser feature.
The proposals below are historical evidence, not approved implementation scope.
They must not be used to reintroduce a hidden App API fallback or browser path.

## Historical prd.md

# 帖子原生读取稳定性与兼容降级原因排查

> 2026-09-30：用户已明确恢复 R7 根因研究，沿用本任务，保持 planning。新增链接 47440211 已直接复现普通响应截断；本例无需先做手机诊断即可进入恢复方案设计。R7-A 及产品修复尚未启动。见 [本例取证](47440211-truncated-response.md)。

## Goal

解决帖子经常无法正常在 App 内阅读、需要切换兼容接口或内置浏览器的问题。以用户提供的帖子为实际排查样例，找出失败原因后修正，不能仅隐藏提示或关闭兜底来宣称解决。

## Parent requirement

R7 in ../09-30-reader-access-location-navigation/prd.md。用户在规划过程中新增问题；现阶段调查与规划，未修改产品代码。

## User evidence

- 新增链接：https://bbs.nga.cn/read.php?page=1&tid=47440211；用户确认标题「每日全a拥挤度分享」，已与普通/兼容完整响应核对。
- 用户补充典型时间特征：一段时间内多次访问都会跳内置浏览器，过一段时间又恢复普通阅读。尚无精确持续时长或同一故障窗口内多份失败响应，不能预设一次立即重试就能恢复。
- 用户进一步确认另一篇帖子（未提供链接）的同一 App A→B→A 对照：原账号失败，其他账号正常原生阅读，马上换回原账号仍失败；随后这篇帖子也恢复原账号访问。用户已确认 .temp/cookies.txt 对应原账号，不再重复询问。47440211 也已恢复；两例分别记录，不能预设另一帖同样响应截断。见 [独立账号差异记录](account-dependent-observation.md)。
- 示例：https://bbs.nga.cn/read.php?page=1&tid=47647197。
- 用户描述近期经常需要兼容模式或内置浏览器，希望在 App 内正常打开。
- 用户已反复明确：当前 App 会自动跳到内置浏览器，非手动打开。
- 新增同样失败的示例：https://bbs.nga.cn/read.php?page=1&tid=47649423。
- 用户已完成开关对照：47649423 开启后正常原生显示；47649534 显示兼容模式；47649545 关闭时自动转浏览器、开启时显示兼容模式。
- 用户表示版本为当前项目最新版；域名按当前配置理解，未获得具体设置值。
- 用户给出 toast：“NGA 后台抽风了，请尝试点击右上角，使用内置浏览器打开”。对应 ArticleListModel.java:122 的旧链路 ServerException 文案。
- 用户明确授权使用 .temp 中的 Cookie 请求 App 读取接口；已经完成四帖普通/App 接口的有界对照。

## Confirmed findings

- 47440211：生产 ArticleByteClient 首次普通读取 HTTP 200，收到 11,915 字符的截断响应，在附件字段名中间结束，JSON 解码即失败；兼容响应可完整渲染19行。稍后同条件普通读取返回17,596字符，完整渲染19行；首次响应是后者的精确前缀。已定位本例的失败环节，但不能据此区分上游生成/缓存/传输中的具体截断来源，也不能断定此前四例同因。
- 兼容模式使用 app_api.php 获取数据，再由同一原生阅读界面渲染；本身仍属于 App 内阅读。
- 开启兼容模式的链路仅在 FORMAT 时尝试 App 接口；FORMAT/CONTENT/PROTOCOL 的终止失败可触发浏览器。
- 自动浏览器兜底默认开启，ArticleListPresenter.showWithWebView 会导航后 finish 原生页。
- 普通解析器的 broad catch 会将包含可选字段/行构造/渲染在内的异常统一折叠为 null，继而可能变成 FORMAT；目前不能看到具体失败阶段。
- 针对用户指定 tid，以访客身份、不携带 Cookie 对普通 read.php 数据接口只读取了一次：HTTP 403，Content-Type text/javascript; charset=GBK，736 bytes。未跟随重定向、重试、切源或使用账户凭据。
- 四帖普通/App 接口共八次有界请求均 HTTP 200；离线旧/新解析链路和完整 HTML 生成分别通过 20、6、11、2 行。另一次使用生产 ArticleByteClient/OkHttp（含 gzip）读取 47649545，HTTP 200，3 行解析通过；它是稍后的响应，不能视为原始响应的精确复放。
- 全渲染复放使用当前源文件、真实模板与渲染器，但以临时替身提供 Android 配置；未复现设备上的网络响应、配置、并发、发行包及界面行为。不能据此宣称已修复。
- 已确认用户提示对应旧链路：解析返回 null 且未识别出站点错误 → ServerException → 自动浏览器兜底。按当前代码，该提示不属于兼容模式开启的链路，也不等同于 HTTP 403。具体造成 null 的异常/响应仍未取得。
- 47649545 的开关对照将启用后的切源原因缩到普通链路 FORMAT → App 成功；该切源不由 403/未知 HTML/网络错误触发。FORMAT 仍可能来自 JSON、字段转换或渲染异常，不能直接猜定其中之一。

## Earlier proposal: R7-A diagnostics (not required before fixing a reproducible case)

以下保留此前无法复现时的最小诊断方案，适用于仍无法通过链接复现的情况。47440211 已取得失败响应，本例应先设计不完整响应的识别与有界恢复，不再将实现手机诊断作为前置条件。诊断方案可独立审核，不等待 R2 等产品决策，也不等于修复完成。

- 在实验室提供默认关闭的“帖子读取诊断”开关；开启后，失败提示或兼容成功提示附加有界诊断码，关闭时维持已有提示。
- 诊断区分 JSON/包装结构、页元数据、回复/作者等字段转换和正文渲染阶段。原始普通失败不能因兼容成功而丢失。
- 仅使用固定阶段、来源和白名单原因，不展示/保存/上传 Cookie、账号标识、正文或原始异常消息；不要求开启会打印原始响应的全局本地日志。
- 不因诊断新增请求、重试、切号、导航或权限；当前兼容开关与浏览器兜底行为维持现状。
- 验收：合成的不同失败能产生相应诊断码；同一失败不重复提示；普通成功无诊断提示；兼容成功保留原普通失败码；并发/迟到/换账号不串码。
- 尚未包含具体解析修复、默认开启兼容、浏览器 UX 调整或全部 R3 文案改造。拿到诊断码后再收敛根因修复方案。完成 R7-A 不能标记整个 R7 完成。

## Requirements (proposed)

1. 区分传输拒绝、响应格式、内容身份不匹配、可选字段异常、正文渲染异常及原生导航副作用。
2. 合法可读内容不应因无关可选字段损坏而丢失整页；目标帖子/账号/核心正文验证不能随之放松。
3. 能在原生阅读器显示的数据优先在原生界面处理，兼容接口继续提供原生阅读。
4. 对真正无法读取的情况，保留准确错误与手动恢复入口；不把关闭浏览器兜底当作修复。是否将自动浏览器改为用户手动选择待方案审核。
5. 诊断仅记录来源、HTTP 状态、处理阶段和有界原因；不记录 Cookie、原始响应或用户正文。
6. 恢复方案覆盖持续一段时间的普通源故障；优先保持 App 内可读，不能以一次立即重试作为已验证的解决方案，不能新增密集/无限普通源重试。

## Acceptance criteria

- [x] 有样例证据明确指出失败发生于哪一层：47440211 截断响应在 JSON 解码阶段失败；具体上游截断来源仍未确定。
- [ ] 对已确认的正常响应建立脱敏/合成回归样例，修复后可在原生界面读取。
- [ ] 可选数据缺陷不会造成无必要的整页降级；真正核心数据/访问错误不伪装成功。
- [ ] 同线程分页、刷新、账号切换与 source handoff 没有重复请求/重复跳转/错误楼层。
- [ ] 用户可明确知道失败原因，未验证的真实场景不会报告已修复。

## Dependencies

R7 与 R3 共享错误模型/提示，与 R4 共享读取及定位链路，应先确定 R7 原因，再整合 R3 和 R4；不能并行改写同一 Presenter/Parser。
不依赖 R1 nga 协议注册。R1 解决唤起入口，R7 解决进入后的数据读取。

## Open evidence

47440211 的链接复现已取得失败响应和完整响应对照，无需先要求用户安装诊断版本。下一步收敛不完整响应的识别和有界恢复：一次同查询/同账号/同源重读可作为候选，但稍后重读成功不证明立即重试有效，需先明确预算与间隔。R7-A 保留用于其他未复现情况；自动浏览器 UX 仍是独立产品决策。未启动产品实施，不扩大真实请求范围。


## Historical design.md

# Native-reader design — truncated-response recovery to be reviewed

> 2026-09-30：47440211 已直接复现普通响应截断，本例恢复设计无需以 R7-A 为前置条件。下方 R7-A 是此前诊断提案，尚未实施。最新证据见 [本例取证](47440211-truncated-response.md)。

## Current evidence and recovery design boundary

The first ordinary 47440211 response is a truncated exact prefix of a later
complete response. JSON parsing fails before any row or renderer work; App and
the later ordinary response both render 19 rows. Do not loosen DTO validation
or fabricate missing JSON/content to accept the incomplete page.

Review a bounded incomplete-response recovery policy before implementation.
One same-query/account/origin foreground ordinary retry is a candidate, with
explicit pacing and a shared budget before existing enabled App fallback.
Later success does not prove immediate retry efficacy. Access/auth/rate-limit
stops, cancellation, stale generations, source adoption and cache eligibility
must remain intact. Do not add a generic FORMAT retry or background recovery.
This is additional proposed scope beyond R7-A's unchanged-request diagnostic
contract; it has not been activated or implemented.

The user reports repeated failures throughout a time window before spontaneous
recovery. Do not adopt immediate ordinary retry as the primary solution without
evidence. Prioritize keeping readable native content available through an
eligible working App source; any default-setting/browser policy changes need
an explicit design decision. No HTTP response cache or timed topic-failure
lockout was found in the inspected online reader. Upstream persistent bad
response/cache delivery is a hypothesis to investigate with bounded response
and freshness metadata, not a confirmed CDN diagnosis.

The user confirmed same-App A-B-A failure/success/failure on ANOTHER post,
whose URL is pending. 47440211 has recovered under the original account.
Keep those observations separate: account/session correlation is reported
for the new post, but its transport and parser cause is unknown. Do not
attribute the captured 47440211 truncation to this new case without its own
response evidence. See research/account-dependent-observation.md. Automatic
account rotation is not a proposed recovery mechanism.

The R7-A sections below are retained for still-unreproduced cases. They do not
block designing recovery for the newly reproduced response-truncation case.

Separate source/transport/parser/row-rendering errors from display and navigation. Preserve account/query identity checks and bounded no-loop source fallback. The actual correction depends on the sample's demonstrated failure stage.

Do not use one generic FORMAT outcome for unrelated metadata or rendering bugs. An isolated optional-field failure may omit that field with an incomplete indication; a wrong topic or unavailable source remains a real error. Preserve original content for editing.

Review automatic browser routing independently of content parsing. Proposed behavior is retaining a native error state with explicit recovery actions when neither native source can provide content. This UX change requires final review; disabling fallback alone is not a content-loading fix.

R3 owns accurate cause/status presentation; R7 owns identifying and repairing the actual read failure. R4 consumes the resulting validated query/page state. Sequence integration to avoid conflicting parser/Presenter changes.

No persistent raw-response logging. Keep coarse diagnostic stage, source, status and nonsecret correlation only. Do not rotate accounts or retry 403 to obtain a different outcome.

## Evidence update — four samples and same-device switch comparison

The user confirmed automatic navigation and the legacy ServerException toast. ArticleListModel.java:112–122 maps an unclassified parser null to that exception; ArticleListPresenter.java:267–270 then allows the browser, and :346–354 navigates/finishes. This is a confirmed trigger chain, not the underlying parse-failure cause. With the current code this toast identifies the compatibility-disabled path.

Both authenticated sources for all four samples returned HTTP 200. Current source parser plus full native HTML generation replay accepted them (20, 6, 11 and 2 rows), with Android settings substituted in the host harness. A later one-call production ArticleByteClient/OkHttp gzip probe for 47649545 also parsed successfully (3 rows). The user confirmed that same post switches from automatic browser navigation with compatibility disabled to native compatibility with it enabled. That source transition pins a normal FORMAT result, but none of the captured host samples reproduces it. A fix must not assume a payload-shape defect or deletion without new evidence.

Since the device comparison identifies the source transition but not the swallowed exception, first preserve a bounded failure stage around body classification, JSON conversion, row conversion and rendering. Store an allowlisted category/exception class or fixed diagnostic code, never raw exception messages, bodies, Cookie or account identifiers. Keep existing legacy and scoped behavior boundaries explicit while investigating; replacing the legacy fallback with silent source/account retries is outside this design.

## R7-A review boundary

This first delivery adds opt-in diagnostics and demonstrates that safe failure context survives the legacy/scoped/fallback paths. It does not repair an unobserved cause, change request strategy, or decide the later automatic-browser UX. The overall R7 acceptance remains open after R7-A. Other parent-task product decisions do not affect this narrow delivery.

### Data flow and ownership

1. Add a small immutable diagnostic value with source, fixed stage and an allowlisted reason. Suggested stages: body/wrapper, JSON decode, page metadata, row conversion, author/comment/attachment conversion, body rendering. Suggested reasons: malformed JSON, unexpected type, invalid number, missing value, invalid structure, other exception. The public code derives only from these enums; do not include raw messages, field values, URLs, IDs, response fragments or Throwable objects.
2. ArticleConvertFactory's diagnostic-aware entry must capture the stage before work and preserve it on each null/exception exit. Existing callers without diagnostics retain the old API and behavior. Use request-local values/callbacks or a typed result; never a static mutable last error, thread-local that outlives the request, or a mutable collector shared across pages. Keep strict ArticleFailure kind intact.
3. NormalArticleParser must propagate the safe diagnostic when null becomes FORMAT. The legacy ArticleListModel path carries it in its ServerException without substituting that diagnostic for an actual recognized site error. Existing public strings remain unchanged when the diagnostic preference is off.
4. ArticleListPresenter retains the original normal diagnostic only within the current read attempt. When App succeeds, append that original code to the existing compatibility notice; when terminal loading fails, append the current relevant code to the existing error. Do not add a second toast. Existing generation/account/foreground guards must run before presenting it, and a later successful ordinary read clears the previous attempt's diagnostic.
5. Add “帖子读取诊断” to Settings → 实验室 with default false. It controls presentation only and requires no permission. It must not enable the existing global “本地日志” feature, which can print bodies through shared converters. Do not persist the diagnostic itself or add upload/telemetry; the user can report the short toast code from a reproduction.

### Compatibility and validation

All request counts, fallback eligibility, selected source, account behavior, cancellation, cache identity, navigation and automatic-browser preference retain their current contracts. Synthetic tests must show the same legacy/scoped outcome before/after adding diagnostic metadata. Include normal success, malformed wrapper/JSON, invalid required fields, a throwing renderer, and two independent in-flight attempts. Confirm that a sentinel credential/body embedded in an exception message never appears in the diagnostic representation.

For success after fallback, report the original normal failure code once and do not reuse it after generation/account change. Unknown HTML/access/network errors do not gain App eligibility. Do not catch fatal VM Errors merely to generate a diagnostic.

### Rollback and limits

The new preference is off by default; disabling it removes visible diagnostics. The diagnostic value/propagation/UI additions should form one reviewable change without parser repair or default-policy changes. Host unit tests verify stage propagation and privacy, not reproduction of the user's device issue. Device installation and live reproduction remain user actions; no ADB operation is authorized. Exact repair scope is chosen only after actual failure-stage evidence arrives.


## Historical implement.md

# Investigation and execution plan

> 2026-09-30：用户已恢复 R7 根因研究；新链接47440211已用生产请求复现截断响应，并完成离线定位。保持 planning，未启动下述诊断/修复实施。

- [x] Record sample URL and distinguish URI entry (R1) from reader failure (R7).
- [x] Trace default-off legacy and enabled scoped chains through fallback and browser navigation.
- [x] Perform exactly one anonymous normal-data observation for the user-specified example; stop on HTTP 403.
- [x] Obtain exact App toast/result: automatic browser navigation with the legacy “NGA后台抽风了” message; add tid=47649423 as a second sample.
- [x] Use explicitly authorized local Cookie for bounded ordinary/App endpoint comparisons, one request per source per sample, with no retry/redirect/account switch: all four returned HTTP 200.
- [x] Replay both samples offline through exact JSON library, old/new parsers and full HTML rendering with substituted Android configuration: 20/6 rows accepted. Record host/device limits.
- [x] Compare the same device/sample: 47649423 becomes natively readable; 47649534 shows compatibility; 47649545 automatically opens browser when disabled and shows native compatibility when enabled.
- [x] Add two further bounded source comparisons and offline replays (11/2 rows), plus one production ArticleByteClient/OkHttp gzip probe (3 rows). All pass on the host; nine authenticated network requests total. Stop requesting new sample URLs.
- [x] Demonstrate the failure layer: 47440211 ordinary response ends inside an attachment key and fails JSON decoding; App and later complete ordinary responses pass. Preserve exact-prefix comparison and synthetic truncation replay in research/47440211-truncated-response.md.
- [x] Extend saved-response replay to 96 source/settings combinations and reproduce seven synthetic failure cases; record them in research/resumed-offline-investigation.md without claiming the device cause is reproduced.
- [ ] Review incomplete-response recognition and native recovery across the user's sustained ordinary-source failure window. Do not assume one immediate retry fixes it; define any retry pacing/budget and avoid delaying eligible App recovery. R7-A is optional for still-unreproduced cases. Coordinate R3/R4 shared files without blocking their independent scope.
- [ ] After approval, implement the smallest validated parser/reader correction and regression test.
- [ ] Verify source/account/generation, unknown/malformed optional fields, rendering failures, request counts and terminal navigation.
- [ ] Run focused tests plus the parent's required Android offline quality gate.
- [ ] Report verified behavior separately from live/device scenarios not reproduced.

## R7-A implementation gate and ordered plan

The following is the earlier optional diagnostic delivery, retained for review.
47440211 now has direct failure evidence; R7-A is not its prerequisite.
No product code or task activation has occurred. A diagnostic approval would
authorize only this bounded delivery, not the new recovery-policy candidate
or browser UX changes.

1. Review the final R7-A summary, then activate this task for the approved first delivery. Dispatch a Trellis implement agent with exclusive ownership of the diagnostic value, ArticleConvertFactory, NormalArticleParser, ArticleFailure/ArticleListModel propagation, ArticleListPresenter notice composition, SettingsLab preference/resources and focused tests. It is not alone in the repository and must preserve others' edits.
2. Read complete reader/failure/lifecycle contracts and relevant frontend settings/state guidelines. The Android quality spec exceeds automatic injection size; explicitly read its remaining sections instead of relying on truncated injected text.
3. Implement request-local immutable diagnostics with fixed stage/reason codes and backward-compatible parser entry points. Record normal null exits and allowed exceptions without retaining Throwable messages or response values. Preserve current exception classification/fallback decisions.
4. Carry original normal failure across one allowed App fallback, combine it with the existing single compatibility/error notice, and honor generation/account/foreground checks. Ordinary success and new attempts must not retain an old code.
5. Add the default-off laboratory preference. No storage of diagnostic history, network log dump, upload, new permission or global debug logging.
6. Add focused tests for stage distinction, null-wrapper paths, typed failure identity, successful render, throwing renderer, concurrent attempts, fallback success and stale completion. Use synthetic bodies and secret sentinels, not captured payloads. Assert unchanged request/fallback behavior and no secret text in codes.
7. Run targeted tests, then the parent's required offline build/unit/lint gate. Dispatch Trellis check for the entire first-delivery diff. Fix findings within this scope.
8. Present the diagnostic code mapping and how the user can enable it for a single reproduction. No ADB/install is implied. Keep R7 unfinished pending device evidence; use that evidence to converge the actual repair scope and later UX choice.

Rollback: revert only the coherent R7-A diagnostic change or turn its preference off. No database/cache migration is involved.

Ownership: reader Parser/Presenter transport outcomes and dedicated tests; coordinate R3 failure UI and R4 launch/anchor work. User-authorized Cookie comparisons are complete. No publication, device operation, unrelated credential access or unbounded additional live requests are implied by this plan.


