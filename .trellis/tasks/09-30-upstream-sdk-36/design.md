# U4 设计：SDK36、构建工具与实际平台适配

状态：2026-09-30 用户已统一确认 U1–U4 全部方案，指定新会话实施；本轮未开始产品实施。上游 `22ba3082` 提供 compile/target36 来源；工具链和平台适配以本次官方资料与当前源码为依据。

## 固定版本与身份

| 配置 | 当前 | 采用 |
| --- | --- | --- |
| compileSdk / targetSdk | 35 / 35 | 36 / 36，基础API36，不是36.1 |
| minSdk | 29 | 29 |
| AGP | 8.6.1 | 8.10.1 |
| Gradle wrapper | 8.7 | 8.11.1，固定官方 SHA-256 |
| Kotlin/KGP/Compose compiler plugin | 2.0.21 | 共同2.2.21 |
| JDK / Java-Kotlin bytecode | 17 / 17 | 17 / 17 |
| Android Build Tools | 35.0.0 | 35.0.0 |
| AndroidX/业务/原生依赖 | 当前版本 | 没有证据要求整体升级，保留 |

官方API最低表写36需AGP8.9.1，但8.9章节仍写最高35；选明确支持36的8.10.1。KGP2.2.20–2.2.21文档范围覆盖所选AGP/Gradle，避免把旧2.0.21带到未声明支持的新工具链。Compose compiler plugin与Kotlin同版本，Compose运行时/UI库不机械升级。
AGP8.10支持Build Tools35.0.0/JDK17，故不增加NDK/JDK21/Gradle9，也不加 suppressUnsupportedCompileSdk 绕过兼容问题。

包名、Debug suffix、签名配置、版本推导不变。Release保持minify=true和当前fullMode=false；Preview保持minify=false，不能把Preview误写成另一个混淆构建。
详见 [工具链与原生证据](research/platform-toolchain-evidence.md)。

## 必须修改的范围

1. 根 `build.gradle` 的SDK pair；catalog AGP/Kotlin共同版本；wrapper distribution/checksum。
2. `.github/workflows/build.yml` 的 platform安装35→36及最终APK target断言35→36；Build Tools35.0.0/JDK17/签名/版本/触发/发布行为保持。
3. `scripts/test_release_workflow.py` 的manifest fixture改36，并证明target35产物不再通过新断言；apksigner路径继续35.0.0。
4. `PostActivity.java`、`LauncherSubActivity.java` 迁移旧Activity `onBackPressed()` 拦截到生命周期所有的AndroidX dispatcher callback。
5. 完成后仅更新实际SDK与相关验证标签的规范段落，保留默认不做设备操作的项目政策。

所有13个模块继承根compileSdk。不给未声明target的library增加无必要配置，不为升级修改Manifest中的包名、权限、deep link或SDK数字的重复来源。

用户主力设备为Android15/API35；本项以Android15体验不退化为主要兼容性目标。Android16专属框架行为只在相应运行时适用，但新的共享返回代码和工具链也影响Android15，必须保留现有行为并纳入回归。

## 返回事件的确定行为

在Android16及以上运行且target36时，默认不再分发旧Activity onBackPressed/KEYCODE_BACK；这两处实际使用点需要适配。

- PostActivity仍先交给当前TopicPostFragment/ToolbarContainer，已打开的表情/格式面板消费一次返回并关闭，Activity不退出；不消费时才返回上一页。
- LauncherSubActivity仍优先询问其可用的BaseFragment；普通Preference/其他Fragment或不存在可消费对象时直接交给系统路径。
- 注册 `OnBackPressedCallback` 时绑定Activity lifecycle；消费函数遇到未添加/空view/已销毁fragment不解引用旧toolbar。
- 不消费时暂时禁用当前callback，调用dispatcher一次，并在finally恢复所需状态；不能以启用callback递归调用自己，也不调用已被替换的legacy super.onBackPressed入口。
- 当前面板可见性没有可直接订阅的统一状态接口，本项选择最小的生命周期callback + consume/fallback实现，不为预测动画新建面板状态体系。已有Kotlin BaseActivity、WebView历史及Compose drawer BackHandler保持其各自所有权。
- 不把toolbar Up、直接finish、应用内SwipeBackHelper重新绕到系统callback，不新增退出/草稿确认语义，不实现自定义预测动画。

使用fake consumer/fallback执行生产策略，验证消费/不消费/fallback一次和不可用view的处理。注册、lifecycle注销及手势取消路径做源码/编译核查；这不证明Android销毁/手势运行已通过。设备返回/预测动画未执行就明确标记，不承诺常驻callback提供这两页的系统预测动画视觉效果。

## Android16 全量适用性结论

[完整平台矩阵](research/android16-applicability-matrix.md) 已逐一映射官方 all-app 与 target36 页面，并区分基础36、QPR2及opt-in能力。

| 平台面 | 本项目结论 |
| --- | --- |
| edge-to-edge | 当前target35已启用，无opt-out；保留现有各入口inset所有者，避免叠加重复padding。Avatar/Template/About/Login等不同基类逐入口记录，不声称全部UI已经实机通过 |
| 大屏/旋转/窗口 | 无方向/宽高比/不可调整大小限制，不需要opt-out；保留现有configChanges与Bundle状态。旧键盘高度缓存风险是既有问题，不凭SDK数字做全窗口重写 |
| 媒体/文件选择/权限 | 当前ACTION_GET_CONTENT和content URI，未申请新照片读取权限；不换picker、不加广泛媒体权限。旧storage gate记录为旧风险，不夹带重做 |
| Intent/链接 | 没有发现接收后直接转发不可信嵌套Intent；保留默认保护与现有explicit routes/deep links/FileProvider，不开全局opt-out或新的matching opt-in |
| 任务/定时/后台 | 无app-owned JobScheduler/WorkManager/DownloadManager或scheduleAtFixedRate；已有一次性queue/Handler/通知PendingIntent保持 |
| 本地网络 | BYOK允许用户LAN地址，不能写成从不使用；Android16该限制为opt-in，本项不选择opt-in或加权限，保留错误/取消契约 |
| 字体/无障碍/图标 | 没有被移除的elegantTextHeight/announcement调用；已有monochrome launcher。U1板块图标与系统themed launcher不是同一能力 |
| 健康/蓝牙/伴侣/GPU | 无对应app API/权限/直接GPU syscall；不新增无消费者代码。不能把app源码搜索代替第三方SDK内部运行证明 |
| ART/R8 | 无app ART内部结构依赖；保留反射/路由契约，使用新工具链跑U3 fixture和R8 |

默认SDK适配不改登录、Cookie、正文WebView桥、U2媒体或R7读取策略。未来运行验证若发现具体回归，按责任文件修复；不以假设风险扩大当前方案。

## 原生16KB与打包

本轮读取缓存AAR与既有Debug APK，确认arm64三库：Bugly/Umeng ELF LOAD对齐64KB，graphics-path为16KB；原APK三者无压缩偏移均16KB对齐。故无预防性依赖/NDK升级依据。
新产物仍需重新检查ELF/ABI和 `zipalign -c -P 16 -v 4`；不设置pageSizeCompat掩盖问题。二进制对齐通过不等于16KB设备运行已验证。
基线检查文件是研究证据，不是尚未构建的U4验收结果。

## 环境、验证与回滚

本机仅有platform35、AGP8.6.1/Kotlin2.0.21等旧缓存；采用版本尚需在整体批准后从已有可信仓库预取。优先任务自己的SDK/tool目录或忽略的local.properties，不能覆盖原工作区文件。
依赖解析完成后正式gate使用离线构建，不重复先跑一遍相同完整线上构建。验证Debug、全模块单测/lint、Release R8、Preview编译/manifest、CI本地脚本及APK对齐。
先dry-run核对新AGP图，确保minify/Preview compile不进入签名打包guard。若它意外需要guarded packaging，记录具体任务图并调整验证方式，不弱化guard或自动读取签名秘密。
没有签名APK打包/发布/安装/设备操作或真实NGA请求。新工具链的编译/lint问题在本项局部修正，不顺带升级无关依赖。
可先设compile36/target35作为定位中间提交，最终必须compile36/target36；回滚整体U4配置/返回/CI补丁，保留U1–U3及所有用户数据。
