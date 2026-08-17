# 模块化沉浸战场 HUD 全系统升级设计

日期：2026-08-15

## 1. 目标

将当前 Web Dashboard 升级为已批准的 **B：模块化沉浸 HUD**：

- 保留已接受的深蓝 / 青蓝 Modern Dark Data Console 基础；
- 保留 `208px` 桌面固定侧栏与现有 11 个可见页面顺序；
- 统一全局壳层、面板、数据表、工具栏、弹窗、状态与动效；
- 将 11 个页面归入五个视觉域，统一但不千篇一律；
- 强化地图、画像卡、任务执行、数据分析和系统健康的沉浸感；
- 常驻动效克制，强动效只由真实事件或用户操作触发；
- 不修改业务计算、API 语义、数据库口径和隐藏兼容页面行为。

本次改造范围为当前可见导航：

```text
玩家队伍
自定义积分
打城考勤
同盟成员队伍
武将阵容
团数据
战斗模拟
州郡分布
设置中心
战场情报
阵容战法研究
```

隐藏兼容页继续保留功能，不投入同等级视觉重构。

## 2. 信息架构

### 2.1 视觉域

#### 情报域

```text
战场情报
州郡分布
```

强调色：青蓝。

核心组件：

- 战术地图；
- 全局雷达；
- 风险与新鲜度；
- 行军与部队；
- 州郡分布；
- 事件时间线。

#### 作战域

```text
战斗模拟
打城考勤
```

强调色：攻守红蓝 + 危险红。

核心组件：

- 武将画像卡；
- 攻守阵容；
- 任务阶段；
- 执行状态；
- 出勤进度；
- 战斗回放。

#### 组织域

```text
玩家队伍
同盟成员队伍
团数据
```

强调色：绿色。

核心组件：

- 玩家身份；
- 队伍组成；
- 同盟 / 分组；
- 活跃度；
- 团队关系；
- 阵容覆盖。

#### 分析域

```text
自定义积分
武将阵容
阵容战法研究
```

强调色：紫青。

核心组件：

- KPI；
- 证据等级；
- 排名；
- 公式 / 规则；
- 阵容统计；
- 配置事实与模拟证据。

#### 系统域

```text
设置中心
```

强调色：金色。

核心组件：

- 链路健康；
- 刷新策略；
- 密度与动效；
- API Token；
- 本地偏好；
- 版本信息。

### 2.2 导航

保持现有 11 个按钮的扁平顺序，不增加“更多”菜单，不隐藏现有入口。

桌面侧栏：

- 固定宽度 `208px`；
- 每个导航项显示领域图形、名称和数字编号；
- 当前项使用领域强调色、左侧光条和轻微内发光；
- 领域标题只作为视觉分组，不可点击；
- 保持现有 `data-tab-index` 和 `switchTab()` 兼容。

移动端：

- 保持现有抽屉导航；
- 领域标题与按钮顺序保持一致；
- 不使用横向顶栏塞入 11 个入口。

## 3. 全局壳层

### 3.1 顶部状态栏

顶部状态栏固定显示：

```text
系统名称
当前账号
SSE 状态
WorldState 版本 / 新鲜度
战报数量
当前时间
命令面板快捷键
```

状态显示：

```text
LIVE：绿色
DEGRADED：黄色
OFFLINE：红色
IDLE：灰蓝
```

顶部背景使用半透明深蓝和 `backdrop-filter`。滚动时不改变高度，不增加大型
标题动画。

### 3.2 环境背景

全局背景由以下层组成：

1. 深蓝渐变；
2. 低透明度网格；
3. 当前领域的环境光；
4. 页面进入时的一次性淡入；
5. 不使用持续粒子系统。

领域环境光通过 `body[data-visual-domain]` 切换：

```text
intelligence
operations
organization
analysis
system
```

### 3.3 页面头部

11 个页面统一使用：

```text
领域 kicker
页面标题
一句用途说明
主要操作
次要操作
数据新鲜度 / 引擎 / 规则版本
```

页面头部不再由各模块随意拼装。

## 4. 设计 Token

`static/dashboard-design-system.css` 是唯一全局 token 来源。

新增或收敛：

```css
--domain-intelligence
--domain-operations
--domain-organization
--domain-analysis
--domain-system

--surface-glass
--surface-elevated
--border-glow
--shadow-hud
--shadow-float

--motion-fast: 160ms
--motion-standard: 240ms
--motion-slow: 360ms
```

兼容旧变量的 alias 保留，禁止在业务 CSS 新增第二套 `:root`。

领域 token 通过页面属性设置：

```html
<div
  class="page hud-page"
  id="tab33"
  data-visual-domain="intelligence"
>
```

## 5. 共享组件

### 5.1 HUD 页面头部

类名：

```text
hud-page-head
hud-page-kicker
hud-page-title
hud-page-summary
hud-page-actions
hud-page-meta
```

### 5.2 HUD 面板

类名：

```text
hud-panel
hud-panel-head
hud-panel-title
hud-panel-meta
hud-panel-body
hud-panel-footer
```

支持：

```text
default
glass
elevated
interactive
warning
danger
success
```

### 5.3 KPI

类名：

```text
hud-kpi-grid
hud-kpi
hud-kpi-label
hud-kpi-value
hud-kpi-trend
hud-kpi-spark
```

规则：

- 左侧领域色光条；
- 数值使用等宽字体；
- 趋势颜色不只依赖红绿，还显示箭头或文本；
- 可选 sparkline 不引入新图表依赖；
- skeleton 与真实内容尺寸一致。

### 5.4 工具栏

类名：

```text
hud-toolbar
hud-toolbar-group
hud-filter
hud-segmented
hud-status-chip
```

筛选区必须能在 `900px` 以下换行，不制造横向页面滚动。

### 5.5 数据表

现有表格统一升级：

- sticky 表头；
- 当前行 hover 聚焦；
- 选中行使用领域色；
- 数值列统一等宽和右对齐；
- 状态使用 chip；
- 空数据、加载、错误分别展示；
- 长表提供顶部阴影提示和底部渐隐；
- 不修改表格字段与排序逻辑。

类名：

```text
hud-table
hud-table-shell
hud-table-scroll
hud-row-selected
hud-empty
hud-error
hud-loading
```

### 5.6 弹窗与侧滑层

统一：

- 深蓝玻璃背景；
- 领域色边框；
- 头部 kicker；
- 明确关闭按钮；
- 背景模糊；
- Esc 关闭；
- 打开后焦点进入；
- 关闭后焦点返回触发元素。

命令面板、积分弹窗、模拟器侧滑库和战报详情使用同一视觉基础。

## 6. 动效系统

### 6.1 动效等级

用户设置增加：

```text
完整
标准
精简
```

映射：

#### 完整

- 环境光；
- 卡片 hover；
- 页面进入；
- 事件脉冲；
- 数值变化；
- 一次性扫描光。

#### 标准

- 页面进入；
- hover；
- 状态变化；
- 无持续扫描。

#### 精简

- 仅 opacity 和颜色；
- 无位移；
- 无缩放；
- 无扫描；
- 无脉冲。

`prefers-reduced-motion: reduce` 始终强制精简。

### 6.2 常驻动效

只允许：

- SSE LIVE 状态点缓慢呼吸；
- 活跃加载 skeleton；
- 必要的进度过渡。

禁止：

- 全屏持续扫描；
- 持续粒子；
- 大面积背景动画；
- 多个模块同时闪烁。

### 6.3 事件动效

真实事件触发一次：

```text
新战报：时间线新增 + 蓝色微光
高风险：红色边框脉冲一次
模拟完成：结果卡扫描一次
积分重算完成：排名变化高亮
WorldState 更新：版本 chip 闪烁一次
任务执行：阶段节点推进
```

事件动效不得改变业务状态。

## 7. 页面级设计

### 7.1 战场情报

- 作为最高完成度示范页；
- 地图画布使用 HUD 边框与角标；
- 雷达使用紫色分析光；
- 风险使用红色；
- 新鲜度使用绿色 / 黄色；
- 详情栏使用可折叠 sections；
- 时间线与地图联动；
- 保留现有全域 / 中距 / 战术镜头语义。

### 7.2 州郡分布

- 保留地图和统计；
- 州卡使用领域色分层；
- Top 图表改为统一 HUD bar；
- 图例、着色维度和数据新鲜度进入页面工具栏。

### 7.3 战斗模拟

- 保留已完成的 A 方案武将画像卡；
- 对阵台纳入作战域红蓝语言；
- 结果和 Server 回放使用统一 HUD 面板；
- 与全局动效等级联动；
- 不修改 Kotlin 引擎和回放数据。

### 7.4 打城考勤

- 任务从普通表格提升为“行动任务”；
- 展示准备、集结、执行、完成阶段；
- 成员到位率使用进度环或进度条；
- 城池目标、时间和风险进入任务头部；
- 详情 / 战报仍使用原接口。

### 7.5 玩家队伍 / 同盟成员队伍 / 团数据

- 三页使用组织域共享队伍卡、成员 chip、阵容缩略图；
- 同名字段视觉一致；
- 表格筛选统一；
- 玩家身份与同盟 / 分组关系更清晰；
- 不复制战斗模拟器的大图画像卡。

### 7.6 自定义积分

- 保留现有规则、预览和确认流程；
- 升级为分析域；
- 当前规则版本和完整性更突出；
- 排名变化使用一次性动画；
- 公式、证据和缺失源状态统一。

### 7.7 武将阵容

- 排行从普通表格升级为阵容卡 + 数据表组合；
- 前三名使用金 / 银 / 铜，不使用表情符号；
- 样本量、胜率和置信度层级清晰；
- 可跳转研究与模拟器。

### 7.8 阵容战法研究

- 配置事实、历史证据、模拟证据使用三种证据标签；
- 列表和详情统一分析域；
- 武将使用本地画像缩略图；
- 卡包、武将、战法和阵容保持同一详情骨架；
- 不在产品界面显示协议实现细节。

### 7.9 设置中心

- 显示当前主题、密度、动效等级；
- 显示后端、SSE、Writer、Kotlin 引擎、画像 manifest 健康状态；
- API Token 仍只保存在浏览器会话；
- 重置设置必须二次确认。

## 8. HTML / CSS 收敛

当前 `dashboard.html` 仍有大量内联样式，本次迁移目标：

- 11 个可见页面新增或改造部分不得新增内联 `style`；
- 将共享视觉移入 `dashboard-design-system.css`；
- 领域特有视觉留在对应业务 CSS；
- 已迁移页面逐步删除旧内联样式；
- 不要求一次性删除隐藏兼容页全部旧样式；
- 不通过 `!important` 叠加更多覆盖层解决问题。

允许为图表数值传递 CSS 自定义属性：

```html
<div class="hud-bar" style="--hud-value:72%">
```

但不允许在 `style` 中写主题色、布局或字体。

## 9. JavaScript 架构

新增：

```text
static/dashboard-hud.mjs
```

职责：

- 页面领域映射；
- 主题 / 动效等级；
- 事件脉冲；
- KPI 数值变化；
- shared empty / loading / error states；
- focus-visible 和 dialog 辅助；
- 不请求业务 API；
- 不维护业务状态。

接口：

```javascript
HudSystem.init()
HudSystem.setDomain(tabId)
HudSystem.setMotionLevel(level)
HudSystem.pulse(element, kind)
HudSystem.animateValue(element, from, to, options)
HudSystem.renderState(container, state)
```

现有业务脚本继续负责数据与交互：

```text
app1.js
app2.js
intelligence-center.js
score-center.js
simulator-workbench.js
intelligence-research.js
```

## 10. 响应式

### Desktop ≥ 1180px

- `208px` 固定侧栏；
- sticky top status bar；
- 主内容填满剩余宽度；
- 情报地图保持地图 + 详情双栏；
- KPI 4–5 列。

### Tablet 760–1179px

- 侧栏收窄到图标模式；
- 页面头部允许换行；
- KPI 2–3 列；
- 地图详情可切换；
- 表格横向滚动限制在组件内部。

### Mobile < 760px

- 抽屉导航；
- 顶部状态缩减为连接、账号和命令按钮；
- 页面头部纵向；
- KPI 2 列或单列；
- 地图、回放和大型表格采用组件内滚动；
- 不允许整个文档横向滚动。

## 11. 可访问性

- 页面只保留一个顶层 `main`；
- 活跃导航设置 `aria-current="page"`；
- tabs 使用 `role=tablist/tab/tabpanel`；
- modal / drawer 使用原生 `dialog`；
- 状态不能只靠颜色；
- 所有 hover 操作有键盘等价操作；
- focus ring 使用领域色；
- reduced-motion 强制精简；
- 对比度满足深色背景文本要求。

## 12. 性能

性能预算：

```text
不新增大型前端框架
不新增持续 requestAnimationFrame 循环
初始页面不加载非当前页大图
地图 Canvas 保持现有绘制策略
画像继续 lazy / eager 分级
事件动效必须可回收
```

新增 CSS / JS 总量目标：

```text
dashboard-hud.mjs < 25KB 未压缩
全局新增 CSS < 35KB 未压缩
```

## 13. 迁移阶段

### Phase 1：HUD 基础设施

- token；
- 顶部状态栏；
- 208px 侧栏；
- 页面领域映射；
- HUD 页面头部；
- 面板、KPI、工具栏、表格；
- 动效等级；
- reduced-motion；
- 设置中心健康状态；
- 战场情报作为示范页。

### Phase 2：作战域

- 战斗模拟；
- 打城考勤；
- 任务阶段与完成动效；
- 回放视觉统一。

### Phase 3：组织域

- 玩家队伍；
- 同盟成员队伍；
- 团数据；
- 共享阵容 / 成员组件。

### Phase 4：分析域

- 自定义积分；
- 武将阵容；
- 阵容战法研究；
- 规则、证据与排名统一。

### Phase 5：收敛与性能

- 删除 11 页已迁移区域的内联样式；
- 去掉冲突覆盖；
- Chrome E2E；
- 移动端；
- 性能与 reduced-motion；
- 文档。

## 14. 错误与降级

- HUD 模块加载失败：业务页面仍可使用现有 HTML / JS；
- 浏览器不支持 `backdrop-filter`：使用不透明 surface；
- `color-mix` 不支持：使用 token fallback；
- 动效异常：不影响数据渲染；
- 设置损坏：恢复默认标准动效；
- 健康接口失败：设置中心显示未知，不伪装正常；
- 当前页 API 失败：使用统一 HUD error state；
- 隐藏兼容页不依赖 HUD 才能运行。

## 15. 测试

### 静态契约

- 11 页都有 `data-visual-domain`；
- 共享 HUD 类存在；
- 已迁移区域不新增主题内联样式；
- 页面只有一个主 `main`；
- 新模块按 mtime 版本化；
- 侧栏顺序不变。

### Node

- tab → domain 映射；
- motion level；
- pulse 只执行一次；
- value animation 可禁用；
- loading / empty / error state；
- reduced-motion；
- 不发业务请求。

### Python

- 首页资源版本；
- 设置健康接口；
- 可选组件缺失的稳定降级；
- 当前 API 契约不变。

### Chrome E2E

- 11 个可见导航顺序；
- 每页领域属性与主题颜色；
- 页面头部 / KPI / 面板；
- 设置动效等级并持久化；
- 模拟器画像和回放；
- 情报地图与雷达；
- 积分弹窗；
- 研究跳转；
- 桌面、平板、移动端无文档横向滚动；
- reduced-motion 模式无扫描、位移和脉冲；
- 页面无 JS error 和 500。

## 16. 非目标

- 不重构隐藏兼容页视觉；
- 不改业务 API；
- 不修改数据库；
- 不引入 React / Vue；
- 不加入持续粒子背景；
- 不加入全局音效；
- 不改变 11 个导航入口及顺序；
- 不删除旧页面；
- 不执行 Git commit。

## 17. 完成标准

1. 11 个可见页面具有统一 HUD 壳层和领域视觉；
2. 侧栏仍为 `208px`，顺序不变；
3. 战场情报达到示范页完成度；
4. 模拟器画像卡与系统风格一致；
5. KPI、面板、表格、工具栏、弹窗统一；
6. 强动效只由事件或交互触发一次；
7. reduced-motion 与精简设置有效；
8. 11 页迁移区域不新增主题内联样式；
9. 业务接口和兼容入口不变；
10. Node、Python、Chrome、移动端和全量回归通过。
