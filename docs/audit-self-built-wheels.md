# GifHelper「自造轮子」审计（基线 Miuix 5ff41ba9）

- **性质**：只读审计。除本文件外未改动任何文件。
- **审计对象**：`src/androidMain/kotlin/com/ayang/giftoolkit` 下全部 **36 个 `.kt`**，逐文件过一遍（清单见 §10）。
- **上游**：`../miuix`，detached HEAD `5ff41ba9`（含本地补丁 `build-logic/.../BuildConfig.kt`，未触碰）。
- **本仓库状态**：HEAD `060c804` + 未提交的基线升级改动（`AGENTS.md`、`build.gradle.kts`、`ui/GlassSupport.kt`、`ui/GlassScope.kt`、`screens/AboutScreen.kt`、`ui/PageChrome.kt`、`docs/`、`scripts/`）。
  **行号以审计时工作区快照为准**；其中 `GlassSupport.kt` / `GlassScope.kt` / `AboutScreen.kt` / `PageChrome.kt` 正被并行修改，引用时请以**符号名**为主、行号为辅。

## 0. 判定原则（先对齐口径）

| 类别 | 定义 | 处置 |
| --- | --- | --- |
| **真·自造轮子** | 手写了 MIUIX **库**已经提供的视觉 / 尺寸 / 动画 / 手势 / 模糊封装；存在官方 API 可直接替换，且替换后行为等价或更一致 | **必须改**（§1） |
| **抄默认值** | 把库形参的默认值又显式写进调用点（值相同也可，但上游一改就脱节） | **建议改**（§1 W4） |
| **官方 example 源码复刻** | 官方 `example/shared` 里有同名实现，但**它不在库的发布物里**（无法 import），只能抄 | **保留** + 建立同步机制（§2） |
| **库无对应能力** | 纯业务、GIF 编解码、任务进度、项目特有语义、AMOLED/画布色这类官方色彩分级里不存在的档位 | **保留，不是轮子**（§3） |
| **合规组合** | 用官方组件（`Card` + `BasicComponent` / `ArrowPreference` / `GlassBottomSheet` / `GlassNavigationBar` …）拼出项目特有布局 | **不算轮子，不要动**（§12） |

> 用户口径「不需要自造轮子，MIUIX 里都可以直接用」在本报告里严格区分两层：**库 API**（`miuix-ui` / `miuix-glass` / `miuix-material` / `miuix-preference` / `miuix-nav` / `miuix-squircle` …，可以直接 import）才算"能直接用"；**官方 example 源码**（`example/shared/src/...`）不算，只能复刻。

## 1. 真·自造轮子（必须改）

### W1 ⭐ 设备屏幕圆角：自己读 RoundedCorner，官方已有同名 API

| 项 | 内容 |
| --- | --- |
| 现状 | `ui/SystemInsets.kt:46-65` `rememberDeviceCornerRadius()`：`view.rootWindowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)`（API 31+）→ 回退 `rounded_corner_radius_bottom` dimen → 0，再用 `LocalContext/LocalView/LocalDensity` remember。共约 20 行。 |
| 调用点 | `App.kt:226`（`NavDisplayEffects.cornerClipRadius`）、`Widgets.kt:494`（底部主操作条 `safeBottomGap(corner = …)`） |
| 官方方案 | `top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius()`<br>• 声明：`miuix-nav/src/commonMain/.../nav/core/NavSystemCornerRadius.kt:18`<br>• Android 实现：`miuix-nav/src/androidMain/.../NavSystemCornerRadius.android.kt:24-45` —— **与本项目逐行同算法**（RoundedCorner bottom-left → framework dimen → 0.dp），唯一差别是官方按 `(context, view, insets)` 记忆，旋转/切屏时会重算 |
| 官方用法 | `example/shared/src/commonMain/kotlin/AppContent.kt:267` `val navCornerRadius = if (isWideScreen) 0.dp else rememberNavSystemCornerRadius()`，`:283` 直接喂 `cornerClipRadius` |
| 建议改法 | 删除 `SystemInsets.kt:46-65`，连同只有它在用的 5 个 import（`android.os.Build`、`android.view.RoundedCorner`、`LocalContext`、`LocalView`、`remember`）；`App.kt:226` 与 `Widgets.kt:494` 改成 `rememberNavSystemCornerRadius()`。**净删约 20 行 + 5 个 import，2 处一行替换**。 |
| 工作量 / 风险 | 小（≤15 min）/ 低（语义完全一致；`0.dp` 直屏回退也一致）。依赖已就绪：`build.gradle.kts:40` 已声明 `miuix-nav` |

### W2 ⭐ 卡内画布（预览舞台）的圆角容器：手写 `.clip(RoundedCornerShape)+.background`，官方有 squircle 直接封装

| 项 | 内容 |
| --- | --- |
| 现状 | ① `ui/Widgets.kt:413-421` `PreviewStage`：`Box(... .height(stageHeight).clip(RoundedCornerShape(12.dp)).background(pageColor()))`；② `screens/ToolScreen.kt:437-443` `AnimatedPreview`：同一段（`aspectRatio` + `.clip(RoundedCornerShape(12.dp))` + `.background(pageColor())`）。 |
| 为什么是轮子 | 官方 `miuix-squircle` 的 KDoc 原文就是冲着这个写法来的：`SquircleBackground.kt:125`「**drop-in replacement for `.clip(RoundedCornerShape(r)).background(color)`** with a squircle outline」，API 在 `:134`（`Modifier.squircleSurface(color, cornerRadius)`）。官方 `Card` / `Button` / `IconButton` 本体全用 `squircleSurface`（`Card.kt:179`、`Button.kt:76`、`IconButton.kt:71`），所以现在卡片是**连续圆角**、卡内画布是**圆弧**，四角近看对不齐。 |
| 官方方案 | `Modifier.squircleSurface(color = pageColor(), cornerRadius = <r>)`（`miuix-squircle/.../SquircleBackground.kt:134`；API<33 自动回退 `RoundedCornerShape`，无需自己判断） |
| 关键旁证 | `build.gradle.kts:32-35` 早就为这件事声明了依赖并写明用途：「miuix-squircle：OS4 的连续曲率圆角（squircleClip / squircleBackground），关于页的应用图标用它，而不是自己 clip 一个 RoundedCornerShape」。但 **全项目源码 `grep squircle` 零命中** —— 依赖和意图都在，代码没跟上，所以这两处 `.clip(RoundedCornerShape(...))` 是漏改而不是设计选择。 |
| 建议改法 | 两处各一行：`.clip(RoundedCornerShape(12.dp)).background(pageColor())` → `.squircleSurface(color = pageColor(), cornerRadius = 12.dp)`；<br>**半径建议同时对齐** `CardDefaults.CornerRadius = 16.dp`（`miuix-ui/.../basic/Card.kt:192`）——现在写的是 12dp，与全项目卡片 16dp 不一致（`Widgets.kt:176` 的 KDoc 示例也写的 `RoundedCornerShape(16.dp)`，同步改）。 |
| 工作量 / 风险 | 小（≤30 min，含 4 套配色目测）/ 低（纯视觉，回退路径由库处理）。依赖已就绪：`build.gradle.kts:35` 已声明 `miuix-squircle` |

### W3 ⭐ 手写「颜色选择行」：行布局 + 色块 + 选中语义 + 触觉全部自绘

| 项 | 内容 |
| --- | --- |
| 现状 | `screens/ToolScreen.kt:341-405` `ColorSwatches`：<br>• 行：`Row(fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp))`（`:357-364`）+ 标题 `Text(style = headline1)`（`:367`）；<br>• 色块：5 个 `Box.size(28.dp).clip(CircleShape).selectable(role = RadioButton)` + 内层 `Box.size(24.dp).clip(CircleShape).background(color).border(2/1.dp, primary/outline)`（`:371-402`）；<br>• 触觉：手调 `HapticFeedbackType.ToggleOn`（`:380-383`）；<br>• 代码注释自己也承认「这是本项目唯一自绘的"选择"控件，官方对应物是 RadioButtonPreference / RadioButton」（`:352-355`）。 |
| 为什么是轮子 | ① **行**：56dp 最小高度、16dp 内边距、`headline1` 标题全部是 `BasicComponent` 的内部实现（`miuix-ui/.../basic/Component.kt:171` `.heightIn(min = 56.dp)`、`:292` `BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)`、`:92` title 用 `headline1`）——本文件把这三项抄了一遍；<br>② **颜色选择**：官方有 `ColorPalette`（`miuix-ui/.../basic/ColorPalette.kt:64`，7×12 HSV 网格 + 选中指示 + 触觉，本体也是 squircle）与 `ColorPicker`（`ColorPicker.kt:69`，HSV/OkHSV/OkLab/OkLch 四套）。 |
| 建议改法 | **A（推荐，功能更强）**：<br>`BasicComponent(title = "抠图颜色", bottomAction = { ColorPalette(color = Color(form.keyColor), onColorChanged = { form.keyColor = it.toArgb() }) })` —— 一行换掉 60 行，触觉/选中/圆角全部由库负责；`form.keyColor` 本来就是 `Int` 存储，可直接接。<br>**B（若产品坚持 5 个预设色）**：保留色块，但用 `BasicComponent(title = "抠图颜色", bottomAction = { Row { … } })` 承载（`bottomAction` 是全宽槽位，见 `Component.kt:67/128`），删掉 hand-copied 的 56dp/16dp/12dp 与自写标题；色块本体官方没有"色卡行"组件，属**合规定制**，可选把选中动画换成 `RadioButton(selected = …, colors = …)`（`RadioButton.kt:58`）。 |
| 工作量 / 风险 | A：中（约 1 h，卡片高度/预览位置要调，UX 从 5 预设变全色域需产品确认）/ 中<br>B：小（≤30 min）/ 低 |

### W4 把库默认值抄进调用点（清理，防上游默认值漂移）

| 子项 | 现状（文件:行号） | 官方默认值 | 建议 |
| --- | --- | --- | --- |
| W4a | `App.kt:228-233` `NavDisplayEffects(enableCornerClip = true, cornerClipRadius = …, dimAmount = 0.5f, backdropColor = …)` | `miuix-nav/.../NavDisplayEffects.kt:64` `enableCornerClip = true`、`:67` `dimAmount = 0.5f` | 删掉 `enableCornerClip` / `dimAmount` 两行；`cornerClipRadius` 换成 W1 的官方 API 后保留；`backdropColor` 保留（项目自定义） |
| W4b | `ui/GlassScope.kt:259` `val bandBrush = remember(surfaceColor) { GlassTopAppBarDefaults.bandBrush(surfaceColor) }`、`:282` `backgroundAlpha = backgroundAlpha ?: { 1f }`、`:283` `bandBrush = bandBrush`、`:284` `bandOverhang = GlassTopAppBarDefaults.BandOverhang` | `miuix-glass/.../GlassTopAppBar.kt:361` `bandBrush: Brush = GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)`、`:362` `bandOverhang = GlassTopAppBarDefaults.BandOverhang`、`:374` `backgroundAlpha = { 1f }` —— **逐字相同** | 全部删掉（含 `:258 surfaceColor` 局部变量），行为不变；同时"上游改默认值我们不跟随"这个风险点自动消失。<br>注意 `:273 largeTitle` / `:274 subtitle` 是 `PageTopBar` 自己的形参，**不要一起删** |

> 判定：W4 不是"重画视觉"，但属于本项目反复踩过的同一类问题（`AGENTS.md`「构建环境的两个坑」第 1 条：GlassNavigationBar 的 floatingMargin 就是这么被打中的）。**工作量小、风险低、收益是长期的**。

### W5 `ProgressButton` 的轮廓与尺寸常量未对齐官方 Button

| 项 | 内容 |
| --- | --- |
| 现状 | `ui/ProgressButton.kt:55` `cornerRadius: Dp = 16.dp`（硬编码）、`:56` `minHeight: Dp = 50.dp`、`:58` `insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)`、`:64` `RoundedCornerShape(cornerRadius)` |
| 官方 | `miuix-ui/.../basic/Button.kt:169` `ButtonDefaults.CornerRadius = 16.dp`、`:174` `ButtonDefaults.InsideMargin = PaddingValues(horizontal = 16.dp, vertical = 13.dp)`、`:164` `ButtonDefaults.MinHeight = 40.dp`；官方 Button 轮廓是 `squircleSurface`（`Button.kt:76`），**不是圆角矩形** |
| 影响 | 同一屏里"开始生成"（自绘轮廓）与结果卡的"分享/保存"（官方 `Button`，`ToolScreen.kt:476-477`）四角轮廓不一致；16dp 是库常量却抄成字面量 |
| 建议改法 | `cornerRadius = ButtonDefaults.CornerRadius`（值相同，去掉字面量）；`insideMargin = ButtonDefaults.InsideMargin`（垂直 12→13dp，需回归）；`minHeight = 50.dp` 是**有意加高**（主操作条），保留并在注释里写清；若要 squircle 轮廓，需把 `Surface` 换成官方 Button 的搭法（`squircleSurface + clickable`）——否则至少把"圆弧 vs 连续圆角"的差异写进注释 |
| 工作量 / 风险 | 小（≤20 min）/ 低（垂直 1dp 观感微调） |

### W6 `InfiniteProgressIndicator` 参数偏离官方默认

| 项 | 内容 |
| --- | --- |
| 现状 | `ui/Widgets.kt:593-598`：`size = 16.dp, strokeWidth = 2.dp, orbitingDotSize = 1.5.dp` |
| 官方 | `miuix-ui/.../basic/ProgressIndicator.kt:406` `DefaultInfiniteProgressIndicatorSize = 20.dp`、`:400` `…StrokeWidth = 2.dp`、`:403` `…OrbitingDotSize = 2.dp` |
| 建议 | 要么吃默认（20/2/2，按钮高 50dp 放得下），要么显式引用 `ProgressIndicatorDefaults.DefaultInfiniteProgressIndicator*` 常量，并把"为什么缩到 16/1.5"写在注释里（目前只有数值没有理由） |
| 工作量 / 风险 | 小 / 低 |

## 2. 官方 example 源码复刻（库不提供，保留 + 同步机制）

这些文件/函数**在官方 example 里几乎逐行存在**，但 `example/shared` 不是库产物（没有发布、不能 import），所以"直接用"做不到。**判定：保留，不是轮子**；风险是"上游 example 改了、我们不知道"。

| # | 本项目 | 官方 example 对应 | 关系 / 同步建议 |
| --- | --- | --- | --- |
| E1 | `ui/CombinedBackdrop.kt:36-61` | `example/shared/.../component/liquid/CombinedBackdrop.kt:24-46` | 逻辑逐行相同（本项目注释已声明 Apache-2.0 移植）。上游改 `Backdrop` 契约（`isCoordinatesDependent` / `offsetResidual*` / `drawBackdrop` 签名）时**编译期会报**，风险可控；建议升级时 diff 该文件 |
| E2 | `ui/GlassScope.kt:106-117` `rememberPageBackdrop` | `example/shared/.../utils/PageUtils.kt:300-308` `rememberBlurBackdrop` | 同一手法（`rememberLayerBackdrop { drawRect(surface); drawContent() }`）；官方返回可空并内含能力判断，本项目把判断上移到 `GlassSupport`（合规，见 E5） |
| E3 | `ui/PageChrome.kt:96-110` `Modifier.pageScrollModifiers` | `example/shared/.../utils/PageUtils.kt:172-186` | 官方版多 `showTopAppBar` 分支；本项目简化版，行为一致 |
| E4 | `ui/PageChrome.kt:119-168` `pageContentPadding` | `example/shared/.../utils/PageUtils.kt:189-225` | 官方基于 `LocalAppPageInsets`；本项目基于自有 `LocalBottomBarClearance` + `LocalRailInset`（项目特有契约），属于**有意的第二套**；仅参数名/结构相似 |
| E5 | `ui/Adaptive.kt:24-32, 100-105` | `example/shared/.../utils/AdaptiveUtils.kt:17-46` | 阈值 `840/600/1.2`、`1200` 逐字相同；官方没有库级 API，只能抄。**升级时必须核这 4 个数字** |
| E6 | `ui/Adaptive.kt:90` `LocalTopBarTitleAlpha` | `example/shared/.../utils/PageUtils.kt:56` | 同名同义 |
| E7 | `ui/GlassScope.kt:210-235` 返回键（`Glass.ChevronBackward` + 28dp + `GlassIconButton`；非玻璃档 `extended.Back` + 24dp + `IconButton`） | `example/shared/.../component/BackNavigationIcon.kt:22-52` | 逐行同款（连 28/24dp 都相同）；合规 |
| E8 | `ui/bg/BgEffect.kt` + `ui/bg/BgEffectShader.kt` | `example/shared/.../component/effect/{BgEffectBackground,BgEffectPainter,BgEffectModifier,BgEffectConfig,OS3BgFrag}.kt` | 移植版（已裁掉 OS2 分支）；AGSL 字符串与 uniform 名一一对应，上游改 uniform 名不会编译报错 → **这是最容易静默漂移的一处**，建议把 `BgEffectShader.kt:11-96` 与上游 `OS3BgFrag.kt` 在升级清单里单列 |
| E9 | `ui/ProgressButton.kt` | 非 example：来自 InstallerX Revived（`ui/page/miuix/widgets/ProgressButton.kt`，Apache-2.0） | 生态移植；库无等价物，见 §3 |
| E10 | `ui/PageChrome.kt:177-354` `Os4ListPage` 整体骨架 | `example/shared/.../GlassPage.kt`（`GlassPage`/`GlassPageHost`）+ `AppContent.kt:653-700`（`CompactScreenLayout`） | 结构同源（Scaffold 宿主 + 内容录制层 + 顶栏兄弟关系），但本项目把"大标题 + 整屏背景 + 弹层槽位"做成了自己的参数化骨架；官方 example 没有可复用的等价物 |

**建议动作**（不需要改 UI 代码）：
1. 把上表 E1–E8 写进 `docs/upgrade-prep-customization-inventory.md`（该文档已有部分内容，补全"官方 example 对应文件"一列）；
2. 升级脚本/清单里固定一条：`git -C miuix diff <old>..<new> -- example/shared/src/commonMain/kotlin/utils example/shared/src/commonMain/kotlin/component/liquid example/shared/src/commonMain/kotlin/component/effect`；
3. 长期：给上游提 PR，把 `rememberBlurBackdrop` / `CombinedBackdrop` / `BgEffectBackground` 提到库（如 `miuix-glass` 或 `miuix-blur`），提完即可删除本地副本。

## 3. 官方没有对应能力（保留，不是轮子）

| # | 位置 | 说明 | 备注 / 可选优化 |
| --- | --- | --- | --- |
| N1 | `ui/Widgets.kt:99-110` `pageColor()` | 「卡内画布」比卡片深/浅一档的次级表面。官方色彩分级里：静态浅色 `surface=#F7F7F7`、`surfaceContainer=#FFFFFF`、`surfaceVariant=#FFFFFF`（`Colors.kt:375/377/382`）、`surfaceContainerHigh=#E8E8E8`（`:385`）；静态深色 `surface=#000000`、`surfaceContainer=#242424`（`:485/492`）、`surfaceContainerHighest=#2D2D2D`（`:497`）—— **深色下 #000000 与 #242424 之间确实没有官方档位**，所以推导值有存在意义。 | 但注释里「Monet 会把 surface/surfaceContainer 压成同一值」这条理由在 `3177a684` 之后**已作废**（AGENTS.md 第 2 条自己也写了）。建议改注释，并把浅色档简化为直接用 `MiuixTheme.colorScheme.surface`（比白卡深一档、颜色与官方一致），只在深色 + AMOLED 保留推导 |
| N2 | `ui/Widgets.kt:83-89` `cardColor()`、`:133-138` `pageBaseColor()` | `surfaceContainer` / `surface` + AMOLED 特判（`#0D0D0D` / 纯黑）。官方没有 AMOLED 概念 | 保留；这是产品决策，不是轮子 |
| N3 | `ui/Widgets.kt:468-518` `PrimaryActionBar` + `ui/SystemInsets.kt:79-95` `safeBottomGap` + `:32-36` `rememberHorizontalSafeInsets` | 全宽主操作条 + 屏幕圆角侵入量公式 `d = R - sqrt(2R·sideGap - sideGap²)`。库没有"通栏主操作条"，也没有圆角安全区公式；官方 example `GlassPage.kt:281` 只有 `maxOf(24.dp, safePadding + 8.dp, …)` 的下限（本项目注释引用的就是这一条，属 example 口径） | 保留。唯一依赖库的 `rememberDeviceCornerRadius` 按 W1 换掉 |
| N4 | `ui/AnimatedTheme.kt:33-69, 72-130` `AnimatedMiuixTheme` + `lerpColors` | 主题切换时逐色插值。`MiuixTheme(controller)` 是"取到目标配色→立刻换掉"，库无过渡能力 | 保留。可选改进（中风险）：把插值结果写回 `ThemeController.lightColors/darkColors`（`ThemeController.kt:238-239` 是 public 的 `mutableStateOf`）并用 `MiuixTheme(controller = controller)`，可一并拿到 `LocalContentColor`（`MiuixTheme.kt:38`）与 `LocalColorSchemeMode`（`:39`），从而删掉 `AnimatedTheme.kt:64-67` 的补丁和 `App.kt:333-337 / 405-409` 两处 `selectedColor` 手算。**Monet + keyColor 模式下 `currentColors()` 走 `colorsFromSeed` 重算，写回 light/darkColors 不生效**，需按模式分开处理，故列为"可选" |
| N5 | `ui/ProgressButton.kt` 整文件 | 库没有"带进度填充的按钮"（`Button.kt` / `ProgressIndicator.kt` 都没有 `progress` 形参） | 保留（轮廓/常量见 W5） |
| N6 | `ui/GlassSupport.kt:32` `isGlassSupported()` | 薄封装 `isRenderEffectSupported()`，是**唯一决策点**（AGENTS 第 3 条） | 保留（这是对库能力的正确用法）。**注意：`isSoftwareRenderer`（自己猜 GL_RENDERER/Build.HARDWARE）已在本轮工作区被删除**，`App.kt` / `Widgets.kt` / `AboutScreen.kt` 已无引用，且 `GlassScope.kt:270` 改为 `progressiveBlur = true`（由库内部 `isRuntimeShaderSupported()` 分支，`GlassTopAppBar.kt:398/406`）。这正是"自造能力判断"的教科书案例，**不要改回去** |

## 4. 尺寸 / 间距 / 字号对照表

> 口径：只列"库里有对应常量/默认值"的项。标 **应替换** 的会在 §11 出现。

| 位置 | 当前值 | 官方值 / API（文件:行号） | 判定 |
| --- | --- | --- | --- |
| `ui/SystemInsets.kt:46-65` | 自算圆角 | `rememberNavSystemCornerRadius()`（`NavSystemCornerRadius.kt:18`） | **应替换**（W1） |
| `ui/Widgets.kt:417` / `screens/ToolScreen.kt:441` | `RoundedCornerShape(12.dp)` + `background` | `Modifier.squircleSurface(color, cornerRadius)`（`SquircleBackground.kt:134`） | **应替换**（W2） |
| `ui/Widgets.kt:417` 半径 | 12.dp | `CardDefaults.CornerRadius = 16.dp`（`Card.kt:192`） | **应替换**（W2，视觉一致性） |
| `ui/Widgets.kt:157-158` | `padding(horizontal = 12.dp).padding(bottom = 12.dp)` | 官方 example `CardSegment(outerHorizontalPadding = 12.dp, …)`（`GroupedCardItems.kt:27/39`） | 保留（与 example 一致，无库常量） |
| `ui/Widgets.kt:152` `GroupCard.bottomPadding` | 12.dp | 无库常量 | 保留 |
| `ui/Widgets.kt:378` `NoteText` | `padding(horizontal = 28.dp, vertical = 10.dp)` | `SmallTitleDefaults.InsideMargin = PaddingValues(28.dp, 8.dp)`（`SmallTitle.kt:42`） | 保留（正文非标题；垂直 10 vs 8 是有意的呼吸，可在注释写明） |
| `ui/Widgets.kt:249-274` `IconSizes` | Row 26 / Action 28 / Inline 19 / Empty 44 / Media 28 | 无库级图标尺寸常量（`Icon.kt:204` 是 `private` 24dp 默认；官方 example 返回键自己写 28/24dp） | 保留（尺寸唯一出处的做法正确） |
| `ui/Widgets.kt:595-597` | `size=16.dp, strokeWidth=2.dp, orbitingDotSize=1.5.dp` | `ProgressIndicatorDefaults`（`ProgressIndicator.kt:400/403/406`：2/2/20dp） | 建议对齐（W6） |
| `ui/Widgets.kt:477/495/504` | `sideGap=12.dp`、下限 `24.dp`、`top=20.dp` | 24dp 下限对应 example `GlassPage.kt:281`；其余无库常量 | 保留（项目几何） |
| `ui/Widgets.kt:547` | `tween(durationMillis = 300)` | 无库级等价（`GlassMotion.kt` 只服务玻璃组件） | 保留 |
| `ui/ProgressButton.kt:55` | `16.dp` | `ButtonDefaults.CornerRadius = 16.dp`（`Button.kt:169`） | 应替换为常量（W5） |
| `ui/ProgressButton.kt:58` | `PaddingValues(16.dp, 12.dp)` | `ButtonDefaults.InsideMargin = (16.dp, 13.dp)`（`Button.kt:174`） | 建议替换（W5） |
| `ui/ProgressButton.kt:56` | `minHeight = 50.dp` | `ButtonDefaults.MinHeight = 40.dp`（`Button.kt:164`） | 保留（有意加高） |
| `screens/ToolScreen.kt:361` | `heightIn(min = 56.dp)` | `BasicComponent` 内部 `.heightIn(min = 56.dp)`（`Component.kt:171`） | **应替换**（W3，改用 BasicComponent） |
| `screens/ToolScreen.kt:362` | `padding(16.dp, 12.dp)` | `BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)`（`Component.kt:292`） | **应替换**（W3） |
| `screens/ToolScreen.kt:367` | `textStyles.headline1` | `BasicComponent` title 用 `headline1`（`Component.kt:92`） | 一致（W3 替换后自动保持） |
| `screens/ToolScreen.kt:373/392` | 28dp 槽 / 24dp 色块 / 2·1dp 边框 | 官方 `ColorPalette`（`ColorPalette.kt:64`） | W3 方案 A 可整体删除 |
| `screens/AboutScreen.kt:297` | `fontSize = 14.sp` | `MiuixTheme.textStyles.body2`（`TextStyles.kt:165-169`，14.sp） | 建议替换（用 textStyle，官方字号常量） |
| `screens/AboutScreen.kt:285` | `fontSize = 35.sp` | 官方最大 `title1 = 32.sp`（`TextStyles.kt:208-212`） | 保留（对齐 KSU/InstallerX 的 35sp，属于有意偏离；建议注释说明） |
| `screens/AboutScreen.kt:59/62` | 132dp / 100dp | 无库常量（参考项目值） | 保留 |
| `ui/PageChrome.kt:121-123` | `extraTop=2.dp, extraBottom=12.dp` | 无库常量（项目内边距契约） | 保留 |
| `ui/PageChrome.kt:277-280` | `+40.dp` 头部留白、`300.dp` 初值、`+20.dp` 占位呼吸 | 官方 example `AboutPage.kt` 同口径（example 非库） | 保留（§2 E10） |
| `ui/PageChrome.kt:79-84` | 镜像 `floatingMargin` 三分支 + `Height + margin + 12.dp` | `GlassNavigationBar.kt:319-324` 内部同式（**未暴露**）；`GlassNavigationBarDefaults.Height = 54.dp`（`:122`） | 保留，但建议去耦（见 §11 建议项 S1） |
| `App.kt:342` | `.padding(horizontal = 24.dp)` | 官方 example `AppContent.kt:791` 同值 | 保留（与官方例一致） |
| `App.kt:229-231` | `enableCornerClip=true` / `dimAmount=0.5f` | `NavDisplayEffects.kt:64/67` 默认即是 | **删冗余**（W4a） |
| `ui/GlassScope.kt:259/282-284` | bandBrush / bandOverhang / backgroundAlpha | `GlassTopAppBar.kt:361/362/374` 默认即是 | **删冗余**（W4b） |

## 5. 动画与手感

| 位置 | 当前做法 | 官方可用能力 | 判定 |
| --- | --- | --- | --- |
| `App.kt:186-189` 切 tab | `pagerState.springAnimateToPage(index)` + `PagerDefaults.flingBehavior(snapAnimationSpec = PagerNavigationSpringSpec)` | 官方 `miuix-ui/.../utils/PagerGestureUtils.kt` 等（已在 import） | 合规（正确用法） |
| `App.kt:265-301` Pager 手势 | `pagerGestureOverride(mode = CrossAxis)` + `PagerGestureNestedScrollConnection` + `userScrollEnabled=false` | 官方 `PagerGesture*` 工具 | 合规 |
| `ui/PageChrome.kt:96-110` 滚动 | `overScrollVertical` + `scrollEndHaptic` + `scrollBehavior.nestedScrollConnection` | 官方 `utils/Overscroll.kt`、`ScrollEndHaptic.kt` | 合规 |
| `ui/BackTransitions.kt:19-91` | 5 档转场，4 档基于官方 `navGraphicsTransition` 自写 | 库只有 `NavTransitions.MiuixDefault / Modal / None`（`NavTransitions.kt:43/66/79`），**没有** AOSP/Classic/Scale 预设 | 合规（官方 API 是函数式积木，自写曲线不是轮子） |
| `ui/AnimatedTheme.kt:49` | `tween(320, FastOutSlowInEasing)` | 库无主题过渡动画 | 保留（N4） |
| `ui/Widgets.kt:545-548` | 进度条 `animateFloatAsState(tween(300))` | 库无进度按钮动画 | 保留 |
| `ui/bg/BgEffect.kt:147-158` | 色段 `Animatable` + `spring(0.9f, 35f)` + 逐帧 `invalidateDraw` | 库无 OS3 背景动画（example 复刻，E8） | 保留 |
| `ui/bg/BgEffect.kt:266-281` | 手写 `withFrameNanos` 60fps 节流循环 | 库无等价 | 保留（example 同款） |
| 全项目 | **没有**手写 `detectDragGestures` / `nestedScroll` / 边缘过冲 / 弹簧仿真 | — | ✅ 无重复 |

## 6. 滚动 / 手势 专项结论

- 无自写 `nestedScroll`、无自写 overscroll、无自写拖动阈值 → **没有与 `utils/Overscroll*`、`PagerGesture*` 重复的实现**。
- 唯一自写的手势相关 Modifier 是 `ui/Adaptive.kt:67-78` `Modifier.railStartInset(base)`：它把"侧栏让位（逐帧动画值）"的读取推到量测期。这是项目特有契约（库没有 rail inset 概念），**保留**；`AGENTS.md` 第 13 条是它的规格说明。

## 7. 模糊与玻璃 专项结论

| 位置 | 现状 | 官方能力 | 判定 |
| --- | --- | --- | --- |
| `ui/Widgets.kt:187-219` `GlassCard` | 官方 `Card` + 官方 `textureBlur`（`shape = RoundedCornerShape(CardDefaults.CornerRadius)`、`blurRadius = 60f`、`BlurDefaults.blurColors(surface@0.6)`），`backdrop == null` 时退回 `cardColor()` | 与官方 example `AboutPage.kt:347-360`（`Card` + `textureBlur`，其 `blurRadius` 变量初值即 60f，见 `:195`）同一写法 | **保留（合规）**。可选升级：`miuix-glass` 已有更高级的玻璃封装 `Modifier.glass(backdrop, shape: GlassShape, style, material, stroke, …)`（`Glass.kt:65`）与 `Modifier.glassPanel(...)`（`GlassPanel.kt:36`，带阴影/stroke/fallback），若想要折射/描边可换；不换也不算轮子 |
| `ui/GlassScope.kt:106-117` `rememberPageBackdrop` | 官方 `rememberLayerBackdrop { drawRect(pageBaseColor()); drawContent() }` | example `rememberBlurBackdrop` 同款 | 保留（E2） |
| `ui/CombinedBackdrop.kt` | 手写两层 `Backdrop` 叠加（实现库的公有 `Backdrop` 接口） | 库无 `CombinedBackdrop`；example 有同名文件 | 保留（E1） |
| `screens/AboutScreen.kt:241-254, 269-280` | 图标/标题 `textureBlur(blurRadius=150f, colors=logoColors, contentBlendMode=DstIn)` | 官方 example `AboutPage.kt:273-278` 同 API（例中 logo 半径 100f；150f 来自 KSU/InstallerX） | 保留（合规；半径差异是有意选择，注释已说明） |
| `ui/GlassScope.kt:259/282-284` | 显式传 `bandBrush` / `bandOverhang` / `backgroundAlpha` | `GlassTopAppBar` 默认值相同 | **删冗余**（W4b） |
| `ui/GlassSupport.kt` | 唯一决策点；`isSoftwareRenderer` 已删除 | 库的 `isRenderEffectSupported()` / `isRuntimeShaderSupported()` | 保留（N6，不要加回自造判断） |

## 8. 主题与颜色

- `pageBaseColor()` = `surface`（`Colors.kt:375/485`）→ 合规。
- `cardColor()` = `surfaceContainer`（`Colors.kt:382/492`）+ AMOLED 特判 → 保留（N2）。
- `pageColor()` = 由 card 反推 → 保留但**注释理由过时**（N1），建议简化浅色档。
- 动画主题：`MiuixTheme(colors = animated)` 重载**缺** `LocalContentColor` 与 `LocalColorSchemeMode`（`MiuixTheme.kt:54-72` vs `:24-44`），项目用 `CompositionLocalProvider(LocalContentColor …)` 补了一半（`AnimatedTheme.kt:63-68`），另一半（`LocalColorSchemeMode` 是 internal）只能在调用点手算 `selectedColor`（`App.kt:333-337`、`App.kt:405-409`）。**这不是轮子，是官方 API 缺口**；改进方向见 N4。

## 9. 导航与自适应

- `ui/Adaptive.kt:24-32`（840 / 600 / 1.2）、`:100-105`（1200）= 官方 example `AdaptiveUtils.kt:17-46`；库无 API → 保留，列入升级核对项（E5）。
- `GlassNavigationRail` / `GlassNavigationBar` 的使用、`LocalRailInset = (ExpandedWidth + Margin) * progress`、`railStartInset` 量测期读取 → 与 `AGENTS.md` 第 8/12/13 条一致，**合规**。
- `App.kt:250-264, 398-414` 录制层/轨道/Rail 的兄弟关系 → 合规（结构性，不是重复实现）。
- `App.kt:215-218` 外层 `Scaffold` 当弹层宿主 = 官方 example `GlassPageHost`（`GlassPage.kt:704`）的等价物，库没有 → 保留（E10）。

## 10. 逐文件判定（36 个 `.kt`）

| # | 文件 | 定性 | 判定 |
| --- | --- | --- | --- |
| 1 | `App.kt` | 主题装配 + NavDisplay + 一级页 Pager/底栏/Rail/弹层宿主 | 合规组合；含 W1（:226）、W4a（:229-231） |
| 2 | `AppState.kt` | 全局状态 + 工具表单 | 业务，保留 |
| 3 | `GifApplication.kt` | Application（含系统预测性返回开关） | 业务，保留 |
| 4 | `MainActivity.kt` | Activity 入口 | 业务，保留 |
| 5 | `Route.kt` | 路由定义 | 业务，保留 |
| 6 | `core/ColorKey.kt` | 色度抠图算法 | 业务，保留 |
| 7 | `core/GifCodec.kt` | GIF 编解码 | 业务，保留 |
| 8 | `core/GifTasks.kt` | 10 个工具的生成流程 | 业务，保留 |
| 9 | `core/MediaTools.kt` | 保存/分享/目录 | 业务，保留 |
| 10 | `core/MotionPhoto.kt` | 动态照片解析 | 业务，保留 |
| 11 | `core/Stabilizer.kt` | 防抖 | 业务，保留 |
| 12 | `data/SettingsStore.kt` | SharedPreferences 持久层 | 业务，保留 |
| 13 | `screens/AboutScreen.kt` | 关于页（渐变 + textureBlur 大标题） | 合规（官方 `textureBlur` 接线）；含 §4 字号两项建议 |
| 14 | `screens/HomeScreen.kt` | 主页工具列表 | 合规（`ArrowPreference`） |
| 15 | `screens/LibraryScreen.kt` | 作品页 + `GlassBottomSheet`/`GlassDialog` | 合规（弹层/行全走官方） |
| 16 | `screens/LicensesScreen.kt` | 许可列表 + 正文阅读 | 合规（`ArrowPreference` + `Text`） |
| 17 | `screens/SettingsScreen.kt` | 设置首页 | 合规 |
| 18 | `screens/ThemeScreen.kt` | 主题设置 | 合规（`DropdownRow`/`SwitchRow`） |
| 19 | `screens/ToolScreen.kt` | 工具页（参数面板/预览/结果） | **含 W3（:341-405 颜色选择）、W2（:441）**；其余合规 |
| 20 | `task/TaskRunner.kt` | 任务分发 + 进度 + 取帧 | 业务，保留 |
| 21 | `ui/Adaptive.kt` | 宽屏/展开阈值 + rail inset + 标题透明度 | example 复刻（E5/E6），保留 |
| 22 | `ui/AnimatedTheme.kt` | 逐色主题过渡 | 库无能力（N4），保留 |
| 23 | `ui/AppInfo.kt` | 版本号串 | 业务，保留 |
| 24 | `ui/BackTransitions.kt` | 5 档转场 | 合规（`navGraphicsTransition`） |
| 25 | `ui/ButtonColorPreview.kt` | 仅 debug 的禁用态配色对比页 | 合规（复用真实 `ProgressButton`） |
| 26 | `ui/CombinedBackdrop.kt` | 两层 backdrop 叠加 | example 复刻（E1），保留 |
| 27 | `ui/GlassScope.kt` | 页面玻璃作用域 + `PageTopBar` + `ContentBackdropBox` | 合规组合；含 W4b（:259/282-284） |
| 28 | `ui/GlassSupport.kt` | 玻璃唯一决策点 | 合规（N6；`isSoftwareRenderer` 已删，勿回退） |
| 29 | `ui/LicenseCatalog.kt` | 许可清单数据 | 业务，保留 |
| 30 | `ui/PageChrome.kt` | 页面骨架 + 滚动/内边距 + 底栏留白 | 合规组合 + example 复刻（E3/E4/E10）；`rememberBottomBarClearance` 见 S1 |
| 31 | `ui/ProgressButton.kt` | 带进度填充的按钮（生态移植） | 库无能力（N5），保留；含 W5 |
| 32 | `ui/SystemInsets.kt` | 横向安全区 / 圆角半径 / 圆角安全边距 | **含 W1（:46-65）**；`safeBottomGap`、`rememberHorizontalSafeInsets` 保留（N3） |
| 33 | `ui/ToolCatalog.kt` | 10 个工具的元数据 + Glass 图标 | 业务，保留 |
| 34 | `ui/Widgets.kt` | 卡片/行/下拉/预览/主操作条/颜色 | 合规为主；含 W2（:417）、W6（:595-597）、N1/N2/N3 |
| 35 | `ui/bg/BgEffect.kt` | OS3 动态渐变背景 | example 复刻（E8），保留 |
| 36 | `ui/bg/BgEffectShader.kt` | AGSL 着色器源码 | example 复刻（E8），保留 |

## 11. 按收益排序的替换清单

### 必须改（真轮子 / 抄默认值，全部"低风险 + 可直接落到代码"）

| 顺序 | 项 | 动作 | 文件:行号 | 净变化 | 工作量 | 风险 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **W1 屏幕圆角** | 删 `rememberDeviceCornerRadius`，改调 `rememberNavSystemCornerRadius()` | 删 `ui/SystemInsets.kt:46-65`（另删 5 个随之失效的 import）；改 `App.kt:226`、`Widgets.kt:494` | −20 行 | 15 min | 低 |
| 2 | **W4a/W4b 抄默认值** | 删除显式传入的库默认值 | `App.kt:229`、`App.kt:231`；`ui/GlassScope.kt:258-259, 282-284` | −8 行 | 15 min | 低 |
| 3 | **W2 卡内画布圆角** | `.clip(RoundedCornerShape(12.dp)).background(pageColor())` → `.squircleSurface(pageColor(), 16.dp)`（或保留 12dp） | `ui/Widgets.kt:413-421`、`screens/ToolScreen.kt:437-443`；同步 `Widgets.kt:176` KDoc | −2 行 + 视觉一致 | 30 min | 低 |
| 4 | **W3 颜色选择行** | 方案 B（先用 `BasicComponent` 承载，删手抄行布局）→ 再评估方案 A（换官方 `ColorPalette`） | `screens/ToolScreen.kt:341-405` | −30 ~ −60 行 | 0.5–1 h | 低（B）/ 中（A） |
| 5 | **W6 指示器参数** | 吃默认值或引用 `ProgressIndicatorDefaults.*` | `ui/Widgets.kt:593-598` | ±3 行 | 10 min | 低 |
| 6 | **W5 ProgressButton 常量** | `ButtonDefaults.CornerRadius` / `ButtonDefaults.InsideMargin`；轮廓差异写清 | `ui/ProgressButton.kt:55-58, 64` | ±4 行 | 20 min | 低 |

### 建议改（去耦 / 清理，不属于"轮子"但同源风险）

| 顺序 | 项 | 动作 | 文件:行号 | 收益 | 风险 |
| --- | --- | --- | --- | --- | --- |
| S1 | `rememberBottomBarClearance` 是"全项目唯一复刻库内部公式" | 给 `GlassNavigationBar` 传 `defaultWindowInsetsPadding = false`，由 `App.kt` 自己 `padding(bottom = floatingMargin)` 下发，`clearance = GlassNavigationBarDefaults.Height + floatingMargin`。这样公式变成**我们拥有的**，上游改内部规则不再影响（只需跟 `Height`，而它已经是公开常量）。注意：底栏只在非宽屏显示，横屏（宽屏）不挂载，故 `displayCutout` 横向 padding 的需求可忽略 | `ui/PageChrome.kt:73-86`、`App.kt:320-343` | 消除最脆弱的一处"上游镜像" | 中（首帧/转动需回归） |
| S2 | `AnimatedTheme` 的 `LocalContentColor` 补丁 + 两处 `selectedColor` 手算 | 改用 `MiuixTheme(controller)` 并把插值色写回 `controller.lightColors/darkColors`（Monet+keyColor 档需另议） | `ui/AnimatedTheme.kt:63-68`、`App.kt:333-337/405-409` | 去掉 2 处补丁、`isDynamicColor` 重新可用 | 中（Monet 档行为差异） |
| S3 | `pageColor()` 注释理由过时 | 改注释；浅色档直接用 `surface`，深色/AMOLED 才推导 | `ui/Widgets.kt:91-110` | 减少"自定义推导"面积 | 低 |
| S4 | example 复刻的升级核对项 | 见 §2 建议动作 1–3 | `docs/upgrade-prep-customization-inventory.md` | 防 E1–E8 静默漂移 | 低 |

### 不要动（合规组合 / 库无能力 / 业务）

- `ui/Widgets.kt` 的 `GroupCard` / `GroupHeader` / `SwitchRow` / `SliderRow` / `DropdownRow` / `LeadingIcon` / `NoteText` / `IconSizes`（薄封装官方 `Card` / `SmallTitle` / `SwitchPreference` / `SliderPreference` / `GlassDropdownPreference` / `Icon`）。
- `GlassCard`（官方 `Card` + 官方 `textureBlur`，与官方 example 同款）。
- `ui/PageChrome.kt` 的 `Os4ListPage` 骨架、`pageScrollModifiers`、`pageContentPadding`（官方组件组合 + 项目内边距契约）。
- `ui/Adaptive.kt` 的 rail inset / 标题透明度契约（函数式下发，量测期读取，是性能正确写法）。
- `ui/BackTransitions.kt`（`navGraphicsTransition` 官方积木）。
- `ui/SystemInsets.kt` 的 `safeBottomGap` / `rememberHorizontalSafeInsets`（库无此几何）。
- `ui/bg/*`、`ui/CombinedBackdrop.kt`（官方 example 复刻，库不提供）。
- `ui/GlassSupport.kt` 的 `LocalGlassEnabled` 单点决策与 `isGlassSupported()`。
- `ui/ProgressButton.kt` 的进度填充实现（库无能力）。
- `screens/*` 里的行式列表、弹层、空态、结果卡（全部官方组件组合）。
- `core/*`、`task/*`、`data/*`、`AppState.kt`、`Route.kt`、`GifApplication.kt`、`MainActivity.kt`、`ui/AppInfo.kt`、`ui/LicenseCatalog.kt`、`ui/ToolCatalog.kt`、`ui/ButtonColorPreview.kt`（业务 / 元数据 / 调试页）。

## 12. 附：本报告引用的官方 API 速查

| API | 位置 |
| --- | --- |
| `rememberNavSystemCornerRadius()` | `miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/core/NavSystemCornerRadius.kt:18`（android actual `…/NavSystemCornerRadius.android.kt:24-45`） |
| `Modifier.squircleSurface(color, cornerRadius)` | `miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquircleBackground.kt:134`（KDoc 于 `:125`） |
| `ColorPalette(...)` / `ColorPicker(...)` | `miuix-ui/.../basic/ColorPalette.kt:64` / `ColorPicker.kt:69` |
| `BasicComponent(...)` / `BasicComponentDefaults` | `miuix-ui/.../basic/Component.kt:59/124`、`:287-293`（56dp 最小行高 `:171`） |
| `CardDefaults` / `ButtonDefaults` / `SmallTitleDefaults` | `Card.kt:187-198` / `Button.kt:152-175` / `SmallTitle.kt:40-43` |
| `ProgressIndicatorDefaults` | `miuix-ui/.../basic/ProgressIndicator.kt:389-407` |
| `GlassTopAppBarDefaults` / `GlassTopAppBar(...)` | `miuix-glass/.../GlassTopAppBar.kt:76-163` / `:348-375` |
| `GlassNavigationBarDefaults` / 底栏外边距公式 | `miuix-glass/.../GlassNavigationBar.kt:119-186` / `:319-324` |
| `Modifier.glass(...)` / `Modifier.glassPanel(...)` | `miuix-glass/.../Glass.kt:65` / `GlassPanel.kt:36` |
| `GlassShape(cornerRadius)` / `GlassDefaults` | `miuix-material/.../GlassShape.kt:95` / `GlassDefaults.kt` |
| `GlassMotion` | `miuix-glass/.../GlassMotion.kt:18-368` |
| `NavDisplayEffects` / `NavTransitions` | `miuix-nav/.../core/NavDisplayEffects.kt:63-69` / `…/transition/NavTransitions.kt:43/66/79` |
| `MiuixTheme(controller)`（含 `LocalContentColor` / `LocalColorSchemeMode`） | `miuix-ui/.../theme/MiuixTheme.kt:24-44` |
| `Colors` 各 role 取值 | `miuix-ui/.../theme/Colors.kt:341-394`（light）、`:451-504`（dark） |
| `TextStyles` 字号 | `miuix-ui/.../theme/TextStyles.kt:146-230` |
| 官方 example 参照 | `example/shared/src/commonMain/kotlin/utils/PageUtils.kt`、`utils/AdaptiveUtils.kt`、`component/liquid/CombinedBackdrop.kt`、`component/effect/*`、`component/GroupedCardItems.kt`、`AppContent.kt`、`AboutPage.kt`、`component/BackNavigationIcon.kt`、`component/GlassBottomSheetDemo.kt`、`GlassPage.kt` |

## 13. 补记：`b00169ba` 轮（1.14，2026-11-22）

本轮先按 `docs/miuix-glass-migration.md`（YuKongA/Mishka 的 OS4 Glass 迁移指南）把 6 条工程红线
与组件替换表逐条对了一遍，再补做三处替换。§1 的 W1–W6 在本轮之前已全部落地
（`squircleSurface`、`rememberNavSystemCornerRadius`、`BasicComponent` 承载颜色行、
删抄来的库默认值、`ButtonDefaults`/`ProgressIndicatorDefaults` 常量）。

### 13.1 本轮新修（3 处）

| # | 位置 | 修前 | 修后 | 判据 |
| --- | --- | --- | --- | --- |
| N1 | `screens/ToolScreen.kt` `ColorSwatches` | 自绘 5 个圆形色块（`clip(CircleShape)` + `border(2dp/1dp)` + 手调 `ToggleOn` 触觉 + 自补 `semantics`）——即 §1 W3 的"方案 B"里仍然留给自己的那一层 | 官方 `ColorPalette`，独立卡 + 16dp 内边距 + 全宽 + `showPreview = false` | 官方 `component/ColorPickerSection.kt:72` 的用法；库的官方颜色选择组件就是它（无"色卡行"组件，所以之前的自绘确实是补库缺口，但**选中语义/触觉/轮廓**不必自己写） |
| N2 | `ui/PageChrome.kt` `isScrolled` | `computedStateOf { listState.canScrollBackward }` | 叠加 `\|\| scrollBehavior.state.overscrollOffset < -1f` | 官方 `GlassPage.kt:176` / `IconPage.kt:182` 逐字一致；指南 §5.4 要求阈值 `-1f` 防亚像素误触发 |
| N3 | `ui/Adaptive.kt`、`App.kt`、`ui/Widgets.kt` | 三处各读 `LocalWindowInfo.current.containerSize` | `App` 根部 `BoxWithConstraints` 注入 `LocalContainerSize`，`containerSize()` 优先读它、回退窗口 | 官方 `utils/AdaptiveUtils.kt:14-46` + `App.kt:89-100`；指南 §6「容器尺寸基准」 |

### 13.2 复核后仍然保留（并给出理由，防止下一轮再翻一遍）

- `ui/Widgets.kt` 的 `GlassCard`：**不要**与 `miuix-glass` 新增的 `GlassCard` 混淆。
  前者采页面背景画材质（`textureBlur`，官方 `AboutPage.kt:347-360` 同款），
  后者是"放在玻璃面板内部的半透明卡"（KDoc 明确说它不采样、不模糊）。语义不同，不能替换。
- `ui/ProgressButton.kt`：库无进度按钮。容器已是官方 `squircleSurface` + `semantics(Role.Button)`。
- `ui/PageChrome.kt` `rememberBottomBarClearance()`：仍镜像 `GlassNavigationBar` 未导出的
  `floatingMargin`。改成 `defaultWindowInsetsPadding = false` 会改变底栏视觉位置（本轮明确不动）。
- `ui/AnimatedTheme.kt`：`LocalColorSchemeMode` 仍是 internal，主题过渡只能自己插值。
- `ui/SubtitleField`：`bringIntoViewRequester` 是 Compose 框架 API，配合官方 `LocalBringIntoViewSpec` 使用；
  唯一的偏差点是 `ime` insets 在组合期读取（指南 §5.2 的"禁止组合期读帧率级状态"），
  但改成布局期读取会把 IME 逐帧 follow 的逻辑拆散，收益小于回归风险，暂列技术债。

### 13.3 指南其余条目对账（本轮结论：已满足）

| 指南条目 | 现状 |
| --- | --- |
| §2.1 录制层先铺不透明底色 | `rememberPageBackdrop(opaqueBase = true)` 里 `drawRect(pageBaseColor())` |
| §2.1 禁止自采样 | 顶栏/底栏/Rail 都是录制层的兄弟节点（`App.kt`、`ContentBackdropBox`） |
| §2.2 两级采样宿主 | 页面层 `LocalPageBackdrop` + 一级页 `LocalRootBackdrop`（Pager 外，真实窗口坐标） |
| §2.3 无模糊降级 | `isGlassSupported()` → `isRuntimeShaderSupported()`，`backdrop = null` 由组件回退 |
| §3 组件替换表 | 顶栏 / 底栏 / Rail / 图标 / 下拉 / Dialog / BottomSheet 全部官方 Glass 组件 |
| §3 圆角与材质 | 未覆盖 `shape`；`GlassSurface` 用库默认 |
| §4.1 Rail 层级与跨层弹窗 | Rail 在 `NavDisplay` 外层 `Box`，弹层由最外层 `Scaffold` 托管 |
| §4.2 背景全屏、内容避让 | `LocalRailInset` / `LocalTopBarTitleAlpha` / `LocalTopAppBarCenterObstructionStart` 三个 lambda |
| §5.1 `layerBackdrop` 先于 inset/padding | `App.kt`、`ContentBackdropBox` 都是 `.fillMaxSize().then(layerBackdrop)` |
| §5.2 禁止组合期读逐帧状态 | 除 `SubtitleField` 的 IME 判据外，其余均走 lambda / `layout` / `graphicsLayer` |
| §5.3 起始缩进不重复 | `pageContentPadding` 只让 start 一侧，`railStartInset` 由 `base` 带安全区 |
| §6 `computedStateOf` / `CapturedAnimatedVisibility` | 已用 `computedStateOf`；无 `AnimatedVisibility` 调用点 |
| §6 `TextFieldState` / `maxLength*` | `TextField` 已用 `TextFieldState`；无废弃 `maxLength` |
| §7 图标 28dp / `GlassTextField` 于 Dialog 内 | Glass 图标统一 `IconSizes.Action = 28.dp` / `Row = 28.dp`；弹层内无输入框 |
