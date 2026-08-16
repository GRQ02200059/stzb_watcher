# README 截图上传说明

这里是仓库首页的产品截图目录。当前只预留展示位置，不包含截图资产；请将你准备
好的 WebP 文件直接上传到本目录。

## 固定文件名

| 文件名 | README 展示位置 | 建议画面 |
|---|---|---|
| `overview-intelligence.webp` | Web 全景主图 | 战场情报地图、主要图层与右侧情报区 |
| `gallery-live-army.webp` | Web 功能画廊 | 实时部队三栏指挥台 |
| `gallery-simulator.webp` | Web 功能画廊 | 双方阵容、模拟配置与执行区 |
| `gallery-research.webp` | Web 功能画廊 | 阵容战法研究工作台 |
| `gallery-score.webp` | Web 功能画廊 | 自定义积分榜单、筛选与规则入口 |
| `gallery-attendance.webp` | Web 功能画廊 | 打城考勤任务阶段与成员区 |
| `gallery-player-teams.webp` | Web 功能画廊 | 玩家队伍列表与主要操作区 |
| `android-battlefield.webp` | Android 三图画廊 | 战场动态、筛选与主要状态 |
| `android-teams.webp` | Android 三图画廊 | 队伍或团队列表与统计 |
| `android-simulator.webp` | Android 三图画廊 | 双方阵容、配置入口与执行区 |

## 启用步骤

1. 保持上述文件名不变，将图片上传到 `docs/assets/screenshots/`。
2. 打开仓库根目录的 `README.md`，找到对应的“截图待上传”占位卡。
3. 删除占位卡，并取消紧邻 `<img>` 标签外层的 HTML 注释。
4. 在 GitHub 预览中确认主图、两列 Web 画廊和三列 Android 画廊显示正常。

示例：

```html
<!--
<img src="docs/assets/screenshots/overview-intelligence.webp"
     alt="Web 战场情报全景"
     width="100%">
-->
```

上传图片后改为：

```html
<img src="docs/assets/screenshots/overview-intelligence.webp"
     alt="Web 战场情报全景"
     width="100%">
```

截图中如包含角色名、区服、服务器地址或其他账号信息，请在上传前自行确认是否需要
遮挡。README 不依赖外部图床，也不要求本机绝对路径。
