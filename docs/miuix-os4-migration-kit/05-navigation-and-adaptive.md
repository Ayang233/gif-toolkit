# 05 · 导航、大屏与 Insets

> 本篇解决四类问题：**用哪种导航形态**、**导航怎么摆**、**内容怎么让位**、**安全区怎么算**。
> 这三件事在 OS4 里是耦合的：导航形态变了，让位和 Insets 都要跟着变。

---

## 1. 结论速查

| 宽度 | 导航形态 | 内容让位 |
|---|---|---|
| < 840dp（竖屏手机） | 底部 `GlassNavigationBar` | 底部留出胶囊高度 |
| 840~1200dp（手机横屏 ~900dp、小平板） | `GlassNavigationRail`，**折叠态 = 顶部居中胶囊（只显示文字）** | **横向让位 0**（折叠 progress = 0） |
| ≥ 1200dp（大平板横屏） | `GlassNavigationRail`，**展开态 = 左侧 250dp 面板** | 起始边让位 262dp |

关键数字：**840 / 600+比例 / 1200** 三档，全部来自官方 example 的 `utils/AdaptiveUtils.kt`
（📌 example-only，**不在库里**）：

```kotlin
// miuix/example/shared/src/commonMain/kotlin/utils/AdaptiveUtils.kt:11-21
@Composable
fun shouldShowSplitPane(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val widthDp = windowInfo.containerSize.width.toDp()
        val heightDp = windowInfo.containerSize.height.toDp()
        val ratio = heightDp / widthDp
        widthDp >= 840.dp || (widthDp >= 600.dp && ratio < 1.2f)
    }
}
// :33-41
@Composable
fun shouldExpandNavigationRail(): Boolean { … widthDp >= 1200.dp }
```

> ⚠️ 抄这两个函数是为了"和生态里其它 Miuix 应用在同一台设备上同一时刻切换布局"。
> 自己定一套阈值不会崩，但并排看会不一致。

---

## 2. 导航框架

### 2.1 三个选项

| 方案 | 能拿到什么 | 缺什么 | 建议 |
|---|---|---|---|
| `miuix-nav` 的 `NavDisplay` | `NavDisplayEffects`（圆角裁剪、dim、`backdropColor`）、`NavTransitions`、预测性返回、`NavSaveableStateHolder` | 生态较新 | **推荐**（GIF 助手用它） |
| `androidx.navigation` | 成熟、文档多 | 没有 `NavDisplayEffects`，圆角转场要自己做 | 已有工程可保留 |
| 自建路由/栈 | 完全可控 | 返回语义、状态保存全要自己实现 | 不推荐 |

### 2.2 `NavDisplay` 的最小用法

📌 签名（`miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/core/NavDisplay.kt:974`）：

```kotlin
@Composable
fun NavDisplay(
    backStack: NavBackStack,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = { backStack.removeLastOrNull() },
    transition: NavTransition = NavTransitions.MiuixDefault,
    effects: NavDisplayEffects = NavDisplayEffects(),
    content: NavEntryBuilder.() -> Unit,
)
```

🏠 GIF 助手的实际配置（`.../App.kt:227-240`）：

```kotlin
val cornerRadius = rememberDeviceCornerRadius()
val navEffects = remember(cornerRadius, pageBackground) {
    NavDisplayEffects(
        enableCornerClip = true,
        cornerClipRadius = cornerRadius,
        dimAmount = 0.5f,
        backdropColor = pageBackground,      // 必须是页面真实底色
    )
}
NavDisplay(
    backStack = stack,
    onBack = pop,
    transition = pageTransition,
    effects = navEffects,
) {
    entry<Route.Root> { … }
    entry<Route.Tool> { … }
}
```

📌 `NavDisplayEffects` 定义在 `miuix-nav/.../core/NavDisplayEffects.kt:63`。
`cornerClipRadius` 不传默认 `0.dp`，等于把圆角裁剪关掉 —— GIF 助手曾经因此没有转场圆角。

### 2.3 返回语义（官方约定）

| 位置 | 行为 | 实现 |
|---|---|---|
| 二级页 | 出栈 | `NavDisplay` 内置（`backStack.size > 1` 时启用预测性返回） |
| 一级页 tab ≠ 0 | 回 tab 0 | 自己加 `BackHandler` |
| 一级页 tab == 0 | **交还系统 → `Activity.finish()`** | **不要拦** |

```kotlin
// 🏠 .../App.kt:199-201
BackHandler(enabled = stack.size <= 1 && tab != 0) { goTab(0) }
```

官方对应物 📌：`example/shared/.../AppContent.kt:910` 的 `MainScreenBackHandler`，
条件是 `selectedPage != 0`。**不要在 tab 0 弹"确认退出"**（GIF 助手曾经加过，后来删掉，
见 [12](12-known-issues-and-solutions.md) C22）。

### 2.4 从 `androidx.navigation` 迁移（不动导航层的最小改法）

`NavHost` 与 `MiuixTheme` **没有冲突**：`NavHost` 只负责"组合哪个 Composable"，
不参与视觉。所以最小改法是：

1. 保留 `NavHost` / `NavController` / 路由定义，**不重写导航层**。
2. 把每个 `composable("route") { … }` 里的内容换成 MIUIX 组件与 `Scaffold`。
3. 把 `MaterialTheme` 换成 `MiuixTheme`（[03](03-os4-theme-system.md) §11 的退场策略）。
4. 弹层宿主：在 `NavHost` **外面**套一层
   `Scaffold(containerColor = Transparent, contentWindowInsets = 0)`
   —— 这是 Glass 弹层（`GlassDialog` / `GlassBottomSheet`）能找到的最外层宿主。

你会失去什么（可接受）：

- `NavDisplayEffects` 的**设备圆角裁剪**（`androidx.navigation` 没有这个能力）。
  替代方案：给每个页面根部加 `Modifier.clip(RoundedCornerShape(设备圆角))`，
  或在转场时接受"直角"。
- `NavTransitions.MiuixDefault` 那套转场；`androidx.navigation` 用自己的 `enterTransition`。

什么时候值得换成 `miuix-nav`：

- 你必须要"进入二级页时设备圆角裁剪 + 0.5 暗化"这套 OS4 转场；
- 你要用 `NavSaveableStateHolder` / `NavEntryViewModel` 那套状态管理。

> 迁移顺序建议：**先保留 androidx.navigation 做到 L3**（OS4 全项目适配），
> 到 L4/L5（Glass）时再评估是否换 `miuix-nav`。换导航层与接 Glass 不要同时做，
> 否则出问题无法归因。

---

## 3. 两种导航的具体摆法

### 3.1 `GlassNavigationBar`（底部）

```kotlin
Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().layerBackdrop(rootBackdrop)) {
        Pager / NavDisplay
    }
    // 兄弟节点，不是录制层子节点
    if (!wide) {
        GlassNavigationBar(
            items = items,
            selectedIndex = tab,
            onSelect = { goTab(it) },
            backdrop = rootBackdrop.takeIf { glassEnabled },
            selectedColor = if (monet) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp),   // 退进屏幕圆角
        )
    }
}
```

要点：

- 左右各留 24dp：胶囊必须退进屏幕圆角，否则大圆角机型两端圆弧被切。
- `selectedColor` 只在 **Monet** 下需要显式传（因为逐色插值主题丢了 `LocalColorSchemeMode`，
  见 [03](03-os4-theme-system.md) §1.1）。
- 高度、外边距、指示器色全部吃库默认；**不要**把 54dp 抄成常量。

### 3.2 `GlassNavigationRail`（侧栏）

```kotlin
Box(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxSize()
            .layerBackdrop(rootBackdrop)           // 满宽录制，不让位
    ) {
        NavDisplay { … }                            // 内容自己用内边距让位
    }
    if (railVisible) {
        GlassNavigationRail(
            items = items,
            selectedIndex = tab,
            onSelect = { goTab(it) },
            backdrop = if (glassEnabled) rootBackdrop else null,
            selectedColor = if (monet) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface,
            state = railState,
        )
    }
}
```

**绝对不要**用 `Row` + `weight`：

```kotlin
// ❌ 错误：Rail 会吃掉整行宽度，内容被挤成 0 宽
Row {
    GlassNavigationRail(…, modifier = Modifier.weight(1f))
    Content(Modifier.weight(1f))
}
```

📌 原因：`GlassNavigationRail` 的根节点是 `BoxWithConstraints(fillMaxSize())`，
自己决定面板位置（`Margin` 12dp + `ExpandedWidth` 250dp）。
官方 `AppContent` 的 `useGlassSidebar` 分支也是"叠放在内容之上"。

### 3.3 Rail 的展开/折叠状态

```kotlin
val expandRail = shouldExpandNavigationRail()          // ≥1200dp
val railState = rememberGlassNavigationRailState(initialExpanded = expandRail)
LaunchedEffect(expandRail) { railState.expanded = expandRail }
```

- **不要写死 `initialExpanded = true`**：手机横屏（~900dp）会变成 250dp 常驻面板，
  比官方"重"得多（[12](12-known-issues-and-solutions.md) C06）。
- **不要用 `showToggle = false`** 藏掉折叠入口：官方折叠态是"顶部居中胶囊 + 左侧方块按钮"。
- 折叠态**不是**顶部导航栏，是 Rail 的另一种形态（只显示文字）。

---

## 4. Insets 与让位：五个独立算式

OS4 布局最容易出错的地方是"同一个安全区被算两遍或一次没算"。GIF 助手的做法是**五个算式各管一件事**。

### 4.1 纵向：Scaffold `innerPadding`

`Scaffold` 的 `contentWindowInsets` 默认把它算好：状态栏 + 导航栏。
**页面内容直接用 `innerPadding` 的纵向部分即可。**

### 4.2 横向：`systemBars ∪ displayCutout`

```kotlin
@Composable
fun rememberHorizontalSafeInsets(): PaddingValues =
    WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
        .asPaddingValues()
```

🏠 `.../ui/SystemInsets.kt:32-36`。

用途：**不在 `Scaffold` 内容槽位里的控件**（底部主操作条、页面根部自绘列表）。
在内容槽位里的控件，横向安全区已经包含在 `innerPadding` 里了，**不要再加一遍**。

### 4.3 底部：玻璃底栏的视觉占位

底栏是浮层（不占 Scaffold 的 `bottomBar` 槽），所以 `innerPadding.bottom` 里没有它。
需要自己算：

```kotlin
@Composable
fun rememberBottomBarClearance(): Dp {
    val navInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val floatingMargin = when {
        navInset <= 16.dp -> 20.dp
        navInset >= 28.dp -> navInset
        else -> 12.dp + navInset
    }
    return GlassNavigationBarDefaults.Height + floatingMargin + 12.dp
}
```

🏠 `.../ui/PageChrome.kt:67-86`。

> ⚠️ 版本差异：`navInset <= 16.dp` 这一档在 MIUIX `3177a684` 上是 `28.dp`，
> 从 `5ff41ba9`（提交 `9413f690`）起改成 `20.dp`。照抄旧知识库会多预留 8dp，
> 列表末尾出现空带（只有导航栏 ≤16dp 的机型才会暴露，420dpi 手势导航模拟器走不到这一档）。

📌 这个 `when` 是**镜像库内部公式**：`GlassNavigationBar.kt:304-323` 里
`floatingMargin` 是函数内局部变量，**没有暴露成参数**。这是全项目唯一"复刻库内部公式"的地方；
上游改规则这里要跟着改。

📌 一个很容易漏的差异：库内部用的是
`maxOf(navigationBars.bottom, animatedCaptionBar.bottom)`，而镜像公式只取了 `navigationBars`。
如果目标设备有 CaptionBar（例如 Android 的 caption bar / 某些折叠屏），
clearance 会**偏小**，列表末尾可能被底栏压住 🔴（未在真机验证）。
处置：要么只镜像 `navigationBars` 并在有 CaptionBar 的设备上补验，要么用
`WindowInsets.captionBar` 也参与 `maxOf`。

### 4.4 侧栏让位：`LocalRailInset` 是**函数**

```kotlin
// 提供（🏠 App.kt:133-141）
val railInset: () -> Dp = remember(railVisible) {
    if (railVisible) ({
        (GlassNavigationRailDefaults.ExpandedWidth + GlassNavigationRailDefaults.Margin) * railState.progress
    }) else ({ 0.dp })
}
```

```kotlin
// 消费（🏠 PageChrome.kt:146-162）：自定义 PaddingValues，在量测期读
object : PaddingValues {
    override fun calculateLeftPadding(ld: LayoutDirection): Dp {
        val extra = if (ld == LayoutDirection.Ltr) railInset() else 0.dp
        return horizontal + safeStart + extra
    }
    override fun calculateRightPadding(ld: LayoutDirection): Dp {
        val extra = if (ld == LayoutDirection.Rtl) railInset() else 0.dp
        return horizontal + safeEnd + extra
    }
    override fun calculateTopPadding(): Dp = top
    override fun calculateBottomPadding(): Dp = bottom
}
```

三个不能忘的点：

1. **只让 start 一侧**（LTR 左 / RTL 右）。两侧都让会在展开态把内容压成竖带。
2. **必须加上横向安全区**（`safeStart` / `safeEnd`），否则横屏被侧栏压住（§5.1）。
3. **不能在组合期解包成 `Dp`**（[06](06-glass-architecture.md) R8）。

### 4.5 底部通栏控件的圆角安全区

屏幕圆角是一段半径 R 的圆弧。距屏幕底部 d 处，水平侵入量是 `R - sqrt(2Rd - d²)`。
按钮左右各留 `g`，只要侵入量不超过 g 就切不到按钮，解出最小 d：

```
d = R - sqrt(2R·g - g²)
再减去系统导航栏已占的高度，下限取 minimum（12dp）
```

🏠 `.../ui/SystemInsets.kt:79-95`。

为什么不用固定 12dp：R=80dp 的机型需要约 32dp 的 d，而直屏机 12dp 就够 🔴（圆角分支未真正触发验证）。

### 4.6 顶部：`Scaffold` 的 `innerPadding.top` + 少量呼吸

```kotlin
val top = innerPadding.calculateTopPadding() + 2.dp
val bottom = maxOf(innerPadding.calculateBottomPadding(), clearance) + 12.dp
```

注意底部是 **`maxOf` 而不是相加**：底部主操作条已经占了一部分，相加会多留一条空带。

---

## 5. 标准案例：1200dp 横屏侧栏遮挡内容 52dp

这是最有价值的一个案例，因为它同时暴露了四个独立缺陷。🔴（模拟器 2400×1080 截图逐点测量，÷2 = dp）

### 5.1 现象

1200×540dp 横屏（`wm density 320` + `user-rotation lock 1`），侧栏展开成左侧面板后：

- 面板右边界压在列表卡片上；
- 每行前置图标只露出一半，被玻璃糊成灰斑；
- 第一个分组标题「创建」整块被盖住。

实测：卡片可见起点 650px（325dp），面板右边界 652px（326dp），列表行图标起点 654px（327dp），
卡片真实左边界是 548px（274dp）= 12dp 外边距 + 262dp 让位。**被遮 104px = 52dp**。

### 5.2 四问四答

**Q1：为什么单独计算 Rail 宽度不够？**

因为面板的**起点**不是 0。`GlassNavigationRail` 内部对
`systemBars ∪ captionBar ∪ displayCutout` 做 `windowInsetsPadding`，
所以面板起点 = 起始边安全区 + `Margin`。横屏时挖孔落在起始边（本机 128px = **64dp**），
面板起点 = 64 + 12 = 76dp，面板右边界 = 76 + 250 = **326dp**。

内容侧如果只让 262dp（250 + 12），左边界 = 12 + 262 = **274dp** < 326dp → 遮挡 52dp。

**Q2：为什么还必须考虑横向安全区？**

因为侧栏自己吃了安全区，内容没吃。`Scaffold` 的 `innerPadding` **横向部分**就是
`systemBars ∪ displayCutout`，而 `pageContentPadding` 早期只用了它的纵向部分，
`calculateLeftPadding` 只返回 `horizontal + railInset()` → 安全区被整块丢掉。

竖屏时挖孔在**顶部**、横向安全区 = 0，`12 + 250 = 262` 与 `262` 刚好相等 ——
**这就是上一轮"1200dp 竖屏验证通过"却漏掉横屏的原因**。

**Q3：为什么需要在正确的阶段读取动画状态？**

`railState.progress` 是逐帧变化的动画值。如果在组合期解包成 `Dp`：

```kotlin
val inset = railInset()                     // ❌ 组合期
Box(Modifier.padding(start = inset))        // 每帧重组整页 + 重排 LazyColumn
```

侧栏展开的 300ms 里，页面会重组约 18 次，滚动位置与手势状态被打断。
正确做法是在 `PaddingValues.calculate*Padding`（量测期）或 `Modifier.layout`（量测期）里读。

**Q4：为什么背景可以保持全屏，而前景内容需要让位？**

因为玻璃的语义是"浮在内容之上"，面板背后必须是完整的内容像素。
如果为了让位把**录制层**也收窄（`padding(start = 262.dp)` 加在 `layerBackdrop` 上），
侧栏左半段就没有像素可采，玻璃只剩一块平色 —— 视觉上侧栏"空了一块"。
所以：**录制层满宽，只有内容的内边距让位**。
官方 `AppContent.kt:300-350` 也是"满宽录制 + 页面内边距让位"。

### 5.3 修复后的数字（对得上）

| 量 | 值 |
|---|---|
| 挖孔安全区 | 128px = **64dp** |
| 侧栏让位 | **262dp**（250 + 12） |
| 卡片外边距 | 12dp |
| 卡片左边界 | **338dp** = 64 + 262 + 12 ✅ |
| 面板右边界 | **324.5dp** |
| 侧栏与卡片间隙 | **13.5dp**（27px） |

### 5.4 可复用的验收表

| 检查项 | 横屏 914dp | 横屏 1200dp | 竖屏 540dp | 竖屏 1200dp |
|---|---|---|---|---|
| 导航形态 | 折叠 Rail（顶部胶囊） | 展开 Rail（左侧面板） | 底栏 | 展开 Rail |
| 内容起始让位 | 0 | 262 + 安全区 | 0 | 262 + 安全区 |
| 卡片左边界 | 12 + 安全区 | 338（本机） | 12 | 262 + 12 |
| 侧栏是否压住卡片 | 否 | 否 | — | 否 |
| 右侧是否多留白 | 否 | 否 | 否 | 否 |
| 录制层是否满宽 | 是 | 是 | 是 | 是 |

> 每一档都要**截图 + 逐点取色/量边界**，不要只看"看起来没问题"。
> GIF 助手的 R9/R10 都是靠 `Pillow` 逐点扫描才发现 52dp / 30 灰阶这种问题的。

---

## 6. 自适应与页面结构

### 6.1 断点表（可扩展）

| 断点 | 布局 | 内容最大宽度 |
|---|---|---|
| < 600dp | 单列 | 全宽 |
| 600–840dp | 单列（更宽边距） | 建议 ≤ 840dp |
| 840–1200dp | Rail（折叠）+ 单列 | 全宽 - 导航 |
| ≥ 1200dp | Rail（展开）+ 单列 | 建议内容区 ≤ 1000dp（居中或靠起始边） |

> "内容最大宽度"是**产品决定**，不是库约束。GIF 助手没有限宽（列表全宽），
> 因为它的内容（设置行）本身就很宽。如果你的项目是阅读类，限宽会更像系统应用。

### 6.2 窗口尺寸变化的正确来源

```kotlin
@Composable
fun rememberWideLayout(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val width = windowInfo.containerSize.width.toDp()
        val height = windowInfo.containerSize.height.toDp()
        width >= 840.dp || (width >= 600.dp && height / width < 1.2f)
    }
}
```

🏠 `.../ui/Adaptive.kt:24-32`。

要点：

- 用 `LocalWindowInfo.containerSize`，**不要**用 `LocalConfiguration.screenWidthDp`
  （分屏/自由窗口下 `screenWidthDp` 不是容器宽度）。
- 阈值判定要**可单元测试**（纯函数更好）。
- 横竖屏切换由系统重建 Activity（GIF 助手在 Manifest 里声明了
  `configChanges="orientation|screenSize|..."`，所以不会重建 —— 两种策略都要保证状态不丢）。

### 6.3 字体缩放与长文本

- 用 `sp`，不要用 `dp` 写字号。
- 行高不要写死：`BasicComponent` 的最小行高 56dp 会随字体放大而增高（它内部用 `heightIn`）。
- 长文本：`maxLines` + `TextOverflow.Ellipsis`，但**标题不要截断到无法辨认**（建议 2 行）。
- 验收：`adb shell settings put system font_scale 1.3` 后重跑主要页面。

### 6.4 长列表

- 用 `LazyColumn`，`key` 必须稳定（否则滚动位置丢失）。
- 玻璃场景下**不要给每一项加玻璃**（每次采样）。
- 大量数据时注意 `beyondViewportPageCount`（分页容器）与 `contentType`（列表）。

---

## 7. RTL 与方向

| 对象 | 处理 |
|---|---|
| 侧栏 | 恒在**起始边**：`railStartInset` 用 `placeRelative`，RTL 下自动镜像 |
| 返回箭头 | RTL 下 `scaleX = -1f`（官方同款） |
| 页面内边距 | `calculateLeftPadding` / `calculateRightPadding` 里按 `LayoutDirection` 分配 `railInset` |
| 图标 | 方向性图标（`ChevronBackward`/`ArrowForward`）需要镜像；对称图标不需要 |
| 数字/时间 | 用 `LocalLayoutDirection` 判断，不要假设 LTR |

```kotlin
// 🏠 .../ui/GlassScope.kt:216-221
Modifier.graphicsLayer { if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f }
```

🔴 未验证：GIF 助手没有切 RTL 语言实测过（`docs/os4-round10-audit.md` §8.4）。
新项目必须实测一次 `开发者选项 → 强制 RTL 布局方向`。

---

## 8. 页面转场

- 用 `NavDisplay(transition = …, effects = …)`；`NavTransitions.MiuixDefault` 是官方默认档。
- 圆角裁剪：`NavDisplayEffects(enableCornerClip = true, cornerClipRadius = 设备圆角, dimAmount = 0.5f, backdropColor = 页面底色)`。
- 退场方向可配（GIF 助手做了 5 档"返回动画"设置，`ui/BackTransitions.kt`）。
- **不要自己写 `AnimatedContent` 做页面切换**：底栏被盖住/揭开的行为依赖 NavDisplay 的转场层。

---

## 9. 窗口与系统栏配置（新项目必做的 6 行）

这几行不属于任何组件，但漏了就会"深色下白边""状态栏图标看不见""三键导航上有一条接缝"。

### 9.1 Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                                  // ① 内容延伸到系统栏下
        window.isNavigationBarContrastEnforced = false       // ② 去掉三键导航的对比度底色
        setContent { AppTheme { App() } }
    }
}
```

🏠 GIF 助手：`MainActivity.kt:12`、`:15`。

1. **`enableEdgeToEdge()`**：不调用的话系统栏区域由窗口背景绘制，深色下会留一条白边。
2. **`isNavigationBarContrastEnforced = false`**：三键导航下系统会给导航栏加一层对比底色（浅色下是白的），
   和页面底色之间出现接缝。

### 9.2 `themes.xml`

```xml
<style name="Theme.Miuix" parent="android:Theme.DeviceDefault.DayNight">
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowActionBar">false</item>
    <item name="android:windowBackground">?android:attr/colorBackground</item>
</style>
```

🏠 GIF 助手：`res/values/themes.xml:4-8`。

关键：父主题必须是 **`DayNight`**。固定浅色会在深色模式下闪白，状态栏图标也不反色。

### 9.3 状态栏 / 导航栏图标反色

```kotlin
val isDarkSurface = MiuixTheme.colorScheme.background.luminance() < 0.5f
SideEffect {
    val window = (context as? Activity)?.window ?: return@SideEffect
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !isDarkSurface
        isAppearanceLightNavigationBars = !isDarkSurface
    }
}
```

🏠 GIF 助手：`App.kt:157-164`。

**为什么不能靠 `themes.xml` 解决**：`System` 模式或手动切深色时，窗口主题不会自动跟着
`MiuixTheme` 的配色变。必须在 Compose 里按**实际配色**再设一次。

### 9.4 预测性返回

```xml
<application android:enableOnBackInvokedCallback="true" … >
```

📌 应用这个 flag 后，系统会把返回手势的进度交给 Compose（`miuix-nav` 已有
`PredictiveBackHandler`；`androidx.navigation` 也支持）。

> GIF 助手为了"让用户切换返回动画"，还额外用 `hiddenapibypass` 反射
> `ApplicationInfo.setEnableOnBackInvokedCallback`（`GifApplication.kt:27-48`）。
> **如果新项目没有这个设置项，不要引 `hiddenapibypass`** —— Manifest 里一行就够。

### 9.5 `windowSoftInputMode`

```xml
<activity android:windowSoftInputMode="adjustResize" … >
```

输入法弹出时内容要跟着缩，否则表单会被键盘遮住。Compose 里配合
`WindowInsets.ime`（或 `Modifier.imePadding()`）。

### 9.6 屏幕方向与配置变更

两种策略，选一个：

| 策略 | Manifest | 行为 | 代价 |
|---|---|---|---|
| 重建 Activity | 不声明 `configChanges` | 系统重建，状态要靠 `rememberSaveable`/ViewModel | 要保证状态可保存 |
| 自行处理 | `configChanges="orientation\|screenSize\|screenLayout\|keyboardHidden\|density\|fontScale\|layoutDirection\|locale\|uiMode"` | 不重建 | 要自己响应所有变化（GIF 助手选这个） |

🏠 GIF 助手：`AndroidManifest.xml:16-18`。

> 选"自行处理"时注意：`uiMode` 变化（深色切换）也在里面，所以要确保配色是
> `MiuixTheme` 驱动的（而不是只在 `onCreate` 读一次系统值）。

---

## 10. 验收清单

- [ ] 四种宽度（竖屏手机、横屏手机、1200dp 横屏、1200dp 竖屏）都截图
- [ ] 侧栏展开态：内容左边界 = 安全区 + 262dp（或你的面板宽 + margin）
- [ ] 侧栏折叠态：内容不让位（0dp），顶部胶囊不与标题重叠
- [ ] 录制层满宽（不是被 padding 收窄的）
- [ ] 顶部主操作条/页面浮层控件自己取了横向安全区
- [ ] 底部留白是 `maxOf(innerPadding.bottom, clearance)` 而不是相加
- [ ] 一级页 tab 0 返回直接退出（无确认框）
- [ ] 强制 RTL 后布局正确
- [ ] 字体 1.3× 后无截断
- [ ] 横竖屏切换后导航形态与让位都正确
- [ ] `enableEdgeToEdge()` 已调用，三键导航无接缝
- [ ] 窗口主题是 `DayNight`，深色下不闪白
- [ ] 状态栏/导航栏图标在四套配色下都清晰可见
- [ ] 输入法弹出时表单不被遮挡
