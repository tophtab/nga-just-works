# 5.5.1 发布设计

## 发布边界

稳定发布的唯一产物来源是 tag 触发的 GitHub Actions。workflow 从 GitHub Secrets 恢复临时 keystore，执行 `verifyReleaseTag :nga_phone_base_3.0:assembleRelease`，独立校验 applicationId、版本、SDK、debuggable 和签名，随后生成 `.sha256` 并创建 Release。版本 code 由 `derive_android_version_code.py` 根据稳定 slot 计算，本地不复制该逻辑。

## 本地门禁

本地仅验证发布说明格式、已有 release workflow contract test 和 Git whitespace；这样可发现配置/说明回归，同时避免接触签名材料或产生与 CI 不同的 APK。tag 推送后停止，不等待远端 run。

## 回滚

若前置检查失败，不创建 tag；修复后重新检查。若 tag 已推送但 Action 失败，保留失败证据并按仓库发布流程处理，不移动或覆盖稳定 tag。
