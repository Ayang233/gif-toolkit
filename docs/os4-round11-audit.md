# 第十一轮：关于页顶栏玻璃修复、全项目 OS4 / Glass 覆盖性复核

本轮由真机截图触发：关于页顶栏出现一条边缘生硬的深色带，返回键左侧浮着一小块错位的玻璃色斑；
同一张截图里「查看源代码 / 开源许可」卡片压在图标与软件名称之上。

修复对象：`screens/AboutScreen.kt`、`ui/GlassScope.kt`，新增 `ui/CombinedBackdrop.kt`（移植自 Miuix 示例，Apache-2.0）。

版本：1.9（11）→ 1.10（12）；1.10 的二次修改见 §7。**真机验证结论待补**（本机模拟器是软件渲染，`textureBlur` / `progressiveTextureBlur`
不产出像素，顶栏这两条路径在上面的表现无法在这里复现）。

---

## 0. 现象与判定

真机截图里的三处异常：

| 现象 | 判定 |
|---|---|
| 顶栏下方一条硬边深色带 | **缺陷**：顶栏玻璃采到了空像素 |
| 返回键左侧一小块错位玻璃色斑 | **同一缺陷**的另一种表现（按钮胶囊也采同一层） |
| 卡片压住图标/标题，标题区不淡出 | **缺陷**：正文顶部那块透明占位首帧高度为 0 |

两者都只在真机出现，模拟器上分别表现为"什么都不画"和"只在打开瞬间闪一下"，所以前几轮都没抓到。

---

## 1. 根因一：顶栏采到了空像素

### 1.1 机制

`GlassTopAppBar` 内部是二选一（`GlassTopAppBar.kt:389-460`）：

- `progressiveBlur && backdrop != null` → 用 `progressiveTextureBlur` 采样传入的 backdrop；
- 否则 → 画 `bandBrush` 渐变带。

同时它会建一层 `progressiveBackdrop`（把传入 backdrop 的像素按顶栏坐标拷一份），
并通过 `LocalGlassTopAppBarContext` 把 `buttonBackdrop = progressiveBackdrop ?: rawBackdrop`
给顶栏里的 `GlassIconButton`（返回键）。

也就是说：**顶栏的模糊层与返回键胶囊，采的是同一份像素**。
这份像素为空时，两者一起花屏 —— 正好对应截图里的"深色带 + 错位色斑"。

### 1.2 为什么是空的

关于页的正文录制层是 `rememberGlassScope(opaqueBase = false)`：

- 不垫页面底色（背后是整屏渐变，垫了会盖住）；
- 正文顶部还有 40dp 的图标留白，列表首项是一块**透明**占位。

于是顶栏所在的那块区域，在正文录制层里**什么都没有画**。
`progressiveTextureBlur` 采到的是一张全透明贴图，画出来就是深色带 + 色斑，
而不是"没有模糊"。

同时它也不能改成只采渐变层：渐变层（`BgEffectBackground`）由
`alpha = { 1f - scrollProgress() }` 驱动，**滚到底时会整层淡出**，那时顶栏又没像素可采了。

### 1.3 修复

1. 新增 `ui/CombinedBackdrop.kt`：把两层 `Backdrop` 按顺序叠成一个采样源
   （与官方 `component/liquid/CombinedBackdrop.kt` 同一份逻辑，本项目不复用示例模块，所以移植一份）。
2. `PageTopBar` 增加 `backdrop: Backdrop? = null` 参数，默认仍是 `glass.contentBackdrop`
   （其余页面不受影响）。
3. 关于页传 `rememberCombinedBackdrop(渐变层, 正文层)`：

```kotlin
val contentBackdrop = glass.contentBackdrop
val topBarBackdrop: Backdrop? = when {
    !blurEnabled -> null
    contentBackdrop != null -> rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
    else -> bgBackdrop
}

PageTopBar(..., backdrop = topBarBackdrop)
```

两层都是顶栏的**兄弟节点**（不在顶栏的子树里），因此不会采样到自己（AGENTS 第 1 条）。
任何时刻至少有一层有真实像素：首屏靠渐变层，滚到底靠正文层（卡片）。

这条也写进了 AGENTS 硬约束 1，作为新的**覆盖性约束**：
"采样源必须在组件的整个绘制区域里都有像素"。

---

## 2. 根因二：正文顶部占位首帧高度为 0

### 2.1 机制

关于页的版式是「固定的标题区（Column） + 盖在上面的 LazyColumn」，
LazyColumn 首项是一块透明 Spacer，高度按标题区实测高度算（`headerHeight + ...`）。

`headerHeightPx` 原初值是 `0`，靠 `onSizeChanged` 在第一帧布局后回填。于是：

- 第一帧（以及真机上可能持续到下一帧）Spacer 高度 ≈ 0 → 卡片直接压到图标/标题上；
- 同时 `scrollProgress` 的判据是 `headerHeightPx > 0`，为 0 时恒返回 `0f` →
  标题区**永远不淡出** —— 这就是截图里"卡片盖住标题，但标题还透出来"的原因。

### 2.2 修复

首帧兜底 300dp（与官方 `AboutPage.kt` 的 `logoHeightDp = mutableStateOf(300.dp)` 同值）：

```kotlin
var headerHeightPx by remember { mutableIntStateOf(0) }
val headerHeight = with(density) {
    (if (headerHeightPx > 0) headerHeightPx else 300.dp.roundToPx()).toDp()
}
```

兜底值只影响第一帧：实测回调之后立刻换成真实高度（模拟器上卡片位置在 2 秒截图里已经归位）。

---

## 3. 全项目 OS4 / Glass 覆盖性复核

这一轮换了个查法：不再只看"组件用对没"，而是逐个 Glass 消费点问三件事 ——
**采的是哪一层？那一层在它的绘制区域里有像素吗？在它所有状态下都有像素吗？**

### 3.1 Glass 消费点逐项

| 页面 / 组件 | 采样源 | 该区域有像素吗 | 所有状态下都有吗 | 判定 |
|---|---|---|---|---|
| 一级页顶栏（工具/作品/设置） | 页面正文层（`opaqueBase = true`） | 有（垫了页面底色） | 有 | 通过 |
| 二级页顶栏（工具/主题/许可） | 页面正文层 | 有 | 有 | 通过 |
| **关于页顶栏** | 渐变层 + 正文层（本轮） | 有 | 有（两层互补） | **本轮修复** |
| 返回键胶囊 `GlassIconButton` | 顶栏的 `progressiveBackdrop`（拷贝自上项） | 随上项 | 随上项 | **随上项修复** |
| 底栏 `GlassNavigationBar` | 一级页根录制层（满屏） | 有 | 有 | 通过 |
| 侧栏 `GlassNavigationRail` | 同上 | 有 | 有 | 通过（与官方示例逐像素一致，第十轮 §1.4） |
| 下拉 `GlassDropdownPreference` | `LocalRootBackdrop ?: 页面层` | 有 | 弹层伸到内容外时边缘会退化成页面色 | 通过（第十轮已改） |
| 弹层 `GlassBottomSheet` / `GlassDialog` | `pageOverlay` 传入的 root 层 | 有（满屏） | 有 | 通过 |
| 关于页图标 / 标题 `textureBlur` | 渐变层 `bgBackdrop` | 首屏有 | **滚到底后渐变淡出 → 无** | **已知边界**：图标/标题此时也已淡出（alpha 0），不会被看到 |
| 关于页卡片 | 背景录制层（渐变层 `bgBackdrop`，1.10） | 有（整屏渐变） | 渐变滚到底会淡出，此时卡片退成混合色材质；不是硬边 | **1.10 改玻璃卡**（§7.2） |

### 3.2 OS4 组件使用（本轮复查结论）

沿第十轮覆盖矩阵逐页对了一遍，本轮没有发现新的组件误用。差异只在两条，且都是有意的：

1. **关于页骨架曾是自建 Scaffold**（1.9 及以前，不走 `Os4ListPage`）。
   这正是本次顶栏问题的土壤：自建骨架里"顶栏采样源"与"正文录制层"的关系要手写，
   而 `Os4ListPage` 已经把这层关系封装好了。
   **1.10 已实施**：给 `Os4ListPage` 增加 `background` / `largeHeader` / `topBarBackdrop` 三个参数，
   把关于页并了回去，详见 §7.1。
2. **关于页卡片在 1.9 及以前不用 `textureBlur`**：参考项目整张卡由模糊层绘制，模糊失效时卡片会连同两个入口一起消失。
   本项目当时优先保证入口可用，保留 0.82 不透明的 `cardColor`。
   **1.10 已改**：卡片走 `GlassCard`，用 `backdrop = null` 时不透明回退来保住"入口不能消失"这条底线，详见 §7.2。

### 3.3 本轮没有改动、但值得记住的边界

- 关于页图标/标题用的 `textureBlur` 采渐变层，滚动到底后渐变淡出、采样为空 ——
  但此时它们的 `alpha` 也已经被 `scrollProgress` 推到 0，不可见，所以不构成现象。
  如果以后把它们的淡出阈值改晚，这里会立刻暴露成"花屏"，要一起改。
- 顶栏的渐进模糊在**软件光栅化**环境仍走 `bandBrush`（`isSoftwareRenderer`），
  真机才走 AGSL。

---

## 4. 验证

### 4.1 模拟器（`swiftshader_indirect`）

| 项 | 结果 |
|---|---|
| 编译 / 打包（debug + release） | 通过 |
| 打开关于页无崩溃（`FATAL EXCEPTION` / `StackOverflowError` / `prepareTreeImpl` 均无） | 通过 |
| 卡片不再压住标题、版本号在卡片上方 | 通过（`_archive/round11/v2_about_glasson.png`） |
| 顶栏玻璃的实际观感 | **无法验证**（软件渲染下这条路不产出像素） |

### 4.2 真机待验证（请按这个顺序看）

1. 关于页首屏：顶栏应当**完全透明**，只有返回键的玻璃胶囊；
   不应再有深色横带、也不应有错位色斑。
2. 关于页往下滚：顶栏应长出模糊带，模糊的是卡片内容，边缘是渐隐的（不是硬边）。
3. 关于页首屏：图标与软件名称应是液态玻璃质感（采样背景），二者与渐变同源。
4. 其余页面回归：主页/工具/设置顶栏、底栏、侧栏、下拉、作品操作面板、删除确认。

如果第 1 条仍有色斑，说明还存在第三层采样问题，需要现场抓 `adb logcat` +
`adb shell dumpsys gfxinfo` 与一张原图。

---

## 5. 改动文件

| 文件 | 目的 |
|---|---|
| `ui/CombinedBackdrop.kt`（新增） | 两层录制层叠成一个采样源（移植自 Miuix 示例，Apache-2.0） |
| `ui/GlassScope.kt` | `PageTopBar` 增加 `backdrop` 参数（默认仍是正文层），用于"正文层覆盖不到顶栏"的页面 |
| `screens/AboutScreen.kt` | 1.9：顶栏改采「渐变层 + 正文层」；标题区高度首帧兜底 300dp。1.10：改走 `Os4ListPage`，卡片改 `GlassCard` |
| `ui/PageChrome.kt` | 1.10：`Os4ListPage` 新增 `background` / `largeHeader` / `topBarBackdrop`，把整屏背景、固定头部、首帧兜底收进骨架 |
| `ui/Widgets.kt` | 1.10：新增 `GlassCard`（官方 `Card` + 官方 `textureBlur`，无采样源时退回不透明卡） |
| `build.gradle.kts` | 1.10 / 12 |
| `AGENTS.md` | 硬约束 1 增补"覆盖性约束"；硬约束 15 改为"关于页走 `Os4ListPage`"，补 `GlassCard` 与三个新参数 |
| `CHANGELOG.md` / `docs/release-notes-1.9.md` / `docs/release-notes-1.10.md` | 版本条目 |

## 6. 回退

- 只回退顶栏：把 `AboutScreen` 的 `topBarBackdrop = ...` 删掉，即回到"采正文层"（旧行为）。
- 只回退首帧兜底：把 `Os4ListPage` 里 `headerHeight` 的 `if` 分支去掉。
- 只关玻璃卡：把 `AboutScreen` 的 `GlassCard(backdrop = ...)` 改成 `GlassCard(backdrop = null)`，
  卡片立刻退回不透明 `cardColor()`（等价于玻璃关闭态），其余版式不变。
- 回退 1.10 整轮：`git revert` 本轮提交即可，不涉及资源与依赖变化。
- **不建议**单独把关于页改回自建 Scaffold：那会同时丢掉 §7.1 的骨架关系与 §7.3 的滚动收起，
  且重新引入 1.9 里"顶栏采样源要手写、首帧高度要手算"的两个坑。

---

## 7. 1.10：关于页并回 `Os4ListPage`、卡片玻璃化

> 本节记录 1.10 的二次修改。§1–§4 描述的是 1.9 的状态；§3.1 / §3.2 里与本节冲突的两行
> 已在原处标注 1.10 的结论。

1.10 没有新的观感目标，只做一件事：把 1.9 里"关于页自己搭骨架"这个土壤去掉。
1.9 的两处修复（顶栏双层采样、首帧 300dp 兜底）都保留，但搬进骨架、由参数表达，
关于页只写自己的内容。

### 7.1 骨架：`Os4ListPage` 的三个新参数

`ui/PageChrome.kt` 新增三个参数，分别对应关于页此前手写的三段关系：

| 参数 | 作用 | 关于页怎么传 |
|---|---|---|
| `background: (@Composable (progress: () -> Float) -> Unit)?` | 在 `Scaffold` **外面**铺页面根部背景。传了它以后 Scaffold 透明、顶栏在收起前透明、`progress == 1f` 后才上 `surface` 实色 | `BgEffectBackground(fillMaxSize, bgModifier = layerBackdrop(bgBackdrop), alpha = { 1f - progress() })` |
| `largeHeader: (@Composable (progress: () -> Float) -> Unit)?` | 固定在列表**下面**的头部；骨架自动在列表首项插一块与头部等高的透明占位，并把"首项滚过头部"的进度交给页面 | `AboutHeader(progress, blurEnabled, bgBackdrop, logoColors, version)` |
| `topBarBackdrop: Backdrop?` | 顶栏采样源覆盖；`null` 时沿用页面正文层（其余页面不传，行为不变） | 玻璃开：`rememberCombinedBackdrop(bgBackdrop, contentBackdrop)`；玻璃关：`null` |

三个参数是**互相关联**的，不要拆开单用：`background` 决定顶栏"收起前透明、收起后实色"，
`largeHeader` 提供收起进度与透明占位，`topBarBackdrop` 保证顶栏在任意滚动位置都有像素。
缺少任意一个，就会回到 1.9 之前那种"顶栏采样源在页面里猜、首帧高度手算"的状态。

**收起进度**（骨架内统一计算，页面不再自己数）：

```kotlin
val headerProgress: () -> Float = {
    when {
        largeHeader == null -> if (isScrolled) 1f else 0f
        listState.firstVisibleItemIndex > 0 -> 1f
        headerHeightPx > 0 ->
            (listState.firstVisibleItemScrollOffset.toFloat() / headerHeightPx).coerceIn(0f, 1f)
        else -> 0f
    }
}
val collapsed by remember { derivedStateOf { headerProgress() == 1f } }
```

顶栏的 `isScrolled` 取 `collapsed`（不是 `canScrollBackward`），所以收起前顶栏不上背景带；
顶栏标题透明度取 `((headerProgress() - 0.35f) / 0.65f)`，即头部收到 35% 之后标题才浮出来，
与官方 `AboutPage.kt` 同一套。`background != null` 时，顶栏背景色在 `collapsed` 之前是
`Color.Transparent`，之后才是 `MiuixTheme.colorScheme.surface`，避免把整屏渐变切出一条白条。

**首帧兜底**也从页面搬进了骨架：`headerHeightPx` 初值 0，量到之前按 300dp 算
（与官方 `AboutPage.kt` 的 `logoHeightDp = mutableStateOf(300.dp)` 同值），
`onSizeChanged` 回填后立刻换成真实高度。透明占位高度 =
`headerHeight + (headerPadTop - listTop) + 20.dp`，其中头部比列表多 40dp 顶部留白，
所以实际是"头部高度 + 58dp"。

### 7.2 卡片：`GlassCard` = 官方 `Card` + 官方 `textureBlur`

`ui/Widgets.kt` 新增 `GlassCard`（`Widgets.kt:188-219`）。它不是新的绘制路径，
而是把参考项目 InstallerX 关于页里那段写法抽成一个组件（`MiuixAboutPage.kt:612-638`）：

```kotlin
Card(
    modifier = Modifier
        .padding(horizontal = 12.dp)
        .padding(bottom = 12.dp)
        .textureBlur(
            backdrop = backdrop,
            shape = RoundedCornerShape(CardDefaults.CornerRadius),
            blurRadius = 60f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.6f))),
            ),
        ),
    colors = CardDefaults.defaultColors(Color.Transparent),
) { content() }
```

几个取值的理由：

| 项 | 取值 | 理由 |
|---|---|---|
| 卡片颜色 | `Color.Transparent` | 卡片本体不画色，材质全部由 `textureBlur` 采样背景得到 |
| 形状 | `RoundedCornerShape(CardDefaults.CornerRadius)` | 与普通 `Card` 同圆角，不另造一个半径 |
| 模糊半径 | `60f` | 卡片是"薄玻璃"，比顶栏/底栏低一档；再高会糊成一块平色 |
| 混合色 | `surface` alpha 0.6 | 保留文字对比度，同时不引入第二套颜色来源 |
| 无采样源 | 退回 `cardColor()` 不透明卡 | 玻璃关闭 / 页面没有录制层时，两个入口不能整块消失 |

**和 1.9 的 `cardColor 0.82` 相比，这次没有放弃"入口不能消失"这条底线**：
回退由 `backdrop == null` 承担，而不是由"永远不做玻璃"承担。
玻璃开启时卡片与图标/标题同采 `bgBackdrop`（渐变层），三者材质同源，
不会出现"图标是玻璃、卡片是实色"的割裂。

已知边界：渐变层在 `progress == 1f` 时 `alpha = 0`，此时卡片已经滚到顶栏下方，
材质会退成 `surface` 0.6 的混合色。这是 §3.3 已有边界的延伸；真机验收时重点看这一刻
有没有硬边。如果出现，应把卡片采样源改成 `topBarBackdrop`（渐变层 + 正文层），
而不是回退成不透明卡。

### 7.3 滚动收起：`fillParentMaxHeight` + 三段淡出

1.9 的关于页首项是一块固定高度的 `Spacer`，内容比屏幕短时 `LazyColumn` 根本不可滚，
头部永远收不回去。1.10 在内容 item 外包一层 `Box`，先放一个撑满父高度的 `Spacer`：

```kotlin
item(key = "about") {
    Box {
        Spacer(Modifier.fillParentMaxHeight())
        Column(Modifier.padding(bottom = 12.dp)) {
            GroupHeader("关于")
            GlassCard(backdrop = ...) { ... }
        }
    }
}
```

这样内容再短也把 item 撑到一屏高，列表一定能滚，头部才收得干净。
头部仍然画在列表**下面**（先画 `Column`，后画 `LazyColumn`），列表内容盖着它往上走。

淡出沿用参考项目的三段阈值（`MiuixAboutPage.kt:482 / 517 / 546`）：

| 元素 | 公式 | 区间 | 缩放 |
|---|---|---|---|
| 版本号 | `((p - 0.05f) / 0.15f)` | 0.05 – 0.20 | 只改 alpha |
| 软件名称 | `((p - 0.20f) / 0.15f)` | 0.20 – 0.35 | alpha + `scale = 1 - p * 0.05f` |
| 图标 | `((p - 0.35f) / 0.15f)` | 0.35 – 0.50 | alpha + `scale = 1 - p * 0.05f` |

版本号只做透明度，不再叠缩放：它最早消失，14sp 再缩 0.95 看不出来，反而多一次
`graphicsLayer` 计算。三段的**起点**与参考项目一致，终点都留了 0.15 的过渡宽度。

### 7.4 验证

| 项 | 结果 |
|---|---|
| `./gradlew :assembleDebug :assembleRelease` | 通过（2026-11-19） |
| debug APK | 45,081,746 B |
| release APK | 3,311,811 B |
| release dex 中 `开始按钮配色对比` | **0** |
| release dex 中 `无素材（禁用态）` | **0** |
| release dex 中 `调试（仅调试包可见）` | **0** |
| release dex 中类名 `ButtonColorPreview` | **0** |
| 同一组搜索在 debug dex 的对照 | 1 / 1 / 1 / 71（说明搜索本身有效） |

release 包只有 1 个 `classes.dex`（3,102,836 B），按 UTF-8 字节逐段搜索，不是 `aapt`
资源表搜索。1.10 只改骨架与卡片组合，没有新增玻璃绘制路径；顶栏、卡片仍然不是
`bgBackdrop` 录制层的子节点（第 1 条的结构没变）。

**没有在 1.10 上重跑模拟器交互，也没有跑真机**：本机模拟器是软件渲染，
`textureBlur` 在这条路径上不产出像素，看不出卡片与图标/标题的实际观感。
关于页的首屏、玻璃卡、滚动收起三项留给真机验收（清单见 §4.2）。

### 7.5 自造轮子自查表

把项目里"不是直接调库"的 UI 代码过了一遍。判定标准只有一条：
**有没有在库之外重画玻璃 / 另造一套颜色来源。** 没有的话，再分"移植 / 适配 / 业务组件"。

| 组件 | 它实际是什么 | 来源 / 官方对应 | 判定 | 保留理由 |
|---|---|---|---|---|
| `GlassCard` | `Card` + `textureBlur` 的组合封装，约 30 行 | InstallerX `MiuixAboutPage.kt:612-638`；Miuix `Card` + `textureBlur` | **组合，不是新画法** | 圆角、内边距、颜色都走 `CardDefaults`；模糊、混合、裁剪全在库里。"透明卡 + 模糊 + null 回退"要在多处保持一致，抽一处比复制安全 |
| `CombinedBackdrop` | 把两层 `Backdrop` 按顺序叠成一个采样源 | Miuix 示例 `component/liquid/CombinedBackdrop.kt` | **官方示例移植** | 上游只把它放在 example，不发布 artifact，无法直接依赖；逻辑逐行保留（Apache-2.0） |
| `ProgressButton` | 轨道是按钮本身，`drawWithContent` 在圆角内画进度填充 | 移植自 InstallerX `widgets/ProgressButton.kt`（Apache-2.0），底层是官方 `Surface` + `ButtonDefaults` | **生态移植** | 官方 Miuix 没有"按钮内进度轨道"组件；生成进度必须画在按钮内部，不能另拉一条进度条 |
| `PreviewStage` | 固定宽高比 + 窗口高度上限 + 空态占位的媒体画布 | 无官方对应；颜色用 `pageColor()`，文字/图标用 Miuix | **业务组件** | 预览的比例、矮窗口下的 32% / 42% 上限是本项目需求，不涉及玻璃 |
| `NoteText` | `Text` + `footnote1` + `onSurfaceVariantSummary` + 28dp 左线 | 官方 `Text` / `TextStyles` | **样式别名，不是轮子** | 统一"说明段落"的字号与左线（卡片 12dp + 行内 16dp）；改一处全站生效 |
| `pageContentPadding` | 把 `innerPadding` / 底栏留白 / 横向安全区 / Rail 让位并成一个 `PaddingValues` | 官方示例 `utils/PageUtils.kt:186` 的同名函数 | **适配层** | 官方那套依赖示例自己的 `LocalAppPageInsets`；我们的底栏与 Rail 是自定义宿主，必须把这几个值并进同一处，否则又会散出手写 100dp 留白 |
| `railStartInset` | `Modifier.layout` 在量测期读取 Rail 让位 + `base` | 官方 `appPageSearchInsets`（`PageUtils.kt:135`）同一手法 | **适配层** | `LocalRailInset` 是逐帧动画值，组合期解包会整页重组（迁移指南红线）；`base` 承载常规边距 + 起始边安全区 |
| `isSoftwareRenderer` | 按 GL renderer / `Build.HARDWARE` 判软件光栅化 | 无官方对应；降级目标是官方 `progressiveBlur = false` 的渐变带 | **能力判定，不是替代视觉** | 库的 `isRenderEffectSupported()` 只查 `SDK_INT >= 31`，查不出 SwiftShader 上"模糊不产出像素"；不降级会让顶栏失去背景带 |
| `AnimatedMiuixTheme` | 逐色插值后交给官方 `MiuixTheme(colors = ...)` | 官方 `MiuixTheme` + `Colors.copy()` | **包装层** | 官方 controller 是硬切；Crossfade 会重绘整棵树并抖录制层。逐色插值只改颜色，布局与状态不动 |

结论：1.10 新增的只有 `GlassCard`（组合）和 `Os4ListPage` 的三个参数；
**没有新增自绘玻璃、渐变或第二套色值来源**。表中其余项都是此前轮次已有的移植/适配，
本轮只做复核，没有改动。

两个"静默漂移"点值得继续盯（都不会编译报错）：
`AnimatedMiuixTheme.lerpColors` 需要跟随 `Colors.copy()` 的新字段；
`CombinedBackdrop` 需要跟随上游示例的签名/叠加顺序。
