# 旧梦WOW：自动启动、客户端安装与 720 高度适配

本文为v7阶段记录。后续完整20GB实机下载/解压、首次启动及v8布局结果见[完整安装与触控验收](OldDreamWOW_Full_Install_and_Touch_20261005.md)。

## 当前约定

- App 名称：旧梦WOW；Android 包名仍为 `it.wowmobile`，原地升级保留原客户端和设置。
- 渲染高度固定720，宽度按实际横屏比例四舍五入到偶数。S10：1520×720；16:9：1280×720；20:9：1600×720。窗口、游戏 Config.wtf 和触屏映射使用一致的尺寸，保持等比显示。
- 延续已安装的独立 WoWMobileTouchUI 插件：技能/背包整条底栏最高1.35倍，字体13–16；不放大 UIParent。窄屏必要时限制底栏宽度，避免背包按钮被裁切。其他屏幕复用相同放大上限。尚未做跨机型性能测试。
- 触屏 JUMP/TGT 为“跳跃/目标”；只迁移原始标签，不改用户调整过的位置、尺寸、其他文字。

## 启动与安装

1. 权限获准后依次检测保存路径、`Download/OldDream/client`、已有 `WoW335CN`。通过启动资源检查的现有客户端直接启动；不会复制现有20GB客户端。
2. 自动启动只发生于首次打开。退出游戏返回启动/设置页，用户可选择再次进入、设置或关闭应用；不自动重进。
3. 缺客户端时显示手动复制路径、目录选择、重新检测与下载入口。手动复制要求指定目录中直接包含 Wow.exe 和 Data 目录。
4. ZIP 地址：`https://cloud.f-li.cn:6500/wow/WoW-3.3.5a-zhCN.zip`，HEAD 大小19,981,061,620字节，解压约19,980,557,200字节；建议预留45GB。
5. 独立前台服务提供下载/解压百分比、GB进度、速度与粗略剩余分钟。通知和页面均可暂停；后台及熄屏下载使用有限于任务生命周期的唤醒锁。
6. 下载缓存 `Download/OldDream/.install`。Range/If-Range配合ETag/Last-Modified续传。暂停解压会保留ZIP，继续时从头解压；进程终止后再次点击下载会读取磁盘进度。
7. Commons Compress ZipFile 支持大ZIP；逐条验长度和CRC，拒绝路径穿越、路径别名、符号链接、加密和重复路径。解压到安装缓存中的 staging，通过检查后再发布到client。既有不完整目录不覆盖，显示处理提示。
8. 启动检查包含exe、5个不可变基础MPQ的长度、语言及补丁资源存在性；属于快速检查，不能证明用户手动复制的每个文件均未损坏。下载包另有逐条CRC验收。来源未提供SHA256清单，因此不宣称完成来源哈希认证。

## 上游升级边界

新增实现集中于 `com.winlator.wowmobile`：ClientInstaller、ClientInstallService、OldDreamIntegration及定制启动页。未改下载通用工具、XServer协议或Wine执行引擎。

上游公共文件仅两个必要桥接：

- XServerDisplayActivity.exit：环境停止后调用 OldDreamIntegration.returnToLauncher；其他入口仍走原上游逻辑。
- RootFSInstaller.install：新增可选完成回调的重载，原签名和原调用保持兼容。定制启动页据回调报告失败。

其余是现有WoW定制模块、Manifest服务权限、图标/字符串资源和测试配置。升级时检查这两处桥接及此前触屏映射补丁即可；仍是源码补丁，并非完全零侵入插件。

## 图标来源

通过内置 image_gen 工具生成；参考网站 `website/static/crest.svg` 盾徽和 `hero.webp` 的冰蓝、深海军蓝、古金风格。生成原图：OldDreamWOW_App_Icon_20261005.png；项目运行资源：drawable-nodpi/olddream_icon.png、各密度launcher PNG及自适应图标前景。

生成提示摘要：单个Android图标，午夜蓝#081117/#101f28、古金#c9a66d、冰青色；中心六边盾徽、金属金边、中央菱形北方符文；小尺寸高对比，主体在安全区，无文字、字母、骷髅、剑、浮水印或展示模型。工具生成完整正方形原图，再通过System.Drawing缩放成Android资源。

## 验证记录

- 构建：`gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain`，BUILD SUCCESSFUL；既有AGP/SDK版本及多参数字符串警告仍在。
- Java独立回归：ClientInstallerTest PASS，覆盖真实HTTP Range恢复、HTTP200整包重启、ETag变化、CRC及中央目录损坏废弃缓存、路径穿越/路径别名、包装目录发布、用户目录保护。TouchCoordinateMapperTest PASS，覆盖触点逆映射与S10/16:9/20:9/平板比例计算。
- 设备：Samsung SM_G9730，Android12/API31，主用户0原地安装成功；工作资料用户10无本App。
- Android自定义Instrumentation对真实源地址做前台服务下载/暂停/恢复，最终一轮续传50,377,866 → 51,442,400字节，PASS；服务在暂停后无存活项。下载页面截图可见19.98GB总量、百分比、MB/s、约剩余分钟和暂停按钮。
- 实际客户端自动进入XServerDisplayActivity，容器.screenSize与WTF/Config.wtf均1520x720。登录并进入世界，S10画面铺满、技能与背包栏完整；法术书图标长按后鼠标与tooltip落在图标上。登录/角色选择/载入美术有自身留边，世界场景铺满。
- 自然退出WoW后回到旧梦WOW设置页，未自动重进；观察到WoW/Wine进程已结束。设置页使用紧凑横屏布局。
- 完整20GB下载/全量解压未在手机上重复执行；ZIP格式/解压/损坏恢复用小型HTTP测试包验证。新装RootFS失败分支与其他Android机型尚未实机覆盖，不能宣称跨设备帧率或所有Android版本兼容已验证。
- 仅保留主用户App；临时Instrumentation测试包及约51MB测试下载已移除，原20GB客户端未复制/移动。
- 最终APK：`OldDreamWOW_Native_Touch_debug_20261005_v7.apk`；SHA256 `0D053C179A20B77C133D69A0B403CBA7F69BB5CC9AC0CF4FB33DB2701C5C277E`。版本号继续3/0.1.2，避免上游控件管理器因版本更新覆盖自定义按钮位置。
- 验证截图：OldDreamWOW_S10_autolaunch_v7.png、OldDreamWOW_S10_world_v7.png、OldDreamWOW_S10_spellbook_v7.png、OldDreamWOW_S10_longpress_v7.png、OldDreamWOW_S10_exit_v7.png、OldDreamWOW_S10_download_v7.png。
- 最终手机状态：媒体音量0，禁止充电常亮，App/WoW/Wine进程关闭并熄屏；以最终ADB状态核验为准。
