# 率土助手

《率土之滨》战场数据查看与分析工具。

## 问题反馈：QQ群 `1063894809`

## 产品预览

### Web 端

![Web 战场情报](docs/assets/screenshots/overview-intelligence.webp)

Web 端用于查看战场情报、实时部队、队伍分析、积分和战术推演。

### Web 功能示例

| 实时部队 | 战斗模拟 |
|---|---|
| ![Web 实时部队](docs/assets/screenshots/gallery-live-army.webp) | ![Web 战斗模拟](docs/assets/screenshots/gallery-simulator.webp) |

| 阵容战法研究 | 自定义积分 |
|---|---|
| ![Web 阵容战法研究](docs/assets/screenshots/gallery-research.webp) | ![Web 自定义积分](docs/assets/screenshots/gallery-score.webp) |

| 打城考勤 | 玩家队伍 |
|---|---|
| ![Web 打城考勤](docs/assets/screenshots/gallery-attendance.webp) | ![Web 玩家队伍](docs/assets/screenshots/gallery-player-teams.webp) |

### Android 端

| 战场 | 战报 |
|---|---|
| ![Android 战场](docs/assets/screenshots/android-battlefield.webp) | ![Android 战报](docs/assets/screenshots/android-teams.webp) |

| 工具中心 | 战术演练 |
|---|---|
| ![Android 工具中心](docs/assets/screenshots/android-simulator.webp) | ![Android 战术演练](docs/assets/screenshots/android-tactical-drill.webp) |

移动端支持战场查看、战报浏览、工具操作和战术演练。

## Windows 版

### 下载

1. 打开项目的 [Releases 页面](https://github.com/GRQ02200059/stzb_watcher/releases)。
2. 进入最新版本。
3. 对采用新打包流程的版本，在 `Assets` 中下载 `STZB-Web-Windows-x64-*.zip`。
4. 完整解压到当前用户可写的目录，保留 `_internal` 和 `runtime` 子目录。

未发布 Release 的构建，可在 [Windows Actions](https://github.com/GRQ02200059/stzb_watcher/actions/workflows/build-windows-web.yml) 下载 `STZB-Web-Windows-verified-*` 产物；只有通过独立 Windows 验收才会出现该产物。旧版本的单文件 EXE 不适用新流程的验收保证。

不要下载 `Source code (zip)` 或 `Source code (tar.gz)`，那是项目文件，不是可直接使用的程序。

### 使用

1. 双击 `STZB助手-Web.exe`。
2. 稍等片刻，程序会自动打开浏览器。
3. 如果浏览器没有自动打开，请手动访问：`http://127.0.0.1:8080`。
4. 按页面提示登录。
5. 保持程序窗口运行，即可继续使用。

新目录包内置 Python 与 Java，无需自行安装。Windows 实时抓包仍需安装 [Npcap](https://npcap.com/#download) 并启用 WinPcap API compatibility；没有驱动时可用 `STZB助手-Web.exe --no-sniffer` 启动已有数据分析。具体步骤和错误日志采集见包内 `README-Windows.txt`。

Mac 开发后的构建、验收和发布方式见 [Windows 打包流程](docs/windows-packaging.md)。

关闭程序后，网页服务也会停止。下次使用时重新双击 EXE 即可。

## Android 版

### 下载与安装

1. 打开项目的 [Releases 页面](https://github.com/GRQ02200059/stzb_watcher/releases)。
2. 进入最新版本，展开 `Assets`。
3. 下载 `app-release.apk`。
4. 点击下载完成的 APK 进行安装。
5. 如果手机提示不允许安装，请在系统设置中允许当前浏览器或文件管理器安装应用。

请优先选择 `app-release.apk`，不要选择源码压缩包或其他测试安装包。

### 首次使用

1. 打开“率土助手”。
2. 按页面提示登录。
3. 进入“工具”，打开“抓包启动台”。
4. 点击“搜索并选择 App”，选择需要查看的游戏。
5. 点击“启动抓包”，并允许系统请求。
6. 切换到游戏中进行登录、打开地图、查看战报等操作。
7. 返回“率土助手”，查看“战场”或“战报”。

使用结束后：

1. 返回“工具”。
2. 打开“抓包启动台”。
3. 点击“停止抓包”。

## 常用功能

- **战场**：查看地图、城池、行军和战场动态。
- **战报**：查看战斗记录和战斗详情。
- **队伍**：查看玩家队伍、武将和战法。
- **同盟**：查看成员信息、考勤和积分。
- **阵容研究**：对比阵容和战法表现。
- **战斗模拟**：配置双方队伍，查看推演结果。

## 页面没有数据怎么办

1. 确认已经登录正确的账号和区服。
2. Android 用户确认已经启动抓包，并允许相关系统请求。
3. 切换回游戏执行一次联网操作，例如打开地图或查看战报。
4. 返回“率土助手”后刷新页面。
5. 如果仍然没有数据，请联系反馈群。

## 使用注意

- 本项目免费使用，禁止倒卖。
- 请只在自己有权使用的设备和账号上使用。
- Windows 版使用时不要直接关闭程序窗口。
- Android 版结束使用后，请主动停止抓包。
- 请从项目 Releases 页面下载最新版本。

## 反馈

问题反馈：QQ群 `1063894809`
