# 第十轮：Glass 横屏效果专项修复、About 原生液态玻璃复刻、开始按钮四方案实机对比、全项目 MIUIX OS4 完整性审查

第九轮报告：`docs/os4-round9-audit.md`

本轮起点（第九轮交付）：`e2911c8` / `34b8e15`，应用版本 `1.7 (9)`。
本轮**未改版本号**：这一轮是修复与审查轮，不是发布轮；版本仍为 `1.7 (9)`，
release APK 只用于真机验证（见 §8.3）。

---

## 0. 基线与取证条件

### 0.1 代码与上游基线

| 项目 | 本轮实测 |
|---|---|
| GifHelper HEAD | `34b8e15`，工作区在本轮开始时带 9 个已修改文件 + 1 个新增文件（第九轮之后被中断的改动，本轮在其基础上继续） |
| 本地 Miuix | `3177a684622a774276c49f52489a43d0d0ed123d`，工作区只有 `build-logic/src/main/kotlin/BuildConfig.kt` 一处补丁（`scripts/setup-miuix.sh` 打的 `workingDir = rootDir`） |
| 上游 main | `gh api repos/compose-miuix-ui/miuix/commits/main` → **`3177a684`，2026-11-18T04:51:05Z，`library: Refine glass floating action button`** |
| 是否需要升级 | **不需要**。本地 HEAD 与上游 main 同 SHA，零提交差异；本地 diff 只有上面那 4 行补丁 |
| 参考项目 | `tiann/KernelSU` `manager/.../about/AboutMiuix.kt`（本轮现取，450 行，存 `_archive/round10/ref_KSU_AboutMiuix.kt`）；`wxxsfxyzm/InstallerX-Revived` `app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt`（存 `_archive/round10/ref_InstallerX_MiuixAboutPage.kt`） |

### 0.2 设备与取证方式

- AVD `android16`（Android 16 / API 36，`google_apis/x86_64`），GPU `swiftshader_indirect`（软件光栅化）。
- 尺寸通过 `wm density` + `cmd window user-rotation lock` 控制：
  - `density 320` + 竖屏 → **540 × 1200 dp**（文中的「1200dp 竖屏」）
  - `density 320` + 横屏 → **1200 × 540 dp**（「1200dp 横屏」）
  - `density 420` + 横屏 → **914 × 411 dp**（「914dp 横屏」，普通手机横屏）
- 截图脚本重写为 `_archive/round10/shoot.ps1`：**按 uiautomator 文本定位点击**，不再写死坐标；
  截图走 `cmd /c adb exec-out screencap -p > file`（PowerShell 的 `>` 会按文本编码写坏 PNG）。
- 像素量测用 Pillow 逐点取色，所有色值都是**截图实测**，不是从主题源码推的。
- 官方对照：安装官方示例 `miuix/example/android/build/outputs/apk/debug/Miuix-v1.1.0(51)-debug.apk`
  （包名 `top.yukonga.miuix.uitest.debug`），同一台 AVD、同一密度、同一横屏姿态截图。

### 0.3 本轮截图目录

```
_archive/round10/
├── before/                       本轮修复前的基线（含 1200dp 横屏浅/深）
│   └── interrupted/              中断前那版构建的六套整页截图（light/dark/monet）
├── final/                        本轮修复后的全部截图
├── ref_official_dark_land.png    官方示例 App 的 1200dp 深色横屏（对照用）
├── ref_glass_04_scrolled.png     官方示例 App 的 1200dp 浅色横屏（第九轮留存，本轮复用）
├── ref_KSU_AboutMiuix.kt         KSU 关于页当前源码
├── ref_InstallerX_MiuixAboutPage.kt  InstallerX 关于页当前源码
└── shoot.ps1                     本轮截图脚本
```

---

## 1. 横屏 Glass 专项

### 1.1 先确认第九轮的成果没有被破坏

- 1200dp 横屏、侧栏展开：内容起始边 **338dp**，侧栏面板右边界 **324.5dp**，间隙 **13.5dp**，
  与第九轮一致（第九轮 §1.5 的验收数字）。
- 侧栏展开态的 `Rail` 位置、`pageContentPadding` 的 `safeStart + railInset` 组合、
  `PrimaryActionBar` 的 `railStartInset(base)` 都未改动。
- 本轮所有修改都**没有**碰 `PageChrome.pageContentPadding`、`Adaptive.railStartInset`、
  `LocalRailInset`、`App.kt` 的录制层位置与 Pager 结构。

### 1.2 「横屏 Glass 不理想」到底体现在哪：三种可能，逐项用证据排除

先按可能性把问题拆成三类，再逐个量：

| 可能原因 | 检查方式 | 结论 |
|---|---|---|
| A. 录制层跟着侧栏收窄 / 坐标错位 | 读源码 + 横竖屏对照截图 | **不存在**。4 个录制点全部 `fillMaxSize()`：`App.kt` 一级页层、`GlassScope.ContentBackdropBox`、`AboutScreen` 的渐变层、库内 `GlassTopAppBar.progressiveBackdrop`。没有任何一处挂在带 padding 的节点上 |
| B. 玻璃采到的底色与页面真实底色不一致 | 截图逐点取色（深色最关键） | **存在，已修**。见 §1.3（放大镜下的直接证据：底部主操作条） |
| C. 侧栏覆盖区域本来就没有内容可糊 | 与官方示例同尺寸同姿态对照 + 逐像素取色 | **存在，且官方同样如此**，不是本项目缺陷。见 §1.4 |

### 1.3 B 类：玻璃/遮挡层的底色与页面真实底色不同源（已修）

**证据（深色、静态配色、竖屏工具页，修复前 `before/interrupted/dark_03_tool.png`）**：
沿 `x = 40` 竖扫，卡片色 `#242424` 到 `y = 2170` 结束，从 `y = 2170` 往下整块变成 **`#1E1E1E`**，
而同一屏的页面底色是 **`#000000`** —— 页面上出现了一条比页面亮的横向色带（底部主操作条）。

原因不是玻璃，而是「谁在给这个区域铺底色」：

| 位置 | 修前用的颜色 | 实际计算值（深色静态） | 页面真实底色 |
|---|---|---|---|
| `rememberPageBackdrop()` 垫底色 | `pageColor()` | `#1E1E1E`（`#242424` 叠 18% 黑） | `#000000` |
| `PrimaryActionBar` 遮挡底色 | `pageColor()` | `#1E1E1E` | `#000000` |
| `Os4ListPage` 的 `Scaffold` | 不传 → 库默认 `surface` | `#000000` | `#000000` |

深色下 `pageColor()` 与 `surface` 差 **30 个灰阶**（浅色只差 1 个：`#F6F6F6` vs `#F7F7F7`），
所以这个问题在浅色下看不出来，深色一眼就能看到；横屏时主操作条更宽、占屏比例更大，也更容易被注意到。

**为什么 `pageColor()` 会与页面不一致**：它的注释写着「Monet 下 `surface` 与 `surfaceContainer`
会被压成同一个值，所以页面要由卡片色反推」。本轮在基线 `3177a684` 上逐套配色实测，这条前提已经不成立：

| 配色 | 页面（`surface`） | 卡片（`surfaceContainer`） | `pageColor()` |
|---|---|---|---|
| 静态浅色 | `#F7F7F7` | `#FFFFFF` | `#F6F6F6` |
| 静态深色 | `#000000` | `#242424` | `#1E1E1E` |
| Monet 浅色 | `#F9F9FF` | `#EDEDF4` | `#E5E5EB` |
| Monet 深色 | `#131316` | `#1F1F22` | — |

四套都不同值，页面与卡片本来就有层次。于是 `pageColor()` 现在只是一个「比页面深/浅一档的次级表面」
（预览舞台、对比卡用它是对的），**不能**再充当"页面底色"。

**修改**（详见 §9 文件清单）：

1. `ui/Widgets.kt` 新增 `pageBaseColor()` = 页面真正铺在屏幕上的那层（`surface`；AMOLED 下纯黑）。
2. `rememberPageBackdrop()` 的垫底色改用它。
3. `PrimaryActionBar` 的遮挡底色改用它。
4. `Os4ListPage` 的 `Scaffold` 显式 `containerColor = pageBaseColor()`（值与库默认相同，把不变量写死）。
5. `App.kt` 里那层根 `Box` 与 `NavDisplayEffects.backdropColor` 同步。

**修复前后实测（深色、工具页、`x = 40` 竖扫）**：

| | `y = 1900`（卡片） | `y = 2170`（操作条起点） | `y = 2210` | `y = 2300` |
|---|---|---|---|---|
| 修复前 | `#242424` | **`#1E1E1E`** | `#404040`（按钮） | `#040404` |
| 修复后 | `#242424` | **`#000000`** | `#404040`（按钮） | `#000000` |

截图：`before/interrupted/dark_03_tool.png` → `final/after_dark_03_tool.png`。

> 说明：这个色差在**竖屏**也存在，只是横屏更显眼。它属于"玻璃采样范围/底色"这一类问题
> （§2.2 第 1、2、6 条），不是布局问题。

### 1.4 C 类：侧栏覆盖区"没有内容可糊"

侧栏展开时，内容让位是作用在 `LazyColumn` 的 `contentPadding` 上（`PageChrome.pageContentPadding`），
列表项**在横向永远不会进入 `x < safeStart + 262dp` 的区间**。也就是说侧栏面板下面压着的是
"永远只有页面底色的区域" —— 玻璃没有内容可采样、可模糊，只剩材质本身。

这不是本项目的实现问题，**官方同构**：官方 `AppContent` 的 `overlayStart` + `PageUtils.contentInsets`
也是让内容避开侧栏。用官方示例 App 在同一台 AVD、同一 1200dp 横屏、同一展开状态下对照：

| 采样点（2400×1080 像素坐标） | 官方示例（深色） | 本项目（深色） |
|---|---|---|
| 页面空白处 `(1800, 1000)` | `(36,36,36)` | `(36,36,36)` |
| 侧栏面板内部 `(400, 1000)` | **`(24,24,24)`** | **`(24,24,24)`** |
| 侧栏面板内部 `(600, 880)` | `(24,24,24)` | `(24,24,24)` |
| 选中项背景 `(400, 290)` | `(24,24,24)` | `(24,24,24)` |

浅色（第九轮留存的官方横屏截图 `ref_glass_04_scrolled.png` 对比本轮 `final/after_1200_land_home.png`）：

| | 页面 | 侧栏面板 |
|---|---|---|
| 官方示例 | `(251,251,251)` | `(249,249,249)` |
| 本项目 | `(247,247,247)` | `(249,249,249)` |

**结论**：侧栏面板的玻璃色与官方示例**逐像素一致**。横屏"不够玻璃"的观感来自
「面板下没有可糊的内容」，官方在同样布局下也是同样的观感 —— 要改变它只能改交互结构
（让内容从侧栏下穿过），那是与官方相反的做法，本轮**不做**。

### 1.5 修复前后整屏差异（横屏 1200dp）

对 `before/before_1200_land_01_home.png` 与 `final/after_1200_land_home.png` 做逐像素差：

```
diff bbox = (192, 13) - (2301, 35)     # 只有状态栏时钟
变化像素 = 457
```

深色同样只有时钟在变。也就是说：**横屏首屏在修复前后完全一致**，
本轮不靠"顺手调一调透明度"制造差别（§1.3 的色带在二级页，不在首屏）。
这条是刻意的验收条件：修 Glass 不能改变已经正确的横屏布局。

### 1.6 横屏专项的额外发现：顶栏渐进模糊在软件渲染器上不可用

`PageTopBar` 玻璃档原本恒定 `progressiveBlur = true`（AGSL 渐进模糊）。
在 `swiftshader_indirect` 上 AGSL 能编译、不抛异常，但产出的是**错位的未模糊副本** ——
顶栏看起来像"字的影子"，滚动内容直接顶到标题上，横屏行距密时最明显。

处理：`ui/GlassSupport.kt` 新增 `isSoftwareRenderer`（判据：`GL_RENDERER` / `GL_VENDOR` 里出现
`swiftshader`/`llvmpipe`/`softpipe`/`software`，或 `Build.HARDWARE` 为 `ranchu`/`goldfish`），
`PageTopBar` 改为 `progressiveBlur = !isSoftwareRenderer`。
降级到的是 `GlassTopAppBar` **自己的默认路径**（`bandBrush` 渐变带，库的 `progressiveBlur` 默认值就是 false），
不是自造的替代视觉。真机（硬件 GLES）仍然走原生渐进模糊。

> 取证限制：这条只能证明"模拟器上是这个现象、这样降级后不再有错位副本"；
> 真机上渐进模糊是否正常，**未验证**（见 §8.4）。

### 1.7 横屏专项结论

| 问题 | 性质 | 状态 |
|---|---|---|
| 侧栏遮挡一级页内容 | 布局 | 第九轮已修，本轮未回退（复测 13.5dp 间隙） |
| 操作条/玻璃垫底色与页面底色不同源（深色差 30 灰阶） | 采样底色 | **已修**（`pageBaseColor()`） |
| 侧栏面板下没有内容可糊 | 与官方同构的必然结果 | **不是缺陷**，与官方示例逐像素一致 |
| 顶栏渐进模糊在软件渲染器上产出错位副本 | 渲染器能力 | 已在软件渲染器上降级到库默认渐变带；真机未验证 |
| 录制层收窄 / 坐标错位 / 自采样 | — | **不存在**（4 个录制点全部满宽，全部与消费方是兄弟节点） |

**是否达到与官方一致**：横屏的 `GlassNavigationRail` / `GlassNavigationBar` / `GlassTopAppBar`
三处都是官方组件 + 官方默认参数（唯一显式传的是 `bandBrush`/`bandOverhang`/`backgroundAlpha`，
与官方 `AdaptiveTopAppBar` 的写法一致），侧栏面板取色与官方示例**完全相同**（§1.4）。
本项目与官方唯一的结构性差异是"官方保留 Rail 进二级页、本项目不保留"（第七轮决定，未变）。

---

## 2. About 页：改为 KSU / InstallerX 的原生 `textureBlur` 实现

### 2.1 删除第九轮的自制渐变路径

第九轮为了在模拟器上显示图标与标题，加了一套与背景共用色段的渐变填色：
`BgPalette` / `rememberBgPalette()` / `gradientFilled()` / `accentBrush()` / `drawContent()` + `BlendMode.SrcIn`。
本轮**整段删除**（`ui/bg/BgEffect.kt` 净减 120 行），改为参考项目的原生机制。
代码里已无 `gradientFilled` / `accentBrush` / `BgPalette`（`grep` 为零命中）。

### 2.2 本轮实际读到的参考源码

**KernelSU `AboutMiuix.kt`**（本轮 `gh api` 现取，行号即当前 main）：

```kotlin
// 图标（:280-316）
Box(modifier = Modifier.size(100.dp).clipToBounds().graphicsLayer { ... })
    Image(
        modifier = Modifier.requiredSize(245.dp).then(
            if (enableBlur) Modifier.textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(0.dp),
                blurRadius = 150f,
                colors = BlurColors(blendColors = logoBlend),
                contentBlendMode = ComposeBlendMode.DstIn,
                enabled = true,
            ) else Modifier,
        ),
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        colorFilter = ColorFilter.tint(colorScheme.onBackground),
        contentDescription = null,
    )

// 标题（:317-347）
Text(
    modifier = Modifier.padding(top = 12.dp, bottom = 5.dp).graphicsLayer { ... }
        .then(if (enableBlur) Modifier.textureBlur(
            backdrop = backdrop,
            shape = RoundedCornerShape(0.dp),
            blurRadius = 150f,
            colors = BlurColors(blendColors = logoBlend),
            contentBlendMode = ComposeBlendMode.DstIn,
            enabled = true,
        ) else Modifier),
    text = state.appName,
    color = colorScheme.onBackground,
    fontWeight = FontWeight.Bold,
    fontSize = 35.sp,
)
```

`logoBlend`（`AboutMiuix.kt:189-203`）：

```kotlin
if (isInDark) listOf(
    BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
    BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
) else listOf(
    BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
    BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
    BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
)
```

**InstallerX Revived `MiuixAboutPage.kt`**（本轮现取）：

```kotlin
// 图标（:477-511）
Box(modifier = Modifier.size(88.dp).graphicsLayer { ... }) {
    if (backdrop != null) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
            modifier = Modifier.requiredSize(160.dp).textureBlur(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                blurRadius = 200f,
                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                colors = BlurColors(blendColors = logoBlend),
                contentBlendMode = BlendMode.DstIn,
                enabled = blurEnable,
            ),
            contentDescription = null,
        )
    } else if (uiState.appIcon != null) {
        Image(bitmap = uiState.appIcon, modifier = Modifier.size(80.dp), ...)
    }
}

// 标题（:513-536）
Text(modifier = ... .then(if (backdrop != null) Modifier.textureBlur(
    backdrop = backdrop,
    shape = RoundedCornerShape(16.dp),
    blurRadius = 150f,
    noiseCoefficient = noiseCoefficient,
    colors = BlurColors(blendColors = logoBlend),
    contentBlendMode = ComposeBlendMode.DstIn,
    enabled = blurEnable,
) else Modifier), ...)
```

`logoBlend`（`MiuixAboutPage.kt:420-431`）与 KSU 的三档颜色、混合模式**逐字相同**。

### 2.3 本项目实际实现（`screens/AboutScreen.kt`）

```kotlin
val bgBackdrop = rememberLayerBackdrop()          // 只录渐变那一层（不是内容外层）
BgEffectBackground(
    modifier = Modifier.fillMaxSize(),
    bgModifier = if (blurEnabled) Modifier.layerBackdrop(bgBackdrop) else Modifier,
    alpha = { 1f - progressProvider() },
) {}

// 图标：容器 100dp，主体采样 132dp，纯剪影、无底板
Box(Modifier.size(MarkBoxSize).graphicsLayer { ...; clip = true }) {
    Image(
        painter = painterResource(R.drawable.ic_app_mark),
        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
        modifier = Modifier.requiredSize(MarkSampleSize).then(
            if (blurEnabled) Modifier.textureBlur(
                backdrop = bgBackdrop,
                shape = RectangleShape,
                blurRadius = 150f,
                colors = logoColors,                        // BlurDefaults.blurColors(logoBlend)
                contentBlendMode = ComposeBlendMode.DstIn,
                enabled = true,
            ) else Modifier,
        ),
    )
}

// 标题：同一 backdrop、同一混合色、同样 DstIn
Text(
    text = "GIF 助手",
    modifier = Modifier.padding(...).graphicsLayer { ... }.then(
        if (blurEnabled) Modifier.textureBlur(
            backdrop = bgBackdrop,
            shape = RectangleShape,
            blurRadius = 150f,
            colors = logoColors,
            contentBlendMode = ComposeBlendMode.DstIn,
            enabled = true,
        ) else Modifier,
    ),
    color = MiuixTheme.colorScheme.onBackground,
    fontWeight = FontWeight.Bold,
    fontSize = 35.sp,
)
```

| 参数 | KSU | InstallerX | 本项目 | 说明 |
|---|---|---|---|---|
| backdrop 来源 | `rememberBlurBackdrop()` + 背景 `layerBackdrop` | 外层传入的 `backdrop` | `rememberLayerBackdrop()` 挂在渐变层 `bgModifier` 上 | 同一处接线：录制层是消费者的**兄弟**，不是父节点 |
| `shape` | `RoundedCornerShape(0.dp)` | `RoundedCornerShape(16.dp)`（图标与标题都是） | `RectangleShape`（= 0 圆角，等价 KSU） | 形状只决定**采样区域**，可见结果由 `DstIn` 的 alpha 决定 |
| `blurRadius` | 150f（图标与标题都是） | 200f（图标）/ 150f（标题） | 150f（两者） | 取 KSU 的一档；库内部按 `BlurDefaults.MaxBlurRadius` 夹紧 |
| `colors` | `BlurColors(blendColors = logoBlend)` | 同 | `BlurDefaults.blurColors(blendColors = logoBlend)` | 用工厂而不是直接构造：`List` 对 Compose 不稳定，直接构造会让 `textureBlur` 每帧重建 effect |
| `contentBlendMode` | `DstIn` | `DstIn` | `DstIn` | 与参考项目同一语义：采样结果只留在内容 alpha 内 |
| `enabled` | `true`（外层 `enableBlur` 判断是否挂 Modifier） | `blurEnable` | `true`（外层 `blurEnabled` 判断是否挂 Modifier） | 同 |
| 图标主体 | `ic_launcher_foreground` + `ColorFilter.tint(onBackground)` | `ic_launcher_monochrome`（不 tint） | `ic_app_mark` + `ColorFilter.tint(onBackground)`（取 KSU 写法） | 三者都是"单色剪影 + 透明背景" |
| 图标容器 | 100dp + `clipToBounds()` | 88dp（无裁剪） | 100dp + `graphicsLayer { clip = true }` | 取 KSU 的 100dp |
| 图标主体尺寸 | `requiredSize(245.dp)` | `requiredSize(160.dp)` | `requiredSize(132.dp)` | 见 §2.4 |
| 标题字号 | 35sp Bold | — | 35sp Bold | 与 KSU 同值 |
| 不可用时的回退 | 不挂 Modifier，正常画 tint 剪影 | `backdrop == null` → 真实应用图标位图 | 不挂 Modifier，正常画 tint 剪影（玻璃开关关闭时可见，截图见 §2.5） | 三种都是参考项目自身机制，没有额外自造视觉 |

差异说明（不隐藏）：

1. `shape` 取 `RectangleShape`（KSU 档）。InstallerX 的 16dp 圆角只影响采样区域四角，
   对本项目的图标/文字形状没有可见差异；选 KSU 是为了与 `blurRadius` 同源。
2. 没传 `noiseCoefficient`：只有 InstallerX 显式传 `BlurDefaults.NoiseCoefficient`，KSU 不传。
   本项目的 `BlurDefaults.blurColors()` 走库默认，与 KSU 一致。
3. InstallerX 在 `backdrop == null` 时会退回**真实应用图标位图**（80dp）。
   本项目没有这条路径，因为 GlassScope 一定给得出 backdrop（关玻璃时走 `blurEnabled = false` 分支）。
4. 图标主体尺寸不同，原因见下。

### 2.4 图标：去掉白底、放大主体，实测尺寸

- 第九轮是 **88dp 圆角底板 + `cardColor().copy(alpha = 0.72f)` + 白色剪影**；
  本轮底板整块删除，只剩纯剪影（`Box` 不再有 `background` / `squircleClip`）。
- 容器 **100dp**，`requiredSize(132dp)`，`clip = true`。
- **实测可见主体：98 × 49dp**（`final_glassoff_07_about.png` 逐像素包围盒，
  `density 320` 下 196 × 98 像素）。横向占容器的 98%。
- 132dp 的来历：`ic_app_mark` 是 108 单位画布、主体占 `x = 14..94`（80/108 = 74.1%），
  `132 × 0.741 ≈ 97.8dp`，与实测一致。

为什么不像 KSU 那样给 245dp、InstallerX 那样给 160dp：

- 那两个数是为**近方形**的应用图标服务的。它们的图标在 108 画布里主体只占约 2/3，
  撑到 245dp 后可视主体仍会超出 100dp 容器一圈、被裁掉边缘 —— 那是设计意图（"主体溢出后裁剪"）。
- 本项目的 `ic_app_mark` 是 **2:1 的横向胶片条**，横向再放大就会把两端的齿孔裁掉；
  纵向放大则没有溢出空间。所以取"刚好填满容器"（98/100dp），
  比第九轮的"88dp 底板里放 88dp 完整图标"明显放大，且不损失图形信息。

浅色/深色可见性：图标走 `ColorFilter.tint(onBackground)`，浅色下深色剪影、深色下浅色剪影，
与标题同色（同一 `onBackground`），不依赖模糊是否生效。

### 2.5 模拟器上的实际表现（GPU 限制，不是接线错误）

| 场景 | 图标 | 标题 | 截图 |
|---|---|---|---|
| 玻璃开（`blurEnabled = true`，走 `textureBlur`） | **不可见** | **不可见** | `final/final_light_07_about.png`、`final/after_dark_07_about.png` |
| 玻璃关（`blurEnabled = false`，走参考项目的"不模糊就正常画"分支） | 正常黑色剪影 | 正常 35sp 粗体 | `final/final_glassoff_07_about.png` |
| 官方示例 App 同一台 AVD | 官方关于页同样没有标题与卡片（第八轮已验证） | 同 | `_archive/round8/off_about3.png` |

原因：`textureBlur(..., contentBlendMode = DstIn)` 是"用采样结果替换内容"。
`swiftshader_indirect` 上这条路径编译得出来、但不产出像素，于是内容被一个空结果替换掉，
`ColorFilter.tint` 也救不回来（它只给内容层上色，不改变"内容被替换"这件事）。
**这不是代码接错**：同一份代码在 `blurEnabled = false` 时图标与标题都正常显示（见上表第二行）。

明确标注：**原生 `textureBlur` 图标/标题 = 真机待验证**。本轮不做任何模拟器专用替代视觉。

### 2.6 backdrop 的来源与"为什么不会采样到自己"

```
AboutScreen
├─ BgEffectBackground（整屏渐变）
│    └─ Spacer ← bgModifier = layerBackdrop(bgBackdrop)   ★录制层
└─ Scaffold(Transparent)
     └─ LazyColumn
          ├─ Box(100dp) { Image(textureBlur(bgBackdrop)) }  ← 兄弟节点
          └─ Text(textureBlur(bgBackdrop))                  ← 兄弟节点
```

录制层只包渐变那一层，图标与标题是它的**兄弟**。前几轮试过"在页面根部套一层满屏录制层给顶栏用"，
结果是顶栏成了录制层的子节点、采样到自己 → `RenderWindow::prepareTreeImpl` 原生栈溢出崩溃
（`_archive/round8/crash_about_libhwui.txt`）。本轮的结构与官方 `AboutPage.kt` 的
`bgModifier = Modifier.layerBackdrop(backdrop)` 是同一处接线。

### 2.7 保留未改的部分

- 页面组织不变：图标 + 软件名称 → 版本号 → 「关于」卡片（查看源代码 / 开源许可）。
- 两个入口仍**不带前置图标**，描述写在 `summary`（InstallerX 同款）。
- 「查看源代码」仍是 `runCatching { startActivity }`，没有死链崩溃。
- 卡片仍用 `cardColor().copy(alpha = 0.82f)` 而**不**用 `textureBlur`：
  参考项目整张卡（含两行入口）都由模糊层绘制，本机 GPU 上模糊不出像素时卡片会连同入口一起消失 ——
  那是功能回退，不是观感问题。这条差异在 `AboutCard` 的 KDoc 里写明。
- 非玻璃档顶栏底色 = `if (collapsed) surface else Transparent`，避免白色条带把渐变从顶部切断。

---

## 3. 开始按钮：四种禁用态配色的实机对比

### 3.1 怎么进

**仅 debug 包**：
`设置 → 主题 → 页面底部「调试（仅调试包可见）」→ 开始按钮配色对比`

- 路由：`Route.ButtonColors`（`Route.kt`）
- 入口：`ThemeScreen(onOpenButtonColors = ...)`，参数为 `null` 时不渲染这一组
- 注册：`App.kt` 的 `if (debuggable) entry<Route.ButtonColors> { ... }`
- `debuggable` = `ApplicationInfo.FLAG_DEBUGGABLE`（`ui/ButtonColorPreview.kt` 的 `rememberIsDebuggable()`）
- release 包：入口不渲染 + 目的地不注册，**两条路径都不存在**（§8.3 有 `aapt`/`dexdump` 级验证）

### 3.2 四种方案与实测色值

按钮都是**真的 `ProgressButton`**（同一个组件、同一 `fillMaxWidth()`、同一 16dp 圆角、
同一 50dp 最小高度、同一 `button` 文字样式、同一 `enabled = false`），只有颜色参数不同。
四个按钮下方同时打印 `#底色 / #文字色 · 对比度`（对比度由页面实时计算，文字带 alpha 时先合成到轨道色）。

**浅色（`final/final_light_11_button_colors.png`）**

| 方案 | 底色 | 文字色 | 对比度 |
|---|---|---|---|
| 当前实现（保持不变） | `disabledSecondaryVariant` `#F2F2F2` | `disabledOnPrimaryButton` `#FFFFFF` | **1.12:1** |
| A | `disabledPrimaryButton` `#C2D9FF` | `disabledOnPrimaryButton` `#FFFFFF` | 1.43:1 |
| B | `disabledSecondaryVariant` `#F2F2F2` | `disabledOnSecondaryVariant` `#B2B2B2` | 1.89:1 |
| C | `pageColor()` `#F6F6F6` | `onSurfaceVariantSummary` `#99000000` | 5.64:1 |
| D | `disabledPrimaryButton` `#C2D9FF` | `onSurfaceVariantSummary` `#99000000` | 5.09:1 |

**深色（`final/after_dark_11_button_colors.png`）**

| 方案 | 底色 | 文字色 | 对比度 |
|---|---|---|---|
| 当前实现 | `#404040` | `#707170` | 2.12:1 |
| A | `disabledPrimaryButton` `#253E64` | `disabledOnPrimaryButton` `#677893` | 2.40:1 |
| B | `disabledSecondaryVariant` `#404040` | `disabledOnSecondaryVariant` `#707170` | 2.12:1 |
| C | `pageColor()` `#1E1E1E` | `onSurfaceVariantSummary` `#80FFFFFF` | 5.15:1 |
| D | `disabledPrimaryButton` `#253E64` | `onSurfaceVariantSummary` `#80FFFFFF` | 4.01:1 |

**Monet 浅色（`final/final_monet_light_11_button_colors.png`）**

| 方案 | 底色 | 文字色 | 对比度 |
|---|---|---|---|
| 当前实现 | `#EFEEF5` | `#E1E5EF` | 1.13:1 |
| A | `#B5BED6` | `#E1E5EF` | 1.47:1 |
| B | `#EFEEF5` | `#9E9EA4` | 2.31:1 |
| C | `#E5E5EB` | `#44464F` | 7.49:1 |
| D | `#B5BED6` | `#44464F` | 5.06:1 |

> 注：C 的 `pageColor()` 在 Monet 浅色下是 `#E5E5EB`，而页面真实底色是 `#F9F9FF`（§1.3）。
> 所以"C = 参考预览框的配色"实际更接近预览舞台，而不是页面本身。这一点在选型时值得注意。

### 3.3 观察（不做选择）

- A / D 保留了"主操作按钮"的视觉特征（蓝底），但 A 的文字在浅色下几乎与底色同亮度（1.43:1），
  深色下也只有 2.40:1 —— 都属于"能看出是个蓝色按钮、看不出上面写了什么"。
- B 是 MIUIX 次按钮的标准配对，文字可辨但明显弱于正文（1.89:1 / 2.31:1）。
- C 是唯一在三套配色下都 ≥ 5:1 的方案，但底色与页面同色，看起来更像"一块凹槽"而不是按钮。
- D 在浅色 5.09:1、Monet 5.06:1、深色 4.01:1，三套都可用，且保留蓝底主色语义。

四种方案的可访问性语义都真实：`enabled = false`、`Role.Button`、不可点击，
没有为了让文字清楚把它改成 enabled。

**正式按钮本轮不变**（仍是 `ProgressButtonDefaults` 默认禁用三色），等看过对比再定。

### 3.4 与第九轮数字的差异

第九轮报告写的是「C = 5.61:1」。本轮同一页面实算为 **5.64:1**，差别来自
对比度的计算方式：本轮先把文字色按自身 alpha 合成到轨道色（`compositeOver`）再算相对亮度，
第九轮是直接拿 `#99000000` 的 RGB 算的。以本轮实现为准（半透明色直接算会虚高/虚低）。

---

## 4. 全项目 MIUIX OS4 组件审计

审计范围：`screens/` 全部 7 个页面 + `ui/` 全部公共组件 + `App.kt` 的主题/导航/弹层宿主。
对照对象：本地 `miuix` 源码（`3177a684`）的 `basic/`、`preference/`、`glass/`、`material/`、`layout/`。

判定档位：**完全对齐** / **本地适配但符合官方规范** / **存在视觉偏差** / **存在功能偏差** /
**存在实现问题** / **不适用**。

### 4.1 覆盖矩阵

| 页面 | 组件 | 官方对应组件 | 当前实现 | 判定 | Glass 状态 | 说明 |
|---|---|---|---|---|---|---|
| 跨页 | 页面骨架 | `Scaffold` + `GlassTopAppBar` + `LazyColumn`（官方 `GlassPage.kt:398`） | `PageChrome.kt` `Os4ListPage` | 本地适配但符合官方规范 | 玻璃（可关） | 顶栏在录制层外、弹层槽位在录制层外，顺序正确 |
| 跨页 | 页面滚动修饰 | 官方 `PageUtils.kt` 同名函数 | `pageScrollModifiers()` | 本地适配但符合官方规范 | — | 少了 `showTopAppBar` 开关（本项目恒有顶栏） |
| 跨页 | 页面内边距 | 官方 `PageUtils.pageContentPadding` | `pageContentPadding()` | 本地适配但符合官方规范 | — | `railInset()` 在 `calculate*` 里读，符合 AGENTS 第 13 条 |
| 跨页 | 底栏占位高度 | 库未暴露 `floatingMargin` | `rememberBottomBarClearance()` | 本地适配但符合官方规范 | — | 复刻库内部高度公式；**已知风险**：按 54dp 最小值算，字体放大时底栏会变高（§7.3） |
| 跨页 | 顶栏（玻璃档） | `GlassTopAppBar` | `PageTopBar` | 本地适配但符合官方规范 | 玻璃 | `bandBrush`/`bandOverhang`/`backgroundAlpha` 显式传，与官方 `AdaptiveTopAppBar` 同款；`progressiveBlur` 按渲染器降级（§1.6） |
| 跨页 | 顶栏（非玻璃档） | `TopAppBar` | `PageTopBar` | 完全对齐 | 非玻璃 | 关于页额外传 `background`（§2.7） |
| 跨页 | 顶栏返回键 | `component/BackNavigationIcon.kt` | 玻璃档 `GlassIconButton`+`ChevronBackward`；非玻璃档 `IconButton`+`Back` | 本地适配但符合官方规范 | 两档 | 非玻璃档 24dp 是**唯一**硬写的图标尺寸（§6） |
| 跨页 | 分组标题 | `SmallTitle` | `GroupHeader` | 完全对齐 | 非玻璃 | 包装无参数 |
| 跨页 | 分组卡片 | `Card`（`CornerRadius 16dp`、`InsideMargin 0dp`） | `GroupCard` | 本地适配但符合官方规范 | 非玻璃 | 只覆盖 `colors = cardColor()` |
| 跨页 | 行首图标 | `Icon` | `LeadingIcon` | 本地适配但符合官方规范 | 非玻璃 | 26dp 是 Glass 28dp 画布塞进 24dp 槽位的实测值 |
| 跨页 | 下拉行 | `GlassDropdownPreference` | `DropdownRow` | 存在视觉偏差 → **已修** | 玻璃浮层 | 之前显式传 `visuals = GlassDefaults.style(isDark)`，深色下材质比顶栏厚一档；本轮删掉，回到库默认 `GlassDefaults.Style` |
| 跨页 | 滑块行 | `SliderPreference` | `SliderRow` | 完全对齐 | 非玻璃 | 只透传必要参数 |
| 跨页 | 开关行 | `SwitchPreference` | `SwitchRow` | 本地适配但符合官方规范 | 非玻璃 | 见 §4.2 第 3 条（Monet 拇指色） |
| 跨页 | 说明段落 | `Text` | `NoteText` | 本地适配但符合官方规范 | 非玻璃 | 28dp 缩进 = 卡片 12 + 组件 16 |
| 跨页 | 预览舞台 | 无对应组件 | `PreviewStage` | 存在视觉偏差 | 非玻璃 | 圆角 12dp 与库内 16dp 不一致，且注释过期（§6） |
| 跨页 | 底部主操作条 | 无对应组件 | `PrimaryActionBar` | 本地适配但符合官方规范 | 非玻璃 | 遮挡底色本轮改为 `pageBaseColor()`（§1.3） |
| 跨页 | 主按钮 | `Button` | `ProgressButton`（移植，Apache-2.0） | 存在视觉偏差 | 非玻璃 | 圆角/高度/内边距自成一套（§6），禁用态配色待定（§3） |
| 跨页 | 加载指示 | `InfiniteProgressIndicator` | 同 | 完全对齐 | 非玻璃 | — |
| 跨页 | 主题装配 | `MiuixTheme(controller)` | `AnimatedMiuixTheme`（逐色插值） | 存在实现问题 → **已修一半** | — | `colors` 重载缺 `LocalContentColor`/`LocalColorSchemeMode`；本轮补了内容色，`isDynamicColor` 用显式 `selectedColor` 兜（§4.2） |
| 主页 | 工具列表 / 行 / 图标 | `Card` / `ArrowPreference` / `Glass` 图标族 | `HomeScreen` | 完全对齐 | 玻璃（可关） | 无覆盖参数 |
| 工具页 | 素材入口行 | `BasicComponent` | `BasicComponent` | 完全对齐 | 非玻璃 | 摘要「未选 / 已选 N 个」 |
| 工具页 | 清除素材 | `IconButton` | `IconButton` + `IconSizes.Inline` | 本地适配但符合官方规范 | 非玻璃 | 19dp 原来是散写，本轮收进 `IconSizes` |
| 工具页 | 预览卡片 / 动图预览 | `Card` + `Image` | `ToolScreen` | 存在视觉偏差 | 非玻璃 | 与 `PreviewStage` 是第二套同视觉实现，圆角 12dp 散写（§6） |
| 工具页 | 各参数滑块/开关 | `SliderPreference` / `SwitchPreference` | 同 | 完全对齐 | 非玻璃 | 帧率/时长/抽帧的 `steps` 语义已逐个核对 |
| 工具页 | **旋转滑块** | `SliderPreference` | 同 | **存在功能偏差 → 已修** | 非玻璃 | `0..270 + steps = 3` 会得到 0/67.5/135/202.5/270，下游按浮点 `postRotate` 会斜切画面；改为 `steps = 2`（0/90/180/270） |
| 工具页 | 抠图颜色 | `ColorPalette` / `RadioButtonPreference` | 自绘 `ColorSwatches` | 存在实现问题 → **已修** | 非玻璃 | 补 `Role.RadioButton` + `selected` 语义 + 颜色名 `contentDescription`；选中时不再 24→28dp 让整行跳动；标题升到 `headline1`；行高补到 56dp |
| 工具页 | 字幕输入 | `TextField` | `TextField`+`TextFieldState` | 本地适配但符合官方规范 | 非玻璃 | 10dp 纵向内边距为散写 |
| 工具页 | 结果卡片 | `Card` + `Button` | 同 | 存在视觉偏差 | 非玻璃 | 「保存到相册」与「分享」同权重，主次不分（§6） |
| 工具页 | 错误/提示 | `Snackbar` | `NoteText` | 存在实现问题 | 非玻璃 | 错误与普通信息同一套样式、无 `liveRegion`（§7.3） |
| 作品页 | 列表 / 空态 / 文件行 | `Card` / `ArrowPreference` | `LibraryScreen` | 完全对齐 | 玻璃（可关） | 文件行用库箭头，不自拼 |
| 作品页 | 文件操作面板 | `GlassBottomSheet` | 同 | 存在视觉偏差 → **已修** | 玻璃面板 | 行内 16dp 叠在面板 24dp 上导致标题错位 → 行内边距改成只给纵向；补 `navigationBarsPadding()` 避免"删除"贴导航条 |
| 作品页 | 删除确认 | `GlassDialog` + `GlassDialogButton(Destructive)` | 同 | 完全对齐 | 玻璃弹层 | 与官方结构同构，破坏性动作用 `Destructive` 更准 |
| 设置页 | 两组入口 | `SmallTitle` / `Card` / `ArrowPreference` | `SettingsScreen` | 完全对齐 | 玻璃（可关） | — |
| 主题页 | 四个下拉 / 三个开关 | `GlassDropdownPreference` / `SwitchPreference` | `ThemeScreen` | 完全对齐（下拉偏差随 `DropdownRow` 一并修好） | 玻璃浮层 | — |
| 关于页 | 根 + 渐变 | 官方 `AboutPage.kt` + 移植 `BgEffectBackground` | `AboutScreen` | 本地适配但符合官方规范 | 玻璃（`opaqueBase = false`） | 渐变在 Scaffold 外，透明 + `contentWindowInsets = 0` |
| 关于页 | 页面骨架 | 无单一对应 | 自建 `Scaffold`+`ContentBackdropBox`+`LazyColumn` | 存在实现问题 | 玻璃 | 与 `Os4ListPage` 是第二套实现（§7.3） |
| 关于页 | 图标 / 标题 | `textureBlur` + `DstIn` | 原生 `textureBlur` | 本地适配但符合官方规范（真机待验证） | 玻璃 | §2 |
| 关于页 | 卡片 / 两个入口 | `Card` / `ArrowPreference` | 同 | 完全对齐 | 非玻璃 | 无前置图标、单一许可入口 |
| 许可页 | 分组 / 条目 / 正文 | `Card` / `ArrowPreference` / `Text` | `LicensesScreen` | 完全对齐 | 玻璃（可关） | 正文 `footnote1` 偏小（§7.3） |
| App | 弹层宿主 | 官方 `GlassPageHost` | 最外层 `Scaffold(Transparent, insets 0)` | 完全对齐 | — | 去掉它会同时坏三件事（AGENTS 第 11 条） |
| App | 一级页录制层 + Pager | 官方 `CompactScreenLayout` | `entry<Route.Root>` | 本地适配但符合官方规范 | 玻璃（可关） | 底栏是录制层兄弟；`beyondViewportPageCount = 1` 是性能取舍 |
| App | 底栏 | `GlassNavigationBar` | 同 | 本地适配但符合官方规范 | 玻璃（可关） | 本轮补 `selectedColor`（§4.2）；横向 24dp 硬编码与注释矛盾（§6） |
| App | 侧栏 | `GlassNavigationRail` | 同 | 完全对齐 | 玻璃（可关） | 折叠阈值、函数式 `railInset` 都对 |
| App | 返回处理 | 官方 `MainScreenBackHandler` | `BackHandler(tab != 0)` | 完全对齐 | — | tab 0 交还系统 |

### 4.2 本轮修掉的 OS4 问题（按严重度）

1. **旋转滑块档位错误**（功能）—— `screens/ToolScreen.kt`：
   `0f..270f, steps = 3` 按 `basic/Slider.kt` 的语义是「中间 3 个档」= 5 档
   （0/67.5/135/202.5/270°），而 `core/GifTasks.kt` 直接把这个浮点喂给 `postRotate`，
   斜角度会把画面斜切、四角露空。改为 `steps = 2`（0/90/180/270）。

2. **主题包装丢掉两个 CompositionLocal**（Monet 视觉）—— `ui/AnimatedTheme.kt`：
   用 `MiuixTheme(colors = …)` 重载，它只提供 `LocalColors`/`LocalTextStyles`/`LocalIndication`/
   `LocalOverscrollFactory`，比 `MiuixTheme(controller = …)` 少了 `LocalContentColor` 与
   `LocalColorSchemeMode`（后者是 internal，调用方补不了）。
   后果：`MiuixTheme.isDynamicColor` 恒 false →
   底栏/侧栏选中项取 `onBackground` 而不是 `primary`；`LocalContentColor` 回落到纯黑。
   处理：
   - `AnimatedMiuixTheme` 里补 `CompositionLocalProvider(LocalContentColor provides onBackground)`；
   - 底栏/侧栏在 `App.kt` 显式传 `selectedColor`（Monet → `primary`，其余 → 库原本的 `onBackground`/`onSurface`）。

   **实测证据**（Monet 浅色、底栏「工具」选中图标区域的主色像素统计）：

   | | 主色像素值 | 像素数 |
   |---|---|---|
   | 修复前 | `(26,28,32)`（`onBackground`，近黑） | 906 |
   | 修复后 | `(71,93,146)`（`primary`） | 906 |

   像素数不变、只有色值变 —— 证明变的只是 tint，不是布局。

3. **抠图色块缺无障碍语义 + 选中时整行抖动** —— `ToolScreen.ColorSwatches`：
   自绘 `Box.clickable` 没有 `role`/`selected`，TalkBack 读空标签；选中时尺寸 24→28dp 会让
   右侧色块整体位移。本轮：`selectable(role = Role.RadioButton)` + `stateDescription` 语义 +
   颜色名 `contentDescription`（绿/蓝/白/黑/红）+ 固定 28dp 槽位（圆点仍 24dp）+
   行高补到 56dp + 标题与同卡其它行同级（`headline1`）。

   > 没有换成官方 `ColorPalette` / `RadioButtonPreference`：这两者是"一排色块"或"一整行单选"，
   > 版式与"标题 + 五个色点"不同；当前形态是产品需要的，本轮只把语义与尺寸补齐。
   > 若以后想彻底用官方件，应改成 `RadioButtonPreference` 五个选项。

4. **下拉弹层材质档与顶栏不一致** —— `Widgets.DropdownRow`：
   删掉 `visuals = GlassPopupDefaults.visuals(style = GlassDefaults.style(isDark))`，
   回到库默认 `GlassDefaults.Style`（与顶栏同一个决策）。

5. **下拉弹层的采样源在一级页会错一整页宽**（潜伏缺陷）—— `Widgets.DropdownRow`：
   之前固定取 `LocalPageBackdrop`。`GlassDropdownPreference` 默认 `renderInRootScaffold = true`，
   弹层由**最外层** Scaffold 绘制、上报真实坐标；一级页的页面录制层在 `HorizontalPager` 里、
   上报槽位坐标（第 2 个 tab 报 x = 1080），两者相减差一整页宽 —— 就是第六轮"作品菜单不模糊"的成因。
   项目已经为 Dialog/BottomSheet 处理过这条（`pageOverlay`），唯独下拉没走同一规则。
   本轮改为 `LocalRootBackdrop ?: LocalPageBackdrop`。

6. **作品操作面板行内边距错位 + 缺导航条留白** —— `screens/LibraryScreen.FileActionSheet`：
   面板自身左右 24dp（`BottomSheetDefaults.insideMargin = DpSize(24, 0)`），
   行内再叠 `BasicComponentDefaults.InsideMargin` 的 16dp → 行标题距面板边 40dp，与面板标题的 24dp 不同线。
   改为行内 `PaddingValues(horizontal = 0, vertical = 16)`；`content` 末尾补
   `Spacer(Modifier.navigationBarsPadding().height(8.dp))`（库 KDoc 明确要求内容自己让导航条）。

7. **玻璃/遮挡层底色与页面不同源**（§1.3）—— `pageBaseColor()`。

### 4.3 保留的本地差异（有意，不是缺陷）

| 差异 | 位置 | 保留原因 |
|---|---|---|
| `pageColor()` / `cardColor()` 派生色 | `ui/Widgets.kt` | 提供"页面 / 次级表面 / 卡片"三档层次；`surface` 与 `surfaceContainer` 之外还需要一档 |
| `ProgressButton`（带进度的按钮） | `ui/ProgressButton.kt` | 官方 `Button` 没有进度填充语义；仅几何值应与官方对齐（§6） |
| 预览舞台 / 动图舞台自绘 | `Widgets.PreviewStage`、`ToolScreen.AnimatedPreview` | 库无媒体舞台组件 |
| 顶栏显式 `bandBrush`/`bandOverhang` | `GlassScope.PageTopBar` | 两条绘制路径都是官方的，备好 band 才能在 AGSL 失效时不出硬边 |
| 关于页骨架自建 | `AboutScreen` | 该页要"自己铺整屏渐变"且不用 `Os4ListPage`；合并需要给 `Os4ListPage` 加变体（§7.3） |
| 关于页卡片不用 `textureBlur` | `AboutScreen.AboutCard` | 模糊不产出像素时入口会一起消失（功能回退） |
| 底部主操作条用纯色遮挡 | `Widgets.PrimaryActionBar` | 二级页没有玻璃底栏可挡穿透内容 |
| 二级页不保留 Rail | `App.kt` | 第七轮既定设计（官方保留），未变 |
| 删除确认用 `Destructive` | `LibraryScreen` | 语义更准，官方枚举提供该角色 |
| 调试页仅 debug | `Route.ButtonColors` / `ThemeScreen` | §3.1 |

---

## 5. 全项目 Glass 完整性

### 5.1 组件清单与采样源

| 页面 | 组件 | 采样源 | 录制层位置 | 布局修饰符 | 降级 | 判定 |
|---|---|---|---|---|---|---|
| 全部页面顶栏 | `GlassTopAppBar` | `glass.contentBackdrop`（页面正文层） | `ContentBackdropBox`（Scaffold 内容槽、满宽），顶栏是**兄弟** | 玻璃档 `contentModifier`（只挪前景）；非玻璃档 `modifier` | `backdrop == null` → `bandBrush` 渐变带；按钮退不透明 | 一致 |
| 一级页（窄） | `GlassNavigationBar` | `rootBackdrop`（Pager 外那一层） | 一级页录制层，满宽 | `align(BottomCenter).padding(horizontal = 24.dp)` | `null` → `surfaceContainer` 胶囊 | 一致 |
| 一级页（宽） | `GlassNavigationRail` | `rootBackdrop` | 同上 | 库内部 `BoxWithConstraints(fillMaxSize)` + `windowInsetsPadding` | `null` → `surfaceContainer` | 与官方逐像素一致（§1.4） |
| 顶栏返回 | `GlassIconButton` | 继承顶栏按钮 backdrop | 同页面层 | `navigationIcon` 槽 | 不透明 `fill` | 一致 |
| 工具/主题下拉 | `GlassDropdownPreference` | `LocalRootBackdrop ?: LocalPageBackdrop`（本轮修） | 一级页层 / 二级页层 | 弹层宿主，无页面 padding | `null` → `GlassPopupDefaults.containerColor()` | 本轮修正 |
| 作品操作面板 | `GlassBottomSheet` | `pageOverlay` 传入（= root ?: 页面层） | 一级页录制层 | 弹层宿主 | `null` → `containerColor` | 一致 |
| 作品删除确认 | `GlassDialog` + `GlassDialogButton` | 同上 | 同上 | 同上 | `null` → `containerColor` | 一致 |
| 关于页图标/标题 | `textureBlur`（非 Glass 组件，同一采样体系） | `bgBackdrop`（只录渐变层） | 渐变层 `bgModifier` | 无布局让位 | `blurEnabled=false` → 正常着色绘制 | 与 KSU 同构，真机待验证 |

### 5.2 backdrop 层级

```
GifApp
└─ Scaffold(Transparent, contentWindowInsets = 0)          ← 弹层宿主（官方 GlassPageHost 同款）
   └─ Box(fillMaxSize).background(pageBaseColor())
      ├─ NavDisplay
      │  └─ entry<Route.Root>
      │     └─ Box(fillMaxSize)
      │        ├─ ★L0 Box(fillMaxSize).layerBackdrop(rootBackdrop)     [满宽]
      │        │   └─ LocalRootBackdrop
      │        │      └─ HorizontalPager
      │        │         └─ 一级页 Scaffold(containerColor = pageBaseColor())
      │        │            ├─ topBar: GlassTopAppBar(backdrop = L1)         ┐ 兄弟
      │        │            ├─ ★L1 ContentBackdropBox.layerBackdrop(L1)     ┘
      │        │            └─ pageOverlay(...)                            ← 弹层状态，不绘制
      │        └─ GlassNavigationBar(backdrop = L0)                        ← L0 兄弟
      ├─ GlassNavigationRail(backdrop = L0)                               ← L0 兄弟
      └─ MiuixPopupHost
         ├─ GlassDialog / GlassBottomSheet → L0（一级页）/ L1（二级页）
         └─ GlassDropdownPreference      → L0 ?: L1（本轮）
关于页：
   ★L2 rememberLayerBackdrop() 挂在 BgEffectBackground 的渐变 Spacer 上
      消费方 = 兄弟节点 Image / Text（textureBlur）
```

4 个录制点全部 `fillMaxSize()`，没有任何一处跟着 `railInset` 收窄；采样坐标全部走库的
`layerCoordinates.localPositionOf(...)`。

### 5.3 逐项核对结果

| 检查项 | 结果 |
|---|---|
| 是否用了官方组件 | 是（`GlassTopAppBar` / `GlassNavigationBar` / `GlassNavigationRail` / `GlassIconButton` / `GlassDropdownPreference` / `GlassDialog` / `GlassBottomSheet`） |
| 是否存在自定义重复实现 | 否（`rememberPageBackdrop`/`pageContentPadding` 是为了规避重组而复刻的**辅助函数**，不是组件） |
| backdrop 完整性 | 4 个录制点满宽；消费方均为兄弟节点；无自采样 |
| 采样坐标 | 统一 `localPositionOf`；弹层统一走 Pager 外那层（本轮把下拉也纳入） |
| 重复录制 | 有：一级页内容同时被 L0 与 L1 录；`beyondViewportPageCount = 1` 再各录一份；硬件上顶栏还有库内第三层（性能问题，见 §7.3） |
| 横竖屏采样范围 | 一致（录制层满宽，不随让位收窄） |
| Insets | 顶栏 / 正文 / Rail 三处横向起点同源（`systemBars ∪ displayCutout`），相加后数值一致 |
| 重复 padding | 未发现同一节点叠两套横向算式 |
| 主题 | 浅/深/Monet 三套均取色验证；玻璃自身按 `background.luminance()` 判明暗 |
| Glass 开关 | 唯一决策点 `LocalGlassEnabled`（`ui/GlassSupport.kt`），全部消费点都读它或它派生的 `glass.enabled` |
| 设备能力降级 | `minSdk = 33`，`isRenderEffectSupported()` 在本项目支持范围内恒真 → 唯一有效降级就是用户开关 |
| `backdrop == null` 回退 | 面板/胶囊/弹层/下拉全部退化为库的 `containerColor`，**没有**第二套自造视觉 |
| 视觉/交互 | 透明度、模糊半径、高光、边缘、形变全部吃库默认；项目只显式给顶栏 `band*` |
| 触觉 | 库内导航/开关自带；自绘色点补 `ToggleOn`；`ArrowPreference`/按钮不震（与库一致） |

### 5.4 模拟器能验证 / 必须真机验证

| 模拟器（`swiftshader_indirect`）可验证 | 必须真机 |
|---|---|
| 布局与让位（338dp / 324.5dp / 13.5dp） | 顶栏真实渐进模糊（模拟器被 `isSoftwareRenderer` 强制走 band） |
| 录制层满宽、无自采样（自采样会立刻 SIGSEGV） | 玻璃材质的折射、边缘 bloom、色散 |
| 玻璃开/关两态是否存在、降级是否退成不透明 | 关于页 `textureBlur` 的图标与标题（本机不产出像素） |
| 弹层层级（Dialog/BottomSheet 画在底栏之上、在录制层外） | 触觉反馈、预测性返回动画与玻璃的叠加 |
| 深浅/Monet 配色层次与色差（可直接取色） | 非软件渲染 GPU 上的 AGSL 正确性 |
| 旋转/折叠往返时坐标更新 | 低端机多层录制的掉帧/发热 |

---

## 6. 尺寸与整体视觉一致性回归

第九轮已修正的尺寸**未推翻**；本轮新增/调整的只有下面几项：

| 项 | 改动 | 理由 |
|---|---|---|
| 抠图色块槽位 | 24/28 抖动 → 固定 28dp（圆点 24dp） | 选中时整行宽度不再跳，右侧色块不位移 |
| 抠图色块行高 | 52dp → 最小 56dp | 与 `BasicComponent` 的最小行高一致 |
| 抠图颜色标题 | `body1` → `headline1` | 与同卡其它行标题同档（`Component.kt:92`） |
| 作品面板行内边距 | 水平 16dp → 0（面板给 24dp） | 行标题与面板标题同一条线 |
| 作品面板底部 | 无 → `navigationBarsPadding()` | "删除"不再贴导航条 |
| 顶栏返回键（非玻璃档） | `24.dp` 硬写 → 仍 24dp | 见下 |

仍未统一、本轮**故意不动**的：

1. **预览舞台/动图预览的 12dp 圆角** vs 卡片/按钮的 16dp。媒体舞台比卡片小一圈，
   圆角略小是常见做法；两处实现（`PreviewStage`、`AnimatedPreview`）应合并，但那是重构，不在本轮范围。
   两处过期注释（写着 `surface`、实际是 `pageColor()`）本轮已一并改掉。
2. **`ProgressButton` 的几何**（16dp 圆角用 `RoundedCornerShape` 而非 `squircleSurface`、
   50dp 最小高度 vs 官方 40dp、内边距 12dp vs 官方 13dp）。
   改它会影响主操作条的观感与所有工具页，属于需要单独验收的改动，本轮只记录。
3. **顶栏非玻璃档返回键 24dp** 是唯一没走 `IconSizes` 的图标尺寸；
   官方 `BackNavigationIcon` 非玻璃档就是 24dp，保持。
4. **底栏横向 24dp 硬编码**：`App.kt` 的注释说"几何不该我们维护"，但代码里确实写了 24dp。
   实测需要这段外边距（大圆角机型两端会被屏幕圆角切掉），保留；注释已在本轮改成
   "这是退进屏幕圆角的实测值"，不再自相矛盾。

---

## 7. 回归报告

### 7.1 构建

| 目标 | 结果 | 产物 |
|---|---|---|
| `:compileDebugKotlin` | 成功 | — |
| `:assembleDebug` | 成功，无 error | `build/outputs/apk/debug/GifHelper-debug.apk`（45,316,921 B） |
| `:assembleRelease` | 成功（R8 + 资源压缩），无 error | `build/outputs/apk/release/GifHelper-release.apk`（3,311,823 B） |

### 7.2 覆盖的页面 × 尺寸 × 主题

| 组合 | 截图 |
|---|---|
| 540×1200dp 竖屏 · 静态浅色 · 玻璃开 | `final/final_light_01..12` |
| 540×1200dp 竖屏 · 静态深色 · 玻璃开 | `final/after_dark_01..12` |
| 540×1200dp 竖屏 · Monet 浅色 · 玻璃开 | `final/final_monet_light_01..12` |
| 540×1200dp 竖屏 · 静态浅色 · 玻璃关 | `final/final_glassoff_01..10` |
| 1200×540dp 横屏 · 静态浅色 · 玻璃开 | `final/after_1200_land_home(_scrolled)`、`before/before_1200_land_01..10` |
| 1200×540dp 横屏 · 静态深色 · 玻璃开 | `final/after_1200_land_dark_home(_scrolled)`、`before/before_1200_land_dark_home` |
| 914×411dp 横屏 · 静态浅色 · 玻璃开 | `final/final_914_land_01..09` |
| 1200dp 横屏 · 官方示例 App · 深色 | `ref_official_dark_land.png` |

每次覆盖的页面：首页 / 工具页（视频转 GIF）/ 作品 / 设置 / 主题 / 关于 / 关于滚动 / 许可 / 主题调试分组 / 按钮对比页。
弹层：作品操作面板与删除确认在 `04_library` 与第二轮交互测试中验证（打开/关闭、位置、遮罩）；
下拉弹层在主题页与工具页验证。

**未覆盖**：
- 深色 + Monet 组合的整页截图（本轮只截了 Monet 深色的按钮对比页 `final/monet_dark_btn.png`）。
- AMOLED 纯黑组合（仅按代码路径核对：`pageColor()`/`pageBaseColor()` 在 AMOLED+深色下返回纯黑）。
- 真机（硬件 GPU）。

### 7.3 未修的问题清单（下一轮候选）

| # | 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|---|
| 1 | 错误提示与普通信息同一样式、无 `liveRegion` | `Widgets.NoteText` + `ToolScreen` | 读屏不播报错误 | `NoteText(text, isError)`：error 色 + `liveRegion = Polite` |
| 2 | 作品面板缺底部导航条留白 | 已修（§4.2 #6） | — | 真机验证 |
| 3 | 底栏占位高度按 54dp 最小值算 | `PageChrome.rememberBottomBarClearance` | 大字体（≥1.6）或标题换行时底栏变高、压住列表末尾 | 改成 `onSizeChanged` 上报实测高度 |
| 4 | 结果卡片两个按钮同权重 | `ToolScreen` 结果卡 | 主次不分 | 「保存到相册」用 `buttonColorsPrimary()` 或 `TextButton` |
| 5 | `ProgressButton` 几何与官方 `Button` 不同源 | `ui/ProgressButton.kt` | 同屏两种圆角 | 改 `squircleSurface` + `ButtonDefaults` 常量 |
| 6 | 关于页骨架是第二套实现 | `AboutScreen` vs `Os4ListPage` | 顶栏/录制层顺序以后只改一处的风险 | 给 `Os4ListPage` 加 `rootBackground`/`opaqueBase` 变体 |
| 7 | 一级页内容被重复录制（L0+L1+相邻页） | `App.kt` | 真机发热/掉帧 | 记录，暂无法在不破坏"顶栏与内容共用录制层"的前提下合并 |
| 8 | 许可正文 `footnote1` 偏小、失败兜底缺"下一步" | `LicensesScreen` | 长文可读性 | 升一档字号 + 补文案 |
| 9 | 顶栏渐进模糊在非软件渲染 GPU 上仍可能画错 | `GlassScope` + `GlassSupport` | 个别机型"只压暗不模糊" | 只能靠用户开关（AGENTS 第 3 条），已写入主题页副标题建议 |
| 10 | RTL 下 Rail 面板与 `railInset` 是否同向 | `GlassNavigationRail` + `Adaptive` | 阿拉伯语横屏可能双向错位 | 真机切 RTL 验证 |

---

## 8. 交付对照

### 8.1 本轮必答的四个问题

1. **横屏 Glass 差异具体表现在哪**：不是采样坐标问题。可量测的差异只有"玻璃/遮挡层的垫底色
   与页面真实底色不同源"（深色差 30 灰阶），已修；侧栏面板本身与官方示例**逐像素相同**；
   观感上的"不玻璃"来自侧栏下没有内容可糊，官方同样如此（§1.4）。
2. **是哪一类问题**：采样底色（B 类），不是录制范围/坐标/层级/渲染（A 类不存在）；
   另有一项渲染器能力问题（顶栏 AGSL 渐进模糊，已按渲染器降级）。
3. **改了哪些文件**：§9。
4. **修复前后实际差异**：横屏首屏逐像素 diff 只有状态栏时钟（§1.5）；深色二级页主操作条
   `#1E1E1E → #000000`（§1.3）；Monet 底栏选中色 `(26,28,32) → (71,93,146)`（§4.2 #2）。

### 8.2 About 页：证明走的是原生 `textureBlur`

- `grep -R "gradientFilled\|accentBrush\|BgPalette" src/` → **零命中**（第九轮那套已删）。
- `AboutScreen` 里图标与标题各有一处 `Modifier.textureBlur(backdrop = bgBackdrop, colors = logoColors, contentBlendMode = ComposeBlendMode.DstIn)`。
- `logoBlend` 的三档颜色/混合模式与 KSU `AboutMiuix.kt:189-203`、InstallerX `MiuixAboutPage.kt:420-431` **逐字相同**。
- `bgBackdrop` 由 `Modifier.layerBackdrop(bgBackdrop)` 挂在渐变层上，与官方 `AboutPage.kt` 同构。
- 模拟器表现与"关了模糊就正常"的对照见 §2.5。

### 8.3 release 包不含调试入口

两道门，都是**编译期**可折叠的：

```kotlin
// build.gradle.kts：字面量布尔，Kotlin 会把它内联成常量（AGP 默认的 DEBUG 是
// Boolean.parseBoolean("true")，不是字面量，推不出取值）
debug   { buildConfigField("boolean", "DEBUG_UI", "true") }
release { buildConfigField("boolean", "DEBUG_UI", "false") }

// App.kt：目的地只在 debug 注册
if (debuggable) { entry<Route.ButtonColors> { ButtonColorPreviewScreen(onBack = pop) } }

// ThemeScreen.kt：入口组同样带编译期门
if (BuildConfig.DEBUG_UI && onOpenButtonColors != null) { ... }
```

release 包里 `DEBUG_UI` 被内联成 `false`，R8 把 `Route.ButtonColors` 的目的地、
主题页的入口组、`ButtonColorPreviewScreen` 整段代码与其中的中文字符串一起删掉。

**本轮实际验证**（解出 release APK 的全部 `classes*.dex`，逐字节搜索）：

| 字符串 | release dex 命中 |
|---|---|
| `开始按钮配色对比` | **0** |
| `无素材（禁用态）` | **0** |
| `调试（仅调试包可见）` | **0** |
| 调试页类名 `ButtonColorPreview` | **0** |

同一套搜索在 debug APK 上命中 1（入口文案）—— 说明搜索本身有效，不是假阴性。

> 走过的弯路（记录一下，免得下轮又绕）：第一版用运行时
> `ApplicationInfo.FLAG_DEBUGGABLE` 判断，debug 正常，但 release dex 里仍留着一个
> "开始按钮配色对比" 字符串（R8 推不出该 flag 的取值，页面类没被删）。
> 改成 `BuildConfig.DEBUG` 后 release 干净了，但 debug 包反而看不到入口 ——
> 因为 AGP 生成的 `DEBUG` 初始化是 `Boolean.parseBoolean("…")`，不是字面量常量，
> Kotlin 不会内联，取值由运行时决定。最终用自定义的 `buildConfigField` 字面量解决，
> 两个变体都验证过（debug 能进页、release 搜不到字）。

### 8.4 真机待验证清单

| 项 | 原因 |
|---|---|
| About 图标与软件名称的液态玻璃 | `swiftshader_indirect` 上 `textureBlur` 不产出像素（官方示例同样） |
| 顶栏渐进模糊 | 同上（AGSL 在软件光栅化上产出错位副本） |
| 玻璃材质的折射/高光/边缘 | 需要硬件 GPU |
| 作品面板底部导航条留白 | 模拟器无手势导航条遮挡样本 |
| 触觉反馈 | 模拟器无马达 |
| RTL 布局 | 未切语言验证 |

---

## 9. 改动文件清单

| 文件 | 目的 |
|---|---|
| `ui/Widgets.kt` | 新增 `pageBaseColor()`（页面真实底色）；`PrimaryActionBar` 遮挡色改用它；`DropdownRow` 采样源改 `LocalRootBackdrop ?: LocalPageBackdrop`、删除 `visuals` 覆盖；新增 `IconSizes.Inline`；修正两处过期注释（禁用态配色、操作条底色）；`pageColor()` 注释补充 Monet 实测数据 |
| `ui/GlassScope.kt` | `rememberPageBackdrop()` 垫底色改用 `pageBaseColor()`；`PageTopBar` 新增 `background` 参数（非玻璃档底色）；玻璃档 `progressiveBlur = !isSoftwareRenderer`；返回键尺寸走 `IconSizes.Action` |
| `ui/GlassSupport.kt` | 新增 `isSoftwareRenderer`（软件光栅化判定，唯一用途是顶栏渐进模糊降级） |
| `ui/PageChrome.kt` | `Scaffold` 显式 `containerColor = pageBaseColor()` |
| `ui/AnimatedTheme.kt` | 补 `CompositionLocalProvider(LocalContentColor)`（`colors` 重载缺失项） |
| `App.kt` | 根 `Box` 与 `NavDisplayEffects.backdropColor` 改 `pageBaseColor()`；底栏/侧栏显式传 `selectedColor`（Monet → primary） |
| `ui/bg/BgEffect.kt` | 删除 `BgPalette`/`rememberBgPalette`/`gradientFilled`/`accentBrush`（第九轮自制渐变）；`BgEffectBackground` 新增 `bgModifier`（挂 backdrop 录制层） |
| `screens/AboutScreen.kt` | 图标/标题改原生 `textureBlur + DstIn + BlurColors(logoBlend)`；删除白底与 `squircleClip`；容器 100dp / 主体 132dp；非玻璃档顶栏透明；KDoc 重写（含 GPU 限制与尺寸推导） |
| `screens/ToolScreen.kt` | 旋转滑块 `steps = 2`；抠图色块补语义/固定槽位/行高/标题档位；`IconSizes.Inline`；动图预览底色改 `pageColor()`（次级表面，保留） |
| `screens/LibraryScreen.kt` | 操作面板行内边距只给纵向；补 `navigationBarsPadding()` |
| `screens/ThemeScreen.kt` | 新增仅 debug 的「调试」分组与入口行（`onOpenButtonColors` 可空） |
| `Route.kt` | 新增 `Route.ButtonColors`（仅 debug 注册） |
| `build.gradle.kts` | 开 `buildFeatures.buildConfig`，加字面量 `DEBUG_UI`（debug=true / release=false），让调试页与入口在 release 里被 R8 整段删除 |
| `ui/ButtonColorPreview.kt`（新增） | 按钮四方案对比页 + `rememberIsDebuggable()` |
| `res/drawable/ic_app_mark.xml` | 注释更新（掩膜用法、主体占画布 74.1%） |
| `AGENTS.md` | 硬约束 2/5/15 更新：`pageBaseColor()` 与 `pageColor()` 的分工、`DropdownRow` 采样源规则、关于页改为原生 `textureBlur`、`SegmentedControl` 文档漂移修正 |
| `CHANGELOG.md` | 新增本轮条目（未发布） |
| `docs/os4-round10-audit.md`（本文件） | 本轮报告 |

---

## 10. 回退依据

- **代码**：本轮起点是 `34b8e15`（第九轮交付）。未提交的改动在工作区；
  需要整体回退时 `git checkout -- .` 并删除 `ui/ButtonColorPreview.kt` 即可回到本轮开始的状态。
- **逐项回退**：
  - 只回退 Glass 底色修复：`Widgets.pageBaseColor()` 的三处调用改回 `pageColor()`
    （`rememberPageBackdrop`、`PrimaryActionBar`、`PageChrome` 的 `containerColor` 删除即可）。
  - 只回退 About：`git checkout 34b8e15 -- src/androidMain/kotlin/com/ayang/giftoolkit/ui/bg/BgEffect.kt src/androidMain/kotlin/com/ayang/giftoolkit/screens/AboutScreen.kt`
    会退回第九轮的 `gradientFilled` 方案（不推荐，仅为回退手段）。
  - 只回退按钮对比页：删 `ui/ButtonColorPreview.kt` + `Route.ButtonColors` + `App.kt` 的 `entry` + `ThemeScreen` 的入口组。
- **截图基线**：`_archive/round10/before/`（本轮修复前）与 `_archive/round10/before/interrupted/`
  （中断前那版构建的六套整页）都保留，可用于逐像素对比。
- **上游**：Miuix 未改动（只有 `scripts/setup-miuix.sh` 打的 `BuildConfig.kt` 补丁），无需回退。
