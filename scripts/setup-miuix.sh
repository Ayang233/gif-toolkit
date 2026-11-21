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
# 上游 f3829d3d = 当前基线（2026-11-20，`perf: improve sidebar performance (#489)`）。
# 对应发布版 v0.9.5-beta02（66248bac，已推送 Maven Central），源码基线取其后 3 个 main 提交：
#   b31c60f2 显式 isContentScrolled 下的过滚驱动修复（我们正走这条路径）
#   c16dfa12 仅 example
#   f3829d3d NavDisplay 布局尺寸延迟读取 / TabRow 量测期测量 / Rail 密度变化居中
# 本轮升级记录见 docs/os4-round12-audit.md。
# 若在受限网络下（github.com:443 不可达）本机无法 fetch 到这个提交，
# 脚本会保留当前 HEAD 继续，不会把已有源码切走。
PINNED_REF="f3829d3d"
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
  echo "!! 本地没有 $REF，尝试从上游 main 拉取"
  if git -C "$TARGET" fetch --quiet origin main --tags && git -C "$TARGET" checkout --quiet main; then
    echo "!! 已退回到上游 main（可能落后于 $REF）"
  else
    # 受限网络：fetch 本身就会失败。此时保留现有源码，不要让脚本中断。
    echo "!! 拉取失败（网络受限？），保留当前提交：$(git -C "$TARGET" rev-parse --short HEAD)"
    echo "!! 如需 $REF，请在能联网的环境执行：git -C \"$TARGET\" fetch origin 后再重跑本脚本"
  fi
fi

# 校验：源码必须真的停在 $REF 上。
# 上面那两个分支在受限网络下只会打印警告并保留旧提交，脚本仍以 0 退出；
# 接着构建就会「用旧基线出一个新版本的包」，而且没人会发现。
if [ "${MIUIX_OFFLINE:-0}" = "1" ]; then
  echo "==> MIUIX_OFFLINE=1，跳过基线校验（构建结果不可复现）"
elif ! git -C "$TARGET" rev-parse --verify --quiet "$REF^{commit}" >/dev/null; then
  echo "!! 解析不到 $REF，当前 HEAD=$(git -C "$TARGET" rev-parse --short HEAD)" >&2
  echo "!! 先执行：git -C \"$TARGET\" fetch origin --tags，再重跑本脚本" >&2
  exit 2
else
  EXPECTED="$(git -C "$TARGET" rev-parse --short "$REF^{commit}")"
  ACTUAL="$(git -C "$TARGET" rev-parse --short HEAD)"
  if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "!! 基线不一致：期望 $EXPECTED，实际 $ACTUAL" >&2
    echo "!! 常见原因：本机源码早于该 ref 发布，或网络受限导致 fetch 失败。" >&2
    echo "!! 解决：git -C \"$TARGET\" fetch origin --tags，再重跑本脚本；" >&2
    echo "!!      确认要用当前提交时设 MIUIX_OFFLINE=1 显式跳过。" >&2
    exit 3
  fi
  echo "==> 基线校验通过：HEAD=$ACTUAL"
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

# 顺手对一下 AGP 版本。
#
# 为什么必须在脚本里做：composite build 下宿主与 ../miuix 是两个独立构建，但 AGP 只允许
# 全构建一个版本。版本不一致时 Gradle 要跑到**依赖解析期**才报错（两分钟），
# 而且报错挂在 `:debugCompileClasspath` 上，看着像依赖坏了，其实是工具链没对齐
# （0.9.5-beta03 把上游 AGP 从 9.5.1 提到 9.5.2，就是这么炸的）。
# 这里提前一秒把两个数字摆出来。
HOST_AGP=""
if [ -f "$PROJECT_DIR/build.gradle.kts" ]; then
  HOST_AGP="$(grep -m1 'id("com.android.application")' "$PROJECT_DIR/build.gradle.kts" \
    | sed -nE 's/.*version "([0-9][0-9.]*)".*/\1/p')"
fi
UP_AGP=""
if [ -f "$TARGET/gradle/libs.versions.toml" ]; then
  UP_AGP="$(grep -m1 -E '^[[:space:]]*agp[[:space:]]*=' "$TARGET/gradle/libs.versions.toml" \
    | sed -nE 's/.*"([^"]+)".*/\1/p')"
fi
if [ -n "$HOST_AGP" ] && [ -n "$UP_AGP" ]; then
  if [ "$HOST_AGP" = "$UP_AGP" ]; then
    echo "==> AGP 对齐：宿主 $HOST_AGP = 上游 $UP_AGP"
  else
    echo "!! AGP 版本不一致：宿主 build.gradle.kts 是 $HOST_AGP，上游 miuix 是 $UP_AGP" >&2
    echo "!! composite build 不允许两个 AGP 共存，构建会在依赖解析期失败：" >&2
    echo "!!   Using multiple versions of the Android Gradle plugin ($UP_AGP, $HOST_AGP) in the same build is not allowed" >&2
    echo "!! 修：把 $PROJECT_DIR/build.gradle.kts 里 com.android.application 的版本改成 $UP_AGP" >&2
  fi
fi

echo
echo "完成。Miuix 位于：$TARGET"
echo "现在可以在 Android Studio 里打开本工程，或执行 ./gradlew :assembleDebug"
