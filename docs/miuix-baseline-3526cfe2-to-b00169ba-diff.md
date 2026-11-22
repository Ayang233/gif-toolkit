# Miuix 基线 `3526cfe2` → `b00169ba` 差异报告

- **日期**：2026-11-22
- **旧基线**：`3526cfe2`（= tag `v0.9.5-beta03`）
- **新基线**：`b00169ba`（`v0.9.5-beta03` 之后 19 个提交，main head）
- **规模**：19 个提交 / 100 个文件
- **结论**：工具链零变化，`src/` 升级即编译通过；有 3 处**观感**变化需要真机确认（见 §4）。

---

## 1. 工具链

| 项 | 旧 | 新 | 动作 |
| --- | --- | --- | --- |
| AGP | 9.5.2 | 9.5.2 | 无 |
| Kotlin | 2.4.21 | 2.4.21 | 无 |
| Compose Multiplatform | 1.13.0-rc01 | 1.13.0-rc01 | 无 |
| Gradle | 9.9.2 | 9.9.2 | 无 |
| compileSdk | 37.2 | 37.2 | 无 |

唯一与构建有关的改动是 `44f1d315 build: Add googleapis maven-central for ci`（只影响上游 CI 的仓库源），
以及 `settings.gradle.kts` / `gradle.properties` 各一处 CI 配置。

## 2. 变更面（按模块）

| 模块 | 文件数 | 说明 |
| --- | --- | --- |
| `docs` | 25 | 组件文档（新增 `GlassCard` 条目等） |
| `miuix-glass` | 23 | 新增 `GlassCard`；弹层动效、底栏/侧栏/顶栏内部优化 |
| `miuix-ui` | 23 | 新增 `DialogMotion`；弹层布局与 Tooltip 约束；`ColorPalette` 内部换 `fastRoundToInt` |
| `miuix-window` | 9 | 桌面窗口（本项目不使用） |
| `example` | 7 | 示例整理 |
| `miuix-blur` | 6 | blur 管线性能与噪声修复 |
| `.github` / `build-logic` / gradle 配置 | 7 | CI |

## 3. A 类：官方原生更新（直接吸收）

| 提交 | 内容 | 对项目的影响 |
| --- | --- | --- |
| `9128921c` | **新增 `miuix-glass` 的 `GlassCard`**（玻璃面板**内部**用的半透明卡，不采样背景） | 无。本项目 `ui/Widgets.kt` 的 `GlassCard` 是"采页面背景画玻璃"的另一件事（`textureBlur`），不是同一个组件 |
| `bf61ef44` | 弹层进出场动画对齐 HyperOS；新增 `layout/DialogMotion`，`GlassDialog` 默认 `DialogMotion.Glass` | 删除确认框的弹簧曲线变成本轮官方预设，**观感变化** |
| `234db0fe` / `d21e82f9` | 渐进模糊带高度改为 `max(顶栏实际高度, 状态栏+折叠高度+底栏内容) + gradientOverhang` | 顶栏模糊带更完整，**观感变化** |
| `accff623` | `GlassBottomSheet` 去掉底边描边 | 文件操作面板底边不再有描边 |
| `6904e7bd` | `GlassBottomSheet` 无 title / 无 action 时不再占头部空间 | 本项目面板有 title，不受影响 |
| `6904e7bd`（同提交） | `GlassBottomSheet` `insideMargin` 默认 `BottomSheetDefaults.insideMargin` → `GlassBottomSheetDefaults.InsideMargin = DpSize(12.dp, 0.dp)` | 面板行左右内边距从 24dp 收成 12dp，**版式变化** |
| `8f621215` | 弹层 / Tooltip 约束在自定义窗口框内 | 桌面端相关，Android 不受影响 |
| `9ca75342` | 级联弹层首项展开时保持顶部圆角 | 本项目未用级联弹层 |
| `cadfb0cf` | 热点路径改用 `fastRoundToInt` / `fastCoerceIn` | 无 |
| `da590171` / `ee0281df` / `acdb4107` / `de9da1d8` / `c83aacd8` | blur 管线：收紧卷积半径、跳过零模糊层、特化 1D shader、修噪声层 padding / renderEffect | 玻璃性能与噪声，无 API 变化 |
| `8fbd4a14` / `06e45370` / `2b226150` / `b00169ba` | 示例与测试整理 | 无 |

## 4. C 类：需要核对的耦合点

| # | 位置 | 上游变化 | 本轮结论 |
| --- | --- | --- | --- |
| C1 | `ui/PageChrome.kt` `rememberBottomBarClearance()` | `GlassNavigationBar` 的 `floatingMargin` 三分支（≤16→20、≥28→跟随、其余→12+inset）**未变** | 无需改 |
| C2 | `ui/GlassScope.kt` 顶栏 | band 高度算法变了；`bandBrush` / `bandOverhang` 的默认值与私有类型契约未变 | 无需改（本项目仍显式传 `bandBrush`，因为它用的是 `pageBaseColor()` 而不是 `surface`） |
| C3 | `ui/GlassScope.kt` 顶栏 `progressiveBlur = true` | 渐进模糊仍由库内部 `isRuntimeShaderSupported()` 分支 | 无需改 |
| C4 | `screens/LibraryScreen.kt` `GlassBottomSheet` | `insideMargin` 默认 24→12；底边描边去掉 | 无需改（吃新默认值），**需真机/模拟器看一眼行宽** |
| C5 | `screens/LibraryScreen.kt` `GlassDialog` | 默认动效换成 `DialogMotion.Glass` | 无需改，**看一遍进出场** |
| C6 | `ui/Widgets.kt` `GlassCard`（自写） | 上游新增了同名 `miuix-glass` 组件，但语义不同（不采样） | **保留自写版本**，不要被"同名"误导去替换 |
| C7 | `ui/AnimatedTheme.kt` | `MiuixTheme` / `Colors` / `ThemeController` 无改动 | 无需改 |
| C8 | `ui/Adaptive.kt` 阈值 840 / 600 / 1.2 / 1200 | 官方 `AdaptiveUtils.kt` 未改 | 无需改（本轮另补 `LocalContainerSize`，见 §5） |
| C9 | `ui/CombinedBackdrop.kt` | `Backdrop` 接口（`isCoordinatesDependent` / `offsetResidual*` / `drawBackdrop`）未变 | 无需改 |
| C10 | `ui/bg/BgEffect*` | AGSL uniform 名未变 | 无需改 |

D 类（零 diff）：`miuix-material`、`miuix-icons`、`miuix-glass-icons`、`miuix-nav`、`miuix-squircle`、
`miuix-preference`、`miuix-core`。

## 5. 本轮按迁移指南修掉的自造轮子

按 `https://github.com/YuKongA/Mishka/blob/main/docs/miuix-glass-migration.md` 逐条复核后，
落实了 3 处（此前各轮已修掉 6 处，见 `docs/audit-self-built-wheels.md` §11 与本文件 §6）：

| # | 位置 | 修前 | 修后（官方口径） |
| --- | --- | --- | --- |
| 1 | `screens/ToolScreen.kt` 抠图颜色 | 自绘 5 个圆形色块：`clip(CircleShape)` + `border(2dp/1dp)` + 手调 `HapticFeedbackType.ToggleOn` + 自补 `semantics` | 官方 `ColorPalette`（7×12 HSV + 灰阶列 + 选中指示 + squircle 轮廓），摆法照官方 `component/ColorPickerSection.kt` |
| 2 | `ui/PageChrome.kt` `isScrolled` | `listState.canScrollBackward` | `listState.canScrollBackward \|\| scrollBehavior.state.overscrollOffset < -1f`（官方 `GlassPage.kt:176`、指南 §5.4） |
| 3 | `ui/Adaptive.kt` + `App.kt` + `ui/Widgets.kt` 视口尺寸 | 三处各自读 `LocalWindowInfo.current.containerSize` | 根部 `BoxWithConstraints` 注入 `LocalContainerSize`，读取时优先它、回退窗口（指南 §6、官方 `AdaptiveUtils.kt:14`） |

## 6. 复核后判定「保留」的项（不是轮子）

| 项 | 理由 |
| --- | --- |
| `ui/Widgets.kt` `GlassCard` | 与上游新 `GlassCard` 语义不同：本项目采页面背景做材质；上游那个是玻璃面板**内**的半透明卡。与官方 `AboutPage.kt:347-360` 的 `Card + textureBlur` 同款 |
| `ui/ProgressButton.kt` | 库无"带进度填充的按钮"；容器已改为官方 `squircleSurface` + `ButtonDefaults` 常量 |
| `ui/Widgets.kt` `PrimaryActionBar` / `ui/SystemInsets.kt` `safeBottomGap` | 库无"通栏主操作条"与屏幕圆角安全区公式 |
| `ui/AnimatedTheme.kt` | 库无主题色过渡；`LocalColorSchemeMode` 仍是 internal，只能外层 `MiuixTheme(controller)` + 内层 `MiuixTheme(colors)` |
| `ui/CombinedBackdrop.kt` / `ui/bg/*` / `ui/Adaptive.kt` 阈值 | 官方 example 源码复刻（不进库发布物），升级时按清单 diff |
| `ui/PageChrome.kt` `rememberBottomBarClearance()` | 镜像了 `GlassNavigationBar` 未导出的 `floatingMargin` 公式；改成 `defaultWindowInsetsPadding = false` 会改变底栏视觉位置，本轮不动，继续登记为 U02 |
| `ui/Widgets.kt` `pageColor()` | AMOLED 与"卡内画布"需要的次级表面，官方色板里没有对应档位 |

## 7. 构建与验证记录

```bash
# 切基线（origin 已从 gh-proxy 改回官方地址，proxy 域名当前返回 403）
git -C ../miuix fetch origin main
git -C ../miuix checkout b00169ba
rm -rf ../miuix/{,build-logic/,miuix-*/}build      # 见下"坑"
./gradlew :assembleDebug :assembleRelease
```

- **踩到的坑**：不清理 `../miuix/*/build` 时，`mergeLibDexDebug` 会报
  `The given file ... GlassDialogButtonDefaults$WhenMappings.class is located outside the root directory ...`。
  这是旧基线残留的 class 与新基线 class 列表不一致，不是代码问题，删 build 目录即恢复。
- `assembleDebug` 47.9 MB、`assembleRelease` 3.44 MB，均 BUILD SUCCESSFUL。
- 模拟器（1080×2400 / 420dpi / API 36）冒烟：首页、工具页（折叠顶栏、滑杆）、
  抠图颜色调色板、作品页、文件操作面板、删除确认框、关于页（渐变 + 玻璃卡）、深色模式，
  均无 FATAL / libhwui / SIGSEGV。截图见 `_baseline-upgrade/shots/new/`。
- 玻璃质感（渐进模糊、`textureBlur` 的真实采样效果）仍以真机为准，模拟器是 SwiftShader。
