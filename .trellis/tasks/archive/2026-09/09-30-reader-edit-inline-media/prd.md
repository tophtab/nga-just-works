# 编辑已发布内容时恢复图片与表情预览

## Goal

编辑已发布主题或回复时，图片与表情继续以可视内容显示，保持与新发帖插入媒体的体验一致，并保证发送原文不损坏。

## Parent requirement

R6 in ../09-30-reader-access-location-navigation/prd.md。用户新增讨论范围；仅规划，未开始实现。

## Confirmed facts

TopicPostPresenter.java:40 使用 ImageSpan 显示新插入表情，:207 显示新上传图片。编辑入口 :81–87 和 TopicPostFragment.java:82 的草稿恢复只插入普通字符串，未重建 ImageSpan。底层正文仍是正确 BBCode。

## Requirements

1. 为已有 [s:分类:名称] 和受支持 [img] 原文重建预览，保持底层文本不变。
2. 表情复用 R5 修正后的映射；图片异步加载，遵守现有图床前缀/缓存和图片显示设置。
3. 编辑、删除、选择媒体不得遗留半截标签；加载失败也不能丢失原始内容。
4. 打开编辑、恢复草稿和页面重建都能恢复预览；迟到回调不得覆盖新输入。
5. 仅显示原有图片不重新上传；发送/保存沿用原始 BBCode。
6. 不把格式化后的帖子 HTML 回填编辑器，不扩展为全部 BBCode 所见即所得。

## Acceptance criteria

- [x] 原有表情/图片在编辑正文中显示，普通文字可正常编辑。
- [x] 仅打开编辑再发送时媒体代码与原始内容一致，无额外上传。
- [x] 混排、重复表情、多个图片、加载失败、撤离页面与草稿重建不丢字、不串图。
- [x] 删除媒体完整移除对应标记，保留周围文本；新增媒体仍可正常插入。
- [x] 未识别标签保留原文，不误替换为另一张图片。

## Dependencies and review

依赖 R5 的统一表情解析映射；图片预览使用现有资源加载，不新增发帖协议。预览媒体按完整标记选择/删除；失败或不支持的原文仍可编辑。与其余五项一起做最终规划审核。动态 GIF 播放与全量富文本编辑暂不包含。
证据见 ../09-30-reader-access-location-navigation/research/followup-script-links-editor.md。
