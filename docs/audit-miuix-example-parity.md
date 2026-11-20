# GifHelper × MIUIX 官方 example 对齐审计（基线 5ff41ba9）

> 判定标准：**MIUIX 官方 example / docs demo / 组件源码本身**（`C:\Users\10487\WorkBuddy\gif\miuix`，HEAD `5ff41ba9`）。
> 用户已把 `miuix/example/android` 编译成 APK，作为"渲染出来的真值"对照。
> Glacier 仅作为"同为下游应用如何落地"的旁证被引用，**不作为判定依据**。

> ⚠️ **快照说明（本报告生成后又有一轮修正）**：报告正文对 `FileActionSheet` 的描述是审计时的状态。
> 随后按"只以 MIUIX 为准"的口径又改了三处：① 删掉 **脚注行**（MIUIX 官方弹层示例没有这个结构，
> 它是下游项目 Glacier 的写法）；② 行图标从 24dp 改为 **28dp**（MIUIX example 里 Glass 图标的统一尺寸：
> `GlassPage.kt:325/359/372`、`IconPage.kt:266`、`PullToRefreshPage.kt:121`）；③ 头部不加
> `GlassIconButton`（官方参数可选，本项目动作都在行内）。判定依据始终是 MIUIX，Glacier 不参与判定。

> 性质：只读审计，除本文件外未改任何文件。
> 快照说明：审计期间还有并行改动落在工作区（`App.kt`、`AnimatedTheme.kt`、`LibraryScreen.kt`、`ToolScreen.kt`、`Widgets.kt`、`AboutScreen.kt`、`GlassSupport.kt`、`PageChrome.kt`、`GlassScope.kt`）。本报告写到"当前工作区"状态；标 ✅ 的项表示并行改动已按官方写法修好。

---

## 0. 结论摘要（10 条，按对"像不像官方 example"的影响排序）

1. **所有列表页都缺官方新基线的 `VerticalScrollBar`。** 官方 example 每个 LazyColumn 页面都挂了 `VerticalScrollBar(adapter, trackPadding, topAppBarScrollBehavior)`：`MainPage.kt:341-346`、`ComponentPage.kt:176-181`、`SettingsPage.kt:506-511`、`AboutPage.kt:447-452`、`IconPage.kt`（页内 pager 列表）。GifHelper 全项目 0 处 → 与 example APK 并排一眼能看出差别。**P0，工作量小**（§3.1）。
2. **非玻璃顶栏没有跟随 blur 的"渐变带"。** 官方非玻璃档不是纯色条：`BlurredBar` 会在顶栏区域铺一层 `textureBlur(blurRadius = 25f, blend surface 0.8)`（`utils/PageUtils.kt:310-337`），页面 Scaffold 的 topBar 颜色传 `Transparent`（`ComponentPage.kt:145-153`、`SettingsPage.kt:105-117`、`MainPage.kt:203-207`）。GifHelper 玻璃关掉后是实色 `TopAppBar`（`GlassScope.kt:291-303`）→ 观感从"OS4 磨砂条"退化成"普通条"。**P1，工作量小**（§3.2）。
3. **`centerObstructionStart` 没接。** 官方 `AdaptiveTopAppBar` 把 `LocalTopAppBarCenterObstructionStart` 传给 Glass/普通两档顶栏（`PageUtils.kt:250,270,291`），值由折叠态 Glass Rail 的 `collapsedStart` 算出（`AppContent.kt:226-232,263`）。GifHelper 的 `PageTopBar`（`GlassScope.kt:149-305`）两个分支都没传 → 手机横屏"折叠 Rail 顶部胶囊"可能压住居中标题。**P1，工作量小**（§3.3）。
4. **底栏结构偏离官方 `Scaffold.bottomBar` 槽。** 官方 `CompactScreenLayout` 是 `Scaffold(contentWindowInsets = safeInsets, bottomBar = { NavigationBar(...) })`（`AppContent.kt:654-700`），页面内容用 `innerPadding` 让位；GifHelper 把 `GlassNavigationBar` 画在 `entry<Route.Root>` 的 Box 里当浮层（`App.kt:320-344`），再用 `LocalBottomBarClearance`（`PageChrome.kt:73-86`）**复刻库内部 floatingMargin 公式**来给内容留位。功能等价但目前属于"抄库内部公式"，官方不需要抄。**P1（结构性），工作量中**（§3.4）；保留也可，但要接受这条维护成本。
5. **页面 insets 责任划分与官方不同。** 官方：`GlassPageHost` 用 `Scaffold(contentWindowInsets = WindowInsets(0))`（`GlassPage.kt:702-711`），各页 Scaffold 用 `LocalAppPageInsets`（`PageUtils.kt:66-120`），顶栏统一 `defaultWindowInsetsPadding = false` + `appPageTopBarInsets()`（`PageUtils.kt:128-132,246,261,271`）。GifHelper：host Scaffold 同样透明+0 insets（`App.kt:215-218` ✅），但页面 Scaffold 不传 `contentWindowInsets`、顶栏也不传 `defaultWindowInsetsPadding = false`（默认 true）→ 靠库默认行为，横屏/挖孔/大字体下与 example 的 inset 叠加方式不同。**P2，需实测对照**（§3.5）。
6. **行内图标尺寸与官方不一致。** 官方 Glass 族图标在列表行里统一 **28dp**（`IconPage.kt:229`、`BadgeSection.kt:48/55/62/69`），extended 族图标走 Icon 默认 24dp 且外面包 `Box(Modifier.padding(end = 8.dp))`（`component/ArrowSection.kt:51-60`）。GifHelper `IconSizes.Row = 26dp`（`Widgets.kt` 的 IconSizes）给所有行图标用 → 比 example 小一档。**P2，工作量小**（§4.1）。
7. **作品操作面板（FileActionSheet）已按官方 example 的"行 + 脚注 + 底部 inset"结构重写（✅ 并行改动）**：当前是 `Column { SheetActionRow ×3; 脚注(footnote2, 左缘对齐 BasicComponent 内边距); Spacer(navigationBars + captionBar) }`。与官方 `component/BottomSheetSection.kt:199-268` 相比剩余 2 个判定点：① 官方 example 的行是 `Card + SwitchPreference` 分组，官方 **glass** 弹层 demo 则是行直接落在面板上——当前代码选择后者（注释已写明理由），**判定：正确**；② 行图标用 `MiuixIcons.Glass.X`（28dp 画布）却写 24dp，按 example 的图标尺寸证据（Glass 行 28dp / 通用图标 28dp / extended 24dp+8dp 间距）应改 28dp；③ 官方 docs demo 的头部有 `endAction = GlassIconButton(...)`，当前工作区已移除，属可选收口动作。**P2，小**（§5）。
8. **关于页与官方 `AboutPage` 的剩余差异**：官方图标**不做** `textureBlur`（88dp 白底圆角 + 74dp launcher，`AboutPage.kt:242-261`）；标题 `textureBlur` 参数已按官方对齐 ✅（`AboutScreen.kt` 现在的 `RoundedCornerShape(16.dp)` / `BlurDefaults.MaxBlurRadius`）；官方卡片用 `Card + textureBlur(cardBlend = ColorBlendToken.Overlay_Thin_Light/Pured_Regular_Light)` + `defaultColors(Transparent, Transparent)`（`AboutPage.kt:205,347-395`），GifHelper `GlassCard` 用 `surface@0.6` + 只传 `color`（`Widgets.kt:191-223`）；官方入口行的附加信息放 `endActions`（如 "GitHub"/"Apache-2.0" 文本，`AboutPage.kt:373-441`），GifHelper 放 `summary`。**P2，工作量小**（§6）。
9. **主题装配已按官方 `AppTheme` 修好（✅ 并行改动）**：官方永远 `MiuixTheme(controller = controller)`（`ui/Theme.kt:62-66`），从而提供 `LocalColorSchemeMode`；GifHelper 现在外层 controller + 内层 animated colors（`AnimatedTheme.kt`），既保留逐色过渡又让 `dynamicGlassMaterial` 生效。**后续只需回归验证**（§7）。
10. **官方没有对应物、应保留的 GifHelper 定制**：`Os4ListPage` 的 `pageOverlay`/`largeHeader`/`background` 三参数骨架、`PrimaryActionBar`+`ProgressButton`、预览舞台、空态、`NoteText`、`pageContentPadding` 的 clearance 模型（在底栏保持浮层方案的前提下）。这些不属于"未对齐"（§8）。

---

## 1. 官方 example 的"标准写法"速查（判定依据）

| 主题 | 官方位置 | 标准写法 |
| --- | --- | --- |
| 二级列表页骨架 | `ComponentPage.kt:127-184` | `Scaffold(modifier = Modifier.imePadding(), topBar = { BlurredBar(backdrop, backdrop != null) { AdaptiveTopAppBar(...) } }, snackbarHost = ...) { innerPadding -> val contentPadding = pageContentPadding(innerPadding, padding, extraTop); Box(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) { LazyColumn(modifier = Modifier.pageScrollModifiers(enableScrollEndHaptic, showTopAppBar, scrollBehavior), contentPadding = contentPadding) { content(); item { Spacer(12.dp) } }; VerticalScrollBar(adapter, Modifier.align(CenterEnd).fillMaxHeight(), trackPadding = contentPadding, topAppBarScrollBehavior = scrollBehavior.takeIf { showTopAppBar }) } }` |
| 一级页（手机） | `AppContent.kt:653-700` | `Scaffold(modifier = fillMaxSize(), contentWindowInsets = LocalAppPageInsets.current.safeInsets, bottomBar = { NavigationBar(...) }) { innerPadding -> Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { pagerContent(innerPadding, Modifier.imePadding()) } }`；backdrop = `rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }` |
| 一级页（宽屏） | `AppContent.kt:612-651` | `Scaffold(contentWindowInsets = LocalAppPageInsets.current.contentInsets, ...)` + Rail 覆盖；页面只吃 `PaddingValues(top/bottom)`，横向安全区由页面自己 `appPageHorizontalInsets()` |
| 顶栏 | `utils/PageUtils.kt:227-297` | 玻璃档 `GlassTopAppBar(..., defaultWindowInsetsPadding = false, contentModifier = contentModifier.then(contentInsetModifier), centerObstructionStart = LocalTopAppBarCenterObstructionStart.current, bandBrush = GlassTopAppBarDefaults.bandBrush(surface), bandOverhang = GlassTopAppBarDefaults.BandOverhang, backdrop = LocalTopAppBarBackdrop.current)`；普通档 `TopAppBar(color = Color.Transparent, modifier = Modifier.background(color).then(contentModifier).then(contentInsetModifier), defaultWindowInsetsPadding = false, centerObstructionStart = ...)` |
| 顶部模糊带 | `utils/PageUtils.kt:310-337` | 非玻璃档也铺 `Box(matchParentSize().textureBlur(backdrop, RectangleShape, blurRadius = 25f, colors = BlurColors(blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.8f))))))`，并 `CompositionLocalProvider(LocalTopAppBarBackdrop provides backdrop)` |
| 分组标题 + 卡片 | `MainPage.kt:288-337`、`component/*Section.kt` | `SmallTitle(text = group.title)` + `Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { … }` |
| 新版分段卡片（Settings/Icon 页） | `component/GroupedCardItems.kt:79-107`、`SettingsPage.kt:149-503` | `groupedCardItems(keyPrefix, items = buildList { add(CardItem(key) { … }) }, outerTopPadding, outerBottomPadding)` → `CardSegment`（相邻卡圆角逐段合并、`squircleSurface`） |
| 设置页 | `SettingsPage.kt:94-125,127-513` | `Scaffold(topBar = { BlurredBar(...) { AdaptiveTopAppBar(title, subtitle = "v… (…)", color = barColor) } }) { SettingsContent(pageContentPadding(innerPadding, padding), ...) }`；行一律 `SwitchPreference` / `GlassDropdownPreference(backdrop = backdrop, ...)` / `ArrowPreference`，`CardItem` 分组 |
| 弹层（玻璃） | `docs/demo/GlassBottomSheetDemo.kt:73-111`、`docs/demo/GlassDialogDemo.kt:65-91`、`GlassPage.kt:409-449` | `GlassBottomSheet(show, backdrop, title, onDismissRequest, endAction = { GlassIconButton { Icon(MiuixIcons.Glass.Ok/Close, tint = onBackground) } }) { Column(fillMaxWidth().heightIn(max).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { … } }`；`GlassDialog(title, summary, onDismissRequest) { Row(spacedBy(12.dp)) { GlassDialogButton(weight(1f), role = Normal/Primary/Destructive) } }` |
| 弹层（普通 example） | `component/BottomSheetSection.kt:115-145,199-268` | `OverlayBottomSheet(title, show, allowDismiss, enableNestedScroll, dimColor = windowDimming.copy(alpha = 0.2f), startAction = IconButton(Close), endAction = IconButton(Ok)) { LazyColumn(fillMaxWidth().scrollEndHaptic().overScrollVertical()) { item { SmallTitle(insideMargin = PaddingValues(16.dp, 8.dp)); Card(padding(bottom = 12.dp), colors = defaultColors(secondaryContainer)) { SwitchPreference(...) } }; item { …; Spacer(navigationBars + captionBar) } } }` |
| 图标尺寸 | `IconPage.kt:229,264-271`、`GlassPage.kt:324-330,354-364`、`ArrowSection.kt:51-60` | Glass 族 28dp（行、顶栏动作、导航条动作）；extended 族 Icon 默认 24dp（不写 size），行首图标外包 `Box(Modifier.padding(end = 8.dp))` |
| 主题 | `ui/Theme.kt:23-68` | `ThemeController(colorSchemeMode = ...)` → `MiuixTheme(controller = controller, textStyles = ...)`（由此提供 `LocalColorSchemeMode`，动态玻璃材质才会生效） |
| 滚动修饰符 | `utils/PageUtils.kt:171-186` | `scrollEndHaptic()` + `overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)` + `nestedScroll(behavior.nestedScrollConnection)`（后两者受 showTopAppBar 开关控制）+ `fillMaxHeight()` |

> docs/demo 与 example 有差异时以 **example 为准**；本文两处都给出。

---

## 2. GifHelper 页面 ↔ 官方 example 对照矩阵

| GifHelper 页面/层 | GifHelper 位置 | 官方等价物 | 结论 |
| --- | --- | --- | --- |
| 一级页骨架（Pager + 底栏 + 弹层宿主） | `App.kt:143-240,241-346` | `AppContent.kt:582-700`（`Home` → `Scaffold` → `CompactScreenLayout`）、`GlassPage.kt:702-711`（Host） | host 已对齐（透明 + `WindowInsets(0)`）✅；底栏槽位/insets 见 §3.4 |
| 主页工具列表 | `HomeScreen.kt:20-48` | `MainPage.kt:288-337`（SmallTitle + Card + ArrowPreference） | 结构对齐 ✅；行图标尺寸/有无图标不同（§4.1） |
| 作品列表 | `LibraryScreen.kt` 的作品列表段（`Os4ListPage` content） | `MainPage.kt:311-326`、`ComponentPage` 列表 | 结构对齐；空态为官方无对应（保留） |
| 设置首页 | `SettingsScreen.kt:22-64` | `SettingsPage.kt:94-125`（title + subtitle 版本号，`SmallTitle` 分组） | 对齐 ✅ |
| 主题设置 | `ThemeScreen.kt:29-134` | `SettingsPage.kt:127-513`（SwitchPreference / GlassDropdownPreference 直接分行） | 用的是 `SwitchRow`/`DropdownRow` 薄封装 → 合规；`DropdownRow` 的 backdrop 选择偏离（§4.3） |
| 工具页 | `ToolScreen.kt:78-222` | `ComponentPage.kt:127-184` 骨架 + `component/*Section.kt` 行写法 | 骨架缺滚动条（§3.1）；行写法基本对齐；`PrimaryActionBar` 官方无对应（保留） |
| 关于页 | `AboutScreen.kt:98-306` | `AboutPage.kt:85-497` | 版式差异见 §6；textureBlur 参数已对齐 ✅ |
| 许可页/许可正文 | `LicensesScreen.kt:31-142` | 官方无等价页；分组/行参照 `SettingsPage` | 保留；`NoteText` 用 footnote1 + 28dp 缩进，与官方 SmallTitle/Card 对齐关系一致 |
| 页面骨架 `Os4ListPage` | `PageChrome.kt:178-354` | `ComponentPage.kt:127-184` + `PageUtils.kt` | 缺 `VerticalScrollBar`（P0）、`snackbarHost` 槽（P2）、insets 责任划分（P2） |
| 顶栏 `PageTopBar` | `GlassScope.kt:149-305` | `PageUtils.kt:227-297` + `component/BackNavigationIcon.kt:22-46` | 返回键已对齐 ✅；非玻璃模糊带（P1）、`centerObstructionStart`（P1）、`defaultWindowInsetsPadding`（P2）见 §3.2/3.3/3.5 |
| 底栏 | `App.kt:320-344` | `AppContent.kt:581-627,702-846` | 官方在 `bottomBar` 槽；GifHelper 浮层 + clearance（§3.4） |
| 侧栏 Rail | `App.kt:398-413` | `AppContent.kt:512-580`（`PersistentGlassNavigationRail`） | GifHelper 默认 insets；官方 `defaultWindowInsetsPadding = false` + `windowInsetsPadding(LocalAppPageInsets.windowInsets)`（§3.5，P2） |
| 作品操作面板 | `LibraryScreen.kt` 的 `FileActionSheet`（快照约 :246-289，含 `SheetActionRow` :297-317） | `component/BottomSheetSection.kt:199-268` + `docs/demo/GlassBottomSheetDemo.kt:73-111` | 已按 example 结构重写 ✅（行 + 脚注 + 底部 nav/captionBar inset）；剩余行图标尺寸/可选头部动作（§5，P2） |
| 删除确认弹层 | `LibraryScreen.kt` 的 `GlassDialog` 调用（快照约 :126-161） | `docs/demo/GlassDialogDemo.kt:65-91`、`GlassPage.kt:409-441` | 对齐 ✅（取消按钮未显式 `role = Normal`，默认相同，P3） |
| 下拉 | `Widgets.kt:304-327` | `SettingsPage.kt:165-207`（`GlassDropdownPreference(backdrop = backdrop, …)`） | 官方传页面 backdrop；GifHelper 传 `LocalRootBackdrop ?: LocalPageBackdrop`（有实测理由，保留并核对） |
| 分组标题/卡片 | `Widgets.kt:64-170` | `component/*Section.kt`、`GroupedCardItems.kt` | `GroupHeader` = `SmallTitle` ✅；`GroupCard` = `Card(12dp 边距)` ✅；新版 `CardSegment` 可选（§4.4） |
| 主题装配 | `AnimatedTheme.kt` | `ui/Theme.kt:23-68` | 已改成 controller 包 colors ✅ |
| 弹层宿主 | `App.kt:215-218` | `GlassPageHost`（`GlassPage.kt:702-711`） | 对齐 ✅ |

---

## 3. P0 / P1 偏离与可直接照抄的改法

### 3.1 【P0】所有列表页缺 `VerticalScrollBar`（官方 example 每个列表都有）

现状：`Os4ListPage` 的 body 只有 `ContentBackdropBox { largeHeader; LazyColumn }`（`PageChrome.kt:283-321`），全项目无 `VerticalScrollBar` / `rememberScrollBarAdapter`（grep 0 命中）。
官方：`MainPage.kt:341-346`、`ComponentPage.kt:176-181`、`SettingsPage.kt:506-511`、`AboutPage.kt:447-452` 全部是同一段：

```kotlin
VerticalScrollBar(
    adapter = rememberScrollBarAdapter(lazyListState),
    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    trackPadding = contentPadding,
    topAppBarScrollBehavior = topAppBarScrollBehavior.takeIf { appState.showTopAppBar },
)
```

改法（`ui/PageChrome.kt`）：`Os4ListPage` 里 `MiuixScrollBehavior()` 已有（`PageChrome.kt:229`），把 `LazyColumn` 的 `contentPadding` 提到局部变量复用，然后在 `ContentBackdropBox` 内、`LazyColumn` 之后加滚动条：

```kotlin
// PageChrome.kt 顶部（@file 级）
@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)
```

```kotlin
// import
import androidx.compose.foundation.layout.fillMaxHeight
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter

// body 内
val contentPadding = pageContentPadding(
    innerPadding = padding,
    extraBottom = extraBottom,
    horizontal = horizontalPadding,
    reserveNavBar = reserveNavBar,
)
ContentBackdropBox(glass) {
    /* largeHeader 不变 */
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
        contentPadding = contentPadding,
    ) {
        if (largeHeader != null) { item(key = "logoSpacer") { /* 不变 */ } }
        content()
    }
    // 官方例：MainPage.kt:341-346 / ComponentPage.kt:176-181
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        trackPadding = contentPadding,
        topAppBarScrollBehavior = scrollBehavior,
    )
}
```

注意：`VerticalScrollBar` 放在 `ContentBackdropBox` 内 → 会被录进页面录制层（官方也是这么放的：`Box(layerBackdrop){ LazyColumn; VerticalScrollBar }`），这是正确的；不要放到录制层外面，否则滚动条不会随玻璃一起被采样。

### 3.2 【P1】非玻璃顶栏缺官方 `BlurredBar` 的 textureBlur 带

现状：`GlassScope.kt:290-303`（非玻璃分支）直接 `TopAppBar(color = background ?: surface, ...)` — 实色条。
官方：`utils/PageUtils.kt:310-337`：

```kotlin
@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val glassStyle = LocalAppState.current.topAppBarStyle == 1
    val blurActive = blurEnabled && backdrop != null
    Box {
        if (blurActive && !glassStyle) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = barBlurColors(),   // surface.copy(alpha = 0.8f) 单层
                    ),
            )
        }
        CompositionLocalProvider(LocalTopAppBarBackdrop provides backdrop) {
            content()
        }
    }
}
```

并且页面把 topBar 的 `color` 设成 `if (backdrop != null) Color.Transparent else surface`（`ComponentPage.kt:151`、`SettingsPage.kt:102`、`MainPage.kt`）。GifHelper 的对应改法（`GlassScope.kt` 非玻璃分支外面包一层）：

```kotlin
// import
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur

} else {
    val pageBackdrop = glass.contentBackdrop   // 已在函数里可用
    val blurActive = pageBackdrop != null
    Box {
        if (blurActive) {
            Box(
                Modifier.matchParentSize().textureBlur(
                    backdrop = pageBackdrop,
                    shape = RectangleShape,
                    blurRadius = 25f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f))),
                    ),
                ),
            )
        }
        TopAppBar(
            title = title,
            modifier = contentInset,
            // 官方：有 backdrop 时条本身透明，让 textureBlur 带露出来
            color = if (blurActive) Color.Transparent else (background ?: MiuixTheme.colorScheme.surface),
            /* 其余参数不变 */
        )
    }
}
```

> 判定依据：这是 example 默认观感的一部分（"Original 顶栏"也会糊），不是可选皮肤。若 GifHelper 的"玻璃材质"开关语义就是"关掉=完全不糊"，则可保留，但需与 example APK 截图对照后明确记录取舍。建议：**玻璃开关只关"玻璃胶囊/导航条材质"，顶栏 blur 带仍保留**（官方 original 模式就是这样）。

### 3.3 【P1】`centerObstructionStart` 未接（折叠 Rail 顶部胶囊 × 居中标题）

现状：`GlassScope.kt:260-289`（GlassTopAppBar）与 `:291-303`（TopAppBar）都没有 `centerObstructionStart`；`App.kt` 只有 `LocalTopBarTitleAlpha`（`App.kt:148-150`）。
官方：`AppContent.kt:226-232` 计算：

```kotlin
val topAppBarCenterObstructionStart = if (
    useGlassSidebar && !sidebarState.expanded && sidebarState.collapsedWidth > 0.dp
) sidebarState.collapsedStart else Dp.Unspecified
// 然后 LocalTopAppBarCenterObstructionStart provides topAppBarCenterObstructionStart（AppContent.kt:263）
// 顶栏：centerObstructionStart = LocalTopAppBarCenterObstructionStart.current（PageUtils.kt:250,270,291）
```

改法：
1. `App.kt` 增加 `val LocalTopAppBarCenterObstructionStart = staticCompositionLocalOf { Dp.Unspecified }`（可放 `ui/GlassScope.kt` 与 `LocalTopBarTitleAlpha` 同处），并在 `App.kt:143-152` 的 provider 里下发：

```kotlin
val topBarCenterObstruction = if (railVisible && !railState.expanded && railState.collapsedWidth > 0.dp) {
    railState.collapsedStart
} else {
    Dp.Unspecified
}
// CompositionLocalProvider(..., LocalTopAppBarCenterObstructionStart provides topBarCenterObstruction)
```

2. `GlassScope.kt` 两个顶栏调用都传 `centerObstructionStart = LocalTopAppBarCenterObstructionStart.current`。

### 3.4 【P1/结构性】底栏：官方在 `Scaffold.bottomBar` 槽，GifHelper 用浮层 + 复刻公式

现状：`App.kt:320-344` 的 `GlassNavigationBar` 画在 `Box` 里 `align(BottomCenter)`；内容让位靠 `PageChrome.kt:73-86` 的 `rememberBottomBarClearance()`（复刻 `GlassNavigationBar.kt:318-323` 的 floatingMargin 公式）。
官方：`AppContent.kt:653-700`：

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = LocalAppPageInsets.current.safeInsets,
    bottomBar = { NavigationBar(navigationItems, mainPagerState, backdrop) },
) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        pagerContent(innerPadding, Modifier.imePadding())
    }
}
```

两种落地方式：
- **A（推荐，向官方看齐）**：在 `entry<Route.Root>` 里改用一层 `Scaffold(containerColor = Transparent, contentWindowInsets = WindowInsets(0), bottomBar = { GlassNavigationBar(...) })`，内容用 `innerPadding.bottom` 让位，删掉 `LocalBottomBarClearance` 与 `rememberBottomBarClearance()`。玻璃录制层包内容、底栏作为 Scaffold 槽的子节点 → 仍是录制层兄弟（Scaffold 的 bottomBar 与 content 是兄弟），不会采样自己。二级页盖住底栏的行为不变（整页在 NavDisplay 里）。工作量：中（要核对 `pageContentPadding` 的 clearance 分支、`PrimaryActionBar` 的让位）。
- **B（保留现状）**：接受"复刻库内部 floatingMargin"这条维护成本；AGENTS 第 1/12 条已记录理由。

判定：**建议 A**，对齐 example 后可以删掉全项目唯一"复刻库内部公式"的地方（`PageChrome.kt:62-71` 注释自证的脆弱点）。

### 3.5 【P2】页面/顶栏 insets 责任划分照官方收口

官方模板（`PageUtils.kt:122-132` + `GlassPageHost`）：

```kotlin
@Composable fun Modifier.appPageHorizontalInsets(): Modifier =
    windowInsetsPadding(LocalAppPageInsets.current.contentInsets.only(WindowInsetsSides.Horizontal))

@Composable fun Modifier.appPageTopBarInsets(): Modifier =
    appPageHorizontalInsets().windowInsetsPadding(LocalAppPageInsets.current.safeInsets.only(WindowInsetsSides.Top))
```

- Host：`Scaffold(contentWindowInsets = WindowInsets(0))`（`GlassPage.kt:708`）✅ GifHelper 一致（`App.kt:215-218`）。
- 页面 Scaffold：官方 GlassPage 用 `Modifier.fillMaxSize()`，Compact 页用 `contentWindowInsets = safeInsets`，宽屏用 `contentInsets`；顶栏一律 `defaultWindowInsetsPadding = false`。
- GifHelper 现状：页面 Scaffold 不传 `contentWindowInsets`（默认 `systemBars ∪ displayCutout`），顶栏默认 `defaultWindowInsetsPadding = true` 自己吃顶部 inset；`contentModifier = Modifier.railStartInset()` 只处理 Rail 横向。

判定：这不是"错误"，但 inset 叠加方式与 example 不同，**横屏、挖孔、大字体、Rail 展开**四种情况下需要以 example APK 为准逐屏对照。若要严格对齐：在 `Os4ListPage` 传 `contentWindowInsets = WindowInsets(0)`，顶栏传 `defaultWindowInsetsPadding = false` + `contentModifier = railStartInset().appPageTopBarInsets()`（需引入官方 `LocalAppPageInsets` 的等价物）。**P2，工作量大**，建议先实测再决定。

---

## 4. 行 / 分组 / 组件级偏离

### 4.1 【P2】行首图标：26dp vs 官方 28dp（Glass 族）

| 项目 | GifHelper | 官方 |
| --- | --- | --- |
| Glass 族行图标尺寸 | `IconSizes.Row = 26dp`（`Widgets.kt:252-277`，`LeadingIcon` 默认值） | `Modifier.size(28.dp)`：`IconPage.kt:229`、`BadgeSection.kt:48/55/62/69`（连 extended 图标在 example 里也常写 28dp）、`GlassPage.kt:327/361/374/392` |
| extended 族行图标 | 同 26dp | 不写 size（Icon 默认 24dp）+ `Box(Modifier.padding(end = 8.dp))`（`component/ArrowSection.kt:51-60`） |

改法：把 `IconSizes.Row` 改成 28dp（Glass 族 28dp 画布，26 在 example 里是放大不足的一档）；如果保留 extended 族图标，则按官方包 `Box(padding(end = 8.dp))`。官方两种写法并不完全统一（IconPage 的玻璃搜索结果不加额外 padding，ArrowSection 的 extended 图标加），但 **28dp 是 example 里 Glass 行图标的唯一尺寸**，这一点没有歧义。

```kotlin
object IconSizes {
    /** 列表行首：Glass 族是 28dp 画布，官方 example 行图标统一 size(28.dp)。 */
    val Row: Dp = 28.dp
    // …其余不变
}
```

### 4.2 【P2】`SwitchRow` / `SliderRow` / `DropdownRow` 薄封装

官方 example 行是直接用 preference 组件：`SwitchPreference(title, summary, checked, onCheckedChange)`（`SwitchSection.kt:82-117`）、`SliderPreference(title, valueText, value, onValueChange)`（`ArrowSection.kt:71-81`）、`GlassDropdownPreference(backdrop = backdrop, title, items, selectedIndex, onSelectedIndexChange)`（`SettingsPage.kt:165-207`）。
GifHelper 的封装（`Widgets.kt:304-376`）只是补了可选 icon 与命名，**合规**（属于组合官方组件）。唯一偏差：`SwitchRow` 多了 `startAction` 图标能力，官方 SwitchPreference 在 example 里没带图标——用不用不影响对齐，建议只在首页/设置入口行用图标。

### 4.3 【保留】`DropdownRow` 的 backdrop 选择

GifHelper：`LocalRootBackdrop.current ?: LocalPageBackdrop.current`（`Widgets.kt:314`）。
官方：`GlassDropdownPreference(backdrop = backdrop)` 传页面 `rememberBlurBackdrop()`（`SettingsPage.kt:165`、`AboutPage.kt` 的 sheet 用 `overlayBackdrop`）。
判定：**保留** GifHelper 的写法——Pager 内页面录制层是槽位坐标（`docs/os4-round7-audit.md` §3 实测），`LocalRootBackdrop` 才是真实坐标。这是官方 example 没有 Pager 内下拉弹层的场景差异，不是偏离。

### 4.4 【P2/可选】新版分组卡片 `CardSegment`

官方最新（`d0f69485 example: Use sectioned grid cards`）在 Settings/Icon 页使用 `groupedCardItems` → `CardSegment`（`GroupedCardItems.kt:26-107`）：相邻 Card 合并圆角（`isFirst/isLast`）、`squircleSurface`、无 `SmallTitle` 的连续块。
GifHelper `GroupCard` 是旧式 `Card(horizontal 12, bottom 12)`（`Widgets.kt:153-170`），与 example 的 `component/*Section.kt` 写法一致（SmallTitle + 单卡），在 GifHelper 的"每组一张卡"结构下 **视觉等价**。若要与 example Settings 页逐像素对齐，才需要引入 `CardSegment`。判定：**建议不改**（避免为一个新 helper 引入一套卡片系统）。

### 4.5 空态 / 主操作条 / 预览舞台 / 进度

官方 example **没有**对应物：空态（`LibraryScreen.kt` 列表空态段）、`PrimaryActionBar`+`ProgressButton`（`Widgets.kt:471-608`、`ui/ProgressButton.kt`）、`PreviewStage`（`Widgets.kt:394-453`）、`NoteText`（`Widgets.kt:378-387`）。这些是项目特有交互，**保留**。已对照修好的点（并行改动）：
- 预览/动画预览圆角改 `squircleSurface`（`Widgets.kt:420-424`、`ToolScreen.kt:422-448`）——官方 Card/Button 本体也是 squircle。
- 抠图颜色行改 `BasicComponent(endActions = …)`（`ToolScreen.kt:342-403`）——官方行规格。
- `InfiniteProgressIndicator` 尺寸走 `ProgressIndicatorDefaults`（`Widgets.kt:596-604`）。

---

## 5. 【重点】作品操作面板 FileActionSheet：官方 `BottomSheetSection` 的"Card 分组 + 行"段落

### 5.1 官方原样（`example/shared/src/commonMain/kotlin/component/BottomSheetSection.kt:199-285`）

```kotlin
@Composable
private fun BottomSheetContent(
    allowDismiss: Boolean,
    onAllowDismissChange: (Boolean) -> Unit,
    enableNestedScroll: Boolean,
    onEnableNestedScrollChange: (Boolean) -> Unit,
    switchChecked: Boolean,
    onSwitchCheckedChange: (Boolean) -> Unit,
    dropdown: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
            .scrollEndHaptic()
            .overScrollVertical(),
    ) {
        item {
            SmallTitle(
                text = "Behavior Settings",
                insideMargin = PaddingValues(16.dp, 8.dp),   // 在 sheet 里必须显式给：sheet 自身的 insideMargin 是 (24dp, 0dp)
            )
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,   // 注意：sheet 内卡用 secondaryContainer，不是页面上的 surfaceContainer
                ),
            ) {
                SwitchPreference(
                    title = "Allow Dismiss",
                    summary = "Drag or Back to dismiss",
                    checked = allowDismiss,
                    onCheckedChange = onAllowDismissChange,
                )
                SwitchPreference(
                    title = "Enable NestedScroll",
                    summary = "Scroll content vs Drag sheet",
                    checked = enableNestedScroll,
                    onCheckedChange = onEnableNestedScrollChange,
                )
            }
        }
        item {
            var textFieldValue by remember { mutableStateOf("") }
            TextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = "TextField",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                ),
            ) {
                dropdown()
                SwitchPreference(
                    title = "SwitchPref",
                    checked = switchChecked,
                    onCheckedChange = onSwitchCheckedChange,
                )
            }
            Spacer(
                Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}
```

配套的头部动作官方写法（`BottomSheetSection.kt:115-129,270-285`；非玻璃档）：

```kotlin
OverlayBottomSheet(
    title = "BottomSheet (O)",
    show = show,
    allowDismiss = allowDismiss,
    enableNestedScroll = enableNestedScroll,
    dimColor = MiuixTheme.colorScheme.windowDimming.copy(alpha = 0.2f),
    onDismissRequest = onDismissRequest,
    onDismissFinished = onDismissFinished,
    startAction = { BottomSheetActionButton(MiuixIcons.Close, "Cancel", onClick = onDismissRequest) },
    endAction = { BottomSheetActionButton(MiuixIcons.Ok, "Confirm", onClick = onDismissRequest) },
) { BottomSheetContent(...) }

@Composable
private fun BottomSheetActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, tint = MiuixTheme.colorScheme.onBackground)
    }
}
```

玻璃档对应的头部动作写法（`docs/demo/GlassBottomSheetDemo.kt:78-86`）是 `endAction = { GlassIconButton { Icon(MiuixIcons.Glass.Ok, tint = onBackground) } }` —— GifHelper 当前正是这一版 ✅。

### 5.2 GifHelper 现状（并行改动后的 `LibraryScreen.kt`）

```text
FileActionSheet（约 :246-289）
  GlassBottomSheet(show, title, backdrop, onDismissRequest, onDismissFinished) {
      Column(fillMaxWidth()) {
          SheetActionRow(MiuixIcons.Glass.Download, "保存到相册", onClick = onSave)
          SheetActionRow(MiuixIcons.Glass.Share, "分享", onClick = onShare)
          SheetActionRow(MiuixIcons.Glass.Delete, "删除", destructive = true, onClick = onDelete)
          Spacer(12.dp)
          Text("size · time", footnote2, onSurfaceVariantSummary,
               padding(horizontal = BasicComponentDefaults.InsideMargin.left))   // 脚注
          Spacer(padding(bottom = navigationBars + captionBar))                  // 官方 BottomSheetSection 的收尾
      }
  }
SheetActionRow（约 :297-317）= BasicComponent(title, titleColor, startAction = { Icon(icon, size = 24.dp, tint) }, onClick)
```

判定（对照 `component/BottomSheetSection.kt:199-268`）：

- ✅ **行本身**：`BasicComponent` 吃默认 56dp 最小高 / 16dp 内边距 / 8dp 图文间距，与官方行一致，不要再手写 `Row + clickable`。
- ✅ **底部 insets**：`Spacer(navigationBars + captionBar)` 就是官方 `BottomSheetSection.kt:260-265` 的收尾写法（原样照抄，正确）。
- ✅ **脚注**：官方 example 的 sheet 里没有脚注行；GifHelper 加"大小 · 时间"是业务需要，放在 `AbsolutePadding 16dp`（与行文字对齐）合理。`footnote2`（11sp）在 example 中仅用于图标网格标签（`IconPage.kt:423/477`）；如果希望更接近官方"行副标题"的层级，建议用 `MiuixTheme.textStyles.body2`（官方 `BasicComponent` summary / `ArrowPreference` endActions 用的都是 body2 字号，`BasicComponentSection.kt:37,43`、`AboutPage.kt:378,429`）。**P3**。
- ⚠️ **行图标尺寸**：`SheetActionRow` 用 `MiuixIcons.Glass.*`（Glass 族画布是 **28dp**，`buildHyperOSIcon` 的 `defaultWidth = 28.dp`）却写 `Modifier.size(24.dp)` → 图形会被缩小约 14%。example 里 Glass 族行图标统一 28dp（`IconPage.kt:229`），extended 族才用 24dp（且行首要补 `Box(padding(end = 8.dp))`，`ArrowSection.kt:51-60`）。**建议改 28dp**（P2/小）。
- ⚠️ **头部动作**：官方 `docs/demo/GlassBottomSheetDemo.kt:78-86` 的玻璃面板有 `endAction = { GlassIconButton { Icon(MiuixIcons.Glass.Ok, tint = onBackground) } }`；example 的普通面板有 `startAction(Close) + endAction(Ok)`（`BottomSheetSection.kt:123-128`）。GifHelper 当前 HEAD 工作区已移除 `endAction`（依赖下拉/外点关闭）。两者都不算错误，但"官方玻璃弹层"默认有一个右侧玻璃胶囊按钮 —— **可选加回**，成本 5 行（P3）。
- ✅ **不分组**：官方 example 的 `Card + SwitchPreference` 分组用于"开关键值设置"；官方 glass 弹层 demo 是行直接落在面板上。当前代码选择后者，**判定正确**，不要为了"像 example"再加一层 Card。

---

## 6. 关于页 vs 官方 `AboutPage.kt`

| 项 | GifHelper（`AboutScreen.kt`） | 官方（`AboutPage.kt`） | 判定 |
| --- | --- | --- | --- |
| 采样源 | `rememberLayerBackdrop()` 只录渐变层（`:111-115`），`background` 槽挂 `bgModifier`（`:157-163` 目前 108 行附近） | `rememberBlurBackdrop()` 录整页（先 `drawRect(surface)`，`PageUtils.kt:299-308`），`Box(Modifier.layerBackdrop(backdrop))` 包页面（`:154`） | **保留**：GifHelper 硬约束 1 不允许把录制层套内容外；但必须补 `isRuntimeShaderSupported()` 兜底（见 `audit-baseline-alignment.md` §3.3） |
| 图标 textureBlur | 做（`AboutScreen.kt:241-255` 附近） | **不做**（88dp 白底 `RoundedCornerShape(24.dp)` + 74dp launcher，`AboutPage.kt:242-261`） | 保留（用户要求图标跟随渐变；参考项目做法）。已按官方容器圆角改 `RoundedCornerShape(24.dp)` ✅ |
| 标题 textureBlur | `RoundedCornerShape(16.dp)` / `BlurDefaults.MaxBlurRadius` / `BlurDefaults.NoiseCoefficient` / `DstIn` ✅ | 同参数（`AboutPage.kt:273-282`） | ✅ 已对齐（并行改动） |
| 卡片 | `GlassCard`：`Card + textureBlur(radius 60f, blend = surface@0.6)` + `defaultColors(color = Transparent)`（`Widgets.kt:191-223`） | `Card + textureBlur(radius=60f, colors = cardBlend(Overlay_Thin_Light / Pured_Regular_Light), noiseCoefficient)` + `defaultColors(Color.Transparent, Color.Transparent)`（`AboutPage.kt:205-220,347-395`） | 差异：混色 token 与 contentColor。**P2**：至少补 `contentColor = Color.Transparent`；混色是否换 `ColorBlendToken` 建议以 example 截图对比后决定（`ColorBlendToken` 是 example 模块内部常量，下游要用需复制色值） |
| 入口行附加信息 | 放 `summary`（"在 GitHub 上查看源代码"） | 放 `endActions` 的 `Text(fontSize = body2, color = onSurfaceVariantActions)`，如 "GitHub"/"Apache-2.0"（`AboutPage.kt:373-441`） | **建议改**（P2/小）：`ArrowPreference(title = "查看源代码", endActions = { Text("GitHub", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = …)` |
| 版本号 | `rememberAppVersion()` 文案（`AppInfo.kt`） | `"v" + VERSION_NAME + " (" + VERSION_CODE + ")"`（`AboutPage.kt:301`） | 若 AppInfo 已含同样格式则 ✅；否则 P3 |
| 头部淡出阈值 | 0.35/0.20/0.05，缩放 0.95 | 同（`AboutPage.kt:247,265,295`） | ✅ |
| 滚动条 | 无 | `VerticalScrollBar`（`AboutPage.kt:447-452`） | 随 §3.1 一起修 |

---

## 7. 已对齐 / 已按官方修好（回归时重点确认，不要再改回）

1. 弹层宿主：最外层 `Scaffold(containerColor = Transparent, contentWindowInsets = WindowInsets(0))`（`App.kt:215-218`）＝ `GlassPageHost`（`GlassPage.kt:702-711`）✅
2. 弹层采样源：`pageOverlay(LocalRootBackdrop ?: 页面层)`（`PageChrome.kt:327-329`）——官方无 Pager 场景，但方向正确，保留。
3. 返回键：玻璃档 `GlassIconButton + Glass.ChevronBackward + 28dp + RTL 翻转`，普通档 `IconButton + extended.Back + 24dp`（`GlassScope.kt:209-234`）＝ `component/BackNavigationIcon.kt:22-46` ✅
4. 玻璃顶栏：`bandBrush = GlassTopAppBarDefaults.bandBrush(surface)`、`bandOverhang = GlassTopAppBarDefaults.BandOverhang`、`progressiveBlur = true`、不传 `style`（吃 `GlassDefaults.Style`）（`GlassScope.kt:259-289`）＝ `PageUtils.kt:251-277` ✅
5. 主题装配：`MiuixTheme(controller) { MiuixTheme(colors = animated) { … } }`（`AnimatedTheme.kt`）＝ `ui/Theme.kt:62-66` 的必要条件 ✅（这使 `dynamicGlassMaterial`、Monet 指示器色等默认值真正生效）
6. 弹层写法：`GlassBottomSheet`（行 + 脚注 + 底部 nav/captionBar inset，`LibraryScreen.kt` 的 `FileActionSheet`）、`GlassDialog + GlassDialogButton(role = Normal/Destructive)`（`LibraryScreen.kt` 的 `GlassDialog` 调用段）＝ docs demo / `GlassPage.kt:409-441` ✅
7. 分组与行：`GroupHeader = SmallTitle`、`GroupCard = Card(horizontal 12dp, bottom 12dp)`（`Widgets.kt:64-170`）＝ `component/*Section.kt` ✅
8. 关于页 textureBlur 参数（shape/radius/noise）✅；预览舞台/动画预览的 `squircleSurface` ✅；抠图颜色行走 `BasicComponent` ✅；`InfiniteProgressIndicator` 走 `ProgressIndicatorDefaults` ✅（并行改动）
9. 顶栏返回键/动作图标 28dp（`IconSizes.Action`）＝ `GlassPage.kt:361/374`、`IconPage.kt:264-271` ✅
10. `GlassDialogButton` 角色语义：取消 = Normal（默认），删除 = Destructive，确认 = Primary —— 与 `GlassDialogButtonRole` 设计一致（`GlassDialogButton.kt:106-149`）✅

---

## 8. 官方没有对应物、明确保留的定制（不属于"未对齐"）

| 项 | 位置 | 保留理由 |
| --- | --- | --- |
| `Os4ListPage` 三参数骨架（`pageOverlay` / `largeHeader` / `background`） | `PageChrome.kt:178-354` | 官方 example 是单页平铺，没有 Pager 内的页面级弹层槽位；GifHelper 的约束（AGENTS 第 1/14 条）更复杂 |
| `LocalBottomBarClearance` + `rememberBottomBarClearance()` | `PageChrome.kt:55-86` | 在底栏保持浮层方案时必需；若采纳 §3.4-A 则删除 |
| `PrimaryActionBar` / `ProgressButton` | `Widgets.kt:471-608`、`ui/ProgressButton.kt` | 官方无"按钮内进度填充 + 生成中可取消"组件 |
| 预览舞台 / 动画预览 / 空态 / `NoteText` | `Widgets.kt:378-468`、`ToolScreen.kt:422-448` | 业务特有 |
| `CombinedBackdrop` | `ui/CombinedBackdrop.kt` | 官方 example 有同名实现（`component/liquid/CombinedBackdrop.kt`）但不在库内；GifHelper 需要（顶栏采渐变+正文两层） |
| `BgEffectBackground`（OS3 渐变移植） | `ui/bg/` | 官方 example 模块实现，未发布为库组件；关于页需要 |
| 关于页图标 textureBlur | `AboutScreen.kt` | 用户明确要求"图标跟随渐变"；官方图标不模糊，属于产品选择 |
| `DropdownRow` 的 `LocalRootBackdrop` 优先级 | `Widgets.kt:304-327` | Pager 坐标实测（`docs/os4-round7-audit.md` §3），官方无 Pager 内下拉场景 |
| `PageTopBar` 的自定义 `titleAlpha`/`background` 参数 | `GlassScope.kt:149-205` | 关于页需要"随滚动进度"的标题透明度与透明顶栏；官方用 `titleAlpha`/`backgroundAlpha` 参数表达，语义等价 |

---

## 9. 按收益排序的整改清单

| # | 优先级 | 改动 | 文件 | 工作量 | 收益 |
| --- | --- | --- | --- | --- | --- |
| 1 | P0 | 所有列表页加 `VerticalScrollBar`（用 `rememberScrollBarAdapter` + `trackPadding` + `topAppBarScrollBehavior`） | `ui/PageChrome.kt` | 小 | 与 example APK 并排最显眼的一处差异 |
| 2 | P1 | 非玻璃顶栏补 `BlurredBar` 的 `textureBlur` 带 + 条色透明 | `ui/GlassScope.kt` | 小 | "Original 顶栏"观感对齐官方 |
| 3 | P1 | 接 `centerObstructionStart`（折叠 Rail 顶部胶囊 × 居中标题） | `App.kt` + `ui/GlassScope.kt` | 小 | 横屏 Rail 折叠态不压标题 |
| 4 | P1 | 底栏进 `Scaffold.bottomBar` 槽，删 clearance 复刻公式（或明确保留并记录） | `App.kt` + `ui/PageChrome.kt` | 中 | 去掉全项目唯一"抄库内部公式"的脆弱点 |
| 5 | P2 | 行首/弹层行 Glass 图标 26dp/24dp → 28dp（Glass 画布 28dp） | `ui/Widgets.kt`（IconSizes）、`screens/LibraryScreen.kt`（SheetActionRow） | 小 | 行图标与 example 一致 |
| 6 | P2 | 关于页卡片补 `contentColor = Color.Transparent`；入口行附加信息从 `summary` 改 `endActions` 文本 | `ui/Widgets.kt`、`screens/AboutScreen.kt` | 小 | 关于页卡片与行写法贴近 `AboutPage.kt` |
| 7 | P2 | FileActionSheet 可选加回 `endAction = GlassIconButton(Close)`（官方 docs demo 有） | `screens/LibraryScreen.kt` | 小 | 与官方玻璃弹层头部一致 |
| 8 | P2 | 页面 Scaffold / 顶栏的 insets 责任收口（`contentWindowInsets = 0`、`defaultWindowInsetsPadding = false` + `appPageTopBarInsets` 等价物） | `ui/PageChrome.kt`、`ui/GlassScope.kt` | 大 | 横屏/挖孔/大字体与 example 逐像素一致；**先实测再决定** |
| 9 | P3 | `GlassDialog` 取消按钮显式 `role = GlassDialogButtonRole.Normal`；关于页版本号格式对齐官方 `vX.Y.Z (code)`；弹层脚注改 `body2` | `screens/LibraryScreen.kt`、`ui/AppInfo.kt` | 小 | 明确意图 / 文案一致 |
| 10 | P3 | Rail 传 `defaultWindowInsetsPadding = false` + `windowInsetsPadding(...)`（对齐 `PersistentGlassNavigationRail`） | `App.kt` | 小 | 宽屏 insets 与 example 一致 |

---

## 10. 回归验证建议（以 example APK 为准）

1. 同一台设备/模拟器（`-gpu host`）同时装 example APK 与 GifHelper，逐页并排截图：主页列表 / 设置 / 主题 / 作品 / 工具页 / 关于 / 弹层。
2. 重点看：右侧滚动条是否出现、位置是否随顶栏收起；非玻璃档（若可切换）顶栏是否磨砂；横屏 Rail 折叠时标题是否被胶囊遮挡；关于页卡片材质/入口行右侧文本。
3. 四个配色组合（静态/Monet × 明/暗）都要过：`dynamicGlassMaterial` 生效后，Monet 下弹层/底栏/顶栏按钮应有 `surfaceContainer` tint（与 example APK 同档），静态下不变。
4. 大字体（系统字体 1.3×/1.6×）与三键导航/手势导航各一次：insets 责任划分的差异主要在这里暴露。
5. API 31/32 设备（或临时把 `isRuntimeShaderSupported()` 打桩 false）：关于页图标/标题必须可见（走实色回退），弹层/底栏走库的 opaque fallback。

---

## 11. 证据索引

**MIUIX 官方 example（判定基准）**
- `AppContent.kt:210-269`（Insets/Rail/TitleAlpha/CenterObstruction 契约）、`:512-580`（`PersistentGlassNavigationRail`）、`:581-700`（`CompactScreenLayout`/`WideScreenContent`）、`:702-846`（`NavigationBar` 含 Glass 分支，`:791` 24dp 留白）
- `ComponentPage.kt:59-184`（二级页骨架：Scaffold + BlurredBar + AdaptiveTopAppBar + LazyColumn + VerticalScrollBar）
- `MainPage.kt:197-347`（主页列表：SmallTitle + Card + ArrowPreference + 顶栏动作 + VerticalScrollBar）、`:349-373`（TopLayer 弹层）
- `SettingsPage.kt:94-125`（Scaffold + BlurredBar + subtitle）、`:127-513`（groupedCardItems + 各 preference 行 + VerticalScrollBar）
- `IconPage.kt:180-399`（顶栏动作/搜索、行图标 28dp、分段卡片、Pager 列表）
- `GlassPage.kt:140-530`（玻璃场景：backdrop 分层、GlassTopAppBar、GlassNavigationBar in bottomBar、GlassDialog/GlassBottomSheet、tab rows）、`:702-711`（`GlassPageHost`）
- `AboutPage.kt:85-497`（关于页：backdrop/组合、BgEffect、图标不做 blur、标题 textureBlur 参数、Card textureBlur、ArrowPreference endActions、VerticalScrollBar、sheet 的 `insideMargin = DpSize(0.dp, 0.dp)`）
- `utils/PageUtils.kt:53-225`（`LocalTopAppBarBackdrop`/`LocalTopAppBarTitleAlpha`/`LocalTopAppBarCenterObstructionStart`/`AppPageInsets`/`pageScrollModifiers`/`pageContentPadding`）、`:227-344`（`AdaptiveTopAppBar`、`rememberBlurBackdrop`、`BlurredBar`）
- `ui/Theme.kt:23-104`（`AppTheme` = `MiuixTheme(controller)`）
- `component/GroupedCardItems.kt:26-107`（`CardSegment` / `groupedCardItems`）
- `component/BottomSheetSection.kt:47-285`（重点：`:199-268` 的 LazyColumn + SmallTitle + Card(secondaryContainer) + 行；`:270-285` 的 IconButton 头部动作）
- `component/DialogSection.kt:40-164`（普通弹层 TextButton 双按钮写法）
- `component/ArrowSection.kt:37-102`、`component/SwitchSection.kt:27-119`、`component/BasicComponentSection.kt:18-75`（行/分组标准写法）
- `docs/demo/GlassBottomSheetDemo.kt:39-114`、`docs/demo/GlassDialogDemo.kt:34-94`（玻璃弹层官方写法）
- MIUIX 组件：`miuix-ui/.../basic/{Scaffold,TopAppBar,Card,Component,Icon,ScrollBar}.kt`；`miuix-glass/.../{GlassBottomSheet,GlassDialog,GlassTopAppBar,GlassIconButton,GlassNavigationBar,GlassNavigationRail}.kt`；`miuix-blur/.../{TextureEffect,BlurDefaults,LayerBackdrop}.kt`；`miuix-ui/.../theme/{MiuixTheme,ThemeController}.kt`

**GifHelper（受审）**
- `App.kt:90-155`（主题、玻璃开关、Rail 状态与 provider）、`:203-240`（host Scaffold + NavDisplay）、`:241-346`（一级页 Pager/录制层/底栏）、`:398-413`（Rail）
- `ui/PageChrome.kt:55-86`（clearance）、`:89-168`（pageScrollModifiers/pageContentPadding）、`:178-354`（Os4ListPage）
- `ui/GlassScope.kt:73-117`（LocalPageBackdrop/rememberPageBackdrop）、`:149-305`（PageTopBar 两档）、`:313-323`（ContentBackdropBox）
- `ui/Widgets.kt:64-170`（GroupHeader/GroupCard）、`:191-223`（GlassCard）、`:252-277`（IconSizes）、`:304-376`（DropdownRow/SliderRow/SwitchRow）、`:378-468`（NoteText/PreviewStage）、`:471-608`（PrimaryActionBar/ProgressButton）
- `screens/{HomeScreen,LibraryScreen,SettingsScreen,ThemeScreen,ToolScreen,AboutScreen,LicensesScreen}.kt`（见 §2 矩阵行号）
- `ui/AnimatedTheme.kt`（`MiuixTheme(controller) ⊃ MiuixTheme(colors)`）、`ui/GlassSupport.kt`（单一能力决策点）、`ui/CombinedBackdrop.kt`、`ui/bg/*`
