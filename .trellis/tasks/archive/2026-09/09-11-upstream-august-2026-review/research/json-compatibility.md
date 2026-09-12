# JSON 兼容性：哪些修复可能只是补齐上游升级后的差异

## 结论

不能把 `93acf42a`（过滤 `jdata`）和 `750871b3` 中的上传 JSON 解析选项，直接认定为当前 fork 漏掉的两个已复现 bug。
当前受影响入口使用 fastjson 1；上游先迁移到 fastjson 2，再加入这两项兼容修复。离线合成实验确认了相关默认行为的差异。

这不证明游戏综合讨论区当前线上一定能打开，也不覆盖所有服务器响应；它证明借鉴时需要先确认解析库和输入条件。

## 实验边界与输入

- 日期：2026-09-11。
- 当前代码：`main@8284c703`。
- JVM：OpenJDK 17.0.20；使用本机已有 Gradle 缓存，未下载依赖，未请求 NGA。
- 对比依赖：`com.alibaba:fastjson:1.1.71.android` 与 `com.alibaba.fastjson2:fastjson2:2.0.59.android8`。上游 `25652de8` 的集中迁移使用后者；这不是两个库最新发布版本的比较。
- `jdata` 实验直接编译当前真实 `TopicListBean.java` 和 `JavaBean.java`，反射调用两个库的 `parseObject(String, Class)`；成功还必须保留合成版块名称，不能只看“没有抛异常”。
- 过滤表达式逐字取自上游 `22ba3082` 的 `TopicConvertFactory.java`。
- 上传实验调用实际使用的 `parseObject(String)` 路径，并检查嵌套 URL 字段。
- 所有输入都是合成数据。示例 URL 使用 `example.invalid`；没有真实用户、帖子、Cookie 或上传 token。

## jdata：24 种组合

每个输入分别测试两个库、原始输入/先应用上游过滤表达式，共 6 × 2 × 2 = 24 种组合。

| 合成输入 | fastjson 1 原始 | fastjson 2 原始 | fastjson 2 + 上游过滤 |
| --- | --- | --- | --- |
| 普通合法 JSON | 成功 | 成功 | 成功 |
| `jdata` 中有合法转义引号 | 成功 | 成功 | 成功 |
| 首字段 `jdata` 含 `\x5C`，紧跟逗号 | 成功 | JSONException | 成功 |
| 嵌套 `jdata` 含 `\x5C`，紧跟逗号 | 成功 | JSONException | 成功 |
| 最后一个字段 `jdata` 含 `\x5C` | 成功 | JSONException | JSONException |
| `jdata` 键与冒号间有空格，值含 `\x5C` | 成功 | JSONException | JSONException |

fastjson 1 加过滤的 6 种输入也全部成功。原始明细见 [probes/result.tsv](probes/result.tsv)。

上游表达式只匹配无额外空格、字符串值后带逗号的形式，并不等于通用的“忽略 jdata”。此外，上游把 `catch (NullPointerException)` 改成 `catch (Exception)`，但 `JSON.parseObject` 仍在 `try` 外，不能据此宣称已兜住 JSON 解析异常。

当前入口：`nga_phone_base_3.0/src/main/java/sp/phone/mvp/model/convert/TopicConvertFactory.java:5,35-55`。
上游来源：[93acf42a](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/93acf42a5b6601f6ee6e257bb31895a3b50ad458)。

## 上传对象键不带引号：6 种组合

| 合成输入 | fastjson 1 默认 | fastjson 2 默认 | fastjson 2 + AllowUnQuotedFieldNames |
| --- | --- | --- | --- |
| `{"data":{"url":"https://example.invalid/a"}}` | 成功 | 成功 | 成功 |
| `{data:{url:"https://example.invalid/a"}}` | 成功 | JSONException | 成功 |

明细见 [probes/upload-result.tsv](probes/upload-result.tsv)。这解释了 `750871b3` 为什么在迁移后需要增加 `JSONReader.Feature.AllowUnQuotedFieldNames`。

## 对采纳顺序的影响

1. 当前仍用 fastjson 1 的入口，不应仅凭上游 commit 标题就紧急加入这些补丁。
2. 若另行统一到 fastjson 2，应把这两类输入放入迁移兼容性要求，并补齐末字段、空格、嵌套等边界。
3. 不应全局宽松解析所有响应；应由具体操作的解析入口处理已确认的格式。
4. 不能用这些合成案例证明真实论坛或上传链路完整可用；本次没有发送任何写请求。

## 复现材料

- [probes/JsonProbe.java](probes/JsonProbe.java)：两库解析真实 TopicListBean，使用上游原表达式。
- [probes/UploadJsonProbe.java](probes/UploadJsonProbe.java)：上传形状的三种解析方式。
- `probes/fixtures/`：六个合成 JSON 文件。
- 两个 TSV 文件：2026-09-11 实际执行结果，包含预期出现的异常。

在仓库根目录，将 `PROBE_JARS` 设为上述两个缓存 JAR 的冒号分隔路径、`PROBE_CLASSES` 设为一个临时输出目录后，可运行：

```bash
PROBE_DIR=.trellis/tasks/archive/2026-09/09-11-upstream-august-2026-review/research/probes
javac -cp "$PROBE_JARS" -d "$PROBE_CLASSES" \
  lib_base_common/src/main/java/gov/anzong/androidnga/common/base/JavaBean.java \
  nga_phone_base_3.0/src/main/java/sp/phone/http/bean/TopicListBean.java \
  "$PROBE_DIR/JsonProbe.java" "$PROBE_DIR/UploadJsonProbe.java"
java -cp "$PROBE_CLASSES:$PROBE_JARS" JsonProbe "$PROBE_DIR/fixtures"
java -cp "$PROBE_CLASSES:$PROBE_JARS" UploadJsonProbe
```

编译产物和依赖 JAR 没有保存到仓库。
