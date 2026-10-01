# 上游吸收整体实施计划

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。

## 统一审核之前

- [x] 复用现有父子任务，建立独立工作区并带入未提交资料。
- [x] 固定上游与本 fork 基线，保留原工作区改动，明确 R7 排除范围。
- [x] 四项 PRD/design/implement、消费者/平台/媒体证据全部收敛。
- [x] 独立方案核查处理跨任务冲突，全部 context/引用校验通过，见 planning-validation.md。
- [x] 完成统一审核入口 review.md，包含四项采用/适配清单、实施顺序、验收和限制。
- [x] 用户已于2026-09-30确认U1–U4整体方案，含U3全部B0–B5。
- [ ] 按用户要求，在新会话启动实施；本轮仅交接。

## 整体获批后的执行顺序

| 阶段 | 工作与落点 | 退出条件 |
| --- | --- | --- |
| S1 | start 现有 U1，按其 implement/check 接通图标链 | U1 验收通过，记录独立提交/回滚点；U2/U3 后续继承 |
| S2 | start 现有 U2，新增标签/CSS，保持 typed/absolute 和附件链 | U2 行为/HTML 样式与原有媒体回归通过 |
| S3 | start 现有 U3，按其完整分批计划推进 | B2 完整切换并删除旧 runtime/keep，B5 才删除无消费者工具；实际 R8 如留待 U4，U3 保留未完成验收 |
| S4 | start 现有 U4，必要工具链与平台适配 | 全工程 Debug gate、Release R8、Preview 编译及最终 SDK/manifest 检查通过 |
| S5 | 父集成复核、规范与交付记录 | 实际代码整合版本同时满足下表，未执行项明确标记 |

同一个整体批准覆盖上述确定的子任务/批次，不在每个子任务开始时重复申请批准。用户改变方案或实施发现会实质改变范围/产品行为时，带具体差异返回审核。
主会话协调 Trellis implement/check 子代理、范围、规范和提交；代理只在独立工作区执行。不要 start 父任务代替子任务，也不新建迁移/清理子任务。

## 父级集成验收矩阵

| 交叉面 | 必须保持的观察结果 | 证据来源 |
| --- | --- | --- |
| U1 × U3 | fastjson2 下旧根树/收藏可读，iconUrl 不序列化；坏前缀不改变排序 | 图标状态测试、JSON 跨版本 fixture、实际文件往返 |
| U2 × U3 | 两种帖子来源输出相同新视频标签效果；源文、图床、音频、评论/签名保持 | decoder/renderer 与普通/兼容 adapter fixture |
| U3 × 阅读/草稿 | 账号、分页、真实楼层、错误、旧正文、草稿和两类缓存语义不变 | 现行 reader/cache/draft contract suites 与迁移矩阵 |
| U4 × U1/U2/UI | target36 的 insets/返回/窗口行为不破坏板块排序、读帖和编辑返回 | 平台适配代码/资源检查、可执行本地回归，设备项标未执行 |
| U3 × U4 × R8 | Kotlin/Java 属性与 JSON 注解经混淆仍具备所需元数据；SDK36 可编译 | R8 minify 成功、mapping/keep 配置检查及序列化行为测试 |
| 外部会话 × 本分支 | 未覆盖源脏树；正式合入的 R1–R6 逻辑和规范保留 | 实际 merge/rebase diff 与受影响套件；不能仅引用旧报告 |

## 最终本地质量门

用户主力为Android15/API35，平台兼容性以其现有体验不退化为重点，再覆盖Android16必要适配。

按各子任务先跑聚焦回归；在最后一次产品修改和必要集成后运行：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
```

核对 settings.gradle 中全部 Android 模块的 XML 报告：单测真实执行且无失败；lint 报告齐全并且 Error/Fatal 为 0。`abortOnError false` 的退出码不能单独作为证据。
最终质量门按规范直接运行一次全模块 `lintDebug --rerun-tasks`，不先跑普通 lint 再重复；此后若结果可信且源码未变更，不重复全量 gate。
Release R8/Preview 的无签名验证命令以及必须的 SDK/AGP/JDK 版本由 U4 implement 固定；不运行会自动发布/安装的任务，不绕过签名 guard。

不执行真实 NGA 请求、ADB、安装、设备测试或发布。它们不是这次统一审批时额外要求用户提供的事项；交付明确区分离线验证与未执行的实机/服务可用性结论。

## 交付与回滚

- 每个 U 项可独立提交与 revert；U3 内部批次也留可回滚点，不能把所有迁移塞进一次不可诊断改动。
- 本轮从源工作区复制的任务/R7 归档须保留来源说明，不把已有调研说成本轮产品实现。
- 若新依赖/平台检查失败，修复属于该子任务的原因；保留其余已通过子任务与用户数据。不隐藏失败、不降低 lint/test 门槛。
- 最终更新每个子任务和父任务的真实交付/检查结果。四个子任务未全部验收前，父任务不能报告完成。
