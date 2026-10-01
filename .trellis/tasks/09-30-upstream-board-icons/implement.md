# U1 实施计划

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。

## 0. 审核入口

- [x] 阅读父 PRD、handoff、来源索引及 U1 PRD，核对固定上游补丁与当前代码。
- [x] 建立 `/home/toph/nga-just-works-upstream-adoption`，分支 `feat/upstream-adoption`，基于 `1a8413d9`。
- [x] 复制并校验五个未提交任务目录及被引用的 R7 归档调研，共 36 个初始文件；未复制其他会话产品改动。
- [x] 补齐 U1 PRD、design、implement 和真实 context 清单。
- [x] 用户已明确批准U1–U4整体最终方案。
- [ ] 新会话读取父approval.md/handoff.md后执行 `task.py start .trellis/tasks/09-30-upstream-board-icons`；本轮不start。

目前没有 runtime current-task 指针，使用明确子任务路径继续规划；不得为了设置指针提前 start。后续若 CLI 需要 session identity，遵循其原生提示建立当前会话标识。

## 1. 协议与派生图标

- [ ] 按 `design.md` 接入上游 nullable `forum_icon_pre` 和 `PreferenceKey.BOARD_ICON_URL`。
- [ ] 实现唯一 `BoardIconUrlResolver`：HTTPS、已知旧 HTTP origin、目录/主机校验、普通/合集/负 fid/default 与 unchanged 判断。
- [ ] Repository 在现有 fastjson 边界处理非字符串元数据，避免坏图标字段使其他合法分类丢失；复用该逻辑读取旧远程缓存。
- [ ] 实体增加可观察、双向 JSON 排除的派生 iconUrl，读取旧 JSON 后按当前前缀重算。
- [ ] 覆盖 preference → raw cache → default 优先级；旧缓存 IO 返回不能覆盖较新的远程前缀。
- [ ] 只有顶层 object 且原始 `result` 为数组（含空数组）可提供前缀；补缺失/null/对象 result 与 preference 错误类型/读取异常案例。

## 2. 当前状态应用与 UI

- [ ] IO 只请求/解析，Main 应用独立的前缀及分类更新；加入单在途 guard，保留一天触发/完成尝试节流和取消语义。
- [ ] 递归更新根树及收藏的全部实际实体；覆盖同板块多对象、重复节点、初始化、新增、重载和恢复路径。
- [ ] 新增节点沿现有分类范围附着到合法父节点；以当前 map 已知 ID（含收藏）去重并逐次更新，保留已有顺序及收藏-only ID 的原有过滤行为。只有新增时冻结完整根树快照并串行写文件。
- [ ] View 消费实体 iconUrl，保留本地 drawable、Coil placeholder/error 与尺寸。
- [ ] 新节点通过内容 revision 和渲染 children 快照进入现有 Lazy grid；保持 Pager、格子和拖动状态，不以 revision 重挂载。
- [ ] 保持收藏候选写入、失败回滚、账号无关性及主页排序 overlay，图标单独变化不写两个列表文件。

## 3. 有意义的离线验收

优先在现有 board 测试包增加 `BoardIconUrlResolverTest`、`ForumBoardIconRefreshTest`，按需要扩展持久化测试。测试必须调用生产路径中使用的函数/状态操作，可抽取窄 internal 入口并注入时钟/请求/保存回调；不为测试另写一份合并算法，不引入整套架构或 JSON 迁移。

| 验收 ID | 输入/动作 | 必须验证的结果 |
| --- | --- | --- |
| U1-A1 | HTTPS 默认/替代主机、末尾斜杠、负 fid、同时有 stid | 精确普通/合集 URL；stid 优先；结构节点无远程 URL |
| U1-A2 | null/空串/非字符串/非法协议/错误目录/userinfo/query/fragment/退役主机 | 保持前缀与已有图标，其他合法分类仍可用；已知 img4 HTTP 正确转换 |
| U1-A3 | result 无新增，只有前缀改变 | 现有普通与合集图标都更新，包括树/收藏分离对象和树中重复节点；重复同前缀不发无效更新 |
| U1-A4 | Compose SnapshotStateObserver 观察实际实体 iconUrl 后更新前缀 | 观察者被通知并读到新值，不依赖 id-only List.equals 失效；实际 View 读取同一属性 |
| U1-A5 | 合法新增节点，前缀不变/改变 | 新节点有正确图标；内容 revision 与分组 children 快照包含新增内容；旧节点顺序不变 |
| U1-A6 | 旧树/收藏 JSON、原始远程缓存、坏缓存、重启 | 无字段也可读；旧缓存可恢复有效前缀；坏缓存回默认；iconUrl 不序列化且不接受磁盘注入值 |
| U1-A7 | 排序中刷新、取消/保存、旧保存失败晚于新排序/新增收藏 | 收藏成员、稳定键、顺序与原回滚语义保持；恢复/重载对象仍使用最新图标 |
| U1-A8 | 仅前缀变化、增加节点并保存 | 前者零列表写入；后者持久化只有原有根层级且完整，收藏根/后代未扁平化；延迟写入用冻结快照 |
| U1-A9 | 一天内/到期/重复点击/失败/取消、延迟旧缓存读取 | 原节流保留、一次在途、无自动重试、取消释放、旧缓存不覆盖新前缀 |
| U1-A10 | 当前内置 drawable、占位/错误路径和拖动接线 | 原显示优先级与手势/无障碍接线保持；不以新 revision 改键或重建页面 |

运行现有 `ForumBoardBookmarkPersistenceTest`、`HomeBoardOrderTest`、`HomeBoardOrderContractTest`；最后一项已经通过 fastjson 读取真实的带尾逗号/字符串数字 asset，不重复构造同质测试。
若安全快照改动使旧的 `localBoardList.toList()` 字符串断言不成立，以真实保存根顺序/层级的行为断言替代，不保留不安全的异步浅拷贝。

## 4. Trellis 实施与检查

批准后主会话加载对应 Phase 2.1；派发 `trellis-implement`，再派发 `trellis-check`。每个提示以明确的 Active task 路径开始，工具 workdir 必须是独立工作区。
所有代理先处理原生 context 注入或 fallback，完整读取 PRD/design/implement；超过注入上限的规范按段读取，不把截断内容当成全文。
主会话负责收敛发现、必要规范更新和最终整合。代理仅修改 U1 文件范围，不覆盖其他会话改动。

先跑 board 包的聚焦单测，再执行项目要求的最终 Android 质量门。最终 gate 可以合并为一次 Gradle 调用，覆盖以下任务；已通过且代码未再变更时不重复运行：

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests 'gov.anzong.androidnga.activity.compose.board.*' --console=plain
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
```

完整质量门直接按规范运行全模块 `lintDebug --rerun-tasks`；此后仅在报告缺失、不可信或代码变化时补跑，不重复已经有效的检查。
从各模块 XML 确认单测实际执行且无失败，以及 settings.gradle 中全部 13 个 Android 模块 lint 报告存在且 Error/Fatal 为 0；`abortOnError false` 的进程退出码本身不算通过。
不运行 aggregate `test`，避免进入需要签名的 Release/Preview 图；U1 不要求签名打包。
独立工作区构建前配置已有 SDK/JDK，必要时复制本机忽略的 `local.properties`，不复制旧 build 目录或签名秘密。
设备/安装/截图/真实 NGA 请求本轮不执行，不把未执行写成已通过。

## 5. 提交与父任务衔接

- [ ] 检查 U1 范围无 Manifest、正文/编辑器/属地、SDK、JSON 依赖改动。
- [ ] 更新相关现行规范中的固定图标/Glide 旧描述，补充动态图标和持久化契约（规范正文用英文）；只改本工作区相关段落。
- [ ] 在 delivery/check 记录真实测试计数、lint 结果和未执行项；按 Trellis 后续提交步骤处理 U1 独立提交。
- [ ] 提交任务文档时识别最初复制的五任务/R7 调研来源，不将其写成“本轮新做的 R7 修复”，也不碰源工作区的未提交内容。
- [x] U1–U4 的设计均已完成，纳入统一审核。
- [ ] 整体批准后按父实施计划继续 U2、U3、U4，在既有批准范围内不重复逐项申请。

回滚：revert U1 产品提交即可；保留列表文件与排序设置，不清数据。父任务集成时基于其他会话实际交付再处理共享规范/文件，不能把当前工作区测试结果代表尚未合入的 R1–R6。
