# Research: U3 全部规划产物复核

- Query: 复核 U3 最终 prd/design/implement 的 B0–B5、JSON 局部选项、R8 验证可执行性与 core/app 边界。
- Scope: internal；仅规划文档、已有研究/探测和必要源码核对，无新增广泛盘点或产品实施。
- Date: 2026-09-30

## Findings

### 总体结论

完整 B0–B5 与已有证据一致：先把 JSON 默认行为变化独立切换，再抽原始 DTO/core decoder，保留 Java 展示/导航/缓存接口；全部43个直接生产消费者、12个直接测试消费者和隐式存储已经纳入。未发现遗漏整组消费者、误把草稿当 JSON、重复移植 App parser、或夹带 R7 的情况。

建议在提交 U1–U4 整体审核前收紧以下两项，并补足一项边界说明。它们不改变已规划范围、不需要新任务或新一轮逐批审批。

### R1：TopicConvertFactory 必须写明实际验证过的树解析选项

- 位置：`design.md` 的 JSON 入口表、`implement.md` 的 U3-A2。
- 当前正文只写“先 JSONObject tree，再 toJavaObject(..., SupportSmartMatch)”，但 `research/probes/JdataTreeProbe.java:11` 实际使用的是 `JSON.parseObject(s, JSONReader.Feature.DisableReferenceDetect)`。省略该选项会让实施者用默认树解析处理 `$ref`，不再是已经验证的路径。
- 建议写出完整操作级调用：

  ```java
  JSON.parseObject(normalized, JSONReader.Feature.DisableReferenceDetect)
      .toJavaObject(TopicListBean.class, JSONReader.Feature.SupportSmartMatch);
  ```

- 该选项局部用于此 tree→bean 适配，不改变全局配置；TopicConvertFactory 自有 wrapper 保留。表格中“原 wrapper/token 预处理”宜准确改为“原 wrapper 处理”：当前 `TopicConvertFactory.java:37-41` 只有 window 前缀剥离，没有普通读帖那套 token regex，不能顺手复制后者来放宽 TOPIC.LIST。
- 加一个嵌套 `$ref`/`@type` fixture，观察其为普通未消费字段且不改变 DTO 所需字段；截断/尾随第二对象仍失败。已验证的六个 jdata fixture 继续全部要求保留板块名，不改为仅断言无异常。

### R2：B2 的实际 Release 规则/mapping 检查不能依赖尚未运行的 U4 产物

- 位置：`implement.md` 的“B2 用固定 R8 版本…辅以实际 app Release merged keep/mapping 检查”，后文却只在 U4 明确运行 `minifyReleaseWithR8`。
- 问题：独立工作区可能没有新 mapping/configuration 输出；旧报告不能证明 B2 新注解/两个新增 marker 被实际 app 规则保留。U3-A7 既承诺真实合并规则核验，就应明确 B2 如何产生当批次证据，或者将不能产生的部分写成阻止最终关闭 U3 的整合门。
- 建议执行顺序：
  1. R8 版本锁定为 **B2 当前 AGP 8.6.1 所解析的 R8 artifact**，记录实际版本和 hash；不是自行任选最新版。U4 换 AGP 后用新解析版本重复同一 fixture。
  2. 先对 `:nga_phone_base_3.0:minifyReleaseWithR8 --dry-run` 核对任务图，确认无安装/签名/最终 APK 包装要求；若允许，B2 就运行当前工具链 minify 并核对本次 `configuration.txt`/mapping，U4 再复验。
  3. 若当前构建图/工具链确实不能执行，只能报告“classfile fixture 与源码规则通过；实际 app minify 仍未验证”，把 U4 联合门纳入 U3-A7 完成条件；不能把旧 mapping 当本次证据，不能先声称 U3 所有门通过。
- 签名边界必须精确表述：`nga_phone_base_3.0/build.gradle:25-31` 在 taskGraph 检查本 app 名称匹配 `.*(assemble|bundle|package).*(release|preview).*` 的任务。`minifyReleaseWithR8` 本身不匹配，但依赖图中的 `packageReleaseResources` 等若存在也可能匹配。静态源码**不能保证** minify 一定不触发此 guard；必须以 dry-run 图为准，不为检查读取签名凭据或运行 assembleRelease/Preview。

### R8 classfile fixture 的最小可执行约束

当前“本机 JVM 反射读取缩减后的类”方向可行，补以下约束即可避免产生自证式测试：

- 使用 R8 `--classfile` 输出，默认 Android dex 不能直接交给 JVM 加载。
- 输入真实生产 bean 的编译 class：至少 TopicListBean 嵌套类、ThreadPageInfo/ReplyInfo、BoardEntity（含 U1 iconUrl）、FilterKeyword、ReportTask.ResultBean、AvatarFileUploadTask.NonameUploadResponse，以及必要 JavaBean/注解等依赖。需要 Android/Kotlin/Compose 引用的类使用正常编译输出并提供对应 library classpath；不能用删掉注解/委托字段的简化复制品替代。
- fixture 消费实际 `proguard.cfg` 和生产 consumer-rules 中相关规则；不得对被测 bean 额外 `-keep class ... { *; }`，不得 `-dontshrink/-dontobfuscate` 全关闭，也不能为方便给遗漏 marker 的类额外保留。
- fixture harness 的 class/成员名从运行参数/数据输入读取，避免 R8 对常量 `Class.forName`/反射自动推断保留，把缺 marker 的问题遮住。只保留 harness 入口，测试用 new/reflection 指向 bean 的方式必须可审计。
- 验证缩减后 JSON 真实读取/输出及排除字段，不只检查 class 存在：authorId 等别名、nested bean、private avatar response、cache handle 与 iconUrl 双向排除、Pattern 不输出。
- 缩减 fixture 只能证明其输入类和规则；它不是 Android minified runtime 端到端执行。最终报告保持这一限制。

### R3：补一处 decoder 输入和失败结果说明，避免重复解析或向 core 倒灌 app 类型

- 位置：`design.md` 普通读取分层图与严格/legacy 模式段。
- 目前输出边界正确，但 decoder 输入是原始 String、已规范化 String 还是 JSONObject 未写明。若 app ArticleErrors 先 parse，core 再 parse raw，会出现两次解析及 wrapper/error 次序偏差；若 core 直接引用 ArticleErrors/ArticleFailure，违反“不依赖 app”契约。
- 推荐固定为：**app facade 保留现有原文、wrapper/token 修复和 scoped 的 ArticleErrors 检查；将修复后的 data 对象及独立 core decode mode 交给 core；core 返回只含 Read*Wire 的结果或其自有有限 shape 问题；app mapper 转成现有 failure/null。** JSONObject 是 fastjson2 库类型而非 app 类型，可作为 decoder 输入；不得从 core_data DTO 输出它给 UI。也可以采用等价单次解析签名，但须明确唯一解析者与错误映射。
- 至少把当前精确映射写在实现入口旁：`ArticleConvertFactory.java:83-88` 的坏 `__R`/count→FORMAT、声明范围内缺失/非对象 row→CONTENT；可选 topic 转换失败→null；scoped 结构化 body→sourceUnavailable，而不是丢整页或转 FORMAT。legacy 原有跳过坏 row/外层返回 null 保留。
- 这只是将既有约束落到函数边界，不需要新增错误框架、Repository、全局 JSON facade 或重写 AppArticleParser。

### 已核对且无须调整的部分

| 项目 | 结论 / 证据 |
| --- | --- |
| SafeJsonParser/ProfileWebUserParser flags | AllowUnQuotedFieldNames + DisableReferenceDetect + DisableSingleQuote 是实际2.0.59.android8可用选项；保留现有 guard 且不启用AutoType/ClassForName，符合现有测试 |
| null writer | 实际JAR `JSONWriter.Feature` 同时含 WriteMapNullValue 和 WriteNulls，文档选 WriteMapNullValue 无 API 拼写问题 |
| JavaBean keep | common consumer-rules 按 marker 保留类和成员；两个无marker反射bean确实需要精确保留；不需要全app keep |
| 数据存储 | raw/topicInfo 原文、explicit null、Int/Long/数组顺序、旧写新读与新写旧读，以及ZIP边界完整；保留Room和Bundle合理 |
| app/core | DTO/core不持有账号、query生命周期、网络fallback/缓存路径；Java adapter/renderer seam保留，对当前架构合适 |
| B0–B5 scope | B1只为可测试性提取必要局部函数，B2原子库切换，B3可未接生产，B4接线，B5仅删无消费者；可以按文档独立回退 |
| 通用质量门 | 已覆盖全模块 test/lint及实际报告判定；最终有效产物仍须遵守项目 `android-quality-guidelines.md:149-158` 的全模块lint重跑要求，不能只拿旧缓存报告证明当前状态 |

### Files / related specs / external references

审阅：当前 U3 `prd.md`、`design.md`、`implement.md`，以及 consumer-inventory/migration-design-evidence/probes。

源码锚点：TopicConvertFactory.java:37-41；ArticleConvertFactory.java:75-88,110-120,165-188；SafeJsonParser.java:16-30；ProfileWebUserParser.java:66-73；app build.gradle:25-31；gradle/libs.versions.toml:2；common consumer-rules.pro:5；app proguard.cfg:23-46。

相关规范：backend thread-detail-compat-contract、thread-page-cache-contract、ai-summary-contract、android-quality-guidelines；frontend android-migration-architecture。固定外部版本沿用已有研究：fastjson2 2.0.59.android8、当前 AGP 8.6.1、Justwen 25652de8/a6e4a890。不重新扩展网络研究范围。

## Caveats / Not Found

- 本轮只复核规划；没有执行 Gradle、R8、Android设备或网络请求，没有修改产品或主会话规划文档。
- 未从默认本机模块缓存定位到 R8 JAR，不能报告已运行缩减fixture；实施按实际 AGP 解析依赖记录版本，失败应如实记录，不是现在需要用户补权限的阻塞。
- R1–R3 的建议已发主会话，由主会话统一修改 design/implement；本研究仅写本文件。
