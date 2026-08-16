# Bug Analysis: 系统导航栏与暗色主题修复被回退或修错目标

## 1. Root Cause Category

- **Category**: C/D/E - Change Propagation Failure, Test Coverage Gap, Implicit Assumption
- **Specific Cause**: 导航栏颜色被历史夜间/非 Compose 条件限制，Android 15 edge-to-edge 后系统默认白色假设失效；过滤页同时使用 Material 2 主题和 Material 3 内容组件，却假设默认内容色会跨主题代际传播。相关修复提交又在恢复 5.5.0 基线时被整体回退，没有保留契约测试。

## 2. Why Fixes Failed

1. `e2f46b4a` / `a428fc10`: 只为当时的夜间/旧 View 症状设置导航栏颜色，Compose 主页面被明确排除，覆盖范围不完整。
2. `43520b6d`: 曾覆盖 Java、Kotlin 和 Compose 系统栏入口，但 `571ad83d` / `54d16af7` 的基线恢复回退了生产代码、测试和规范，所以当前分支没有实际修复。
3. `b9a7d2d9`: 把两段可见的 `Color.Gray` 辅助说明改成资源文字色，未处理真正不可见的 Material 3 默认“新增”和列表文字，属于表面修复。
4. `24fd95f1`: 滑条配色方向正确，但同样被基线恢复提交回退。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
| --- | --- | --- | --- |
| P0 | Architecture | 系统栏配置覆盖 Java、Kotlin、Compose 三条入口，不与 View inset 分流条件绑定 | DONE |
| P0 | Theme boundary | Material 2 `AppTheme` 下使用 Material 2 默认内容组件，保留显式辅助色层级 | DONE |
| P0 | Test coverage | 契约测试检查旧 gate、三条系统栏入口、Material imports、灰色说明数量和四个滑条资源 | DONE |
| P1 | Insets | 捕获初始 padding、每次分发重算，并保留父布局的 `LayoutParams` 类型 | DONE |
| P1 | Documentation | 将系统栏和 Material 主题代际边界写入 frontend component spec | DONE |

## 4. Systematic Expansion

- **Similar Issues**: 其他 Compose 页面若在 Material 2 `AppTheme` 下导入 Material 3 默认内容组件，也可能在暗色模式出现黑字/黑图标。
- **Design Improvement**: 把系统栏主题行为视为全局 Activity 契约，只把内容 padding/inset 适配按 View 与 Compose 分流。
- **Process Improvement**: 基线恢复或 revert 后必须运行源契约测试，确认用户可见修复、测试和规范没有一起丢失。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/frontend/component-guidelines.md`。
- [x] 增加系统栏、Material 组件代际和滑条资源契约测试。
- [x] 记录历史提交与回退链条。
