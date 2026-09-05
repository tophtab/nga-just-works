# 执行计划：历史缓存旧图床图片失效

> 当前任务保持 `planning`。另一个会话应先向用户展示并取得对最新规划摘要的明确批准，再运行 `task.py start`；本会话不修改产品代码。

## Step 1 — 建立退役页面主机的失败测试

修改 `NgaImageHostContractTest`：

- 为自动模式加入 `img*.nga.178.com`、`img*.ngacn.cc` 的裸 host、scheme 和 `/attachments` 代表形态；期望固定安全前缀。
- 保留未知合法页面主机以及页面 A/B 不串值断言。
- 用退役服务器值复跑三种手动模式，锁住优先级。
- 增加 `/avatars/` 遗留绝对 URL 的非附件归一化契约。

修改 `ArticleConvertFactoryTest`：

- 添加历史 `_ATTACH_BASE_VIEW = img.nga.178.com/attachments` 的合成页面，期望解析为默认前缀。
- 不使用用户 ZIP 或真实路径。

验证新测试在实现前按预期失败，且失败只指向缺失的退役主机兼容。

## Step 2 — 在 `NgaImageHost` 自动分支修正页面值

- 复用/提取完整 host 级别的遗留图床判断，避免另建漂移的字符串清单。
- 保留 `sanitizeServerAttachmentBaseView` 的现有格式解析职责。
- 仅在 `resolveAttachmentsPrefix(... MODE_AUTO ...)` 使用服务器页面值时，将已知退役 host 回退到 `DEFAULT_ATTACHMENTS_PREFIX`。
- 未知合法 host 仍返回页面级前缀；缺失/非法仍走原安全兜底。
- 不改变手动默认、img9、自定义模式，不引入静态页面状态或联网探测。

聚焦验证：

```bash
./gradlew :lib_base_common:test
./gradlew :nga_phone_base_3.0:testDebugUnitTest --tests '*ArticleConvertFactoryTest*'
```

**回滚点 A**：resolver 和对应测试可独立回退；无持久化迁移。

## Step 3 — 接通头像遗留 URL 归一化

- 调整 `FunctionUtils.parseAvatarUrl`，让直接 URL、嵌套 JSON 提取结果以及其他既有返回路径统一经过 `NgaImageHost.normalizeLegacyHosts`。
- 优先添加行为测试；若现有 Android 静态依赖阻塞 JVM 测试，提取最小纯函数后测试，不通过联网、真实数据或宽泛 source-text 断言替代。
- 检查 `ArticleListAdapter`、`ProfileActivity`、评论头像现有调用均继续复用该入口，避免逐点重复改写。

聚焦验证：

```bash
./gradlew :nga_phone_base_3.0:testDebugUnitTest --continue
```

**回滚点 B**：头像入口改动可独立回退，不影响附件 resolver。

## Step 4 — 做全链路静态与行为检查

- 确认 `ArticleConvertFactory → HtmlData → ForumImageDecoder/HtmlAttachmentBuilder` 仍只传一个页面前缀。
- 确认正文相对图片、附件 HTML、附件 image URL list 都使用修正后的前缀。
- 确认没有把页面值写入 static、SharedPreferences 或 `ThreadRowInfo`。
- 确认非附件路径仍保留 `img` 编号，`img9.nga.cn` 仍使用 HTTP，板块图标和上传主机没有改动。
- 检查 `.temp/cache_20260905084551.zip` 及其内容未被加入版本库。

静态检查：

```bash
rg -n "_ATTACH_BASE_VIEW|resolveAttachmentsPrefix|sanitizeServerAttachmentBaseView|normalizeLegacyHosts|parseAvatarUrl" \
  lib_base_common lib_core nga_phone_base_3.0 lib_bu_message
git status --short
```

## Step 5 — 更新可执行规范

更新 `.trellis/spec/backend/nga-platform-access-rules.md` 的 `THREAD.PAGE Page-Scoped Attachment Host` 契约：

- 自动模式的“合法服务器值”排除已知退役 `img*.nga.178.com` / `img*.ngacn.cc` 图床；这些值确定性回退到默认附件前缀。
- 记录历史头像等绕过正文 decoder 的 URL 必须在其加载入口复用遗留主机归一化。
- 保持路径族和手动模式优先级不变。

## Step 6 — 质量门

聚焦单测：

```bash
./gradlew :lib_base_common:test \
  :lib_core:testDebugUnitTest \
  :nga_phone_base_3.0:testDebugUnitTest \
  :lib_bu_message:testDebugUnitTest \
  --continue
```

编译：

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

Lint：

```bash
./gradlew :lib_base_common:lintDebug \
  :lib_core:lintDebug \
  :nga_phone_base_3.0:lintDebug \
  :lib_bu_message:lintDebug \
  --continue
```

检查报告内容并区分既有基线与本任务新增问题。无新的用户授权时，不运行 ADB、设备测试或 NGA 在线探测。

## Step 7 — 最终审查与提交前检查

- 对照 PRD 的 AC1–AC9 逐项记录证据。
- 确认不存在导入时 JSON 重写、图片实体打包或网络 fallback 等范围膨胀。
- 运行 Trellis check，完成需要的规范更新，再按 Trellis finish/commit 流程交付。
