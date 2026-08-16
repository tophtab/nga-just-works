# Android Checkout 与 versionCode 优化实施计划

## 1. Checkout

- 将 checkout depth 改为 tag `1` / main `0` 的 ref 条件表达式。
- 为 checkout 启用 `filter: blob:none`。
- 保留 main 的 `git tag --merged "$GITHUB_SHA"` 逻辑。

## 2. versionCode Derivation

- 新增独立 Python 脚本，实现 `Mmmppbbb` 公式、格式/字段/build slot/Android 上限
  校验和 CLI 输出。
- 新增 Python unittest，覆盖稳定、preview、两位 minor/patch 和失败边界。
- 在 workflow stable 分支使用 build slot `0`。
- 在 main 分支通过 `git rev-list --first-parent --count` 计算距离并加一，作为 preview
  build slot。
- 删除 `4043 + GITHUB_RUN_NUMBER` versionCode 公式；保留现有 debug versionName。

## 3. Contract Tests

- 扩展 `ReleaseWorkflowContractTest`，覆盖 checkout depth、`blob:none`、版本推导
  脚本调用、stable/preview build slot 数据流和旧 offset 移除。
- 保留所有现有发布身份、签名、校验与说明断言。

## 4. Validation

- `python3 -m unittest scripts/test_derive_android_version_code.py`
- focused `ReleaseWorkflowContractTest`
- workflow YAML parse；可用时运行 `actionlint`
- `git diff --check`
- 不运行 ADB、安装、instrumentation 或真实 NGA 网络访问。

## 5. Delivery

- 运行 Trellis full-scope check 并修复确认的问题。
- 判断是否需要更新 Android quality spec。
- 创建独立提交，执行 finish-work/archive/journal，push `origin/main`。
- 不等待或监控 push 后的 GitHub Actions。实际 main/tag checkout 时长与生成的
  versionCode 留待自然触发的 workflow 记录。
