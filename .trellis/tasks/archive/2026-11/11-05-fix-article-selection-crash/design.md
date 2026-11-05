# 技术设计：用官方 API 取代 WebView 选词菜单接管

## 1. 作用域与触发路径

正文渲染路径唯一：`ArticleListAdapter.onBindContentView()` → `LocalWebView`。
`LocalWebView` 是 `gov.anzong.androidnga.common.view.WebViewEx` 的唯一子类；其它页面
直接使用原生 `WebView`。设置页字号预览的 `fragment_settings_size.xml` 也使用
`LocalWebView`，会同步获得新行为，可接受（R5）。

选词工具栏由 Chromium `SelectionPopupControllerImpl` 通过
`mView.startActionMode(callback, TYPE_FLOATING)` 拉起，`mView` 即 WebView 自身。
旧实现正是在这里包装回调并重建菜单。

## 2. 方案：只使用 WebSettings 官方开关

在 `LocalWebView.setLocalMode()`（`initialize()` 调用）里，紧跟
`WebSettings settings = getSettings();` 之后加入：

```java
settings.setDisabledActionModeMenuItems(
        WebSettings.MENU_ITEM_SHARE | WebSettings.MENU_ITEM_PROCESS_TEXT);
```

- 对不可编辑选区，Chromium 默认菜单为：复制、全选、分享、网页搜索、第三方
  `ACTION_PROCESS_TEXT` 项。
- 禁用 `SHARE` + `PROCESS_TEXT` 后只剩「复制 / 全选 / 网页搜索」，与产品要求的
  「复制 / 全选 / 搜索」等价（文案由系统本地化）。
- 复制、全选、搜索全部沿用 Chromium 的 item id 与处理逻辑，不再需要
  `evaluateJavascript`、`ClipboardManager`、`Intent.ACTION_WEB_SEARCH`。
- `MENU_ITEM_WEB_SEARCH` 不禁用；Chromium 只在存在 `ACTION_WEB_SEARCH` 处理程序时
  显示该项（`AwActionModeCallback.isWebSearchAvailable()`）。

### 兼容性

`WebSettings.setDisabledActionModeMenuItems` 自 API 24 起提供，`minSdk 29` 覆盖。
该开关由 Chromium 的 `AwContents.isSelectActionModeAllowed` 消费，属 WebView 的
稳定公开 API，不依赖厂商 ActionMode 实现。

## 3. 删除清单（消除崩溃路径）

| 文件 | 动作 |
|------|------|
| `sp/phone/view/webview/LocalWebView.java` | 删除两个 `startActionMode` 覆写、`wrapSelectionCallback`、`ActionMode` import；新增 settings 调用 |
| `sp/phone/view/webview/ArticleSelectionActionModeCallback.java` | 删除 |
| `sp/phone/view/webview/ArticleSelectionText.java` | 删除 |
| `src/test/.../ArticleSelectionActionModeContractTest.kt` | 删除 |
| `src/test/.../ArticleSelectionTextTest.java` | 删除 |
| `src/main/res/values/ids.xml` | 删除三个 `menu_article_selection_*` |
| `.trellis/spec/frontend/component-guidelines.md` | 改写「Article WebView text selection」一节 |

删除后，选词路径上不再执行任何本仓库代码；旧实现的三个风险点（`menu.clear()`、
每次 invalidate 重建、`onGetContentRect` 按 WebView 版本转发）同时消失。

## 4. 备选方案与被否原因

- **B：保留重建，增加 `hasExpectedItems` 守卫、未变化时 `return false`。**
  只减少 invalidate 时的重建次数；首次 `onCreateActionMode` 仍 `clear()` + 自建
  item，仍由应用承担三个动作。若 MIUI 崩在首次创建，B 修不好。保留为对照实验，
  不作为最终方案。
- **C：`onGetContentRect` 防御 + destroy 前结束 ActionMode。**
  只是兜底，不消除触发面；方案 A 删除 wrapper 后 `onGetContentRect` 部分自然消失。
- **A 变体：保留 wrapper，只 remove 不需要的 item（不 clear）。**
  在厂商注入项必须清除时才启用。遍历菜单按标题白名单删除是脆弱点，且仍引入应用层
  回调；不作为首选。

## 5. 厂商注入项回潮的兜底

若真机确认 MIUI 识屏/传送门等条目重新出现且产品不接受，追加**仅删除**的
`ActionMode.Callback2` 包装。注意系统智能选择项走的是
`android.R.id.textAssist`（Chromium `updateAssistMenuItem` 添加），不受
`MENU_ITEM_*` 开关控制；若回潮来源是该 id，可直接
`menu.removeItem(android.R.id.textAssist)`，这是公开框架 id，无需匹配文案。

- `onCreateActionMode` / `onPrepareActionMode` 先调用 delegate；
- 之后只 `menu.removeItem(...)` 非白名单项；
- **禁止** `menu.clear()`、禁止新增自实现 item、禁止自定义 id；
- 不得在 `onPrepareActionMode` 里无条件返回 `true` 触发额外 invalidate。

该兜底需单独评审后才能落地。

## 6. 测试策略

- `WebSettings` 在 JVM 单测中不可用（无 Robolectric），本改动没有可执行的 JVM 行为
  测试。
- 按 `.trellis/spec/backend/android-quality-guidelines.md`，纯源码拼写断言的
  `ArticleSelectionActionModeContractTest` / `ArticleSelectionTextTest` 随被取代的
  实现一起删除，不再新增同类 spelling test。
- 验证以 Debug gate（编译 + 单测 + lint）+ `assembleDebug`（资源删除需打包验证）
  为准；行为验收依赖反馈者在 12X 上确认。

## 7. 回滚

改动集中在一次提交，回滚即恢复旧接管实现。无数据迁移、无持久化变化。

## 8. 风险

见 `prd.md` Risks。最关键的是 R-1：崩溃栈尚未取得；本设计通过「删除选词路径上的全部
自研代码」把应用侧可能性降到零，若仍复现则根因在 Chromium / MIUI。
