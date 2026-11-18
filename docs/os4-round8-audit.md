# 第八轮：MIUIX 最新基线、Glass 深度适配、触觉反馈对齐与本项目专项复查

本轮六件事：把 Miuix 拉到真正的 `origin/main` 并先编译运行官方 APK、按作者迁移指南复核采样结构、
补一份触觉反馈审计并逐项对齐、重新取证官方底栏行为、重做关于页、做完 Glass 与业务的回归。

所有结论都对应实际源码路径、commit、logcat / 像素 / 文件系统证据。
模拟器判不了的（玻璃厚度、高光、真实震动手感）单列在 §9。

---

## 0. 基线与取证条件

| 项 | 值 |
| --- | --- |
| 本轮起点 | `GifHelper` `0302fe8`（工作区干净）；`miuix` 本地 `a0f47eb9` |
| 起点说明 | `git diff 499f84fb a0f47eb9` 为**空** —— 上一轮中断时用 `git am` 落下上游补丁，内容等于上游 `499f84fb`，只是 SHA 不同 |
| 本轮 MIUIX `origin/main` | **`3177a684622a774276c49f52489a43d0d0ed123d`**（`library: Refine glass floating action button`，2026-11-18 通过 `git ls-remote` 实时取得） |
| 真实差异 | 只有 `3177a684` 一个提交：`GlassFloatingActionButton.kt`（56dp、主色层从 first 改 third、按下缩放到 0.9）+ 两份文档。`31 insertions(+), 23 deletions(-)`，`miuix-ui` / `miuix-preference` / `miuix-nav` **零改动** |
| composite 补丁 | `build-logic/src/main/kotlin/BuildConfig.kt` 两处 `workingDir = rootDir`，`git reset --hard origin/main` 后重新 `git apply`，未丢 |
| 官方示例 APK | `miuix/example/android/build/outputs/apk/debug/Miuix-v1.1.0(51)-debug.apk`，17,073,862 B，2026-11-18 18:02 现场编译 |
| 构建命令 | `cd miuix && ./gradlew :example:android:assembleDebug --console=plain` → `BUILD SUCCESSFUL in 58s` |
| GIF 助手 APK | 同一 `3177a684` 上 `./gradlew :assembleDebug` |
| 设备 | AVD `android16`（API 36，1080×2400 / 420dpi，`-gpu swiftshader_indirect`）；横屏可用宽 **914dp**；`wm density 144` → **1200dp** |
| 截图 / 录屏 | `_archive/round8/` |

### 0.1 升级时踩到的一件事：`compileSdk` 必须到 37.2

新基线的 `build-logic/BuildConfig.kt` 把 `COMPILE_SDK = 37 / COMPILE_SDK_MINOR = 2`、`TARGET_SDK = 37`。
GIF 助手原来写 `compileSdk = 37`，AGP 在配置期直接拒绝：

```
Dependency ':miuix:miuix-material' requires libraries and applications that
depend on it to compile against version 37.2 or later of the Android APIs.
: is currently compiled against android-37.0.
```

改成 AGP 9 的块语法后通过（`build.gradle.kts`）：

```kotlin
compileSdk { version = release(37) { minorApiLevel = 2 } }
```

`targetSdk` 仍是 36：只放开编译期 API，不改运行时行为（预测性返回等验收前提不变）。

### 0.2 官方 APK 的安装与运行

`versionCode` 变成 51（本地是用 `git am` 重放的上游历史，`git rev-list --count` 比上游小），
直接被 `INSTALL_FAILED_VERSION_DOWNGRADE` 挡住（设备上还是上一轮的 1307）。
用 `adb install -r -d` 覆盖安装，启动正常：`_archive/round8/off_home.png`（首页）、
`off_settings.png`（设置页，顶部 `Settings / v1.1.0 (51)`）、`off_about3.png`（关于页）。

---

## 1. 作者迁移指南：读了，落到代码里的只有三条

来源：`https://github.com/YuKongA/Mishka/blob/main/docs/miuix-glass-migration.md`
（Mishka 用 `miuix = "0.9.5-beta01"`，与本轮基线同档）。

指南有九条工程红线，逐条对过本项目，**真正有偏差的是第 2 条**：

| 指南要求 | 本项目原状 | 处理 |
| --- | --- | --- |
| 录制 backdrop 先画不透明底色再 `drawContent()` | `rememberPageBackdrop` 已 `drawRect(pageColor())` | 无需改 |
| 禁止自采样（录制层不能包含消费它的玻璃） | AGENTS 第 1 条，历史上真崩过 | 无需改；本轮**又撞了一次**（见 §6.3） |
| 两级宿主：页面局部层给顶栏/底栏，整页层给 Dialog/BottomSheet/Dropdown | `contentBackdrop` + `LocalRootBackdrop` | 无需改 |
| `layerBackdrop` 必须在 insets / padding / 侧栏缩进**之前** | `App.kt` 里是 `fillMaxSize().then(layerBackdrop)`，页面里是 `Box(fillMaxSize().contentBackdropModifier)` | 无需改（顺序已经是对的） |
| **禁止组合期读帧率级状态**（`railState.progress`、滚动偏移） | `LocalRailInset` 是 **Dp 值**，`App.kt` 组合期算 `(ExpandedWidth+Margin) * railState.progress` | **已改**，见 §2 |
| 顶栏父容器禁止 `clipToBounds()` | 页面顶层没有裁剪 | 无需改 |
| `overScrollVertical` 放在 `nestedScroll` 之前 | `pageScrollModifiers` 就是 `scrollEndHaptic → overScrollVertical → nestedScroll` | 无需改 |
| 模糊不可用时 backdrop 置 `null` | `glassEnabled && isGlassSupported()` 单点决策 | 无需改 |
| 顶栏缩进不要重复叠加 | 本项目顶栏不消费侧栏缩进（只有内容让位） | 记录为差异，见 §2.2 |

## 2. 每帧状态：`LocalRailInset` 从 Dp 改成函数

### 2.1 问题

`ui/Adaptive.kt` 原来下发的是值：

```kotlin
val LocalRailInset = compositionLocalOf { 0.dp }   // App.kt 里 = (250+12).dp * railState.progress
```

`railState.progress` 是 `Animatable.value`（`GlassNavigationRail.kt:255`），
侧栏展开/折叠的那 300ms 里每帧都变。组合期解包它 → `LocalRailInset` 的每个消费者
（`pageContentPadding` → `LazyColumn` 的 `contentPadding`、`PrimaryActionBar` 的内边距）
在整个动画期间**每帧整页重组**，而且 `contentPadding` 一变就要重排整个列表。

### 2.2 改法

对齐官方 `AppContent.kt:219-236` / `PageUtils.kt:96-133`：**下发函数，量测期才求值**。

```kotlin
val LocalRailInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

// App.kt
val railInset: () -> Dp = remember(railVisible) {
    if (railVisible) ({ (ExpandedWidth + Margin) * railState.progress }) else ({ 0.dp })
}
```

消费者：

- `pageContentPadding()` 返回自定义 `PaddingValues`，在 `calculateLeftPadding / calculateRightPadding`
  里调 `railInset()`。`LazyColumn` 是在**量测期**读这两个方法的，所以侧栏动画期间只重排、不重组。
  仍然只让 start 一侧（第 7 轮修的「右侧白空 250dp」不复发）。
- `PrimaryActionBar` 原来写 `.padding(start = sideGap + LocalRailInset.current)`，
  `Modifier.padding` 只吃即时值，所以新增 `Modifier.railStartInset(base)`（`ui/Adaptive.kt`），
  用 `layout {}` 在量测期读同一份函数并 `placeRelative`，行为与 padding 等价。

验收：`wm density 144`（1200dp）下展开态内容正确让位 262dp、右侧无额外留白
（`_archive/round8/n_rail_portrait1200.png`）；横屏 914dp 折叠态让位 0
（`n_land.png`）。侧栏展开过程中不再有整页重组（滚动位置、滑动状态都不再被打断）。

> 踩坑记录：先试过 `wm density 144` + 横屏，得到一张内容被压扁、文字被裁的截图。
> 那是「旋转 + 改密度」组合下模拟器/renderer 的问题（同一份 APK 在 1200dp **竖屏**下完全正常，
> 且与 R7 同结构），换回竖屏复测即恢复。以后测 1200dp 用竖屏。

## 3. 官方底栏行为：录屏逐帧重新取证（第 7 轮结论成立，措辞要改）

第 7 轮写的「底栏画在页面内部，随页面一起滑走」和实际看到的「底栏固定不动」并不矛盾，
但都不准确。本轮在**新基线**上重录：`_archive/round8/nav8.mp4`（`animator_duration_scale = 10`，
60fps，41 帧），场景「设置 → 关于」。

| 帧 | 像素事实 |
| --- | --- |
| `f_018` | 退出一级页场景被整体左移并压暗：底栏「作品」一项的文字中心从 x≈359 移到 x≈170（**-189px**）；同时新页左边缘在 x≈318 |
| `f_019` | 底栏右半截被新页盖住，只剩左侧「Icon」一项露在旧页窄条里（`crop_019.png`） |
| `f_021` | 新页已铺满，底栏完全不可见，旧页只剩左侧一条被压暗的残边 |
| `f_024+` | 稳定在二级页，没有底栏 |

结论（源码与像素一致）：**底栏是一级页场景里的普通子节点**，跟着一级页一起被
`NavTransitions.MiuixDefault` 平移（`translationX = -0.25w · d`）并压暗（`alpha = 1 - 0.1d`），
随后被新页盖住；返回时被揭开。它**没有**自己的进退场动画，也**不是**钉在屏幕上不动的浮层。
-189/270 ≈ 0.70 正好是那一帧的过渡进度，与新页左边缘 318 = 1080×(1-0.70) 互为印证。

因此 GIF 助手「底栏放在 `entry<Route.Root>` 里、不做 `stack.size` 挂载判断」是正确结构，
本轮不动（`App.kt:230-325`）。

## 4. MIUIX 触觉反馈审计

### 4.1 全量检索范围

`miuix-*/src/**/*.kt` + `example/shared/src`，检索
`LocalHapticFeedback / HapticFeedbackType / performHapticFeedback / hapticEffect / SliderHapticState / Vibrator / VibrationEffect`。
命中 23 个文件，去掉纯转发后剩 8 个组件族 + 一个滚动修饰符。

### 4.2 区分三类来源

| 类别 | 本项目涉及 |
| --- | --- |
| A. 组件内部主动调用 | `Switch` / `Checkbox` / `RadioButton` / `RadioButtonPreference` / `Slider`（含 `VerticalSlider`、`RangeSlider`、`ColorPicker` 内置滑杆）/ `NumberPicker` / `PullToRefresh` / 各 Dropdown、Popup / `scrollEndHaptic` |
| B. 平台/Compose 间接产生 | **没有**。Miuix 的 `Button`、`ArrowPreference`、`BasicComponent`、`GlassIconButton`、`GlassNavigationBar`、`GlassNavigationRail`、`GlassTopAppBar`、`GlassBottomSheet`、`GlassDialog` 全部**不主动触发**，也不依赖 `clickable` 的默认反馈（Compose 的 `clickable` 本身不产生震动） |
| C. 官方 Example 自己加的 | `pageScrollModifiers(enableScrollEndHaptic)`（一个设置项）、`AppState.showScrollEndHaptic`；本次未发现其它自定义反馈 |

### 4.3 触觉清单

`constant=` 是 `dumpsys vibrator_manager` 里 `reason: performHapticFeedback(constant=N)` 的原值
（模拟器能确认「API 被调用 + 系统接受」，不能确认手感）。触发实现全部来自当前源码行。

| 组件 | 用户动作 | 触发时机 | 反馈类型 | 默认开启 | 触发实现 | GIF 助手对应位置 |
| --- | --- | --- | --- | --- | --- | --- |
| `Switch` | 点按开关（不是整行） | 状态翻转的那一次 | `ToggleOn` / `ToggleOff`（实测 constant=21 / 22） | 是 | `Switch.kt:123` | `SwitchRow` → `SwitchPreference` |
| `Switch` | 拖动圆点 | 跨过中点（±21px）时一次；到边界再补一次 | 同上，`hasVibrated/hasVibratedOnce` 双向抑制 | 是 | `Switch.kt:186-208` | 同上 |
| `SwitchPreference` | 点**整行**（文字区） | **无反馈** | — | — | `SwitchPreference.kt` 的 `BasicComponent(onClick)` 不调用反馈 | 同上（与官方一致，未额外补） |
| `Checkbox` | 点按 | 每次状态变化 | Off→`ToggleOn`，On→`ToggleOff`，Indeterminate→`SegmentTick` | 是 | `Checkbox.kt:150` | 本项目无 |
| `RadioButton` / `RadioButtonPreference` | 选中某一项 | 每次点按 | 未选中→`ToggleOn`，已选中→`ToggleOff` | 是 | `RadioButton.kt:101`、`RadioButtonPreference.kt:131` | 色板（自绘，已对齐，见 §5） |
| `Slider` / `VerticalSlider` / `RangeSlider` / `ColorPicker` 滑杆 | 拖到 0% 或 100% | 首次到边界 | `GestureThresholdActivate`（实测 constant=23） | **是（默认 `SliderHapticEffect.Edge`）** | `Slider.kt:1043-1058`，`edgeFeedbackTriggered` 抑制重复 | 所有 `SliderRow` |
| 同上，`hapticEffect = Step` | 每跨一个离散档位 | 档位变化，且不在边界 | `TextHandleMove` | 否（需显式传） | `Slider.kt:1075-1082` | 本项目未传 = 不触发（与官方默认一致） |
| 同上，自定义 `keyPoints` | 磁吸到关键点 | 进入关键点 | `TextHandleMove`（`handleKeyPointHaptic`） | 否 | `Slider.kt:1109+` | 未使用 |
| `NumberPicker` | 滚动换项 | 每换一项 | `TextHandleMove` | 是 | `NumberPicker.kt:135` | 未使用 |
| `PullToRefresh` | 下拉到阈值 | 首次到达 `ThresholdReached` | `GestureThresholdActivate` | 是 | `PullToRefresh.kt:734` | 未使用 |
| `GlassDropdownPreference` | 展开 | 展开瞬间 | `ContextClick`（实测 constant=6） | 是 | `GlassDropdownPreference.kt:138` | `DropdownRow`（主题模式 / 水印位置 / 宫格列数 / 预测性返回） |
| `GlassDropdownPreference` | 选中某一项 | 选择生效 | `Confirm`（实测 constant=16） | 是 | `GlassDropdownPreference.kt:172` | 同上 |
| `Overlay/Window Dropdown*`、`*DropdownPopup` | 展开 / 选中 | 同上 | `ContextClick` / `Confirm` | 是 | `OverlayDropdownMenu.kt:120`、`OverlayDropdownPopup.kt:81` 等 | 未使用（本项目统一走 Glass 档） |
| `Modifier.scrollEndHaptic` | 列表甩到顶/底 | 越界 fling 且未被消费 | `TextHandleMove`（可传参） | 需显式挂 | `ScrollEndHaptic.kt:84-96`，`onPreScroll` 里复位 | `pageScrollModifiers`（默认 true） |
| `GlassNavigationBar` / `Rail` / `TopAppBar` / `GlassBottomSheet` / `GlassDialog` / `Button` | 任意 | **无反馈** | — | — | 全文件检索无 haptic 调用 | 底栏 / 侧栏 / 顶栏 / 作品面板 / 删除确认 / 主操作按钮 —— 一律不加 |

### 4.4 模拟器实测（`dumpsys vibrator_manager`）

| 动作 | 结果 |
| --- | --- |
| 官方 Example：点开关 | `constant=21`（开）/ 再点 `22`（关），每次一条，无重复 |
| 官方 Example：点复选框（On→Off） | `constant=22` |
| 官方 Example：展开 Color Mode 下拉 | `constant=6` |
| 官方 Example：选中 Light | `constant=16` |
| 官方 Example：切底栏 tab / 点 `ArrowPreference` 行 | **无记录** |
| GIF 助手：`SwitchRow` 点整行文字区 | 无记录（与官方 `SwitchPreference` 同源同行为） |
| GIF 助手：点开关本体 | `constant=22` |
| GIF 助手：分辨率滑块拖到最大 | `constant=23`，一条 |
| GIF 助手：在最大值处再拖 | **无新记录**（`edgeFeedbackTriggered` 生效） |
| GIF 助手：色板换色 / 再点同一个色 | 一条 `21` / 无新记录 |

> `constant` 与 Compose `HapticFeedbackType` 的对应关系按「源码分支 ↔ 实测事件」配对得出：
> 开关的开/关分支对应 21/22，滑杆边界分支对应 23，下拉展开/选中对应 6/16。
> 没有把 21/22/23 直接写成某个 `HapticFeedbackConstants` 常量名，避免张冠李戴。

### 4.5 系统设置兼容

全部走 `LocalHapticFeedback`（Compose 标准通道），系统「触觉反馈」开关、强度档位、
无马达设备的降级都由系统接管；项目里没有 `Vibrator`、没有自建震动、没有“震动强度”设置项。
模拟器上关闭系统触觉后再触发，`dumpsys` 仍记录请求、但 `VibrationSettings.vibrateOn` 侧不再执行 ——
这一条属于**系统行为**，真机手感另列 §9。

## 5. GIF 助手对齐了哪些反馈

只有一处需要补：`screens/ToolScreen.kt` 的 `ColorSwatches`（唯一自绘的“选择”控件，`.clickable`）。
依据是官方同类选择控件（`RadioButtonPreference.kt:131` / `RadioButton.kt:101`）在“选中项发生变化”时报 `ToggleOn`；
按迁移指南「无状态变化不加反馈」，点已选中的颜色直接跳过。

```kotlin
val hapticFeedback = LocalHapticFeedback.current
.clickable {
    if (form.keyColor != value.toInt()) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
        form.keyColor = value.toInt()
    }
}
```

其余位置**故意不加**：底栏/Rail 选择、`ArrowPreference` 行、主操作按钮、作品面板条目，
官方对应组件都没有反馈（§4.3 最后一行）；`SwitchPreference` 整行点击无反馈是官方现状，
本轮不改 —— 真觉得别扭的话应该上游修，而不是在本项目里加一层和官方不一致的行为。

## 6. 关于页：照官方版式重做

### 6.1 官方长什么样

`example/shared/src/commonMain/kotlin/AboutPage.kt`：整块 OS3 动态渐变背景（`BgEffectBackground`）、
88dp 图标 + 35sp 粗体标题 + 版本号，滚动时这三块按不同斜率淡出/缩到 0.95；
卡片是 `ArrowPreference` 行；顶栏 `expandable = false`，标题透明度 = `(progress-0.35)/0.65`
（首屏只有返回键，滚起来才出标题）；卡片底色 `Color.Transparent` + `textureBlur` 玻璃，
标题是 `textureBlur(..., contentBlendMode = DstIn)` 的“液态玻璃字”。

### 6.2 本项目实现（`screens/AboutScreen.kt` + 新增 `ui/bg/`）

| 项 | 做法 |
| --- | --- |
| 渐变背景 | 移植官方 `component/effect/`（Painter / Modifier / OS3 着色器 / 配色预设），三个文件：`ui/bg/BgEffect.kt`、`BgEffectShader.kt`。只保留关于页实际用到的 OS3 分支，删掉示例里 OS2 与设置面板相关代码 |
| 层级 | 渐变铺在**页面根部**、Scaffold 之外（`Scaffold(containerColor = Transparent, contentWindowInsets = 0)`），这样渐变才能一直延伸到状态栏下面 —— 否则顶栏下方会切出一条硬边 |
| 录制层 | `rememberGlassScope(opaqueBase = false)`：关于页背后已经是整屏不透明渐变，录制层再垫 `pageColor()` 会把渐变整块盖掉 |
| 标题/图标/版本 | 位置、字号、字重、滚动淡出曲线全部照官方；**标题用主题色实字**，不是玻璃字（原因见 6.3） |
| 卡片 | `Card` + `cardColor().copy(alpha = 0.82f)`，渐变透一层上来；**不用** `textureBlur`（同因） |
| 行 | 查看源代码（GitHub）/ 开源许可（GPL-3.0）/ 第三方许可 —— 三行，用户选定 |
| 许可正文 | 新增 `Route.OwnLicense` + `assets/licenses/gpl-3.0.txt`；`LicenseDetailScreen` 拆出 `LicenseTextScreen(asset,title,license)` 复用 |

### 6.3 两处与官方的差异（有证据，不是省事）

官方 APK 在同一台模拟器上打开关于页，**大标题和两张卡片完全不产出像素**
（`_archive/round8/off_about3.png`：图标和版本号之间是空的，行直接浮在渐变上，没有卡片底）。
说明 `textureBlur` 这条 AGSL 路径在该 GPU 上静默失败（不报错、不降级）。
若照抄，GIF 助手的关于页会变成“没有应用名、没有卡片”——所以标题用实字、卡片用半透明底色，
其余（字号、字重、滚动淡出、间距、玻璃顶栏）全部照官方。

> 中途还撞了一次第 1 条硬约束：为了让顶栏采样到渐变，曾在页面根部给渐变套一层满屏
> `layerBackdrop` 并把顶栏的采样源指过去 —— 顶栏成了录制层的**子节点**，采样到自己，
> `RenderNode::prepareTreeImpl` 原生栈溢出闪退（18:31 的 logcat 里 `prepareTreeImpl` 递归了几百层，
> 缓冲区已滚动覆盖，未留原件；同一现象与 R6 那次崩溃同源）。
> 现结构是「渐变在页面根部（不录制）+ Scaffold 透明 + 顶栏采样正文录制层」，
> 顶栏与正文录制层是兄弟关系，不再自采样。

### 6.4 关于页在四个组合下的表现

| 组合 | 结果 |
| --- | --- |
| 浅色 + 玻璃开 | 渐变从状态栏下方一直铺到导航栏，无硬边；图标/标题/版本/三行齐全（`n_about8.png`） |
| 深色 + 玻璃开 | 紫色渐变、白字可读、卡片近黑半透明（`n_about_dark.png`） |
| 浅色 + 玻璃关 | 顶栏退回普通 `TopAppBar`（不透明），渐变仍在正文里；不会出现硬边消失后的割裂 |
| 横屏 / 1200dp | 渐变按 PAD 预设；`expandable = false`，标题不展开 |

## 7. 全项目 Glass 逐项复查

| # | 位置 | 组件 | 采样源 | 录制层级 | 本轮结论 |
| --- | --- | --- | --- | --- | --- |
| 1 | 底部导航胶囊 | `GlassNavigationBar` | 一级页录制层（Pager 外） | 兄弟节点 | 通过（`n_settings.png`） |
| 2 | 折叠态 Rail 胶囊（914dp） | `GlassNavigationRail` | 同上 | 一级页之外 | 通过（`n_land.png`） |
| 3 | 展开态 Rail 面板（1200dp） | 同上 | 同上 | 同上 | 通过，内容只让 start 侧（`n_rail_portrait1200.png`） |
| 4 | 页面顶栏 | `GlassTopAppBar` + progressiveBlur | 页面录制层 | 兄弟节点 | 通过（工具页滚动时标题压着模糊内容，`gen_result_card.png`） |
| 5 | 顶栏 band / progressive | `GlassTopAppBarDefaults.bandBrush` | 同上 | 同上 | 通过 |
| 6 | 页面卡片 | `Card` + `cardColor()` | 不采样 | — | 非玻璃，无需改 |
| 7 | 页面内下拉 | `GlassDropdownPreference` | 页面录制层 / `LocalRootBackdrop` | 弹层宿主 | 通过（结构与 R7 相同） |
| 8 | Dialog（删除确认） | `GlassDialog` | `LocalRootBackdrop` | 最外层 Scaffold | 通过（`act_delete_dialog.png`） |
| 9 | 二级页顶栏返回 | `GlassIconButton` | 页面录制层 | 兄弟节点 | 通过（工具页截图里返回键是白胶囊） |
| 10 | 作品操作菜单 | `GlassBottomSheet` | `LocalRootBackdrop` | 最外层 Scaffold | 通过（`sheet_new.png`，采样坐标修复保持有效） |
| 11 | 关于页顶栏 / 渐变 | `GlassTopAppBar` + 自定义背景 | 正文录制层（无底色） | 兄弟节点 | 本轮新做，见 §6 |

明暗、玻璃开关、横竖屏都各跑了一遍，无闪退、无平色块、无自采样。

## 8. 业务回归

| 项 | 结果 | 证据 |
| --- | --- | --- |
| 导入 | 系统照片选择器选入 1 个 GIF → `已选择 1 个文件` | `import_ok.png` |
| 素材预览 | 预览区出图（蓝圆 + 橙方） | `gen_result2.png` |
| 生成 | `已生成 compress_1794999177468.gif · 38 KB`，文件落盘 | `files/outputs/` 时间戳 18:51 |
| 进度 / 取消 | **未验证**：小 GIF 生成 <1s，按钮来不及进「取消生成 · x%」 | — |
| 结果展示 | 结果区出现文件卡与「分享 / 保存到相册」 | `gen_result_card.png` |
| 保存到相册 | `content query` 出现 `compress_1794917968167.gif`，`date_added=1794998530` | MediaStore |
| 分享 | 系统分享面板（`ChooserActivityLauncher` 获得焦点） | `act_share.png` |
| 我的作品 | 列表 + `共 18 个导出文件` | `lib_page.png` |
| 作品操作菜单 | 面板打开、标题为文件名 | `sheet_new.png` |
| 删除 + 确认 | `GlassDialog` 确认后计数 18 → 17 | `act_delete_confirm.png` / `act_delete_done.png` |

## 9. 未验证 / 需要真机

| 项 | 原因 |
| --- | --- |
| 玻璃厚度、高光、边缘折射、抗锯齿 | 模拟器 `-gpu swiftshader_indirect`，与真机 GPU 差异大 |
| `textureBlur` 在真机上的表现 | 本机该路径不产出像素（官方 APK 同样），真机是否正常未知；若正常，关于页可换回玻璃字与玻璃卡 |
| 真实震动手感、强度、时长 | 模拟器只能确认调用路径与系统接受 |
| 系统触觉开关/强度档位对反馈的实际影响 | 需真机 |
| 预测性返回的手势跟手动画 | 本轮只用返回键验证了「关面板 / 回一级」 |
| 生成进度条与取消 | 素材太小，任务 <1s，未观察到中间态 |
| 连续快速进出二级页 20 次以上 | 只做了数次往返 |
| 进程死亡后的完整状态恢复 | 本轮只验证了杀进程冷启动后栈能恢复（回到工具页），未做逐项状态核对 |

## 10. 改动文件

| 文件 | 改动 |
| --- | --- |
| `build.gradle.kts` | `compileSdk` 改块语法到 37.2 |
| `ui/Adaptive.kt` | `LocalRailInset` 改为 `() -> Dp`；新增 `Modifier.railStartInset()` |
| `App.kt` | 下发 `railInset` 函数；新增 `Route.OwnLicense` 目的地；About 传入新回调 |
| `ui/PageChrome.kt` | `pageContentPadding` 改成自定义 `PaddingValues`，量测期读侧栏让位 |
| `ui/Widgets.kt` | `PrimaryActionBar` 改用 `railStartInset()` |
| `ui/GlassScope.kt` | `rememberPageBackdrop(opaqueBase)`、`rememberGlassScope(opaqueBase)`；`PageTopBar` 增加 `titleAlpha` / `backgroundAlpha` 覆盖参数 |
| `ui/bg/BgEffect.kt`、`ui/bg/BgEffectShader.kt` | 新增：关于页 OS3 动态渐变（移植官方示例，Apache-2.0） |
| `screens/AboutScreen.kt` | 重写为官方版式 |
| `screens/LicensesScreen.kt` | 拆出 `LicenseTextScreen` |
| `screens/ToolScreen.kt` | 色板补 `ToggleOn` 反馈 |
| `Route.kt` | 新增 `OwnLicense` |
| `assets/licenses/gpl-3.0.txt` | 新增（本项目 LICENSE 正文） |
| `AGENTS.md` | 基线号、`compileSdk`、`LocalRailInset` 约束、关于页结构说明 |
| `CHANGELOG.md` | 本轮条目 |
| `docs/os4-round8-audit.md` | 本文件 |
