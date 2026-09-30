# R1：实施计划

- [x] 在新会话读取已审核 PRD、design.md、上下文清单后启动此子任务。
- [x] 核对 Activity 的冷启动/onNewIntent 参数处理，添加纯 nga URI 解析器和独立清单 filter。
- [x] 复用现有 ArticleListParam 和主题/回复入口，不重写读取链路。
- [x] 验证真实 Via 示例、回复链接、重复冲突参数、不支持类型、整数边界和热启动目标更新。
- [x] 运行相关单元测试及父任务的构建、单测、lint 检查；Trellis check 覆盖整个子任务差异。
- [x] 记录系统默认处理程序限制和未执行的设备场景；更新链接入口契约。

所有权：AndroidManifest.xml、ArticleListActivity 入口、新增纯解析器和对应测试。与 R4 对启动参数的改动顺序集成，不能覆盖其目标锚点字段。工作者不独占代码库，须保留其他人的改动。
无额外真实 NGA 请求、设备安装或发布。回退边界见 design.md。
