# 06 · Glass 架构：backdrop 录制、采样与消费

> 本篇是整套知识库的重点。**Glass 失效的 90% 都是节点关系或坐标问题，不是材质参数问题。**
> 读懂三个词就能读懂一切：**录制层**（谁被录下来）、**采样源**（`Backdrop`）、**消费者**（谁在画玻璃）。

---

## 1. 概念模型

### 1.1 一条数据链

```
[内容节点 A] --layerBackdrop(backdrop)--> 录制进 GraphicsLayer
                                              │
                                    backdrop: LayerBackdrop
                                              │
[消费者节点 B] --drawBackdrop(backdrop, …)----┘  采样 + 加效果（模糊/混合/折射）
```

📌 三个官方类型（全部在 `miuix-blur`）：

| 类型 | 位置 | 语义 |
|---|---|---|
| `interface Backdrop` | `miuix-blur/.../Backdrop.kt:16` | **只读采样源**。`drawBackdrop()` 把录制结果画到消费者上 |
| `class LayerBackdrop` | `miuix-blur/.../LayerBackdrop.kt:55` | 由 `rememberLayerBackdrop()` 创建，持有一个 `GraphicsLayer` |
| `Modifier.layerBackdrop(backdrop)` | `LayerBackdropModifier.kt:22` | **唯一的录制入口**：把这个节点（含子树）的绘制录进那个 layer |

关键的接口细节 📌：

```kotlin
// miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/Backdrop.kt
interface Backdrop {
    val isCoordinatesDependent: Boolean
    val offsetResidualX: Float
    val offsetResidualY: Float
    fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
        downscaleFactor: Float = 1f,
    )
}
```

`coordinates` 就是消费者自己的 `LayoutCoordinates`。**它决定了"从录制层的哪个位置取像素"** ——
这是所有坐标问题的根源（§6.2）。

### 1.2 三种"东西"必须分清

| 名称 | 是什么 | 在哪 | 新项目能用吗 |
|---|---|---|---|
| **库 API** | `rememberLayerBackdrop`、`Modifier.layerBackdrop`、`Modifier.drawBackdrop`、`Modifier.textureBlur`、`Modifier.glassPanel`、`glassSurface`、`GlassDefaults`、`GlassStyle`… | `miuix-blur` / `miuix-material` / `miuix-glass` | ✅ 直接用 |
| **example-only** | `rememberBlurBackdrop`、`rememberCombinedBackdrop`、`LocalTopAppBarBackdrop`、`LocalTopLayer`/`TopLayerHost`、`GlassPageHost` | `miuix/example/shared/...` | ⚠️ 逻辑可参考，代码要自己写 |
| **GIF 助手自建** 🏠 | `GlassScope`、`LocalPageBackdrop`、`LocalRootBackdrop`、`rememberPageBackdrop`、`ContentBackdropBox`、`Os4ListPage`、`LocalRailInset`、`CombinedBackdrop` | `GifHelper/.../ui/*.kt` | ❌ 不存在的库 API，必须重新设计 |

> 本条是纪律：**库里没有 `rememberBlurBackdrop`，也没有 `rememberCombinedBackdrop`。**
> 它们分别是 `miuix/example/shared/src/commonMain/kotlin/utils/PageUtils.kt:294` 与
> `.../component/liquid/CombinedBackdrop.kt:46`。
> 库里也没有 `LocalRootBackdrop` / `LocalRailInset`（全仓库零命中）。

### 1.3 三层"消费者"分类

| 类别 | 例子 | 采样源应该是 | 宿主 |
|---|---|---|---|
| **页面框架型** | 顶栏、底栏、侧栏、Tab 条 | 页面正文层 / 一级页整屏层 | 页面 Scaffold 或页面根部 |
| **页面局部型** | 卡片、按钮、悬浮工具条 | 页面正文层 | 页面内 |
| **全局弹层型** | Dialog、BottomSheet、Dropdown、Popup | **必须能覆盖弹层整个区域的层** | **最外层 Scaffold** |

第三类的宿主是硬性的：`GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` 的
`renderInRootScaffold` **默认是 `true`** 📌（`GlassDialog.kt:55`、`GlassBottomSheet.kt:59`、
`GlassDropdownPreference.kt:73`），它们会去找**最外层** Scaffold 的 `LocalRootDialogStates`
（`miuix-ui/.../utils/MiuixPopupUtils.kt:597`，库内 internal），由那个 Scaffold 的 `MiuixPopupHost` 绘制。

---

## 2. 最小可用接线（先跑通这个，再谈结构）

```kotlin
// 1) 建采样源
val base = pageBaseColor()                      // 组合期取色（不能在 onDraw 里调 @Composable）
val backdrop = rememberLayerBackdrop {          // 📌 miuix-blur/.../LayerBackdrop.kt:39
    drawRect(base)                              // 关键：先垫底色，见 §5 红线 R4
    drawContent()                               // 再录内容
}

// 2) 录制层：只能包"要被采到的内容"，且不能包含消费者自己
Box(
    Modifier
        .fillMaxSize()
        .layerBackdrop(backdrop)                // 📌 唯一的录制入口
) {
    Content()
}

// 3) 消费者：与录制层是兄弟，不在录制层内部
GlassTopAppBar(
    title = "标题",
    backdrop = backdrop,                        // 传采样源
)
```

三条不能动的约束：

1. **消费者不能是录制层的后代**（否则采样到自己 → 见 R1）。
2. **`drawRect` 必须在 `drawContent` 之前**（否则空白区域透明 → 见 R4）。
3. **录制层不能为了"让消费者有背景"而把消费者包进来**（这是最常见的错误改法 → 见 R2）。

---

## 3. 标准接线步骤（9 步，逐项验收）

### 步骤 1：确定页面背景是什么

先回答"这一页最底下铺的是什么"：

| 页面背景 | 录制层怎么处理 | 例子 |
|---|---|---|
| 纯色（`surface`） | 录制层**垫同色**再录内容 | 常规列表页 |
| 整屏渐变 / 图片 / 视频 | 录制层**不垫色**（`opaqueBase = false`），只录背景层 + 内容层 | About 页 |
| 透明（背后是别的页面） | 不要做玻璃，或把采样源指向真正有像素的层 | 弹层（采样源必须是底层页面） |

判据：**"这一页的底色和 `MiuixTheme.colorScheme.surface` 一样吗？"** 不一样就不能用垫色录制层。

### 步骤 2：建立正确的录制区域

录制区域 = **所有可能被采样到的像素来源**的并集，且**不含消费者**。

- 录制区域太小 → 消费者某些位置没有像素（R3）。
- 录制区域包含消费者 → 自采样（R1）。

### 步骤 3：确认背景在录制层里真的被画了

这是最容易被跳过的一步。检查方法（GIF 助手用语）：

> **"这个组件所在的位置，在被采样的那层里是谁画的？画了没有？"**

三种"看起来有、实际没有"的情况：

1. 录制层没垫底色，内容又是稀疏列表 → 卡片间隙是透明的。
2. 页面背景画在录制层**外面**（例如渐变铺在 `Scaffold` 之外），录制层里只有内容。
3. 背景层自己会淡出（滚动到底变透明）→ 某段时间内是空的。

第 2、3 种必须用**叠加采样源**（§6.3）解决，不能靠调参。

### 步骤 4：确定页面局部 Glass 组件

| 组件 | 要不要 backdrop | 采样谁 |
|---|---|---|
| `GlassTopAppBar` | 要（默认 null，不传就没玻璃） | 页面正文层 |
| `GlassCard`（本项目封装） | 要 | 页面背景层（About 页） |
| `GlassIconButton` | 可选；在 `GlassTopAppBar` 内可继承 | 继承顶栏 |
| `GlassTabRow` / `GlassSegmentedTabRow` | 要 | 顶栏或页面层 |
| `GlassFloatingActionButton` / `GlassFloatingToolbar` | 可选 | 页面层 |

### 步骤 5：确定全局弹层 Glass 组件

`GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` / `GlassDropdowmPopup` 等：
**必须挂在最外层 Scaffold 的子树里**，采样源必须是"录在弹层坐标空间里的层"。

### 步骤 6：分别提供正确的采样源

一个页面通常需要 **2 个**采样源，不是一个：

| 采样源 | 服务谁 | 位置要求 |
|---|---|---|
| 页面正文层 | 顶栏、卡片、局部按钮 | 页面内（`Scaffold` 内容槽） |
| 一级页整屏层 | 底栏、侧栏、**所有弹层** | **在分页容器外面** |

二级页（不在分页容器里）可以合并成一个，但弹层仍推荐用最外层那一层（§6.2 的坐标原因）。

### 步骤 7：确认节点关系（本步骤决定会不会闪退）

画出你的树，然后在每个 `layerBackdrop` 上标记"它包含哪些玻璃消费者"。
**任何消费者出现在自己采样源的子树里 = 必须改结构。**

### 步骤 8：检查坐标、Padding、Insets、裁剪

- 录制层是否被 `padding` 收窄？（不应收窄，见 §6.4）
- 消费者是否在 `padding` 之后画？（`contentModifier` vs `modifier`，见 R6）
- 弹层宿主是否和录制层在同一个坐标空间？（§6.2）
- 屏幕圆角 / 状态栏区域是否被裁掉？（§6.5）

### 步骤 9：运行实际渲染验证

按 [11](11-testing-and-acceptance.md) §2 的三级证据执行。**模拟器通过 ≠ 真机通过**。

---

## 4. 三种合法宿主结构

### 4.1 结构 A：两级宿主（官方 example 思路）📌

```
Scaffold(containerColor = Transparent, contentWindowInsets = 0)   ← 弹层宿主（最外层）
└─ Box(fillMaxSize)
   ├─ Box(fillMaxSize).layerBackdrop(rootBackdrop)   ★录制层 R（整屏，录在分页容器外）
   │   └─ Pager / NavDisplay
   │      └─ 页面
   │         └─ Scaffold(topBar = GlassTopAppBar(pageBackdrop))
   │            └─ content
   │               └─ Box.layerBackdrop(pageBackdrop)  ★录制层 P（页面正文）
   └─ GlassNavigationBar(backdrop = rootBackdrop)     ← 底栏是 R 的兄弟
      GlassNavigationRail(backdrop = rootBackdrop)    ← 侧栏是 R 的兄弟
```

- 顶栏采 P，顶栏是 P 的兄弟（在 `Scaffold` 的 `topBar` 槽，不在 `content` 槽）。
- 底栏/侧栏采 R，它们是 R 的兄弟。
- 弹层由最外层 Scaffold 绘制，天然在 R 外面，采 R。

📌 官方对应物：`miuix/example/shared/src/commonMain/kotlin/AppContent.kt` 的根 `Scaffold`
（`:308-311`）+ `Modifier.layerBackdrop(sidebarBackdrop)`（`:349`）+ `PersistentGlassNavigationRail`（`:368`）
+ `TopLayerHost`（`:378`）；`CompactScreenLayout` 自己建
`rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }`（`:636-700`）。

### 4.2 结构 B：GIF 助手的一级页结构 🏠

与 A 同构，差别在于**底栏下沉到一级页内部**：

```
GifApp
└─ Scaffold(Transparent, insets=0)                        ← 弹层宿主（README 硬约束 11）
   └─ Box(background(pageBaseColor()))
      ├─ NavDisplay
      │  └─ entry<Route.Root>
      │     └─ Box(fillMaxSize)
      │        ├─ Box(fillMaxSize).layerBackdrop(rootBackdrop)   ★录制层 R
      │        │   └─ CompositionLocalProvider(LocalRootBackdrop = rootBackdrop)
      │        │      └─ HorizontalPager → 三个一级页 → Os4ListPage
      │        │           ├─ topBar = PageTopBar(backdrop = contentBackdrop)
      │        │           └─ ContentBackdropBox → Box.layerBackdrop(contentBackdrop) ★录制层 P
      │        └─ GlassNavigationBar(backdrop = rootBackdrop)   ← R 的兄弟
      └─ GlassNavigationRail(backdrop = rootBackdrop)           ← R 的兄弟（在 NavDisplay 外层）
```

为什么底栏要下沉到 `entry<Route.Root>` 里：为了让底栏跟着一级页**被二级页盖住**，
而不是用 `stack.size` 挂载/卸载（后者会闪，见 [12](12-known-issues-and-solutions.md) C20）。
代价是：底栏和录制层必须在**同一层**里做兄弟。这个取舍是**本项目特定的**，
新项目如果底栏本来就画在 App 层，用结构 A 即可。

### 4.3 结构 C：不分页的简单应用（推荐给新项目起步）

```
Scaffold(Transparent, insets=0)
└─ Box(fillMaxSize).layerBackdrop(rootBackdrop)     ★唯一的录制层
   └─ NavHost / 内容
      └─ 页面（不要再建第二层）
```

顶栏、底栏、弹层全部采 `rootBackdrop`。代价是**顶栏采样包含顶栏自身所在区域之外的一切**，
所以顶栏必须画在录制层**外面**（`Scaffold` 的 `topBar` 槽），否则就是自采样。

> 这正是 GIF 助手早期 `SIGSEGV` 的成因：它把录制层上移到了 `Scaffold` 自身，
> 而顶栏与内容共用同一个 backdrop（见 R2）。

---

## 5. 层级红线（每条都有真实事故）

> 每条格式：**错误结构 → 现象 → 根因 → 正确结构 → 回归验证**。
> 案例编号对应 [12](12-known-issues-and-solutions.md)。

### R1 消费者不能是录制层的后代

- **错误**

```kotlin
Box(Modifier.layerBackdrop(backdrop)) {
    GlassNavigationBar(backdrop = backdrop)   // ← 采到自己
}
```

- **现象**：直接闪退。`Fatal signal 11 (SIGSEGV)`，logcat 里 `libhwui` 的
  `RenderNode::prepareTreeImpl` 递归几百层（栈溢出）。
- **根因**：录制层在绘制时会包含子节点的绘制命令；子节点又在消费这个 layer → 递归。
- **正确**：消费者与录制层同级。

```kotlin
Box {
    Box(Modifier.layerBackdrop(backdrop)) { Content() }
    GlassNavigationBar(backdrop = backdrop)        // 兄弟
}
```
- **回归验证**：反复进出该页面 20 次，logcat 搜 `prepareTreeImpl` / `Fatal signal` 零命中。

### R2 不能用"把消费者包进录制层"来解决顶栏没背景

- **错误**（GIF 助手 1.4 真实事故）：

```kotlin
Scaffold(
    modifier = Modifier.layerBackdrop(pageBackdrop),   // ← 录制层上移到 Scaffold
    topBar = { GlassTopAppBar(backdrop = pageBackdrop) },
) { ... }
```

- **现象**：`SIGSEGV`。官方 `GlassPage.kt` 敢这么写，是因为它顶栏采的是**另一个** backdrop
  （`LocalTopAppBarBackdrop`，example-only）；顶栏与内容**共用**同一个 backdrop 时，这样写就是自采样。
- **正确**：录制层留在内容槽内，顶栏在 `topBar` 槽。

### R3 采样源必须在消费者的整个绘制区域里都有像素

- **错误**：弹层挂在卡片上，采样源是**卡片局部**录制层。卡片 56dp、弹层 5 项 →
  弹层下半截落在卡片之外。
- **现象**：弹层下半截**只剩材质、透不出背景**，还能看到一条**横向硬边**；
  同一页另一个高卡片的下拉看起来正常（因为弹层落在卡内）——同一组件、两种表现。
- **正确**：采样源换成**整页层**（一级页用录在分页容器外的那层）。
- **回归验证**：打开每个可能伸出容器的弹层，滚到底看有没有硬边。

### R4 录制层必须先垫底色再录内容

- **错误**：`rememberLayerBackdrop { drawContent() }`
- **现象**：玻璃面板上出现"糊影 / 脏斑"：卡片间隙、列表 padding 区原样透出内容；
  换个页面颜色还会显出一块**比页面亮的方块**。
- **根因**：裸 `rememberLayerBackdrop` 只录内容，空白处是透明的；模糊透明 = 透明。
- **正确**

```kotlin
val surfaceColor = pageBaseColor()
rememberLayerBackdrop {
    drawRect(surfaceColor)   // 必须与页面真正铺的那层同色
    drawContent()
}
```
- **注意**：垫色必须等于页面**真实底色**。用"由卡片色反推的次级表面"会差 30 个灰阶
  （深色 `#1E1E1E` vs 页面 `#000000`，见 [12](12-known-issues-and-solutions.md) C14）。

### R5 不要在不合适的层级重复录制

- **现象**：滚动卡顿、发热；同屏出现 2~3 层整屏录制。
- **根因**：每多一个 `layerBackdrop` 就多一次整屏 GPU 录制。
- **做法**：
  - 卡片**不要**自带录制层（GIF 助手 `GroupCard` 曾经自带，R5 后删除）。
  - 分页容器每页一个录制层时，预组合页数要调小（GIF 助手把 `beyondViewportPageCount`
    从官方 example 的 4 改成 1，理由就是"每页挂一个玻璃录制层"）。
- **验证**：`adb shell dumpsys gfxinfo` 看掉帧；慢速滚动录屏逐帧看。

### R6 前景让位用 `contentModifier`，不要挪整条（含背景带）

- **错误**：侧栏展开时给顶栏传 `modifier = Modifier.railStartInset()`。
- **现象**：顶栏那条模糊背景带**跟着变窄**，右侧露出一条没有玻璃的区域。
- **正确** 📌 `GlassTopAppBar` 有 `contentModifier`（只挪前景）与 `modifier`（整体）。
  玻璃档传 `contentModifier`，非玻璃档（普通 `TopAppBar` 没有 contentModifier）才传 `modifier`。
- 官方对照：`miuix/example/.../GlassPage.kt:334`。

### R7 录制层不能跟着"内容让位"收窄

- **错误**：侧栏展开时把录制层 `padding(start = 262.dp)`。
- **现象**：侧栏左半段没有像素可采，玻璃只剩一块平色。
- **正确**：录制层始终满宽，**只让内容的内边距**。
- 官方对照：`AppContent.kt:300-350`（满宽录制 + 页面内边距让位）。

### R8 不要在组合期解包逐帧动画状态

- **错误**

```kotlin
val inset = LocalRailInset.current()        // ← Dp 值，组合期求值
Box(Modifier.padding(start = inset))        // 每帧重组整个页面
```

- **现象**：侧栏展开/折叠的 300ms 里整页重组、列表重排、滚动被打断。
- **正确**：下发**函数** `() -> Dp`，在量测/绘制期读：

```kotlin
// 提供方：函数
val railInset: () -> Dp = { (250.dp + 12.dp) * state.progress }

// 消费方 1：自定义 PaddingValues 的 calculate*Padding 里读
override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
    safeStart + railInset()

// 消费方 2：Modifier.layout 里读（Modifier.padding 只吃即时值）
fun Modifier.railStartInset(base: Dp = 0.dp): Modifier = layout { measurable, constraints ->
    val extra = (base + railInset()).roundToPx()
    val p = measurable.measure(constraints.offset(horizontal = -extra))
    layout(p.width + extra, p.height) { p.placeRelative(extra, 0) }
}
```

> `GlassNavigationRailState.progress` 是逐帧变化的动画值（`miuix-glass/.../GlassNavigationRail.kt`），
> 官方迁移指南把它列为红线之一。
> 🏠 GIF 助手实现：`GifHelper/.../ui/Adaptive.kt:51`、`:67-78`。

---

## 6. 两大定律

### 6.1 定律一：兄弟关系定律

> **玻璃消费者与它的采样源，必须在同一个父节点的不同子树上。**

推论：一个 `layerBackdrop` 最多能服务"它的兄弟及其后代"，**不能服务它自己的祖先**。

### 6.2 定律二：坐标空间定律

> `drawBackdrop` 用消费者的 `LayoutCoordinates` 去录制层里取像素。
> **这条链路只在"同一个坐标空间"里自洽。**

具体失效场景（GIF 助手实测数字）：

`HorizontalPager` 的每一页按页宽错位摆放。页面内的录制层，其窗口坐标是**槽位坐标**
（第 2 个 tab 上报 x = 1080）；而弹层由最外层 Scaffold 绘制，上报**真实坐标**（x = 0）。
两者相减 = `-1080` → 采样矩形整块落在录制层之外 → 弹层只剩材质、看不到背景。

```
修复前： panelWin=(0,1777)  recWin=(1080,0)  offset=(-1080,1777)
修复后： panelWin=(0,1777)  rootWin=(0,0)    offset=(0,1777)
```

🔴 可迁移性：**这是库的通用约束**，不是 GIF 助手的特例。任何"弹层宿主在分页容器之外"的应用都会遇到。

**规则**：

- 分页容器（`HorizontalPager` / `ViewPager` / 分栏）内的录制层 → 只能给**同一页内**的组件采样。
- 弹层 / 跨页 / 全屏浮层 → 采样源必须录在**分页容器外面**。
- 二级页（不在分页容器里）→ 页面层坐标就是真实坐标，可以给弹层用。

### 6.3 叠加采样源

有些页面的采样源会"分时段为空"，这时要把两层叠成一个 `Backdrop`。

📌 原理：`Backdrop` 是接口，`drawBackdrop` 可以转发。example 里有一份（`rememberCombinedBackdrop`，
`miuix/example/.../CombinedBackdrop.kt:46`），**不在库里**。

🏠 GIF 助手的实现（`GifHelper/.../ui/CombinedBackdrop.kt`，Apache-2.0，移植自 example）：

```kotlin
@Stable
class CombinedBackdrop(val first: Backdrop, val second: Backdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent
    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY
    override fun DrawScope.drawBackdrop(
        density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Float,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}
```

什么时候需要它：

| 场景 | 为什么单层不够 |
|---|---|
| 整屏渐变背景 + 内容列表 | 渐变层滚到底淡出；内容层顶部有留白 → 各缺一段时间 |
| 视频/图片背景 + 内容 | 视频暂停/切换时像素变化或为黑 |
| 半透明遮罩层 + 内容 | 遮罩本身透明 |

关键约束：两层都必须与消费者是**兄弟**（R1 仍然成立）。

### 6.4 录制层尺寸 vs 内容让位

| 对象 | 是否让位 | 原因 |
|---|---|---|
| 录制层 | ❌ 不 | 让位后侧栏/边缘没有像素（R7） |
| 页面内容内边距 | ✅ 让 | 防止被侧栏压住 |
| 顶栏前景（`contentModifier`） | ✅ 让 | 标题居中在内容区 |
| 顶栏背景带（`modifier` 之外的部分） | ❌ 不让 | 视觉上要满宽 |

### 6.5 Insets 与裁剪

- **状态栏**：页面内容要延伸到状态栏下（`enableEdgeToEdge()`），背景带要盖住状态栏区域。
- **屏幕圆角**：导航转场可用 `NavDisplayEffects(enableCornerClip = true, cornerClipRadius = …)` 📌
  （`miuix-nav/.../NavDisplayEffects.kt:63`）；圆角半径取设备真实值（GIF 助手用
  `getRoundedCorner(POSITION_BOTTOM_LEFT).radius`，见 [05](05-navigation-and-adaptive.md) §4.5）。
- **底部**：录制层要覆盖到屏幕底部，否则底栏玻璃采不到导航栏区域的像素。

---

## 7. 回退策略（三级）

玻璃不能"要么全有要么全崩"，必须有三层回退：

### 7.1 用户开关（必须有）

```kotlin
// 唯一决策点：用户偏好 && 设备能力
val glassEnabled = prefs.glassEnabled && isGlassSupported()
```

理由 📌：`isRenderEffectSupported()` 只是 `SDK_INT >= 31`（`RenderEffect.android.kt:10-12`），
**查不出某台 GPU 上模糊会不会翻车**；`isRuntimeShaderSupported()` 只是 `SDK_INT >= 33`。
真机上确实出现过"内容不糊开、只被压暗"的情况 🔴，所以用户必须能关。

GIF 助手做法 🏠：`LocalGlassEnabled` 是**唯一**决策点（`GifHelper/.../ui/GlassSupport.kt:63`），
页面里禁止再出现 `isRenderEffectSupported()` 判断 —— 否则会出现"顶栏玻璃、底栏实心"的割裂。

### 7.2 每个组件都要能用 `backdrop = null` 工作

📌 库的事实：`GlassTopAppBar` / `GlassNavigationBar` / `GlassIconButton` / `GlassBottomSheet` /
`GlassDialog` / `GlassDropdownPreference` 的 `backdrop` 参数**都可空**；
`Modifier.glassPanel` 在 `backdrop == null` 或 `!isRuntimeShaderSupported()` 时走 `fallback`
（`GlassPanel.kt:46-58`）。

所以业务侧只需要：

```kotlin
GlassTopAppBar(
    title = title,
    backdrop = if (glassEnabled) pageBackdrop else null,   // ← 一行
)
```

⚠️ 注意：`backdrop == null` 在**部分组件**上意味着"完全没有材质"（例如 `Modifier.textureBlur`
没有采样源时会把卡片画成透明板）。凡是"关掉玻璃就看不见入口"的地方，必须显式给不透明回退：

```kotlin
if (glassEnabled) GlassCard(backdrop = bgBackdrop) { Content() }
else Card(colors = CardDefaults.defaultColors(color = cardColor())) { Content() }
```

🏠 GIF 助手把这个判断做在 `GlassCard(backdrop: Backdrop?)` 内部（`backdrop == null` 时退回
`cardColor()` 不透明卡）。

### 7.3 软件渲染 / 未知 GPU 的降级

GIF 助手加了一个**渲染器探测** 🏠（`GifHelper/.../ui/GlassSupport.kt:45-60`）：
用 `GL_RENDERER` / `GL_VENDOR` + `Build.HARDWARE` 判断 `swiftshader` / `llvmpipe` / `ranchu` / `goldfish`，
只用于一处决策：顶栏要不要走 `progressiveBlur`（AGSL 路径）。

为什么必须这么做 🔴：本机 AVD（`-gpu swiftshader_indirect`）上 AGSL **能编译、不抛异常**，
但产出的是**错位的未模糊副本**；官方示例 APK 在同一台机器上是同一个现象 →
这是渲染器问题，不是接线问题。

> 但**不要**因此自创替代视觉（例如自绘渐变冒充模糊）。GIF 助手第九轮这么干过一次，
> 第十轮整段删除（`grep gradientFilled|accentBrush|BgPalette` 零命中）。
> 降级只能走库自己的 fallback 路径。

---

## 8. 一个完整的可运行骨架（新项目起步用）

> 这是**结构示意**，参数请按项目调整。不要直接复制 GIF 助手的 `GlassScope` / `Os4ListPage`。
> 其中 `LocalAppBackdrop` 是本例自建的 CompositionLocal（库里没有），需要自己声明：
> `val LocalAppBackdrop = compositionLocalOf<LayerBackdrop?> { null }`。

```kotlin
@Composable
fun AppShell() {
    val glassEnabled = LocalGlassEnabled.current

    // 一级页整屏层：给底栏、侧栏、所有弹层用
    val rootBackdrop = rememberLayerBackdrop {
        drawRect(MiuixTheme.colorScheme.surface)
        drawContent()
    }

    Scaffold(                                  // 弹层宿主（最外层）
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 录制层：只包内容
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (glassEnabled) Modifier.layerBackdrop(rootBackdrop) else Modifier)
            ) {
                CompositionLocalProvider(LocalAppBackdrop provides rootBackdrop.takeIf { glassEnabled }) {
                    AppContent()               // 你的 NavHost / Pager
                }
            }

            // 底栏：录制层的兄弟
            if (!isWide) {
                GlassNavigationBar(
                    items = items, selectedIndex = idx, onSelect = { idx = it },
                    backdrop = rootBackdrop.takeIf { glassEnabled },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// 页面内：第二层录制层 + 顶栏
@Composable
fun Screen() {
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(MiuixTheme.colorScheme.surface)
        drawContent()
    }
    val overlayBackdrop = LocalAppBackdrop.current        // 弹层用一级页那层

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = { GlassTopAppBar(title = "标题", backdrop = pageBackdrop.takeIf { glassEnabled }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().then(Modifier.layerBackdrop(pageBackdrop))) {
            ListContent(pageContentPadding(padding))
        }
        // 弹层：Scaffold 内、录制层外
        MyDialogs(backdrop = overlayBackdrop ?: pageBackdrop)
    }
}
```

自检（结构 A 的 5 个"是"）：

1. 弹层宿主是最外层 `Scaffold`？
2. 底栏/侧栏与 `rootBackdrop` 同级？
3. 顶栏与 `pageBackdrop` 同级（在 `topBar` 槽）？
4. 弹层的采样源录在分页容器外面？
5. 两个录制层都先 `drawRect(surface)` 再 `drawContent()`？

---

## 9. 排错决策表（按现象查）

| 现象 | 先查 | 再查 | 对应红线 |
|---|---|---|---|
| 直接闪退（`prepareTreeImpl`） | 消费者是否在录制层里 | 录制层是否上移到了 Scaffold | R1、R2 |
| 玻璃位置完全没背景（只剩材质） | 采样源录在哪个容器 | 是否跨分页容器（差一页宽） | §6.2 |
| 玻璃某一段没有背景（有硬边） | 采样源在该区域有像素吗 | 是否需要叠加采样源 | R3、§6.3 |
| 玻璃面板上有"糊影"/脏斑 | 录制层有没有 `drawRect` | 垫色是否等于页面真实底色 | R4 |
| 玻璃面板上有一块比页面亮的方块 | 垫色用了哪个颜色 | 是否用了次级表面 | R4 |
| 顶栏背景带变窄/缺一条 | 是否用 `modifier` 挪整条 | 应改 `contentModifier` | R6 |
| 侧栏展开时整页卡 | inset 是否在组合期解包 | 是否用了 `padding(start = dp)` | R8 |
| 模拟器上正常、真机不对（或相反） | 是否软件渲染 | 真机补验 | §7.3、[11](11-testing-and-acceptance.md) |

---

## 10. 参考

- 官方 blur 指南：`miuix/docs/guide/blur.md`（含 API 31/33 分工与 `minSdk 33`）
- 官方 glass 指南：`miuix/docs/guide/glass.md`（含 `fallback` 与 `overrideLibrary` 说明）
- 库源码：`miuix-blur/.../{Backdrop,LayerBackdrop,LayerBackdropModifier,DrawBackdropModifier,TextureEffect}.kt`
- 官方 example：`miuix/example/shared/src/commonMain/kotlin/{AppContent.kt,GlassPage.kt,utils/PageUtils.kt}`
- 真实案例：`12-known-issues-and-solutions.md` C10~C19
- 本项目实现：`GifHelper/.../ui/{GlassScope,CombinedBackdrop,GlassSupport}.kt`
