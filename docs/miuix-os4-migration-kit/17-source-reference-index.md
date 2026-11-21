# 17 · 官方文档、源码、提交与参考项目索引

> 用途：任何一条结论都要能追到"哪一行"。本篇是索引，不重复结论。
> 路径约定：`miuix/...` 指 MIUIX 上游源码；其余相对路径都相对**本仓库根目录**（clone 后目录名是 `gif-toolkit/`）。

---

## 1. 本库的证据基线

| 项 | 值 |
|---|---|
| MIUIX 仓库 | `compose-miuix-ui/miuix` |
| commit | `3177a684622a774276c49f52489a43d0d0ed123d` |
| 提交说明 | `library: Refine glass floating action button` |
| 提交时间 | 2026-11-18 12:51:05 +0800 |
| 版本号 | `0.9.5`（`miuix/build-logic/src/main/kotlin/BuildConfig.kt:5`） |
| 参考工程 | GifHelper 1.10（`versionCode = 12`），commit `c2108c6` |
| 审计日期 | 2026-11-19 |
| 仓库形态 | shallow clone（`git rev-parse --is-shallow-repository` = true），本地 tag 最新 `v0.9.4` |

> ⚠️ 基线是**主干未发布**状态。新项目适配时必须按 [02](02-version-and-dependencies.md) §5 重新确认版本。

---

## 2. 官方文档索引

### 2.1 `miuix/docs/guide/`（14 篇）

| 文件 | 主题 | 本库引用处 |
|---|---|---|
| `getting-started.md` | 依赖、平台、接入 | [02](02-version-and-dependencies.md) §9 |
| `blur.md` | blur 架构、`minSdk 33`、API 门槛、`textureBlur` | [06](06-glass-architecture.md) §2、[10](10-about-and-special-effects.md) |
| `glass.md` | glass surface/Modifier、背景采样、API 33 fallback | [06](06-glass-architecture.md)、[07](07-glass-component-guide.md) |
| `material.md` | `miuix-material`、RuntimeShader、`GlassStyle`/`GlassMaterial` | [06](06-glass-architecture.md) §5 |
| `squircle.md` | 连续曲率圆角 | [03](03-os4-theme-system.md) §7 |
| `icons.md` | 图标体系、`miuix-icons` / `miuix-glass-icons` | [04](04-component-mapping.md) §6 |
| `miuix-nav.md` | 自包含导航 runtime | [05](05-navigation-and-adaptive.md) §2 |
| `miuix-window.md` | Desktop 窗口 | —（Android 不涉及） |
| `multiplatform.md` | 平台支持 | [01](01-project-assessment.md) §3 |
| `theme.md` | 主题系统 | [03](03-os4-theme-system.md) §2 |
| `colors.md` | 颜色方案 | [03](03-os4-theme-system.md) §3 |
| `textstyles.md` | 文本样式 | [03](03-os4-theme-system.md) §6 |
| `utils.md` | `MiuixPopupUtils` 等 | [06](06-glass-architecture.md) §4 |
| `best-practices.md` | 外部资源链接 | — |

### 2.2 `miuix/docs/components/glass/`（16 篇）

`index.md`、`glasstopappbar.md`、`glassnavigationbar.md`、`glassnavigationrail.md`、
`glasstabrow.md`、`glassiconbutton.md`、`glassfloatingactionbutton.md`、`glassfloatingtoolbar.md`、
`glassdialog.md`、`glassbottomsheet.md`、`glasspopup.md`、`glasssecondarypopup.md`、
`glasstransformpopup.md`、`glassdropdownpopup.md`、`glassdropdownpreference.md`、`glasstextfield.md`。

📌 注意两个"文档未覆盖"的事实：
- `GlassIconDropdownMenu`（`miuix-glass/.../GlassIconDropdownMenu.kt:49`）是 public 组件，
  但 `docs/components/glass/index.md` 的组件表**没有列出**它。
- 文档表里也没有 `glassSurface` / `Modifier.glassPanel` 这两个底层入口（它们不属于"组件"）。

### 2.3 其它组件文档

- `miuix/docs/components/basic/`：40 篇（`scaffold.md`、`topappbar.md`、`button.md`、`card.md`、
  `textfield.md`、`switch.md`、`slider.md`、`checkbox.md`、`progressindicator.md` 等）。
- `miuix/docs/components/extended/`：17 篇（`arrowpreference.md`、`switchpreference.md`、
  `sliderpreference.md`、`dropdownpreference.md` 等）。
- `miuix/docs/zh_CN/`：以上 guide / components 的中文镜像。

---

## 3. 官方源码索引（按主题）

### 3.1 Backdrop / Blur（`miuix-blur`）

| 符号 | 位置 | 说明 |
|---|---|---|
| `interface Backdrop` | `miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/Backdrop.kt:16` | `drawBackdrop(density, coordinates, layerBlock, downscaleFactor)` |
| `rememberLayerBackdrop` | `miuix-blur/.../LayerBackdrop.kt:39` | `@Composable fun rememberLayerBackdrop(graphicsLayer = rememberGraphicsLayer(), onDraw = DefaultOnDraw): LayerBackdrop` |
| `class LayerBackdrop` | `LayerBackdrop.kt:55` | `drawBackdrop` 实现在 `:71` |
| `Modifier.layerBackdrop` | `LayerBackdropModifier.kt:22` | 把内容录进 layer |
| `Modifier.drawBackdrop`（回调版） | `DrawBackdropModifier.kt:106` | `shape`/`effects`/`onDrawBehind`/`onDrawSurface`/`onDrawFront`/`contentBlendMode` |
| `Modifier.drawBackdrop`（pipeline 版） | `DrawBackdropModifier.kt:161` | `BackdropPipeline`，避免每帧重建 |
| `Modifier.textureBlur` | `TextureEffect.kt:27`（单半径）/ `:61`（双半径） | 最常用 |
| `Modifier.progressiveTextureBlur` | `TextureEffect.kt:176` / `:212` | 顶栏渐进模糊 |
| `BlurColors` / `BlendColorEntry` / `BlurBlendMode` | `BlurDefaults.kt:25` / `:40` / `:52` | DstIn + 混合色 |
| `BlurDefaults` | `BlurDefaults.kt:143` | `BlurRadius = 20f`、`MaxBlurRadius = 100f`、`NoiseCoefficient` |
| `ProgressiveBlur` | `ProgressiveBlur.kt:21` | 方向与渐变 |
| `isRenderEffectSupported()` | `miuix-material/.../RenderEffect.android.kt:10-12` | **仅 SDK≥31，库内无调用点** |
| `isRuntimeShaderSupported()` | `miuix-material/.../RuntimeShader.android.kt:16-18` | **SDK≥33，真正的门槛** |

### 3.2 材质（`miuix-material`）

| 符号 | 位置 | 关键默认值 |
|---|---|---|
| `GlassDefaults` | `miuix-material/src/commonMain/.../GlassDefaults.kt:11` | `CornerRadius 24.dp`、`NoiseCoefficient 0.0045f`、`SourceDensity 3f`、`Style = GlassStyles.CommonMediumRegularLowLight` |
| `GlassDefaults.style(isDark)` | 同文件 `:42-46` | light=`CommonMediumRegularLowLight`，dark=`CommonMediumRegularDark` ← **不要自己按明暗选** |
| `GlassStyle` 结构 | `GlassStyle.kt:23-31` | `blend` / `inner` / `edge` / `reflect` / `light` / `refract` / `background` / `blur` |
| `GlassMaterial` | `GlassMaterial.kt:64` | `blurRadius: Dp` + 3 层 `GlassColorLayer` |
| 预设材质 | `GlassMaterials.kt` | `PopupViewGlass* 60.dp`、`DialogGlass* 60.dp`、`ActionBarMask 40/60.dp`、`PuredThinGlass* 20.dp` |

### 3.3 组件（`miuix-glass`）

| 组件 | 文件:行 | 是否必须传 backdrop | `renderInRootScaffold` |
|---|---|---|---|
| `GlassTopAppBar` | `GlassTopAppBar.kt:262`（滚动版）/ `:340`（`isContentScrolled` 版） | 可选（默认 null） | — |
| `GlassNavigationBar` | `GlassNavigationBar.kt:217` | 必须（可空） | — |
| `GlassNavigationRail` | `GlassNavigationRail.kt:323` | 必须（可空） | — |
| `GlassTabRow` | `GlassTabRow.kt:242` | 必须（可空） | — |
| `GlassSegmentedTabRow` | `GlassTabRow.kt:473` | 必须（**非空**） | — |
| `GlassIconButton` | `GlassIconButton.kt:64` | 可选 | — |
| `GlassFloatingActionButton` | `GlassFloatingActionButton.kt:74` | 可选 | — |
| `GlassFloatingToolbar` | `GlassFloatingToolbar.kt:75` | 可选 | — |
| `GlassDialog` | `GlassDialog.kt:48` | 必须（可空） | **默认 true** |
| `GlassBottomSheet` | `GlassBottomSheet.kt:50` | 必须（可空） | **默认 true** |
| `GlassDialogButton` | `GlassDialogButton.kt:67` | 无 | — |
| `GlassPopup` | `GlassPopup.kt:196` | 必须（可空） | — |
| `GlassSecondaryPopup` | `GlassSecondaryPopup.kt:54` | 必须（可空） | — |
| `GlassTransformPopup` | `GlassTransformPopup.kt:226` | 必须（可空） | — |
| `GlassDropdownPopup` | `GlassDropdownPopup.kt:61` | 必须（可空） | — |
| `GlassDropdownPreference` | `GlassDropdownPreference.kt:64` | 必须（可空） | **默认 true** |
| `GlassIconDropdownMenu` | `GlassIconDropdownMenu.kt:49` | 必须（可空） | — |
| `GlassTextField` | `GlassTextField.kt:43` / `:96` / `:151` | 无 | — |
| `glassSurface(...)` | `GlassSurface.kt:35` | 必须 | — |
| `Modifier.glassPanel(...)` | `GlassPanel.kt:36` | 必须 | — |
| `Modifier.glass(...)` | `Glass.kt:60` | 必须（非空） | — |

### 3.4 主题与 Scaffold（`miuix-ui`）

| 符号 | 位置 | 说明 |
|---|---|---|
| `MiuixTheme(controller=)` | `miuix-ui/src/commonMain/.../theme/MiuixTheme.kt:24-44` | 提供 `LocalColors`/`LocalTextStyles`/`LocalIndication`/`LocalOverscrollFactory`/`LocalContentColor`/`LocalColorSchemeMode` |
| `MiuixTheme(colors=)` | `MiuixTheme.kt:54-72` | **少** `LocalContentColor` 与 `LocalColorSchemeMode` |
| `ThemeController.currentColors()` | `ThemeController.kt:246-274` | Monet 走 `platformDynamicColors` |
| `Colors` 字段清单 | `theme/Colors.kt` | 54 个颜色角色（`copy()` 形参为准） |
| `Scaffold` | `basic/Scaffold.kt:88`（`popupHost` 默认 `MiuixPopupHost`）、`:90`（`contentWindowInsets` 默认）、`:104`/`:115`（`LocalRootDialogStates`） | 弹层宿主 |
| `MiuixPopupHost` | `utils/MiuixPopupUtils.kt:544` | 库 public |
| `LocalRootDialogStates` / `LocalRootPopupStates` | `MiuixPopupUtils.kt:597-598` | **库里是 internal**，只能通过 Scaffold 间接用 |
| `renderInRootScaffold` 的选择逻辑 | `MiuixPopupUtils.kt:184-186`（dialog）、`:265-267`（popup） | 决定找最外层还是当前 Scaffold |

### 3.5 导航（`miuix-nav`）

| 符号 | 位置 |
|---|---|
| `NavKey` | `nav/core/NavKey.kt:26` |
| `NavBackStack` / `navBackStackOf` | `nav/core/NavBackStack.kt:23` / `:28` |
| `NavController` | `nav/core/NavController.kt:19` |
| `NavDisplay` | `nav/core/NavDisplay.kt:974`（backStack DSL）、`:1005`（controller 版） |
| `NavTransitions` | `nav/transition/NavTransitions.kt:28` |
| `NavDisplayEffects` | `nav/core/NavDisplayEffects.kt:63` |
| 预测性返回 | `nav/gesture/PredictiveBackHandler.kt:81`、`:95` |
| 系统圆角 | `nav/core/NavSystemCornerRadius.kt:18` |

### 3.6 只在 example 里的符号（**不是库 API**）

| 符号 | 位置 | 本库引用 |
|---|---|---|
| `rememberBlurBackdrop()` | `miuix/example/shared/src/commonMain/kotlin/utils/PageUtils.kt:294` | [06](06-glass-architecture.md) §2 |
| `rememberCombinedBackdrop()` | `miuix/example/shared/src/commonMain/kotlin/component/liquid/CombinedBackdrop.kt:46` | [06](06-glass-architecture.md) §6.3 |
| `LocalTopAppBarBackdrop` | `.../utils/PageUtils.kt:53` | [06](06-glass-architecture.md) §4.2 |
| `LocalTopAppBarTitleAlpha` | `PageUtils.kt:56` | [05](05-navigation-and-adaptive.md) §4.4 |
| `LocalAppPageInsets` / `AppPageInsets` | `PageUtils.kt:64-74` | [05](05-navigation-and-adaptive.md) §4.2 |
| `LocalTopLayer` / `TopLayerHost` | `.../utils/TopLayer.kt:38-64` | [06](06-glass-architecture.md) §4.2 |
| `GlassPageHost` | `.../GlassPage.kt:686-692`（private） | [06](06-glass-architecture.md) §4.2 |
| `AdaptiveTopAppBar` | `PageUtils.kt:225` | [05](05-navigation-and-adaptive.md) §3.2 |
| `shouldShowSplitPane()` / `shouldExpandNavigationRail()` / `shouldShowThreePanes()` | `.../utils/AdaptiveUtils.kt:11-41` | [05](05-navigation-and-adaptive.md) §3.1 |

---

## 4. 关键提交索引

### 4.1 引入与演进（`git log --oneline -30 -- miuix-glass miuix-blur`）

| commit | 说明 | 影响 |
|---|---|---|
| `b7a5f08f` | `library: ...` glass 模块初始（13 个组件） | 引入 `miuix-glass` / `miuix-glass-icons` |
| `4328d633` | blur 基础（`Backdrop.kt`、`DrawBackdropModifier.kt`） | 引入 `miuix-blur` |
| `ca674992` | `library: Split materials out of glass and blur into miuix-material` | 三模块拆分 |
| `19b5b831` | **breaking change**（见 §4.3） | `glassPanel.fallback` → `Modifier?`、`GlassTopAppBar` 语义变化、`glass` minSdk 33→24 |
| `cd5d4b91` | `library: Add GlassNavigationRail (#484)` | 新增 Rail（2026-11-16） |
| `447e6140` | `library: Add GlassFloatingActionButton and GlassFloatingToolbar` | 新增两个组件（11-17） |
| `a5405892` | `library: Add GlassTextField and fix floating bar captionBar padding` | 新增 `GlassTextField`（11-18） |
| `63d51cdf` | `library: Support horizontal drag and morphing indicator in GlassSegmentedTabRow` | 分段控件成熟 |
| `d45ca181` | `library: Lower BlurDefaults.MaxBlurRadius to 100f` | 影响 `blurRadius` 夹紧 |
| `3177a684` | HEAD：`library: Refine glass floating action button` | `Size` 60→56dp、按压缩放变化 |

### 4.2 Glass 图标改名（**升级必查**）

📌 提交 `2855a36b`（`library: Add more miuix glass icons (#483)`，2026-11-14）的 15 组 `R` 改名：

| 旧 | 新 |
|---|---|
| `Glass.Folder` | `Glass.FolderOutline` |
| `Glass.Favorites` | `Glass.FavoriteOutline` |
| `Glass.FavoritesFill` | `Glass.FavoriteFill` |
| `Glass.Book` | `Glass.BookFill` |
| `Glass.WorldClock` | `Glass.ClockFill` |
| `Glass.CloudFill` | `Glass.Cloud` |
| `Glass.Photos` | `Glass.ImageFill` |
| `Glass.Image` | `Glass.ImageOutline` |
| `Glass.Lock` | `Glass.LockOutline` |
| `Glass.Notes` | `Glass.NotesOutline` |
| `Glass.Notifications` | `Glass.NotificationOutline` |
| `Glass.Settings` | `Glass.SettingsOutline` |
| `Glass.Unstar` | `Glass.StarFill` |
| `Glass.Starred` | `Glass.StarOutline` |
| `Glass.Unlock` | `Glass.UnlockOutline` |

另：`758bb3e2`（`library: Clean up and fix glass icons`，11-17）把 `CaremaOutline` 拼写修正为
`CameraOutline`、删除 `DrawerListHandle`、新增 `UnfoldMore`。

### 4.3 `19b5b831` 的 breaking change 明细

commit body 自述（原文摘要）：

- `Modifier.glassPanel` 的 `fallback` 参数变为 `Modifier?`，默认 `null`。
- `GlassTopAppBar`：`isContentScrolled` 语义变化；两个重载与 `collapseRamp` 增加 `visualContentOffset`；
  `BandOverhang` 调整。
- `GlassNavigationBar.onSelect`：只在释放 / 键盘 / 无障碍激活时触发一次，**不再按下即触发**。
- `miuix-glass` Android `minSdk` 从 33 降到 24。
- `drawBackdrop` 新增 `BackdropPipeline` 重载。
- `miuix-glass` 的 `overrideLibrary` 不再需要；`miuix-blur` 仍需 33。

---

## 5. 参考项目与二次来源

> ⚠️ 本节前三条是 **GIF 助手审计文档记录的引用**（`docs/os4-*-audit.md`），
> 本库**未**独立核对其源码。引用时请注明"经 GIF 助手审计转引"，不要当作一手事实。

| 参考 | 用到的部分 | 出处 |
|---|---|---|
| **KernelSU**（`AboutMiuix.kt`） | About 页 `logoBlend` 三档混合色（`AboutMiuix.kt:189-203`）、图标尺寸策略（245dp 主体 / 100dp 容器）、`clipToBounds` | GIF 助手 R9/R10 审计 |
| **InstallerX Revived**（`MiuixAboutPage.kt`） | 玻璃卡写法（`:612-638`）、`textureBlur` 混合色（`:420-431`）、淡出阈值（`:482/517/546`）、`fillParentMaxHeight` 撑满 | GIF 助手 R10/R11 审计 |
| **Kyant0/AndroidLiquidGlass** | `CombinedBackdrop` 的原始思路（经 MIUIX example 转引） | `miuix/example/.../CombinedBackdrop.kt` 头部注释 |
| **MIUIX example** | 全部 structural 参考（`AppContent.kt`、`GlassPage.kt`、`PageUtils.kt`） | 一手，见 §3.6 |

---

## 6. GIF 助手侧索引（🏠 本地实现，不可照抄）

| 文件 | 作用 | 本库引用 |
|---|---|---|
| `.../ui/GlassScope.kt` | `GlassScope`、`LocalPageBackdrop`、`LocalRootBackdrop`、`rememberPageBackdrop`、`PageTopBar`、`ContentBackdropBox` | [06](06-glass-architecture.md) §4、[07](07-glass-component-guide.md) |
| `.../ui/PageChrome.kt` | `Os4ListPage`、`pageContentPadding`、`pageScrollModifiers`、`LocalBottomBarClearance` | [05](05-navigation-and-adaptive.md) §5 |
| `.../ui/Adaptive.kt` | `rememberWideLayout`、`LocalRailInset`、`railStartInset`、`LocalTopBarTitleAlpha` | [05](05-navigation-and-adaptive.md) §3–§4 |
| `.../ui/GlassSupport.kt` | `LocalGlassEnabled`、`isGlassSupported`、`isSoftwareRenderer` | [06](06-glass-architecture.md) §5 |
| `.../ui/CombinedBackdrop.kt` | 两层 backdrop 叠加 | [06](06-glass-architecture.md) §6.3 |
| `.../ui/Widgets.kt` | 三层配色、`GlassCard`、`DropdownRow`、`PrimaryActionBar`、`IconSizes` | [03](03-os4-theme-system.md)、[08](08-custom-component-migration.md) |
| `.../ui/AnimatedTheme.kt` | 逐色插值主题 | [03](03-os4-theme-system.md) §5 |
| `.../App.kt` | 装配顺序、弹层宿主、Pager、Rail | [18](18-gif-helper-baseline-audit.md) |
| `AGENTS.md` | 15 条硬约束（本库 [12](12-known-issues-and-solutions.md) 的主要来源） | [12](12-known-issues-and-solutions.md) |
| `docs/os4-round5~11-audit.md` | 逐轮真实审计（含实测数字） | [12](12-known-issues-and-solutions.md) |

GIF 助手的实测环境（引用数字时必须一起写）：

| 项 | 值 |
|---|---|
| 设备 | AVD `android16`（Android 16 / API 36，1080×2400 / 420dpi） |
| GPU | R5–R7 `-gpu host`；R8–R11 `-gpu swiftshader_indirect` |
| 尺寸手法 | `wm density 320` + 旋转 → 1200×540dp；`wm density 144` → 1200dp；`density 420` + 横 → 914×411dp |
| 像素测量 | Pillow 逐点取色；`adb exec-out screencap -p` |
| 录屏 | `animator_duration_scale=10`，20~60fps 逐帧 |
| 官方对照 | 现场编译官方示例 APK 同机截图（`Miuix-v1.0.9(1292)` / `(1307)` / `v1.1.0(51)`） |

---

## 7. 外部链接

| 目标 | URL |
|---|---|
| MIUIX 仓库 | https://github.com/compose-miuix-ui/miuix |
| MIUIX 文档站 | https://compose-miuix-ui.github.io/miuix/ |
| 官方 blur 指南 | https://github.com/compose-miuix-ui/miuix/blob/main/docs/guide/blur.md |
| 官方 glass 指南 | https://github.com/compose-miuix-ui/miuix/blob/main/docs/guide/glass.md |
| Glass 组件文档目录 | https://github.com/compose-miuix-ui/miuix/tree/main/docs/components/glass |
| Maven Central 查询 | https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/ |
| GIF 助手仓库 | https://github.com/Ayang233/gif-toolkit |
| KernelSU（About 参考） | https://github.com/tiann/KernelSU |
| InstallerX Revived（About/玻璃卡参考） | https://github.com/Live-Block/InstallerX-Revived |
| AndroidLiquidGlass（思路来源） | https://github.com/Kyant0/AndroidLiquidGlass |
| Android `RuntimeShader` | https://developer.android.com/reference/android/graphics/RuntimeShader |
| Android `RenderEffect` | https://developer.android.com/reference/android/graphics/RenderEffect |

---

## 8. 索引维护规则

1. 新增一条结论时，同时在本篇加一行"文件:行号"，否则视为无依据。
2. 行号会随上游变动失效。失效时**不要猜**，重新读源码并更新；无法确认就写「约」。
3. 升级基线后：
   - 更新 §1 的 commit / 时间 / 版本；
   - 在 §4 追加本次相关提交；
   - 重跑 [02](02-version-and-dependencies.md) §6 检查表；
   - 把"只在 example 里"的符号清单重新核一遍（上游可能把它提升为库 API，也可能删掉）。
