# 网页唤起本客户端

## Goal

本客户端能接收有证据支持的帖子唤起链接，并向用户解释哪些网页按钮受系统/浏览器限制。

## Parent requirement

R1 in ../09-30-reader-access-location-navigation/prd.md. 本子任务可独立验收；产品范围已明确，本窗口只定稿规划，实施留待新会话。

## Confirmed facts

清单只支持 HTTP/HTTPS read.php；无自定义协议。ArticleListActivity.java:46 已有 tid/pid 等参数解析。用户提供 Via 的 nga://openType=2?page=1&tid=47649154&。本地保存 js_read.js:4058–4075 确认 type 2 主题/type 5 回复；链接和该 Android 代码未指定官方包名。

## Requirements

1. 基于真实链接格式定义接入；参数无效时安全结束，不误开主题。
2. 保留普通 NGA 网页链接能力；不把任意外部地址转成带账号请求。
3. 说明 Android 默认链接设置及官方包名限定，不能保证每次出现选择器。

## Acceptance criteria

- [x] 有证据的隐式链接能进入正确帖子。
- [x] 无效/不支持链接不崩溃、不误开。
- [x] 明确记录显式官方包名路径的限制。

## Out of scope

适用父任务非目标；按本子任务边界实施，不执行真实 NGA/设备操作。

## Dependencies and review

协议证据已齐；本次支持 type 2/5，版面 type 1/3 不在本次帖子入口范围。浏览器/系统是否实际弹出选择器未做设备验证。

完整证据：[current-behavior.md](../09-30-reader-access-location-navigation/research/current-behavior.md)。
