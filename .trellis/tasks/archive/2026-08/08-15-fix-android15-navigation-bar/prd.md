# 修复 Android 15 日间主题导航栏背景

## 背景

用户反馈 issue2 仍然存在：帖子列表页面底部系统导航区域呈白色，而应用日间主题背景为浅黄色；Android 15 设备同样复现。历史代码只在夜间模式设置导航栏颜色，并在 Compose 抽屉改版后跳过 Compose 页面，导致实际问题覆盖范围与修复目标不一致。

## 需求

- 所有使用 NGA 主题的 Activity，在日间和夜间模式下都必须为系统导航区域提供与页面主题一致的背景。
- Android 15（targetSdk 35 强制 Edge-to-Edge）手势导航和三键导航模式都不能出现白色系统区域。
- 保留导航栏图标与背景的可读性：浅色背景使用深色图标，深色背景使用浅色图标。
- 不破坏现有状态栏、IME inset 和 Android 15 `adjustResize` 兼容逻辑。
- 识别并撤销历史“仅夜间模式/非 Compose 才设置导航栏颜色”的错误限制；修复必须覆盖截图中的 Compose 主页面以及旧 View 页面。

## 验收标准

1. 日间主题的导航栏背景使用 `R.color.background_color`（当前为浅黄色），不再回落为白色。
2. 夜间主题的导航栏背景继续使用夜间 `R.color.background_color`。
3. Compose 主页面和 Java/View `BaseActivity` 页面均配置系统栏背景与图标外观。
4. Android 15 API 行为通过单元/契约测试验证：代码不再以 `isNightMode && !mComposeEnabled` 作为导航栏颜色设置条件，并处理 navigation bar insets。
5. 相关模块编译、测试通过，且不引入 lint/type 错误。
