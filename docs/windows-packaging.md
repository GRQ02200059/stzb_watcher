# Windows 应用打包与验收流程

状态：2026-09-14 设计稿。本文定义要实施的新流程；当前 CI 尚未实现这些验收步骤。

## 交付目标

在 Mac 上开发、提交代码，由 Windows CI 构建 Windows x64 应用。用户下载 ZIP、完整解压、双击 `STZB助手-Web.exe`，即可打开本地网页。无需自行安装 Python、Java，也无需复制仓库中的数据文件。

默认交付 PyInstaller `onedir` 目录包。原因是本项目同时包含 Python 服务、前端静态资源、配置表和 Kotlin/JVM 战斗引擎；目录包方便检查依赖，也避免每次启动都解压全部运行库。将来需要安装程序时，可以用安装器封装同一份已经验收的目录包。

Windows 实时抓包依赖 Npcap 系统驱动。未安装驱动时，应用应仍能打开并使用已有数据，抓包入口给出明确安装提示。不能把复制 `wpcap.dll` 当成驱动安装成功，也不能让该依赖的失败阻断整个 Web 服务。

## 已核实的现状

- `api_server.py` 使用 `BASE_DIR` 查找情报快照，但 PyInstaller 把 `data` 放在资源目录。这一错误已在隔离模拟中复现：启动时找不到 `data/intelligence/client-9.2.2/manifest.json`。
- 当前 `.github/workflows/build-windows-web.yml` 只检查 exe 是否存在，然后上传；没有启动应用、请求接口或模拟对局。
- 当前 spec 收集整个 `battle-engine` 目录，但 Windows CI 没有编译 JVM 引擎，也没有准备随包 Java。
- `battle_engine_adapter.py` 默认调用 Gradle 生成的 Unix 启动脚本，Windows 不能直接沿用该启动方式。
- 2026-09-13 Windows 构建日志有 `Library wpcap.dll required via ctypes not found` 警告。这证明构建环境缺少该依赖，不足以判定用户遇到的具体报错。

## 发布目录

```text
STZB-Web-Windows-x64-<version>-<short-sha>/
  STZB助手-Web.exe
  _internal/                 # Python 运行库及应用只读资源
    static/
    data/
    protocol/
    hero_scraper/output/     # 运行时确实读取的数据文件
    battle-engine/          # 所需配置与 SOURCE.json，不复制整套源码
  runtime/java/             # 固定版本的 Windows x64 Java 17 运行环境
  runtime/battle-engine/lib/ # 本次代码编译出的引擎与依赖 JAR
  build-info.json
  README-Windows.txt
```

发布 ZIP 外附 `SHA256SUMS.txt`。哈希用于确认文件完整性，不能替代 Windows 代码签名。

数据库、账号档案、抓包内容、日志和开发机配置不得进入发布包。首版沿用现有便携模式：可写数据保存在应用目录，说明书要求解压到当前用户可写的位置。暂不迁移已有用户的数据位置。

## 资源和启动约定

1. 内置静态页面、配置表、武将资料和协议目录统一从资源根目录读取：打包态使用 `sys._MEIPASS`，源码态使用项目目录。
2. 数据库、档案和抓包输出使用可写应用目录。不要用当前工作目录推断这两种路径。
3. 打包态通过绝对路径调用随包 `runtime/java/bin/java.exe`，使用参数列表传递 `-cp`、JAR 路径和主类 `com.stzb.battle.cli.BattleEngineCliKt`，不依赖 PATH、系统 Java 或 Unix shell 脚本。具体 JAR 与配置路径通过真实模拟请求验收。
4. 浏览器应在本地服务已就绪后打开。启动失败时保留控制台错误和日志位置。
5. 所有调用 Python 脚本的入口都要检查冻结环境：打包后的 `sys.executable` 是应用 exe，不能继续当成 Python 解释器执行 `.py` 文件。涉及的导入入口需用独立回归测试约束，再做局部适配。

## CI：构建 → 验收 → 发布

### 1. 确定输入

- 手动构建允许选择分支；日志和 `build-info.json` 记录实际 checkout 的完整 commit SHA。
- Pull request 执行构建和验收；主分支相关变更自动执行。触发路径覆盖 Python 模块、requirements、静态资源、数据、协议、引擎及打包脚本。
- 发布是单独的显式选项，默认关闭。校验 release 标签与本次 commit 一致；不覆盖指向其他 commit 的既有版本。
- 固定 Windows runner 系列、Python 3.11、Java 17、Gradle、PyInstaller，以及包含传递依赖的 Windows 构建锁文件。工具升级作为独立变更验收。仅固定 `windows-latest` 名称不等于固定环境。

### 2. 干净构建

在 Windows 构建 job 中执行：

1. checkout 指定 commit，不携带开发机目录。
2. 建立 Python 虚拟环境，按锁文件安装依赖，执行 `pip check`。
3. 校验资源清单：所需文件必须存在、JSON 可解析、关键配置表非空；失败立即退出。
4. 运行资源路径、启动器和引擎适配相关回归测试。
5. 配置固定 Java 17 和 Gradle，在独立的 `battle-engine` 项目中运行 `gradle -p battle-engine test installDist`。不使用根目录 Android 构建作为桌面构建入口。
6. 收集本次 `installDist` 的 JAR、固定版本 Windows Java 运行环境及其许可文件。下载的运行环境必须验证发布方校验值。
7. 从明确的资源清单创建 PyInstaller 目录包；只复制运行所需内容，不递归收集开发仓库、历史 build 或账号资料。
8. 写入版本、commit、运行时版本和资源清单摘要，生成候选 ZIP。
9. 上传候选 ZIP 给验收 job；候选包不是可发布版本。

PowerShell 每个外部命令执行后显式检查 `$LASTEXITCODE`。不能只依赖 `$ErrorActionPreference` 或残留的 exe 判断成功。

### 3. 独立 Windows 验收

使用第二个 Windows job，仅下载候选 ZIP 和验收脚本，避免应用意外使用构建工作区的资源。GitHub runner 自带开发工具，因此仅换一个 job 仍不够：应用子进程必须清除 Python/Java 环境变量，限制 PATH，确保只使用随包运行时。

验收步骤：

1. 解压到包含中文和空格的临时路径，工作目录设为另一个空目录。
2. 启动 `STZB助手-Web.exe --no-browser --no-sniffer --host 127.0.0.1 --port <空闲端口>`。
3. 最多等待 60 秒，每次检查进程是否提前退出。stdout/stderr 写入独立日志。
4. 验证首页和首页引用的本地 JS/CSS/图像均可读取。响应必须是目标文件内容，不能仅以 HTTP 200 判断成功。
5. 请求情报 manifest、武将/战法列表、研究目录和模拟引擎元数据；检查 JSON 结构、`ok` 标记和必要数据存在。
6. 通过 `/api/simulate` 发起一次固定输入的真实对局。要求随包 Java 引擎返回成功和完整的必要结果字段；禁止 mock 引擎，也不能仅检查 JAR 存在。
7. 检查首次运行创建的数据库能打开、必要表存在，且运行数据没有写入 `_internal`。停止并重启后确认数据仍可读取。
8. 注入缺文件故障：从包副本中移除情报 manifest，再运行同一验收程序，必须返回失败。证明验收确实能拦住本次问题。
9. 无论成功失败，终止测试进程树，上传应用日志、接口验收报告和失败步骤；失败不得进入发布 job。

基本应用验收关闭真实抓包，不连接游戏服务器、不调用真实账号认证。Npcap 驱动安装、网卡权限及实际流量需要在安装了 Npcap 的 Windows 环境单独验证；报告明确记录是否完成。

### 4. 发布已验收的原包

- 发布 job 依赖构建和验收均成功，使用已经验收的同一个 ZIP，不重新编译。
- Artifacts 文件名包含版本与短 commit SHA，附带 SHA-256 和验收报告，避免下载错版本。
- 仅显式开启发布时创建 GitHub Release。写权限只授予发布 job；构建和验收使用只读仓库权限。
- 日志总是上传，正式下载包只在验收成功后标记为可交付。

## 文件修改范围

| 文件 | 负责的变化 |
| --- | --- |
| `.github/workflows/build-windows-web.yml` | 构建、独立验收、发布依赖及日志上传 |
| `packaging/scripts/build_web_exe.ps1` | 资源校验、干净构建、运行时收集、退出码与 ZIP |
| `packaging/pyinstaller/stzb-web.spec` | onedir 与明确的运行资源清单 |
| `packaging/pyinstaller/requirements-build.txt` 及 Windows 锁文件 | 固定构建依赖 |
| 新增 `packaging/scripts/smoke_windows.py` | 进程生命周期、接口/对局/缺文件验收与报告 |
| `api_server.py` | 已确认的资源路径及冻结环境脚本执行边界 |
| `battle_engine_adapter.py` | 打包态调用随包 Windows Java 与引擎 |
| `run_web_exe.py` | 服务就绪后打开页面，启动失败可诊断 |
| 对应 `test/` 文件 | 故障回归及正常启动验证 |
| `README.md`、Windows 包内说明 | 下载、完整解压、日志位置与 Npcap 使用条件 |

不修改 Android 模块、游戏协议算法、战斗规则或用户已有未提交工作。若打包验证发现这些范围内的其他问题，报告证据并单独确定修复范围。

## 完成标准

- 原先的资源路径问题先有失败证据，再通过回归验证。
- Windows 候选 ZIP 在独立 job 中通过真实 exe 启动、资源接口、真实 Java 对局和重启验证。
- 移除必需配置后，验收稳定失败，CI 阻断发布。
- 用户下载的是验收过的同一份文件，可以追溯 commit 和哈希。
- 报告明确区分：本地测试、Windows exe 验收、Npcap/实际抓包验证。任何未执行的项均不得标记通过。

## 官方参考

- [PyInstaller 目录包与单文件运行机制](https://pyinstaller.org/en/stable/operating-mode.html)
- [Scapy Windows 安装与 Npcap 依赖](https://scapy.readthedocs.io/en/stable/installation.html)
- [GitHub Actions 构建产物](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts)
