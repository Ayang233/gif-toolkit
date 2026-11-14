#!/usr/bin/env bash
# 把 Miuix 源码克隆到工程的同级目录，并打上 composite build 必需的补丁。
#
# 为什么需要补丁：
#   Miuix 的 build-logic 在**配置阶段**执行 git（取版本号/短 hash），
#   而 composite build 下这个子构建的工作目录是宿主工程（不是 git 仓库），
#   于是 git 以 exit 128 失败，Gradle 同步直接报错。
#   补丁就是给这两处 git 调用补上 workingDir = rootDir。
#
# 用法：
#   ./scripts/setup-miuix.sh                # 默认钉在开发时用的提交
#   ./scripts/setup-miuix.sh main           # 跟上游 main
#   MIUIX_DIR=../miuix-main ./scripts/setup-miuix.sh

set -euo pipefail

MIUIX_REPO="https://github.com/compose-miuix-ui/miuix.git"
PINNED_REF="19b5b831"
REF="${1:-$PINNED_REF}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TARGET="${MIUIX_DIR:-$PROJECT_DIR/../miuix}"

echo "==> Miuix 目标目录：$TARGET"

if [ ! -d "$TARGET/.git" ]; then
  echo "==> 克隆 Miuix"
  git clone "$MIUIX_REPO" "$TARGET"
else
  echo "==> 目录已存在，跳过克隆"
fi

echo "==> 切换到 $REF"
if ! git -C "$TARGET" checkout --quiet "$REF" 2>/dev/null; then
  echo "!! 找不到 $REF，退回到上游 main"
  git -C "$TARGET" fetch --quiet origin main
  git -C "$TARGET" checkout --quiet main
fi

BUILD_CONFIG="$TARGET/build-logic/src/main/kotlin/BuildConfig.kt"
if [ ! -f "$BUILD_CONFIG" ]; then
  echo "!! 没找到 $BUILD_CONFIG，上游结构可能变了，请手动检查" >&2
  exit 1
fi

if grep -q "workingDir = rootDir" "$BUILD_CONFIG"; then
  echo "==> 补丁已存在，跳过"
else
  echo "==> 打补丁：给两处 git 调用补 workingDir"
  # GNU sed（Git Bash / Linux / macOS 装 gsed 时）支持替换串里的 \n
  sed -i \
    -e 's|^\(\s*\)commandLine("git", "rev-list", "--count", "HEAD")|\1workingDir = rootDir\n\1commandLine("git", "rev-list", "--count", "HEAD")|' \
    -e 's|^\(\s*\)commandLine("git", "rev-parse", "--short", "HEAD")|\1workingDir = rootDir\n\1commandLine("git", "rev-parse", "--short", "HEAD")|' \
    "$BUILD_CONFIG"

  if ! grep -q "workingDir = rootDir" "$BUILD_CONFIG"; then
    echo "!! 补丁没打上，请手动在 getGitVersionCode / getGitHashShort 里加 workingDir = rootDir" >&2
    exit 1
  fi
fi

echo
echo "完成。Miuix 位于：$TARGET"
echo "现在可以在 Android Studio 里打开本工程，或执行 ./gradlew :assembleDebug"
