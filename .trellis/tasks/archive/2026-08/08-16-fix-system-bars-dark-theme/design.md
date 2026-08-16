# 技术设计

## 根因与边界

本任务包含两个独立但同属主题契约的 UI 修复：

1. Java 旧页面和 Compose 主页面必须共同配置系统导航区域。当前 Java
   `BaseActivity` 的导航栏代码被 `isNightMode() && !mComposeEnabled` 包住，
   而 `MainActivity` 在父类 `onCreate` 前设置 Compose 标记；Android 15/target
   SDK 35 的 edge-to-edge 又使系统默认白色不再可靠。`43520b6d` 的修复曾覆盖
   两条路径，但随后被 `571ad83d` / `54d16af7` 回退。
2. Compose 过滤页由 Material 2 `AppTheme` 承载，但“新增”入口和规则列表使用
   Material 3 `Icon` / `Text`，默认内容色没有继承 Material 2 暗色 palette；两处
   显式 `Color.Gray` 的辅助说明仍可见，不是本次反馈对应的故障点。XML 设置页把
   已完成滑条轨道绑定到主题 `colorPrimary`，不符合暗色模式左白右灰要求。

## 方案

- 在 `nga_phone_base_3.0` 的 Java `BaseActivity` 中抽出统一系统栏初始化：
  解析当前 `R.color.background_color`，设置导航栏颜色、关闭 Android Q+ 对比度
  scrim，并按日/夜模式设置导航栏图标外观。不要再依赖夜间或 Compose 条件。
- 保留旧 View 页面所需的状态栏/底部 inset 逻辑，但让 listener 每次收到 inset
  都更新状态栏占位高度、导航栏高度和底部 padding，避免旋转、导航模式切换或
  IME 重新分发时使用旧值。
- 在共享 Kotlin BaseActivity 与 Compose `ScaffoldApp` 同样配置导航栏颜色、
  decor 背景和图标外观；Compose 路径使用 `MaterialTheme.colors.background`，
  防止主页面跳过 Java BaseActivity 的 legacy 逻辑。
- 在 `FilterWordFragment` 统一使用与外层 `AppTheme` 相同的 Material 2
  `Icon` / `Text`，或显式使用 `MaterialTheme.colors.onBackground`；暗色 palette
  下“新增”图标/文字以及规则列表文字为白色，浅色下仍为深色。保留
  “长按子项可删除”和规则说明的固定灰色辅助层级。
- 在 `fragment_settings_size.xml` 的四个 `SeekBarEx` 上将已完成轨道设为
  `@color/text_color`（暗色为近白色，浅色保持深色文字色），未完成轨道继续使用
  `@color/text_color_disabled`（暗色灰色），从而实现左白右灰的暗色效果；不改变
  滑块提示文字和交互范围。

## 测试与兼容性

- 添加源契约测试，断言旧的夜间/Compose gate 不存在、Java/Kotlin/Compose
  路径均有导航栏颜色和图标设置、过滤页不再混用导致默认黑色的 Material 3
  内容组件且保留灰色辅助文案、四个滑条均使用 `@color/text_color`。
- 运行 JVM 契约测试、相关模块 debug 编译和 lint；不运行 ADB 或设备测试，除非
  用户在本轮另行授权。
- 回滚边界按文件分开：系统栏改动可独立回滚，过滤页/滑条资源改动可独立回滚；
  不回退到“仅夜间/非 Compose”条件。
