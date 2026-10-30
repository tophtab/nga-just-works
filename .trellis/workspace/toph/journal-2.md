# Journal - toph (Part 2)

> Continuation from `journal-1.md` (archived at ~2000 lines)
> Started: 2026-10-01

---



## Session 75: 精简非必要测试并准备 6.2.1 发布
<!-- trellis-session: v=2 fp=291d19674c367dbd -->

**Date**: 2026-10-01
**Task**: 精简非必要测试并准备 6.2.1 发布
**Branch**: `main`

### Summary

完成两轮测试精简：删除8项测试与2个文件，减少232行测试代码；553项JVM测试、32项Python测试及13模块lint通过。正式版6.2.1发布说明及标签校验通过，归档后推送main和正式标签触发CI。

### Git Commits

| Hash | Message |
|------|---------|
| `e5e23287` | test: remove obsolete and redundant tests for 6.2.1 |

### Status

[OK] **Completed**

### Next Steps

- Push main and annotated 6.2.1 tag; CI publication is not polled under project policy.


## Session 76: Compatibility reader display parity and 6.2.4 release
<!-- trellis-session: v=2 fp=d57e0b02f2dd0958 -->

**Date**: 2026-10-02
**Task**: Compatibility reader display parity and 6.2.4 release
**Branch**: `fix/compat-reader-display-parity`

### Summary

Implemented default-style support count, attachments and nested comments in compatibility reading; preserved main-post identity/actions, source text, default output and shared IP-location flow. Independent review fixed alterinfo fallback and transient display-state serialization. Final gate passed 562 tests and 13 lint reports with zero Error/Fatal. User authorized commit/push/finish-work and stable 6.2.4; notes validated; main/tag push follows bookkeeping.

### Git Commits

| Hash | Message |
|------|---------|
| `2e2a0a70` | fix: align compatibility reader with default display |

### Status

[OK] **Completed**


## Session 77: Restore floor favorites and release 6.2.5
<!-- trellis-session: v=2 fp=69f6cc222438ce8c -->

**Date**: 2026-10-03
**Task**: Restore floor favorites and release 6.2.5
**Branch**: `fix/restore-floor-favorite`

### Summary

Researched issue #10 and original favorite behavior, then restored both original floor favorite menu entries and the existing clicked-row tid/pid call. Preserved navigation and server APIs. Full gate passed 562 JVM tests and 13 lint reports with zero Error/Fatal; Debug packaging passed. User approved commit, archive and stable patch 6.2.5. Release notes and versionCode 60205000/tag verification passed; main/tag push follows bookkeeping without CI polling per project policy.

### Git Commits

| Hash | Message |
|------|---------|
| `090dce71` | fix: restore floor favorite action |

### Status

[OK] **Completed**


## Session 78: Reader tab desync when leaving the last page
<!-- trellis-session: v=2 fp=b0bc3e9599bfafa8 -->

**Date**: 2026-10-24
**Task**: Reader tab desync when leaving the last page
**Branch**: `fix/reader-tab-desync`

### Summary

Diagnosed via decompiled ViewPager 1.0.0 / RecyclerTabLayout 1.5.0: a foreground page result adopted during the settle rewrote the reader selection because the outgoing fragment pauses only at the end of the scroll, and the non-animated jump back dispatched (position, 0f), which crosses no tab threshold. Gated adopt's currentPage write on key.page == state.currentPage and moved state-driven jumps through TabLayoutEx.setCurrentItem. Two fail-first reader tests (expected 6 but was 7 / 13); full Debug gate 576 tests and 13 lint reports clean; visual half left unverified per opt-in ADB policy. Specs updated: reader selection ownership + page-tab sync rule.

### Git Commits

| Hash | Message |
|------|---------|
| `d6f90812` | fix: keep the reader selection when a late page result arrives |

### Status

[OK] **Completed**


## Session 79: 修复列表页页码泄漏为帖子页坐标 (#14)
<!-- trellis-session: v=2 fp=13818c6bc580ace5 -->

**Date**: 2026-10-27
**Task**: 修复列表页页码泄漏为帖子页坐标 (#14)
**Branch**: `main`

### Summary

普通读取采用响应 __PAGE/__R__ROWS_PAGE 作为有效页与页大小，列表入口固定第 1 页；越界请求不再折叠为单页。无 __PAGE 行为不变，旧版缓存回放保留存储页。全量 Debug 539 例与 lint 通过；机型手动确认未执行。

### Git Commits

| Hash | Message |
|------|---------|
| `b0fdf46c` | fix: 列表页页码不再泄漏为帖子页坐标 |

### Status

[OK] **Completed**


## Session 80: 修复手机误触发平板分栏并精简设置项说明
<!-- trellis-session: v=2 fp=98b65ecd2400f2bf -->

**Date**: 2026-10-30
**Task**: 修复手机误触发平板分栏并精简设置项说明
**Branch**: `fix/tablet-split-phone-trigger`

### Summary

手机开启「平板分栏」后点板块会先按 Activity Embedding 分栏启动（右侧滑入、首页停留）再回退全屏并闪一下。根因是规则在所有设备无条件注册，而规则尺寸条件只在启动分栏后校验。改为注册前按当前 Configuration 判断（≥840dp 宽、≥600dp 短边、横屏；AndroidX 把 height>=width 视为竖屏），不满足就 clearRules；配置变化时重评估并用状态签名去重。手机上隐藏开关，并按反馈移除预测返回手势/平板分栏/重置表情顺序/AI 测试连接的副标题，首页布局保留平铺/紧凑回显。

### Main Changes

- TabletSplit 增加 canSplit/isTabletDevice 注册前门槛与签名去重
- NgaClientApp 任意配置变化重新评估分栏规则
- 手机上隐藏「平板分栏」，清理四处设置副标题
- 新增 TabletSplitEligibilityTest 与 Activity Embedding 分栏 spec

### Git Commits

| Hash | Message |
|------|---------|
| `e6308b9a` | fix: 手机不再误触发平板分栏过渡 |
| `cc6943c7` | fix: 恢复首页布局的当前选项文字 |

### Testing

- [OK] testDebugUnitTest + lintDebug 全量通过（JDK 17，lint 0 Error/0 Fatal）
- [OK] 小米 15 实机 6.3.2-debug.3：不再分栏滑入/闪回，开关隐藏，设置项符合预期
- [OK] 平板端未实机验证（按项目策略无设备授权）

### Status

[OK] **Completed**

### Next Steps

- 合并 PR #16 后发布 6.3.3
- 平板端补一次横屏分栏回归（含旋转与折叠）


## Session 81: ApiResult 错误模型落地
<!-- trellis-session: v=2 fp=4931e46ae4ddf828 -->

**Date**: 2026-10-30
**Task**: ApiResult 错误模型落地
**Branch**: `feat/api-result-error-model`

### Summary

借鉴上游 5.1.x 的失败分类结构，在 lib_base_network 引入 sealed ApiResult/ApiError 与 apiSafeCall，并让消息 PagingSource 携带可读错误原因。

### Main Changes

- 新增 ApiResult/ApiError/BusinessException/apiSafeCall 与 20 个单测
- 消息列表/详情 Repository 接入 apiSafeCall，空列表仍按分页错误处理
- 更新网络契约、操作登记表与 SOURCE_LEDGER

### Git Commits

| Hash | Message |
|------|---------|
| `1b7c14a8` | docs(task): plan ApiResult error model and message layering |
| `0056e3e7` | feat(network): add shared ApiResult failure model |

### Testing

- [OK] lib_base_network 20 tests / lib_bu_message 5 tests / MessageParserGoldenTest 3 tests 全部通过
- [OK] 全量 testDebugUnitTest + lintDebug：648 tests，lint 0 error

### Status

[OK] **Completed**

### Next Steps

- 开始子任务 10-30-message-module-layering：消息三页分层重构


## Session 82: 消息模块分层重构
<!-- trellis-session: v=2 fp=ca7e508cd081e47f -->

**Date**: 2026-10-30
**Task**: 消息模块分层重构
**Branch**: `feat/api-result-and-message-layering`

### Summary

把私信列表/详情/发送三页拆成 Activity/Screen/ViewModel/Repository/Parser 五层，删除 285 行 MessageConvertFactory，默认与 Miuix 两套外观保持不变。

### Main Changes

- 三页分层，解析器纯函数化并返回 ApiResult
- 详情标题/收件人改为导航参数，收件人按上游取会话发起人
- 发送请求每次重建 query/form map，移除共享可变 map
- 迁移 golden 断言并补充 R9 Repository 透传测试
- 删除 MessageConvertFactory 与旧 compose 目录，更新 manifest、路由常量、spec 与 SOURCE_LEDGER

### Git Commits

| Hash | Message |
|------|---------|
| `4595709e` | refactor(message): layer the three message pages under ApiResult |

### Testing

- [OK] lib_bu_message 29 tests；app 模块 549 tests；assembleDebug 通过
- [OK] lintDebug：13 个模块 0 Error/Fatal
- [OK] 真机两套外观走查未运行（无当次 ADB 授权）

### Status

[OK] **Completed**

### Next Steps

- 如需合并，开 PR；父任务 10-30-api-result-and-message-layering 已 2/2 待归档
