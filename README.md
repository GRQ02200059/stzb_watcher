# stzb_watcher

> 《率土之滨》战场实时监控与数据分析平台

游戏战报采集系统，配合 Web 仪表盘实现团队战斗数据的实时展示、多维分析与考勤管理。

---

## Features

| 模块 | 说明 |
|---|---|
| 实时战报流 | 抓包解析战报，XOR/zlib 双协议支持，自动入库 |
| 全部战报 | 可搜索/筛选的完整战报列表，支持战报详情弹窗 |
| 团数据 | 按分组或成员统计战报、胜率、功勋（来源：盟数据）、攻城场次 |
| 战场分析 | 活跃度热力图、战力段位分布、对阵联盟统计、最活跃玩家 |
| 武将阵容 | 三人组合出战胜率排行，基于战报实时计算 |
| 分组武勋 | 按分组汇总 wuxun，支持按时间段筛选 |
| 同盟成员 | 同步盟内成员数据（功勋、贡献、势力值等） |
| 攻城考勤 | 智能分配攻城人员，智能进行攻城场次出勤统计，支持导出 CSV |
| 战场态势 | 分时段战斗热力图与战力分布 |
| 战场消息 | 实时战场公告与聊天记录 |
| 城池地图 | 格子占领态势可视化 |

---

## Tech Stack

- **Backend**: Python 3 · Flask · SQLite
- **Packet Capture**: Scapy（XOR 解码 + zlib 解压）
- **Frontend**: Vanilla JS · CSS Variables（无框架依赖）
- **Data Sync**: 定时轮询 `/api/refresh`

---

## Quick Start

### 1. 安装依赖

```bash
pip install flask scapy
```

### 2. 启动 API 服务

```bash
python api_server.py
```

服务默认监听 `0.0.0.0:8080`，浏览器打开 `http://localhost:8080` 即可访问仪表盘。

### 3. 启动抓包采集

```bash
python start_pipeline.py
```

抓包数据自动写入 `capture_new/` 目录并触发入库流程。

---

## Project Structure

```
stzb_watcher/
├── api_server.py          # Flask API 服务 + 静态文件托管
├── start_pipeline.py      # 抓包 → 解析 → 入库 主流程
├── db_build.py            # 数据库初始化 / 迁移
├── db_import.py           # 战报批量入库
├── static/
│   ├── dashboard.html     # 单页仪表盘
│   ├── app1.js            # 核心交互逻辑（tab 切换、战报流）
│   ├── app2.js            # 各功能模块（团数据、武将阵容等）
│   ├── hero_data.js       # 武将基础数据
│   └── herocfg.js         # 武将配置（图标、国家、类型）
├── capture_new/           # 原始 JSON 文件
├── logs/                  # 运行日志
└── requirements.txt
```

---

## API Overview

| Endpoint | 说明 |
|---|---|
| `GET /api/status` | 服务状态与统计概览 |
| `GET /api/battles` | 战报列表（分页 + 筛选） |
| `GET /api/battles/:id` | 单条战报详情 |
| `GET /api/team_report` | 团数据（`dim=group\|player`，`period=today\|week\|all`） |
| `GET /api/battle_analysis` | 战场分析汇总 |
| `GET /api/heroes/combo_winrate` | 武将三人组合胜率 |
| `GET /api/team_users` | 同盟成员列表 |
| `GET /api/attendance` | 攻城考勤 |
| `GET /api/ranking_v2` | 排行榜（`dim=player\|union\|zone`） |
| `POST /api/refresh` | 触发数据入库 |

---

## Notes

- 功勋数据以盟数据（`team_users.wuxun`）为准，战报内 `atk_gongxun` 仅供参考
- 数据库文件为 `stzb_<server_ip>.db`，SQLite 格式，可直接用 DB Browser 查看
- 本项目仅用于个人学习与团队内部数据管理，请遵守相关法律法规及游戏服务条款

---

## License

MIT
