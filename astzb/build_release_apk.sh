#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
APKSIGNER="$HOME/Library/Android/sdk/build-tools/35.0.1/apksigner"

cd "$ROOT_DIR"

if [[ ! -f "$ROOT_DIR/keystore.properties" ]]; then
  echo "缺少 keystore.properties，无法生成正式签名 APK"
  exit 1
fi

echo "==> 构建 release APK"
./gradlew clean :app:assembleRelease

if [[ ! -f "$APK_PATH" ]]; then
  echo "未找到输出 APK：$APK_PATH"
  exit 1
fi

echo "==> 签名验证"
"$APKSIGNER" verify --verbose --print-certs "$APK_PATH"

echo "==> 构建完成"
echo "APK 路径：$APK_PATH"
