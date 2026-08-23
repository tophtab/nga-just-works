# 执行计划：有导航键时恢复侧滑返回

## 前置

- 分支 `main`，工作区干净。注意本仓库是共享 worktree，提交前重新确认 `git status`。
- 无需联网拉新依赖：`me.imid.swipebacklayout.lib:library:1.1.0` 已在 `~/.gradle` 缓存中。

## 步骤

### 1. 导航模式探测 — `DeviceUtils.hasNavigationButtons`

文件：`lib_base_common/src/main/java/gov/anzong/androidnga/base/util/DeviceUtils.kt`

- 在 `object DeviceUtils` 内新增私有常量：`NAVIGATION_MODE = "navigation_mode"`、`MIUI_FULL_SCREEN_GESTURE = "force_fsg_nav_bar"`、`NAVIGATION_MODE_THREE_BUTTON = 0`、`NAVIGATION_MODE_GESTURE = 2`。
- 新增 `@JvmStatic fun hasNavigationButtons(context: Context): Boolean`，按 design.md §2 的顺序判定，整体 `try/catch` 兜底返回 `false`。
- 注释说明「同步判定，不能等 insets」这一约束，避免后人重构成 insets 版本。
- 沿用文件现有风格：`object` + `@JvmStatic` + `try/catch { // ignore }`。

**验证**：`./gradlew :lib_base_common:compileDebugKotlin`

### 2. 恢复 `SwipeBackHelper`

文件（新建）：`lib_base_common/src/main/java/gov/anzong/androidnga/base/common/SwipeBackHelper.java`

- 包路径与原项目一致（`base/common` 目录需新建）。
- `onCreate` / `onPostCreate` / `findViewById` 逐字对齐删除前的实现（10dp、`EDGE_ALL`）。
- 新增 `setContentBackgroundColor(@ColorInt int color)`：取 `getSwipeBackLayout().getChildAt(0)`，非空则 `setBackgroundColor`。方法上写清「为什么不能依赖 windowBackground」（#202020 vs #080C10）。
- 公开签名不得出现 `me.imid.*` 类型。

**验证**：`./gradlew :lib_base_common:assembleDebug`

**回滚点 A**：此时 `nga_phone_base_3.0` 未改动，App 行为完全未变。

### 3. 接入 `BaseActivity`

文件：`nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/BaseActivity.java`

1. import `SwipeBackHelper`、`DeviceUtils`。
2. 新增字段 `mSwipeBackEnabled = true`、`mSwipeBackHelper`。
3. `onCreate` 在 `initializeWebTheme` 之后、`configureSystemBars()` 之前插入 `setupSwipeBack();`。
4. 新增 `private void setupSwipeBack()`：`mSwipeBackEnabled && DeviceUtils.hasNavigationButtons(this)` 才 new + `onCreate(this)`。
5. 新增 `protected void setSwipeBackEnable(boolean enable)` 写字段。
6. `configureSystemBars()` 里给 `getWindow().getDecorView().setBackgroundColor(backgroundColor);` 套 `if (mSwipeBackHelper == null) { ... }`，并写注释说明原因。
   ⚠️ **这一行的字面写法不能改**（`SystemThemeContractTest` 用子串断言）。
7. 覆写 `onPostCreate`：`super` → `mSwipeBackHelper.onPostCreate()` → `setContentBackgroundColor(ContextUtils.getColor(R.color.background_color))`。
8. 覆写 `findViewById(int)`：`super` 返回 null 且 helper 非空时委托 helper。

**验证**：`./gradlew :nga_phone_base_3.0:compileDebugJavaWithJavac`

### 4. 首页例外 — `MainActivity`

文件：`nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/MainActivity.java`

- `onCreate` 中 `setComposeEnabled(true);` 之后、`super.onCreate` 之前加 `setSwipeBackEnable(false);`。
- 加一行注释点明原因：首页右滑是打开抽屉，且首页无上一页。

### 5. 契约测试

文件（新建）：`nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/SwipeBackContractTest.kt`

沿用 `SystemThemeContractTest` 的 `projectRoot` / `source()` 读源码模式，四组断言：

| 测试方法 | 覆盖 | 断言要点 |
| --- | --- | --- |
| `navigationModeDetectionCoversAospAndMiui` | AC1 / AC2 | `DeviceUtils.kt` 含 `hasNavigationButtons`、`"navigation_mode"`、`"force_fsg_nav_bar"`、`NAVIGATION_MODE_GESTURE` |
| `swipeBackIsWiredOnlyForNavigationButtonDevices` | AC1 / AC3 | `SwipeBackHelper.java` 含 `EDGE_ALL` 与 10dp 换算；`BaseActivity.java` 含 `DeviceUtils.hasNavigationButtons(this)`、`mSwipeBackHelper.onPostCreate()`、`mSwipeBackHelper.findViewById(id)`；`MainActivity.java` 含 `setSwipeBackEnable(false)` |
| `swipeBackHasNoUserFacingToggle` | AC4 | `PreferenceKey.java` 不含 `KEY_SWIPE_BACK`；`res/xml/settings.xml` 不含 `swipe_back`；`res/xml-v28/settings.xml` 不存在；`BaseActivity.java` 不含 `PreferenceKey.KEY_SWIPE_BACK` |
| `swipeBackKeepsNightBackgroundColor` | AC5 / AC6 | `BaseActivity.java` 同时含 `if (mSwipeBackHelper == null)` 与原字面量 `getWindow().getDecorView().setBackgroundColor(backgroundColor)`；含 `setContentBackgroundColor`；`lib_base_common/build.gradle` 含 `me.imid.swipebacklayout.lib` |

### 6. 验证门

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests "*SwipeBackContractTest*" --tests "*SystemThemeContractTest*"
./gradlew :nga_phone_base_3.0:lintDebug
./gradlew :nga_phone_base_3.0:assembleDebug
```

全仓 `testDebugUnitTest` 在 `lib_bu_statistics` / `lib_core` 有既存基线失败（见 journal Session 43），不作为本任务的判定依据；如运行需与基线对照。

真机验证**不执行**（ADB opt-in，本任务未获授权），报告中标注 "not run per project policy"，并列出需维护者目视确认的四项（见 design.md §7）。

### 7. 收尾

- `trellis-check` 全量核对 → 更新 spec（若沉淀出新契约）→ 提交。
- 提交信息建议：`feat(android): restore swipe back on navigation button devices`。
- 提交前重新 `git status`（共享 worktree，分支可能已被其它会话推进）。

## 回滚点

| 编号 | 位置 | 状态 |
| --- | --- | --- |
| A | 完成步骤 2 | 只新增 `lib_base_common` 代码，App 行为未变 |
| B | 完成步骤 4 | 功能生效；紧急止血可让 `hasNavigationButtons` 恒返回 `false` |
| C | 提交后 | 单个提交 `git revert` |

## 风险清单

| 风险 | 处理 |
| --- | --- |
| 改写 `configureSystemBars()` 时打断 `SystemThemeContractTest` 的字面量断言 | 步骤 3.6 明确「不得改写法」，步骤 5 再断言一次 |
| 深色模式导航栏区域退化为 #202020 | `setContentBackgroundColor` 显式覆盖 + AC5 |
| `SwipeBackLayout` 拦截 `ViewPager` / 图片缩放的横向手势 | 边缘宽度仅 10dp，与原项目一致；真机复核项 |
| MIUI 全面屏手势被误判为三键 | 优先查 `force_fsg_nav_bar` |
| insets 派发经过新增的 `SwipeBackLayout` 层出现偏差 | 静态推导为透传，列入真机复核项 |
