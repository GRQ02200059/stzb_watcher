# astzb Android 本机抓包与数据迁移工程

这个目录是 `stzb_watcher` 的 Android 抓包 PoC。核心目标是让 App **不依赖 PC**，直接在安卓本机完成 STZB 数据包抓取和解析：

```text
目标 App -> VpnService -> hev-socks5-tunnel -> App 内 SOCKS5 捕获器 -> STZB TCP payload -> 本机包头解析
```

## 当前能力

- 本机战场数据客户端
  - 使用 `DashboardActivity`
  - 数据源已经切到本机 SQLite，不依赖 PC Flask
  - 支持查看：
    - 本机数据概览
    - `10` / `92` 完整战报列表
    - `2100` 战报通知列表兜底
    - 战报筛选：玩家、同盟、战斗类型、结果
    - 战报筛选：时间范围、地块 wid
    - `5028` 行军监控
    - 本机排行统计
    - `700` 同盟/个人势力排行
    - `103` 同盟成员
    - `5026` 地图格子
    - 最近 STZB 原始包
    - 通用业务记录
  - 点击战报通知会进入 `BattleDetailActivity` 查看本机详情
- 本机 SQLite 数据层
  - 使用 `LocalStzbDatabase`
  - 已建表：
    - `stzb_packets`：所有识别到的 STZB 原始应用层包
    - `battle_notices`：`2100` 战报通知
    - `chat_messages`：`2100` 聊天消息
    - `battle_monitor_moves`：`5028` 行军队伍
    - `battles_v2`：`10` / `92` 完整战报核心字段
    - `battle_heroes`：完整战报攻守方武将
    - `wuxun_log` / `power_log` / `attendance`：战报衍生统计
    - `team_users`：`103` 同盟成员
    - `map_cells`：`5026` 地图格子
    - `union_list` / `player_power_rank`：`700` 排行
    - `local_records`：其他消息类型的通用业务记录
- 本机导出
  - 首页 `导出抓包`：导出最近原始 IP 包
  - 首页 `导出本机 STZB 解析包`：导出最近识别的应用层 STZB 包
  - 首页 `导出本机数据库`：导出 `stzb_local.db`
  - 首页 `导出真机诊断样本`：导出计数、消息号分布、最近包预览和 raw hex 前缀
- 本机同盟成员
  - 从 `103` 拆专表 `team_users`
  - 支持基础成员列表和分组统计
  - 在本机战场数据页点击 `同盟成员`
- 本机地图格子
  - 从 `5026` 拆专表 `map_cells`
  - 支持基础格子列表、命名城池数量和类型分布统计
  - 在本机战场数据页点击 `地图格子`
- 本机排行统计
  - 使用 `LocalStzbRepository.loadBattleRankings`
  - 从本机 `battles_v2` 聚合：
    - 玩家武勋排行
    - 同盟武勋排行
    - 玩家最高势力排行
  - 在本机战场数据页点击 `排行统计`
- 武将名称解析
  - 使用 `HeroNameResolver`
  - APK assets 已内置 `herocfg.json`
  - 完整战报和 2100 通知中的武将 ID 会优先显示真实武将名，找不到时回退 `武将ID`
- 本机 STZB 抓包桥接
  - 使用 `LocalSocksCaptureServer`
  - `hev-socks5-tunnel` 的 SOCKS5 上游固定指向 `127.0.0.1:10808`
  - App 内 SOCKS5 捕获器负责转发真实连接，同时截取 client -> server 的 TCP payload
  - `StzbStreamParser` 已迁移 PC 端核心包头识别：
    - 游戏端口：`8001`
    - 包长：`buf[0:4]`
    - 消息 ID：`buf[4:8]`
    - 数据类型：`buf[12]`
    - `2=plain`
    - `3=zlib`
    - `5=xor`
  - 可在主页面点击 `查看本机解析 STZB 包`
  - 识别到的包会写入 App 私有目录：
    - `files/capture_new/<msg_id>/cap_<timestamp>_<msg_id>_<decode>.(txt|json)`
  - 可在主页面点击 `导出本机 STZB 解析包`
- 本机 5028 行军监控解析
  - 使用 `LocalBattleMonitorParser`
  - 已迁移 PC 端 `parse_battle_monitor_13a4` 的轻量核心逻辑
  - 可从 `5028` 中提取：
    - 队伍 ID
    - 玩家/同盟
    - 起点/终点坐标
    - 当前坐标
    - 出发/到达时间
    - 地图状态数量
  - 点击 `查看本机解析 STZB 包` 时会优先展示 5028 行军摘要
- 本机 2100 战报/聊天解析
  - 使用 `LocalBattleNoticeParser`
  - 可解析：
    - `data[1] == 9`：聊天消息
    - `data[1] == 0/1`：战报通知
  - 战报通知会进入本机战报列表
- 本机 10/92 完整战报解析
  - 使用 `LocalFullBattleParser`
  - `10`：个人完整战报列表
  - `92`：同盟完整战报列表
  - 已展开入：
    - `battles_v2`
    - `battle_heroes`
    - `wuxun_log`
    - `power_log`
    - `attendance`
  - 本机战报页会优先显示完整战报，若为空再回退到 `2100` 战报通知
- 通用业务消息迁移
  - 使用 `LocalAuxiliaryParser`
  - 当前已覆盖并入库摘要：
    - `103`：同盟成员
    - `510`：玩家统计
    - `5026`：地图格子
    - `6314`：攻城战场动态
    - `6318`：攻城队列
    - `301`：玩家行军
    - `700`：联盟/个人势力排行
    - `780`：公告
    - `671`：武将解锁
    - `21`：玩家自身信息
    - `6243`：战区玩家
    - `90005`：db_sync
    - `10` / `92`：完整战报原始记录兜底
- 原始抓包验证模式
  - 使用 `CaptureVpnService`
  - 可选择已安装 App
  - 可读取 TUN 原始 IPv4 包
  - 可查看 hex 预览并导出最近包
- 开源桥接模式
  - 已拉取 `sockstun` 和 `hev-socks5-tunnel`
  - 已接入 `hev.sockstun.TProxyService`
  - 已配置 `VpnService + SOCKS5` 参数写入
  - 已可通过 NDK 编译 `libhev-socks5-tunnel.so`

## 开源项目位置

```text
third_party/sockstun
third_party/hev-socks5-tunnel
```

`sockstun` 是 Android VPN 集成参考，底层依赖 `hev-socks5-tunnel`。

## NDK 要求

当前工程优先使用：

```text
26.3.11579264
```

安装完成后应存在：

```bash
~/Library/Android/sdk/ndk/26.3.11579264/source.properties
~/Library/Android/sdk/ndk/26.3.11579264/ndk-build
```

## 环境检查

```bash
cd /Users/bytedance/stzb_watcher/astzb
bash check_android_env.sh
```

返回含义：

- `OK`：SDK/NDK 已就绪，可以编 native
- `WARN`：NDK 还没装完整，普通 App 可编，但开源桥接 native 不可用
- `FAIL`：SDK、platform 或 build-tools 缺失

## 编译

```bash
cd /Users/bytedance/stzb_watcher/astzb
./gradlew :app:assembleDebug
```

如果 NDK 尚未安装完整，Gradle 会跳过 `sockstun` native build，保证 PoC App 仍可构建。

如果 NDK 已安装完整，Gradle 会启用：

```text
third_party/sockstun/app/src/main/jni/Android.mk
```

并尝试产出：

```text
libhev-socks5-tunnel.so
```

当前已验证产出：

```text
app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/arm64-v8a/libhev-socks5-tunnel.so
app/build/intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib/armeabi-v7a/libhev-socks5-tunnel.so
```

## 手机上怎么验证

1. 打开 App
2. 点击 `选择已安装 App`
3. 选择 STZB 包名
4. 点击 `启动本机 STZB 抓包桥接`
5. 打开 STZB 做联网操作
6. 回到本 App 查看日志，或点击 `查看本机解析 STZB 包`
   - 如果抓到了 5028，会看到 `5028行军摘要`
   - 示例：`行军#1 team=... 玩家 123,456 -> 124,456 arrive=...`
7. 点击 `打开本机战场数据`
8. 查看本机概览、完整战报、战报通知兜底、排行统计、同盟成员、地图格子、行军监控、最近包和更多业务记录
9. 点击 `导出本机 STZB 解析包` 查看当前识别结果导出路径
10. 原始 TUN 验证仍可用：点击 `授权并启动`

## 当前注意点

- 本机 STZB 抓包模式会自动启动 App 内 SOCKS5 捕获器，不需要 PC 代理。
- 当前已完成本机识别、轻量落盘、SQLite 入库、本机数据页，以及多类消息的摘要迁移。
- `10` / `92` 完整战报已经展开到本机战报专表；目前先迁核心字段，PC 端超长扩展字段仍保留在 `raw_json` 中，后续可继续补列。
- `10` / `92` 已补充部分 PC 端扩展字段，包括队伍 ID、进阶、武将类型、装备、技能、支援和地块扩展标记。
- 武将名配置来自 `app/src/main/assets/herocfg.json`，后续如 PC 端武将库更新，需要同步这个资源文件。
- HTTPS 明文不会因为 VPN/tun2socks 自动解密。
