# 帖子表情渲染一致性

## Goal

确保点击的表情、发送代码和本地帖子图片一致，与用户观察到的网页正确结果一致。

## Parent requirement

R5 in ../09-30-reader-access-location-navigation/prd.md. 本子任务可独立验收；产品范围已明确，本窗口只定稿规划，实施留待新会话。

## Confirmed facts

已确认主渲染把赞同/闪光对调；面板和旧解码映射正确。6 类共 238 项静态比较发现两项差异。并未复现用户具体表情。

## Requirements

1. 修正赞同/闪光，使用统一 category+name 到 asset 映射防止重复表漂移。
2. 保留表情分类、文件名、自定义排序、发帖代码和图片资源；不修改历史回复。
3. 覆盖现有 238 项的一致性验证；未知表情不被替换成另一张已知表情。
4. 同步核查旧解码路径，避免只修复一处调用。

## Acceptance criteria

- [x] [s:ac:赞同] 显示 ac/ac42.png，[s:ac:闪光] 显示 ac/ac43.png。
- [x] 238 项面板发送代码与帖子渲染资源一致。
- [x] 混排、重复代码、引用/签名、已排序面板无映射回归。

## Out of scope

适用父任务非目标；按本子任务边界实施，不执行真实 NGA/设备操作。

## Dependencies and review

已确认错配及238项映射一致性属于本次范围，无需用户继续提供表情样例；其他未复现的资产问题不能宣称已修复。在六项摘要中一并审核。

完整证据：[current-behavior.md](../09-30-reader-access-location-navigation/research/current-behavior.md)。
