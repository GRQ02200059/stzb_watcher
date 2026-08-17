#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
NDK_VERSION="26.3.11579264"
NDK_DIR="$SDK_DIR/ndk/$NDK_VERSION"

echo "== astzb Android environment check =="
echo "Project: $PROJECT_DIR"
echo "SDK:     $SDK_DIR"
echo "NDK:     $NDK_DIR"
echo

if [[ ! -d "$SDK_DIR" ]]; then
  echo "FAIL: Android SDK directory does not exist."
  exit 1
fi

if [[ ! -f "$SDK_DIR/platforms/android-35/source.properties" ]]; then
  echo "FAIL: Android platform android-35 is missing."
  exit 1
fi

if [[ ! -f "$SDK_DIR/build-tools/35.0.1/source.properties" ]]; then
  echo "FAIL: Android build-tools 35.0.1 is missing."
  exit 1
fi

if [[ ! -f "$NDK_DIR/source.properties" ]]; then
  echo "WARN: NDK $NDK_VERSION is not fully installed yet."
  echo "      Expected: $NDK_DIR/source.properties"
  echo "      The app can still build without native tunnel, but open-source bridge cannot."
  exit 2
fi

if [[ ! -x "$NDK_DIR/ndk-build" ]]; then
  echo "FAIL: ndk-build is missing or not executable."
  echo "      Expected: $NDK_DIR/ndk-build"
  exit 1
fi

echo "OK: Android SDK and NDK look ready."
echo "Next: cd \"$PROJECT_DIR\" && ./gradlew :app:assembleDebug"
