# 修复个人资料页深色模式配色

## Goal

让旧版个人资料页在深色模式下使用深色卡片和可读的文字颜色，同时保持白天模式现有的黄褐色视觉。

## Background

- 所有资料页入口最终路由到 `nga_phone_base_3.0` 的 `ProfileActivity`。
- [activity_user_profile_content.xml](/home/toph/nga-just-works/nga_phone_base_3.0/src/main/res/layout/activity_user_profile_content.xml:25) 的四个 `CardView` 将背景写死为 `#fff5d7`，标题和标签也写死为 `#712d08`、`#121c46`、`#551200`、`#808080`。
- `ProfileActivity` 已根据 `ThemeManager.isNightMode()` 为内嵌 WebView 选择夜间背景（[ProfileActivity.java](/home/toph/nga-just-works/nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/ProfileActivity.java:402)），问题集中在外层原生布局的硬编码颜色。
- 最近的深色模式提交没有修改个人资料页布局，因此本任务补齐该遗漏。

## Requirements

- 将个人资料页卡片背景、标题、标签、数值和操作链接改为语义化颜色资源，不在布局中直接写死这些颜色。
- 在 `values/` 保留当前白天模式的视觉颜色，在 `values-night/` 为同一组资源提供深色背景和高对比文字颜色。
- 资料页的状态颜色和布局结构保持现有行为；签名、管理权限、声望 WebView 的现有夜间逻辑不变。
- 为资源覆盖和布局引用增加源代码契约测试，防止日后重新引入硬编码浅色值。

## Acceptance Criteria

- [x] 白天模式个人资料页仍显示原有的浅黄色卡片和黄褐色文字。
- [x] 深色模式个人资料页的四个卡片不再使用 `#fff5d7`，卡片背景为深色，标题、标签和数值在深色背景上可读。
- [x] `activity_user_profile_content.xml` 不再包含本任务涉及的硬编码浅色卡片/文字颜色，并全部引用语义化资源。
- [x] 日夜资源和布局引用通过 `SystemThemeContractTest` 或等价测试验证。
- [x] 相关 Android 单元测试通过；不改变其他页面主题行为。

## Out of Scope

- 不重写 `ProfileActivity` 或迁移到 Compose。
- 不调整资料页的布局、文案、间距、图标或 WebView 内容逻辑。
- 不修改其他页面的深色模式配色。
