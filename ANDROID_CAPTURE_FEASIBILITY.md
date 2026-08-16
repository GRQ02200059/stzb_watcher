# Android 抓包方案可行性验证

## 目标

验证以下方案是否适合当前项目：

- Android `VpnService`
- 现成 `tun2socks` 内核
- Kotlin 原生 App
- 免 Root
- 尽量少写底层网络栈代码

验证问题不是“理论上能不能抓到流量”，而是：

> **它能不能抓到足够支撑当前 `stzb_watcher` 解析链路的数据。**

---

## 结论先行

## 可以实现的部分

基于现有公开资料，这条路线**可以实现一个免 Root 的 Android 抓流量方案**，而且工程复杂度明显低于“手写 VPN 网络栈”。

可确认的事实：

- `VpnService + tun2socks` 是 Android 上成熟存在的组合
- 现成实现会把 TUN 里的设备流量转发到一个代理出口
- 可以做按应用选择流量接管
- 可以只抓目标 App
- 可以在一个 APK 内完成，不依赖用户额外安装 Clash

参考：

- `cordova-plugin-tun2socks-udp-associate` 明确说明它“启动系统级 VPN，并将设备所有流量通过 VPN TUN 接口转发到 SOCKS server”，且目标是 Android 5.0+；同时提到基于 `VPNService.Builder` 的按应用绕过能力 [jsDelivr 文档](https://www.jsdelivr.com/package/npm/cordova-plugin-tun2socks-udp-associate)
- `xjasonlyu/tun2socks` 明确说明自己是把“所有应用的流量透明转发到代理”的通用组件，并支持 `HTTP/SOCKS4/SOCKS5/Shadowsocks` 等代理后端 [pkg.go.dev 文档](https://pkg.go.dev/github.com/xjasonlyu/tun2socks/v2)

## 不能直接下结论的部分

目前**不能直接确认**的是：

> 这条路线是否能稳定拿到和你现在 `scapy_v2.py` 同等级、可直接复用现有协议解析器的原始字节流。

也就是说：

- 能做“流量接管”这件事，不等于已经证明
- 能做“和当前 PC 版一样的战报协议解析”

这一步必须靠最小 PoC 验证。

---

## 对当前项目意味着什么

你现在的桌面版链路依赖：

- 按端口拿到目标 TCP 流量
- 做流重组
- 按自定义包头解析
- 处理 `明文 / zlib / XOR`
- 从字节流中恢复消息 ID 与 JSON 载荷

而 `VpnService + tun2socks` 的天然定位是：

- 把设备流量引进你的 App
- 再交给代理后端转发

所以它天然更擅长：

- 接管连接
- 获取目标地址 / 端口 / 时序 / 流量统计
- 在合适的实现里获取进入 TUN 的原始 IP 包

它**不天然保证**：

- 你在 Java/Kotlin 层可以直接像 `scapy` 一样拿到现成 TCP payload
- 你不用处理流重组就能直接套用当前解析器

因此对当前项目，真正要验证的是：

1. 能否拿到目标游戏流量
2. 拿到的是哪一层数据
3. 该层数据是否足够还原你当前的协议解析链

---

## 最小验证结论

## 我给出的判断

### 结论 A：方案本身可落地

**能做。**

如果目标是：

- 免 Root
- 一个 APK 内完成
- 只抓指定 App
- 快速做出第一个可运行版本

那么 `VpnService + tun2socks` 是当前最合理的起点。

### 结论 B：对你这个项目“有希望”，但还没被证明

**有希望，但必须做 PoC。**

原因是你不是普通的“抓 HTTP/HTTPS 元数据”需求，而是要继续跑：

- 自定义 TCP 流量
- 自定义包头
- XOR / zlib 解码
- 战报协议解析

这要求你最终能接近“原始 payload”级别的数据。

### 结论 C：先不要碰 MITM

**第一阶段不需要先搞 HTTPS 解密。**

因为你当前项目本来就不是按浏览器抓明文 HTTP 页面在工作，而是在解析游戏自己的协议流量。

最先要验证的不是“能否看 HTTPS 明文”，而是：

- 能否在 Android 侧拿到与你 PC 端相似的协议字节流

---

## 最小 PoC 的验收标准

只要下面 4 项通过，就说明这条路线值得继续投：

### P0-1：能成功建立 VPN

验证点：

- `VpnService.prepare()` 正常
- 用户授权后能建立 TUN
- 目标 App 流量被导入 VPN

通过标准：

- 启动服务后，目标 App 仍能联网
- 非目标 App 可按设计走直连或绕过

### P0-2：能只抓目标游戏

验证点：

- 使用 `addAllowedApplication()` 或等效选择逻辑
- 仅接管目标包名

通过标准：

- 只有目标游戏流量进入采集链

### P0-3：能拿到原始 IP 包或可恢复 payload 的数据

验证点：

- 在 TUN 入口或 native 层拿到原始字节
- 能区分 TCP/UDP
- 能记录五元组和包长度

通过标准：

- 至少能导出十几条连续的目标流量片段
- 可和 PC 端抓到的同一时段流量做结构比对

### P0-4：能复现至少一种已知消息

验证点：

- 用 Android 侧数据跑一版最小解析器
- 至少识别一个已知消息类型

建议优先目标：

- 已知明文包
- 或已知 `zlib` 包
- 再做 `XOR`

通过标准：

- 在 Android 侧成功恢复出与 PC 端一致的一条消息

---

## 最小 PoC 不做什么

第一阶段明确不做：

- 不做完整仪表盘 UI
- 不做全量页面迁移
- 不做 Room 全量建模
- 不做 HTTPS MITM
- 不做完整战报链路

只做：

- 建 VPN
- 接目标 App
- 导出原始数据
- 跑一条最小解析链

---

## 推荐 PoC 结构

```text
android-poc/
├── app/
│   ├── vpn/
│   │   ├── CaptureVpnService.kt
│   │   └── VpnController.kt
│   ├── tunnel/
│   │   ├── Tun2SocksBridge.kt
│   │   └── NativeBridge.kt
│   ├── capture/
│   │   ├── PacketSink.kt
│   │   ├── SessionTracker.kt
│   │   └── CaptureLogger.kt
│   ├── parser/
│   │   ├── PacketHeaderParser.kt
│   │   ├── ZlibDecoder.kt
│   │   └── XorDecoder.kt
│   └── ui/
│       └── VerifyScreen.kt
```

PoC 页面只需要 5 个按钮：

1. 请求 VPN 权限
2. 启动抓取
3. 停止抓取
4. 导出最近 N 条流量
5. 运行最小解析测试

---

## 风险判断

## 低风险

- Android 上建立 `VpnService`
- 限定只抓某个 App
- 接现成 tun2socks 内核

## 中风险

- Java/Kotlin 层如何拿到最适合你当前协议解析的数据入口
- native 层与 Kotlin 层的桥接方式

## 高风险

- 游戏实际流量在 Android 上是否仍然能以你当前解析方式还原
- 协议层是否存在平台差异、TLS 包装、或额外混淆

---

## 最终判断

对“先做简单验证”这个目标，我的结论是：

> **值得做。并且应该立刻用最小 PoC 验证。**

但这里的“验证成功”定义要非常准确：

- 不是“能连上 VPN”
- 不是“能看到 443 端口”
- 而是“能拿到足以复用现有解析链的数据”

只要这一点成立，这条路线就可以继续扩成安卓版。

如果这一点不成立，就应该及时止损，改成：

- Root 方案
- 或 PC 抓包 + Android 展示的混合方案

---

## 下一步建议

下一步最合适的是直接启动一个 **Android 最小 PoC 设计稿**，只做验证工程：

1. `VpnService`
2. `tun2socks bridge`
3. `PacketSink`
4. `最小协议解析器`
5. `验证页面`

目标不是产品化，而是用最少代码回答一个问题：

> **Android 免 Root 路线，能不能继续复用当前项目的战报解析能力。**
