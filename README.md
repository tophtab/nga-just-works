# NGA Just Works

`NGA Just Works` 是基于
[Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE)
进行的二次开发，当前代码基线为上游提交
[`5d807617f8058950f7ea81dda405e38fb0cc37ec`](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE/commit/5d807617f8058950f7ea81dda405e38fb0cc37ec)，并按需引入和适配上游后续的功能改进与问题修复。

## 下载

[GitHub Releases](https://github.com/tophtab/nga-just-works/releases) 


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

## AI 功能

以下功能已在
[`feature/ai-summary`](https://github.com/tophtab/nga-just-works/tree/feature/ai-summary)
分支实现，尚未合入主分支。体验时请在 GitHub Releases 中选择标题包含该分支名的预览版。

- 楼层总结 — 在楼层菜单一键总结当前楼层，自动附带帖子标题、楼层号与作者。
- 用户分析（AI 查成分）— 基于用户近期公开主题与回复，分析兴趣、观点和发言风格。
- 自定义 AI 服务 — 配置一个 OpenAI Chat Completions 兼容服务，填写 API 地址、API Key 和模型；支持获取模型列表、手动填写模型及连接测试。
- 查成分提示词 — 提供论坛锐评、详细分析两种预设，也可自定义提示词。
- 流式结果展示 — 正文以纯文本逐步显示，思考过程默认折叠；支持关闭取消、重试和复制正文。

## 致谢

**代码基线**

- [Justwen/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/Justwen/NGA-CLIENT-VER-OPEN-SOURCE) — 本项目的上游
- [ymback/NGA-CLIENT-VER-OPEN-SOURCE](https://github.com/ymback/NGA-CLIENT-VER-OPEN-SOURCE) — 同源分支

**其他 NGA 客户端** — 功能与交互设计的参考

- [nga_harmony](https://github.com/apap6628114/nga_harmony) — HarmonyOS ArkTS 客户端，AI 功能设计的主要参考
- [MNGA](https://github.com/BugenZhao/MNGA)
- [NGNGA](https://github.com/PoiScript/NGNGA)
- [NgaLite](https://github.com/fhyxz001/NgaLite)
- [open-nga](https://github.com/mlzzen/open-nga)

**AI相关**
- [LINUX DO 社区](https://linux.do/)
- 给 AI 立规矩的开源框架：[trellis](https://github.com/mindfold-ai/Trellis)

## 风险说明

本项目基于原项目进行 AI 辅助的 vibe coding，可能存在尚未发现的缺陷、安全或兼容性问题。安装与使用前请自行审查并评估风险，风险自负。欢迎审查代码、提交问题与改进，并在遵守许可证与来源声明的前提下继续二次开发。

## 许可证与来源

本项目依据 GNU GPL version 2 发布。详见 [`LICENSE`](LICENSE) 与
[`SOURCE_LEDGER.md`](SOURCE_LEDGER.md)。
