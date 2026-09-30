# 帖子访问错误提示准确化

## Goal

让用户看懂无法访问的实际原因，不把所有失败都说成要求验证或帖子已删除。

## Parent requirement

R3 in ../09-30-reader-access-location-navigation/prd.md. 本子任务可独立验收；产品范围已明确，本窗口只定稿规划，实施留待新会话。

## Confirmed facts

ArticleFailure.kt:27/:42 将 403、3xx、未知 HTML 归入 ACCESS；ArticleByteClient 在解码正文前分类状态。浏览器删除页不是本地请求的状态证据。

## Requirements

1. 真实 403 显示无法访问帖子（HTTP 403）；其他来源不能伪造 403。
2. 只在结构化站点信息或明确错误页证据存在时显示删除/不存在/无权限。
3. 未知 HTML 使用中性提示；真正验证码/验证信息才提示访问验证。
4. 文案与重试策略解耦，停止条件不放宽；保留状态/有限错误原因而非整页正文。

## Acceptance criteria

- [x] 403、3xx、200 HTML、明确业务错误、真实验证分别有正确文案。
- [x] 帖子正常正文中提到删除/403不会触发错误分类。
- [x] 文案准确化不新增切源或切账号，保持现有失败边界；错误提示只出现一次。

## Out of scope

适用父任务非目标；按本子任务边界实施，不执行真实 NGA/设备操作。

## Dependencies and review

没有额外产品待选项；在六项规划摘要中一并审核。R7 及 R7-A 已搁置，不是本任务前置；不推断用户失败请求的真实状态。

完整证据：[current-behavior.md](../09-30-reader-access-location-navigation/research/current-behavior.md)。
