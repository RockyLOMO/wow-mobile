# 旧梦WOW v8：S10 完整安装验证、下载限速与触控布局

## 发布约定

每次本机打包后更新 `E:\rxdev\webhost\wow\OldDreamWOW.apk`，公共文件名固定，不带日期或版本后缀。

推荐使用 `tools/build-olddream.ps1`：构建成功后复制、核对 SHA-256，再替换固定 APK；构建失败不发布。已完成构建和设备验证后，可用 `-PublishOnly` 只同步 APK。需沿用本机已有 JDK17 设置。源码版本仍为3/0.1.2，避免上游控件管理器覆盖玩家的配置。

下载地址：<https://cloud.f-li.cn:6500/wow/OldDreamWOW.apk>。

## 改动边界

- 保持高度720和按屏幕比例计算宽度；S10仍为1520×720。
- 独立 WoWMobileTouchUI v5 插件将右侧每条12格技能栏排成两列六行，原按钮、技能槽、快捷键、显隐规则保持；缩放1.20，距顶部225像素，右边留4像素。第二条侧栏启用时放在第一条左边。
- 原底栏最高1.35倍、文字13–16及UIParent由游戏控制的规则保留。战斗中延迟布局调整，脱战后更新。
- Native“目标／跳跃”从x=.94移到x=.81，为最多四列技能留出空间；S10中心1227像素。经游戏坐标输出、768高UI画布到720渲染的换算验证，两条侧栏最左1330.5像素，“目标”描边最右1282.875像素，间距约47.6像素。图标40.5像素、栏高276.75像素、顶边225像素、底边501.75像素。
- Dpad中心从136改为149像素。半宽147、描边3.75，左描边外缘约0.125像素，完整贴近左边。仅迁移原始位置与标签，玩家自定义位置保留。
- 首次安装默认连接`cloud.f-li.cn`，保留已有玩家指定的服务器。Native新装不再添加ConsolePort手柄校准插件；首次登录由独立插件设置W/S移动、A/D平移、空格跳跃与Tab选择目标。切换Legacy模式时仍可安装ConsolePort。
- 每次启动统一`gxWindow=1`、`gxMaximize=1`，在匹配分辨率的Wine桌面内最大化。复用旧客户端发现独占全屏配置丢失键盘焦点；统一后原客户端密码输入、登录及进入世界均恢复，保持画面铺满。
- 断网错误改为中文；完整ZIP在进程重启后显示“完整压缩包已保留，点击继续解压”。安装通知打开页面后保持安装完成自动启动意图。
- 已知旧版v2/v3/v4插件可自动升级；玩家自行改过的Lua/TOC保留。没有新增上游公共运行引擎改动。

## nginx实际链路和策略

S10 `192.168.31.54` → Ubuntu VM `192.168.31.4:6500` → Windows `192.168.31.7:6500` → `E:\rxdev\webhost\wow`。只改Windows会让公网转发被当成VM局域网来源，因此入口VM也需要相同策略。

两层使用`geo`：192.168.31.2–254为局域网；31.0、31.1、31.255及其他地址不是局域网。`map`将局域网速率设为0，其余1250000字节/秒。公网ZIP保留同一共享并发槽、超额429；局域网键为空，不消耗该槽。

```nginx
geo $wow_lan {
    default 0;
    192.168.31.0/24 1;
    192.168.31.0/32 0;
    192.168.31.1/32 0;
    192.168.31.255/32 0;
}
map $wow_lan $wow_download_rate { default 1250000; 1 0; }
map "$wow_lan:$uri" $wow_package_key {
    default "";
    ~^0:/wow/.*\.zip$ "wow";
}
limit_conn_zone $wow_package_key zone=wow_package:1m;
# /wow/ location:
limit_conn wow_package 1;
limit_conn_status 429;
limit_rate $wow_download_rate;
```

入口VM两处/wow代理使用`proxy_buffering on; proxy_max_temp_file_size 0; proxy_buffers 8 64k;`。实测原无缓冲代理未执行出口限速；开启内存缓冲后恢复，禁止磁盘临时缓冲，避免20GB下载产生大代理缓存。

实际配置：Windows `D:\home\nginx-1.28.0\conf-http\00-wow-rate-zone.conf`与`static-web.conf`；VM `/etc/nginx/conf.d/wow-rate-zone.conf`与`/home/nginx/conf-http/static-web.conf`。两层`nginx -t`通过并已重载。Windows既有wasm重复类型警告不影响检查通过。

回退原文件保存在本机`T:\TEMP\ai\20261005\wow-full-client-validation\*-original.conf`；VM `/tmp/olddream-static-web-before-lan.conf`、`/tmp/olddream-wow-rate-before-lan.conf`。VM缓冲调整前另备份`/tmp/olddream-static-web-before-buffering.conf`。

实测速率：32MiB LAN经两层代理HTTP206、0.626478秒、53,560,431B/s；VM非LAN回环8MiB HTTP206、6.334817秒、1,324,206B/s（启动容许瞬间发送，长传趋于配置值）。Windows非LAN回环8MiB约1MB/s。S10完整下载最后16,797,143,446字节用268.605秒，约62.5MB/s。入口日志确认手机`lan=1 rate=0`，Windows看到VM同样`lan=1 rate=0`。

参考：[nginx geo](https://nginx.org/en/docs/http/ngx_http_geo_module.html)、[limit_rate](https://nginx.org/en/docs/http/ngx_http_core_module.html#limit_rate)、[proxy缓冲](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_buffering)。

## 本次设备测试记录

设备SM_G9730，Android12/API31，主用户0原地升级。

1. 原客户端临时改名为`WoW335CN.validation-backup-20261005`保留数据，以缺客户端状态开始真实下载。
2. 下载2,943,647,466字节后通过UI暂停；恢复HTTP206从同一偏移续传。
3. 下载过程中强制停止并重开；恢复请求`Range: bytes=3065986468-`。
4. 下载时断开WiFi约20秒，收到网络错误并保留3,183,918,174字节；恢复网络后请求`Range: bytes=3183918174-`继续。
5. 熄屏5秒下载仍推进到3,618,069,272字节，安装唤醒锁存在；解压阶段也验证熄屏继续。
6. 完整ZIP为19,981,061,620字节。手机`sha256sum`为`0879ebdd9d98aa4883d76f7eb5d8021113569c5e8a9d263192a3e21f4f2bfe79`，与源ZIP一致，证明多次续传拼接后文件完整。安装器本身仍执行逐条CRC，不声称内置SHA认证。
7. 解压到6.44GB后通过UI暂停，ZIP保留；APK升级、进程重启后识别完整ZIP，并从头解压，不重下20GB。由通知返回安装页面已实测。
8. Java回归：ClientInstallerTest与TouchCoordinateMapperTest均PASS，覆盖Range/200/ETag、损坏恢复、目录保护以及五种屏幕比例触点映射。APK构建BUILD SUCCESSFUL。

9. 完整2300条目解压、CRC及资源验收通过；安装目录发布到`Download/OldDream/client`，ZIP/staging/metadata自动清空，安装服务结束、唤醒锁释放。解压平均约36–55MB/s，末段小文件使文件计数继续而字节百分比接近100%。
10. 安装完成后未再点“进入游戏”，页面自动进入XServerDisplayActivity；首次新客户端已登录、选角色并进入世界，未弹手柄校准。Config.wtf为1520x720、realmlist为cloud.f-li.cn。首次冷启动读取游戏资源约1分钟，后续复用缓存更快。
11. 世界画面铺满，底栏/背包完整。脚本错误显示打开后加载/重载新Lua，没有出现错误窗口；游戏内坐标输出验证栏尺寸、缩放与位置。两条侧栏均启用，空槽被客户端插件隐藏，因此截图部分位置没有图标，不代表仍是一列。右侧奥术射击长按后光标/提示命中图标；左右平移、跳跃已操作验证。
12. 原客户端已恢复到`WoW335CN`，本次自动安装的20GB测试副本已删除，下载缓存空。最终APK冷启动自动检测原客户端并直启；原v4触控插件升级v5、容器F盘重新指向原路径已核对。
13. 并发验证：VM非LAN5秒受限下载进行中，第二个同类ZIP请求429；同时LAN请求HTTP206约50MB/s，未占用公网并发槽。
14. 最终APK实际正常退出游戏后返回旧梦WOW页面；进入游戏设置再返回，不会自动重进。WoW/Wine进程已结束。最终媒体音量0（范围0–15），充电常亮0，App强制停止，屏幕Dozing；主用户0有App、工作资料用户10无App，安装服务无存活项，原客户端存在、测试副本及ZIP缓存均已清理。

截图：`OldDreamWOW_S10_full_download_v8.png`、`resume_v8.png`、`extract_pause_v8.png`、`extract_v8.png`、`autolaunch_v8.png`、`world_v8.png`、`right_skill_v8.png`（均有OldDreamWOW_S10_前缀）。

新装RootFS失败分支、实际公网路由、已有禁用角色切回Legacy的启用设置及其他Android机型未做实机覆盖；不能用本次结果承诺所有设备帧率或所有Android版本兼容。

## 最终APK

`OldDreamWOW.apk`：168,123,268字节，SHA-256 `852F2AB8C460571981D062F567F2D476BD688E02742D43C6494B9C9754912A4B`。

主用户0安装最终APK成功；旧客户端实际进入世界、v4插件自动升级。发布脚本执行成功，网页地址完整下载HTTP200、168,123,268字节，下载文件哈希与本机APK一致。公共目录与本机docs均使用固定文件名。
