# v13：键盘入口与原生模式修复

验收日期：2026-10-08。包版本仍为0.1.2 / code3，阶段v13，触控Lua v8。现行操作见[安装手册](../../OldDreamWOW_New_Device_Setup_Checklist.md)，模块边界见[开发维护手册](../../OldDreamWOW_Development_Guide.md)。

## 改动与来源

- 用户决定先只增加键盘。顶部中间40×36 dp小按钮，距顶部6 dp，绘制键盘轮廓和按键，点击显示/收起系统软键盘，不夺走Wine输入焦点。没有添加鼠标左右键按钮。
- PC源`D:\Program Files\Game\World of Warcraft 3.3.5a CN`和Nginx六片ZIP均没有ConsolePort。只读ZIP中央目录确认2,727条目、ConsolePort条目0；六分片清单和下载逻辑保持当前版本。
- S10旧客户端存在8个ConsolePort目录。Native设置为true，但游戏实际CP/Bar已加载，GetAddOnInfo的enabled=nil。旧Lua把启用许可当成已加载状态，早期TOC又未被升级，模式脚本没有执行。
- TOC和Lua精准迁移补齐。实机仅更正DisableAddOn/ReloadUI仍出现手柄栏，不能把该尝试写成成功；其更深的加载覆盖机制未确认。
- 最终使用启动前可逆隔离：Native把ConsolePort前缀目录移动到客户端根`.olddream-consoleport`，不让WoW加载；Legacy恢复原目录并启用核心ConsolePort/ConsolePortBar，WTF及玩家修改内容保留。不覆盖同名冲突。

## 验证

- JDK17定向编译并执行`ConsolePortIsolationTest`：`PASS: Native isolation, Legacy restoration, idempotency, custom bytes and conflict protection`。
- 最终`:app:assembleDebug`：`BUILD SUCCESSFUL in 51s`；S10主资料`adb install -r --user 0`成功。
- S10键盘按钮可打开/收起软键盘并完成登录输入。原生启动后8个CP目录保留在备份位置，AddOns中无ConsolePort。
- 进入Firstkiller角色，Xbox动作栏消失，底栏、背包和右侧技能栏恢复。[实机状态](../../assets/screenshots/OldDreamWOW_S10_native_keyboard_20261008.png)显示`OD 1 nil nil true`（TouchUI/CP/Bar/Native），`TARGET 0 STRAFELEFT STRAFERIGHT`。
- [游戏内键盘打开截图](../../assets/screenshots/OldDreamWOW_S10_keyboard_open_20261008.png)。
- Native场景在最终Legacy启用补充前的同一隔离实现上实测；最终包另安装，自动进入登录页，确认最终Lua迁移成功；构建、本机副本、HTTP下载与手机安装包哈希一致。Legacy的真实文件恢复有测试，完整Legacy游戏体验及其他手机本轮未验收。

## 发布

固定APK：`E:\rxdev\webhost\wow\OldDreamWOW.apk`及`docs/OldDreamWOW.apk`，170,980,247字节。

SHA-256：`DDAE489E401BBEB4506884CC5111DBCE8AFB43772D156E167E5C9BF93D9EEAB6`。发布后以本机、HTTP及手机安装包哈希复核；收尾已关闭游戏/Wine，ps无相关进程；媒体音量0，常亮设置0，屏幕Dozing。
