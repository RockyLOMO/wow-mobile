> 历史阶段记录：仅保留当时的设计与验证结果。现行功能以[安装使用手册](../../OldDreamWOW_New_Device_Setup_Checklist.md)和[开发维护手册](../../OldDreamWOW_Development_Guide.md)为准。文中的旧下载地址、APK哈希、分辨率和未完成状态不代表当前版本。

# WoW Mobile Native Touch 实现与 S10 测试清单

## 代码与产物

- Fork：`https://github.com/RockyLOMO/wow-mobile`
- 本地：`D:\projs\wow-mobile`，分支 `feature/wow-native-touch`
- 上游基线：`0c8068f617737d9c0fb6246f37b54f85b620b09d`
- 最新 Debug APK：`D:\projs\wow-mobile\docs\WoW_Mobile_Native_Touch_debug_20261004_v4.apk`
- APK SHA-256：`1DC5945F9E98A24CCC1506AAEDC3D7C64C3A8F019107F2547054AEF619180CAA`
- 最新提交：`3fd2838`（已推送到 fork 的 `feature/wow-native-touch`）

## 已实现

- WoW Settings 可切换 Native Touch 与 ConsolePort / Legacy，并调整长按时间、镜头灵敏度。
- Native Profile 仅包含 WASD 摇杆、JUMP (Space)、TGT (Tab)。Native 模式下 A/D 分别绑定 `STRAFELEFT` / `STRAFERIGHT`，控制人物左右横移；视角由右手拖动控制。
- Native 手势：轻点绝对坐标左键；快速拖动按住右键并发送相对位移；长按松手右键；长按后拖动绝对坐标左键；双指滑动滚轮。
- 控件指针与触屏指针按 Android pointer ID 分流，支持摇杆与镜头/点击同时使用。
- ACTION_CANCEL、Activity onPause、切换 Profile 时释放本手势按下的鼠标键和控件键。
- Legacy Profile 与 ConsolePort 插件保留，可通过 WoW Settings 回退。
- 轻点鼠标按下时间从 30 ms 延长到 120 ms，降低低帧率下漏点概率。
- 新增 `WoWMobileTouchUI` 小插件，进入世界时把默认技能栏设为 1.35 倍；Native 摇杆上移，避免覆盖放大的技能栏。v3 把 WoW 界面整体缩放设为 1.12，并相应调整技能栏独立缩放，避免技能图标再次变大；聊天窗口字体至少 14。安装器只升级未改动过的 v1 插件文件，保留用户自行修改的文件。

## 构建验证

在 `D:\projs\wow-mobile`，使用 JDK 17 执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:compileDebugJavaWithJavac --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
git diff --check
```

Java 编译及 Debug APK 构建均显示 `BUILD SUCCESSFUL`。APK 的包名为 `it.wowmobile`，仅包含 `arm64-v8a`。

已连接的 Galaxy S10 为 `SM-G9730`，Android 12。初次尝试 `adb install -r` 时旧版因签名不同被 Android 拒绝。用户自行卸载旧版后，2026-10-03 执行 `adb install` 返回 `Success`。手机上已重新选定 `/storage/emulated/0/WoW335CN`，应用配置显示 Native Touch、380 ms 和 1.0×。游戏启动快捷方式含 `controlsProfile=6`，实际画面只显示 WASD、TGT、JUMP。

用户登录角色后，实测快速滑动 200 px / 250 ms，场景明显旋转，验证镜头右键相对位移。游戏视频设置实际显示 800×600，“使用 UI 缩放”原本未勾选；已勾选并把滑块调至最高。游戏配置文件与 Winlator 容器记录 960×432，因此分辨率菜单与容器记录不一致，应以游戏内画面和菜单为准。单靠内置 UI 缩放，技能图标仍约 34 px 高。游戏内执行 `MainMenuBar:SetScale(1.35)` 后，图标约 48 px 高，画面可见；据此做成自动加载插件。

新版 APK 于 2026-10-03 在 S10 上执行 `adb install -r` 返回 `Success`。启动后检查手机上的 `Interface/AddOns/WoWMobileTouchUI`，`.toc` 和 `.lua` 均已创建；`controls-6.icp` 中摇杆纵坐标已变为 `0.62`。新版在登录界面单次轻点“片头动画”即打开菜单，验证短点击修复至少在登录界面有效。

## 2026-10-04 游戏内复核与收尾

在 Galaxy S10 登录并进入角色后，未输入缩放命令，技能栏已自动显示约 1.35 倍大小；摇杆上移后不再覆盖技能栏。[实机画面](../../assets/screenshots/WoW_Native_Touch_S10_after_v2.png)。ADB 快速滑动 165 px / 240 ms，场景明显旋转；在角色头像上按住 520 ms 后松手，右键菜单弹出。登录页按钮也已单击命中。ADB 无法可靠注入双指与摇杆加右手的同时操作，相关项目保留手测状态。

游戏通过 `/quit` 正常退出后，执行 `adb shell am force-stop it.wowmobile`。随后 `adb shell ps -A` 未找到 `Wow.exe`、`it.wowmobile`、`box64`、Wine 或 XServer 进程；`stay_on_while_plugged_in` 为 `0`，手机屏幕为 Dozing。`cmd media_session volume --stream 3 --get` 返回 `volume is 0 in range [0..15]`，游戏媒体音量保持静音。

## 2026-10-04 字体、自动操作与资料清理

- Galaxy S10 的 Android 用户 0（主资料）与用户 10（工作资料）原本都安装了 `it.wowmobile`，工作资料标记为从未启动。执行 `adb shell pm uninstall --user 10 it.wowmobile` 返回 `Success`。复核用户 0 仍安装、用户 10 不再安装。Android 多用户共用 APK 代码文件，因此释放的主要是工作资料专属数据与桌面入口，不能把整个 APK 体积算作节省空间。
- 旧插件进游戏后，先通过 `/run` 对比整体缩放 1.12、技能栏补偿缩放、聊天字号 14。v3 将配置写入 `WoWMobileTouchUI.lua`，新版 APK 安装到用户 0 后，手机上的旧版 Lua 自动升级为 v2；未改动用户账户与 BigFoot 文件。
- 退出游戏后检查到 `gxResolution` 实际保存为 `800x600`。这会渲染 480,000 像素，且在 S10 宽屏里让界面过大、技能栏两侧裁切；`960x432` 渲染 414,720 像素，少 13.6%。将 `WTF/Config.wtf` 改为 `960x432` 后重新登录验证，实际世界画面铺满屏幕，文字较清晰，技能栏可见。[实机画面](../../assets/screenshots/WoW_Native_Touch_S10_font_v3.png)。登录与加载画面可能短暂留有黑边，进入世界后铺满。
- 本机账号 `WTF/Account/WOWADMIN/config-cache.wtf` 的 `SET autoLootDefault "1"` 已在游戏正常退出后核实；v3 的新安装默认配置也加入这一项。点击附近尸体后尸体消失，未弹出需要逐项点击的拾取窗；未单独核实掉落物清单。
- BigFoot 账号设置已开启 `EnableRepairHelper=1`、`AutoRepairDurability=1`，即商人可修理时自动修理身上装备；`RepairHelper_RepairAll=0` 维持原设置。`SellerHelper.AutoSellPoor=1`（自动卖灰色物品）、商人/银行/交易时自动开包、`QuickLoot.EnableQuickLoot=1` 也已开启。没有额外打开自动交任务等可能误操作的功能。自动修理和自动出售仅核对配置，未实测商人交易。
- 执行 `:app:assembleDebug` 显示 `BUILD SUCCESSFUL`，`adb install --user 0 -r` 显示 `Success`。安装后自动升级的 Lua 首行是 `-- WoW Mobile Touch UI v2`。收尾执行 `am force-stop it.wowmobile`、`svc power stayon false`、熄屏；`ps -A` 无 WoW、应用或 Wine 进程，`stay_on_while_plugged_in=0`。用户 0 仍有包，用户 10 无包；分辨率和自动拾取配置仍为 `960x432` 与 `1`。

## 2026-10-04 左右横移修复（v4）

此前方向键发送 W/A/S/D，但 A/D 沿用了 WoW 的转向绑定，导致左右方向键转动角色与镜头。`Provisioner.patchBindingsCache` 现在按输入模式写入 A/D：Native Touch 使用 `STRAFELEFT` / `STRAFERIGHT`，Legacy 使用 `TURNLEFT` / `TURNRIGHT`。每次从 Play 启动会重新应用，因此已有手机账号也能更新；其余按键沿用原配置。此轮实测账号使用账号级绑定（`GetCurrentBindingSet()=1`）。自定义角色级绑定尚未覆盖验证。

在 S10 当前游戏中设置并保存横移绑定后，分别按住方向盘右、左区域 700 ms，场景随人物位移，角色朝向 `GetPlayerFacing()` 始终为 `1.2462794780731`；聊天输出分别为 `STRAFERIGHT`、`STRAFELEFT`。[左右横移验证截图](../../assets/screenshots/WoW_Native_Touch_S10_strafe_v4.png)。构建 `:app:assembleDebug` 返回 `BUILD SUCCESSFUL`，安装 `adb install --user 0 -r` 返回 `Success`。新版再次点击 Play 后，账号配置仍是 `bind A STRAFELEFT`、`bind D STRAFERIGHT`。

收尾关闭客户端，`ps -A` 无 WoW、应用、Wine 或 box64 进程；媒体音量为 0，USB 常亮为 0，手机已熄屏。

## S10 手动测试

2026-10-04 v5 已按用户要求保留 **1280×1024**，取消整体 UI 强制放大，并同步游戏与容器分辨率；旧的 960×432 建议已被替代。底部技能栏、背包及图标右键命中见[本次修复记录](WoW_Mobile_1280_Layout_Fix_20261004.md)。

安装兼容签名的 APK 后，先进入 WoW Settings，选 Native Touch。分辨率最低宽度 1280，Play 会自动同步容器尺寸。登录游戏后逐项检查：

- [ ] 轻点技能栏、背包、地图、任务窗和商人窗，Windows 鼠标命中触点且发出一次左键。（登录界面按钮已通过，其余待测）
- [x] 快速滑动画面，镜头持续旋转；松手立即停止。（已在世界内观察到场景旋转；屏幕边缘连续滑动待测）
- [x] 长按后松手触发右键交互。（角色头像的右键菜单已验证；NPC、尸体和物品待测）
- [ ] 长按技能或背包物品后拖动，出现短震动，拖动到目标位置并释放左键。
- [ ] 双指上下滑动，WoW 镜头缩放或聊天列表滚动。
- [ ] 左手一直按 WASD 摇杆，同时右手分别拖镜头、点击技能、按 JUMP 和 TGT。
- [ ] 切后台、拉通知栏、切换 Profile 后，没有卡住的鼠标键或移动键。
- [ ] 在 WoW Settings 切回 ConsolePort / Legacy，原虚拟按键布局可用。
- [ ] 连续游玩 30 分钟，并在 1280×1024、1520×720 分辨率下检查坐标准确性。

如果某项不符，请记录触摸位置、手势时长、游戏分辨率，以及是否同时按摇杆。
