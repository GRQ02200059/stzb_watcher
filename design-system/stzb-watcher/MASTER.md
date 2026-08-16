# Design System — STZB Watcher (率土战场指挥台)

> **LOGIC:** 构建某个具体页面(Tab)前，先看 `design-system/stzb-watcher/pages/<page>.md`。
> 若存在，则其规则 **覆盖** 本 Master；否则严格遵循本文件。

---

**Project:** STZB Watcher · 率土战场指挥台
**Generated:** 2026-08-11
**Category:** Real-Time Monitoring / Data-Dense Command Dashboard (dark)
**Source:** ui-ux-pro-max skill — Style「Real-Time Monitoring」+ Palette「Financial Dashboard (dark)」，保留项目战术金品牌色。
**Stack:** 原生 HTML + Vanilla JS 单页(无构建)，Flask 静态托管；主文件 `static/dashboard.html`。

## Design Dials
- **Variance 4/10** — Balanced / Modern（数据台不玩花活，克制的不对称）
- **Motion 5/10** — Standard（状态脉冲/流入 stagger，300–450ms；数据表禁止 overshoot）
- **Density 8/10** — Dense / Dashboard（紧凑间距，最大化数据可见性）

---

## Global Rules

### Color Palette (dark, WCAG AA)

以 Financial Dashboard 深色骨架为底，**战术金 `--gold` 保留为品牌强调**（承接率土身份），状态色遵循监控惯例。

| Role | Hex | CSS Variable | 用途 |
|------|-----|--------------|------|
| Background (base) | `#020617` | `--bg` | 最底层画布 |
| Surface / Panel | `#0b1019` | `--panel` | 一级面板 |
| Surface raised | `#0E1223` | `--panel2` | 卡片/凸起面 |
| Surface hover | `#141b2b` | `--panel3` | 行/卡片 hover |
| Border | `#233043` | `--border` | 分隔线/描边 |
| Border strong | `#334155` | `--border2` | 强分隔/聚焦框基 |
| Text primary | `#e6ebf2` | `--text` | 正文主色 |
| Text muted | `#94A3B8` | `--text2` | 次要说明 |
| Text faint | `#5b6b7d` | `--muted` | 占位/禁用 |
| **Brand gold** | `#c8a044` | `--gold` | 品牌强调/标题/主按钮 |
| Brand gold hi | `#e8c86a` | `--gold2` | 金色高亮/hover |
| Accent green (live/positive) | `#22C55E` | `--green` | 在线/胜/正向 |
| Warning amber | `#f0a935` | `--amber` | 警告/队列中 |
| Critical red | `#EF4444` | `--red` | 严重/失败/负向 |
| Info blue | `#4a8fe0` | `--blue` | 信息/次序列 |
| Cyan | `#3ab8c8` | `--cyan` | 数据序列/链接 |
| Purple | `#9060d0` | `--purple` | 数据序列/特殊态 |
| On-gold / On-accent | `#0b0f18` | `--on-accent` | 强调底上的文字 |

**Semantic status tokens（禁止组件里裸写 hex）：**
- `--status-live: var(--green)` · `--status-warn: var(--amber)` · `--status-crit: var(--red)` · `--status-idle: var(--text2)`

**Color Notes:** 深底 + 金色品牌 + 绿正向/红负向/琥珀警告。多序列图表按 gold→cyan→blue→purple→green 取色，且**必须**辅以线型/图形区分（不能只靠颜色）。

### Typography

- **中文正文:** `'SimSun','宋体','STSong',serif`（`--font-body`）— 保留率土气质。
- **数据 / 数字 / 代码:** `'Share Tech Mono', monospace`（`--font-mono`）— 所有数字、ID、时间戳、KPI 数值一律等宽字体，保证对齐。
- **Base size:** 15px；正文 line-height 1.5；表格/密集区可降到 13–14px 但不低于 12px。
- **CSS Import:**
```css
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');
```

### Spacing (density 8 — dense)

| Token | Value | Usage |
|-------|-------|-------|
| `--sp-1` | `4px` | 紧贴间隙 |
| `--sp-2` | `8px` | 图标/内联间距 |
| `--sp-3` | `12px` | 卡片内边距(密) |
| `--sp-4` | `16px` | 标准内边距 |
| `--sp-5` | `20px` | 区块间距 |
| `--sp-6` | `24px` | 区块外边距 |
| `--sp-8` | `32px` | 大分区 |

### Radius / Shadow / Motion

| Token | Value |
|-------|-------|
| `--r-sm` | `4px` |
| `--r-md` | `8px` |
| `--r-lg` | `12px` |
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,.4)` |
| `--shadow-md` | `0 4px 14px rgba(0,0,0,.5)` |
| `--shadow-lg` | `0 12px 32px rgba(0,0,0,.6)` |
| `--glow-gold` | `0 0 18px #c8a04440` |
| `--glow-green` | `0 0 10px #22C55E80` |
| `--ease` | `cubic-bezier(.22,.61,.36,1)` |
| `--dur` | `200ms`（交互）/ `380ms`（进场） |
| `--pulse` | `pulse 2s infinite`（live 指示灯） |

---

## Component Specs（全站公共类，贯穿所有 Tab）

### KPI 卡 `.stat-card` / `.cards-row`
- 网格 `.cards-row{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:var(--sp-3)}`。
- 卡片：`--panel2` 底、`--border` 描边、`--r-md`、左侧 3px 状态色条（`.stat-card.is-live` 绿 / `.is-warn` 琥珀 / `.is-crit` 红）。
- 数值用 `--font-mono`、`--gold2`、≥1.6rem；标签 `--text2`、≤.72rem 大写字距。
- hover：`box-shadow:var(--shadow-md)`，**不位移**（数据台禁止 scale 抖动）。

### 数据表 `.tbl-wrap > table`
- 容器 `overflow:auto`，sticky 表头 `thead th{position:sticky;top:0;background:var(--panel);z-index:2}`。
- 斑马纹 `tbody tr:nth-child(even){background:#0c1220}`；行 hover `background:var(--panel3)`（150ms）。
- 单元格 `padding:8px 12px`；数字列 `text-align:right;font-family:var(--font-mono)`。
- 边框仅横向 `border-bottom:1px solid var(--border)`，避免网格噪声。

### 按钮 `.btn`
```css
.btn{font-family:var(--font-mono);padding:8px 16px;border-radius:var(--r-sm);
  border:1px solid var(--border2);background:var(--panel2);color:var(--text);
  cursor:pointer;transition:all var(--dur) var(--ease);min-height:34px;}
.btn:hover{border-color:var(--gold);color:var(--gold2);box-shadow:var(--glow-gold);}
.btn:focus-visible{outline:2px solid var(--gold);outline-offset:2px;}
.btn-primary{background:var(--gold);border-color:var(--gold);color:var(--on-accent);font-weight:600;}
.btn-primary:hover{background:var(--gold2);border-color:var(--gold2);}
.btn-ghost{background:transparent;border-color:var(--border);}
```
- 触控目标 ≥ 34px 高（移动端 44×44）。

### 输入 / 筛选 `.filters .input,.select`
```css
.input,.select{background:var(--panel);border:1px solid var(--border2);color:var(--text);
  border-radius:var(--r-sm);padding:8px 12px;font-size:14px;transition:border-color var(--dur);}
.input:focus,.select:focus{border-color:var(--gold);outline:none;box-shadow:0 0 0 3px #c8a04430;}
```

### 状态标签 `.pill`
```css
.pill{display:inline-flex;align-items:center;gap:6px;font-family:var(--font-mono);
  font-size:.72rem;padding:2px 10px;border-radius:999px;border:1px solid var(--border);}
.pill.live{color:var(--green);border-color:#22C55E55;}
.pill.live::before{content:"";width:7px;height:7px;border-radius:50%;background:var(--green);
  box-shadow:var(--glow-green);animation:var(--pulse);}
.pill.warn{color:var(--amber);border-color:#f0a93555;}
.pill.crit{color:var(--red);border-color:#EF444455;}
```

### 实时流 `.feed`
- 竖向时间线；每条 `border-left:2px solid <status>`，时间戳 `--font-mono --text2`。
- 新条目进场 `stagger`（见 Motion）；`prefers-reduced-motion` 下取消动画。

### 进度/占比条 `.bar-row`
- 轨 `--panel3`、填充按数值取状态色、`transition:width .5s var(--ease)`；数值文本始终可见（不只靠色）。

### 子导航 `.subtabs`（Tab 内二级切换，如世界场景）
- 胶囊式；激活项底部 2px `--gold` 下划线 + `--gold2` 文字。

---

## Style Guidelines

**Style:** Real-Time Monitoring（BI/Analytics，Dark ✓ Full，WCAG AA）
**Keywords:** live data updates, status indicators, alert pulse/glow, streaming charts, connection status, auto-refresh。
**Key Effects:** 状态灯脉冲、数据流平滑更新、进场 stagger、hover 行高亮、连接状态指示。
**图表:** 时序→Line/Streaming Area(Canvas，缓冲 60–300s)；异常→Line with Highlights(形状标记 not color)；KPI vs 目标→Bullet/Gauge(数值始终可见)。多序列用线型区分。

### Pattern — Real-Time / Operations
- 顶部：产品名 + 连接状态 + 关键指标；主体：数据密集网格；实时区常驻。
- CTA/操作放导航区与指标区之后。

---

## Motion

**Stagger List**（进场，Standard）— load/scroll 触发，300–450ms：
```js
// 仅装饰性容器用；数据表禁用 overshoot
gsap.from('.stat-card', { opacity:0, y:16, duration:.4,
  stagger:{ each:.06, from:'start' }, ease:'power2.out' });
```
- ✅ live 指示灯 `pulse 2s infinite`；数据流入用 opacity+translateY。
- ❌ 数据表/密集信息区禁用 `back.out` 回弹；禁止 animate width/height（用 transform）。
- 必须尊重 `@media (prefers-reduced-motion: reduce)`：冻结动画、去脉冲。

---

## Anti-Patterns (Do NOT Use)
- ❌ **Emoji 当图标** — 导航/状态用内联 SVG（Lucide/Heroicons 风格 stroke 图标）。当前 nav 的 emoji 逐步替换为 SVG。
- ❌ 组件里裸写 hex — 一律用 token / semantic 变量。
- ❌ 布局位移 hover（scale 抖动）、瞬时状态切换（0ms）。
- ❌ 只靠颜色传达状态（图表/标签需形状或文字）。
- ❌ 低对比灰底灰字（正文 ≥ 4.5:1）、正文 < 12px。
- ❌ 移动端横向滚动、固定 px 容器宽度、禁用缩放。
- ❌ 无 focus 环、hover-only 交互。

---

## Pre-Delivery Checklist
- [ ] 无 emoji 图标（改 SVG，统一图标集）
- [ ] 所有可点元素 `cursor:pointer`
- [ ] hover 过渡 150–300ms，无布局位移
- [ ] 文本对比度 ≥ 4.5:1（深底浅字已满足）
- [ ] `:focus-visible` 焦点环可见
- [ ] `prefers-reduced-motion` 已处理
- [ ] 响应式：375 / 768 / 1024 / 1440
- [ ] 内容不被固定 header/nav 遮挡
- [ ] 移动端无横向滚动
- [ ] 数字/时间戳等宽字体对齐
- [ ] 图表状态不只靠颜色（含线型/形状/文字）
