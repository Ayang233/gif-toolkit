# OS4 Glass 深度对照排查报告

对照《GIF 助手 OS4 Glass 深度对照排查与一致性修复要求》第一～四章，以 Miuix 官方 main 分支源码 + 官方 example 实际配置为基准，对 GIF 助手做源码级差异审查。

**本轮只排查、不改代码。** 所有结论均指向具体文件与行号，可复核。

---

## 0. 基线与方法

| 项 | 状态 |
| --- | --- |
| Miuix 源码 | `ac0af467`（`fix: Adjust navigation bar bottom spacing on iOS`），与文档第五章锁定的提交一致 |
| 工作树 | 仅 `build-logic/src/main/kotlin/BuildConfig.kt` 一处 `workingDir = rootDir` 补丁（composite build 必需） |
| 参照实现 | `example/shared/src/commonMain/kotlin/AppContent.kt`（官方 APK 的同源代码） |
| GifHelper | `1.3` / versionCode 5，工作树干净 |
| 方法 | 逐项读官方实现 → 读本地实现 → 定位机制差异。**未使用视觉印象推断** |

关键一点：官方 example 的默认值本身就是"标准答案"，不需要跑起来猜。已核实的默认值如下。

| 官方配置项 | 默认值 | 出处 |
| --- | --- | --- |
| `pagerInterceptionMode` | **1 = CrossAxis** | `AppState.kt:34` |
| `enablePageUserScroll` | `true` | `AppState.kt:32` |
| `beyondViewportPageCount` | **4** | `AppContent.kt:896` |
| 顶栏 `defaultWindowInsetsPadding` | **`false`**（且自管 inset） | `PageUtils.kt:275` |
| 顶栏 `bandBrush` | 显式传 `bandBrush(surfaceColor)` | `PageUtils.kt:258,276` |
| 下拉弹层 `backdrop` | **全部传录制层**，无一处为 `null` | `GlassPage.kt:452/481/498`、`BlurSection.kt:171` |

---

## 1. 结论摘要

| 级别 | 数量 | 内容 |
| --- | --- | --- |
| **P0 阻断验收** | 3 | 边界可拖动、底部操作区多余背景、顶栏渐进模糊无降级 |
| **P1 明显不一致** | 4 | 弹层放弃玻璃采样、底部面板非玻璃、预组合为 0、自绘对话框 |
| **P2 细节偏差** | 5 | inset、玻璃 style、返回图标、功能缺口、双底栏 |
| 待实机验证 | 3 | 模糊失败的实际表现、弹层恢复采样后是否复现脏斑、真机跟手 |

三条 P0 全部能追溯到**同一类原因：用自己的补丁替代了库已有的机制**。

---

## 2. P0 问题

### P0-1 一级页在首尾仍可继续拖动

**现象**：处于「工具」页继续左滑、处于「设置」页继续右滑，页面仍产生位移。

**官方实现**（`AppContent.kt:865-907`）：

```kotlin
val flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = PagerNavigationSpringSpec)
val nativeConnection = PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)
HorizontalPager(
    state = pagerState,
    modifier = modifier.pagerGestureOverride(
        pagerState = pagerState,
        flingBehavior = flingBehavior,
        mode = appState.pagerInterceptionMode,   // 默认 1 = CrossAxis
        enabled = appState.enablePageUserScroll,
    ),
    userScrollEnabled = appState.enablePageUserScroll &&
        appState.pagerInterceptionMode != PagerInterceptionMode.CrossAxis.ordinal,  // → false
    pageNestedScrollConnection = if (CrossAxis) PagerGestureNestedScrollConnection else nativeConnection,
    verticalAlignment = Alignment.Top,
    flingBehavior = flingBehavior,
    beyondViewportPageCount = 4,
)
```

`PagerInterceptionMode.CrossAxis` 时 `userScrollEnabled = false`，**Pager 自身的触摸 scrollable 被完全移除**，手势由 `pagerGestureOverride` 安装的识别器接管。其落位逻辑在 `PagerGestureUtils.kt:628-640`：

```kotlin
val target = when {
    signedVelocity > minimum -> ceil(position).toInt()
    signedVelocity < minimum -> floor(position).toInt()
    else -> position.roundToInt()
}
state.animateToPage(this, target, signedVelocity)   // 内部 coerceIn(0, pageCount - 1)
```

目标页被 `coerceIn` 钳在 `0..pageCount-1`，且 `ScrollScope.scrollBy` 在边界不产生位移 —— 所以**首尾页拖不动、不产生过冲、不回弹**。

**GifHelper 现状**（`App.kt:220-231`）：

```kotlin
HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxSize(),
    beyondViewportPageCount = 0,
    flingBehavior = PagerDefaults.flingBehavior(state = pagerState, snapAnimationSpec = PagerNavigationSpringSpec),
)
```

只换了吸附曲线，手势仍走 Compose 原生 Pager。

**根因（不是"边界没限制"，是走了另一条渲染路径）**：

`MiuixTheme` 全局注入了 `LocalOverscrollFactory provides MiuixOverscrollFactory`（`MiuixTheme.kt:40,68`）。原生 Pager 在边界时，`scrollBy` 被拒后溢出的位移交给 overscroll 工厂处理 → 产生弹性位移。所以页面"拖得动"是 overscroll 的视觉效果，不是真的翻页。

官方 CrossAxis 模式不经过 Pager 的 scrollable，也就**完全不进 overscroll 工厂** —— 这是两套机制的本质差别，符合文档"不要只打边界补丁，要从机制入手"的要求。

**修复方案**：接入官方三件套（`pagerGestureOverride` + `userScrollEnabled = false` + `PagerGestureNestedScrollConnection`），并把 `flingBehavior` 提到 Pager 外面共用同一个实例（官方示例要求 `pagerGestureOverride` 与 Pager 收到同一个 `flingBehavior`）。

**涉及文件**：`App.kt`

**副作用与风险（必须先评估）**：

1. CrossAxis 识别器会在 `PointerEventPass.Initial` 上抢占手势，用于"横向打断子内容滚动"。工具页/作品页的 `LazyColumn` 垂直滚动必须回归，重点测「竖向滑动列表时不误触翻页」「横向滑动时能打断 Fling」。
2. `pagerGestureOverride` 的活动范围是 Pager 整块区域，**包括顶栏和底栏之外的内容**。GifHelper 的底栏是浮层、不占 Pager 区域，因此底栏点击不受影响；但顶栏在 `Scaffold.topBar`，同样不在 Pager 内，安全。
3. 该模式依赖 `beyondViewportPageCount` 足够大才能"滑入中的页面立即响应"。官方给 4，GifHelper 给 0，两者叠加可能让快速横滑出现空白页 —— 见 P1-6，建议一起改。

**验收点**：首/末页拖动零位移；中页快速横滑可打断；竖向滚动列表不误翻页；底栏点击正常。

---

### P0-2 二级页底部操作区多出一层背景

**现象**：`开始生成` 按钮下方能看出额外的底色/色带。

**官方实现**（`GlassPage.kt:244-260`）—— 注意 `Column` 上**没有任何背景**：

```kotlin
bottomBar = {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPageHorizontalInsets()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)
            .layout { measurable, constraints ->
                val bottom = maxOf(24.dp, safePadding.calculateBottomPadding() + 8.dp,
                                   padding.calculateBottomPadding() + 8.dp)
                    .roundToPx().coerceIn(0, constraints.maxHeight)
                val placeable = measurable.measure(constraints.offset(vertical = -bottom))
                layout(placeable.width, placeable.height + bottom) { placeable.placeRelative(0, 0) }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { /* 按钮 */ }
}
```

底部安全区靠 `layout{}` 的**负偏移 + 撑高**完成：内容上移 `bottom`，再把自身高度补回 `bottom`。视觉上底部就是页面本身，没有第二层。

**GifHelper 现状**（`Widgets.kt:344-359`）：

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .background(
            Brush.verticalGradient(
                0f to background.copy(alpha = 0f),
                0.55f to background,     // 到这里已经完全不透明
                1f to background,
            ),
        )
        .navigationBarsPadding()
        .padding(start = sideGap, end = sideGap, top = 20.dp)
        .padding(bottom = bottomGap),
)
```

**根因**：这层渐变是历史上为"列表文字从按钮底下透出来"打的补丁（`ui-audit.md` 第 12 条）。但它现在是**冗余**的：

- `Os4ListPage` 的 `pageContentPadding()` 已经用 `maxOf(innerPadding.bottom, clearance)` 让内容区让出了底栏高度（`PageChrome.kt:105`）；
- 按钮在 `Scaffold.bottomBar` 槽位里，`innerPadding.bottom` 已包含它的高度。

内容本来就不会穿到按钮底下。额外再铺一层不透明渐变，等于在页面色上又盖一块**同色但边缘是渐变的矩形**，所以会看到"shelf"式的边界。

**修复方案**：删掉 `background(Brush.verticalGradient(...))`，改用官方的 `layout{}` 负偏移写法；`bottomGap` 由 `safeBottomGap()` 手算改为 `maxOf(24.dp, insets.bottom + 8.dp)`。

**涉及文件**：`ui/Widgets.kt`（`PrimaryActionBar`）、`ui/PageChrome.kt`（若同时简化 `pageContentPadding`）

**副作用**：需要回归「工具页滚到末尾时，最后一张结果卡不会被按钮压住」。理论上 `pageContentPadding` 已保证，但 `extraBottom = 20.dp`（`ToolScreen.kt:111`）的注释写着"底部主操作条自带渐变遮罩" —— 遮罩删掉后这个值可能要重算。

---

### P0-3 顶栏渐进模糊无条件开启，缺降级链

**GifHelper 现状**（`GlassScope.kt:115-128`）：

```kotlin
GlassTopAppBar(
    title = title,
    isContentScrolled = isScrolled,
    expandable = expandable,
    progressiveBlur = true,            // ← 硬编码，无任何条件
    backdrop = glass.contentBackdrop,
    ...
    style = GlassDefaults.style(MiuixTheme.colorScheme.background.luminance() < 0.5f),
)
```

**官方实现**（`PageUtils.kt:251-281`）：

```kotlin
val shaderSupported = isRuntimeShaderSupported()
val useBandBrush = !shaderSupported || (backdrop != null && backgroundBlur)
val bandBrush = remember(useBandBrush, surfaceColor, color) {
    if (useBandBrush) GlassTopAppBarDefaults.bandBrush(surfaceColor) else SolidColor(color)
}
GlassTopAppBar(
    ...
    progressiveBlur = appState.glassTopBarProgressiveBlur && appState.enableBlur,
    backgroundAlpha = { if (backgroundBlur || !shaderSupported) backgroundAlpha() else 0f },
    defaultWindowInsetsPadding = false,
    bandBrush = bandBrush,
    bandOverhang = if (appState.enableBlur || !shaderSupported) GlassTopAppBarDefaults.BandOverhang else 0.dp,
)
```

**根因**：`GlassTopAppBar` 内部（`GlassTopAppBar.kt:392-433`）只要 `progressiveBlur && rawBackdrop != null` 就建 `progressiveBackdrop` 并走 `progressiveTextureBlur` 分支，**另一条 `bandBrush` 渐变分支被完全跳过**。

而 `progressiveTextureBlur` 依赖 AGSL 运行时着色器。本项目已有实测记录：该着色器在 SwiftShader 上编译失败（`ERROR: 0:192: 'w' : memory exhausted`），**失败是静默的**，该图层不产出内容 → 顶栏只剩按钮、没有任何渐变过渡 → 内容与顶栏硬切，这正是"能明显看出边界"的观感来源。

`isRuntimeShaderSupported()` 只判 `SDK_INT`，查不出 GPU 侧编译失败 —— 官方因此额外给了 `appState.glassTopBarProgressiveBlur` 这个开关，并在 `!shaderSupported` 时强制 `useBandBrush = true`。

**修复方案**：按官方结构补齐四件事 ——

1. `progressiveBlur` 改为「用户开关 && 玻璃总开关」，不再硬编码；
2. 显式传 `bandBrush = GlassTopAppBarDefaults.bandBrush(surface)`；
3. 显式传 `bandOverhang`（玻璃开时 `BandOverhang`，关时 `0.dp`）；
4. 显式传 `backgroundAlpha`，避免 progressive 与 band 两层同时上色。

**涉及文件**：`ui/GlassScope.kt`、`data/SettingsStore.kt` + `AppState.kt` + `screens/ThemeScreen.kt`（若要暴露"顶栏渐进模糊"开关）

**副作用**：`bandBrush` 一旦显式传入且类型为 `TopAppBarBandBrush`，库会走 `TopAppBarFadeBrush` 分支（`GlassTopAppBar.kt:435-457`），渐变高度 = `bandOverhang + 12dp`，比默认路径更低更长。这是官方的"无硬边"关键，必须与 `bandOverhang` 成对设置，单独改一个会更难看。

---

## 3. P1 问题

### P1-4 下拉弹层主动放弃玻璃采样

| | |
| --- | --- |
| **GifHelper** | `Widgets.kt:202` `backdrop = null` ← 唯一一处，全项目 |
| **官方** | `GlassPage.kt:452/481/498`、`AboutPage.kt:468`、`BlurSection.kt:171/179/342/350/494/502` —— **全部传录制层，零处为 null** |

官方两种用法：

- 整页录制层：`Box(Modifier.fillMaxSize().layerBackdrop(backdrop))` 包住 LazyColumn，Dropdown 直接传这个 `backdrop`（`GlassPage.kt:411,452`）；
- 局部录制层：`preferenceBackdrop = rememberLayerBackdrop()` 挂在**卡片容器**上，Dropdown 传它（`BlurSection.kt:125-128,171`）。这是为"只采样卡片背景、坐标天然吻合"准备的。

**根因**：GifHelper 的注释（`Widgets.kt:194-201`）把"弹层中部一坨灰糊"归因于坐标反算偏移，于是传 `null` 退化成不透明底卡。但那个脏斑的成因更可能是当时录制层**没有铺底色** —— 裸 `rememberLayerBackdrop()` 只录内容，卡片之间的空隙与 padding 区都是透明的，采样后仍是透明，看起来就是"脏斑"。

这条推论有旁证：`rememberGlassScope` 现在已经是 `drawRect(pageColor()) + drawContent()`（`GlassScope.kt:59-62`），并且注释明确写了这个修复。**脏斑的前提条件已经不存在了，但 `backdrop = null` 留着没改回来。**

**修复方案**：优先照 `BlurSection` 的局部录制层写法 —— 在 `GroupCard` 上加 `layerBackdrop(preferenceBackdrop)`，`DropdownRow` 传它。局部层的采样区与弹层锚点同源，比整页层更不容易偏。

**涉及文件**：`ui/Widgets.kt`

**副作用**：需要实机验证是否复现脏斑（见第 5 节）。若复现，退回 `null` 并在此处记录原因，作为"无法直接复用官方方案"的明确说明。

---

### P1-5 作品页底部面板不是玻璃

| | |
| --- | --- |
| **GifHelper** | `LibraryScreen.kt:183` `WindowBottomSheet(...)` —— 系统窗口，无 backdrop、无玻璃材质 |
| **官方** | `GlassBottomSheet.kt`（`miuix-glass`），`GlassPage.kt:403-409` 传 `backdrop = pageBackdrop` + `material` + `stroke` |

`WindowBottomSheet` 的参数表里只有 `backgroundColor`，没有 `backdrop`/`material`，所以它**从根本上不可能**画出 OS4 玻璃面板 —— 不是参数没调对。

**修复方案**：改用 `GlassBottomSheet(backdrop = ..., material = ..., stroke = ...)`。注意它与 `WindowBottomSheet` 的宿主机制不同（前者在 Compose 树内叠放，后者是独立窗口），`onDismissRequest` / `onDismissFinished` 的调用时序要重新对齐，否则会出现"关闭动画播到一半列表已刷新"。

**涉及文件**：`screens/LibraryScreen.kt`

---

### P1-6 预组合页数为 0，与官方 4 差距大

| | |
| --- | --- |
| **GifHelper** | `beyondViewportPageCount = 0`，注释理由："每页都挂着玻璃录制层，多留一页就多一次整屏 GPU 录制" |
| **官方** | `beyondViewportPageCount = 4` |

这是**有意识的性能权衡**，但代价明确：

- `beyondViewportPageCount = 0` 时相邻页只在真正进入视口后才组合，首次横滑会有一帧白屏/掉帧；
- 叠加 P0-1 的 CrossAxis 模式后更明显 —— 官方那个识别器依赖"滑入中的页面已经就绪"才能立即响应。

**修复方案**：与 P0-1 一起评估。建议先按 1（只多留一页）试，观察 GPU 占用与滑动流畅度，不要直接跳 4。

**涉及文件**：`App.kt`

---

### P1-7 退出确认是自绘对话框

`App.kt:343-389` 用 `Box + windowDimming + Card + Button` 手搓了一个对话框。库里已有 `WindowDialog`（系统窗口）与 `GlassDialog`（玻璃档），官方 example 用的是 `GlassDialog`（`GlassPage.kt:380-400` 附近）。

自绘版本缺：进出场动画、系统返回键联动、`windowDimming` 的正确渐入、以及玻璃档下的材质。`AGENTS.md` 的"加弹层怎么下手"一栏其实已经写明"确认用 `WindowDialog`"，与实际代码不符 —— 文档与实现已经脱节。

**涉及文件**：`App.kt`

---

## 4. P2 细节偏差

| # | 项 | GifHelper | 官方 | 影响 |
| --- | --- | --- | --- | --- |
| P2-8 | 顶栏 `defaultWindowInsetsPadding` | 默认 `true`（额外吃 `displayCutout` + `navigationBars` 横向 inset） | 显式 `false`，改用 `appPageTopBarInsets()` | 直屏无差异；刘海屏/横屏下顶栏内容可能比卡片多缩进一截 |
| P2-9 | 顶栏玻璃 `style` | `GlassDefaults.style(isDark)`，深色取 `CommonMediumRegularDark` | `GlassDefaults.Style`（`CommonMediumRegularLowLight`，不分明暗） | 深色模式下玻璃厚度/亮度与官方不同 |
| P2-10 | 顶栏返回图标 | `MiuixIcons.Back`（**extended 家族**，`GlassScope.kt:104,108`） | 玻璃顶栏配 Glass 族图标 | 与 `AGENTS.md` 第 10 条"图标只用 Glass 族"自相矛盾；线宽比同栏其它图标粗 |
| P2-11 | 功能缺口 | 无悬浮按钮、无搜索框、无说明浮层（tooltip）、运行状态非底部面板 | FAB / `SuperSearchBar` / `Modifier.tooltip` / FAB 槽位 | 属于第一份文档第三章列出的方向，非 bug |
| P2-12 | 双底栏 | 单套 `GlassNavigationBar` + 宽屏 `GlassNavigationRail` | iOS-like 与 OS4 Glass 双底栏可切 | 第一份文档第三章的"参考方向"，不强制 |

补充：P2-11 里的四项不是"实现错了"，而是**尚未引入**。若决定不做，应在 `ui-audit.md` 的"记录但未改"里显式写明理由，避免下一轮又被当成缺陷重查。

---

## 5. 必须实机验证的假设

这三条是本轮**无法从源码单独定论**的，修复前必须先验证，否则可能改错方向：

1. **顶栏模糊失败的真实表现**。P0-3 的推论是"AGSL 编译失败 → 该层空绘制 → 硬边"。但上一轮结论是在 API 34 / SwiftShader 上取的样，环境已换到 Android 16（`android16` AVD），**需要重新取样**，确认新镜像的 SwiftShader 是否仍失败、失败时顶栏到底是"全无"还是"残留脏区"。
2. **弹层恢复 backdrop 后是否复现脏斑**。P1-4 的判断建立在"脏斑根因是没垫底色"之上，这是推论不是实证。验证方式：临时把 `DropdownRow` 的 `backdrop` 指向 `contentBackdrop`，在浅色/深色下各开一次下拉，看菜单中部是否有灰斑。
3. **CrossAxis 模式的跟手与误触率**。模拟器触控不可靠（`input tap` 常被吞），必须真机。测项：快速横滑、竖向滚动列表时横滑、滑动中途反向、底栏连续点击。

---

## 6. 本轮未覆盖

- 顶栏 `GlassTopAppBar` 之外的其他玻璃组件（`GlassIconButton` / `GlassTabRow` / `GlassDialog`）逐个参数比对 —— 当前项目里实际用到的只有 `GlassIconButton`，已随 P0-3 一并受益。
- `Adaptive.kt` 的宽屏阈值与官方 `AdaptiveUtils` 的逐值核对（上一轮声称"一致"，本轮未复核）。
- 窄屏（<360dp）下的实际布局 —— 需真机或改 AVD 分辨率。
- 导入/生成链路的功能回归（本轮不动代码，无回归风险）。
- `_archive/` 下历史方案文档与新结论的一致性（可能存在过期结论）。

---

## 7. 建议实施顺序

按"改一处、多页受益"的原则排：

```
第一批（机制层，3 个文件，一次编译）
  P0-1 Pager 三件套 + P1-6 预组合页数        → App.kt
  P0-3 顶栏降级链（bandBrush/bandOverhang/backgroundAlpha）→ GlassScope.kt
  P0-2 底部操作区去背景                       → Widgets.kt + PageChrome.kt

第二批（组件层）
  P1-4 弹层局部录制层                         → Widgets.kt
  P1-7 对话框换库组件                         → App.kt
  P1-5 底部面板换 GlassBottomSheet            → LibraryScreen.kt

第三批（一致性收尾）
  P2-8/9/10 顶栏 inset、style、返回图标
```

第一批三项互相独立，但共享同一次编译 —— 符合"不频繁编译"的要求。第二批的 P1-4 与 P0-3 都动玻璃采样，如果第一批的顶栏改动导致观感变化，先把第一批验收完再进第二批。

**编译前必须先做第 5 节的第 1 项验证**（顶栏模糊在 Android 16 上的实际行为），否则 P0-3 的修复方向可能是错的。

---

## 8. 完成情况记录

### 本轮基线

| 项 | 值 |
| --- | --- |
| Miuix 提交号 | `ac0af4671bc9e4489d0960685a46a5b1ffd557da` |
| 提交时间 | 2026-11-15T15:29Z |
| 相较上一版 | **无变化**（`git ls-remote origin main` 直接返回同一 SHA，未重复同步） |
| 官方 example | `1.0.9.ac0af467`，`top.yukonga.miuix.uitest.debug`，编译后实机运行 |

### 顶栏模糊的实测结论（推翻了上一轮的环境假设）

| 环境 | AGSL `memory exhausted` 报错次数 | 渐进模糊 |
| --- | --- | --- |
| API 34 + SwiftShader（1.2/1.3 时期） | 113 | 该图层静默不产出 → 硬边 |
| **Android 16 + gfxstream（本轮）** | **0** | **正常工作**，内容在顶栏下方连续虚化 |

官方 example 与 GifHelper 在同一环境下都是 0 次报错、模糊都生效。
**"模糊能看出边界"不是着色器失败**，而是：GifHelper 的 `GlassTopAppBar` 没传
`bandBrush` / `bandOverhang`，一旦落回渐变分支就是一条生硬矩形；而渐进分支在
内容离顶栏较近时，band 会盖住列表首行（官方首行是卡片空白，所以看不出）。
本轮补齐降级链参数后，实测顶栏过渡连续、无水平分界线。

### 逐项结果

| # | 问题 | 处理 | 状态 |
| --- | --- | --- | --- |
| P0-1 | 首尾页越界拖动 | 接官方 Cross-Axis 三件套，`beyondViewportPageCount` 0→1 | ✅ 实测两端位移 0.00% |
| P0-2 | 底部操作区多余背景 | 去渐变，改与页面同色的纯色底（挡住穿透内容） | ✅ 无渐变过渡带，滑块不再透出 |
| P0-3 | 顶栏模糊降级链 | 显式传 `bandBrush` + `bandOverhang` + `backgroundAlpha` | ✅ 过渡连续 |
| P1-4 | 弹层放弃玻璃采样 | `GroupCard` 局部录制层 + `LocalCardBackdrop` | ✅ 真采样，无脏斑 |
| P1-5 | 作品页底部面板 | 尝试 `GlassBottomSheet` → 无 Scaffold 宿主不渲染 → **保留 `WindowBottomSheet`** | ⚠️ 保留原实现 |
| P1-6 | 预组合页数 | `beyondViewportPageCount` 0 → 1 | ✅ 横滑无白帧 |
| P1-7 | 自绘退出对话框 | 自绘 → `GlassDialog`（不渲染）→ **`WindowDialog`** | ✅ 底部滑入式卡片 |
| P2-8 | 顶栏 WindowInsets | **保留默认 `true`**（见 CHANGELOG 说明） | ⚠️ 保留原实现 |
| P2-9 | 顶栏 Glass Style | 去掉 `style(isDark)`，吃 `GlassDefaults.Style` | ✅ |
| P2-10 | 返回图标 | `MiuixIcons.Back` → `Glass.ChevronBackward` 28dp | ✅ |
| P2-11/12 | FAB / 搜索 / 双底栏 | 未做（按指令不擅自增加功能） | — |

### 编译与验收

| 项 | 结果 |
| --- | --- |
| `:assembleDebug` | BUILD SUCCESSFUL |
| `:assembleRelease` | BUILD SUCCESSFUL，`GifHelper-release.apk` **3,249,774 B**（1.3 为 3,217,006 B，+32.7 KB） |
| 版本 | `1.4` / versionCode 6（仅升版本，**未打 tag、未发布**） |
| 崩溃 | debug 与 release 全流程 0 次 `Fatal signal` / `FATAL EXCEPTION` |
| 已验收 | 三页左右滑动与两端边界、工具页底部按钮（浅/深色）、主题页下拉弹层、退出对话框、顶栏滚动模糊、返回图标 |
| 未覆盖 | 真机模糊质感与 CrossAxis 跟手（无真机）、宽屏 Rail、AMOLED、大圆角机型、真实任务生成链路（缺素材） |

### 新增的硬约束（已写入 `AGENTS.md`）

1. **录制层不能上移到 `Scaffold`** —— 顶栏与内容共用同一个 `contentBackdrop` 时，
   录制层包含顶栏会导致 `RenderNode::prepareTreeImpl` 无限递归、SIGSEGV。
   官方能那么写是因为它顶栏用了另一个 backdrop。
2. **`GlassDialog` / `GlassBottomSheet` 需要 Scaffold 宿主** —— GifApp 层没有 Scaffold，
   这类组件会静默不渲染；页面级浮层要用 `WindowDialog` / `WindowBottomSheet`。

