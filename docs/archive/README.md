# 旧梦WOW 历史资料索引

本目录保留原始设计和阶段验收，文中数值、下载地址、APK哈希、待办状态只适用于当时版本。当前功能统一看[安装与使用手册](../OldDreamWOW_New_Device_Setup_Checklist.md)和[开发维护手册](../OldDreamWOW_Development_Guide.md)，总入口见[文档首页](../README.md)。

整理日期2026-10-08；旧测试没有重新执行；10月8日新增的验收单独列出。原文保留，仅增加历史提示和修复资源链接。

## 阶段文档

| 日期 / 阶段 | 主题与原始记录 |
| --- | --- |
| 初始设计 | [原生触控技术方案](history/WoW_Mobile_Native_Touch_Technical_Plan.md) |
| 10-03 | [原生触控实现与S10验证](history/WoW_Mobile_Native_Touch_Implementation_20261003.md) |
| 早期布局 | [S10触控技能布局](history/WoW_Mobile_Touch_Skill_Layout.md) |
| 10-04 / v5 | [1280布局与触点修复](history/WoW_Mobile_1280_Layout_Fix_20261004.md) |
| 10-05 / 方案复核 | [显示与自动准备客户端流程复核](history/OldDream_Client_Startup_and_Display_Review_20261005.md) |
| 10-05 / v7 | [启动下载与720高度适配](history/OldDreamWOW_Bootstrap_and_720p_20261005.md) |
| 10-05 / v8 | [完整安装、LAN下载及右侧技能布局](history/OldDreamWOW_Full_Install_and_Touch_20261005.md) |
| 10-05 / v9 | [容量检查、后台授权与控件位置](history/OldDreamWOW_Storage_and_Bar_Adjustment_20261005.md) |
| 10-05 / v10 | [加载页与技能误触目标保护](history/OldDreamWOW_Loading_and_Target_Guard_20261005.md) |
| 10-05 / v11 | [真实计时、历史耗时与120秒入口](history/OldDreamWOW_Startup_History_20261005.md) |
| 10-07 / v12 | [六分片下载、片内续传、SHA-256及完整安装验收](history/OldDreamWOW_Split_Client_Download_20261007.md) |
| 10-08 / v13 | [顶部键盘入口、旧ConsolePort隔离与原生模式验收](history/OldDreamWOW_Keyboard_and_Native_Mode_20261008.md) |

## 如何使用证据

- v12记录六片共19,982,223,800字节下载校验、第二片内关闭App后续传、2,727条目解压与自动进入登录页。该轮没有登录账号进入角色。
- v11记录正常加载、真实计时、历史曲线以及两分钟前后手动入口边界。
- v10及之前的游戏内记录覆盖S10布局、技能空隙命中、保留目标、切换目标、Esc、横移和镜头；不代表所有手机或持续战斗已经验收。
- 更早的800×600、固定宽1280、ConsolePort默认、单ZIP下载及60秒手动入口已经被后续版本替代。查构建、测试的原始输出时，以相应阶段文档为准。

## 配套资源

- [实机截图目录](../assets/screenshots/)：51张阶段截图，各阶段文档直接链接对应图片。
- [图标资源目录](../assets/brand/)与[触控配置目录](../assets/touch/)：保留图标原图、实际配置及原始布局备份。
- [旧APK目录](apk/)：当前保留一份v7本机快照；较早快照已不在此目录，原文的旧版本信息仅作历史记录。APK忽略Git，不随源码拉取。当前固定APK位于[docs根目录](../OldDreamWOW.apk)。

后续原始验收记录继续放`history/`并更新本索引；现行行为修改同时更新两份主手册，避免多个阶段文件被当作当前操作指南。
