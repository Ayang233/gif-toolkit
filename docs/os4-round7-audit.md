# 第七轮：底栏行为复核、作品菜单玻璃背景、全项目复查

本轮四件事：核实官方底栏在「一级 → 二级」时的真实行为、按核实结果改 GIF 助手的导航结构、
查清作品菜单（GlassBottomSheet）采样落空的原因并修掉、顺带做一次全项目复查。

所有结论都对应实际源码、运行截图或录屏。模拟器无法判定的项单列在最后。

---

## 0. 基线与取证条件

| 项 | 值 |
| --- | --- |
| MIUIX 本地基线 | `d45ca181`（`library: Lower BlurDefaults.MaxBlurRadius to 100f`） |
| MIUIX 上游 `origin/main` | **`189ef10a`**（比基线多 3 个提交，见 §0.1） |
| GIF 助手本轮起点 | `969a88a`（工作区干净，无未提交修改） |
| 官方示例 APK | `Miuix-v1.0.9(1307)-debug.apk`，同一基线现场编译，已装机 |
| GIF 助手 APK | 同一基线上 `./gradlew :assembleDebug` |
| 设备 | AVD `android16`，1080×2400 / 420dpi，`-gpu host`；横屏可用宽约 **914dp** |
| 额外形态 | `wm density 144` → 宽 **1200dp**，用于验证展开态 Rail |

产物目录：`_archive/round7/`（录屏、逐帧、前后截图）。

### 0.1 上游三个新提交，本轮不同步

```
189ef10a example: Lift GlassPage popups into TopLayer
76504ab0 library: Refine progressive blur sharp overlay and shaders
7188729e example: Always use collapsed top bar on About page
```

`git diff d45ca181..origin/main --stat` 显示只有两处属于库：`miuix-blur` 的
`DrawBackdropModifier` / `BlurEffect` / `Shaders`（渐进模糊的锐化叠加与降采样微调），
`miuix-glass` / `miuix-ui` / `miuix-nav` / `miuix-material` **零改动**，公共 API 没有变化。

因此本轮不同步：同步只会改变模糊的渲染细节，却会让今天的整机验收（导航结构、采样坐标）
全部失效，需要重跑一遍。下一轮升级时再一起做，届时重点看 `GlassPage` 改成 TopLayer 之后
官方示例的弹层宿主结构。

---

## 1. 官方底栏在「一级 → 二级」时的真实行为

### 1.1 源码

| 排查项 | 结论 | 依据 |
| --- | --- | --- |
| 底栏挂在哪个页面 | **一级页内部**，不是 App 级 | `AppContent.kt:395-400`：`entry<Route.Main> { Home(...) }`；`Home`（`:509-537`）→ 窄屏走 `CompactScreenLayout`（`:581-627`），它的 `Scaffold(bottomBar = NavigationBar)` 里就是玻璃底栏 |
| 底栏是否参与页面切换 | 参与，但**没有自己的进退场动画** | 底栏是 Main 页的子节点，随整页一起被 `NavTransitions.MiuixDefault` 处理：被覆盖页 `translationX = -0.25w·d`、`alpha = 1-0.1d`（`NavTransitions.kt:43-57`） |
| 二级页进入时底栏是否还在 | 在，但被**新页盖住** | 新页从右侧滑入并绘制在 Main 页之上；Main 页含底栏，所以底栏是被盖住而不是消失 |
| 返回时 | 底栏被**揭开** | 二级页右滑退出，Main 页（含底栏）在下面露出来 |
| 是否用 `visible` 控制 | 完全不用 | 二级页里底栏根本不在组合树中（Main 页被 NavDisplay 移出） |
| 横屏是否一致 | 宽屏走 `WideScreenContent`，没有底栏，只有 Rail；官方 Rail **在二级页也保留**（上轮实测），我们**故意偏离**（见 §2.3） |

### 1.2 APK 逐帧（`_archive/round7/nav1.mp4`，144 帧 / 7.1s，约 20fps）

场景：设置页 → About（一级进二级）。

| 帧 | 观察 |
| --- | --- |
| 1–2 | 一级页静止，底栏胶囊完整，胶囊左边缘 x=58px |
| 3 | 过渡中：新页从右侧滑入，其左边缘在 x≈1040；**底栏仍在原位置、完整、没有缩小也没有变淡** |
| 4 | 新页已覆盖全屏，底栏**消失**（被盖住），旧页只剩左侧一条被压暗的残边 |
| 5+ | 稳定在二级页，没有底栏 |

关键点：帧 3 与帧 4 之间底栏没有任何独立的收缩/淡出过程 —— 它不是"自己消失"，
而是"被新页面盖住"。这与第六轮记录的「底栏随页面一起滑走」方向一致，但更准确的说法是
**被覆盖页的视差 + 被新页盖住**；与你观察到的"底栏固定不动"也不矛盾：
底栏确实不自己动，它只是停在原位等新页盖上来（回退时再被揭开）。

`_archive/round7/crop_b3.png` / `crop_b4.png` 是这两帧的底部裁切图。

---

## 2. GIF 助手导航结构：改前 / 改后

### 2.1 改前

```
GifApp
└── Box(background)                     ← 没有 Scaffold
    ├── Box(layerBackdrop)              ← 整屏录制层
    │   └── NavDisplay
    │       ├── entry<Route.Root>  → HorizontalPager（三个 tab）
    │       └── entry<Tool/Theme/...>
    ├── GlassNavigationRail   if (wide && stack.size <= 1)
    └── GlassNavigationBar    if (!wide && stack.size <= 1)   ← 画在 NavDisplay 外面
```

问题（不是"看起来不一样"，是结构性的）：

1. 进二级页时 `stack.size` 一变，底栏**立即卸载**。此时新页还没盖上来（过渡约 250ms），
   所以第一帧就能看到底栏"闪没"。
2. 返回时 `stack.size` 变回 1，底栏**立即挂载**，而它画在 NavDisplay **之上** ——
   退出中的二级页还在屏幕上，底栏却已经浮在它上面。
3. `modalOverlayOpen` 就为第 2 条服务（弹层要压在底栏上），引入了一个跨页面的运行时状态，
   面板异常退出时有机会残留。

### 2.2 改后（对齐官方 `CompactScreenLayout`）

```
GifApp
└── Scaffold(containerColor = Transparent, contentWindowInsets = 0)   ← 新增：弹层宿主
    └── Box(background)
        └── NavDisplay
            ├── entry<Route.Root>
            │   └── Box
            │       ├── Box(layerBackdrop(rootBackdrop))            ← 一级页录制层
            │       │   └── CompositionLocalProvider(LocalRootBackdrop)
            │       │       └── HorizontalPager
            │       └── GlassNavigationBar  if (!wide)              ← 兄弟节点，属于一级页
            └── entry<Tool/Theme/...>
        └── GlassNavigationRail  if (wide && stack.size <= 1)        ← 在一级页之外
```

| 项目 | 改前 | 改后 |
| --- | --- | --- |
| 底栏归属 | App 级 | 一级页内部（与官方一致） |
| 底栏在场判据 | `!wide && stack.size <= 1 && !modalOverlayOpen` | **不判断**：二级页的组合里根本没有它 |
| 进二级页 | 立即消失 | 随一级页被新页盖住（录屏 `nav_slow.mp4` 逐帧可见：底栏一直跟着一级页平移，被新页压住） |
| 返回 | 立即浮现在退出中的二级页之上 | 随一级页被揭开 |
| 弹层层级 | 页面 Scaffold 绘制 → 被底栏压住 | 最外层 Scaffold 绘制 → 永远在底栏之上 |
| `modalOverlayOpen` | 需要 | **删除**（连同它的残留风险） |
| Rail | 同一判据 | 仍由 `stack.size <= 1` 控制（§2.3 的故意偏离） |

验收录屏：`_archive/round7/nav_slow.mp4`（`animator_duration_scale=10` 放慢，258 帧），
`pa16.png` 是进入过渡中（底栏原地被覆盖），`pp120.png` 是返回过渡中（底栏被揭开）。

### 2.3 与官方的两处故意差异

1. **二级页不显示 Rail**。官方宽屏进二级页仍保留左侧 Rail；按第六轮起的产品约定，我们任何方向都不显示一级导航。
   代价是宽屏下二级页顶部/左侧更空；收益是返回路径只有一条（不会出现"二级页里点导航跳到另一个 tab"）。
2. **折叠态顶栏标题淡出**用的是官方机制（`LocalTopAppBarTitleAlpha` = 折叠进度），
   但我们没有搜索栏那一套 `searchTop` 让位，因为页面里没有搜索栏。

---

## 3. 作品菜单玻璃背景：根因与修复

### 3.1 结论先行

面板不是"材质太厚看不出来"，而是**采样区间整块落在录制层之外**，
玻璃只拿到空背景，于是只剩材质与描边 —— 表现就是一块均匀浅色面板。

### 3.2 取证（临时探针，已删除）

在录制层与弹层面板各挂一个 `onGloballyPositioned`，打印窗口坐标与
`layer.localPositionOf(panel)`：

```
修复前： panelWin=(0,1777)  recWin=(1080,0)  offset=(-1080,1777)  layer=1080x2400
修复后： panelWin=(0,1777)  rootWin=(0,0)    offset=(0,1777)      layer=1080x2400
```

`panelSize = 1080×623`。修复前 offset.x = **-1080**，即采样矩形从 x=-1080 开始、
宽度 1080 —— 整块在录制层（0..1080）之外，一个像素都采不到。

为什么会差 1080：页面录制层在 `HorizontalPager` 的页面里，
`positionInWindow()` 报的是**槽位坐标**（第 2 个 tab 的槽位 x = 1080），
而弹层由 Scaffold 的弹层宿主绘制，报的是真实坐标（x = 0）。两者相减正好一页宽。

### 3.3 像素侧的证据

同一块面板区域（x 300..800，y 1800..2400）的纵向亮度剖面：

| 状态 | 剖面 | 判定 |
| --- | --- | --- |
| 修复前 | 全程 **238.4**（min = max = 238） | 采样为空，只剩材质 |
| 修复后 | 234.0 → 239.0，且列表滚动一屏后整体变化约 1 级 | 采到了真实背景 |

对比：官方示例在浅色页面上打开同一个 `GlassBottomSheet`，同一段剖面有 12 级起伏
（`_archive/round7/off_sheet_nopaper.png`，页面上有蓝色元素）。
所以"浅色列表上看着均匀"是材质（`DialogGlassLight` 两层 0x99FFFFFF 的 SoftLight / HardLight）
+ 低对比内容的必然结果，不是采样错误 —— 这一条在本轮前后都能复现，官方同样如此。
换成彩色背景（`off_sheet_wallpaper.png`，官方示例开壁纸）模糊立刻可见。

### 3.4 修复

| 改动 | 位置 | 说明 |
| --- | --- | --- |
| 新增最外层 `Scaffold` | `App.kt` | `GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` 的 `renderInRootScaffold` 默认 true，会找**最外层** Scaffold 的宿主。没有这层壳时弹层退回到页面自己的 Scaffold 并被页面录制层包住（既有坐标问题，也有采样到自己的风险）。官方 `GlassPageHost`（`GlassPage.kt:652`）就是这层壳 |
| 录制层下沉到一级页 | `App.kt` | 录 Pager 外面、底栏外面的内容，窗口坐标 x 从 0 开始 |
| 新增 `LocalRootBackdrop` | `ui/GlassScope.kt` | 弹层的采样源；二级页没有 Pager，回落到页面自己的录制层 |
| `Os4ListPage.pageOverlay` 取 `LocalRootBackdrop ?: 页面录制层` | `ui/PageChrome.kt` | 一处改动覆盖所有页面级弹层 |
| 删除 `modalOverlayOpen` | `AppState.kt` / `LibraryScreen.kt` | 弹层画在最外层，不再需要"让出一层导航" |

安全约束仍然成立：弹层由最外层 Scaffold 绘制，**不在**任何录制层里，
所以不会出现采样到自己（AGENTS.md 第 1 条，历史上真崩过）。

---

## 4. 全项目 Glass 位置逐项核对

| # | 位置 | 用的组件 | 采样源 | 录制层级 | 模拟器表现 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 底部导航胶囊 | `GlassNavigationBar` | 一级页录制层（Pager 外） | 兄弟节点 | 正常，滚动内容在胶囊下会糊 | 通过 |
| 2 | 折叠态 Rail 胶囊 | `GlassNavigationRail`（`progress=0`） | 同上 | 同级，一级页之外 | 正常 | 通过 |
| 3 | 展开态 Rail 面板 | 同上（1200dp） | 同上 | 同上 | 正常，内容按进度让位 | 通过 |
| 4 | 页面顶栏 | `GlassTopAppBar` + progressiveBlur | 页面录制层 | 兄弟节点 | 顶栏下方文字确实被糊掉（`top_zoom.png`） | 通过 |
| 5 | 顶栏 band / progressive | `GlassTopAppBarDefaults.bandBrush` | 同上 | 同上 | 未滚动态几乎不可见，滚动时逐级加深 | 通过 |
| 6 | 页面卡片 | `Card` + `cardColor()` | 不采样 | — | 卡片本来就不是玻璃（官方 `Card` 也不是） | 无需玻璃 |
| 7 | 页面内下拉菜单 | `GlassDropdownPreference` | 页面录制层 | 卡片外的弹层宿主 | 弹层不透明感强，浅色页上几乎看不出模糊（见下方说明） | 结构正确，观感待真机 |
| 8 | Dialog | `GlassDialog`（新增删除确认） | `LocalRootBackdrop` | 最外层 Scaffold | 正常显示、遮罩正常 | 通过 |
| 9 | 二级页玻璃控件 | `GlassIconButton`（顶栏返回） | 页面录制层 | 兄弟节点 | 正常 | 通过 |
| 10 | 作品操作菜单 | `GlassBottomSheet` | `LocalRootBackdrop` | 最外层 Scaffold | 采样修复（§3） | 通过 |
| 11 | 其它自定义玻璃 | 无 | — | — | — | — |

明暗主题、玻璃开关、横竖屏切换都在两种主题下各跑了一遍：
关掉玻璃时底栏仍是一个浮起胶囊（库的 fallback），顶栏退回普通 `TopAppBar`，
弹层退回不透明面板，无闪退（`glass_off_root.png`、`sheet_glass_off2.png`）。

第 7 项的说明：下拉弹层在二级页采样页面自己的录制层，坐标是一致的（二级页没有 Pager，
`positionInWindow` 就是真实坐标）。浅色页 + 60dp 模糊 + 白色材质层之后，
弹层内部只测到 3 级起伏，肉眼基本是均匀浅色 —— 这与官方同一组件在浅色页面上的表现一致，
不是采样落空（落空会是 0 级起伏，如修复前的作品面板）。

---

## 5. 复查中发现并修掉的其它问题

| 问题 | 现象 | 原因 | 处理 |
| --- | --- | --- | --- |
| 展开态 Rail 下内容被压成竖带 | 1200dp 宽屏时卡片右侧空出 250dp | `pageContentPadding` 把 `LocalRailInset` 同时加在 start 与 end | 只让 start 一侧（`PaddingInfo(start = horizontal + inset, end = horizontal)`） |
| 折叠态胶囊压住顶栏标题 | 横屏一级页标题与顶部胶囊重叠 | 标题没有随折叠进度淡出 | 用官方机制 `LocalTopAppBarTitleAlpha`：标题透明度 = `railState.progress`，绘制期读取不重组 |
| 横屏工具页预览区像一块空白 | 预览占位图标与提示语被底部按钮压住 | 预览高 = 宽 ÷ 16:9 ≈ 620dp，远超 411dp 的横屏窗口 | 预览高度改成"窗口高度的比例"上限（≥500dp 取 42%，矮窗口取 32%），宽度仍按实际可用宽度算 |
| 删除作品无二次确认 | 点错就永久删除 | 面板的删除项直接 `file.delete()` | 加 `GlassDialog` 确认框，破坏性按钮用 `GlassDialogButtonRole.Destructive`（红色） |

---

## 6. 验收结果

### 已验证（模拟器）

| 项 | 结果 |
| --- | --- |
| 官方 APK 一级 → 二级行为 | 录屏 + 逐帧确认：底栏无独立退场动画，被新页盖住（§1.2） |
| GIF 助手一级 → 二级 / 返回 | 放慢动画逐帧确认：底栏随一级页被覆盖 / 被揭开（`nav_slow.mp4`） |
| 竖屏一级页底栏 | 正常 |
| 横屏折叠胶囊 | 正常，内容满宽，标题让位 |
| 1200dp 展开 Rail | 正常，内容只让 start 一侧 |
| 二级页任何方向无一级导航 | 竖屏、横屏均确认 |
| 旋转（一级 ↔ 横屏、二级 ↔ 横屏） | 导航不闪现，tab 不丢（旋转前后都停在「作品」） |
| 作品面板 | 打开正常；保存到相册（MediaProvider 有插入记录）；分享（系统分享面板出现）；删除（带确认，文件消失、计数 17→16） |
| 系统返回关闭面板 | 返回关闭面板而不是退出页面 |
| 面板关闭后导航 | 底栏一直在（不再有"隐藏/恢复"） |
| 导入 | 系统照片选择器选入 GIF，`已选择 1 个文件` + 预览出图 |
| 生成 | 两次生成各产出一个 `compress_*.gif`（31KB），并出现在「我的作品」顶部 |
| Glass 明/暗、开关、横竖屏 | 见 §4 |

### 未验证 / 需真机

| 项 | 原因 |
| --- | --- |
| 玻璃厚度、高光、边缘折射、抗锯齿 | 模拟器 `-gpu host` 与真机 GPU 差异大 |
| 个别 GPU 上 AGSL 静默失败后的降级观感 | 需要目标机型 |
| 预测性返回的**手势动画**（跟手位移） | 本轮只用返回键验证了"关闭面板/返回一级"；手势跟手需要在真机上连续拖动 |
| 深层页（工具 → 结果）在横屏下的滚动体验 | 只验证了布局不重叠，未做长列表滚动压测 |
| 连续快速进出二级页 20 次以上 | 只做了 3 次往返 |
| 进程恢复后保留的状态 | 本轮确认了返回栈会被恢复（重启后回到二级页），但没有做完整的进程死亡恢复验收 |

---

## 7. 改动文件

| 文件 | 改动 |
| --- | --- |
| `App.kt` | 新增最外层 `Scaffold`；录制层下沉到一级页；底栏移入 `entry<Route.Root>`；Rail 采样新录制层；接 `LocalTopBarTitleAlpha`；删掉 App 级底栏块 |
| `ui/GlassScope.kt` | 新增 `LocalRootBackdrop`（弹层采样源）与其文档 |
| `ui/PageChrome.kt` | `pageOverlay` 改用 `LocalRootBackdrop`；`pageContentPadding` 只在 start 侧让位 |
| `ui/Adaptive.kt` | 新增 `LocalTopBarTitleAlpha` |
| `ui/Widgets.kt` | `PreviewStage` 高度按窗口高度封顶（矮窗口更小） |
| `screens/LibraryScreen.kt` | 删掉 `modalOverlayOpen` 联动；删除改为带确认的 `GlassDialog` |
| `AppState.kt` | 删除 `modalOverlayOpen` |
| `AGENTS.md` | 第 1/11/12/14 条按新结构重写 |
