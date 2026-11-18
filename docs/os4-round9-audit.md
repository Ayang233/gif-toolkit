# 第九轮：1200dp 横屏侧栏遮挡修复、尺寸统一审查、关于页调整与 OS4 / Glass 复核

本轮三件事：**先把 1200dp 横屏下侧栏压住一级页面内容的问题修掉**；再对全项目做一遍尺寸审查；
最后按 InstallerX Revived 的排版重做关于页、把两个许可入口合并，并把开始按钮的配色问题**只考证不改**。

所有结论对应实际源码、上游提交、像素测量与截图。模拟器判不了的（玻璃厚度、真实震动手感、
`textureBlur` 在真机上的表现）单列在 §8。

---

## 0. 基线与取证条件

| 项 | 值 |
| --- | --- |
| 本轮起点 | `GifHelper` `8ab6307`（工作区干净，`git status` 无改动） |
| MIUIX 本地 HEAD | `3177a684` |
| MIUIX 远端 `origin/main` | **`3177a684622a774276c49f52489a43d0d0ed123d`**（`library: Refine glass floating action button`，作者时间 2026-11-18T04:51:05Z） |
| 查询时间 | 2026-11-18 19:28 (+08) |
| 查询方式 | `git ls-remote origin refs/heads/main` **失败**：`CONNECT tunnel failed, response 502`（代理不通）。改用 `gh api repos/compose-miuix-ui/miuix/commits/main` 取到同一个 HEAD，再用同一接口拉最近 20 条提交核对 |
| 提交差异 | 本地 = 远端，**0 个提交的差**（第八轮已经把基线停在 `3177a684`，这 6.5 小时内上游没有新提交） |
| 本地补丁 | 只有 `build-logic/src/main/kotlin/BuildConfig.kt` 的两处 `workingDir = rootDir`（composite build 下 git 调用必须回到 rootDir），`git diff --stat` = `1 file changed, 4 insertions(+)` |
| 库版本 | `0.9.5-beta01`（`6d2554a8 build: bump version to 0.9.5-beta01`），`COMPILE_SDK 37.2 / TARGET_SDK 37`；GIF 助手 `compileSdk` 已在 37.2，`targetSdk` 仍 36 |
| 设备 | AVD `android16`（API 36，物理 1080×2400 / 420dpi，`-gpu swiftshader_indirect`） |
| 1200dp 场景 | `wm density 320` → 2.0 px/dp，`cmd window user-rotation lock 1` → 窗口 **1200×540dp**（2400×1080px） |
| 914dp 场景 | `wm density` 复位 420 → 2.625 px/dp，横屏 **914×411dp** |
| 挖孔 | `DisplayCutout{insets=Rect(0,128,0,0)}`（竖屏在顶部）；横屏旋转后**落在起始边 = 128px = 64dp** |
| 截图 | `_archive/round9/` |

### 0.1 上游 `GlassNavigationRail` 是否对齐

PR #484 = `cd5d4b91 library: Add GlassNavigationRail (#484)`，之后还有三个跟进的提交，
本地全部包含，没有遗漏能力也没有沿用旧实现：

| 提交 | 内容 | 本地 |
| --- | --- | --- |
| `cd5d4b91` | #484 本体：浮动玻璃侧栏 + 展开/折叠形变 | 有 |
| `42b3b53b` | 展开态条目图标与 toggle 图标对齐 | 有 |
| `9981e091` | 展开态支持 secondary items | 有（本项目 3 项，未用 secondary） |
| `499f84fb` | secondary 条目与指示器的动画 | 有 |

同一时间窗内其它可能影响本项目的上游改动：

| 提交 | 影响 | 本项目处理 |
| --- | --- | --- |
| `a5405892 Add GlassTextField and fix floating bar captionBar padding` | 浮条 + `captionBar`（桌面/多窗口）底部重复留白 | 手机/平板场景 `captionBar` 恒为 0，无需适配；`GlassNavigationRail` 自己已 `union(captionBar)` |
| `dadc3bda Avoid double bottom padding with captionBar` | 同上 | 同上 |
| `76504ab0 Refine progressive blur sharp overlay` | 顶栏 `progressiveBlur` 的合成顺序 | 吃库默认，不改参数 |
| `3177a684 Refine glass FAB` | FAB 56dp / 主色层 third / 按下 0.9 | 本项目无 `GlassFloatingActionButton` |

结论：**本轮没有需要跟随的升级动作**，基线原样保留。差异只在本地补丁上，回退方式见 §9。

---

## 1. 1200dp 横屏：侧栏压住一级页面内容

### 1.1 复现

```bash
adb shell wm density 320                  # 2.0 px/dp
adb shell cmd window user-rotation lock 1 # 横屏 → 1200×540dp
adb shell am start -n com.ayang.giftoolkit/.MainActivity
```

现象（`a_1200x540_home.png`，修复前）：侧栏展开成左侧面板，面板右边界压在列表卡片上，
每行的前置图标只露出一半、被玻璃糊成一块灰斑；第一个分组标题「创建」整块被盖住。

### 1.2 像素测量

修复前（2400×1080 截图，坐标是物理像素，÷2 = dp）：

| 测量项 | 像素 | dp |
| --- | --- | --- |
| 侧栏面板左边界 | 153 | 76.5 |
| 侧栏面板右边界 | 652 | 326 |
| 卡片可见起点（从面板下面露出来的位置） | 650 | 325 |
| 列表行前置图标起点 | 654 | 327 |
| 卡片真实左边界（= 12dp 卡片外边距 + 让位宽度） | 548 | 274 |
| **被面板盖住的宽度** | **104** | **52** |

修复后（`b_1200x540_home_fixed.png`）：

| 测量项 | 像素 | dp |
| --- | --- | --- |
| 侧栏面板右边界 | 649 | 324.5 |
| 卡片左边界 | 676 | 338 |
| 间隙 | 27 | 13.5 |

`338 = 64（横屏挖孔安全区）+ 262（侧栏）+ 12（卡片外边距）`，与设计值逐位对得上。

### 1.3 根因

三处叠加，缺一不可：

1. **侧栏是吃安全区的。** `GlassNavigationRail` 内部对
   `WindowInsets.systemBars ∪ captionBar ∪ displayCutout` 做了 `windowInsetsPadding`，
   所以它自己会从起始边再让 64dp：面板起点 = `64 + 12(Margin) = 76dp`（实测 76.5dp，吻合）。
2. **页面内容没吃安全区。** `ui/PageChrome.kt` 的 `pageContentPadding` 只把
   `innerPadding` 的**纵向**部分用了，横向被整块丢掉，`calculateLeftPadding` 只返回
   `horizontal + railInset()`：

   ```kotlin
   // 修复前
   override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
       val extra = if (layoutDirection == LayoutDirection.Ltr) railInset() else 0.dp
       return horizontal + extra          // ← innerPadding 的横向安全区没了
   }
   ```

   于是内容左边界 = `262 + 12 = 274dp`，比侧栏右边界（326dp）**少 52dp**，正好被压住。
3. **竖屏为什么看不出来。** 竖屏挖孔在顶部，横向安全区为 0，两个数字刚好相等
   （`12+250 = 262` = `262`），所以第八轮「1200dp 竖屏内容正确让位」的结论成立，
   但不能替代横屏验收 —— 这正是上一轮漏掉这一场景的原因。

官方对应的做法（`example/shared/.../utils/PageUtils.kt:104-114`）是把安全区与侧栏叠加：

```kotlin
contentInsets.left = safeInsets.left + overlayStart()   // safeInsets 含 systemBars ∪ displayCutout
```

### 1.4 修复

| 文件 | 改动 |
| --- | --- |
| `ui/PageChrome.kt` | `pageContentPadding` 的左右改为 `horizontal + innerPadding.calculate{Left,Right}Padding + railInset()`；安全区值在组合期取一次（只在配置变化时变），侧栏宽度仍在 `calculate*Padding`（量测期）读 |
| `ui/Adaptive.kt` | `Modifier.railStartInset(base)` 真正使用 `base`（之前参数被忽略）；`placeRelative(extra,0)` 统一处理 RTL |
| `ui/SystemInsets.kt` | 新增 `rememberHorizontalSafeInsets()`：`systemBars ∪ displayCutout` 的横向部分，给"不在 Scaffold 内容槽位里"的控件用 |
| `ui/Widgets.kt` | `PrimaryActionBar` 的 `base` 改为 `12dp + 起始安全区`，结束边补上安全区；圆角安全区推导也把起始安全区算进去 |
| `ui/GlassScope.kt` | `PageTopBar` 的玻璃档传 `contentModifier = railStartInset()`（只挪前景、背景带仍满宽，官方 `GlassPage.kt:334` 同款），非玻璃档挂 `modifier` |

没有动的地方（有意）：**录制层不跟着收窄**（`App.kt` 的一级页录制层、`ContentBackdropBox` 都是
`fillMaxSize`），页面背景仍然铺满整屏；侧栏仍只让起始边，右侧不额外留白。

### 1.5 验收场景

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 1200dp 横屏，Rail 展开 | 面板右边界 324.5dp，内容 338dp，间隙 13.5dp，图标完整 | `b_1200x540_home_fixed.png`、`b_land_settings.png` |
| 1200dp 横屏，Rail 折叠 | 内容恢复到起始边（64dp 安全区 + 12dp），无残留偏移；顶栏标题重新居中 | `d2_home_collapsed.png` |
| 1200dp 横屏，再次展开 | 回到 338dp，无跳动 | `d3_home_reexpanded.png` |
| 1200dp 竖屏，Rail 展开 | 让位 262dp，右侧无额外空白 | `e1_portrait1200.png` |
| 914dp 横屏，Rail 折叠 | 顶部胶囊（只显示文字），内容不被挤压 | `c_914_land_home.png` |
| 横屏二级页（工具 / 关于 / 许可） | 内容从 64dp 安全区起，返回键在安全区内 | `b_land_tool.png`、`b_land_about.png` |
| 展开动画过程中 | 仍只在量测期读 `railState.progress`，无整页重组（沿用第八轮的 `() -> Dp` 机制，本轮未改这条） | 源码 |
| 一级页之间切换（工具 / 作品 / 设置） | 三页左边界一致（338dp），无错位 | `b_1200x540_home_fixed.png`、`b_land_library.png`、`b_land_settings.png` |

> 未记录项：展开动画的逐帧录屏。本轮只验证了静止态与折叠/展开后的稳定态；
> 动画期间"内容跟随而不是跳变"由实现方式（量测期读取 + `layout{}`）保证，但没有逐帧像素证据。

---

## 2. 全项目尺寸审查

### 2.1 现有尺寸常量

| 类别 | 出处 | 值 |
| --- | --- | --- |
| 卡片外边距 / 组间距 | `GroupCard`、`AboutCard` | 12dp（下方 12dp） |
| 卡片行内边距 | 库 `BasicComponentDefaults.InsideMargin` | 16dp（四边） |
| 行首图标 | `IconSizes.Row` | 26dp（Glass 28dp 画布） |
| 顶栏返回 / 动作图标 | `Glass.ChevronBackward` / `extended.Back` | 28dp / 24dp |
| 空态插画 | `IconSizes.Empty` | 44dp |
| 媒体区占位图标 | `IconSizes.Media`（本轮新增） | 28dp |
| 底栏高度 | 库 `GlassNavigationBarDefaults.Height` | 54dp（+ 浮起外边距） |
| 底栏左右外边距 | `App.kt` | 24dp |
| 侧栏宽度 / 外边距 | 库 `GlassNavigationRailDefaults` | 250dp / 12dp |
| 页面上下内边距 | `pageContentPadding` | `extraTop 2dp`、`extraBottom 12dp`（各页 12/20/24） |
| 主操作按钮 | `ProgressButton` | 高 50dp、圆角 16dp、内边距 16×12 |
| 库按钮 | 库 `ButtonDefaults` | 高 40dp、圆角 16dp、内边距 16×13 |
| 媒体块圆角 | `PreviewStage` / `AnimatedPreview` | 12dp |
| 关于页 | `AboutScreen` | 图标 88dp / squircle 24dp / 标题 35sp / 版本 14sp / 顶部 40dp |
| 分组标题 | 库 `SmallTitle` | 左右 28dp、上下 8dp |
| 字号 | `MiuixTheme.textStyles.*` | 无自定义（关于页 35sp / 14sp 照官方） |

### 2.2 本轮修掉的不一致

| # | 位置 | 问题 | 处理 |
| --- | --- | --- | --- |
| 1 | `Widgets.kt` 的 `NoteText`（工具页「已生成 …」） | 左 16dp，比卡片正文（28dp）左 12dp，看着像没对齐 | 改 28dp（= 12 卡片 + 16 行内） |
| 2 | 工具页「预览」卡内的 `Box` | 内边距 12dp → 画布起点 24dp，与卡片正文 28dp 差 4dp | 改 `horizontal 16 / vertical 12` |
| 3 | 结果卡 `Column` | 同上，文件名/大小比其它行左 4dp | 同上 |
| 4 | 字幕 `TextField` 外层 | 又补了 12dp，而字段自带 `InsideMargin = 16dp` → 文字实际在 40dp | 去掉横向补白，落在 28dp |
| 5 | 底部主操作条 | 左 0 / 右 12（`railStartInset` 忽略 `base`） | 两侧 12dp（+ 安全区） |
| 6 | 预览占位图标 | 硬编码 `28.dp` | 走 `IconSizes.Media` |
| 7 | 许可正文 | `horizontalPadding = 16dp`，与其它页正文左线差 12dp | 改 28dp |
| 8 | 许可页首卡 | 多补 `padding(top = 8.dp)` 顶替分组标题 | 已有分组标题，删掉 |

### 2.3 保留的差异（有意，不统一）

| 差异 | 原因 |
| --- | --- |
| 主操作按钮 50dp vs 库按钮 40dp | 页面级唯一主操作，视觉重量要高于卡内次要按钮（「分享 / 保存到相册」）；全项目只有这一处，不存在跨页不一致 |
| `extraBottom` 12 / 20 / 24dp | 取决于列表最后一项是什么：默认卡（12）、主操作条（20）、长文本（24） |
| 清空按钮图标 19dp | 行尾动作，视觉重量必须低于行首 26dp 图标；只有一处 |
| 媒体块圆角 12dp vs 卡片 16dp | 内层元素圆角小于外层，避免同心圆角看起来"鼓" |
| 顶栏返回键 28 / 24dp | 玻璃档用 Glass 族 28dp 画布，非玻璃档退回 extended 的 24dp（官方 `BackNavigationIcon` 同样分档） |
| 关于页 88dp / 35sp / 14sp | 照 MIUIX 官方 `AboutPage.kt` 与 InstallerX 的数值，不按列表规范走 |

### 2.4 覆盖到的页面与组合

一级三页（工具 / 作品 / 设置）、工具页、预览与结果区、主题设置、关于、许可列表、许可正文、
底栏 / 侧栏 / 顶栏、卡片与行、Dialog 与 BottomSheet、下拉弹层、空态。

组合：浅色 / 深色、静态配色 / Monet、玻璃开 / 关、竖屏 / 横屏、914dp / 1200dp、
字体缩放 1.3 倍、长文件名与多行描述。

未发现：文字裁切、图标错位、卡片高度不一致、控件挤压、横屏内容过度拉伸。
字体缩放 1.3 下，关于页副标题正常换行、卡片随之增高（`l2_font13_about.png`）。

---

## 3. 关于页：参考实现调查

### 3.1 参考对象（本轮实际读到的源码）

| 应用 | 版本 / 提交 | 文件 |
| --- | --- | --- |
| KernelSU 管理器 | `tiann/KernelSU` main = `f4603d14`（2026-11-17） | `manager/app/src/main/java/me/weishu/kernelsu/ui/screen/about/AboutMiuix.kt` |
| InstallerX Revived | `wxxsfxyzm/InstallerX-Revived` main = `caccb787`（2026-11-12），即用户提到的 `26.11.caccb78` | `app/.../ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt` |
| MIUIX 官方示例 | 本地 `3177a684` | `example/shared/src/commonMain/kotlin/AboutPage.kt` |

KSU 只算 **KernelSU 原版管理器**（`me.weishu.kernelsu`），不含 KernelSU-Next 或其它分支；
它的关于页有两个实现（`AboutMaterial.kt` / `AboutMiuix.kt`），本项目对齐的是 **Miuix 那一份**。

### 3.2 图标与软件名称"跟随背景渐变"的真实实现

三家的写法是同一套，不是渐变 Shader、也不是 `ShaderBrush`：

```kotlin
// InstallerX Revived MiuixAboutPage.kt:493（图标）/ :524（标题）
Image(
    painter = painterResource(id = R.drawable.ic_launcher_monochrome),
    modifier = Modifier.requiredSize(160.dp).textureBlur(
        backdrop = backdrop,                       // 背景的录制层（OS3 动态渐变已画进去）
        shape = RoundedCornerShape(16.dp),
        blurRadius = 200f,                         // 标题是 150f
        colors = BlurColors(blendColors = logoBlend),
        contentBlendMode = BlendMode.DstIn,        // ← 关键：把结果裁进字形/剪影
        enabled = blurEnable,
    ),
)
```

`logoBlend`（与 MIUIX 官方 AboutPage 完全相同的三档混合）：

| 主题 | 混合色 | 混合模式 |
| --- | --- | --- |
| 浅色 | `0xcc4a4a4a` / `0xff4f4f4f` / `0xff1af200` | ColorBurn / LinearLight / Lab |
| 深色 | `0xe6a1a1a1` / `0x4de6e6e6` / `0xff1af500` | ColorDodge / LinearLight / Lab |

要点：

- **图标用单色剪影做掩膜**：InstallerX 用 `ic_launcher_monochrome`（160dp，套在 88dp 盒子里不裁），
  KSU 用 `ic_launcher_foreground` + `ColorFilter.tint(onBackground)`（245dp，套在 100dp `clipToBounds` 里）。
  两者都是"只有 alpha 的形状"，颜色完全由采样结果决定。
- **`contentBlendMode = DstIn`**：`textureBlur` 先采样 backdrop、按 `blendColors` 混一层，
  再用 `DstIn` 把结果限制在内容的 alpha 内 —— 所以字是"字形形状的液态玻璃"，不是"渐变色的字"。
- **同一套 backdrop**：图标、标题、卡片都用页面这一份 `backdrop`，所以颜色与背景同源、同步。
- **回退**：`backdrop == null`（不支持模糊）时，图标退回普通 `Bitmap`/drawable，标题退回主题色实字。
- 不支持 RGBA16F / `isRuntimeShaderSupported()` 为 false 时，InstallerX 还会把 `blurEnable` 置 false，
  走同一套回退。

结论：这里没有任何 `RuntimeShader` / `AGSL` 自写着色器、也没有 `drawWithCache` 手画渐变；
**背景渐变是一条绘制路径，图标/标题是"采样背景 + 混合 + DstIn"另一条**，Glass 模糊则是第三条。

### 3.3 为什么本项目不能照抄

第八轮已经实测：**本机 GPU（swiftshader）上 `textureBlur` 静默不产出像素**。
官方示例 APK 的关于页在同一台模拟器上连标题、两张卡都没有（`_archive/round8/off_about3.png`：
图标是普通位图所以还在，标题与卡片是 `textureBlur` 所以整块消失）。
照抄的结果是"关于页没有应用名"。

所以本轮取同一条**视觉机制**、换一条**绘制路径**：

```
drawContent()                        // 先画字形/剪影，得到 alpha 掩膜
drawRect(brush, BlendMode.SrcIn)     // 只在有像素的地方铺渐变
```

`SrcIn` 与 `DstIn` 在这里是同一件事（保留内容 alpha、替换颜色），
但不需要 RGBA16F 图层、也不依赖 AGSL，任何 GPU 都有像素。
**颜色来源复用 `ui/bg/`**：把 `BgEffectBackground` 的配色状态（预设 + 逐段推进的色段）抽成
`BgPalette`，背景与前景共用同一个实例 —— 只有一套渐变、一套相位，不会出现"背景在变、字不变"。

色标处理：背景的粉/蓝饱和度只有 0.1 上下，直接描上去等于没字。
`BgPalette.accentColors()` 按 HSV 统一压亮度、给饱和度设下限（`S ≥ 0.55`，
浅色 `V = 0.42`、深色 `V = 0.92`），色相与色相漂移保留。

实测对比度（像素采样）：浅色标题 `(57,49,100)` on `(212,201,255)` ≈ **7.5:1**；
深色标题 `(152,193,243)` on `(22,60,123)` ≈ **5.7:1**，都高于 WCAG 对大号粗体文本的 3:1。
隔 6 秒再截一帧，标题色从 `(57,49,100)` 漂到 `(48,52,106)` —— 证明确实跟着色段在变
（`k1_about_v2.png` / `k2_about_v2_shift.png`）。

---

## 4. 关于页与许可入口的修改

### 4.1 版式（对齐 InstallerX `MiuixAboutPage.kt`）

| 项 | 改前（第八轮） | 改后 |
| --- | --- | --- |
| 结构 | 88dp 图标 + 35sp 标题 + 版本；两张卡（源代码 / 许可两行） | 同左，但卡片前加 `SmallTitle("关于")`，卡片内是两行 `ArrowPreference(title, summary)` |
| 图标 | `ic_app_badge`（白底黑主体位图） | 底板 `cardColor(α .72)` + 新增 `ic_app_mark`（纯白剪影）走渐变填充，88dp / squircle 24dp |
| 标题 | 主题色实字 | 跟随背景色段的渐变（机制见 §3.3） |
| 查看源代码 | 前置 `Glass.Light.Link` 图标 + 右侧「GitHub」标签 | **无前置图标**，副标题「在 GitHub 上查看源代码」 |
| 开源许可 | 前置图标 + 右侧「GPL-3.0」；另有独立的「第三方许可」入口 | **无前置图标**，副标题「本应用自身许可证与所用第三方开源库的许可条款」；只剩一个入口 |
| 滚动 | 标题区按斜率淡出/缩到 0.95 | 不变（照官方曲线） |
| 顶栏 | `expandable = false`，标题透明度 `(p-0.35)/0.65` | 不变 |

文案取自 InstallerX 的 `values-zh-rCN/strings.xml`：
`get_source_code` = 「查看源代码」、`get_source_code_detail` = 「在 GitHub 上查看源代码」；
许可行按本项目实际内容改写描述，没有照抄它的「第三方开源库」措辞细节。

### 4.2 两个许可入口合并

改前：`Route.Licenses`（第三方清单）+ `Route.OwnLicense`（GPL-3.0 正文），两个入口两个页面。

改后：一个入口 `Route.Licenses`，页内按分组：

| 分组 | 行 | 正文 asset |
| --- | --- | --- |
| 本项目 | GIF 助手 · gif-toolkit contributors · GPL-3.0 | `licenses/gpl-3.0.txt`（35,823 B，来自仓库 LICENSE） |
| 第三方组件 | Miuix / Miuix Icons / Kotlin / Compose Multiplatform / Jetpack Compose / AndroidX / kotlinx.coroutines（7 项） | `licenses/apache-2.0.txt`（11,560 B，取自 Miuix 仓库 LICENSE） |
| 归属 | NOTICE 与归属声明 | `licenses/notice.txt`（1,195 B，从仓库根 `NOTICE` 复制） |

- 正文页仍是 `LicenseTextScreen`，三组共用；`Route.OwnLicense` 与其 `entry` 一并删除，没有留下死路由。
- 页面底部补一句「第三方组件按各自的原始许可分发，其许可不因本工程的整体许可而改变」，
  与 NOTICE 的措辞一致。
- 内容没有删减：GPL-3.0 全文、Apache-2.0 全文、NOTICE（含 Miuix 示例代码改编归属）都在。
- 长文本显示正常：`b_land_gpl.png`（1200dp 横屏）、段落按空行切分后由 Compose 自行排版。

### 4.3 踩到的一个细节

`ic_app_badge` 的主体（胶片条 14..94）与 `ic_launcher_monochrome`（25.2..82.8）**尺寸不一样**，
直接叠一层剪影盖不住原来的黑主体。所以新增 `ic_app_mark.xml`：路径与 `ic_app_badge` 的
主体完全一致、纯白填充，只作为可着色剪影使用；徽章里的白色波浪线不保留
（同一色系下会糊成一块，反而破坏剪影）。

---

## 5. 开始按钮：无素材状态的颜色（**只考证，未改**）

### 5.1 现状

- 按钮不是 Miuix 的 `Button`，是移植自 InstallerX Revived 的 `ProgressButton`
  （`ui/ProgressButton.kt`，Apache-2.0），外层由 `PrimaryActionBar` 包住。
- 无素材时 `form.canRun = false` → `ProgressButton(enabled = false)`。
- 颜色由 `ActionButton` 决定（`busy = false` 分支）：

  ```kotlin
  ProgressButtonDefaults.progressButtonColors(
      trackColor = MiuixTheme.colorScheme.primary,
      progressColor = MiuixTheme.colorScheme.primary,
      contentColor = MiuixTheme.colorScheme.onPrimary,
  )
  // 禁用时 ActionButton 不传 disabled* 三色 → 落回 ProgressButtonDefaults 的默认值：
  // disabledTrackColor   = MiuixTheme.colorScheme.disabledSecondaryVariant
  // disabledContentColor = MiuixTheme.colorScheme.disabledOnPrimaryButton
  ```

### 5.2 具体颜色值（浅色主题，库默认）

| 角色 | 值 | 用途 |
| --- | --- | --- |
| `disabledSecondaryVariant` | `#F2F2F2` | 按钮禁用态**底色**（实测截图 `(242,242,242)`） |
| `disabledOnPrimaryButton` | `#FFFFFF` | 按钮禁用态**文字** |
| `primary` | `#3482FF` | 可用态底色 |
| `onPrimary` | `#FFFFFF` | 可用态文字 |
| 预览框底色 `pageColor()` | `#F7F7F7`（surface 再叠 3.5% 黑） | 预览画布 |
| 预览框文字 `onSurfaceVariantSummary` | `0x99000000` → 叠在 #F7F7F7 上 ≈ `#636363` | 空态提示语 |

对比度（WCAG 2.1 相对亮度）：

| 组合 | 对比度 | 说明 |
| --- | --- | --- |
| **当前禁用态** `#F2F2F2` + `#FFFFFF` | **1.12:1** | 几乎不可读，这就是用户看到的问题 |
| 预览框 `#F7F7F7` + `#636363` | 5.61:1 | 用户观察到"更醒目"的来源 |
| Miuix 主按钮禁用 `#C2D9FF` + `#FFFFFF` | 1.43:1 | 库自己的配对 |
| Miuix 次按钮禁用 `#F2F2F2` + `#B2B2B2` | 1.89:1 | 库自己的配对 |
| 深色主题当前禁用 `#404040` + `#677893` | 2.31:1 | 深色下没这么糟 |

### 5.3 根因判断

不是"设计成这样的"，而是**配对错了**：`disabledOnPrimaryButton` 是为
`disabledPrimaryButton`（`#C2D9FF`）配的，这里却配在了 `disabledSecondaryVariant`（`#F2F2F2`）上，
两个都是近白的浅灰，直接塌成 1.12:1。库的两套配对分别是 1.43 / 1.89，都比这个高。

参考依据：

- `miuix-ui/.../basic/Button.kt:196-210` `ButtonDefaults.buttonColorsPrimary`：
  `disabledColor = disabledPrimaryButton`、`disabledContentColor = disabledOnPrimaryButton`。
- 同文件 `buttonColors`（次按钮）：`disabledSecondaryVariant` + `disabledOnSecondaryVariant`。
- `ui/ProgressButton.kt:117-119` 的默认值（本轮未改）。

### 5.4 可选方案（本轮都**没有**落地）

| 方案 | 做法 | 对比度 | 风险 |
| --- | --- | --- | --- |
| A 修正配对 | 禁用态改用 `disabledPrimaryButton` + `disabledOnPrimaryButton` | 1.43:1 | 与库主按钮禁用态完全一致；仍偏低，但语义正确（浅蓝=主操作被禁用） |
| B 修正配对（次按钮档） | `disabledSecondaryVariant` + `disabledOnSecondaryVariant` | 1.89:1 | 最接近可读，但按钮看起来像"灰色次按钮"，主操作感消失 |
| C 用户提的方案 | 底色 `pageColor()`、文字 `onSurfaceVariantSummary`（照预览框） | 5.61:1 | 对比最好，但按钮会变成"页面同色的幽灵按钮"，看不出是主操作、也看不出是禁用态 |
| D 折中 | 底色 `disabledPrimaryButton`、文字取 `onSurfaceVariantSummary` 一档 | 5.09:1 | 兼顾"蓝色=主操作"与可读性；偏离库的默认配对，需要单独立项确认 |

结论：**本轮不改**。要动的话建议走 A 或 D，二选一需要先确认"禁用态要不要保留主色语义"。

---

## 6. MIUIX OS4 / Glass 逐项复核

| # | 组件 | 官方实现 | 本地实现 | 对齐状态 | 差异原因 / 证据 |
| --- | --- | --- | --- | --- | --- |
| 1 | `GlassNavigationBar` | `CompactScreenLayout`（`AppContent.kt:581-627`），一级页浮层 | `App.kt` `entry<Route.Root>` 内、录制层兄弟位 | 完全一致 | 参数全吃默认；`n_settings.png`（R8）/ 本轮 `j1_about_phone.png` 底栏正常 |
| 2 | `GlassNavigationRail`（展开） | `AppContent.kt:313-353`，叠在内容上，内容让位 | `App.kt:377-387`，同结构 | **本轮修正偏差** | 让位漏了安全区（§1）；修正后 338dp |
| 3 | `GlassNavigationRail`（折叠） | 顶部居中胶囊、只显示文字 | 同 | 完全一致 | `c_914_land_home.png` |
| 4 | `GlassTopAppBar` | `contentModifier + appPageTopBarInsets()` | `PageTopBar` 传 `contentModifier = railStartInset()` | **本轮补齐** | 之前标题不跟内容让位（§1.4） |
| 5 | `GlassIconButton` | 顶栏返回，28dp Glass 图标 | `PageTopBar` 同 | 完全一致 | `b_land_tool.png` 返回键白胶囊 |
| 6 | `GlassFloatingActionButton` | `3177a684` 改 56dp/主色层/按下 0.9 | 未使用 | 不适用 | 项目无 FAB |
| 7 | `GlassDropdownPreference` | `GlassPage.kt:447` 传整页 backdrop | `DropdownRow` 传 `LocalPageBackdrop` | 完全一致 | 本轮 `i2_theme_dropdown.png`：位置、选中态、箭头正常 |
| 8 | `GlassDialog` | 由最外层 Scaffold 绘制 | `LibraryScreen` 删除确认 | 完全一致 | `act_delete_dialog.png`（R8） |
| 9 | `GlassBottomSheet` | 同上，采样 root backdrop | 作品操作面板 | 完全一致 | 本轮 `b_land_sheet.png`：面板、标题、三项都在，遮罩压暗正确 |
| 10 | 页面 backdrop | `rememberBlurBackdrop()` 先铺底色 | `rememberPageBackdrop()` 同理 | 完全一致 | 关于页 `opaqueBase = false` 是唯一例外 |
| 11 | root backdrop | 录在 Pager 外 | `App.kt` 的 `rootBackdrop` | 完全一致 | 坐标一致（R7 §3 结论仍成立） |
| 12 | 层级关系 | 玻璃节点是录制层兄弟 | 同（AGENTS 第 1 条） | 完全一致 | 本轮未新增录制层父子关系，无 `prepareTreeImpl` 崩溃 |
| 13 | Insets / 安全区 / 侧栏让位 | `safeInsets + overlayStart` | 本轮改为同样叠加 | **本轮修正** | §1 |
| 14 | 横竖屏 / 宽窄屏 | 840dp 用 Rail、1200dp 展开 | 同（`ui/Adaptive.kt`） | 完全一致 | 914 / 1200 两档实测 |
| 15 | 玻璃开关关闭回退 | 组件自带 fallback | `LocalGlassEnabled` 单点决策 | 完全一致 | `g1_glassoff_home.png`、`h2_phone_tool.png` |
| 16 | 不支持模糊的降级 | `isRenderEffectSupported()` + 用户开关 | 同 | 完全一致（但见 §8） | 该判定只查 SDK 版本，查不出 GPU 翻车 |
| 17 | 触觉反馈 | 见 R8 §4 清单 | 未改动 | 完全一致 | 本轮没有新增可点控件的反馈 |
| 18 | 字体 / 图标 / 圆角 / 控件尺寸 | 库默认 | 走 `IconSizes` + 库常量 | 见 §2 | 本轮统一了 8 处 |

---

## 7. 测试与回归

| 项 | 结果 |
| --- | --- |
| `./gradlew :assembleDebug` | BUILD SUCCESSFUL（26s / 33s / 1m35s 三次，最后一次含版本号变更） |
| `./gradlew :assembleRelease` | BUILD SUCCESSFUL，3,311,827 B（R8 开启，debug 签名） |
| release 包实机跑通 | `R9_01_home_1200x540.png` ~ `R9_06_notice.png`（版本 1.7（9），1200dp 横屏，浅色 + 玻璃开） |
| 自动化测试 | **项目没有测试源集**（`src/**/test`、`androidTest` 均不存在），`./gradlew test` 无可执行用例 —— 如实记录，不虚报数量 |
| 编译警告 | 无 error；项目自身只剩 `core/MediaTools.kt:214` 一处 `COLOR_FormatYUV420SemiPlanar` 的 JDK 弃用告警，与本轮无关 |
| 1200dp 横屏一级三页 | 通过（§1.5） |
| 1200dp 横屏二级页（工具 / 关于 / 许可 / GPL 正文） | 通过 |
| 1200dp 竖屏 | 通过 |
| 914dp 横屏折叠 | 通过 |
| 玻璃开 / 关 | 通过（`g1_glassoff_home.png`、`h2_phone_tool.png`） |
| 浅色 / 深色 | 通过（`j1_about_phone.png`、`f3_dark_about.png`） |
| 字体缩放 1.3 | 通过（`l2_font13_about.png`） |
| 下拉弹层 / 底部面板 / 删除确认 | 通过 |
| 页面切换与返回 | 通过（含 1200dp 横屏侧栏折叠/展开往返） |
| 崩溃 / ANR | 无 |

未验证：

| 项 | 原因 |
| --- | --- |
| 玻璃厚度、高光、边缘折射 | 模拟器 `swiftshader_indirect` |
| `textureBlur` 在真机上的像素表现 | 本机该路径不产出像素；真机若正常，关于页可换回参考实现的写法（§3.2） |
| 真实震动手感 | 模拟器只能确认调用路径 |
| 展开动画逐帧 | 只验证静止态（§1.5 备注） |
| 生成进度与取消的中间态 | 素材太小，任务 <1s |

---

## 8. 改动文件

| 文件 | 改动 |
| --- | --- |
| `ui/PageChrome.kt` | `pageContentPadding` 叠加横向安全区（横屏遮挡根因） |
| `ui/Adaptive.kt` | `railStartInset(base)` 真正使用 `base`；RTL 用 `placeRelative` 统一 |
| `ui/SystemInsets.kt` | 新增 `rememberHorizontalSafeInsets()` |
| `ui/Widgets.kt` | `PrimaryActionBar` 两侧对称 + 安全区；`NoteText` 16→28dp；`IconSizes.Media` |
| `ui/GlassScope.kt` | `PageTopBar` 接 `contentModifier` / `modifier` 让位 |
| `ui/bg/BgEffect.kt` | 抽出 `BgPalette` + `rememberBgPalette()`；新增 `gradientFilled()` / `accentBrush()` |
| `screens/AboutScreen.kt` | 按 InstallerX 重排；图标与标题跟随背景色段；单许可入口 |
| `screens/LicensesScreen.kt` | 分段式统一许可页；正文页内边距 16→28dp |
| `screens/ToolScreen.kt` | 预览卡/结果卡内边距、字幕输入框横向补白 |
| `ui/LicenseCatalog.kt` | 新增 `LicenseGroup` / `projectLicense` / `noticeEntry` / `licenseEntries` |
| `Route.kt` | 删除 `OwnLicense` |
| `App.kt` | 删除 `OwnLicense` 目的地与 `onOpenOwnLicense` 传参 |
| `res/drawable/ic_app_mark.xml` | 新增：关于页可着色剪影 |
| `assets/licenses/notice.txt` | 新增：仓库 NOTICE 副本 |
| `build.gradle.kts` | 版本 1.6（8）→ 1.7（9） |
| `CHANGELOG.md` / `AGENTS.md` / `docs/os4-round9-audit.md` | 文档 |

---

## 9. 回退依据

| 项 | 方式 |
| --- | --- |
| 代码基线 | 本轮起点 `8ab6307`（`git checkout 8ab6307 -- .` 可整轮回退） |
| MIUIX 基线 | `3177a684`，未动；本地补丁只有 `BuildConfig.kt` 的 4 行，`scripts/setup-miuix.sh` 可重建 |
| 本轮相对 1.6 的完整改动 | `git diff 8ab6307` |
| 安装包 | `_archive/round8/GifHelper-1.6.apk`（3,311,215 B）可回装 |
| 数据集 | 本轮没有改动生成逻辑，`files/outputs/` 与 MediaStore 无副作用 |
