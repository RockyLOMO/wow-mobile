> 历史阶段记录：仅保留当时的设计与验证结果。现行功能以[安装使用手册](../../OldDreamWOW_New_Device_Setup_Checklist.md)和[开发维护手册](../../OldDreamWOW_Development_Guide.md)为准。文中的旧下载地址、APK哈希、分辨率和未完成状态不代表当前版本。

# S10：1280 分辨率布局和触点修复（2026-10-04 v5）

## 当前设置

保留手机保存的 **1280×1024**。WoW Settings 可选分辨率宽度最低 1280；已有其他有效宽度不少于 1280 的配置会加入下拉列表，不会因列表缺项自动回退到低分辨率。新安装默认 1280×720。

每次 Play 启动同步游戏的 `gxResolution` 与 Wine 容器的 `screenSize`。Native Touch 启动自动铺满横屏；菜单里的 Toggle Fullscreen 仍可切换为保持比例、带黑边的显示方式。

## 修复原因与实现

1. 原先 Config.wtf 保存为 1280×1024，容器仍是 960×432，实际桌面与游戏配置不一致。现已同步为 1280×1024。
2. 原生点击使用旧缓存矩阵，全屏拉伸时两个方向共用比例，切换全屏也不更新。现在从最后一帧的实际 OpenGL viewport 和场景缩放/平移计算逆变换，X/Y 分开映射，包含黑边偏移和边界裁剪；镜头拖动使用同一比例。
3. v2 插件强制 `UIParent:SetScale(1.12)`，导致整个技能栏和背包窗口一起放大。v3 让游戏管理 UIParent，只对底部栏按可用宽度限制倍率，保留聊天字号至少 14。升级只替换原样的 v1/v2 插件，自定义 Lua 不覆盖。

## 验证

- `:app:assembleDebug --no-daemon --console=plain`：`BUILD SUCCESSFUL`。
- 独立 Java 回归检查：拉伸、黑边、缩放、平移、旋转后的尺寸更新、边界裁剪、相对移动比例和分辨率保留检查全部 `PASS`。
- Samsung S10，main profile 安装：`adb install --user 0 -r` 返回 `Success`；游戏配置和容器均是 1280×1024，手机插件首行为 `-- WoW Mobile Touch UI v3`。
- 进入 Firstkiller 游戏世界后，技能栏两端、背包窗口和物品格完整显示。长按底部两个有技能的图标，游戏记录 `ActionButton5 RightButton`、`ActionButton7 RightButton`；对应 `GetMouseFocus()` 为 ActionButton5/7。背包格长按后焦点为 `CombuctorItem41`，没有落在标题上。
- 点击登录、进入世界均成功；镜头快速拖动仍可使用。未做所有分辨率的实机逐项验证或长时间性能测试。

[旧版裁切画面](../../assets/screenshots/WoW_Native_Touch_S10_layout_before_v5.png) · [修复后布局与图标命中证据](../../assets/screenshots/WoW_Native_Touch_S10_layout_v5.png)

当时产物为v5 Debug APK，当前本机已不保留该文件；最新版本见[文档首页](../../README.md)。只安装到 main profile，APK 不随源码提交。

源码提交 `a97b3a3`，已推送 fork 的 `feature/wow-native-touch` 分支。APK SHA-256：`012F8C3ADCE669B64359E336BD4AB771175A82569250160B9C28A4BA8A8B33F5`。

最终 APK 再次安装后，从 Play 启动直接铺满 S10 横屏，[自动全屏截图](../../assets/screenshots/WoW_Native_Touch_S10_fullscreen_v5.png)。设置页当前选项为 1280×1024，列表无宽度小于 1280 的选项。收尾关闭游戏与应用，`ps -A` 无 WoW/Wine/应用进程，媒体音量为 0，USB 常亮为 0，手机已熄屏；main profile 有包，work profile 无包。
