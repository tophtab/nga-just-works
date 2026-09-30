# R4：实施计划

- [x] 新会话读取已审核 PRD、design.md 和 reader/预加载/生命周期契约后启动。
- [x] 沿我的回复→显示全部链路传递独立目标 pid/floor，保留原查询到 FULL 查询的语义转换。
- [x] 新阅读器根据目的源分页事实选择初始页，并初始化新 generation 的待定位锚点。
- [x] 复用实际行匹配与现有有界源切换对齐；对找不到目标提供一次明确提示，保持主题可读。
- [x] 覆盖旋转/热启动/账号切换/迟到回调/无目标普通入口；保证无无限找页与重复滚动。
- [x] 运行导航/会话/相关 Fragment 测试及父任务离线门禁，Trellis check 覆盖整个改动并更新显示全部契约。

所有权：ArticleNavigation、ArticleListParam/Activity 启动目标、ReaderSession/ViewModel 锚点初始化、Fragment 定位入口和测试。先整合 R2/R3，再由 R1 复用入口；不得覆盖其他工作者改动。R7 搁置不会阻塞此任务。
