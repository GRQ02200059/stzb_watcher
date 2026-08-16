# README 截图上传说明

这里是仓库首页的产品截图目录。Web 端七张截图已经采集并启用；Android 端仍保留
三个固定位置，请将准备好的 WebP 文件直接上传到本目录。

## 固定文件名

| 文件名 | README 展示位置 | 建议画面 |
|---|---|---|
| `overview-intelligence.webp` | Web 全景主图 | 已启用 |
| `gallery-live-army.webp` | Web 功能画廊 | 已启用 |
| `gallery-simulator.webp` | Web 功能画廊 | 已启用 |
| `gallery-research.webp` | Web 功能画廊 | 已启用 |
| `gallery-score.webp` | Web 功能画廊 | 已启用 |
| `gallery-attendance.webp` | Web 功能画廊 | 已启用 |
| `gallery-player-teams.webp` | Web 功能画廊 | 已启用 |
| `android-battlefield.webp` | Android 三图画廊 | 待上传 |
| `android-teams.webp` | Android 三图画廊 | 待上传 |
| `android-simulator.webp` | Android 三图画廊 | 待上传 |

## Android 启用步骤

1. 保持三个 Android 文件名不变，将图片上传到 `docs/assets/screenshots/`。
2. 打开仓库根目录的 `README.md`，找到对应的“截图待上传”占位卡。
3. 删除占位卡，并取消紧邻 `<img>` 标签外层的 HTML 注释。
4. 在 GitHub 预览中确认三列 Android 画廊显示正常。

示例：

```html
<!--
<img src="docs/assets/screenshots/android-battlefield.webp"
     alt="Android 战场总览"
     width="100%">
-->
```

上传图片后改为：

```html
<img src="docs/assets/screenshots/android-battlefield.webp"
     alt="Android 战场总览"
     width="100%">
```

截图中如包含角色名、区服、服务器地址或其他账号信息，请在上传前自行确认是否需要
遮挡。README 不依赖外部图床，也不要求本机绝对路径。
