# 有导航键时恢复侧滑返回

## 背景

Issue #4（yelan6166 提出）要求恢复原开源版的侧滑返回：帖子内外及设置页，从屏幕左侧右滑 / 右侧左滑返回上一页。

该功能在上游 `77817d8f 下线侧滑返回功能`（2023-11-12）被整体删除，理由是「和边缘返回效果一致，属于安卓默认操作」。后续在 issue 讨论中确认：提问者使用的是**导航按键**（三键导航），此时系统没有边缘返回手势，所以功能并不重复。维护者结论是「开导航按键的时候，有这个功能」。

维护者本次进一步明确：**不做设置开关**。开关会让 App 出现两套并行交互模型（全面屏手势 / 导航按键），行为由系统导航模式单独决定即可。

## 目标

在**存在导航按键**的设备上自动启用侧滑返回；在全面屏手势模式下完全不启用，且不引入任何用户可见的设置项。

## 需求

### R1 按系统导航模式自动决定

- 系统���于三键导航或两键导航（有屏幕内返回键）时，启用侧滑返回。
- 系统处于全面屏手势导航时，不启用侧滑返回（系统边缘返回手势已覆盖该交互）。
- 判定在 Activity 创建时同步完成，不依赖窗口 insets 到达。
- 需要覆盖 MIUI 等使用私有开关的全面屏手势实现。

### R2 不提供开关

- 不恢复 `PreferenceKey.KEY_SWIPE_BACK`。
- 不向 `res/xml/settings.xml` 或其他设置页新增任何条目。
- 不恢复被删除的 `res/xml-v28/settings.xml`。
- 不���取任何与侧滑返回相关的 SharedPreferences。

### R3 生效范围按原项目标准

- 生效于 `gov.anzong.androidnga.activity.BaseActivity` 的子类（帖子列表、帖子详情、设置页、个人资料等 14 个 Activity）。
- **首页 `MainActivity` 不生效**：首页没有上一页，且首页右滑是打开侧边抽屉，冲突。原项目同样对首页 `setSwipeBackEnable(false)`。
- 触发边缘为左 + 右 + 下（`EDGE_ALL`），边缘宽度 10dp —— 与删除前的 `SwipeBackHelper` 完全一致。原项目 `swipe_back_position`（有效屏幕位置）数组从未被代码读取，属遗留死资源，本次不激活。

### R4 不破坏现有深色模式与 Android 15 edge-to-edge 表现

`d19f6fdc`（Android 15 导航栏配色修复）之后，`BaseActivity` 依赖 `decorView.setBackgroundColor()` 给透明导航栏区域上色。而侧滑返回库要求 decorView 背景为空才能透出下层页面。二者必须共存：

- 侧滑生效时，页面常态下的背景色（含导航栏区域）必须与当前一致：浅色 `@color/shit2`、深色 `@color/night_bg_color`（#080C10）。**不得**退化为主题 `windowBackground` 的深色值 `#FF202020`。
- 侧滑不生效时（全面屏手势设备），`BaseActivity` 行为与本次改动前逐字节等价。
- `SystemThemeContractTest` 现有断言必须全部通过。

## 约束

- `minSdk 29 / targetSdk 35 / compileSdk 35`。API 29 起 `Settings.Secure.navigation_mode` 必然可用，无需低版本兼容分支。
- 复用已在 `lib_base_common` 中声明的 `me.imid.swipebacklayout.lib:library:1.1.0`（依赖未随功能一起删除，构件在 aliyun public 镜像可解析）。不引入新依赖。
- 主题 `AppThemeDayNight` 已含 `android:windowIsTranslucent=true`，与原项目一致，**不需要**改动 styles。
- 设备验证按项目策略默认不执行（ADB / 真机操作 opt-in，本任务未获授权）。交付以 JVM 单测 + lint + assembleDebug 为准，真机观感由维护者确认。

## 非目标

- Compose 侧 `com.justwen.androidnga.ui.BaseActivity`（lib_base_ui）**不在本次范围**。原项目没有这个基类；且它挂在非透明主题 `Theme.AppCompat.DayNight.NoActionBar` 上，接入需要额外改主题，风险与收益不匹配。受影响页面（`FragmentTemplateActivity` 承载的帖子二级页、关于页）保持现状，后续如有需要另开任务。
- 不清理 `strings.xml` / `arrays.xml` 中残留的 `swipeback_*`、`setting_title_swipe_back` 死资源（与本需求无关，避免扩大 diff）。

## 验收标准

- [x] AC1 三键 / 两键导航模式下，`BaseActivity` 子类装配 `SwipeBackHelper`，边缘 10dp、`EDGE_ALL`。
- [x] AC2 全面屏手势模式下（含 MIUI 私有开关开启时）不装配 `SwipeBackHelper`，Activity 视图层级与改动前一致。
- [x] AC3 `MainActivity` 在任何导航模式下都不启用侧滑返回。
- [x] AC4 全仓无 `swipe_back` 偏好键读写，`res/xml/settings.xml` 无新增条目，无 `xml-v28/settings.xml`。
- [x] AC5 侧滑生效时，内容根视图背景为 `@color/background_color`，深色模式下为 #080C10 而非 #202020。（代码与契约测试已锁定；真机观感待维护者确认）
- [x] AC6 `SystemThemeContractTest` 全部通过（含 `getWindow().getDecorView().setBackgroundColor(backgroundColor)` 字面量断言）。
- [x] AC7 新增契约测试覆盖 R1 / R2 / R3 的源码约定。
- [x] AC8 `:nga_phone_base_3.0:testDebugUnitTest`（目标测试）、`lintDebug`、`assembleDebug` 通过。

## 交付记录

- `:nga_phone_base_3.0:testDebugUnitTest` 全量 133 tests / 0 failures / 0 errors（含新增 `SwipeBackContractTest` 4 项、`SystemThemeContractTest` 4 项）。
- `:lib_base_common:testDebugUnitTest`、`:nga_phone_base_3.0:lintDebug`、`:nga_phone_base_3.0:assembleDebug` 通过。
- 产物核对：`swipeback_layout`、`shadow_left/right/bottom`、`SwipeBackLayout` 类均已合入 debug APK（验证 `implementation` 作用域下 AAR 资源合并正常）。
- 真机验证 **not run per project policy**（ADB opt-in，本任务未获授权）。
- 相对 design.md 的一处补充：`setupSwipeBack()` 加了 try/catch 兜底并在失败时置空 helper。原项目该功能默认关闭，本次对导航键设备默认开启，挂载异常会直接崩在 `onCreate`；失败降级回改动前路径（此时 `configureSystemBars()` 因 helper 为 null 会重新涂回 decor 背景）。

