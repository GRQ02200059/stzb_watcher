# APK 稳定出包指南

本文档基于当前 `astzb` 工程的实际配置整理，目标是稳定生成一个可以在目标设备上正常安装的 APK。

## 现状说明

- 当前已支持 `debug` 与 `release` 构建。
- `release` 现在支持读取根目录下的 `keystore.properties` 做正式签名。
- 当前 `release` APK 默认输出路径：
  - `app/build/outputs/apk/release/app-release.apk`（已签名时）
  - `app/build/outputs/apk/release/app-release-unsigned.apk`（未配置签名时）
- 当前原生 ABI 包含：
  - `armeabi-v7a`
  - `arm64-v8a`

## 一、准备签名证书

建议使用独立的发布证书，不要使用默认 debug 证书。

在项目根目录执行：

```bash
keytool -genkeypair \
  -v \
  -keystore release-keystore.jks \
  -alias stzb_release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650
```

生成后，在项目根目录创建 `keystore.properties`：

```properties
storeFile=release-keystore.jks
storePassword=你的store密码
keyAlias=stzb_release
keyPassword=你的key密码
```

说明：

- `storeFile` 支持相对路径，以上写法表示证书文件放在项目根目录。
- `keystore.properties` 和 `*.jks` 已加入 `.gitignore`，不要提交到仓库。

## 二、构建正式 APK

先清理，再出正式包：

```bash
./gradlew clean :app:assembleRelease
```

如果签名配置正确，生成物通常是：

```bash
app/build/outputs/apk/release/app-release.apk
```

如果看到的是：

```bash
app/build/outputs/apk/release/app-release-unsigned.apk
```

说明当前没有读取到有效的发布签名配置，不能直接用于稳定分发安装。

## 三、验证 APK 是否可安装

### 1. 检查签名

```bash
./gradlew -q :app:signingReport
```

需要确认：

- `release` 变体不再是 `Config: null`
- 能看到 `Store`、`Alias`、`SHA1`、`SHA-256`

### 2. 检查 APK 内容

列出 APK 输出：

```bash
find app/build/outputs/apk -type f | sort
```

建议再用 Android SDK build-tools 里的 `apksigner` 验证：

```bash
$ANDROID_HOME/build-tools/35.0.1/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

如果输出中至少存在一项有效的正式签名方案（如 `v2` 或 `v3`），说明签名结构正常。

### 3. 在设备上安装验证

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

常用参数：

- `-r`：覆盖安装
- `-d`：允许降级安装（仅测试时使用）
- `-t`：允许安装测试包

## 四、安装失败的常见问题排查

### 1. `INSTALL_PARSE_FAILED_NO_CERTIFICATES`

原因：

- APK 未签名
- 签名流程异常

处理：

- 确认生成的是 `app-release.apk`，不是 `app-release-unsigned.apk`
- 运行 `apksigner verify`

### 2. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

原因：

- 设备上已安装同包名应用，但签名证书不同

处理：

- 卸载旧包再装：

```bash
adb uninstall com.example.myapplication
```

- 或保持使用同一套正式证书持续发布

### 3. `INSTALL_FAILED_VERSION_DOWNGRADE`

原因：

- 当前安装包 `versionCode` 小于设备上已安装版本

处理：

- 递增 `versionCode`
- 测试阶段可临时用：

```bash
adb install -r -d app/build/outputs/apk/release/app-release.apk
```

### 4. `INSTALL_FAILED_NO_MATCHING_ABIS`

原因：

- 设备 CPU 架构不在 APK 包含的 ABI 中

当前工程仅包含：

- `armeabi-v7a`
- `arm64-v8a`

处理：

- 面向 x86 模拟器时，补充 x86_64 支持，或改用 ARM 模拟器
- 真机分发优先使用 ARM 设备验证

### 5. 设备系统版本不满足

当前工程配置：

- `minSdk = 33`

这意味着 Android 13 以下设备无法安装，这不是打包错误，而是版本门槛。

处理：

- 若目标是更广覆盖，需要专项评估并下调 `minSdk`
- 若只面向 Android 13+ 设备，保持不变即可

## 五、推荐的稳定出包流程

每次正式出包，按下面顺序执行：

1. 检查 `versionCode` 是否递增
2. 检查 `versionName` 是否与发布版本一致
3. 确认证书和 `keystore.properties` 可用
4. 执行 `./gradlew clean :app:assembleRelease`
5. 执行 `apksigner verify`
6. 在至少 2 台真机上执行覆盖安装和冷启动验证
7. 记录本次 APK 的 SHA-256 与版本号

## 六、建议覆盖的安装稳定性测试

至少覆盖以下组合：

- Android 13 真机，`arm64-v8a`
- Android 14 真机，`arm64-v8a`
- Android 15 真机，`arm64-v8a`
- 32 位 ARM 设备或兼容环境，`armeabi-v7a`

每台设备建议执行：

1. 首次安装
2. 覆盖安装升级
3. 卸载后重装
4. 冷启动
5. VPN 授权与抓包主流程启动
6. 前后台切换后再次启动

## 七、持续稳定产出的最佳实践

- 永远使用同一套正式签名证书
- 每次发包递增 `versionCode`
- 不要把测试机上残留的旧签名包与正式包混装
- 将 `assembleRelease + apksigner verify + adb install` 固化成脚本或 CI
- 每次升级 NDK、AGP、compileSdk 后都做一次全量安装回归
- 发布前固定验证 ABI、签名、系统版本门槛三件事

## 八、当前工程特别需要注意的点

- 当前 `release` 签名配置原先为空，现已支持 `keystore.properties`
- 当前 `applicationId` 已为 `com.netease.stzb.netease`
- 当前已提供一键出包脚本：

```bash
./build_release_apk.sh
```
- 当前 `minSdk = 33`
  - 若用户设备低于 Android 13，会直接无法安装
- 当前 release 已能成功构建，但未配置证书时输出的是 unsigned APK，不能作为最终稳定安装包
