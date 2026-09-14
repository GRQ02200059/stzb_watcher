STZB 助手 Windows x64 便携版

支持 Windows 10 1903 及以上版本、Windows 11（x64）。

1. 将 ZIP 完整解压到当前用户可写的目录，再双击 STZB助手-Web.exe。
   请保留 _internal 和 runtime 目录，不要只复制 exe，不要从 ZIP 内直接运行。
2. 程序就绪后自动打开浏览器；本地地址默认为 http://127.0.0.1:8080/。
   保持控制台窗口运行，关闭窗口会停止服务。
3. 已包含 Python 和 Java 运行环境，无需额外安装。
4. Windows 实时抓包需要安装 Npcap（https://npcap.com/#download）。
   请启用 WinPcap API compatibility 选项，按驱动配置授予抓包权限。
   没有驱动时仍可使用已有数据；可从终端运行：
   .\STZB助手-Web.exe --no-sniffer
5. 数据库、账号档案和抓包输出保存在应用目录。升级前停止旧程序并备份这些数据，
   用新包中的程序、_internal 和 runtime 替换旧程序文件，保留自己的数据。
6. 启动失败时，打开 PowerShell，进入解压目录并执行：
   & '.\STZB助手-Web.exe' --no-browser --no-sniffer *> startup.log
   提供 startup.log 和 build-info.json 中的 version、commit 字段即可定位版本。
7. build-info.json 记录构建版本与文件校验值；实际 Windows 验收结果见下载页 smoke-report.json。
   CI 基础验收不代表已验证你的网卡、Npcap 权限或真实游戏流量。
