# 修复历史缓存旧图床图片失效

## Goal

修复 Issue #6 暴露的历史缓存兼容问题：帖子页 JSON 中保存的
`data.__GLOBAL._ATTACH_BASE_VIEW` 仍可能指向已经退役的 NGA 图床，当前“自动”模式会把正文相对图片和附件继续展开到该旧主机，导致导入后的缓存以及原地历史缓存均无法重新加载图片。

用户打开历史缓存时，应用应在不改写缓存文件的前提下，把已知退役图床映射到当前可用的对应主机。对于本次证据中同样绕过正文解码链的旧头像地址，也应执行相同的遗留主机归一化。

## Background and Confirmed Facts

- Issue #6 评论报告：缓存时图片可见，导入后图片失效，回到原软件后也已不可见；现有导入/导出功能来自上游项目。
- 用户提供的 `.temp/cache_20260905084551.zip` 是有效 ZIP；归档中只有主题描述和两页原始 JSON，没有任何图片二进制文件。
- 两页 JSON 的 `time` 字段对应 2024-11-19，且都包含
  `_ATTACH_BASE_VIEW = img.nga.178.com/attachments`。
- 正文中有 3 条 `./...` 相对图片引用，另有 2 条附件记录；去重后共有 4 条附件路径。2026-09-05 的限量诊断中，旧主机无法解析，而同一路径放到 `https://img.nga.cn/attachments` 后均返回 `200 image/jpeg`。
- 两页还包含 23 条 `img.nga.178.com/avatars/...` 头像地址；该路径绕过 `ForumImageDecoder`。同日抽取一条路径验证，换到 `https://img.nga.cn/avatars/...` 后返回 `200 image/jpeg`。
- `ArticleListModel.cachePage` 只持久化主题描述与原始响应 JSON；`TopicListPresenter.exportCacheTopic` 只压缩 `files/cache/`，导入过程没有删除原软件图片的逻辑。
- `ArticleConvertFactory.resolveAttachmentsPrefix` 会读取缓存 JSON 的 `_ATTACH_BASE_VIEW`；`NgaImageHost.resolveAttachmentsPrefix` 的自动模式目前只做语法校验，因此会接受语法正确但已经退役的主机。
- 正文相对图片与附件已经通过同一页的 `HtmlData.attachmentsPrefix` 展开；这里不需要重新设计缓存路径格式。
- `FunctionUtils.parseAvatarUrl` 提取头像 URL 后直接交给 Glide，没有调用现有的 `NgaImageHost.normalizeLegacyHosts`。
- 项目现有图床契约要求按路径族处理：附件走 `img.nga.cn`（或用户手动选择的主机）；非附件遗留地址保留原 `img` 编号并迁移到 `.nga.cn`，不能把所有媒体路径一刀切到附件主机。

原始 ZIP 含帖子正文和用户数据，仅作为本地取证输入，不得加入 Git、测试 fixture、日志或失败快照。详细的脱敏证据见 `research/cache-export-forensics.md`。

## Requirements

### R1 自动模式识别退役的页面级附件主机

- `NgaImageHost` 继续作为附件地址解析的唯一权威。
- 自动模式收到以下已知遗留图床族作为 `_ATTACH_BASE_VIEW` 时，不得继续输出旧主机：
  - `img*.nga.178.com`
  - `img*.ngacn.cc`
- 因该字段专用于 `/attachments` 路径族，所有上述退役主机应落到固定安全前缀
  `https://img.nga.cn/attachments`；路径、文件名、查询串和画质后缀由现有渲染链保持不变。
- 识别应覆盖缓存中出现的裸主机加路径形式，并与当前已接受的 HTTP、HTTPS、协议相对形式保持一致。
- 自动模式收到未知但符合现有契约的非遗留服务器主机时，仍应按页面级值使用，不能把“自动”退化成固定域名。

### R2 保持手动模式和页面隔离语义

- `https://img.nga.cn`、`http://img9.nga.cn` 和自定义三种手动模式继续忽略 `_ATTACH_BASE_VIEW`。
- 用户明确配置的自定义主机不属于本任务的自动迁移对象；本任务只纠正自动模式下来自服务器或历史 JSON 的页面值。
- 页面级前缀仍只存在于当前转换调用和 `HtmlData` 中，不写入静态 URL 缓存、偏好设置或 `ThreadRowInfo`。
- 页面 A/B 不得相互污染附件前缀。

### R3 修复缓存头像的遗留绝对地址

- `FunctionUtils.parseAvatarUrl` 对最终提取出的 URL 调用统一遗留图床归一化；直接 URL 和嵌在头像 JSON 字符串中的 URL 都要覆盖。
- `img.nga.178.com/avatars/...` 应生成 `https://img.nga.cn/avatars/...`。
- 当前 `.nga.cn` 地址、非 NGA 地址、null、空值以及无法提取 URL 的既有行为不得回归。
- 只复用现有精确锚定在 `img` 图床族上的规则，不得误改 `nga.178.com`、`bbs.ngacn.cc` 等论坛主机。

### R4 兼容、测试与文档

- 使用脱敏的最小合成 JSON/URL 覆盖历史缓存形态，不读取或提交用户 ZIP 作为自动化测试输入。
- 增加退役页面主机、未知有效页面主机、手动模式优先级、页面隔离、正文/附件最终前缀和头像归一化回归测试。
- 更新 `.trellis/spec/backend/nga-platform-access-rules.md`：语法合法但已知退役的服务器附件主机属于自动模式的兼容回退条件。
- 自动化测试不得访问 NGA；实现不得新增运行时 DNS/HTTP 健康检查。

## Acceptance Criteria

- [ ] AC1：自动模式解析 `img.nga.178.com/attachments` 时得到 `https://img.nga.cn/attachments`。
- [ ] AC2：自动模式对 `imgN.nga.178.com`、`imgN.ngacn.cc` 的受支持输入形态执行同一安全映射，不产生任何旧附件主机 URL。
- [ ] AC3：自动模式仍接受并逐页隔离非遗留的合法服务器主机；缺失、非法值继续走固定安全前缀。
- [ ] AC4：默认、img9、自定义三种手动模式继续覆盖任意服务器页面值，现有协议语义不变。
- [ ] AC5：包含历史 `_ATTACH_BASE_VIEW` 和 `[img]./...[/img]` 的最小合成页面，经现有转换链生成的新域名附件 URL；附件区和图片 URL 列表使用同一前缀。
- [ ] AC6：`parseAvatarUrl` 对直接和嵌套的 `img.nga.178.com/avatars/...` 地址返回对应 `https://img.nga.cn/avatars/...`，同时保持其他输入兼容。
- [ ] AC7：遗留非附件路径继续按现有规则保留 `img` 编号；论坛主机、当前图床及本地 asset 不被误改。
- [ ] AC8：用户提供的 ZIP 不进入版本库；测试 fixture、日志和失败输出不包含其正文、用户名或真实图片路径。
- [ ] AC9：相关 JVM 单测、Debug 编译及受影响模块 lint 通过；既有无关基线单独记录，不通过放宽断言规避。

## Out of Scope

- 把图片二进制下载进离线缓存或随 ZIP 导出。
- 恢复已经从所有可用 CDN 主机物理删除的图片。
- 修改缓存 ZIP 格式、导入/导出流程或批量重写用户已有 JSON。
- 完全忽略 `_ATTACH_BASE_VIEW`，或删除“自动”图片域名模式。
- 运行时网络探测、WebView `onerror` 重试、备用图床自动切换。
- 改动上传主机、板块图标、表情资源或主站 NGA 域名设置。

## Risks and Deferred Items

- 当前证据足以证明所给 ZIP 的 URL 展开故障，但它不是 Issue #6 报告者本人当时的归档；修复目标是已验证的同类机制，而不是宣称还原其设备上的临时 WebView/Glide 缓存状态。
- 本修复恢复的是仍能从新主机获取的远端资源。若产品目标升级为“服务器删图后仍永久可见”，需要另立完整离线媒体缓存任务。
- 用户自定义填写退役域名仍可能失败，这是尊重手动选择的既有语义；如需禁止，应作为独立产品决策。
