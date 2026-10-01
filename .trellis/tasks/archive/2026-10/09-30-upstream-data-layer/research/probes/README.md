# U3 离线 JSON 边界 probe

2026-09-30，`javac 17.0.20.1`，仅合成输入；没有 NGA 请求、账号文件、Android 构建或设备操作。JAR 来自本机 Gradle 缓存：

- `com.alibaba:fastjson:1.1.71.android`
- `com.alibaba.fastjson2:fastjson2:2.0.59.android8`

`JsonBoundaryProbe.java` 比较 bean 属性命名、特殊键、数字、null 与完整文档消费。`json-boundary-result.txt` 是实际输出。对象 `toString()` 默认省略 null，因此该输出中的 `pageSize` 缺失**不表示 parse 已删除该键**；真正缓存写入必须使用 WriteMapNullValue，缓存字段保留由现有 ArticleCache 测试进一步核验。

`JdataTreeProbe.java` 编译当前真实 `TopicListBean.java`，读取存档6个fixture，对比 fastjson2 直接 typed parse 与 tree→bean 路径。`jdata-tree-result.txt` 中 `tree=true` 要求保留正确板块名，非仅“没有异常”。结果支持 TopicConvertFactory 的局部树解析适配，无需采用上游 jdata 删除regex；不证明所有非标准JSON或真实接口都可用。

在独立工作区根目录复现（编译输出放临时目录）：

```bash
U3_PROBE_DIR=.trellis/tasks/09-30-upstream-data-layer/research/probes
U3_FIXTURE_DIR=.trellis/tasks/archive/2026-09/09-11-upstream-august-2026-review/research/probes/fixtures
U3_OLD_JAR=/home/toph/.gradle/caches/modules-2/files-2.1/com.alibaba/fastjson/1.1.71.android/2565ebe4cdd89d470cd5051cfe3e3b5e04bac54b/fastjson-1.1.71.android.jar
U3_NEW_JAR=/home/toph/.gradle/caches/modules-2/files-2.1/com.alibaba.fastjson2/fastjson2/2.0.59.android8/e3d3becdd73e3a5f0a7c38d9e720eaf84f49102c/fastjson2-2.0.59.android8.jar
U3_PROBE_CLASSES=$(mktemp -d /tmp/u3-json-probe.XXXXXX)
javac -cp "$U3_OLD_JAR:$U3_NEW_JAR" -d "$U3_PROBE_CLASSES" \
  lib_base_common/src/main/java/gov/anzong/androidnga/common/base/JavaBean.java \
  nga_phone_base_3.0/src/main/java/sp/phone/http/bean/TopicListBean.java \
  "$U3_PROBE_DIR/JsonBoundaryProbe.java" "$U3_PROBE_DIR/JdataTreeProbe.java"
java -cp "$U3_PROBE_CLASSES:$U3_OLD_JAR:$U3_NEW_JAR" JsonBoundaryProbe
java -cp "$U3_PROBE_CLASSES:$U3_NEW_JAR" JdataTreeProbe "$U3_FIXTURE_DIR"
```

初次探测的 research/probes/classes 编译目录已删除，仅保留源码/结果/说明。正式实施时将必要场景加入模块回归测试，研究probe不代替产品测试。
