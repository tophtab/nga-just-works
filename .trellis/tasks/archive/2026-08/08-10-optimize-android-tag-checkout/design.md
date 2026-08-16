# Android Checkout 与 versionCode 优化设计

## Checkout Decision

`actions/checkout@v4` 保持单一步骤，通过 ref 条件决定历史深度：

```text
refs/tags/* -> fetch-depth 1
main        -> fetch-depth 0
both        -> filter blob:none
```

稳定 tag 的版本身份完全来自 `GITHUB_REF_NAME`，只需要当前 tag 提交。main preview
仍执行 `git tag --merged "$GITHUB_SHA"`，因此保留完整 commit/tag 可达图；partial
clone 只省略历史 blob，checkout 当前工作树时仍按需取得全部当前文件。

## versionCode Layout

采用十进制 `Mmmppbbb` 布局：

```text
code = major * 10_000_000
     + minor *    100_000
     + patch *      1_000
     + build_slot
```

- minor、patch：`0..99`；显示时等价于两位字段。
- stable build slot：`0`。
- preview build slot：`first_parent_distance(stable_tag, GITHUB_SHA) + 1`，范围
  `1..999`。
- 最终 code：Android 支持的 `1..2_100_000_000`。

示例：

| Build | versionCode |
| --- | ---: |
| `5.5.0` semantic base (published APK remains legacy `4069`) | `50,500,000` |
| `5.6.0` stable | `50,600,000` |
| `5.6.0` preview slot 1 | `50,600,001` |
| `5.6.0` preview slot 27 | `50,600,027` |
| `5.6.1` stable | `50,601,000` |
| `5.10.12` preview slot 345 | `51,012,345` |

该布局为每个 patch 版本预留 999 个 preview code。下一 patch 的 stable base 比上一
patch 的最大 preview code 大 1，因此升级顺序不会交叉。迁移后的首个 code 远大于
现有 `4069`，不会形成降级。

## Preview Sequence

preview build slot 不使用全局 `GITHUB_RUN_NUMBER`，而使用最近稳定 tag 到当前 SHA
的 first-parent commit 距离加一：

```bash
distance=$(git rev-list --first-parent --count "${stable_base}..${GITHUB_SHA}")
build_slot=$((distance + 1))
```

这使同一 commit 的 workflow 重跑得到相同 code，正常向 main 追加 commit 时 code
递增，并避免全局 run number 最终超过三位。main 已因版本 tag 可达性保留完整提交
图，因此该计算不增加 blob 下载。

force-push/rewrite main 可能缩短 first-parent 距离，因而不属于受支持的发布历史操作。
first-parent distance 达到 999 时，加一后的 build slot 已为 1000；此时 workflow
必须明确失败，而不是溢出到下一语义版本区间。

## Testable Boundary

新增独立 Python 脚本负责解析 `X.Y.Z`、校验字段/build slot/Android 上限并输出
versionCode。workflow 负责选择 stable base 和 build slot，避免把算术与错误处理
埋在 YAML shell block 中。

验证分三层：

1. Python unittest 验证公式和所有边界。
2. Kotlin workflow contract test 验证 checkout 分流、partial clone、stable slot 0、
   preview first-parent slot 和旧 run-number offset 已移除。
3. workflow YAML 解析、focused Gradle test 与 `git diff --check`。

## Compatibility And Rollback

`versionName`、applicationId、签名、R8、APK manifest 校验、发布说明和 GitHub
Release 行为不变。回滚时 checkout 与 versionCode 可以分别恢复，但一旦包含新
versionCode 的 APK 已发布，不得再发布更小的旧式 code。
