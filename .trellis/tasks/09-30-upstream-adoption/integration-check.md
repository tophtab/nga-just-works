# 集成验收记录 — PASS

实施分支 `feat/upstream-adoption`；起点 `557f7bea`，已通过 `0eebe1e5` 整合正式main修复 `c768a3cb`。源仓库在途89份规划文件已复制校验并额外备份，未覆盖其内容。

## 已完成的代码与交叉验收

| 交叉面 | 本次证据 |
| --- | --- |
| U1 × U3 | 图标观察/排序/缓存测试、JSON2双向排除iconUrl、固定旧格式文件与新旧三方向往返；见U1 check及U3 B0/B2/B5 evidence |
| U2 × U3 | 严格相对标签、完整前缀/原源文/音频回归；两种reader与renderer套件，U2离线72种布局场景；最终U4全模块套件继承 |
| U3 × 阅读/草稿 | B0–B5独立review通过；九份旧golden、156+104+72旧facade输出基线保持；账号/真实楼层/分页/错误/缓存/草稿既有契约未改 |
| U4 × U1/U2/UI | 两处Activity迁移生命周期callback，面板先消费、单次fallback；4项生产策略测试、原drawer/WebView/Up/SwipeBack/草稿所有权保持 |
| U3 × U4 | 新工具链Debug构建、820项测试、13模块新鲜lint0 Error/Fatal；R8 8.10.24生产bean classfile夹具6项通过；实际app Release R8及Preview Kotlin/Java编译均已通过 |
| 外部main修复 | 回复搜索使用回帖作者的正式修复保留；新增JSON调用/测试迁移JSON2；4项新导航测试及完整套件通过 |
| SDK/CI/原生 | 13模块compile36/JVM17，min29/target36；37项Python脚本测试含target35拒绝；新Debug APK三库ELF/ZIP16KB通过；Release/Preview独立manifest身份正确 |

最终U4证据见 `../09-30-upstream-sdk-36/research/implementation-evidence.md` 及其JSON/日志。完整lint共826项warning，不以退出码代替Error/Fatal逐报告检查。

## 最后两项实际验收已补齐

在用户对具体最小收尾方案继续指示后，仅在构建进程环境加载现有签名配置；未打印或复制配置，未修改guard。
配置后的实际dry-run仍为300/230任务，均不包含APK/AAB打包或签名任务。

- 实际app `minifyReleaseWithR8 --offline`通过（3分7秒）；实际DEX中8项关键bean、10个ThreadPageInfo别名访问器注解及board派生字段注解保持。实际mapping/合并规则/DEX的路径与哈希见U4 `app-r8-outputs.json`；64MB mapping保留在build输出，不入库。
- `compilePreviewKotlin` 与 `compilePreviewJavaWithJavac`通过（13秒）；三个variant manifest再次核对正确。
- 未新增产品/keep规则改动，因此已通过的820测试、37脚本测试、13模块lint及新APK静态证据继续有效。

最终独立结论见U4 `research/implementation-check.md`。U3-A7/U4-A7与父交叉验收具备实际证据，最终独立check已通过，可完成本轮归档。

## 发布准备与边界

用户已明确授权完成后commit、finish-work、push并发布新版本；计划稳定版本6.2.0，`release-notes/6.2.0.md`通过校验。
最终独立check通过后提交U4与集成记录，执行五任务finish-work/会话记录，重新检查main/tag后推送。源main工作区另有测试精简在途改动，本轮不触碰；从隔离工作区将已整合的HEAD推送远端main。沿用标签驱动CI打包发布；推送后不轮询CI、不提前宣称APK已发布成功。

设备/ADB/安装、真实NGA/CDN、预测返回动画、picker/IME/大屏、16KB设备运行均未执行；按项目政策不自动扩展为用户必须补做的事项。
