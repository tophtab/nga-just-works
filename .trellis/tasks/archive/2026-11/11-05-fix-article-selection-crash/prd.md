# 修复正文选词工具栏在 MIUI 13 上导致崩溃

## Goal

停止应用层接管 WebView 选词菜单（`startActionMode` 包装 + `menu.clear()` 重建），改用
`WebSettings.setDisabledActionModeMenuItems(MENU_ITEM_SHARE | MENU_ITEM_PROCESS_TEXT)`，
让 Chromium 自己提供「复制 / 全选 / 网页搜索」，消除 issue #20 中 MIUI 13 / Android 12
长按选中正文即崩溃的问题。

## Background

- Issue #20（Xiaomi 12X / MIUI 13 / Android 12）：选中正文大概率崩溃，6.3.4 上变为必然。
- 应用层唯一在选词时执行的代码是 5.0.0 起生效的菜单接管（`e46a1b09`）：
  `LocalWebView` 覆写 `startActionMode`，`ArticleSelectionActionModeCallback` 在
  `onCreateActionMode` / `onPrepareActionMode` 里 `menu.clear()` 后重建三项菜单。
- 该接管只在小米 15（HyperOS）上验证过「分享按钮消失」，**从未在 MIUI 13 上验证过
  稳定性**；5.0.0 到 6.3.4 之间选词代码没有改动，频率变化更像系统 WebView 版本差异。
- 5.6.0 与 6.3.4 都包含这段代码；崩溃栈尚未取得，本任务按「移除崩溃路径上的全部自研
  代码」处理，同时保留产品要求的菜单形态。

## Requirements

- R1. `LocalWebView` 的 WebSettings 必须调用
  `setDisabledActionModeMenuItems(WebSettings.MENU_ITEM_SHARE | WebSettings.MENU_ITEM_PROCESS_TEXT)`；
  不禁用 `MENU_ITEM_WEB_SEARCH`，正文选词菜单由 Chromium 提供「复制 / 全选 / 网页搜索」。
- R2. 删除应用层选词接管：`LocalWebView` 的两个 `startActionMode` 覆写与
  `wrapSelectionCallback`、`ArticleSelectionActionModeCallback.java`、
  `ArticleSelectionText.java`。
- R3. 删除只服务于旧接管的资源与测试：`ids.xml` 里的
  `menu_article_selection_copy` / `_select_all` / `_search`，
  `ArticleSelectionActionModeContractTest.kt`、`ArticleSelectionTextTest.java`。
- R4. 复制、全选、搜索完全交给 Chromium；不再使用 `evaluateJavascript`、剪贴板写入或
  `ACTION_WEB_SEARCH` 的自研实现。
- R5. 作用域仅限 `LocalWebView`（正文与设置页字号预览）；其它使用原生 `WebView` 的页面
  （个人资料、内置网页、登录、关于）行为不变。
- R6. 更新 `.trellis/spec/frontend/component-guidelines.md` 的
  「Article WebView text selection」一节：记录官方 API 契约、禁止再次
  `menu.clear()` / 重建 Chromium 菜单，以及厂商注入项回潮时的 remove-only 兜底。
- R7. 不新增依赖；`minSdk 29`、`setDisabledActionModeMenuItems`（API 24+）满足兼容范围。
- R8. 菜单项文案与顺序由 Chromium / 系统本地化决定；接受「网页搜索」而不是「搜索」，
  且设备没有网页搜索处理程序时该项会隐藏。

## Acceptance Criteria

- [ ] `LocalWebView` 设置 `MENU_ITEM_SHARE | MENU_ITEM_PROCESS_TEXT`，且不再包含
      `startActionMode` / `wrapSelectionCallback`。
- [ ] `ArticleSelectionActionModeCallback.java`、`ArticleSelectionText.java` 已删除。
- [ ] `ArticleSelectionActionModeContractTest.kt`、`ArticleSelectionTextTest.java` 已删除。
- [ ] `res/values/ids.xml` 不再声明三个 `menu_article_selection_*` id。
- [ ] 非归档源码内 `grep` 不到 `ArticleSelectionActionModeCallback`、
      `ArticleSelectionText`、`menu_article_selection_`。
- [ ] `component-guidelines.md` 选词一节已改写，并记录 remove-only 兜底约束。
- [ ] `./gradlew testDebugUnitTest lintDebug --continue --console=plain` 通过，
      所有 Android 模块 lint Report 为 0 Error / 0 Fatal。
- [ ] `./gradlew :nga_phone_base_3.0:assembleDebug` 通过。
- [ ] 真机验收（由 issue 反馈者完成，本机无设备连接）：在 Xiaomi 12X / MIUI 13 /
      Android 12 上长按正文出现「复制 / 全选 / 网页搜索」，不崩溃，三项均可用。

## Out Of Scope

- 取得反馈者的 logcat；若移除接管后仍崩溃，再单开任务分析 Chromium 侧。
- 删除 MIUI 识屏/传送门等厂商注入项：本轮接受其可能回潮，只有确认回潮且产品不接受时
  才追加 remove-only 过滤器。
- 修改已归档的选词任务与历史 release notes。
- 为其它 WebView 增加或修改选词行为。
- 版本号、发布流程与 release notes 更新。

## Risks

- **R-1（中）**：崩溃栈尚未取得。本方案移除了选词路径上的全部自研代码，若崩溃仍发生，
  说明根因在 Chromium / MIUI 实现，需要日志后重新定位。
- **R-2（低）**：厂商注入项（识屏/传送门）可能重新出现；兜底方案只能 remove-only，
  绝不能退回 `menu.clear()` 重建。
- **R-3（低）**：WebView 内可编辑控件的选词菜单同样失去分享与 process-text 项；
  正文场景无编辑控件，可接受。
- **R-4（低）**：无网页搜索处理程序的设备不再显示搜索项（旧实现显示但点击无效）。
