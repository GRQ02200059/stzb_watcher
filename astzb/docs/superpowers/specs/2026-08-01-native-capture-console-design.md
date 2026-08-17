# 原生抓包启动台设计

## 目标

将旧 `MainActivity` 中真正与抓包有关的能力迁移到 Compose 客户端，使“更多”中的“抓包启动台”直接打开原生页面，不再依赖旧页面完成日常操作。

迁移范围：

- 展示抓包运行状态、目标 App、本机 SOCKS 地址、已解析包数量。
- 搜索并选择已安装 App。
- 请求系统 VPN 授权并启动现有 `TProxyService` + `LocalSocksCaptureServer` 链路。
- 停止抓包。
- 展示 STZB 解析日志，并按一个或多个协议 ID 筛选。
- 清空日志、解析包和战场监控内存状态。
- 导出解析包、SQLite 数据库和迁移诊断，并由用户通过系统文件选择器选择位置和文件名。
- 保留旧控制台兼容入口，但不再作为“更多”的主抓包入口。

旧侧栏和已经迁移至主导航的数据报表不属于本次范围。

## 架构

新增独立抓包功能模块，分为三层：

1. `CaptureConsoleController` 封装 Android 与旧抓包实现的交互，包括读取状态、列出 App、启动、停止、清空及生成导出内容。Controller 不持有界面对象，也不直接发起 Activity Result。
2. `CaptureConsoleViewModel` 管理页面状态、目标 App、协议筛选、日志订阅和一次性操作结果。状态更新通过 `StateFlow` 暴露。
3. `CaptureConsoleScreen` 只渲染状态并发送用户意图。VPN 授权和 `CreateDocument` 启动器留在 Compose 宿主层，结果再交回 ViewModel/Controller。

现有 `TProxyService`、`LocalSocksCaptureServer`、`Preferences`、`PacketLogStore`、`LocalStzbPacketStore` 和数据库实现继续作为底层事实来源，不复制抓包或协议解析逻辑。

## 页面与交互

“更多工具”将“经典抓包控制台”改为“抓包启动台”，进入新的二级路由 `capture-console`。页面顶部提供返回“更多”的按钮。

页面按以下顺序组织：

1. 状态卡：运行中/未启动、目标 App、SOCKS 端口、解析包数量和刷新按钮。
2. 控制卡：目标 App 输入框、搜索选择按钮、启动按钮、停止按钮。
3. 日志卡：协议 ID 筛选输入框、筛选后的 STZB 解析日志、清空按钮。
4. 导出卡：解析包、SQLite 数据库、迁移诊断三个导出按钮，以及“打开旧控制台”的兼容入口。

启动流程：用户点击启动；若 VPN 已授权则直接启动，未授权则打开系统授权页；授权成功后才配置 per-App tunnel 并启动。未选择目标 App 时禁止启动，避免意外建立非预期的全局链路。

停止流程：发送 `TProxyService.ACTION_DISCONNECT`、停止旧 `CaptureVpnService`（兼容历史状态）并停止 `LocalSocksCaptureServer`，随后刷新状态。

## 状态与错误处理

页面状态包含：

- `loading`、`running`、`nativeReady`。
- 目标包名与已选 App 名称。
- SOCKS host/port、解析包数量。
- 原始日志、协议筛选、筛选后日志。
- 操作中状态及最近一次提示/错误。

native 库不可用、目标 App 不存在、VPN 授权被取消、服务启动异常或导出失败时，页面展示明确错误且保持可重试。日志为空时展示等待说明。日志筛选仅匹配完整数字协议 ID，支持逗号、空格、斜杠和换行分隔。

导出先在 Controller 中生成临时文件或字节内容，再通过 `CreateDocument` 写入用户选择的 URI；取消选择不会生成错误，也不申请广泛存储权限。

## 测试与验收

- Controller/ViewModel 单元测试覆盖初始状态、目标 App 选择、协议筛选、启动前置条件、停止、清空和导出结果。
- Compose 测试覆盖页面标题、状态、控制按钮、日志筛选、三个导出入口、返回和旧控制台兼容入口。
- 导航测试验证“更多 → 抓包启动台 → 返回更多”。
- 完整构建后覆盖安装，不卸载用户数据。
- 真机验收目标 App 选择、VPN 授权、启动、运行状态、停止，以及三个系统导出选择器；验收期间不落盘不需要的导出文件。

## 兼容性与安全

- 不修改现有协议解析和数据库结构。
- 不清理用户已有抓包文件或数据库。
- “清空”只在用户明确点击后执行，并在界面说明清空的是内存日志/状态。
- 根目录 `README.md` 和 `astzb/third_party` 不纳入提交。
