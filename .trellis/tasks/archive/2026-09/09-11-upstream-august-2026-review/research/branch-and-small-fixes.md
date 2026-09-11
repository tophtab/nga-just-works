# 分支、图床、版块兼容与发版变更

## 固定比较对象

- 原项目：<https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE>。
- 2026-09-11 执行 `git fetch upstream-justwen` 并以 `git ls-remote --symref` 核对远端。
- 默认分支 `master`：`cdad1abba80236602dc98999bee53d802412a7e4`。
- 持续更新的 `master_release`：`22ba3082501bcbb08f52a66d787f970f59c2dda7`，标签 `5.0.2`。
- 当前 fork：`main@8284c703`，完整 SHA 记录在 [commit-inventory.json](commit-inventory.json)。
- 三者共同来源：`5d807617f8058950f7ea81dda405e38fb0cc37ec`。

`master_release` 在 2026 年 8 月有 16 个提交；`master` 有 2 个。不能直接把它们算成 18 项不同更新。
Git 的分支引用不记录创建日期，本次只确认分支当前状态和提交历史，不声称它在 8 月新建。

```text
5d807617（本 fork 的固定上游基线）
├─ 当前 fork 的自主修改 … 8284c703
├─ master: 8862cdd4（图床） → cdad1abb（补 4.2.2 版本记录）
└─ master_release: bd7da348（2025-11 的 4.2.2）
   → 31c1c820（同一图床补丁） → … → 22ba3082（5.0.2）
```

补丁等价性已用 `git patch-id --stable` 验证：

| 提交 | 稳定 patch-id | 含义 |
| --- | --- | --- |
| `31c1c820` / `8862cdd4` | `82b2b0b41bb246c653f4a7bd54e20d0794791141` | 同一图床改动；release 的提交消息还记录了 cherry-pick 来源 |
| `cdad1abb` / `bd7da348` | `a414c7568876f82120ccc514b288f4788e57a47b` | master 补上的版本记录，在 release 分支早已有等价改动 |

release 分支的整个 8 月增量（`bd7da348..22ba3082`）涉及 108 个文件，新增 2140 行、删除 3539 行；这包含搬迁、重命名、删除旧代码，不是新增功能数量。

作者日期和实际提交日期分开保存在清单中。例如 `3596da00` 作者日期是 8 月 11 日，但 8 月 23 日才进入这段历史；不能按作者日期猜重构步骤先后。

## 31c1c820 / 8862cdd4：服务端图床

上游从帖子返回的 `data.__GLOBAL._ATTACH_BASE_VIEW` 提取附件主机，传入帖子行与 HTML 上下文；改动包括正文相对图及部分附件构造。其实现仍把服务端字符串按 `/` 粗切成主机，部分 URL 模板自己补协议，未形成统一完整前缀契约。

当前 fork 已采用核心机制并扩展：

- `ArticleConvertFactory.java:99-115` 按页面提取前缀，检查 `__GLOBAL` 和字段类型。
- `NgaImageHost.java:28-42,95-135,211` 负责自动、预设、自定义和完整附件前缀解析。
- `ForumImageDecoder.java:43-54` 使用页面前缀，并归一化历史绝对旧域名。
- 既有图床任务覆盖正文、附件、投票、媒体、评论/签名等链路；之后还有历史缓存图片修复。

对应记录：`.trellis/tasks/archive/2026-08/08-08-image-host-auto-mode/`；当前提交来源可追溯到 `f4c47f3d` 等本地适配。

**建议：视为已覆盖，保留现有实现。** 后续若参考上游数据重构，必须把本 fork 的完整前缀和历史地址行为带过去。

## 93acf42a：游戏综合讨论区解析

在 JSON 解析前删除特定形状的 `jdata` 字段，规避其中的 `\x5C` 等非标准转义；还把后续转换的捕获范围由空指针扩大为 Exception。

当前 `TopicConvertFactory.java:35-55` 没有这项过滤，但仍使用 fastjson 1。离线验证发现，当前库可以处理合成的同类输入，而迁移后的 fastjson 2 会拒绝它；上游正则对末字段和带空格的字段也不完整。详见 [json-compatibility.md](json-compatibility.md)。

**建议：作为 JSON 库迁移的兼容案例保留。** 目前没有证据把它排为当前 fork 必须立刻修的线上故障。

## 6a785430：版块图标地址动态更新

上游通过版块分类接口的 `forum_icon_pre` 更新本地偏好，再生成普通 fid 图标与 stid 合集图标地址，并把最终 URL 放进 BoardEntity。`750871b3` 只把远程刷新时间戳 key 加上 `_v2`，使升级后下一次打开版块时可重新请求。

最终上游 `ForumBoardViewModel.kt:96-99` 仍保留“必须有新增版块才合并”的条件。因此只有图标前缀变化、没有新增版块时，新前缀会保存到偏好，但当前模型不会立即重算图标；要等后续模型初始化或有新增版块的合并。移植时应连同这个更新触发缺口一起处理，不能宣称上游已完整解决即时刷新。

当前 fork 在 `fb88ef64`（8 月 6 日）已经把两类图标改为 `https://img4.nga.cn/...`：

- `nga_phone_base_3.0/src/main/java/sp/phone/common/ApiConstants.java:12,23`。
- `ForumBoardView.kt:197-203` 使用这些常量。
- `ForumsListBean.kt:9-12` 尚无 `forum_icon_pre`。
- `ForumBoardViewModel.kt:197-212` 仍只在新增版块非空时合并。

所以“图标域名失效的修复”已覆盖，“后续图标前缀随服务端变化”尚未覆盖。它和正文 `_ATTACH_BASE_VIEW` 是两条不同接口/路径，不能混成同一附件主机。

**值得拆取的部分：** 读取和校验 `forum_icon_pre`，使用有效前缀更新图标；空值/坏值保留可用默认；前缀更新与有没有新增版块解耦。

**不能连带覆盖的部分：** 上游 `ForumBoardModel.kt:233-237` 把保存内容从 `localBoardList` 改成了 `boardMap.values`。该 map 含收藏根、分类和递归子版块；`ForumBoardRepository.kt:29-43` 又把读回的数组直接当根列表。源码已明确改变持久化层级，且 HashMap 不保证顺序。它不能替代当前 fork 的根列表持久化、收藏稳定键和主页排序。

当前保存边界是 `ForumBoardModel.kt:421-426`；主页排序测试还明确约束此调用。这个冲突有具体代码依据，不是泛泛担心“合并可能冲突”。尚未执行上游 App 的重启 UI 验证。

## 86e6e782 / 22ba3082：发版与 SDK

- `86e6e782`：仅把应用版本改成 4.2.3 / 4023。
- `22ba3082`：版本改成 5.0.2 / 5002，并把 compileSdk、targetSdk 从 35 升到 36；minSdk 保持 30。
- 当前 fork 的 `build.gradle:120-125`：minSdk 29，compile/target 35，版本由自己的发布流程生成。

**建议：版本号没有移植价值；SDK 36 可列为独立的 Android 16 适配工作。** 保留本 fork 的 Android 10 安装下限及自己的版本派生逻辑，不能因上游发版直接覆盖构建配置。本次未做 API 36 构建或设备验证。
