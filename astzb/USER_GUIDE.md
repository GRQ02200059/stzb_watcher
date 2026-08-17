# st助手安卓独立版 使用文档

如果需要查看按标签页展开的详细功能说明，请参考：

- [TAB_FEATURE_GUIDE.md](file:///Users/bytedance/stzb_watcher/astzb/TAB_FEATURE_GUIDE.md)

## 1. 应用信息

- 应用名称：`st助手安卓独立版`
- 当前包名：`com.netease.stzb.netease`
- 当前正式 APK 路径：
  - `app/build/outputs/apk/release/app-release.apk`

## 2. 普通用户下载与安装

普通用户不需要下载源码或自行编译。请打开项目的
[Releases 页面](https://github.com/GRQ02200059/stzb_watcher/releases)，进入最新版本，
在 `Assets` 中点击 `app-release.apk` 下载，然后在手机上点击 APK 安装。

下载时注意：

- 不要下载 `Source code (zip)` 或 `Source code (tar.gz)`；
- 不要优先安装 `app-debug.apk`；
- 如果同时存在 `app-release-unsigned.apk`，请改用已签名的 `app-release.apk`；
- 如果 Android 系统提示限制安装未知应用，请允许当前浏览器或文件管理器安装应用后重试。

也可以使用 ADB 安装已下载的正式包：

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" install -r /path/to/app-release.apk
```

## 3. 安装要求

### 3.1 系统要求

- Android 13 及以上
- ARM 设备
  - `arm64-v8a`
  - `armeabi-v7a`

说明：

- 当前工程 `minSdk = 33`，Android 13 以下设备无法安装。
- x86 / x86_64 模拟器默认不在当前 APK 支持范围内。

### 3.2 构建产物安装方式

推荐安装正式包：

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" install -r /Users/bytedance/stzb_watcher/astzb/app/build/outputs/apk/release/app-release.apk
```

如果是手动拷贝到手机安装，请确认选择的是：

```text
app-release.apk
```

不要误装：

```text
app-debug.apk
```

否则可能遇到测试包安装限制。

## 4. 如何生成正式 APK

项目根目录已提供一键脚本：

```bash
cd /Users/bytedance/stzb_watcher/astzb
./build_release_apk.sh
```

脚本会自动执行：

1. `clean`
2. `assembleRelease`
3. `apksigner verify`

如果需要查看详细出包说明，请参考：

[BUILD_RELEASE.md](file:///Users/bytedance/stzb_watcher/astzb/BUILD_RELEASE.md)

## 5. 首次启动怎么用

首次打开 App 后，建议按下面顺序操作：

1. 进入主页面
2. 确认目标包名为：
   - `com.netease.stzb.netease`
3. 点击抓包相关入口
4. 按系统提示授予 VPN 权限
5. 返回游戏进行联网操作
6. 再回到本 App 查看解析结果和各业务页面

## 6. 主流程使用说明

### 5.1 启动抓包

抓包主链路是：

```text
目标 App -> VpnService -> 本地代理桥接 -> App 内解析 -> SQLite 入库 -> Dashboard 展示
```

常规使用流程：

1. 打开 `st助手安卓独立版`
2. 进入抓包或主控页面
3. 点击启动抓包 / 启动桥接
4. 系统弹出 VPN 授权时点确认
5. 切到游戏执行登录、切地图、打开战报、查看队伍等操作
6. 回到本 App 查看日志和业务页面

### 5.2 查看解析日志

日志页默认应关注 STZB 业务解析结果，而不是原始杂项流量。

建议重点看：

- 是否出现协议解析记录
- 是否出现 5028 / 5026 / 10 / 92 / 2100 等业务日志
- 是否出现本地入库成功提示

### 5.3 打开数据看板

抓到数据后，进入 `Dashboard` 查看迁移后的各业务页面。

侧栏通过左上角 `菜单` 按钮打开，不再依赖边缘左滑唤起。

## 7. 主要页面怎么使用

以下页面都在 Dashboard 侧栏或对应入口中。

### 6.1 全部战报

用途：

- 查看完整战报列表
- 支持按玩家、同盟、结果、时间等筛选

适合用来确认：

- `10 / 92` 是否成功抓到
- 战报详情是否已展开入库

### 6.2 同盟成员

用途：

- 按分组查看成员
- 展开 / 收起分组
- 点击成员进入详情子页

适合用来确认：

- `103` 同盟成员链路是否正常

### 6.3 工程考勤

用途：

- 管理任务列表
- 新建任务
- 查看任务概览、成员、战报、操作子页
- 导出 CSV

适合用来做：

- 攻城任务统计
- 到勤核对

### 6.4 团数据

用途：

- 按分组 / 按成员查看统计
- 按周期切换
- 导出 CSV

适合用来确认：

- 本地战报聚合统计是否正确

### 6.5 战斗模拟

用途：

- 查看和编辑攻守双方阵容
- 调整等级、进阶、战法
- 运行单次或多次模拟

### 6.6 州郡分布

用途：

- 查看州 / 同盟 / 分组统计
- 支持全部成员或指定团范围
- 指定团场景使用搜索 Picker 选择

### 6.7 实时队伍监控

对应网页端 5028 监控页。

用途：

- 查看实时队伍流
- 自动每 5 秒刷新
- 支持按玩家、同盟、队伍 ID、坐标、wid 搜索

适合用来确认：

- 5028 行军监控链路是否正常
- 新数据是否持续进入

### 6.8 辅助战场监控

对应网页端 13A2 / 5026 相关辅助页。

用途：

- 查看辅助战场监控
- 结合地块、队伍、阵容、战绩做索引

适合用来确认：

- 5026 地图地块与辅助队伍索引链路是否正常

## 8. 刷新与导出

### 7.1 刷新

当前主要页面都带刷新逻辑。

使用建议：

- 切到对应页面后点击刷新按钮
- 实时队伍监控页面本身也会自动轮询刷新

### 7.2 导出

当前支持多类导出，常见包括：

- 导出抓包结果
- 导出解析后的 STZB 包
- 导出本地数据库
- 导出工程考勤 CSV
- 导出团数据 CSV

如果你要排查问题，优先保留：

1. 当前 APK
2. 导出的数据库
3. 导出的解析包

## 9. 常见问题

### 8.1 安装失败 `-15`

通常是安装到了测试包，或安装方式把 APK 当成 `testOnly` 处理。

处理方式：

- 优先安装 `app-release.apk`
- 不要安装 `app-debug.apk`
- 如果必须安装 debug 包，则使用：

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" install -r -t /path/to/app-debug.apk
```

### 8.2 安装失败：签名冲突

现象：

- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

原因：

- 手机上的同包名旧包使用了不同签名

处理方式：

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" uninstall com.netease.stzb.netease
```

然后重新安装正式包。

### 8.3 安装失败：版本过低

现象：

- `INSTALL_FAILED_VERSION_DOWNGRADE`

处理方式：

- 提高 `versionCode`
- 或测试时使用：

```bash
"$HOME/Library/Android/sdk/platform-tools/adb" install -r -d /path/to/app-release.apk
```

### 8.4 安装失败：系统版本不满足

原因：

- 当前包要求 Android 13 及以上

### 8.5 能启动但抓不到数据

优先检查：

1. 是否真的点了 VPN 授权
2. 是否启动了抓包 / 桥接
3. 是否在游戏里做了联网操作
4. 是否查看了正确的业务页面
5. 日志里是否有协议解析失败提示

### 8.6 看不到侧栏

当前侧栏只支持：

- 点击左上角 `菜单` 打开
- 点击遮罩关闭
- 打开后左滑收起

不再支持边缘左滑直接唤起。

## 10. 推荐使用流程

建议你平时按下面这个顺序使用：

1. 运行 `./build_release_apk.sh`
2. 安装 `app-release.apk`
3. 首次打开时完成 VPN 授权
4. 进入游戏触发数据
5. 回到 App 看 `实时队伍监控`
6. 再看 `全部战报`、`同盟成员`、`工程考勤`、`团数据`
7. 需要排错时导出数据库和解析包

## 11. 文档索引

- 工程说明：[README.md](file:///Users/bytedance/stzb_watcher/astzb/README.md)
- 出包说明：[BUILD_RELEASE.md](file:///Users/bytedance/stzb_watcher/astzb/BUILD_RELEASE.md)
- 当前使用文档：[USER_GUIDE.md](file:///Users/bytedance/stzb_watcher/astzb/USER_GUIDE.md)
