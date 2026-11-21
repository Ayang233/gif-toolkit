# 07 · Glass 组件使用指南

> 全部条目的签名、默认值、文件行号来自 MIUIX `3177a684`（`0.9.5` 开发线）。
> **升级上游后必须重核**（尤其"是否必须传 backdrop"和 `renderInRootScaffold` 默认值）。

---

## 1. 先纠正三个命名误解

| 你可能以为 | 实际 | 位置 |
|---|---|---|
| `GlassSurface` 是个 Composable | 是**工厂函数** `glassSurface(...)`，返回 `@Composable (Shape) -> Modifier` | `miuix-glass/.../GlassSurface.kt:35` |
| `GlassPanel` 是个 Composable | 是 **`Modifier.glassPanel(...)`** | `GlassPanel.kt:36` |
| `Glass.*` 图标是 `MiuiIcons` 扩展 | 是 `MiuixIcons.Glass.*`（另有 `MiuixIcons.Glass.Light.*` 细线档） | `miuix-core/.../icon/MiuixIcons.kt:12-18` |

还有两个"不在库里"的常见误用：

- `rememberBlurBackdrop()` —— 只在 `miuix/example/shared/.../utils/PageUtils.kt:294`。
- `rememberCombinedBackdrop()` —— 只在 `miuix/example/shared/.../component/liquid/CombinedBackdrop.kt:46`。

---

## 2. 依赖与门槛速查

| 组件族 | 需要模块 | 需要 backdrop | API 门槛 |
|---|---|---|---|
| 所有 `miuix-glass` 组件 | `miuix-glass`（自动带 `blur` / `material` / `ui`） | 见各条 | 编译 24；**真模糊 33** |
| `Modifier.textureBlur` / `progressiveTextureBlur` | `miuix-blur` | 必须 | 33 |
| `GlassDefaults` / `GlassStyle` | `miuix-material` | — | 24 |
| `MiuixIcons.Glass.*` | `miuix-glass-icons` | — | 24 |

低版本行为 📌：`miuix-glass` 的 minSdk 已是 24，但 `miuix-blur` 仍是 33；
`Modifier.glassPanel` 在 `backdrop == null` 或 `!isRuntimeShaderSupported()` 时走 `fallback`
（`GlassPanel.kt:46-58`）。所以 < 33 的设备**能编译、能显示描边/底色，但没有模糊**。

---

## 3. 组件逐项说明

> 列格式：作用 / 签名要点 / 是否要 backdrop / 局部还是全局 / 与普通组件的关系 / GIF 助手用法 / 坑。
> 签名只保留**决策相关**的参数，完整签名见 `17-source-reference-index.md` §3.3。

### 3.1 `GlassTopAppBar`

- **作用**：OS4 顶栏。支持大标题折叠、滚动渐进模糊带、返回键玻璃胶囊。
- **签名要点** 📌（两个重载，`GlassTopAppBar.kt:262` / `:340`）：
  `title`、`isContentScrolled`（第二个重载）、`expandable`、`progressiveBlur = false`、
  `backdrop`、`contentModifier`、`largeTitle`、`subtitle`、`scrollBehavior`、`bandBrush`、
  `bandOverhang`、`titleAlpha`、`backgroundAlpha`、`navigationIcon`、`actions`、`bottomContent`。
- **backdrop**：可选，默认 `null`。**不传就没有玻璃**（只有 band 或纯色）。
- **局部 / 全局**：页面框架型（局部 Glass）。
- **与普通组件关系**：非玻璃档用 `TopAppBar`（`miuix-ui/basic`），两者参数高度对齐，
  可以做成"同一个封装里二选一"（GIF 助手 `PageTopBar` 就是这么做的 🏠）。
- **GIF 助手用法** 🏠：`.../ui/GlassScope.kt:260-288`。关键点：
  - `progressiveBlur = !isSoftwareRenderer`（软件渲染走库的 band 渐变，不自绘）
  - `backdrop = backdrop ?: glass.contentBackdrop`
  - `contentModifier = Modifier.railStartInset()`（侧栏让位只挪前景）
  - 显式传 `bandBrush = GlassTopAppBarDefaults.bandBrush(surfaceColor)` 与
    `bandOverhang = GlassTopAppBarDefaults.BandOverhang`
- **坑**：
  - **`progressiveBlur` 与 `bandBrush` 是二选一的两条绘制路径**：`progressiveBlur && backdrop != null`
    走 `progressiveTextureBlur`（AGSL），否则走 `bandBrush` 渐变。AGSL 在个别 GPU 上**静默失败**
    （不抛异常、不产出内容），所以 band 参数必须备好。
  - 不要传 `style = GlassDefaults.style(isDark)`：库的默认 `GlassDefaults.Style` 不按明暗分档，
    明暗由材质内部的 `isDarkTheme()` 处理。自己分档会做出"第二套材质"。

### 3.2 `GlassNavigationBar`

- **作用**：底部悬浮胶囊导航。
- **签名要点** 📌（`GlassNavigationBar.kt:217`）：`items: List<GlassNavigationItem>`、
  `selectedIndex`、`onSelect`、`backdrop`、`selectedColor`、`unselectedColor`、`indicatorColor`、
  `visible`、`height`、`trailingAction`、`defaultWindowInsetsPadding`。
- **backdrop**：必须传（可空）。
- **局部 / 全局**：页面框架型；GIF 助手把它放一级页内。
- **与普通组件关系**：替代 `miuix-ui` 的 `NavigationBar`。关玻璃时它自己仍保留"浮起胶囊 + 阴影 + 指示器"，
  不会退化成通栏白条 📌（`backdrop = null` 的 fallback 路径）。
- **坑**：
  - **`visible = false` 不是"隐藏"**，而是"缩小 + 淡出 + 自身模糊一起收"，视觉上是"缩一下" 📌
    （`GlassNavigationBar.kt:200` 注释、`:256-266` 实现）。想"无动画移除"只能**不挂载**。
  - `onSelect` 在 `19b5b831` 之后**只在释放/键盘/无障碍激活时触发一次**，不再按下即触发。
  - 高度取 `GlassNavigationBarDefaults.Height`（54dp），不要抄成自己的常量。
  - 底部外边距公式没有暴露成参数，库内部按 `navigationBars` 与 `captionBar` 取 `maxOf` 再分段 📌
    （`GlassNavigationBar.kt:304-323`）。业务侧若需要算"内容要留多高"，只能镜像这个公式（见 [05](05-navigation-and-adaptive.md) §4.3）。

### 3.3 `GlassNavigationRail`

- **作用**：宽屏侧边导航；折叠态是**顶部居中胶囊（只显示文字）**。
- **签名要点** 📌（`GlassNavigationRail.kt:323`）：`items`、`selectedIndex`、`onSelect`、`backdrop`、
  `state: GlassNavigationRailState`、`secondaryItems`、`showToggle = true`、
  `expandedWidth`、`collapsedTop`、`selectedColor`、`scrollState`。
- **backdrop**：必须传（可空）。
- **局部 / 全局**：页面框架型；GIF 助手放在一级页外面（App 层）。
- **坑（最大的一个）**：
  - 根节点是 `BoxWithConstraints(fillMaxSize())`，自己决定面板位置（`Margin` 12dp + `ExpandedWidth` 250dp）。
    **不能放进 `Row` 用 `weight` 排**——它会吃掉整行宽度，内容被挤成 0 宽。
    正确结构：`Box { Box(fillMaxSize + padding(start=…) + layerBackdrop) { Content }; Rail }`。
  - `ExpandedWidth` / `Margin` 是公开默认值（`GlassNavigationRailDefaults`），内容让位应读它们，
    不要写 250.dp / 12.dp。
  - 展开/折叠的 `progress` 是逐帧动画值，见 [06](06-glass-architecture.md) R8。

### 3.4 `GlassTabRow` / `GlassSegmentedTabRow`

- **作用**：
  - `GlassTabRow`（`GlassTabRow.kt:242`）= **可横向滚动的页签**，不是分段控件。
  - `GlassSegmentedTabRow`（`GlassTabRow.kt:473`）= **一整条凹槽 + 一个滑块**，这才是分段控件，
    支持横向拖拽与指示器 morph（提交 `63d51cdf`）。
- **backdrop**：`GlassTabRow` 可空；`GlassSegmentedTabRow` **非空**（`backdrop: Backdrop`）。
- **与普通组件关系**：替代 `miuix-ui` 的 `TabRow` / `TabRowWithContour`。
  注意 `TabRowWithContour` 会额外画 5dp 轮廓 padding，看起来像"一排小按钮"。
- **选择的判据**：
  - 2~4 个离散选项 → 优先 `GlassSegmentedTabRow`（或设置页里的 `GlassDropdownPreference`）。
  - 页签多、要横滑 → `GlassTabRow`。
- **GIF 助手**：两者都**未使用**（全项目 grep 零命中）；设置页的 2~4 档选择一律走下拉。

### 3.5 `GlassIconButton`

- **作用**：浮在小玻璃胶囊里的图标按钮，OS4 顶栏返回键的标准形态。
- **签名要点** 📌（`GlassIconButton.kt:64`）：`onClick`、`inheritTopBarMaterial = true`、
  `backdrop = null`、`size`（默认取 `LocalGlassTopAppBarContext`）、`shape`、`style`、`content`。
- **backdrop**：可选。**在 `GlassTopAppBar` 子树内会自动继承**顶栏的 backdrop/material/size
  （通过 internal 的 `LocalGlassTopAppBarContext`）。
- **坑**：在页面里手写裸 `IconButton` 拿不到胶囊材质；必须用 `GlassIconButton`。
  官方 `component/BackNavigationIcon.kt` 的做法是：玻璃档 `GlassIconButton` + `MiuixIcons.Glass.ChevronBackward`，
  非玻璃档才退回 `IconButton` + extended 的 `Back`。

### 3.6 `GlassFloatingActionButton` / `GlassFloatingToolbar`

- **作用**：悬浮主操作按钮 / 悬浮工具条。
- **签名要点** 📌：FAB `GlassFloatingActionButton.kt:74`（`backdrop = null`、`size` 默认
  `GlassFloatingActionButtonDefaults.Size` = **56dp**，HEAD 当天刚从 60dp 改）；
  Toolbar `GlassFloatingToolbar.kt:75`（`backdrop = null`、`cornerRadius`、`outSidePadding`）。
- **backdrop**：可选。
- **GIF 助手**：未使用（它的主操作是页面底部通栏按钮，见 [08](08-custom-component-migration.md)）。
- **选型建议**：单页大量内容的"新建/添加"用 FAB；多动作（撤销、重做、筛选）用 FloatingToolbar。
  两者都不适合替代"设置页里的普通按钮"。

### 3.7 `GlassDialog`

- **作用**：OS4 玻璃对话框。
- **签名要点** 📌（`GlassDialog.kt:48`）：`onDismissRequest`、`show`、**`backdrop`**、
  `renderInRootScaffold = true`、`enableWindowDim = true`、`title`、`summary`、`cornerRadius`、
  `maxWidth`、`outsideMargin`、`insideMargin`、`onDismissFinished`、`content`。
- **backdrop**：必须传（可空）。
- **局部 / 全局**：**全局弹层**。宿主是最外层 `Scaffold`。
- **坑**：
  - 页面级 Dialog 必须写在页面的 `Scaffold` 内容里（能拿到宿主），但**不能**在页面录制层内部。
    GIF 助手为此加了 `Os4ListPage(pageOverlay = …)` 槽位（Scaffold 内、录制层外）🏠。
  - 放在页面 `Scaffold` 外面 → 静默不渲染（点了没反应、也不报错）。
  - 采样源要按 [06](06-glass-architecture.md) §6.2 选（跨分页容器就差一页宽）。

### 3.8 `GlassBottomSheet`

- **作用**：OS4 玻璃底部面板。
- **签名要点** 📌（`GlassBottomSheet.kt:50`）：`onDismissRequest`、`show`、**`backdrop`**、
  `renderInRootScaffold = true`、`allowDismiss`、`enableNestedScroll`、`title`、
  `startAction`、`endAction`、`insideMargin`（`DpSize(24, 0)`）、`dragHandleColor`、`onDismissFinished`。
- **backdrop**：必须传（可空）。
- **局部 / 全局**：全局弹层。
- **坑**：
  - 行内边距会**双计**：面板自带 24dp 左右内边距，`BasicComponent` 又有 16dp。行内应改
    `PaddingValues(horizontal = 0, vertical = 16)`。
  - 内容**自己负责底部导航条留白**（库 KDoc 明确要求）：末尾加
    `Spacer(Modifier.navigationBarsPadding().height(8.dp))`。
  - 面板内容不能塞进页面的 `Scaffold` 内容之外。

### 3.9 `GlassPopup` / `GlassSecondaryPopup` / `GlassTransformPopup` / `GlassDropdownPopup`

| 组件 | 作用 | 签名要点 | 适用场景 |
|---|---|---|---|
| `GlassPopup` | 锚点旁的玻璃浮层 | `BoxScope` 扩展；`anchorBounds: Rect`、`sizing`、`visuals`、`secondary = false` | 自定义菜单、信息浮层 |
| `GlassSecondaryPopup` | 二级浮层（浮层上的浮层） | 同上 + `materialAnchor: GlassPopupAnchor?` | 浮层里再开一层 |
| `GlassTransformPopup` | **从锚点形变展开**的浮层 | `anchor: GlassPopupAnchor`、`anchorContent`、`simplified`、`stacked`、`anchorAlpha` | 需要"从按钮长出来"的动效（官方 example 主用这个） |
| `GlassDropdownPopup` | 下拉浮层（锚点 + 列表） | `anchorBounds`、`anchor`、`CornerRadius` 取 `GlassDropdownDefaults` | 自绘下拉 |
| `GlassIconDropdownMenu` | 图标菜单（带 entries） | `anchor`、`entries: List<DropdownEntry>`、`collapseOnSelection` | 图标网格/菜单 |

全部 `backdrop` 必须传（可空），全部**没有** `renderInRootScaffold`（它们靠调用点的
`BoxScope` 与锚点定位，由调用方决定画在哪）。

**GIF 助手**：只用 `GlassDropdownPreference`（见 3.10），没有直接用这些 Popup；
它们被 `GlassDropdownPreference` 内部消费。

**坑**：`GlassPopup` 系列都有 `anchorBounds` / `GlassPopupAnchor` 的坐标要求，
请让锚点与浮层在**同一个 `Box` 作用域**内；跨作用域要自己算 `Rect`。

### 3.10 `GlassDropdownPreference`

- **作用**：设置页里的"玻璃浮层下拉"，替代 `WindowDropdownPreference`（后者弹白底卡片，一眼不是 OS4）。
- **签名要点** 📌（`GlassDropdownPreference.kt:64`）：`onSelectedIndexChange`、`items`、
  `selectedIndex`、`title`、**`backdrop`**、`renderInRootScaffold = true`、`showValue`、
  `summary`、`startAction`、`bottomAction`、`insideMargin`、`visuals`、`onExpandedChange`。
- **backdrop**：必须传（可空）。
- **局部 / 全局**：**全局弹层**（默认渲染在根 Scaffold）。
- **GIF 助手用法** 🏠：`DropdownRow`（`.../ui/Widgets.kt:301-323`）：
  `backdrop = (LocalRootBackdrop.current ?: LocalPageBackdrop.current).takeIf { glassEnabled }`。
- **坑**：
  - 不要传 `visuals = GlassPopupDefaults.visuals(style = GlassDefaults.style(isDark))`：
    默认就是 `GlassDefaults.Style`，自己按明暗分档会让深色下弹层比顶栏厚一档。
  - 因为它**默认渲染在根 Scaffold**，采样源必须是最外层坐标空间那一层（[06](06-glass-architecture.md) §6.2）。

### 3.11 `GlassDialogButton`

- **作用**：对话框内的按钮（不是玻璃，只是配色/尺寸对齐）。
- **签名要点** 📌（`GlassDialogButton.kt:67`）：`onClick`、`enabled`、
  `role: GlassDialogButtonRole`（`Normal` / `Primary` / `Destructive`）、`colors`、`insideMargin`。
- **backdrop**：无。
- **用法**：破坏性操作（删除）必须用 `role = Destructive`，颜色由库给。

### 3.12 `GlassTextField`

- **作用**：玻璃配色的输入框。
- **签名要点** 📌（`GlassTextField.kt:43` / `:96` / `:151` 三个重载）：
  分别接 `TextFieldState`、`String`、`TextFieldValue`；参数与 `miuix-ui` 的 `TextField` 对齐。
- **backdrop**：无（它不是玻璃材质，只是配色）。
- **适用**：需要"与玻璃表面同一视觉"的输入框。普通表单页用 `TextField` + `cardColor()` 更稳。
- **GIF 助手**：未使用（工具页字幕输入用普通 `TextField`）。

### 3.13 底层入口：`glassSurface` / `Modifier.glassPanel` / `Modifier.glass`

| 入口 | 用途 | 什么时候用 |
|---|---|---|
| `glassSurface(backdrop, visuals, cornerRadius)` 📌`GlassSurface.kt:35` | 返回 `(Shape) -> Modifier`，给自绘组件加玻璃表面 | 官方组件覆盖不到的自定义容器 |
| `Modifier.glassPanel(backdrop, shape, style, alpha, material, stroke, shadow, shading, fallback)` 📌`GlassPanel.kt:36` | 玻璃"面板"（含 fallback） | 需要"无 backdrop 时也能显示某种底色" |
| `Modifier.glass(backdrop, shape, style, alpha, tint, material, noiseCoefficient, stroke, contentBlendMode, enabled, shading)` 📌`Glass.kt:60` | 玻璃绘制（非空 backdrop） | 完全自定义的玻璃表面 |
| `Modifier.textureBlur(backdrop, shape, blurRadius, noiseCoefficient, colors, contentBlendMode, enabled)` 📌`TextureEffect.kt:27` | 采样 + 模糊 + 混色（**不含折射/边缘高光**） | 玻璃卡、图标、文字 |
| `Modifier.progressiveTextureBlur(...)` 📌`TextureEffect.kt:176` | 渐进模糊（顶部到底部衰减） | 顶栏背景带 |
| `Modifier.drawBackdrop(backdrop, pipeline, contentBlendMode, enabled)` 📌`DrawBackdropModifier.kt:161` | 最底层：自定义效果链 | 需要 `BackdropEffectScope` 里多个效果组合 |

**顺序原则**：能用官方组件就不要用 `Modifier.glass`；能用 `textureBlur` 就不要用 `drawBackdrop`。

---

## 4. 组件 × 场景矩阵

| 场景 | 推荐 | 不推荐 | 原因 |
|---|---|---|---|
| 页面顶栏 | `GlassTopAppBar` | `TopAppBar` + 自己模糊 | 大标题折叠/band 的逻辑在库里 |
| 底部主导航 | `GlassNavigationBar` | `NavigationBar` | OS4 的悬浮胶囊形态 |
| 宽屏侧边导航 | `GlassNavigationRail` | 自绘抽屉 | 折叠/展开动画与阈值 |
| 设置项开关 | `SwitchPreference`（普通组件） | `Glass*` 包装 | 设置页不需要玻璃行 |
| 设置项下拉 | `GlassDropdownPreference` | `WindowDropdownPreference` | 后者白底卡片，不是 OS4 |
| 主操作按钮 | `Button` / 自绘 ProgressButton | `GlassFloatingActionButton` | FAB 是"悬浮单动作"，不是页面主 CTA |
| 危险确认 | `GlassDialog` + `GlassDialogButton(Destructive)` | 系统 `AlertDialog` | 视觉割裂 |
| 行内菜单 | `GlassBottomSheet` | 系统 PopupMenu | 手势与遮罩一致性 |
| 普通卡片 | `Card` + `cardColor()` | 全都做玻璃卡 | 文字对比度更稳、性能更好 |
| 关于页卡片 | `Card` + `textureBlur`（玻璃卡） | 自绘渐变 | 见 [10](10-about-and-special-effects.md) |

---

## 5. 设备兼容性

| 环境 | 表现 | 结论 |
|---|---|---|
| 真机 API 33+，硬件 GPU | 全部组件正常（🔴 本库无真机证据，需项目自验） | 目标环境 |
| 真机 API 33+，弱 GPU | 可能出现"内容不糊开、只被压暗" 🔴 | 必须留用户开关 |
| 模拟器 `-gpu host`（gfxstream） | 多数情况正常 | 可用于结构验证 |
| 模拟器 `-gpu swiftshader_indirect` | AGSL **能编译但不产出正确像素**；`textureBlur` + `DstIn` 会让内容整块消失 🔴 | 只能验证结构，不能验证观感 |
| API 24~32 | `glassPanel` 走 fallback：有描边/底色，**无模糊** | 只做 OS4 时可接受 |
| RTL | 组件本身支持；箭头/让位方向要自己处理 | 见 [05](05-navigation-and-adaptive.md) §7 |

---

## 6. 每个 Glass 组件的接入自检（5 问）

1. 这个组件的 `backdrop` 传了吗？（不传 = 没玻璃）
2. 采样源在**这个组件所在的整个区域**都有像素吗？
3. 这个组件是采样源的**兄弟**吗？
4. `backdrop = null` 时它还能用吗？（入口会不会消失）
5. 它在**软件渲染**下会退化到什么程度？这个退化可接受吗？

---

## 7. GIF 助手的实际使用清单（对照用）

| 组件 | 用了吗 | 位置 |
|---|---|---|
| `GlassTopAppBar` | ✅ | `ui/GlassScope.kt:260` |
| `GlassIconButton` | ✅ | `ui/GlassScope.kt:226` |
| `GlassNavigationBar` | ✅ | `App.kt:321` |
| `GlassNavigationRail` | ✅ | `App.kt:399` |
| `GlassDropdownPreference` | ✅ | `ui/Widgets.kt:312` |
| `GlassDialog` + `GlassDialogButton` | ✅ | `screens/LibraryScreen.kt:114` |
| `GlassBottomSheet` | ✅ | `screens/LibraryScreen.kt:244` |
| `Modifier.textureBlur` | ✅ | `ui/Widgets.kt:202`（玻璃卡）、`screens/AboutScreen.kt:244`/`:272`（图标/标题） |
| `glassSurface` / `Modifier.glassPanel` / `Modifier.glass` | ❌ | 没有自绘玻璃表面 |
| `GlassTabRow` / `GlassSegmentedTabRow` | ❌ | 离散选项走下拉 |
| `GlassFloatingActionButton` / `GlassFloatingToolbar` | ❌ | 主操作是底部通栏按钮 |
| `GlassPopup` 系列 | ❌ | 由 `GlassDropdownPreference` 内部使用 |
| `GlassTextField` | ❌ | 用普通 `TextField` |
| `GlassIconDropdownMenu` | ❌ | — |

---

## 8. 判断依据：什么时候**不该**用 Glass

这是最容易做错的地方。**不是所有 UI 都该改成 Glass。**

### 8.1 用普通 MIUIX 组件

- 设置页的每一行（`SwitchPreference` / `ArrowPreference` / `SliderPreference`）：
  行是信息密度单位，玻璃会让文字对比度不稳、性能变差。
- 表单输入（`TextField`）：焦点/选区/光标在玻璃上对比度不足。
- 长列表的每一项：每项一个玻璃表面 = 每项一次采样。
- 空状态、说明文字：没有"浮在背景上"的语义。

### 8.2 用 Glass 组件

- **框架型**：顶栏、底栏、侧栏、Tab 条 —— 它们**浮在内容上**，玻璃的语义正确。
- **浮层型**：Dialog、BottomSheet、Dropdown、Popup —— 天然"浮在页面上"。
- **强调型**：About 页的大图标/标题/卡片（背景是动态渐变时）、少量主入口卡。
- 判据一句话：**"这个元素是否在视觉上位于其它内容之上，并且需要让人看见背后的内容？"**
  两个"是"才做玻璃。

### 8.3 保留业务自定义组件

- 有独特交互的（进度填充按钮、时间轴、画布/预览区）。
- 有独特数据可视化的（图表、波形）。
- 官方组件语义不匹配的（例如多选 + 自定义排序的列表）。
- 条件：**必须继承 MIUIX Theme**（颜色/字号/圆角都从 `MiuixTheme` 取），
  且**不能破坏 Glass 层级**（不要自建录制层把消费者包进去）。详见 [08](08-custom-component-migration.md)。

### 8.4 用系统原生控件

- 权限/文件选择器（SAF、`ActivityResultContracts`）。
- 系统分享面板、系统对话框（涉及安全/系统语义）。
- 通知、悬浮窗、输入法。
- 判据：**系统负责的交互不要重画**。

### 8.5 决策流程图

```
这个元素浮在内容之上吗？
├─ 否 → 普通 MIUIX 组件（或系统控件）
└─ 是 → 它会遮挡大面积内容、需要看见背后吗？
        ├─ 否 → 普通 MIUIX 组件 + 阴影/描边就够
        └─ 是 → 是系统负责的交互吗？
                ├─ 是 → 系统原生
                └─ 否 → 官方有对应 Glass 组件吗？
                        ├─ 有 → 用官方组件（本篇 §3）
                        └─ 无 → 自绘 + Modifier.glassPanel / textureBlur（读 08）
```
