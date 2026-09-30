# 我的回复显示全部定位原楼层

## Goal

点击我的回复中的显示全部后看到完整讨论上下文，并定位那条回复。

## Parent requirement

R4 in ../09-30-reader-access-location-navigation/prd.md. 本子任务可独立验收；用户已明确认可定位原回复的行为；最终完整规划审核仍待完成。

## Confirmed facts

ArticlePage.kt:123 的 showAll 固定 page=1；pid/searchpost 在查询中代表局部窗口。已有 ArticleAnchor 支持 pid/真实楼层匹配。

## Requirements

1. 把导航目标与查询筛选分开，FULL 查询不携带原 pid/author/search 过滤。
2. 保留明确目标 pid 与有效 floor；不能把搜索结果页码当主题页码。
3. 复用当前源分页事实和位置锚点，加载后必须验证实际行匹配。
4. 缺失目标、删除回复、未知分页或切换数据源时保持主题可读并提示无法定位，不无限探页。

## Acceptance criteria

- [x] 第一页/后续页的我的回复在完整主题内定位正确。
- [x] 正常源/兼容源不同页大小、跨页、pid-only、缺失/删除目标测试通过。
- [x] 旋转/新 Intent/旧回调不触发过期滚动；一般显示全部入口继续有效。

## Out of scope

适用父任务非目标；按本子任务边界实施，不执行真实 NGA/设备操作。

## Dependencies and review

用户已说“第四个，那就那么干了”；无额外先决子任务，但与 R2 同改 ArticleListFragment 时需顺序集成。

完整证据：[current-behavior.md](../09-30-reader-access-location-navigation/research/current-behavior.md)。
