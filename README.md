# NGA Just Works

`NGA Just Works` 是基于
[Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE)
进行的二次开发，当前代码基线为上游提交
[`5d807617f8058950f7ea81dda405e38fb0cc37ec`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/5d807617f8058950f7ea81dda405e38fb0cc37ec)。

本项目是 NGA 三方客户端，与 NGA 及原项目作者不存在隶属、授权或背书关系。

## 下载

在 [GitHub Releases](https://github.com/tophtab/nga-just-works/releases) 下载
APK。每个分支推送代码后都会自动构建签名预览版，纯 Markdown 或 `.trellis`
文档改动跳过构建；各分支分别保留最新成功发布的预览版。

- 主分支：标题以 `(Debug)` 结尾，文件名为 `NGA-Just-Works-<版本>.apk`。
- 功能分支：标题包含完整分支名，文件名增加可读分支后缀，例如
  `NGA-Just-Works-<版本>-feature-ai-summary.apk` 或
  `NGA-Just-Works-<版本>-feature-thread-detail-compat-mode.apk`。
- 正式版：版本标签 `X.Y.Z` 对应 `NGA-Just-Works-X.Y.Z.apk`。

每个 APK 均附带 `.apk.sha256` 校验文件。特殊或较长的分支名会被转换、缩短；
请以 Release 标题中的完整分支名区分。不同分支使用相同的应用包名和签名，
安装时会覆盖同一应用，不能并排安装。

## 功能对比

### 新增

- 收藏板块顺序自定义
- 左滑触发侧边栏
- 表情顺序自定义
- 选词菜单简化
- 主题页回顶
- 列表页点标题回顶刷新

### 调整

- 默认设置
- 设置页归类
- 按钮排序
- 应用图标

### 移除

- 二级「加号」菜单
- 左手模式与底部标签两项设置
- 选词菜单中的「分享」
- 发帖工具栏的键盘按钮
- 内嵌的 release 签名路径与口令

## 未来计划

参考 [nga_harmony](https://github.com/apap6628114/nga_harmony) 的实现引入 AI 功能：

- [ ] 通用 AI 对话 — 流式输出、多轮、可中断
- [ ] 帖子内容分析 — 一键把帖子送入对话并自动注入上下文
- [ ] 用户行为分析 — 按发帖数据分析时段分布与版块偏好
- [ ] 多服务商支持 — DeepSeek、智谱 GLM、豆包、MiniMax、Kimi、OpenAI 及自定义
- [ ] 场景化提示词 — 为帖子总结、用户分析等场景分别定制 system prompt
- [ ] 流式 Markdown 渲染

## 致谢

**代码基线**

- [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE) — 本项目的上游
- [ymback/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/ymback/NGA-CLIENT-VER-OPEN-SOURCE) — 同源分支

**其他 NGA 客户端** — 功能与交互设计的参考

- [lnga_harmony](https://github.com/apap6628114/lnga_harmony) — HarmonyOS ArkTS 客户端，AI 功能规划与论坛锐评提示词设计参考
- [MNGA](https://github.com/BugenZhao/MNGA)
- [NGNGA](https://github.com/PoiScript/NGNGA)
- [NgaLite](https://github.com/fhyxz001/NgaLite)
- [open-nga](https://github.com/mlzzen/open-nga)

**AI相关**

- [nga-analyzer](https://github.com/noer25/nga-analyzer) — 详细分析提示词设计参考
- [LINUX DO 社区](https://linux.do/)
- 给 AI 立规矩的开源框架：[trellis](https://github.com/mindfold-ai/Trellis)

## 风险说明

本项目基于原项目进行 AI 辅助的 vibe coding，可能存在尚未发现的缺陷、安全或兼容性问题。安装与使用前请自行审查并评估风险，风险自负。欢迎审查代码、提交问题与改进，并在遵守许可证与来源声明的前提下继续二次开发。

## 许可证与来源

本项目依据 GNU GPL version 2 发布。详见 [`LICENSE`](LICENSE) 与
[`SOURCE_LEDGER.md`](SOURCE_LEDGER.md)。
