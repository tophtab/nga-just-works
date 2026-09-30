# R1：网页唤起设计

## 边界与证据

支持已取证的 Android `nga://openType=2?...` 主题和 `openType=5` 回复入口。类型位于 authority，不能当作 query 字段。用户的 Via 链接及保存的 js_read.js:4058–4104 均支持此格式；不扩展到 Harmony 改写形式或 type 1/3 版面入口。

## 接入流程

1. 在现有帖子 Activity 的 HTTP/HTTPS filter 之外单独注册 nga scheme，避免不同 scheme/host/path 条件混合。Android host 名不适合承载 `openType=2` 的业务校验，统一在小型纯解析器中验证真实 authority/type。
2. 解析器输出已验证的主题/回复参数，接受用户示例的末尾 `&`；对重复冲突字段、非法/溢出标识、不支持类型拒绝导航。拒绝 user-info、非预期 authority 和伪装路径。参数按现有字段语义校验，不拼接任意远端地址。
3. type 2 需要有效 tid；type 5 需要有效 pid，tid 可由合法回复结果确认。page 缺省为 1。保留有证据的 tid/pid/page，并与现有 authorid/searchpost 语义隔离，不能把未知字段升级为请求参数。
4. 新建 Activity 和 onNewIntent 共用入口，避免热启动时仍打开旧帖。生成现有 ArticleListParam，再使用现有主题/查找窗口导航。

## 平台约束

注册后本 App 成为该隐式 URI 的候选处理程序；Via/Android 已有默认选择仍可能直接打开其他 App。显式指定官方包名的 Intent 不由此解决。HTTP/HTTPS 既有行为和系统域名验证能力保持现状。

## 验证与回退

纯解析测试覆盖真实示例、type 5、缺省/末尾分隔符、非法/冲突参数、其他 scheme/type；清单与入口检查覆盖冷/热入口一致性。设备 chooser 验证留作新会话中明确安排的手动验证，不虚报通过。
回退仅撤销新增 filter 和 URI 适配代码，不影响已有网页链接能力；无需 R7 读取故障修复。
