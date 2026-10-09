# 旧梦WOW 文档

整理日期：2026-10-08。现行功能截至2026-10-08键盘入口与原生模式修复（阶段v13）；APK版本为`0.1.2 / versionCode 3`，触控Lua插件为v8。六分片安装验收日期仍为10月7日；10月8日新增S10键盘与原生模式验收。

## 从这里开始

| 需要了解 | 文档 |
| --- | --- |
| 安装、客户端下载、触控操作、换手机后哪些设置自动继承 | [安装与使用手册](OldDreamWOW_New_Device_Setup_Checklist.md) |
| 模块边界、续传、安全解压、界面规则、构建发布及验证范围 | [开发维护手册](OldDreamWOW_Development_Guide.md) |
| 原始方案、阶段修改、S10验收与截图 | [历史资料索引](archive/README.md) |

当前默认原生触控，高720并按屏幕比例计算宽度；顶部提供小型键盘图标入口，左右按钮为人物横移，右侧动作栏与文字放大，点击空地保留目标。缺少客户端时提供六片下载、续传、校验和自动解压，完成后自动启动；Play后的宣传加载页显示真实计时和按历史耗时推进的百分比。

## APK与资源

- [手机下载最新固定APK](https://cloud.f-li.cn:6500/wow/OldDreamWOW.apk)；[本机APK副本](OldDreamWOW.apk)。发布位置：`E:\rxdev\webhost\wow\OldDreamWOW.apk`。
- [应用图标原图](assets/brand/OldDreamWOW_App_Icon_20261005.png)。
- [S10触控配置](assets/touch/WoW-Mobile-S10-Touch-Skills.icp)与[原始ConsolePort布局备份](assets/touch/WoW-Mobile-S10-Original-ConsolePortLK.icp)。新安装自动建立配置，通常不需导入。

本次核对本机APK大小：170,980,247字节；SHA-256：`DDAE489E401BBEB4506884CC5111DBCE8AFB43772D156E167E5C9BF93D9EEAB6`。后续发布重新核对。APK不包含约20GB客户端，APK产物不提交Git。

## 目录约定

```text
docs/
  README.md                               文档入口
  OldDreamWOW_New_Device_Setup_Checklist.md 安装与使用手册
  OldDreamWOW_Development_Guide.md          开发维护手册
  OldDreamWOW.apk                          当前本机APK
  assets/brand/                           图标资源
  assets/screenshots/                     历史实机截图
  assets/touch/                           触控配置与备份
  archive/README.md                       历史索引
  archive/history/                        原始阶段文档
  archive/apk/                            本机旧APK
```

日常操作和现行规则统一维护两份主手册；新增阶段验收记录放入归档并补充历史索引。历史文档中的旧分辨率、下载地址、APK哈希和待办状态只代表当时版本。
