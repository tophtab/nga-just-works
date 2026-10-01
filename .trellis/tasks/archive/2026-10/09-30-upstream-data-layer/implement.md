# U3 完整分批实施计划

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。

## 批次与退出门

| 批次 | 文件/工作责任 | 通过后才进入下一批 | 回滚点 |
| --- | --- | --- | --- |
| B0 基线与 golden | 完整消费者清单的43生产/12测试文件及隐式持久化；继承 U1/U2，核对已正式交付的 R1–R6；补全部旧格式/字段 fixture | 当前 reader/cache/AI/profile/board 与新 golden 在旧库通过，记录实际基线 | 仅测试/资料 |
| B1 局部测试入口 | TopicConvertFactory、TopicPostModel 上传、AvatarFileUploadTask、SafeJsonParser、ProfileWebUserParser 的必要纯 decode helper；保持旧库 | jdata 六种、alias、无引号上传、特殊键、错误 envelope 和原结果保持；已有纯函数不额外包层 | 单独撤 helper 提取 |
| B2 JSON2 原子切换 | root/common/core/catalog依赖；清单全部43/12文件及U1新增代码；3类旧注解/U1 iconUrl、两处 static toJavaObject、cache null writer、两个 ParserConfig 入口、两个缺marker bean、proguard | 全操作与全部存储三方向往返通过；runtime/kapt依赖分别核验；最小 shrinker fixture 通过后才删旧runtime/keep | 单笔撤全部依赖/类型/API/注解改动，数据不迁移 |
| B3 wire DTO/core decoder | core_data Kotlin/JVM17；ReadThread/Post/User/Topic/AttachmentWire；core→core_data依赖与 ReadThreadWireDecoder（输入已解析data与core mode，输出typed wire/shape问题，不重parse raw） | 严格有效性、用户关联、计数/顺序、缺用户/组、scalar/结构化正文测试；此时不切生产 facade | 撤新增DTO/decoder/依赖 |
| B4 旧公共 facade 接入 | ArticleConvertFactory 保留 getArticleInfo/getScopedArticleInfo/renderRow；新增 ReadThreadLegacyMapper；NormalArticleParser仅必要适配 | 普通/legacy重放、App既有链、正文/身份/楼层/缓存/错误全对比；先附件/评论/黑名单再HTML | 撤 facade接线和mapper，JSON2批可保留 |
| B5 清理和联合验收 | 删除确认无调用 app MessageConvertFactory、副本或被B4替代且已无调用的内部helper；活跃message parser保留；规范与清单更新 | 无漏迁runtime消费者；U1持久化/U2媒体联合回归；全模块质量门/R8证据记录 | 纯删除独立可恢复 |

详细源文件清单和逐字段责任是 [consumer-inventory](research/consumer-inventory.md) 与 [迁移依据](research/migration-design-evidence.md)，实施者必须完整读取，不能仅按表中示例文件替换 imports。
如 R1–R6 尚未正式交付，可完成隔离基线检查，但最终集成记录明确未包含版本；不能为追求“最新”把原工作区未提交文件复制过来。

## 必须执行的行为矩阵

| ID | 范围 | 验收关键点 |
| --- | --- | --- |
| U3-A1 | 全部网络/数据入口 | ordinary/App/topic/upload/message/notification/profile/动作/AI 各自成功与失败，保持 wrapper/GBK/字段类型/别名和 typed failure，不增加请求或重试 |
| U3-A2 | jdata/特殊JSON | 六种存档 jdata tree→bean 正确保留板块名；unquoted入口局部兼容；@type/$ref普通数据、无类加载/引用替换、BigDecimal、深度/大小与尾部拒绝 |
| U3-A3 | 正文和作者 | String/Number/null/object/array与字段缺失不同；OP/匿名/黑名单/client详情/WP/17/附件/评论顺序保持；来源可读/可编辑能力不伪造 |
| U3-A4 | 页与身份 | full/author/PID/search、resolvedTid、真实floor、未知/末页size、账号/generation/source、CONTENT/FORMAT/EMPTY/AUTH/RATE_LIMIT等及fallback资格保持 |
| U3-A5 | 全部持久化 | inventory存储表每行三方向往返；null/Int/Long/数组顺序、原文、owner/layout/atomic恢复、legacy ZIP、隐式表情偏好；不清数据 |
| U3-A6 | U1/U2联合 | JSON2后iconUrl双向排除、图标观察/排序保持；新视频和typed/audio/完整前缀及双来源输出保持 |
| U3-A7 | JSON反射与R8 | marker/Annotation/Signature、两个原缺marker bean、cache handle/Pattern/iconUrl排除；最小生产bean缩减fixture与实际合并keep配置核验 |
| U3-A8 | 草稿/Room/模块边界 | Bundle键/附件编辑状态、Room version1/columns不变；core无app依赖、账号/网络/缓存副作用；旧公共Java调用接口兼容 |

测试以现有 NormalArticleParserTest/AppArticleParserTest/ArticleConvertFactoryTest、ArticleCacheStoreTest/ArticlePageCacheTest、AuthorLocationStoreTest/ProfileLocationParserTest、AI/消息/board套件为基底；新增有意义的字段/存储/shrinker fixture，不另外实现一份被测算法。
B0/B2 的旧库 oracle 放测试专用依赖或研究 probe，APK runtime 不能同时携带旧库作为 fallback。保存脱敏合成样本，不取真实账号/私信/草稿。

## 检查与执行方式

每批 implement 后由 check 核对实际diff及对应行为门；先受影响模块，再在 B2/B4/B5 稳定检查点完成必要全工程 gate（完整 lint 直接使用规范要求的 --rerun-tasks），已通过且未再变更的结果不机械重复：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug testDebugUnitTest --continue --console=plain
./gradlew lintDebug --continue --rerun-tasks --console=plain
./gradlew :nga_phone_base_3.0:dependencyInsight --dependency fastjson --configuration debugRuntimeClasspath
./gradlew :nga_phone_base_3.0:dependencyInsight --dependency fastjson --configuration releaseRuntimeClasspath
./gradlew :nga_phone_base_3.0:minifyReleaseWithR8 --dry-run --console=plain
```

核对所有模块 XML 实际测试数/失败和 lint 0 Error/Fatal；缺失/不可信报告按规范重跑，不降低阈值。
B2 用当前工具链的同版本 R8 `--classfile` 缩减实际生产 bean/注解与生产 keep 规则，再在本机 JVM 从动态输入的类名反射读取缩减后的类；不能添加 fixture 专用全类 keep，不能让直接调用/常量类名使 R8 推断保留而掩盖缺 marker。先检查实际 app minify 的 dry-run 图：如没有触发签名 guard 的 app assemble/bundle/package 任务，执行 `:nga_phone_base_3.0:minifyReleaseWithR8` 并检查本次 mapping/合并规则，不能读旧产物。若依赖图触发宽泛 guard，保留 classfile/真实规则证据并记录实际任务约束，不能修改 guard 或读取签名秘密；将实际app minify明确记为未完成项，U4再核对新图并补实际证据，不能把缺项报告为已通过。两种证据均不声称已跑 Android minified runtime。
U4 更换工具链后再运行同一语义样本与实际 `minifyReleaseWithR8`，检查新 Kotlin/AGP 没有丢字段。无需签名打包、发布或设备操作；所有网络行为均使用本地 fake/fixture。

收尾 `rg` 检查生产中的 `com.alibaba.fastjson.`、SerializerFeature、ParserConfig、旧JSONField和依赖；研究/旧库oracle/构建工具classpath区别记录，不用全仓字符串归零误删历史资料。

## 最终交付

每批记录基线、采用/保留差异、实际测试结果与回滚提交；B5 及全部验收门通过才称 U3 完成。若实际 app minify 因签名 guard 等任务约束留待 U4，B5 只标记代码批次完成，U3 仍保留未完成验收、不得关闭或归档；U4 取得本次实际 R8 联合证据后再关闭 U3。JSON/模型批次都属于现有子任务，整体批准后不重复逐批向用户申请许可。
任何会改变文件schema、普通读取恢复、账号选择或既有产品行为的发现返回父规划，不通过“兼容补丁”夹带 R7。

## 规划审核闭环

[独立审核](research/planning-review.md)提出的局部tree flag、B2新R8证据与单次core/app解析边界已写入设计/本计划。Topic只保留本入口已有wrapper，不借机添加普通读帖token修复。精确错误映射已固定在设计的严格/legacy边界，B3/B4夹具逐项断言。签名guard导致实际minify未执行时必须保留未完成状态，不能引用旧mapping或提前归档U3。
