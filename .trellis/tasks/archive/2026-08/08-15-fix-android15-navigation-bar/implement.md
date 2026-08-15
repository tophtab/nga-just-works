# 实施计划

1. [x] 记录历史提交行为和当前失败路径，补充契约测试覆盖旧条件不得存在。
2. [x] 修改 Java `BaseActivity` 的导航栏颜色、图标外观及 Android 15 inset 背景绘制。
3. [x] 修改 Compose 与共享 UI 基类路径，确保 Compose/旧 XML 页面同样配置系统导航区域。
4. [x] 运行针对性 Kotlin/Java 契约测试与 Android 模块编译；检查 git diff，确认未撤销用户改动。
5. [x] 按 Trellis 质量检查和 break-loop 要求更新规范，提交修复。

## 验证记录

- `./gradlew :nga_phone_base_3.0:testDebugUnitTest :lib_base_ui_compose:testDebugUnitTest`：通过。
- `./gradlew :lib_base_ui:compileDebugKotlin :nga_phone_base_3.0:testDebugUnitTest :lib_base_ui_compose:testDebugUnitTest`：通过（`lib_base_ui` 的既有 `ExampleUnitTest` 未配置 JUnit，未运行该模块 unitTest 任务）。
- `./gradlew :nga_phone_base_3.0:lintDebug :lib_base_ui_compose:lintDebug`：通过。
