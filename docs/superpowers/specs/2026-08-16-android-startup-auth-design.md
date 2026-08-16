# Android 启动认证设计

日期：2026-08-16

状态：已确认

## 1. 目标

为 `astzb/app` 安卓客户端接入与 Windows 桌面端相同的公网账号体系。用户必须在每次应用进程冷启动时完成一次 session 验证，验证成功后才能进入现有业务界面、抓包控制和悬浮窗功能。

本次同时删除现有 10 天本地试用限制。软件保持完全免费，登录页必须明确显示：

- `本软件完全免费，禁止任何形式的倒卖、付费代装或捆绑销售。`
- `密码无法找回，请自行妥善保存。`

## 2. 不在范围内

- 不修改现有 Dashboard、Compose 业务页面和底部导航结构。
- 不增加邮箱、验证码、密码找回、恢复码或设备数量限制。
- 不增加运行期间心跳或周期性账号检查。
- 不上传本地抓包、SQLite、战报或游戏数据到认证服务器。
- 不增加管理员网页或 Android 自动更新。
- 首版继续使用用户已接受的 HTTP 明文认证地址。

## 3. 认证契约

认证服务地址：

```text
http://152.136.236.184:9080
```

Android 客户端复用现有稳定接口：

- `POST /v1/register`
- `POST /v1/login`
- `POST /v1/session/verify`
- `POST /v1/logout`

客户端版本取 `BuildConfig.VERSION_NAME`。请求超时为 10 秒。响应必须带 `Cache-Control: no-store`，并严格解析现有 JSON envelope；未知或畸形响应统一视为无效响应，不把响应正文或异常详情显示给用户。

## 4. 总体架构

采用单 Activity Compose 门禁：

```text
StzbAppActivity
  -> AndroidAuthGate
      -> AuthRepository
      -> AuthSessionStore
      -> AuthStartupCoordinator
  -> 验证成功后创建现有 StzbApp
```

`StzbAppActivity` 仍是 Launcher Activity。它不再先执行本地试用检查，而是先渲染认证门禁。只有门禁进入 `Ready` 状态后，才创建现有仓库引用并渲染 `StzbApp`。

认证模块按职责拆分：

1. `AuthRepository`
   - 负责 HTTP 请求、严格 JSON 解析、错误码映射和 10 秒超时。
2. `AuthSessionStore`
   - 负责 session token 的加密保存、读取和删除。
3. `AuthStartupCoordinator`
   - 负责启动时读取 token、验证 session 和决定是否放行业务界面。
4. `AuthViewModel`
   - 管理登录、注册、重试、登出和稳定用户文案。
5. `AuthGateScreen`
   - 只负责 Compose UI，不直接访问网络或存储。

## 5. 启动状态机

状态定义：

- `CheckingSession`：读取本地 token 并验证。
- `LoginRequired`：没有 token 或 token 已失效。
- `SubmittingLogin`：正在登录。
- `SubmittingRegistration`：正在注册。
- `Blocked`：账号被禁用或服务全局停用。
- `Unavailable`：认证服务器不可达或响应无效。
- `Ready`：本次进程启动验证成功。

冷启动流程：

1. 读取加密 session token。
2. 没有 token时进入 `LoginRequired`。
3. 有 token 时调用 `/v1/session/verify`。
4. 验证成功进入 `Ready`。
5. `SESSION_INVALID`：删除 token，进入 `LoginRequired`。
6. `ACCOUNT_DISABLED`：删除 token，进入 `Blocked`。
7. `SERVICE_DISABLED`：保留 token，进入 `Blocked`。
8. 网络不可达、超时或无效响应：保留 token，进入 `Unavailable`。

进程进入 `Ready` 后设置进程内认证标记。该进程存活期间不再访问认证服务，符合“只在程序启动时检测”的要求。

## 6. 登录与注册

登录和注册只使用用户名、密码和客户端版本。

- 用户名在提交前去除首尾空白。
- 密码不写日志、不写文件、不写 SharedPreferences。
- 请求完成后立即清空 ViewModel 和输入框中的密码。
- 登录或注册成功后保存 session token，并直接进入 `Ready`，不重复调用 verify。
- 登录失败使用稳定中文文案，不显示服务端原始正文。
- 注册页提供返回登录入口。

用户错误文案与 Windows 保持一致：

- 用户名或密码格式不正确
- 用户名已被使用
- 用户名或密码错误
- 账号已禁用
- 服务暂不可用
- 登录状态已失效，请重新登录
- 当前客户端版本过低
- 请求过于频繁，请稍后重试
- 认证服务器暂时无法连接

## 7. Session 安全存储

使用 Android Keystore 生成不可导出的 AES-256-GCM 密钥，别名固定为：

```text
STZBWatcher.AuthSession
```

session token 加密后存入应用私有 SharedPreferences。存储内容只包含：

- 格式版本；
- GCM IV；
- 密文与认证标签。

不保存密码。用户名可作为非敏感偏好保存，用于下次填充登录框。

以下情况删除 token：

- session 无效；
- 账号被禁用；
- 用户主动登出；
- 密文损坏、解密失败或存储格式不支持。

全局停服和暂时断网不删除 token。

Android 备份规则必须排除认证 session 文件和 Keystore 相关偏好，避免 token 被云备份迁移到其他设备。

## 8. 防绕过策略

现有应用包含多个 Activity、通知 PendingIntent、VPN 服务和悬浮窗服务。仅保护 Launcher Activity 不足以防止从旧 Activity 或通知直接进入。

新增进程级 `AuthAccessGuard`：

- 只有 `AuthStartupCoordinator` 成功后才能把当前进程标记为已认证。
- `StzbAppActivity` 在 `Ready` 前不创建业务界面。
- `MainActivity`、`DashboardActivity` 和 `BattleDetailActivity` 在 `onCreate` 开头检查 guard。
- guard 未认证时统一跳转 `StzbAppActivity`，清理被绕过的 Activity。
- VPN 和悬浮窗服务启动前检查 guard；未认证时拒绝启动并停止自身。
- 服务通知和其他 PendingIntent 统一打开 `StzbAppActivity`。

该 guard 只存在内存中，因此系统杀死进程后必须重新验证 session。

## 9. 删除本地试用限制

删除或停用：

- `LocalTrialManager`
- `TrialPolicy`
- `ExpiredActivity`
- `activity_expired.xml`
- `TrialPolicyTest`
- 所有 `ensureAccessOrRedirect()` 调用
- Manifest 中的 `ExpiredActivity`

旧试用 SharedPreferences 可保留为无害遗留数据，不做迁移读取，也不再影响访问。新版本升级后直接进入账号认证门禁。

## 10. UI

认证页使用现有 `AstzbTheme` 和 Material 3，不改变业务页视觉设计。

页面包含：

- 应用标题；
- 免费及禁止倒卖声明；
- 用户名输入框；
- 密码输入框；
- 密码无法找回说明；
- 登录按钮；
- 注册模式切换；
- 进度状态；
- 稳定错误信息；
- `Unavailable` 状态下的重试按钮。

密码输入框禁止自动建议明文。提交期间禁用重复操作。系统返回键不能绕过门禁进入业务界面。

## 11. 登出

在现有“更多”页面增加“退出登录”入口：

1. 如果本地有 token，尽力调用 `/v1/logout`。
2. 无论网络调用成功与否，都删除本地 token。
3. 清除进程内认证标记。
4. 停止 VPN 抓包服务和悬浮窗服务。
5. 回到认证页面。

登出失败不得保留本地登录状态。

## 12. 错误与日志

- 不记录用户名、密码、完整 session token、请求正文或响应正文。
- 网络错误只映射为稳定公开错误类型。
- token 只允许在 `AuthRepository` 请求对象和 `AuthSessionStore` 内短暂存在。
- HTTP 明文风险在登录页面或首次提示中明确说明，文案不得暗示链路已加密。
- 认证不可达时必须 fail closed，不能离线绕过。

## 13. 测试

### 13.1 JVM 单元测试

- API 请求路径、JSON 字段和版本号；
- `Cache-Control: no-store` 要求；
- 错误码映射；
- 10 秒超时；
- token 存储 round-trip 和损坏清除；
- 启动状态机各分支；
- 账号禁用与 session 无效删除 token；
- 停服和断网保留 token；
- 登录/注册成功直接进入 Ready；
- 密码在请求结束后清空；
- 登出始终清除 token 和进程 guard；
- guard 防止旧入口绕过。

### 13.2 Android 仪器测试

- 冷启动显示认证页；
- 免费、禁止倒卖和密码不可找回文案可见；
- 登录成功进入现有 `StzbApp`；
- 重启进程后自动 verify；
- 禁用、停服和断网页面；
- 从通知和旧 Activity Intent 启动时不能绕过；
- 登出返回认证页并停止后台服务。

### 13.3 模拟器验收

使用现有 `Pixel_6` AVD：

1. 清除应用数据；
2. 注册测试账号；
3. 登录进入业务页；
4. 强制停止并重启，确认自动验证；
5. 断网重启，确认不能绕过；
6. 恢复网络并重试；
7. 登出并确认 token 已清除；
8. 在获得许可后验证账号禁用和全局停服只影响下次启动。

## 14. 完成定义

- 10 天试用限制不再执行；
- 未登录或启动验证失败时无法进入任何业务 Activity、VPN 或悬浮窗服务；
- Android 与 Windows 使用同一账号和 session 契约；
- 密码不落盘，session token 使用 Android Keystore 加密保存；
- 每个进程只在启动阶段验证一次；
- 登录页展示完全免费、禁止倒卖和密码无法找回说明；
- JVM、仪器测试和 Pixel_6 冷启动验收通过；
- 原有 Compose 业务界面、导航、抓包和本地数据行为保持不变。
