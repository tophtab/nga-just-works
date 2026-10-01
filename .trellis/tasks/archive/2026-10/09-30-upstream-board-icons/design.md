# U1 技术设计：板块动态图标与刷新

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。基线：`1a8413d9`；上游：`6a785430`、`750871b3`。

## 目标与采用边界

沿用上游 `forum_icon_pre → board_icon_url → BoardEntity.iconUrl → UI` 链，补齐仅前缀变化时的更新，保持本 fork 的收藏、板块树和排序。
“动态”是地址随响应更新，不是动画。现有内置图标优先，远程图标使用当前 Coil 2.5.0；旧调研的 Glide 描述不再代表这条 UI 路径。

| 上游部分 | U1 决策 | 依据 |
| --- | --- | --- |
| `ForumsListBean.forum_icon_pre` / `BOARD_ICON_URL` | 复用字段与 preference key | 上游现成分类元数据链 |
| `stid` 优先、普通和合集路径 | 复用构造意图，集中校验与规范化 | 保留两种路径族、负 fid |
| 每个实体的 `iconUrl` | 适配为可观察、非持久化的派生属性 | 当前 Compose + 可变实体 + id-only equals |
| `boardMap.values` 保存 | 不采用，保存有序根树 | map 会丢重复节点，且混入后代和收藏根 |
| 仅新增板块才合并 | 分离前缀更新和新增节点 | 两者均可独立变化 |
| `_v2` 时间戳 | 保留原 key 与一天间隔，复用旧原始缓存 | 无列表格式迁移；无需清缓存或强制额外请求 |

详细证据：[上游与刷新](research/upstream-refresh-and-url.md)、[状态与持久化](research/state-persistence-ui.md)。

## 数据流与职责

```text
BOARD.CATEGORIES（原请求、身份与 GBK 解码）
  → Repository：fastjson 1 解析，隔离非法图标字段
  → 完整分类结果 + 可选的有效图标前缀（IO 不访问可变板块树）
  → Model 在 Main 按当前树去重/插入，更新所有实际节点的派生 iconUrl
  → 已接受前缀写入 preference；新增板块才保存根树快照
  → iconUrl 的 Compose 状态更新；新增节点另发布内容 revision
  → Coil 接收新的 URL 字符串，原占位图/错误图继续有效
```

不改 `BOARD.CATEGORIES` 请求参数、账号行为、编码或正文附件图床；不接入帖子读取和 R7。
图标 URL 只交给现有无账号头的图片加载链，不把前缀主机加入登录或携带 Cookie 的论坛请求范围。

## 图标前缀契约

新增板块专用、可在 JVM 测试的 `BoardIconUrlResolver`，以现有 `ApiConstants.URL_BOARD_ICON` / `URL_BOARD_ICON_STID` 为默认值权威来源。
不要调用 `NgaImageHost` 的附件设置，也不要在 View、Model 各写一套拼接。

1. 输入须为字符串；去除首尾空白，目录尾斜杠规范为一个。缺失、null、数字、对象、数组视为无可用更新。
2. 支持绝对 HTTPS URL，默认端口，合法 DNS 主机，无 userinfo/query/fragment；拒绝反斜杠、内部空白、IP/localhost、占位主机、已退役的 `img*.nga.178.com` / `img*.ngacn.cc`。
3. 为保留有证据的旧写法，只有 `http://img4.nga.cn` 的板块目录转换为现有 HTTPS 地址；其他 HTTP、无协议和非 HTTP(S) 输入保留上次有效前缀。不会假定陌生主机的 HTTPS 可用。
4. 当前已知目录为精确路径 `/ngabbs/nga_classic/f/app/`。合集在同一已接受 origin 下使用 `/proxy/cache_attach/ficon/`。不以宽泛 replace 或正文附件路径推导；未知目录保留原值，等有来源证据再扩展。
5. 不把 host 固定为 `img4`：分类响应可提供另一合法 HTTPS origin，但只用于该次接受前缀的精确 scheme/host/port 与图标路径。保留当前图片加载器，不引入全 App 网络策略迁移。
6. `stid != 0` 输出 `<collection-prefix><stid>v.png`；否则 `fid != 0` 输出 `<ordinary-prefix><fid>.png`，保留负号；两者为 0 的结构节点无远程图标。
7. 校验失败不写 preference、不清空已有 iconUrl、不阻止合法的分类增量。规范化前后相等时不触发无意义更新。图片下载失败仍用原错误占位图，不额外重试或自动猜域名。

不承诺语法校验能证明 CDN 当前可达。输入边界来自固定上游与现有路径证据；本轮未请求真实前缀。

## 初始化、刷新和节流

初始化优先级：有效 `board_icon_url` → 旧 `board_list_remote.json` 中的有效前缀 → 当前 HTTPS 默认值。
preference 缺失、类型错误或读取异常均在图标来源边界局部处理，继续尝试原始缓存/default；不能让错误的 preference 使模型初始化失败。
缓存缺失、损坏或非法元数据只影响图标来源选择；不删除恢复文件，不重建收藏，也不把旧缓存中的分类重新覆盖到用户树。
启动阶段立即可用偏好/默认值；需要读取旧远程缓存时在 IO 读取，返回 Main 应用。防止延迟缓存结果覆盖该实例已接受的新远程前缀。

保留点击进入板块后发起分类请求的触发点、`board_remote_request_time` 和一天间隔。旧缓存已有前缀时可立即迁移；没有时显示当前默认图标，等下次正常到期请求。正常时钟下最多等待剩余的一天间隔。
不采用 `_v2`，不提高 `BOARD_LOCAL_VERSION_CURRENT = 6`，不主动删 `board_list.json`。

刷新流程：Main 原子检查到期且无在途请求 → IO 请求/解析 → Main 应用结果 → 记录正常完成尝试时间 → finally 释放在途标志。
失败仍遵循原完成后节流，不添加快速重试；取消传播并释放标志，不应用取消结果。普通空新增列表仍可携带前缀变化。
读取/解析阶段不对 `boardMap` 去重；在应用时对当前树判断，避免 IO 期间收藏或排序操作使结果过期。
不为缺乏证据的服务端错误码新增语义。前缀只从原始顶层 JSON object 且 `result` 实际为数组的分类 envelope 取值；`result=[]` 可以更新前缀，缺失/null/非数组以及仅有前缀的无关对象均不能更新。先验证实际 JSON 类型，不依赖 DTO 默认值或类型强转；畸形/错误响应不改当前图标。

## 状态与 UI 更新

- `BoardEntity.iconUrl` 用 Compose `mutableStateOf` 支持的 String 属性，初始为空。getter/setter 均显式设置 fastjson `serialize=false, deserialize=false`；不沿用不兼容委托属性的 `@JvmField`。
- 保持 `BoardEntity.equals/hashCode`、fid/stid、id、parentId 和排序键的定义。
- 递归遍历实际 `localBoardList` 和 `bookmarkBoard`；不能只遍历 map、不能按 `equals` 去重。收藏对象可以与树中对象分离，树里同一板块也可能出现多次。
- 初始化、有效前缀变化、新增/恢复收藏、收藏文件重载、拖动取消/回滚、新增普通节点后都按当前前缀补齐图标。原地更新实体，不替换拖动快照引用。
- 图标单独变化由格子读取的 `iconUrl` 触发重绘，不重挂载 Pager/列表，不改变当前页、滚动位置、拖动状态或键。
- 只有新增树节点时递增 `boardContentRevision`。`ForumBoardContent` 按根 id 和 revision 构建当前渲染层级的 children 列表快照（包括分组 children），让 Lazy grid 看到新增内容；不用 `key(revision)` 重建页面。
- 普通格子存在重复 fid/stid，沿用其现有键策略；收藏仍用 `bookmarkStableKey`。不把收藏键机械复制到普通网格。
- 本地 drawable 优先、48dp 尺寸、默认占位/错误图、点击和 TalkBack 排序操作保持。

## 持久化、并发与回滚

图标只是 prefix + fid/stid 的派生值，不进入收藏 JSON、根树 JSON、id 或账号状态。旧 JSON 无 `iconUrl` 正常读取；若已有同名字段也不信任其值，按当前有效前缀重算。
前缀变更只保存 prefix preference，不重写 `board_bookmark.json` / `board_list.json`，从而避开旧收藏写入与图标刷新竞争。

有新增节点时，保留现有支持分类和父节点位置，只追加到实际存在的父节点；跳过缺少必要名字/父节点的条目。是否为新增沿用应用时的 `boardMap` 已知 ID 集合（包含收藏），每次接受后更新该集合；不顺带改变“仅有收藏的同 ID 阻止树增量插入”的原有规则。图标刷新仍遍历实际根树/收藏全部对象，不能混用这两个集合。合并不重排已有节点或重置主页 overlay。
在模型同步边界内冻结有序 `localBoardList` 的完整层级快照，再交给现有单线程 IO 保存；不能在异步 lambda 执行时才浅拷贝可变树。
收藏写入、staging/backup 恢复、candidate still-current 与失败回滚语义保持，图标更新不构成排序或成员变更。

U1 独立提交并可独立 revert。回滚后的旧版本忽略新增 preference，仍读取原列表格式并使用固定图标；不需要清用户数据或恢复数据库版本。真实 CDN 相同 URL 内容变更不在本次缓存失效范围内。

## 文件范围

主要改动位于 app 的 `compose/board/`：`ForumBoardModel.kt`、`ForumBoardRepository.kt`、`ForumBoardViewModel.kt`、`ForumBoardView.kt`、两个 data DTO、新增窄 URL/树更新测试入口；公共模块只添加 `PreferenceKey.BOARD_ICON_URL`。
`ApiConstants.java` 保留默认值，如需修改仅更新与实际 Coil 链不符的注释。测试位于现有 board 测试包。
不修改正文 decoder、作者属地、编辑器、Manifest、SDK 或 JSON 依赖。

## 验证与剩余限制

验收矩阵和完整命令见 [实施计划](implement.md)。需要行为测试证明前缀更新、重复实体覆盖、观察通知、重启兼容与排序并发；不能仅凭字段存在或源码 contains 断言宣称这些行为通过。
本轮不做设备操作/真实 NGA 请求，因此不会宣称实机视觉、CDN 可达或服务端成功率已验证。
独立工作区未纳入其他会话的 R1–R6 在途修改；父任务最终集成再检查其已审核交付，不从当前脏树覆盖进来。
