> 历史阶段记录：仅保留当时的设计与验证结果。现行功能以[安装使用手册](../../OldDreamWOW_New_Device_Setup_Checklist.md)和[开发维护手册](../../OldDreamWOW_Development_Guide.md)为准。文中的旧下载地址、APK哈希、分辨率和未完成状态不代表当前版本。

# WoW Mobile 原生触控改造技术方案
## 左摇杆 + 直接触摸 UI + 手势鼠标 + 镜头拖拽

> 目标设备：Samsung Galaxy S10 / Android 横屏  
> 目标游戏：World of Warcraft 3.3.5a (Build 12340)  
> 目标项目：`marcocastignoli/wow-mobile`  
> 推荐实现位置：**WoW Mobile fork 内实现，不改通用 Winlator 上游**  
> 建议开发模型：GPT-6 Sol + High  
> 文档日期：2026-10-03

---

# 1. 结论：改 WoW Mobile，不直接改 Winlator

推荐在 **WoW Mobile** 内实现新的 `Native Touch` 输入模式。

不要直接在通用 Winlator 上游做这个功能。

原因：

1. WoW Mobile 本身就是一个专门运行 WoW 3.3.5a 的 Winlator fork。
2. WoW Mobile 已经包含完整的 Winlator 输入层源码：
   - `TouchpadView.java`
   - `InputControlsView.java`
   - `XServerDisplayActivity.java`
   - `XServer.java`
3. 当前 WoW Mobile 已经有：
   - WASD 虚拟摇杆
   - 鼠标左键/右键注入
   - 相对鼠标移动
   - 绝对坐标移动
   - 双指滚轮
   - 输入控件 Overlay
4. 所以不需要重写输入系统，只需要新增一套 **WoW 专用手势状态机**。
5. 通用 Winlator 上游目前并没有完整的 Surface/Android 式“点哪里点哪里”原生触控模式；这个功能在 Winlator 社区仍属于 feature request。
6. WoW 的手势需求比较特殊：
   - 单击 = 左键
   - 快速拖动 = 按住右键转镜头
   - 长按 = 右键
   - 长按后拖动 = 左键拖拽
   这不一定适合其他 Windows 游戏。
7. 把改动限制在 WoW Mobile，可以避免破坏其他 Winlator 游戏，并且未来同步上游 Winlator 时更容易处理冲突。

---

# 2. 当前源码已经具备的基础

截至 WoW Mobile v0.1.2 / commit `0c8068f` 附近，已有以下基础设施。

## `TouchpadView.java`

路径：

```text
app/src/main/java/com/winlator/widget/TouchpadView.java
```

已经具备：

```java
moveCursorToTouchpoint
xServer.injectPointerMove(...)
xServer.injectPointerMoveDelta(...)
xServer.injectPointerButtonPress(...)
xServer.injectPointerButtonRelease(...)
```

并已有：

- 单指 Tap 左键
- 两指 Tap 右键
- 两指滚动
- 相对鼠标移动
- 绝对鼠标移动
- 屏幕坐标 → Guest/Wine 坐标转换
- 外接鼠标输入

现有逻辑已经支持：

```text
moveCursorToTouchpoint = true

单指 Tap
→ 鼠标直接移动到触摸位置
→ 左键点击
```

所以“直接点 WoW 技能图标”的基础已经存在。

缺的是：

```text
单指快速拖动
→ 自动变成 Right Mouse Hold + Relative Move
```

以及：

```text
长按再拖
→ Left Mouse Hold + Absolute Move
```

---

## `InputControlsView.java`

路径：

```text
app/src/main/java/com/winlator/widget/InputControlsView.java
```

这个类很适合继续保留。

重要现有行为：

```text
如果触摸命中了虚拟按键
→ ControlElement 消费事件

如果没有命中虚拟按键
→ 事件转交 TouchpadView
```

所以可以天然实现：

```text
左下虚拟摇杆
+ 少数 Jump / Target 按钮
+ 其它整块屏幕都是 Native Touch
```

不需要重新开发一个 Overlay 系统。

---

## `XServerDisplayActivity.java`

路径：

```text
app/src/main/java/com/winlator/XServerDisplayActivity.java
```

当前 View 层级大致是：

```text
XServerView
↓
TouchpadView
↓
InputControlsView
```

这里适合加入：

```text
Legacy ConsolePort
Native WoW Touch
```

模式切换。

---

# 3. 最终目标操作方式

目标不是模拟 Xbox。

目标是更接近普通手机 MMORPG：

```text
左手：
虚拟摇杆移动

右手：
直接触摸 WoW 原生 UI
直接点击技能
直接点击 NPC / 怪物 / 背包
滑动画面转镜头
```

建议最终只保留三个常驻虚拟控件：

```text
[左摇杆]
[JUMP]
[TARGET]
```

可选再保留：

```text
[MENU]
```

不要默认显示：

```text
A/B/X/Y
L1/L2
R1/R2
LCLICK
RCLICK
LOOK
```

---

# 4. 最终手势定义

这是本方案最重要的部分。

---

## 4.1 单指轻点：鼠标左键

操作：

```text
轻点屏幕某位置
```

行为：

```text
Mouse Move Absolute(x, y)
Mouse Left Down
Mouse Left Up
```

用途：

- 点 WoW 原生技能
- 点怪物进行选择
- 点角色头像
- 点背包格子
- 点菜单
- 点任务文本
- 点地图
- 点 ActionBar

体验：

```text
手指点哪里
=
Windows 鼠标点哪里
```

---

# 4.2 单指快速拖动：鼠标右键拖拽 / 转镜头

操作：

```text
按下
马上开始滑动
```

行为：

```text
Right Mouse Down
Relative Mouse Move(dx, dy)
Relative Mouse Move(dx, dy)
...
Right Mouse Up
```

用途：

**WoW 转镜头 / 转人物方向**

这是手机端最核心的操作。

注意：

这里必须尽量使用：

```text
Relative Mouse Movement
```

而不是一直使用 Absolute 坐标。

否则容易出现：

```text
手指滑到屏幕边缘
→ Windows 鼠标也到边缘
→ 镜头无法继续旋转
```

---

# 4.3 长按不移动：鼠标右键单击

操作：

```text
长按约 350~450ms
不移动
松手
```

行为：

```text
Mouse Move Absolute(x, y)
Right Mouse Down
Right Mouse Up
```

用途：

- 和 NPC 对话
- 拾取尸体
- 使用部分世界对象
- WoW 右键功能
- 背包物品右键使用/装备

推荐默认：

```text
LONG_PRESS_MS = 380ms
```

以后做成可调参数。

---

# 4.4 长按后拖动：鼠标左键拖拽

操作：

```text
按住约 380ms
出现轻微震动
继续拖动
```

行为：

```text
Mouse Move Absolute(startX, startY)
Left Mouse Down

Mouse Move Absolute(x, y)
Mouse Move Absolute(x, y)
...

Left Mouse Up
```

用途：

- 拖技能到动作栏
- 调整动作栏技能位置
- 拖背包物品
- 拖装备
- WoW UI 左键拖动

建议：

进入 `LEFT_DRAG` 状态时：

```text
手机震动 20~30ms
```

让用户知道：

```text
“现在已经进入拖拽模式”
```

---

# 4.5 双指上下滑：鼠标滚轮

保留 Winlator 现有逻辑。

操作：

```text
两指向上 / 向下滑
```

映射：

```text
Mouse Wheel Up
Mouse Wheel Down
```

用途：

- WoW 镜头拉近/拉远
- 聊天框滚动
- UI 列表滚动
- 某些插件列表

这个功能不要删除。

---

# 4.6 Pinch 缩放：第二阶段可选

不是 MVP 必须功能。

未来可以：

```text
双指张开
→ Wheel Up

双指捏合
→ Wheel Down
```

用于更像手机 App 的镜头缩放。

第一版建议不做，避免增加手势冲突。

---

# 5. 跳跃是否应该独立？

## 结论：应该。

保留一个独立：

```text
[JUMP]
```

按钮。

映射：

```text
KEY_SPACE
```

原因：

跳跃经常需要：

```text
左手正在移动
+
右手正在转镜头
+
同时按 Jump
```

如果把 Jump 改成：

- 双击
- 双指
- 上滑
- 摇杆手势

都会和：

- 左键点击
- 镜头拖动
- 滚轮
- 移动

产生冲突。

所以：

```text
Jump = 独立小按钮
```

是最简单、最好用的方案。

建议位置：

```text
右下
```

但不要盖住 WoW ActionBar。

---

# 6. 是否需要 Target 按钮？

## 强烈建议保留。

按钮：

```text
[TGT]
```

映射：

```text
KEY_TAB
```

用途：

```text
选择最近敌人
再次点击切换下一敌人
```

虽然直接点击怪物也可以选目标，但在手机屏幕上：

- 怪比较小
- 多只怪重叠
- 战斗中镜头移动
- 猎人需要快速换目标

直接触摸不一定足够快。

所以建议：

```text
直接点怪 = 精准选择

TGT / TAB = 战斗快速切怪
```

二者同时存在。

---

# 7. 是否需要单独“互动/拾取”按钮？

## MVP 不需要。

因为：

```text
长按 NPC
= Right Click

长按尸体
= Right Click / Loot
```

已经可以完成。

但是如果实际测试发现：

```text
380ms 长按拾取太慢
```

第二阶段可以增加可选：

```text
[INTERACT]
```

并在 WoW 3.3.5a 中给：

```text
Interact With Target
```

绑定一个键，例如：

```text
F
```

然后虚拟按钮直接发送：

```text
KEY_F
```

这样：

```text
选中尸体 / NPC
→ 点一下 Interact
```

即可。

第一版不建议增加。

---

# 8. 键盘入口

仍然需要。

因为 WoW 有：

- 登录账号
- 输入密码
- 聊天
- 输入 GM 命令
- 搜索拍卖
- 输入角色名

建议第一版继续使用 WoW Mobile / Winlator 现有：

```text
侧边 Drawer
→ Keyboard
```

不要把键盘入口塞进主要游戏 HUD。

第二阶段可选：

```text
三指轻点
→ Android Keyboard
```

但不是 MVP 必须。

---

# 9. ESC / 菜单

不是核心手势。

可继续使用：

```text
Android Back
→ WoW Mobile Drawer
```

或者保留一个很小的：

```text
[MENU]
```

发送：

```text
KEY_ESC
```

推荐：

```text
默认不显示
```

在 Native Touch 设置中提供开关。

---

# 10. 推荐最终屏幕布局

```text
┌──────────────────────────────────────────────┐
│                                              │
│                                              │
│                WoW 游戏画面                  │
│                                              │
│      轻点 = 左键                             │
│      快速拖 = 右键拖镜头                     │
│      长按 = 右键                             │
│      长按再拖 = 左键拖拽                     │
│                                              │
│                                    [TGT]     │
│                                              │
│      ◯ 移动                        [JUMP]    │
│                                              │
└──────────────────────────────────────────────┘
```

WoW 自己原生的：

```text
技能栏
背包
头像
小地图
任务
菜单
```

全部直接用手指点击。

---

# 11. 推荐手势状态机

新增：

```java
enum WowTouchState {
    IDLE,
    PENDING,
    CAMERA_RIGHT_DRAG,
    LONG_PRESS_READY,
    LEFT_DRAG
}
```

---

## 11.1 ACTION_DOWN

```text
state = PENDING

记录：
pointerId
downX
downY
lastX
lastY
downTime

将鼠标绝对位置移动到触点
但暂时不发送 Left/Right Down
```

不要在 `ACTION_DOWN` 立刻左键。

否则无法区分：

```text
Tap
和
Camera Drag
```

---

# 11.2 ACTION_MOVE

计算：

```text
distanceFromDown
elapsedTime
dx
dy
```

---

## 状态：PENDING

### 情况 A

```text
elapsed < LONG_PRESS_MS
distance > touchSlop
```

判定：

```text
CAMERA_RIGHT_DRAG
```

执行：

```text
Right Mouse Down
```

后续使用：

```text
Relative Mouse Move(dx, dy)
```

---

### 情况 B

```text
elapsed >= LONG_PRESS_MS
distance <= touchSlop
```

切换：

```text
LONG_PRESS_READY
```

执行：

```text
短震动
```

但此时不要立即发右键。

原因：

用户下一步可能要：

```text
长按后拖动技能
```

---

## 状态：LONG_PRESS_READY

如果开始移动：

```text
distance > touchSlop
```

切换：

```text
LEFT_DRAG
```

执行：

```text
Mouse Move Absolute(downX, downY)
Left Mouse Down
```

后续：

```text
Mouse Move Absolute(currentX, currentY)
```

---

## 状态：CAMERA_RIGHT_DRAG

持续：

```text
Relative Mouse Move(dx, dy)
```

---

## 状态：LEFT_DRAG

持续：

```text
Mouse Move Absolute(currentX, currentY)
```

---

# 11.3 ACTION_UP

根据当前 state：

---

## PENDING

说明是普通 Tap：

```text
Mouse Move Absolute(x, y)

Left Mouse Down
30ms
Left Mouse Up
```

---

## LONG_PRESS_READY

说明是长按但没有拖：

```text
Mouse Move Absolute(x, y)

Right Mouse Down
30ms
Right Mouse Up
```

---

## CAMERA_RIGHT_DRAG

执行：

```text
Right Mouse Up
```

---

## LEFT_DRAG

执行：

```text
Left Mouse Up
```

---

最后：

```text
state = IDLE
```

---

# 12. ACTION_CANCEL 必须特别处理

这是非常重要的一项。

Android 可能因为：

- 系统手势
- 通知
- App 切后台
- 多点触控变化
- Activity pause

产生：

```text
ACTION_CANCEL
```

必须执行：

```java
releaseLeftMouseIfPressed();
releaseRightMouseIfPressed();
state = IDLE;
```

否则容易出现：

```text
鼠标右键一直处于按住状态
```

或者：

```text
鼠标左键卡住
```

这会非常影响 WoW。

建议同时在：

```text
onPause()
onStop()
```

增加安全释放。

---

# 13. Touch Slop 不要写死成 10 px

现有 Winlator：

```java
MAX_TAP_TRAVEL_DISTANCE = 10
```

对于 S10 这种高 DPI 手机，不建议继续用固定物理像素判断。

推荐：

```java
ViewConfiguration.get(context).getScaledTouchSlop()
```

并允许乘：

```text
1.0 ~ 1.5
```

默认：

```text
cameraDragSlop = scaledTouchSlop * 1.2
```

这样更适配不同 DPI 手机。

---

# 14. 推荐默认参数

三星 S10 起始值：

```text
Tap 最大时间：
250 ms

Long Press：
380 ms

Camera Drag Slop：
Android scaledTouchSlop × 1.2

Left Drag Slop：
Android scaledTouchSlop × 1.2

Left/Right Click Hold：
30 ms

Camera sensitivity：
1.0

Long Press haptic：
25 ms
```

全部最好做成后续可配置参数。

---

# 15. 多点触控优先级

必须支持：

```text
左拇指一直按移动摇杆
+
右拇指转镜头
```

以及：

```text
左拇指移动
+
右拇指点击技能
```

因此：

## InputControlsView

命中：

```text
D-PAD
JUMP
TARGET
```

的 Pointer：

```text
由 ControlElement 消费
```

其它 Pointer：

```text
转交 TouchpadView / NativeTouchController
```

现有 `InputControlsView` 已经是这种基本架构，因此应尽量复用。

---

# 16. 不要让左摇杆区域触发鼠标手势

NativeTouch 模式下保留的虚拟元素：

```text
D_PAD
JUMP
TARGET
```

它们必须优先于：

```text
Tap
Camera Drag
Long Press
```

即：

```text
Touch hits ControlElement
→ ControlElement owns pointer until ACTION_UP

Touch does not hit ControlElement
→ Native Touch owns pointer
```

---

# 17. 需要考虑 Pointer ID

现有 `TouchpadView` 使用：

```java
Finger[] fingers = new Finger[MAX_FINGERS];
```

并一定程度上按 Android `pointerId` 当数组索引。

Native Touch 新实现建议不要再依赖：

```text
pointerId < 4
```

最好使用：

```java
SparseArray<Finger>
```

或者：

```java
Map<Integer, Finger>
```

这样移动摇杆 + 屏幕手势长期运行更稳。

如果不想第一版扩大改动范围，可以保持旧结构，但应加入测试。

---

# 18. Absolute 与 Relative Mouse 分工

这是整个实现的技术重点。

## Absolute Mouse

用于：

```text
轻点
长按右键
左键拖 UI
```

函数：

```java
xServer.injectPointerMove(x, y)
```

---

## Relative Mouse

用于：

```text
右键拖动镜头
```

优先：

```java
WinHandler.mouseEvent(
    MouseEventFlags.MOVE,
    dx,
    dy,
    0
);
```

或者复用 Winlator 当前 relative mouse pipeline。

不要在 Camera Drag 时持续：

```java
injectPointerMove(x, y)
```

因为 WoW 的相机旋转更适合相对移动。

---

# 19. 建议新增一个独立控制器类

不要把全部逻辑硬塞进现有 `TouchpadView.java`。

建议新增：

```text
app/src/main/java/com/winlator/wow/WowNativeTouchController.java
```

职责：

```text
手势状态
Tap 判断
Long Press
Camera Drag
Left Drag
Haptic
ACTION_CANCEL
```

而：

```text
TouchpadView
```

继续负责：

```text
坐标转换
XServer 鼠标注入
已有滚轮
外接鼠标
```

这样未来同步 Winlator 上游时：

```text
TouchpadView 冲突更少
```

---

# 20. 推荐新增模式

在 WoW Mobile Settings 中增加：

```text
Touch Control Mode
```

选项：

```text
1. ConsolePort / Legacy
2. Native Touch
```

---

## Legacy

保持现在完全不动：

```text
A/B/X/Y
L1/L2
LOOK
LCLICK
RCLICK
ConsolePortLK
```

保证回退能力。

---

## Native Touch

自动：

```text
moveCursorToTouchpoint = true
pointerButtonLeftEnabled = true
pointerButtonRightEnabled = true
WowNativeTouchController = enabled
```

触屏 Profile 只保留：

```text
WASD D-PAD
JUMP = Space
TARGET = Tab
```

可选：

```text
MENU = Esc
```

---

# 21. 注意当前 showInputControls() 的一个冲突点

当前 `XServerDisplayActivity.showInputControls()` 会：

```java
touchpadView.setPointerButtonRightEnabled(false);
```

这是为旧虚拟手柄 Profile 防止冲突。

但是 Native Touch 模式必须：

```java
touchpadView.setPointerButtonRightEnabled(true);
```

否则：

```text
长按右键
Camera Right Drag
```

无法工作。

建议：

```java
if (touchMode == NATIVE_WOW_TOUCH) {
    touchpadView.setPointerButtonLeftEnabled(true);
    touchpadView.setPointerButtonRightEnabled(true);
} else {
    touchpadView.setPointerButtonRightEnabled(false);
}
```

---

# 22. ConsolePort 怎么处理

第一版：

**不要删除 ConsolePort。**

原因：

- 避免扩大修改范围
- Legacy 模式需要
- 用户可以随时回退

Native Touch 模式：

```text
不依赖 ConsolePort 释放技能
```

而是直接：

```text
手指
→ 鼠标左键
→ WoW 原生 ActionBar
```

后续确认 Native Touch 稳定后，可增加：

```text
Native Touch 时禁用 ConsolePort UI
```

作为第二阶段优化。

---

# 23. S10 推荐游戏分辨率

第一测试分辨率：

```text
1140 × 540
```

原因：

- 19:9
- 与 Galaxy S10 屏幕比例吻合
- WoW UI 在 6.1 英寸屏幕上相对较大
- 比默认 960×432 清晰
- Snapdragon 855 / Exynos 9820 都比 720p 压力低

第二档：

```text
1520 × 720
```

用于：

```text
Native Touch 已稳定
+
希望画面更清晰
```

但是 UI 会更小，点击难度上升。

Native Touch 第一阶段建议：

```text
1140×540
```

优先保证触控命中率。

---

# 24. 第一版不要做的东西

为了降低 Bug：

```text
不要做复杂多指快捷技能
不要做手势施法
不要做屏幕技能 Overlay
不要做双击技能
不要做 Pinch Zoom
不要自动判断 UI 区域和 3D 世界区域
不要修改 Wow.exe
不要修改 AzerothCore
```

只做：

```text
系统级鼠标/键盘模拟
```

---

# 25. MVP 修改文件建议

主要：

```text
app/src/main/java/com/winlator/widget/TouchpadView.java
app/src/main/java/com/winlator/XServerDisplayActivity.java
```

新增：

```text
app/src/main/java/com/winlator/wow/WowNativeTouchController.java
```

可能修改：

```text
app/src/main/java/com/winlator/widget/InputControlsView.java
```

只在必要时改。

设置 UI：

根据 WoW Mobile 当前 WoW Settings 实现位置增加：

```text
Touch Mode
Native Touch sensitivity
Long Press delay
```

---

# 26. MVP 功能优先级

## P0 必须

- [ ] 左摇杆 WASD
- [ ] 单击绝对位置左键
- [ ] 快速拖动右键 Camera Drag
- [ ] Camera Drag 使用相对鼠标
- [ ] 长按右键
- [ ] 长按后左键 Drag
- [ ] JUMP / Space
- [ ] TARGET / Tab
- [ ] ACTION_CANCEL 自动释放左右键
- [ ] 支持移动 + 镜头同时操作
- [ ] 支持移动 + 点击技能同时操作
- [ ] Legacy ConsolePort 可回退

## P1 推荐

- [ ] 双指滚轮
- [ ] Native Touch 参数设置
- [ ] 长按震动
- [ ] ESC 可选按钮
- [ ] 右键 Drag 灵敏度
- [ ] PointerId 健壮化

## P2 后续

- [ ] Pinch → Mouse Wheel
- [ ] 三指键盘
- [ ] Interact 独立按钮
- [ ] Native Touch 自动禁用 ConsolePort UI
- [ ] 自定义手势设置

---

# 27. 必测场景

## UI

- [ ] 点动作栏技能
- [ ] 点背包
- [ ] 点背包物品
- [ ] 拖背包物品
- [ ] 拖动作栏技能
- [ ] 点地图
- [ ] 点任务窗口
- [ ] 点商人窗口

## 世界

- [ ] 点怪选中
- [ ] 快速拖镜头
- [ ] 长按 NPC 打开对话
- [ ] 长按尸体拾取
- [ ] 长按世界对象互动

## 移动

- [ ] 摇杆向前 + Camera Drag
- [ ] 摇杆向前 + 点技能
- [ ] 摇杆向前 + Jump
- [ ] 摇杆向前 + Tab Target

## 猎人

- [ ] 移动中点自动射击/技能
- [ ] 宠物战斗时切目标
- [ ] 怪物近身时镜头操作
- [ ] 拾取尸体
- [ ] 拖宠物技能/动作条

## 稳定性

- [ ] Android 切后台时没有卡右键
- [ ] 来电/通知后没有卡左键
- [ ] 横屏恢复后坐标正确
- [ ] 1140×540 坐标正确
- [ ] 1520×720 坐标正确
- [ ] 连续 30 分钟无输入卡死

---

# 28. 调试日志

开发版增加可选日志：

```text
WOW_TOUCH DOWN
WOW_TOUCH TAP_LEFT
WOW_TOUCH CAMERA_START
WOW_TOUCH CAMERA_MOVE dx=...
WOW_TOUCH CAMERA_END
WOW_TOUCH LONG_PRESS_READY
WOW_TOUCH RIGHT_CLICK
WOW_TOUCH LEFT_DRAG_START
WOW_TOUCH LEFT_DRAG_END
WOW_TOUCH CANCEL_RELEASE_ALL
```

Release 关闭详细 Move 日志。

---

# 29. 成功标准

完成后用户体验应该是：

```text
左拇指：
摇杆走路

右拇指：
点技能 → 技能直接释放

点怪：
直接选中

拖动画面：
镜头立即旋转

长按 NPC/尸体：
右键互动 / 拾取

长按技能再拖：
移动技能位置

右下 Jump：
跳跃

Target：
Tab 换怪
```

并且：

```text
不需要 A/B/X/Y
不需要 L1/L2
不需要 LOOK 开关
不需要 LCLICK/RCLICK 按钮
不需要在技能图标上再覆盖透明虚拟按键
```

---

# 30. Codex 执行要求

请使用：

```text
GPT-6 Sol
Reasoning: High
```

任务：

> 基于当前 `marcocastignoli/wow-mobile` 最新源码，实现本文定义的 `Native Touch` 模式。保留 Legacy ConsolePort 模式，禁止破坏现有功能。优先复用现有 `TouchpadView` 的坐标变换、鼠标注入、滚轮和 `InputControlsView` 的未命中事件转发。新增独立 `WowNativeTouchController` 状态机。Native Touch Profile 默认仅保留 WASD D-PAD、JUMP(Space)、TARGET(Tab)。完成 debug APK 构建并列出改动文件、关键逻辑、测试方法。不要修改 WoW.exe，不修改 AzerothCore，不下载任何游戏客户端文件。

执行顺序：

1. 拉取当前最新 WoW Mobile。
2. 记录 base commit。
3. 阅读当前：
   - `TouchpadView.java`
   - `InputControlsView.java`
   - `XServerDisplayActivity.java`
4. 确认当前 `moveCursorToTouchpoint` 实现。
5. 新建功能分支：
   ```text
   feature/wow-native-touch
   ```
6. 实现 `Native Touch`。
7. 保留 Legacy。
8. 编译 Debug APK。
9. 输出 APK。
10. 提供实机 S10 测试 checklist。
11. 如果某个设计与当前源码不匹配，以当前源码为准做最小架构调整，但不得改变本文的最终手势语义。

---

# 31. 参考项目

WoW Mobile：

```text
https://github.com/marcocastignoli/wow-mobile
```

Winlator：

```text
https://github.com/brunodev85/winlator
```

Winlator native touchscreen feature request：

```text
https://github.com/brunodev85/winlator-app/issues/21
```

重点源码：

```text
app/src/main/java/com/winlator/widget/TouchpadView.java
app/src/main/java/com/winlator/widget/InputControlsView.java
app/src/main/java/com/winlator/XServerDisplayActivity.java
```

---

# 最终推荐

架构：

```text
WoW Mobile
├── Legacy ConsolePort Mode
│   └── 保持现有实现
│
└── Native Touch Mode
    ├── 左摇杆 → WASD
    ├── Tap → Absolute Left Click
    ├── Quick Drag → Right Mouse Camera Drag
    ├── Long Press → Right Click
    ├── Long Press + Drag → Left Drag
    ├── Two Finger Swipe → Mouse Wheel
    ├── Jump → Space
    └── Target → Tab
```

这是本项目第一版最合适的范围。

不要再增加其它功能，先把这套手势在 Galaxy S10 上调稳定。
