# Miuix 基线 `f3829d3d` → `3526cfe2` 差异报告

> 目标基线：Miuix `3526cfe2`（2026-11-21，`example: split LazyColumn cards into per-row items and gate compact backdrop`）
> = 发布版 **`v0.9.5-beta03`**（tag 就打在 `3526cfe2` 上，发布后 main 无新增提交）
> 旧基线：`f3829d3d`（v0.9.5-beta02 `66248bac` + 其后 3 个 main 提交）
> 判定依据：上游仓库没有 CHANGELOG，`docs/miuix-baseline-maintenance.md` §Step 2 要求以 **GitHub Releases + commit** 为准；
> 本次核对了 Releases 页 `v0.9.5-beta03` 正文与 `origin/main` 实际 head，两者一致。
> 规模：9 个提交、439 个文件（336 个是 `miuix-glass-icons` 的未用 import 清理、38 个 example、4 个 docs）。

---

## 0. 结论速览

1. **工具链条只有一处变化：AGP `9.5.1` → `9.5.2`。** CMP 仍是 `1.13.0-rc01`、Kotlin 仍是 `2.4.21`、Gradle wrapper 仍是 `9.9.2`、
   `compileSdk` 仍是 `37.2`（`build-logic/BuildConfig.kt`：`COMPILE_SDK=37 / MINOR=2 / TARGET=37 / MIN=24`）。
   与 Releases 正文一致（beta03 只列了 AGP / spotless / hot-reload 三条依赖升级，没有 CMP 与 compileSdk 抬升）。
2. **代码适配 0 处。** `debug` 与 `release` 都是一次构建通过，没有改名、没有签名变化。
3. **有一处行为变化需要真机看观感**：`GlassTopAppBar` 的 band 透明度驱动源从 `maskProgress`
   换成了 `scrollBehavior.state.collapsedFraction`（`715ac29a`）。我们吃的是默认值，所以跟着变。

---

## 1. 工具链（AGENTS.md 第 1 条要求逐项核对）

| 项 | 旧基线 `f3829d3d` | 新基线 `3526cfe2` | 下游是否必须同步 |
| --- | --- | --- | --- |
| AGP | 9.5.1 | **9.5.2** | **是**（composite build 只允许一个 AGP，已在 `build.gradle.kts` 同步） |
| Compose Multiplatform | 1.13.0-rc01 | 1.13.0-rc01 | 否 |
| Kotlin / compose 编译器插件 | 2.4.21 | 2.4.21 | 否 |
| Gradle wrapper | 9.9.2 | 9.9.2 | 否（宿主本来就同版本） |
| compileSdk / buildTools / minSdk | 37.2 / 37.0.0 / 24 | 37.2 / 37.0.0 / 24 | 否 |
| targetSdk（上游自身） | 37 | 37 | 否（宿主仍停在 36，只影响编译期） |

`scripts/setup-miuix.sh` 末尾的 AGP 对账在本轮确实先报了
`宿主 9.5.1 / 上游 9.5.2`，改完再跑变成 `AGP 对齐：宿主 9.5.2 = 上游 9.5.2` —— 这条自检按设计生效了。

---

## 2. 差异分类（按 `miuix-baseline-maintenance.md` §Step 3 四类）

### A 官方原生更新（直接吸收）

- `3f56c659` blur / glass 降低重组与分配；`daa2d15f` 简化顶栏 band 实现（`drawWithCache`、常量缓存、
  `rememberUpdatedState` 收敛）。属于纯性能/实现重构。
- `0320daa0` `GlassTabRow` 列表内静止不透明度与淡出闪烁修复（本项目未使用 `GlassTabRow`）。
- `715ac29a` 顶栏 band 渐隐恢复 + 折叠进度追踪（见下文 C 类）。
- `miuix-glass-icons` 336 个文件批量删除未用 import —— **API 与路径都没变**，
  `MiuixIcons.Glass.*` 引用不受影响。
- `GlassPress.kt` / `GlassIconButton.kt` / `GlassNavigationBar.kt` 内部量测与 `constrainHeight` 收敛。

### B 本项目定制（不用动）

- `src/` 一行未改。`ui/GlassScope.kt`、`ui/PageChrome.kt`、`App.kt` 的录制层/顶栏/底栏装配关系不受影响。

### C 冲突（依赖上游默认值/内部行为，需要核对）

| # | 上游变化 | 我们的暴露面 | 结论 |
| --- | --- | --- | --- |
| 1 | `GlassTopAppBar` **删除 `visualContentOffset` 参数**（两个重载都删） | `ui/GlassScope.kt` 调 `GlassTopAppBar` 时未传该参数 | **零动作**（编译即验证） |
| 2 | band 透明度驱动源 `maskProgress` → `collapsedFraction`（仅 `expandable && scrollBehavior != null` 时） | `PageTopBar` 传了 `scrollBehavior`、`expandable` | 编译无感，**观感需真机确认**（见 §3） |
| 3 | 顶栏 mask 过渡 `GlassMotion.topBarMask()` → `tween(100ms, Linear)` | 吃默认值 | 折叠手感略快，模拟器上看不出问题 |
| 4 | `underlayMaterial` 不再受 `keepMaterial` 约束（只判 `rawBackdrop != null`） | 未传 `keepMaterial`，吃默认 true | 无行为差异 |
| 5 | `LocalGlassTopAppBarContext` 从 `staticCompositionLocalOf` 改为 `compositionLocalOf`，`GlassTopAppBarContext` 加 `@Stable` | 内部类型，我们不引用 | 零动作 |
| 6 | `GlassTopAppBarDefaults` 各 `@Composable` 函数补 `@ReadOnlyComposable` | 我们调 `bandBrush()`（非 composable 重载） | 零动作 |
| 7 | `GlassNavigationBar` 内部布局改 `constrainHeight`；`floatingMargin` 公式**未变**（≤16dp→20dp / ≥28dp→跟随 / 其余 12dp+inset） | `PageChrome.rememberBottomBarClearance()` 复刻了这条公式 | **不用改**（U02 维持上一轮的 20dp） |

### D 无冲突

`miuix-nav`、`miuix-squircle`、`miuix-core`、`miuix-ui/basic` 的公开签名无变化；`miuix-material` 只有内部清理
（`GlassOverlays.kt` / `GlassShaderCore.kt` 删了一行）与 `EdgeStrokeModifier.kt` 的注释/结构整理。

---

## 3. 本轮适配清单

| 文件 | 改动 | 原因 |
| --- | --- | --- |
| `scripts/setup-miuix.sh` | `PINNED_REF` `f3829d3d` → `3526cfe2` | 基线唯一权威来源 |
| `build.gradle.kts` | `com.android.application` `9.5.1` → `9.5.2` | composite build 单 AGP 约束 |
| `AGENTS.md` | 更新「构建环境的两个坑」第 1 条的基线与 AGP 说明 | 三处对齐 |
| `src/**` | **无改动** | 无需适配 |

`versionCode` / `versionName` 保持 `14` / `1.12` 未动：本轮只吸收基线，是否发 `1.13`（`versionCode 15`）等你确认。

---

## 4. 验证结果（模拟器 android16 / API 36 / 1080×2400，软件渲染）

| 项 | 结果 |
| --- | --- |
| `./gradlew :assembleDebug` | **BUILD SUCCESSFUL**，一次通过，零编译错误、零警告新增 |
| `./gradlew :assembleRelease` | **BUILD SUCCESSFUL**，R8 + lintVital 通过；45MB / 3.3MB，与历史一致 |
| release 的 `DEBUG_UI` 分支 | 当时 dex 内「开始按钮配色对比」0 次（debug 包 1 次）。**2026-11-21 追加：该调试页已整条删除**，`BuildConfig.DEBUG_UI` 与 `ButtonColorPreview.kt` 一并移除，两个包现在都是 0 次 |
| debug 安装 + 启动 | 正常，`pidof` 存活，logcat 无 `FATAL` / `SIGSEGV` / `libhwui` |
| release 安装 + 启动 | 正常，首页完整渲染 |
| 工具页出图 | 视频转 GIF 跑通，产物 `video2gif_*.gif` 183 KB，结果卡带「分享 / 保存到相册」 |
| 顶栏 · 玻璃开 | 滚动后出现模糊带 + 胶囊返回键，无色带、无硬边 |
| 顶栏 · 玻璃关 | 实色条与页面底色一致（`pageBaseColor()`），无亮块/暗块 |
| 顶栏 · 深色 + 玻璃开 | band 为克制的深色渐隐，没有出现「比页面亮的横带」 |
| 字幕输入框 + 键盘 | 键盘弹出后输入框（y≈1142）在「开始生成」（y≈1356）上方，按钮不被压住、无重叠 |

**模拟器上不能判定的**（`miuix-baseline-maintenance.md` §0.5）：SwiftShader 下 `textureBlur` 不产出像素，
玻璃的模糊质感、band 厚度、滚动帧率只能真机复核。本轮 C 表第 2 条（band 驱动源变化）正是需要在真机上确认观感的项。

---

## 5. 回滚

```bash
git -C ../miuix checkout f3829d3d
# 宿主两处改回：scripts/setup-miuix.sh 的 PINNED_REF、build.gradle.kts 的 AGP 9.5.1
bash scripts/setup-miuix.sh
```
