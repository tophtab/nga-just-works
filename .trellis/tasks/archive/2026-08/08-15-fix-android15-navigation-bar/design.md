# 技术设计

## 根因证据

- `e2f46b4a`（2019-08-16）把原本无条件的 `setNavigationBarColor` 改为仅夜间模式，日间主题从此依赖系统默认白色。
- `a428fc10`（2025-08-09）又增加 `!mComposeEnabled` 限制；截图所在 `MainActivity` 设置了 Compose，因此完全跳过旧导航栏颜色代码。
- 项目 target/compile SDK 为 35。Android 15 强制 Edge-to-Edge 后，旧的窗口默认背景不会保证填充透明导航区域；仅调用一次 `setNavigationBarColor` 也不足以覆盖 Compose/手势导航路径。

## 方案

1. 在 Java `gov...activity.BaseActivity` 中统一初始化系统栏外观：移除夜间和 Compose 条件，按当前主题解析 `background_color`，设置导航栏颜色/对比度策略，并通过 `WindowInsetsControllerCompat` 设置浅色导航图标标志。
2. 在 Edge-to-Edge inset 回调中维护一个专用导航栏背景 view（或等效的 decor 背景），高度跟随 `navigationBars()` inset，颜色跟随主题；这样 Android 15 透明/手势导航时仍由应用绘制背景。
3. 在 Compose `BaseComposeActivity`（以及 Java `MainActivity` 的 Compose 路径）复用同一套系统栏配置，避免因为旧 listener 被跳过而产生白色区域。
4. 保持现有内容底部 padding 和 `mNaviBarHeight` 计算，用 `WindowInsetsCompat` 的每次回调更新尺寸，避免旋转/导航模式切换时 stale inset。

## 回滚边界

若 Android 旧版本出现系统栏图标对比度回归，可仅回滚图标外观设置；导航栏背景和 inset 绘制是本问题的必要修复，不应恢复“仅夜间/非 Compose”条件。
