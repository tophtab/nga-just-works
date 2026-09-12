# NGA Android AI 设置与总结实施计划

## Implementation order

### 1. AI 设置与请求底座

- [x] 在 `settings.xml` 一级页面增加与“实验室”同级的“AI 设置”入口。
- [x] 新增 `SettingsAiFragment` 和 `settings_ai.xml` 二级页面，复用现有 `LauncherSubActivity` 导航与返回行为。
- [x] 在二级页放置 API 服务地址、密码型 API Key、模型名称、连接测试和清除配置；一级页不展开这些字段。
- [x] 实现单一 `AiConfig`：API 服务地址、API Key、模型名称。
- [x] 实现字段校验、保存、修改、清除和配置状态展示。
- [x] 使用 Android Keystore 保护 API Key，并审查日志、备份和普通偏好中的明文 Key 隔离。
- [x] 实现独立、无 NGA Cookie 的非流式 OpenAI-compatible 请求客户端。
- [x] 增加简短连接测试及成功、认证、地址、网络、服务端错误提示。

### 2. 楼层 AI 总结

- [x] 在两套楼层三点菜单中增加“AI 总结”。
- [x] 从被点击的 `ThreadRowInfo` 生成只包含标题、楼层、作者和当前楼层纯文本的请求。
- [x] 未配置时引导至 AI 设置；已配置时进入共用总结界面。
- [x] 完成留在帖子页的共用可滚动总结弹窗，以及加载、结果、错误、重试、复制与关闭取消。

### 3. 用户资料页 AI 总结

- [x] 在 `menu_user_profile.xml` 增加“AI 总结”，资料未加载时隐藏。
- [x] 始终使用当前 `mProfileData.uid`，读取该用户主题第一页与回复第一页，不自动翻页。
- [x] 整理有长度上限的公开内容并调用共用总结客户端。
- [x] 复用楼层总结的可滚动弹窗及加载、结果、错误、重试和复制能力。

### 4. Verification

- [x] 覆盖一级入口、二级页导航/返回、配置校验、Key 生命周期、endpoint 规范化和请求/响应错误测试。
- [x] 覆盖楼层对象绑定、资料页目标 UID、未配置引导、取消和迟到结果丢弃测试。
- [x] 用 MockWebServer 验证请求格式、错误映射和 NGA Cookie 隔离，并审查敏感信息日志路径。
- [x] App 构建和单元测试、所有模块 lint 通过；仓库诊断的既有失败已记录，设备操作遵守当前项目的显式授权规则。

完成证据见 [verification.md](verification.md)。UI 合约测试为静态检查，
实际设备、重启与真实服务联调没有执行；不将测试 APK 编译视为设备测试通过。

## Planned validation commands

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest :nga_phone_base_3.0:assembleDebug :nga_phone_base_3.0:assembleDebugAndroidTest --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew testDebugUnitTest --continue --console=plain
```

本轮没有设备测试授权。按 `android-quality-guidelines.md` 不查询 ADB 或设备，
不安装 APK、不运行 instrumentation，也不启动模拟器；设备验证记为
“not run per project policy”，不阻塞交付。不使用真实用户 Key 或真实论坛内容
做自动化测试。仓库级 debug 单元测试和所有模块 lint 按质量规范执行。

## Containment

- AI 配置或连接测试失败时，只显示错误，不影响论坛功能。
- 未配置或配置被清除后，两个总结入口不得发送请求。
- 楼层或用户对象失效时取消请求，不把结果显示到其他对象。
- 若独立 AI 客户端无法安全隔离 NGA Cookie 或日志，则停止启用 AI 入口，不修改原有 NGA 网络栈来规避问题。
