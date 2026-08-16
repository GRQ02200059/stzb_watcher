# 战斗模拟器武将画像卡视觉升级设计

日期：2026-08-15

## 1. 目标

在现有战斗模拟工作台中落地已确认的 **A：全息战场立绘** 方向：

- 武将卡以真实画像为视觉主体；
- 保持当前深蓝 / 青蓝 Modern Dark Data Console 体系；
- 增加阵营光效、玻璃信息舱、扫描光和轻微景深；
- 保持等级、进阶、士气与两个额外战法槽清晰可操作；
- 画像加载失败时使用精致将魂占位，不出现破图；
- 默认不依赖外网，外网只作为可选后备。

本次只升级战斗模拟器的武将卡与武将库缩略图，不修改战斗计算、回放语义或
其他 Dashboard 页面。

## 2. 视觉方向

每张卡使用三层结构：

```text
画像层
├── 全幅武将画像
├── 阵营色环境光
├── 边缘暗角
└── 悬浮轻微放大与景深

HUD 层
├── 站位标签
├── 阵营 / 兵种 / 品质标签
├── HERO ID
└── 武将名称

玻璃信息舱
├── 等级
├── 进阶
├── 当前队伍士气
├── 两个额外战法槽
└── 替换 / 移除操作
```

### 2.1 阵营色

```text
蜀：绿色
魏：蓝色
吴：红色
汉：金色
群：紫色
晋：青色
未知：灰蓝
```

阵营色通过 CSS 自定义属性注入单张卡：

```html
<article
  class="sim-hero-card"
  style="--sim-camp-accent:#4a8fe0;--sim-camp-glow:#4a8fe055"
>
```

不增加新的全局 `:root`。

### 2.2 动效

- 图片悬浮放大不超过 `1.04`；
- 扫描光仅在 hover / focus-within 时运行一次；
- 卡片向上移动不超过 `3px`；
- 支持 `prefers-reduced-motion`，关闭位移、缩放和扫描动画；
- 不使用持续高频动画。

## 3. 画像资产方案

### 3.1 来源

本地源目录：

```text
/Users/bytedance/stzb/work/emulator-backups/
Pixel_6-before-12G-20260814-223729/Documents/
mini_client_res/card/card_big/
```

文件命名：

```text
big_card_<hero_or_icon_id>.jpg
```

当前源目录约 907 个画像文件、305MB。文件虽然使用 `.jpg` 扩展名，但不是可直接
交给浏览器或 `cwebp` 的普通 JPEG，而是客户端封装的循环 XOR 数据。

已通过马超原图与已知解码图逐字节比对确认：

```text
XOR key (hex): 8e 50 9f e8 59 67 91 fb
```

解码：

```python
decoded = bytes(
    value ^ XOR_KEY[index % len(XOR_KEY)]
    for index, value in enumerate(encoded)
)
```

解码后必须以 JPEG SOI `ff d8` 开头。客户端部分文件缺少 JPEG EOI
`ff d9`，同步器在缺失时补齐一次，再交给 `cwebp`；补齐后仍不能解析的文件进入
manifest `errors`，对应武将使用 CDN / 占位降级。

### 3.2 映射

`hero_table.csv` 提供：

```text
heroid
icon_hero_id
```

映射优先级：

1. `icon_hero_id > 0` 且存在对应画像；
2. 使用 `heroid` 对应画像；
3. 本地画像不存在时，使用 CDN 后备 URL；
4. CDN 也失败时显示将魂占位。

源配置中约 1,174 个已发布武将可通过 `icon_hero_id / heroid` 命中本地画像。

### 3.3 同步器

新增：

```text
scripts/sync_hero_portraits.py
```

接口：

```text
python scripts/sync_hero_portraits.py \
  --source-root <card_big> \
  --hero-table battle-engine/src/main/resources/battle-config/hero_table.csv \
  --target-root static/hero-portraits

python scripts/sync_hero_portraits.py ... --check
```

同步器职责：

1. 读取发布武将和 `icon_hero_id` 映射；
2. 只处理实际被已发布武将引用的唯一画像；
3. 使用固定 8 字节循环 XOR 解码客户端画像；
4. 校验 SOI，并在缺失时补 JPEG EOI；
5. 用 `cwebp` 校验并生成 WebP；
6. 仍不能解析的单图进入 `errors`；
7. 限制最长边，控制页面加载体积；
8. 生成 manifest；
9. `--check` 检测源图、映射、输出文件和 checksum 漂移；
10. 不复制原始 305MB JPG 目录。

目标目录：

```text
static/hero-portraits/
├── manifest.json
├── placeholder.svg
└── cards/
    ├── 100013.webp
    ├── 100016.webp
    └── ...
```

### 3.4 转换参数

默认建议：

```text
最长边：720px
WebP quality：78
metadata：none
```

质量目标：

- 卡片尺寸下无明显马赛克；
- 单图典型体积 40–120KB；
- 所有画像总量应显著低于原始 305MB；
- 同一 `icon_hero_id` 只保存一份文件。

## 4. 后端数据契约

扩展 `/api/simulate/heroes` 的每个武将对象：

```json
{
  "id": 100027,
  "name": "张辽",
  "camp": 2,
  "army": 3,
  "quality": 4,
  "iconId": 100027,
  "portraitUrl": "/static/hero-portraits/cards/100027.webp",
  "portraitFallbackUrl": "https://g0.gph.netease.com/.../card_medium_100027.jpg?gameid=g10",
  "portraitLocal": true
}
```

规则：

- `portraitUrl` 始终是项目内路径；
- 本地文件不存在时指向 `placeholder.svg`；
- `portraitFallbackUrl` 只供前端在本地占位前尝试一次；
- `portraitLocal` 表示本地是否存在真实画像；
- 不读取用户目录运行时文件，所有 Web 资产必须在同步阶段进入项目目录。

## 5. 前端行为

### 5.1 对阵卡

`heroCardMarkup()` 增加：

```html
<div class="sim-hero-portrait">
  <img
    src="/static/hero-portraits/cards/100027.webp"
    data-fallback-src="https://..."
    alt="张辽武将画像"
    loading="eager"
    decoding="async"
  >
  <div class="sim-hero-scan"></div>
</div>
```

首屏六张卡使用 `loading="eager"`。

图片错误处理：

```text
本地 WebP 失败
→ 若尚未尝试 CDN，则切 CDN
→ CDN 失败
→ 切 placeholder.svg
→ 标记 data-portrait-state="placeholder"
```

必须防止 `onerror` 无限循环。

### 5.2 武将库

武将库条目使用小尺寸画像缩略图：

- 优先使用同一 `portraitUrl`；
- `loading="lazy"`；
- 图片右侧显示名称、ID、阵营、兵种、品质；
- 图片失败使用同一占位机制；
- 搜索和滚动性能不应因 1,400 个 `<img>` 同时加载而下降；
- 仍只渲染过滤后的前 160 条。

### 5.3 占位图

`placeholder.svg` 使用：

- 深蓝背景；
- 阵营色边缘光通过 CSS 外层提供；
- 大号武将末字；
- “PORTRAIT OFFLINE” 小字；
- 不显示破图图标。

因 SVG 文件不能为每个武将动态写名字，末字继续由现有
`.sim-hero-visual::after` 显示。

## 6. 兼容性

必须保留：

```text
window.StzbSimulator.loadLineup()
window.StzbSimulator.getState()
window.StzbSimulator.run()
```

画像字段只属于英雄目录元数据，不进入阵容模板和模拟请求，避免模板被资源路径污染。

现有模板 schema 保持 `schemaVersion=1`。

## 7. 错误处理

- 源画像目录不存在：同步器明确失败；
- `cwebp` 不存在：同步器给出安装提示，不静默退回原 JPG；
- 单个源图片损坏：记录到 manifest 的 `errors`，其武将使用占位；
- manifest 不存在：API 仍返回英雄列表，全部使用占位和 CDN 后备；
- 前端本地和 CDN 均失败：显示占位，不弹 Toast；
- 画像错误不影响阵容编辑和模拟运行。

## 8. 测试

### Python

- `icon_hero_id` 优先于 `heroid`；
- XOR 解码与已知马超 JPEG 样本逐字节一致；
- 缺失 JPEG EOI 时补齐且不重复追加；
- 无效 XOR/JPEG 数据进入 manifest `errors`；
- 相同 icon 只生成一个 WebP；
- manifest 记录源 SHA-256、输出 SHA-256 和映射；
- `--check` 检测输出被修改；
- API 返回本地画像、后备 URL和占位状态；
- 源目录缺失和 `cwebp` 缺失明确失败。

### Node / 静态契约

- 卡片渲染 `<img>`、alt、fallback；
- 图片错误最多尝试本地、CDN、占位三步；
- 武将库图片使用 lazy loading；
- 画像字段不进入模板序列化；
- `prefers-reduced-motion` 关闭扫描与缩放；
- CSS 中存在画像、扫描、玻璃信息舱选择器。

### Chrome E2E

- 默认六张武将卡均显示真实画像；
- 换将后画像同步变化；
- 强制图片失败后显示占位且页面无 JS error；
- 卡片 hover 不遮挡等级、战法和操作按钮；
- 移动端画像卡仍可横向浏览和编辑。

## 9. 非目标

- 不同步动态画像、视频或 Spine 动画；
- 不加入画像选择器；
- 不迁移全部 305MB 原始 JPG；
- 不修改战斗引擎；
- 不把外网 CDN 作为唯一图片来源；
- 不在其他 Dashboard 页面全面替换头像。

## 10. 完成标准

1. 默认六名武将显示真实画像；本地可转换的使用 WebP，损坏源图允许使用 CDN；
2. 卡片视觉与 A 方案一致；
3. 阵营色、玻璃面板、扫描光和 hover 景深可见；
4. 图片失败时无破图；
5. 武将库缩略图可用；
6. 图片不影响模拟器兼容接口、模板或战斗请求；
7. `--check`、Python、Node、Chrome E2E 全部通过。
