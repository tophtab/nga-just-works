# U1–U4 整体方案审核

状态：2026-09-30用户已统一确认四项方案，指定新会话实施；尚未启动产品实施。批准记录见 [approval.md](approval.md)。
专项与跨任务审核的修订及文档检查见 [规划核查记录](planning-validation.md)。

## 本次确认的范围

新会话按 [handoff.md](handoff.md) 从最新基线建立独立工作区并带入任务文档，按以下完整方案吸收上游。原规划工作区已清理。沿用现有父任务和四个子任务，保留其他会话未提交改动。
R7调研继续独立归档，产品修复/兼容或浏览器移除不进入本计划。

| 子任务 | 实施结果 | 重要保留/适配选择 | 详细产物 |
| --- | --- | --- | --- |
| U1 板块图标 | 有效服务端前缀更新普通/合集远程图标，仅前缀变化也刷新所有显示节点 | 内置图标优先；收藏/主页顺序/根树不变；旧原始缓存恢复前缀，无缓存时用默认等正常一天节流；派生iconUrl不写列表JSON | [PRD](../09-30-upstream-board-icons/prd.md) / [设计](../09-30-upstream-board-icons/design.md) / [计划](../09-30-upstream-board-icons/implement.md) |
| U2 视频 | 新增严格 `[flash]./…[/flash]` 与video自适应宽高/独占一行 | typed视频/音频/absolute链接、完整图床与源文保持；只转换有效区间，转义特殊字符；不换播放器、不改附件列表链接 | [PRD](../09-30-upstream-video-support/prd.md) / [设计](../09-30-upstream-video-support/design.md) / [计划](../09-30-upstream-video-support/implement.md) |
| U3 数据层/JSON | 全runtime fastjson1→fastjson2 2.0.59.android8；普通读取wire DTO/纯decoder下沉共享层，app薄mapper接旧Java接口 | 保留所有已有字段/身份/分页/错误/缓存；不机械改名HTML/展示类；六种jdata用局部tree→bean，不复制删除regex；无数据schema迁移 | [PRD](../09-30-upstream-data-layer/prd.md) / [设计](../09-30-upstream-data-layer/design.md) / [全部B0–B5计划](../09-30-upstream-data-layer/implement.md) |
| U4 SDK36 | compile/target36 + AGP8.10.1/Gradle8.11.1/Kotlin与Compose compiler2.2.21/JDK17；两处返回入口与CI适配 | min29/身份/签名/版本保持；Build Tools35.0.0及业务库保留；不全局重做insets或加权限；现有native无必升依据 | [PRD](../09-30-upstream-sdk-36/prd.md) / [设计](../09-30-upstream-sdk-36/design.md) / [计划](../09-30-upstream-sdk-36/implement.md) |

## 实施顺序与回滚

统一批准后按 **U1 → U2 → U3（B0–B5）→ U4 → 父集成验收** 执行。各子任务按需start/implement/check，在本次整体授权内不重复请求批准。

U3完整批次：

| 批次 | 内容 |
| --- | --- |
| B0 | 固定包含U1/U2及正式交付的外部改动的基线、旧行为与全部格式样本 |
| B1 | 在旧库下提取必要的局部纯decode测试入口，固定兼容矩阵 |
| B2 | 43个当前生产JSON文件、12个测试消费者、隐式存储和U1增量一次完整切换；R8/注解/别名/null/特殊键一起验证 |
| B3 | 新普通读取wire DTO与core decoder，先独立编译/测试 |
| B4 | 既有ArticleConvertFactory facade接新decoder/app mapper；Java展示、导航、缓存公共接口保留 |
| B5 | 删除真正无消费者的副本/helper，完成全链/跨U1-U2验收与规范更新 |

每项/批次保留提交和回滚点。B2跨模块类型切换须整笔回滚；B4可独立退回旧mapper而保留JSON2。没有要求清用户数据的回滚步骤。

用户主力手机为Android15/API35；U4以Android15现有体验不退化为主要兼容性目标，同时完成Android16必要适配，详见approval.md。

## 全部验收与最大风险

| 组 | 验收重点 | 风险控制 |
| --- | --- | --- |
| U1 | 异常/变化前缀、重复对象、Compose观察、旧JSON/重启、排序并发/回滚 | 图标派生值双向排除JSON，递归实际树，新增结构单独通知，无前缀-only列表写入 |
| U2 | 精确标签边界、邻接/畸形、属性转义、两来源与manual图床、离线CSS布局 | 不动旧typed规则；实际decoder测试+本地浏览器，不能声称实机播放通过 |
| U3 | 完整消费者/每种存储三方向往返、别名/null/数值、错误/source有效性、缩减后反射 | JSON切换与模型映射分开；保留public facade；不得把未知/截断响应伪造成功 |
| U4 | SDK/身份、返回fallback一次、CI拒绝错误target、全模块编译/lint/R8/native对齐 | 固定受支持工具链、dry-run签名guard、无无关依赖升级或平台opt-out |
| 集成 | JSON2保留U1排除和U2渲染；新工具链保留U3模型/反射；正式合入的R1–R6保持 | 独立diff/检查点与最终真实集成版本验证，不覆盖源脏树 |

详细矩阵/命令在各计划及 [父实施计划](implement.md)。项目门槛为全模块Debug单测、lint Error/Fatal为0、Debug构建；U3/U4补实际可行的Release R8/缩减fixture、Preview编译、workflow脚本与APK对齐。

## 已完成的证据与未执行项

已完成固定上游/当前源码复核、完整消费者与存储/R8盘点、官方Android16逐项适用性矩阵，以及任务文档/上下文/引用检查。
本轮运行了两库合成JVM probe：验证字段别名差异、@type/$ref/数值/完整文档消费，以及真实TopicListBean的六种jdata样例；读取旧AAR/APK完成native静态对齐检查。它们是设计证据，不是新产品验收。

尚未执行产品代码修改、Android构建/产品单测/lint/R8、设备操作、真实NGA请求、签名打包、提交或发布。所选SDK/工具链尚未缓存，实施时需先预取再运行离线gate。
设备播放、返回手势动画、大屏/IME、16KB设备运行与服务可用性不由离线结果保证；本轮不自动要求设备后续操作。

## 审核结论入口

用户已确认这份整体方案，授权既定四项及U3全部批次，指定新会话实施。任务暂保留planning以表示未start，implementation_approved=true；无需重复确认。
