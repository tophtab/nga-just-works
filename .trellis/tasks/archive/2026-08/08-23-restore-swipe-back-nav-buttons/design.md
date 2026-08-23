# 技术设计：有导航键时恢复侧滑返回

## 1. 方案总览

三处改动 + 一处测试：

| 层 | 文件 | 职责 |
| --- | --- | --- |
| 能力探测 | `lib_base_common/.../base/util/DeviceUtils.kt` | `hasNavigationButtons(Context): Boolean` |
| 交互封装 | `lib_base_common/.../base/common/SwipeBackHelper.java`（新建，路径同原项目） | 包住 `SwipeBackActivityHelper`，固定 10dp / `EDGE_ALL`，并修正内容根背景 |
| 接入点 | `nga_phone_base_3.0/.../activity/BaseActivity.java` | 生命周期挂载 + 与 edge-to-edge 背景逻辑互斥 |
| 例外 | `nga_phone_base_3.0/.../activity/MainActivity.java` | 显式关闭 |
| 契约 | `nga_phone_base_3.0/src/test/.../SwipeBackContractTest.kt`（新建） | 锁定上述约定 |

不新增依赖，不改主题，不改设置页。

## 2. 导航模式探测

### 契约

```kotlin
// gov.anzong.androidnga.base.util.DeviceUtils
@JvmStatic
fun hasNavigationButtons(context: Context): Boolean
```

`true` = 屏幕上有导航按键（三键或两键导航）→ 启用侧滑返回。
`false` = 全面屏手势 → 不启用。

### 判定顺序

1. `Settings.Global.getInt(resolver, "force_fsg_nav_bar", 0) != 0` → MIUI 全面屏手势已开 → `false`。
   MIUI 的全面屏手势不写 AOSP 的 `navigation_mode`，只查后者会误判为三键。
2. `Settings.Secure.getInt(resolver, "navigation_mode", 0)`：
   - `0` 三键导航 → `true`
   - `1` 两键导航（药丸 + 返回键，仍有实体返回入口）→ `true`
   - `2` 全面屏手势 → `false`
3. 任何异常 → `false`（fail-closed）。

### 取舍

- **为什么不用 window insets 判定**（`tappableElement().bottom == 0`）：insets 在 `onCreate` 时尚未派发，而 `SwipeBackActivityHelper.onActivityCreate()` 必须在 `onCreate` 内调用（它要在 decor 定型前清空窗口背景）。异步判定会导致「先按无按键装配、拿到 insets 再拆」的二段式，视图层级会在首帧后变动。`Settings.Secure` 是同步的、`minSdk 29` 起必然存在的 AOSP 键，直接满足需求。
- **fail-closed 的理由**：读不到时保持改动前行为，最坏是某台异形设备拿不到新功能；fail-open 则可能在手势设备上叠一层多余的 SwipeBackLayout，并连带触发第 4 节的背景改写路径。
- **已知限制**：运行中切换导航模式，已创建的 Activity 不会重新装配，返回首页重进即可。不做监听，避免为边缘场景引入 `ContentObserver` 生命周期。

## 3. SwipeBackHelper

与被删除的原实现同包同名（`gov.anzong.androidnga.base.common`），参数保持一致：边缘宽度 `10dp`，`setEdgeTrackingEnabled(EDGE_ALL)`。相对原实现只加一个方法（见第 4 节）：

```java
public void onCreate(Activity activity)                 // 构造 + onActivityCreate + 边缘参数
public void onPostCreate()                              // attachToActivity
public void setContentBackgroundColor(@ColorInt int)    // 新增：修正内容根背景
public <T extends View> T findViewById(@IdRes int id)   // 委托查找
```

公开签名不出现 `me.imid.*` 类型，因此 `lib_base_common` 里 `implementation 'me.imid.swipebacklayout.lib:library:1.1.0'` 的 `implementation` 作用域无需改成 `api`。AAR 的资源（`swipeback_layout.xml`、三张阴影图、`R.style.SwipeBackLayout` 默认样式）随运行时类路径正常合并进 APK；库构造函数带 `defStyleRes = R.style.SwipeBackLayout` 兜底，主题不定义 `SwipeBackLayoutStyle` 也能工作 —— 原项目同样没定义。

## 4. 与 edge-to-edge / 深色模式的冲突（本设计的核心）

### 冲突链

`SwipeBackActivityHelper.onActivityCreate()` 的实际字节码行为：

```
window.setBackgroundDrawable(ColorDrawable(0))
window.decorView.setBackgroundDrawable(null)      // ← 必须为空，否则拖动时看不到下层页面
```

`SwipeBackLayout.attachToActivity()` 随后把 decor 的第 0 个子节点（decorChild，含 `android.R.id.content`）摘出、塞进 SwipeBackLayout，并对它执行 `setBackgroundResource(android.R.attr.windowBackground)`。

而 `d19f6fdc` 之后 `BaseActivity.configureSystemBars()` 做的是：

```java
getWindow().getDecorView().setBackgroundColor(backgroundColor);   // backgroundColor = R.color.background_color
```

—— Android 15 强制 edge-to-edge 后 `setNavigationBarColor` 失效，靠这句给导航栏区域上色。

两者直接互斥：`configureSystemBars()` 在 `onCreate` 里位于挂载之后，会把 decor 背景重新涂实，侧滑时露不出下层页面。

而如果只是简单跳过这句，decorChild 会拿到主题的 `windowBackground`：

| 模式 | `@color/window_background` | `@color/background_color` |
| --- | --- | --- |
| 浅色 | `@color/shit2` (#FFF8E7) | `@color/shit2` (#FFF8E7) |
| 深色 | **#FF202020** | `@color/night_bg_color` **#080C10** |

深色模式下会从 #080C10 变成 #202020 —— 正好踩中最近几轮深色模式修复（`1bd133e0`、`06d77a96`）刚统一的配色。

### 解法

侧滑生效时把「上色的图层」从 decorView 下移到内容根：

1. `configureSystemBars()` 中，`decorView.setBackgroundColor(...)` 用 `if (mSwipeBackHelper == null)` 包住 —— 未启用侧滑（含全部全面屏手势设备）时逐字节等价于现状。
2. `onPostCreate()` 中 `attachToActivity` 之后，调用 `setContentBackgroundColor(ContextUtils.getColor(R.color.background_color))`，覆盖库写入的 `windowBackground`。

内容根在 edge-to-edge 下铺满整个窗口（导航栏区域是 padding，不是 margin，背景照样绘制到边），所以导航栏区域的颜色与现状一致；拖动时被平移的是内容根，露出的是已被清空的 decor → 下层 Activity。

### 契约测试的硬约束

`SystemThemeContractTest` 用**源码字面量**断言：

```kotlin
assertTrue(javaSource.contains("getWindow().getDecorView().setBackgroundColor(backgroundColor)"))
```

加 `if` 包裹后该子串仍然存在，测试继续通过。**改动时不得重排这句的写法**（例如提取局部变量 `decorView`），否则会打断现有契约。

## 5. BaseActivity 接入

新增字段：

```java
private boolean mSwipeBackEnabled = true;      // 子类可在 super.onCreate() 前关闭
private SwipeBackHelper mSwipeBackHelper;      // null 表示本次未启用
```

`onCreate` 顺序（在 `super.onCreate()` 与 `configureSystemBars()` 之间插入挂载）：

```
mConfig / updateThemeUi
super.onCreate
initializeWebTheme
setupSwipeBack()            ← 新增：mSwipeBackEnabled && DeviceUtils.hasNavigationButtons(this)
configureSystemBars()       ← decor 上色改为条件执行
enableEdge2Edge()
```

新增覆写 `onPostCreate`（`attachToActivity` + 背景修正）与 `findViewById`（`super` 返回 null 时委托 helper，覆盖挂载完成前对 SwipeBackLayout 内部视图的查找）。

### 相对原项目的两点简化

- **不恢复 `onCreateBeforeSuper` / `onCreateAfterSuper`**。原项目需要这对空钩子，是因为 `BaseActivity.onCreate` 里 `setSwipeBackEnable(读偏好)` 会覆盖子类在 super 之前设的值。本设计中 `BaseActivity` 只做「与导航模式取与」，不再写 `mSwipeBackEnabled`，子类在 `super.onCreate()` 之前调用即可生效 —— `MainActivity` 现有的 `setToolbarEnabled(true)` / `setComposeEnabled(true)` 正是这个位置。省掉两个空钩子。
- **不恢复偏好读取**（R2）。

`MainActivity` 只加一行 `setSwipeBackEnable(false);`，与 `setComposeEnabled(true)` 相邻。

### 对 `enableEdge2Edge()` 的影响

`enableEdge2Edge()` 在 `onCreate` 里只注册 insets 监听，回调发生在 `onPostCreate` 挂载之后。回调内用的 `contentView`（`android.R.id.content`）与 `contentView.getParent()` 对象身份在搬迁前后不变，`getWindow().getDecorView().findViewById(R.id.status_bar)` 也仍在 decor 子树内，因此监听逻辑无需改动。`SwipeBackLayout` 是普通 `FrameLayout`，不覆写 insets 派发，insets 正常透传到内容根。**此条为静态推导，需在真机/模拟器复核状态栏占位与键盘 padding**（见验收）。

## 6. 影响面与回滚

- 全面屏手势设备：`mSwipeBackHelper` 恒为 null，所有新增分支短路，行为不变。
- 导航按键设备：多一层 `SwipeBackLayout`；`MainActivity` 除外。
- 回滚：四处改动互不耦合，`git revert` 单个提交即可；紧急止血也可只把 `hasNavigationButtons` 改为恒返回 `false`，功能整体休眠且不影响其它逻辑。

## 7. 验证

- JVM：新增 `SwipeBackContractTest` + 现有 `SystemThemeContractTest`。
- 构建：`lintDebug`、`assembleDebug`。
- 真机：按项目策略默认不执行（ADB opt-in，本任务未获授权）。需维护者在三键导航设备上确认：帖子/设置页左右边缘可拖回、拖动中能看到下层页面、深色模式底部导航栏区域仍为 #080C10、首页右滑仍是抽屉。
