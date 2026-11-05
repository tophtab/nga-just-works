# Implement — 用官方 API 取代 WebView 选词菜单接管

按顺序执行。步骤 1 是行为替换，步骤 2–4 是删除，步骤 5 是契约文档。每一步后都能
独立编译；删除步骤全部完成后才跑完整 gate。

## Step 1 — `LocalWebView` 改用官方开关

文件：`nga_phone_base_3.0/src/main/java/sp/phone/view/webview/LocalWebView.java`

- [ ] 在 `setLocalMode()` 里紧跟 `WebSettings settings = getSettings();` 加入：
      ```java
      settings.setDisabledActionModeMenuItems(
              WebSettings.MENU_ITEM_SHARE | WebSettings.MENU_ITEM_PROCESS_TEXT);
      ```
      放在 `setJavaScriptEnabled(true)` 之后、`setTextZoom` 之前均可；保持与其它
      settings 调用同一段落。
- [ ] 删除 `startActionMode(ActionMode.Callback)` 与
      `startActionMode(ActionMode.Callback, int)` 两个覆写。
- [ ] 删除 `wrapSelectionCallback(...)` 及其 Javadoc。
- [ ] 删除 `import android.view.ActionMode;`。
- [ ] 确认 `LocalWebView` 不再引用 `ArticleSelectionActionModeCallback`。

**Gate 1**：`grep -n "startActionMode\|ActionMode" LocalWebView.java` 无输出；
单独编译该模块（见下方 Gate 命令，可先只跑 `:nga_phone_base_3.0:compileDebugJavaWithJavac`）。

## Step 2 — 删除被取代的实现

- [ ] 删除 `nga_phone_base_3.0/src/main/java/sp/phone/view/webview/ArticleSelectionActionModeCallback.java`
- [ ] 删除 `nga_phone_base_3.0/src/main/java/sp/phone/view/webview/ArticleSelectionText.java`
- [ ] 全仓 `grep` 确认非归档源码无引用（归档任务与 journal 保持原样）。

**Gate 2**：`./gradlew :nga_phone_base_3.0:compileDebugJavaWithJavac` 通过。

## Step 3 — 删除旧测试

- [ ] 删除 `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionActionModeContractTest.kt`
- [ ] 删除 `nga_phone_base_3.0/src/test/java/sp/phone/view/webview/ArticleSelectionTextTest.java`
- [ ] 不新增替代的源码拼写测试（见 `design.md` §6 与 android-quality-guidelines）。

**Gate 3**：`./gradlew :nga_phone_base_3.0:compileDebugUnitTestJavaWithJavac` 通过。

## Step 4 — 删除无用资源 id

文件：`nga_phone_base_3.0/src/main/res/values/ids.xml`

- [ ] 删除：
      - `menu_article_selection_copy`
      - `menu_article_selection_select_all`
      - `menu_article_selection_search`
- [ ] 保留 `tag_1` / `tag_2` / `tag_3` / `status_bar` / `miuix_*`。

**Gate 4**：`./gradlew :nga_phone_base_3.0:processDebugResources` 通过。

## Step 5 — 更新规范

文件：`.trellis/spec/frontend/component-guidelines.md`，「Article WebView text selection」一节。

- [ ] 删除「覆写 `startActionMode` / 重建菜单 / 自有 id / `evaluateJavascript`」的旧契约。
- [ ] 写入新契约：
      - 用 `WebSettings.setDisabledActionModeMenuItems(MENU_ITEM_SHARE | MENU_ITEM_PROCESS_TEXT)`
        保留 Chromium 的复制 / 全选 / 网页搜索；
      - 禁止覆写 `startActionMode`、禁止 `menu.clear()` 重建 Chromium 菜单；
      - 文案、顺序、搜索项可用性由 Chromium / 系统决定；
      - 若未来必须去掉厂商注入项，只允许 remove-only 包装，且需单独评审。
- [ ] 记录验证要求：该行为无法用 JVM 单测覆盖，必须真机长按正文确认。
- [ ] 检查同文档其它位置是否还有旧实现的描述（grep
      `ArticleSelectionActionModeCallback` / `menu_article_selection_`）。

**Gate 5**：文档 diff 自审；`grep -rn "menu.clear()\|startActionMode" .trellis/spec/frontend/component-guidelines.md`
只剩明确禁止性描述。

## Step 6 — 全仓审计

- [ ] `grep -rn "ArticleSelectionActionModeCallback\|ArticleSelectionText\|menu_article_selection_" --include=*.java --include=*.kt --include=*.xml --include=*.md . | grep -v references | grep -v '/build/' | grep -v '.trellis/tasks/archive' | grep -v '.trellis/workspace'`
      应无输出。
- [ ] `grep -rn "startActionMode" --include=*.java --include=*.kt nga_phone_base_3.0/src lib_base_ui/src lib_base_common/src`
      应无输出。

## Step 7 — Debug gate 与打包

项目 shell 默认 JDK 21，必须用 JDK 17：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew testDebugUnitTest lintDebug --continue --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug --console=plain
```

- [ ] 单测无 failure / error / skip 异常；报告存在且非空。
- [ ] 逐模块检查 lint 报告：0 Error / 0 Fatal（仓库 `abortOnError false`）。
- [ ] `assembleDebug` 成功，记录 APK 路径。
- [ ] Python 脚本未改动，无需跑 `scripts` 单测。

## Step 8 — 真机验收（外部反馈者）

本机 `adb devices` 无设备，按项目策略不执行设备操作。交付时说明：

- [ ] 请 issue #20 反馈者在 Xiaomi 12X / MIUI 13 / Android 12 上确认：
      长按正文出现「复制 / 全选 / 网页搜索」，不崩溃，三项均可用。
- [ ] 若厂商注入项（识屏/传送门）回潮且产品不接受，按 `design.md` §5 追加
      remove-only 兜底，另开评审。
- [ ] 若仍崩溃：收集 `adb logcat -d -b crash`，本方案未覆盖 Chromium 侧根因。

## Rollback 点

- 全部改动合并为一次提交；任意 Gate 失败即 `git restore` 未提交改动。
- 已提交后发现行为回退：`git revert` 该提交即可恢复旧接管，无数据影响。

## 完成定义

`prd.md` 的 Acceptance Criteria 全部勾选（真机项标记为由反馈者待确认），Gate 7 通过，
spec 已更新，改动已提交。
