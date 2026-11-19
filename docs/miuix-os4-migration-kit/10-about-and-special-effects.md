# 10 · About 页、动态渐变与特殊效果

> 本篇覆盖三件"看起来很炫、实现很脆"的东西：**整屏动态渐变**、**`textureBlur` + `DstIn`**、
> **固定大标题的滚动收起**。
> 三者共同的前提是：**采样源必须在该区域所有状态下都有像素**。

---

## 1. About 页的整体结构

一个 OS4 风格的 About 页有四个层次：

```
页面根部（Scaffold 之外）
└─ 动态渐变背景（整屏，延伸到状态栏下）
   └─ Scaffold(containerColor = Transparent, contentWindowInsets = 0)
      ├─ topBar：玻璃顶栏（收起前透明，收起后上实色）
      ├─ 固定头部（图标 / 名称 / 版本），画在列表下面
      └─ 可滚动列表（盖着头部往上走），首项是透明占位
          └─ 玻璃卡（入口行）
```

### 1.1 GIF 助手的实现方式：把"骨架"参数化

🏠 `Os4ListPage` 用三个参数表达这三种特殊需求（`GifHelper/.../ui/PageChrome.kt:172-223`）：

> 下文的 `progress` / `collapsed` / `headerHeight` / `pageContentPadding(...)` 都是"骨架里已有的变量"，
> 不是库 API；自己实现时按 §4 的公式算。

| 参数 | 语义 | 内部做了什么 |
|---|---|---|
| `background: (@Composable (progress: () -> Float) -> Unit)?` | 页面根部整屏背景 | 把 `Scaffold` 设成 `Transparent` + `contentWindowInsets = 0`；背景铺在 Scaffold **外面** |
| `largeHeader: (@Composable (progress: () -> Float) -> Unit)?` | 固定头部 | 头部画在列表**下面**；列表首项自动插一块与头部等高的透明占位；`progress` = 首项偏移 / 头部高度 |
| `topBarBackdrop: Backdrop?` | 顶栏采样源覆盖 | 默认用正文录制层，这里可以换成叠加层 |

> **为什么背景必须在 Scaffold 外面**：`Scaffold` 的 `containerColor` 与 `contentWindowInsets`
> 会在顶栏下方切出一条硬边，渐变就断在状态栏下面了（[12](12-known-issues-and-solutions.md) C08）。

### 1.2 如果不想改造骨架，最小实现

```kotlin
Box(Modifier.fillMaxSize()) {
    BgEffectBackground(Modifier.fillMaxSize(), alpha = { 1f - progress() }) {}
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = { GlassTopAppBar(title = "关于", backdrop = topBarBackdrop, isContentScrolled = collapsed) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Header(progress)                       // 画在下面
            LazyColumn(contentPadding = pageContentPadding(padding)) {
                item { Spacer(Modifier.height(headerHeight + 58.dp)) }   // 透明占位
                item { Box { Spacer(Modifier.fillParentMaxHeight()); AboutCards() } }
            }
        }
    }
}
```

---

## 2. 动态渐变背景（OS3 风格）

### 2.1 它是什么

MIUIX 生态里的"OS3 背景"：4 段颜色在超大范围内缓慢流动 + 模糊，用 **AGSL 运行时着色器**
（`RuntimeShader`）在 GPU 上逐帧计算。

🏠 GIF 助手移植版：`GifHelper/.../ui/bg/BgEffect.kt`（17KB）+ `BgEffectShader.kt`（片段源码）。

```kotlin
@Composable
fun BgEffectBackground(
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,      // 只叠在渐变层上（用来挂 backdrop 录制层）
    alpha: () -> Float = { 1f },          // 整体透明度（随滚动进度递减）
    dynamic: Boolean = true,              // 色段是否继续轮换
    content: @Composable (BoxScope.() -> Unit),
)
```

🏠 `BgEffect.kt:127-137`。

### 2.2 实现要点

| 要点 | 做法 |
|---|---|
| 能力判定 | `remember { isRuntimeShaderSupported() }`；不支持时退化成普通 `Box`（`BgEffect.kt:138`、`:159-162`） |
| 预设 | `OS3_PHONE_LIGHT/DARK`、`OS3_PAD_LIGHT/DARK` 四套（`:64-106`） |
| 动画 | 色段轮换周期 `preset.colorInterpPeriod * 500` ms + `spring(dampingRatio = 0.9f, stiffness = 35f)`（`:154-155`） |
| 逐帧性能 | 只 `invalidateDraw`，不触发重组（`:265-281`） |
| `alpha` 参数 | 用 `() -> Float`（绘制期读），避免滚动时每帧重组 |

`alpha` 为什么要用函数？因为它的值来自列表滚动偏移，逐帧变化。
传 `Float` 会让整页在滚动时重组（同 [06](06-glass-architecture.md) R8 的道理）。

### 2.3 采样层挂载（关键）

```kotlin
val bgBackdrop = rememberLayerBackdrop()

BgEffectBackground(
    modifier = Modifier.fillMaxSize(),
    bgModifier = if (blurEnabled) Modifier.layerBackdrop(bgBackdrop) else Modifier,   // ← 只录渐变
    alpha = { 1f - progress() },
) {}
```

**不要**把录制层套在内容外面：内容是渐变层的**兄弟**，套进去就成"采样到自己"（[06](06-glass-architecture.md) R1）。

---

## 3. `textureBlur` + `DstIn`：让图标与标题"由背景生成"

### 3.1 效果

图标与软件名称不是用固定颜色画的，而是**取背景的模糊结果 → 混色 → 用内容的 alpha 当遮罩**。
所以它们的材质与背后的渐变"同源"，滚动/变色时会一起变。

### 3.2 完整写法

```kotlin
// 混合色（深/浅各一套，与 KSU / InstallerX 逐字相同）
val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
val logoBlend = remember(isDark) {
    if (isDark) listOf(
        BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
    ) else listOf(
        BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
    )
}
val logoColors = BlurDefaults.blurColors(blendColors = logoBlend)   // ← 必须用工厂，见 §3.4

Image(
    painter = painterResource(R.drawable.ic_app_mark),
    colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
    modifier = Modifier
        .requiredSize(132.dp)
        .textureBlur(
            backdrop = bgBackdrop,
            shape = RectangleShape,                 // 形状只裁采样区域
            blurRadius = 150f,                      // 库内部按 BlurDefaults.MaxBlurRadius 夹紧
            colors = logoColors,
            contentBlendMode = BlendMode.DstIn,     // ← 用内容 alpha 当遮罩
            enabled = true,
        ),
)

Text(
    "GIF 助手",
    color = MiuixTheme.colorScheme.onBackground,
    fontWeight = FontWeight.Bold,
    fontSize = 35.sp,
    modifier = Modifier.textureBlur(
        backdrop = bgBackdrop,
        shape = RectangleShape,
        blurRadius = 150f,
        colors = logoColors,
        contentBlendMode = BlendMode.DstIn,
        enabled = true,
    ),
)
```

🏠 `GifHelper/.../screens/AboutScreen.kt:240-289`。

### 3.3 `DstIn` 到底做了什么

`contentBlendMode = DstIn` 的语义：**用内容层（图标/文字的形状与 alpha）去裁"采样+模糊+混色"的结果**。

```
背景采样 → 模糊 → 三档混色 ─┐
                          ├─ DstIn：只在内容不透明处保留 ─→ 最终像素
内容（字形/剪影的 alpha） ──┘
```

所以：

- 图标资源必须是**纯色剪影**（单色 + 透明背景）。GIF 助手为此专门做了一个
  `ic_app_mark.xml`（纯白填充、路径与徽章主体一致）。
- `ColorFilter.tint` 只影响"内容层"的颜色，**不改变**"内容被替换"这件事。
- 一旦采样结果为空，最终结果就是**空**——图标与标题整块消失（§5）。

### 3.4 为什么必须用 `BlurDefaults.blurColors(...)`

📌 `BlurColors` 是 `@Immutable data class`，但 `blendColors: List<…>` 对 Compose 来说是**不稳定类型**。
直接用构造函数会每帧重建 effect。`BlurDefaults.blurColors(...)` 内部按参数 `remember`。

🏠 源码注释也写了这一条（`AboutScreen.kt:138-141`）。

### 3.5 尺寸推导（不是随手给的数）

GIF 助手的实测推导：

| 量 | 值 | 说明 |
|---|---|---|
| 图标画布 | 108 × 108 单位 | `ic_app_mark.xml` |
| 实际主体范围 | x = 14..94（**80 单位**） | 占画布 **74.1%** |
| 目标容器 | 100dp | 与 KSU 的 `Modifier.size(100.dp)` 同档 |
| 需要的采样尺寸 | 100 / 0.741 ≈ **135dp** | 取 **132dp**（左右各留 1dp 余量，避免圆角被裁） |
| `requiredSize` | 132dp | 主体约 97.8dp |
| 实测可见主体 | **98 × 49dp**（`density 320` 下 196 × 98 px） | 逐像素包围盒 |

> KSU 用 245dp / InstallerX 用 160dp，是因为它们的应用图标是**近方形**，靠图形自带留白把主体收进容器；
> GIF 助手的 mark 是 **2:1 横向胶片条**，横向再放大就会裁掉两端齿孔。
> **换项目必须重算这个比例**，不能照抄 132dp。

---

## 4. 固定大标题的滚动收起

### 4.1 进度怎么算

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
```

🏠 `GifHelper/.../ui/PageChrome.kt:231-239`。

要点：

- **首帧兜底 300dp**（`headerHeightPx` 初值 0 时用 300dp）。用 0 会让透明占位高度为 0 →
  首帧卡片直接压住图标/标题，且进度判据 `> 0` 不成立 → 头部永远收不回去
  （[12](12-known-issues-and-solutions.md) C23）。
  300dp 与官方 `AboutPage.kt` 的 `logoHeightDp` 初值一致。
- 头部高度用 `onSizeChanged` 回填，**不要**写死。

### 4.2 三块的淡出阈值

| 元素 | 起始 | 结束 | 缩放 |
|---|---|---|---|
| 版本号 | `progress = 0.05` | 0.20 | 不变 |
| 软件名称 | `0.20` | 0.35 | → 0.95 |
| 图标 | `0.35` | 0.50 | → 0.95 |

```kotlin
Modifier.graphicsLayer {
    val p = ((progress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
    alpha = 1f - p
    scaleX = 1f - p * 0.05f
    scaleY = 1f - p * 0.05f
}
```

🏠 `GifHelper/.../screens/AboutScreen.kt:221-303`；阈值与 InstallerX `MiuixAboutPage.kt:482/517/546` 一致（转引）。

### 4.3 顶栏的两种状态

| 状态 | 背景 | 标题 |
|---|---|---|
| `progress < 1` | **完全透明**（背景自己铺满） | 跟着 `progress` 淡入 |
| `progress == 1` | `surface` 实色 | 完全不透明 |

非玻璃档是这个规则；玻璃档的 band 由库按滚动进度自己上色，不看你传的 `background`。

### 4.4 短内容也要能滚

```kotlin
item {
    Box {
        Spacer(Modifier.fillParentMaxHeight())   // 撑满一屏
        Cards()
    }
}
```

否则内容不足以滚动时，头部永远收不回去（`scrollProgress` 恒 0），顶栏永远透明。

---

## 5. 软件渲染环境下 `textureBlur` 的失效（必读）

### 5.1 现象

在本机 AVD（`-gpu swiftshader_indirect`）上，**玻璃开启**时 About 页的图标与软件名称
**整块不可见**；关掉玻璃开关就正常显示。

### 5.2 根因

`textureBlur(..., contentBlendMode = DstIn)` 是"用采样结果**替换**内容"。
软件光栅化上这条 AGSL 路径能编译、不抛异常，但**不产出像素** →
内容被一个空结果替换 → 什么都没有。

🔴 同类现象也出现在官方示例 APK 上（同机同设置，标题与两张卡片都不显示），
所以这是**渲染器能力问题，不是接线问题**。

### 5.3 处置（三选一，不要选第四种）

| 方案 | 做法 | 评价 |
|---|---|---|
| A 保持原生实现，标注真机待验证 | 什么都不改 | ✅ 推荐（GIF 助手 1.8 之后的结论） |
| B 检测到软件渲染时**跳过** `textureBlur` | `blurEnabled = glassEnabled && !isSoftwareRenderer` | ✅ 可接受（GIF 助手 1.10 做法） |
| C 换一套自制渐变 | ❌ 禁止 | 与真机不一致，必然返工（GIF 助手第九轮踩过，第十轮整段删除） |

方案 B 的代码：

```kotlin
val blurEnabled = LocalGlassEnabled.current && !isSoftwareRenderer
// 所有 textureBlur 都挂在 if (blurEnabled) 之后
```

🏠 `GifHelper/.../screens/AboutScreen.kt:111`、`GifHelper/.../ui/GlassSupport.kt:45-60`。

> `isSoftwareRenderer` 的判据：`GL_RENDERER`/`GL_VENDOR` 里出现
> `swiftshader`/`llvmpipe`/`softpipe`/`software`/`emulator opengl`，或 `Build.HARDWARE` 含
> `ranchu`/`goldfish`。主线程没有 EGL 上下文，`glGetString` 常返回 null，所以必须有第二条判据。

### 5.4 真机验收清单（🔴 尚未完成）

- [ ] 首屏：图标与名称呈液态玻璃质感（不是纯色剪影）
- [ ] 首屏：顶栏只有返回键玻璃胶囊，无深色带、无错位色斑
- [ ] 下滚：顶栏长出边缘渐隐的模糊带（不是硬边矩形）
- [ ] 滚到底：图标/名称已淡出，顶栏变实色且无接缝
- [ ] 渐变在状态栏下连续（无横线）
- [ ] 玻璃卡在 `progress == 1`（渐变 `alpha = 0`）时没有硬边

---

## 6. 关于页的采样源：为什么需要叠加两层

| 层 | 覆盖时段 | 缺的时段 |
|---|---|---|
| 渐变层（`bgBackdrop`） | 首屏有像素 | 列表滚到底后整体淡出 → 空 |
| 正文层（`contentBackdrop`） | 滚动后有卡片经过 | 首屏顶栏区域是透明的（顶部 40dp 是图标留白） |

单采任一层，都有一段时间顶栏采到**全透明** → `progressiveTextureBlur` 贴图全透明 →
真机表现为**一条边缘生硬的深色带 + 返回键左侧一小块错位色斑**。

修法：叠加两层。

```kotlin
val topBarBackdrop = when {
    !blurEnabled -> null
    contentBackdrop != null -> rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
    else -> bgBackdrop
}
```

🏠 `GifHelper/.../screens/AboutScreen.kt:147-151`；`CombinedBackdrop` 实现见 [06](06-glass-architecture.md) §6.3。

**不要**为了"让顶栏采到渐变"在页面根部再套一层 `layerBackdrop` —— 那样顶栏就成了录制层的子节点，
采样到自己 → `libhwui` 栈溢出（[06](06-glass-architecture.md) R1/R2）。

---

## 7. 玻璃卡（入口行）

```kotlin
GlassCard(backdrop = if (blurEnabled) bgBackdrop else null) {
    ArrowPreference(title = "查看源代码", summary = "在 GitHub 上查看源代码", onClick = { … })
    ArrowPreference(title = "开源许可", summary = "本应用自身许可证与所用第三方开源库的许可条款", onClick = onOpenLicenses)
}
```

要点（细节见 [08](08-custom-component-migration.md) §6）：

- 卡片本体透明 + `textureBlur(shape = RoundedCornerShape(CardDefaults.CornerRadius), blurRadius = 60f,
  blendColors = surface.copy(alpha = 0.6f))`。
- `backdrop == null` 退回不透明卡（入口不能消失）。
- 入口行**不带前置图标**，描述写在 `summary`。
- 不要自造卡片渐变或第二套卡片色。

---

## 8. 许可页

| 要素 | 做法 |
|---|---|
| 清单 | 一个 `LicenseCatalog`（本项目 / 第三方 / NOTICE），`thirdPartyLibraries` 一行一项 |
| 正文 | `assets/licenses/*.txt`（GIF 助手：`gpl-3.0.txt` 35,823 B、`apache-2.0.txt` 11,560 B、`notice.txt` 1,195 B） |
| 路由 | **不要**为每个许可开一个 Route；用 `Route.License(index)` 复用一页 |
| 排版 | 等宽字体不必，但必须可选中复制；长文本用 `LazyColumn` |
| 入口 | About 页一个「开源许可」行，本项目 GPL / 第三方 Apache / NOTICE 在同一页分组 |

🏠 参考：`GifHelper/.../ui/LicenseCatalog.kt`、`screens/LicensesScreen.kt`。

---

## 9. 检查清单

- [ ] 背景铺在 `Scaffold` **外面**，`containerColor = Transparent`、`contentWindowInsets = 0`
- [ ] 渐变层挂了录制层，且内容不在录制层里面
- [ ] 顶栏采样源覆盖"所有滚动位置"（单层够不够？需要叠加吗？）
- [ ] `textureBlur` 用 `BlurDefaults.blurColors(...)` 构造
- [ ] `contentBlendMode = DstIn` 且图标资源是纯色剪影
- [ ] 图标采样尺寸按画布主体比例算过（不是照抄 132dp）
- [ ] 首帧头部占位有兜底高度（300dp）
- [ ] 短内容也有 `fillParentMaxHeight` 撑满
- [ ] 软件渲染下有降级（跳过 `textureBlur`），且没有自制替代视觉
- [ ] 真机验收清单已跑（🔴 默认未跑）

---

## 10. 参考

- 官方 `RuntimeShader` / AGSL：`miuix/docs/guide/material.md`
- `textureBlur` 文档：`miuix/docs/guide/blur.md`
- 官方 About 示例：`miuix/example/shared/.../AboutPage.kt`（`logoHeightDp = 300.dp` 出处）
- KSU：`AboutMiuix.kt:189-203`（混合色）、`:313`（tint）、`clipToBounds`
- InstallerX Revived：`MiuixAboutPage.kt:612-638`（玻璃卡）、`:420-434`（混合色）、`:482/517/546`（阈值）、`:604-608`（`fillParentMaxHeight`）
- 🏠 GIF 助手：`screens/AboutScreen.kt`、`ui/bg/BgEffect.kt`、`ui/Widgets.kt`（`GlassCard`）、`ui/CombinedBackdrop.kt`
- 案例：`12-known-issues-and-solutions.md` C15（顶栏空像素）、C23（首帧占位）、C24（DstIn 失效）、C25（尺寸）、C26（骨架合并）、C27（玻璃卡）
