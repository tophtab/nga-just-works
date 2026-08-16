# 实施计划

1. [x] 在 Java 与 Kotlin BaseActivity、Compose Scaffold 中恢复并完善系统导航栏主题契约，处理 edge-to-edge 背景、图标外观和重复 inset 分发。
2. [x] 修复过滤规则页 Material 2/3 内容色断层，使“新增”入口和规则列表在暗色模式下使用白色，同时保留灰色辅助说明。
3. [x] 修复字体/头像大小 XML 页面四个滑条的左右轨道颜色。
4. [x] 增加/更新 Java、Kotlin、Compose 与资源源契约测试，覆盖旧 gate 不得恢复。
5. [x] 运行针对性单元测试、debug 编译和 lint，检查浅色/深色资源解析及 diff。
6. [x] 完成 Trellis 质量检查，记录 issue2 历史回退根因与验证结果。

## 验证命令

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest
./gradlew :lib_base_ui:testDebugUnitTest :lib_base_ui_compose:testDebugUnitTest
./gradlew :nga_phone_base_3.0:assembleDebug
./gradlew :nga_phone_base_3.0:lintDebug :lib_base_ui:lintDebug :lib_base_ui_compose:lintDebug
```

设备/ADB 验证不在本计划默认授权范围内。

## 验证结果

- `:nga_phone_base_3.0:testDebugUnitTest`、`:lib_base_ui:testDebugUnitTest`、`:lib_base_ui_compose:testDebugUnitTest`：通过。
- `:nga_phone_base_3.0:assembleDebug`：通过。
- `:nga_phone_base_3.0:lintDebug`、`:lib_base_ui:lintDebug`、`:lib_base_ui_compose:lintDebug`：通过，三个 XML 报告均为零 Error/Fatal。
- `lintDebug --continue --rerun-tasks`：全仓通过，全部生成的 debug lint XML 报告均为零 Error/Fatal。
- `testDebugUnitTest --continue`：本任务涉及模块通过；全仓命令仍因既有 `lib_bu_statistics` 缺 JUnit、`lib_module_debug` kapt 测试桩错误及 `lib_core` 既有运行时类缺失而失败，未扩大本任务范围处理。
- `git diff --check`：通过。
- 设备/ADB：按项目授权策略未运行。
