# Bug Analysis: Android 15 日间主题导航栏显示白色

## 1. Root Cause Category

- **Category**: C/D/E — Change Propagation Failure、Test Coverage Gap、Implicit Assumption
- **Specific Cause**: 旧实现假设日间模式可以使用系统默认白色导航栏，并在 Compose 改版时直接跳过共享导航栏处理；targetSdk 35 强制 Edge-to-Edge 后，这个假设不成立。系统栏颜色、透明区域背景和图标外观也没有作为同一契约维护。

## 2. Why Fixes Failed

1. 2019 年修复只在夜间设置颜色：只覆盖了当时的暗色可读性症状，没有覆盖日间主题一致性。
2. 2025 年 Compose 改版增加 `!mComposeEnabled`：避免旧 View inset 逻辑干扰 Compose 的同时，也把导航栏颜色配置一并跳过，范围切分错误。
3. 旧测试只关注页面内容/状态栏，没有断言 Compose 和 View 两条路径都设置 navigation bar，因此回归无法在构建阶段被发现。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
| --- | --- | --- | --- |
| P0 | Architecture | 将系统栏颜色配置与 View 专用 inset padding 分开，颜色配置覆盖所有 Activity | DONE |
| P0 | Test Coverage | 增加 Java 与 Compose 系统导航栏源契约测试 | DONE |
| P1 | Documentation | 在 Android component spec 中记录 SDK 35 Edge-to-Edge 契约 | DONE |
| P1 | Review | 修改 Compose/View 分流条件时检查是否误伤共享系统 UI 行为 | DONE |

## 4. Systematic Expansion

- **Similar Issues**: `gov...BaseActivity`、`lib_base_ui.BaseActivity`、Compose `ScaffoldApp` 是三条系统栏入口，必须保持日/夜主题一致。
- **Design Improvement**: 把主题颜色/图标外观视作全局 Activity 行为，只把 status/navigation inset padding 保留为页面技术栈专用逻辑。
- **Process Improvement**: Android target SDK 升级时，同时验证状态栏、手势导航、三键导航和 IME 四种窗口区域。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/frontend/component-guidelines.md`
- [x] 增加 Java/Compose regression contract tests
- [x] 记录旧提交 `e2f46b4a` 与 `a428fc10` 的失败边界
