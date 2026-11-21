# AGENTS.md

给接手这个仓库的人（或 AI 助手）看的上下文。README 讲「是什么、怎么构建」，这里讲「改的时候要注意什么」。

## 一句话

单模块 Kotlin Multiplatform 工程，**全部实现放在 `androidMain`**，`commonMain` 只用来声明依赖；
UI 全部基于 [Miuix](https://github.com/compose-miuix-ui/miuix)，**不引入 Material 组件**。

## 目录地图

```
src/androidMain/kotlin/com/ayang/giftoolkit/
├── App.kt                主题装配、NavDisplay 路由、一级页（Pager + 玻璃底栏）/宽屏侧栏、最外层弹层宿主
├── Route.kt              路由定义（Root / Tool / About / Licenses / License）
├── AppState.kt           全局状态 + 每个工具一份 ToolForm（表单状态）
├── data/SettingsStore.kt 外观设置持久层（SharedPreferences）
├── ui/
│   ├── ToolCatalog.kt    十个工具的元数据（id / 标题 / 说明 / 分组 / 图标 / 按钮文案）
│   ├── Widgets.kt        分组卡片、玻璃卡片 GlassCard、行、下拉、预览舞台、主操作条、pageColor/cardColor、图标尺寸
│   ├── PageChrome.kt     页面骨架 Os4ListPage（整屏背景 / 固定大标题区 / 顶栏采样源）+ pageScrollModifiers + pageContentPadding
│   ├── Adaptive.kt       宽屏判定（阈值与官方 AdaptiveUtils 一致）
│   ├── GlassScope.kt     页面玻璃作用域 + PageTopBar（玻璃/普通两态）
│   ├── GlassSupport.kt   玻璃开关与能力检测的**唯一决策点**
│   ├── AppInfo.kt        版本号串（关于页与设置页副标题共用）
│   ├── bg/                关于页的 OS3 动态渐变背景（移植自 miuix 示例，Apache-2.0）
│   │                      + BgEffectBackground 的 bgModifier：给关于页的 textureBlur 挂录制层
│   ├── ProgressButton.kt 自带进度填充的按钮（移植自 miuix 生态，Apache-2.0；容器形状走官方 Button 的 squircleSurface + clickable）
│   └── LicenseCatalog.kt 许可清单：本项目 / 第三方组件 / NOTICE
├── screens/              主页 / 工具页 / 作品页 / 设置 / 主题 / 关于 / 开源许可
├── task/TaskRunner.kt    任务分发 + 进度 + 预览取帧
└── core/                 GifCodec / GifTasks / MediaTools / Stabilizer / ColorKey / MotionPhoto
```

## 常用命令

```bash
./scripts/setup-miuix.sh                 # 首次：准备 Miuix 源码并打补丁
./gradlew :assembleDebug                 # 调试包
./gradlew :assembleRelease               # 发布包（R8，约 3MB）
adb install -r build/outputs/apk/debug/GifHelper-debug.apk
adb shell am start -n com.ayang.giftoolkit/.MainActivity
```

环境：JDK 21、Android SDK Platform 37、Build-Tools 37.0.0。
没有 `local.properties` 时用 `ANDROID_HOME` 环境变量也能构建（已验证）。

## 硬约束（违反会出事，不是风格问题）

1. **玻璃容器必须是 `layerBackdrop` 录制层的兄弟节点，不能是子节点。**
   子是采样到自己 → `libhwui` 的 `RenderNode::prepareTreeImpl` 原生栈溢出 → 直接闪退。
   这是项目历史上真实发生过的崩溃。现在全项目有两类录制层，各自的"兄弟"是谁：

   - **一级页录制层**（`App.kt` 的 `entry<Route.Root>`，包住 Pager）：兄弟是底栏、Rail、
     以及所有页面级弹层（弹层由最外层 Scaffold 绘制，本来就在它外面）。
   - **页面正文录制层**（`GlassScope.contentBackdrop`，落在 `ContentBackdropBox`）：兄弟是顶栏。

   **录制层仍然不能上移到 `Scaffold` 自身。** 官方 `GlassPage.kt` 敢那么写，是因为它顶栏采的是
   `LocalTopAppBarBackdrop`（另一个 backdrop）；我们顶栏与内容**共用** `contentBackdrop`，
   录制层一旦包含顶栏，顶栏采样就会把正在采样的自己录进去 —— 1.4 试过，SIGSEGV。

   **新增一条坐标硬约束：弹层的采样源必须录在 `HorizontalPager` 外面。**
   Pager 的每一页按页宽错位摆放，页面内录制层上报的窗口坐标是**槽位坐标**
   （第 2 个 tab 报 x = 1080），而弹层上报的是真实坐标（x = 0）—— 两者相减差一整页宽，
   采样区间整块落在录制层之外，玻璃只剩材质、看不到背景（第六轮"作品菜单不模糊"的真因，
   `docs/os4-round7-audit.md` §3 有实测数字）。所以弹层用 `LocalRootBackdrop`（录在 Pager 外），
   不用页面自己那一层。二级页没有 Pager，才回落到页面录制层。

   **新增一条覆盖硬约束：采样源必须在组件的整个绘制区域里都有像素。**
   录制层比组件小、或在某些状态下是空的（透明度归零、区域内没有不透明内容）时，
   `textureBlur` / `progressiveTextureBlur` 会采到全透明，画出来是**边缘生硬的深色带**
   或**一小块错位色斑**，而不是"没有模糊"。
   已经踩过的两个例子：
   - 关于页顶栏原来采正文录制层，而正文层刻意不垫底色（渐变在页面根部）、顶部还有 40dp 图标留白
     → 顶栏区域全透明 → 真机上顶栏一条黑带 + 返回键胶囊花屏（`_archive/round11/`）。
     修法：`Os4ListPage(topBarBackdrop = rememberCombinedBackdrop(渐变层, 正文层))`。
   - 渐变层自己会在滚动到底后淡出，所以**不能**只采它 —— 这就是要叠两层的理由（`ui/CombinedBackdrop.kt`）。
   判断方法：问一句"这个组件所在的位置，在被采样的那层里是谁画的？画了没有？"
2. **颜色分三层，别混用：`pageBaseColor()` / `pageColor()` / `cardColor()`。**
   - `pageBaseColor()` = 页面**真正铺在屏幕上**的那层（= `MiuixTheme.colorScheme.surface`，AMOLED 下纯黑）。
     玻璃录制层垫底色、底部主操作条遮挡、`Os4ListPage` 的 `containerColor` 只用这一个。
     用错会在页面上留一块比页面亮的方块：深色静态下 `pageColor()` 是 `#1E1E1E`、页面是 `#000000`
     （第十轮实测，`docs/os4-round10-audit.md` §1.3）。
   - `pageColor()` = 由卡片色反推的**次级表面**（比页面深/浅一档），给预览舞台这类
     "要跟页面拉开一档"的位置。它不是页面底色。
   - `cardColor()` = `surfaceContainer`，卡片用。
   旧结论「Monet 会把 `surface`/`surfaceContainer` 压成同一个值」在 `3177a684` 上**已不成立**
   （第十轮四套配色实测都不同值），所以页面直接用 `surface` 不会和卡片糊在一起。
3. **玻璃只有一个决策点：`ui/GlassSupport.kt` 的 `LocalGlassEnabled`。**
   页面里不许再出现 `isRenderEffectSupported()` / `isRuntimeShaderSupported()` 之类的判断 ——
   一度 `App.kt`、`GlassScope`、`Widgets.kt` 各判各的，就出过「顶栏玻璃、底栏实心」的割裂。
   **`isGlassSupported()` 现在只调库的 `isRuntimeShaderSupported()`**（`SDK_INT >= 33`）——
   这正是官方 example 的判断点（`LicensePage.kt:53`、`PageUtils.kt` 的 `rememberBlurBackdrop()`）。
   以前还叠了一个 `isRenderEffectSupported()`（`SDK_INT >= 31`），在 `minSdk 33` 下恒为 true，
   0.9.5-beta02 那轮已删掉。
   **注意它查不出某台 GPU 上模糊会不会翻车**（真机上出现过「内容不糊开、只被压暗」），
   所以"关掉玻璃"这个开关必须一直留给用户。
4. **弹层的 `backdrop` 用 `pageOverlay` 传进来的那个（= `LocalRootBackdrop ?: 页面正文录制层`），
   不要用卡片局部层，也不要用 `null` 一躲了事。**
   `pageOverlay` 已经按页面类型选好了采样源，调用方直接传，不要再自己挑。
   卡片局部录制层必翻车：`GroupCard` 自建 `layerBackdrop` 时，弹层伸出行边界后没有像素可采，
   下半截只剩材质、还带一条横向硬边（`docs/os4-round5-audit.md` §1.3 有对照图）。
   **不要以为 `localPositionOf` 一定能算对坐标**：它只在同一条布局链上自洽。
   `HorizontalPager` 的页面按页宽错位，页面内录制层上报槽位坐标（第 2 个 tab x = 1080），
   弹层上报真实坐标 —— 差一整页宽，采样整块落在录制层之外
   （第六轮"作品面板不模糊"的真因，第七轮用探针实测，见 `docs/os4-round7-audit.md` §3）。
   所以一级页的弹层必须采 `LocalRootBackdrop`（录在 Pager 外面）。
   1.4 之前一律传 `null` 是躲脏斑的补丁；脏斑的真实成因是录制层没垫底色，
   `rememberPageBackdrop` 现在先 `drawRect(pageBaseColor())`，前提已经不成立。
   **前提**：弹层宿主不能在被采样的录制层里面（第 1、11 条）。
   **下拉走同一条规则**：`DropdownRow` 的 `backdrop` = `LocalRootBackdrop ?: LocalPageBackdrop`，
   不是只看页面层 —— `GlassDropdownPreference` 默认 `renderInRootScaffold = true`，
   弹层由最外层 Scaffold 绘制、上报真实坐标（第十轮修，`docs/os4-round10-audit.md` §4.2 #5）。
5. **离散选项（2~4 个）优先用 `DropdownRow`；做分段控件时用上游的 `GlassSegmentedTabRow`。**
   库里三个页签组件（`TabRow` / `TabRowWithContour` / `GlassTabRow`）都是**可横向滚动的页签**，
   不是「一整条凹槽 + 一个滑块」的分段控件；`TabRowWithContour` 还会额外画 5dp 轮廓 padding，
   看起来像一排小按钮。上游后来补了 `GlassSegmentedTabRow`（`GlassTabRow.kt`），那才是分段控件。
   > 第十轮更正：本条以前写「分段控件用自绘的 `SegmentedControl`」，
   > 但源码里**从来没有**这个组件（全项目 grep 零命中），属于文档漂移，已改写。
   滑块取色也不能用 `colorScheme.background`（Monet 下不是白色），要取 `cardColor()`。
6. **卡片内不加 `HorizontalDivider`。** 靠 56dp 最小行高 + 16dp 内边距区分，这与 KernelSU / HyperOS 设置页一致。
7. **不要引入 `androidx.compose.material3`。** 会和 Miuix 的设计语言打架，且显著增大包体。
8. **`GlassNavigationRail` 必须叠放在内容之上，不能进 `Row` 当兄弟。**
   它的根节点是 `BoxWithConstraints(fillMaxSize())`，自己决定面板位置（左边距 12dp + 宽 250dp）。
   一旦用 `Row` + `weight` 排列，它会吃掉整行宽度，内容被挤成 0 宽 —— 实机上就是"侧栏在、内容全白"。
   正确结构：`Box { Box(fillMaxSize + padding(start = 侧栏宽) + layerBackdrop) { NavDisplay } ; Rail }`。
   官方 `AppContent` 也是这个结构（`useGlassSidebar` 分支）。
9. **内边距只走 `pageContentPadding()`。** 页面里不要再手写 `+2.dp` 或固定 `128.dp` 底部留白：
   底栏是浮层、不占 Scaffold 槽位，高度由 `LocalBottomBarClearance` 单点下发（`App.kt` 算）。
   二级页（工具页 / 主题 / 关于 / 许可）要传 `reserveNavBar = false` —— 二级页根本不挂载底栏，
   再预留 100dp 会在列表末尾留一块空带。
   **横向安全区也归它管**：左右 = `innerPadding.calculate{Left,Right}Padding + railInset()`。
   `innerPadding` 的横向部分就是 `systemBars ∪ displayCutout`，横屏时挖孔落在起始边（本机 64dp）。
   曾经只让 `railInset`、把这段丢掉，结果侧栏自己吃了安全区、内容没跟上，横屏下被压住 52dp
   （`docs/os4-round9-audit.md` §1）。**不在 Scaffold 内容槽位里的控件**（底部主操作条、
   页面级浮层控件等）用 `rememberHorizontalSafeInsets()` 自己取，不要再写第三套算式。
10. **图标只用官方 Glass 族，不自绘、不混 `extended`。**
    `MiuixIcons.Glass.X`（定色/Regular）给主入口、底栏、核心卡片；
    `MiuixIcons.Glass.Light.X`（同族细线档）给设置、关于、作品等次级入口。
    Glass 族是 **28dp 画布**（`buildHyperOSIcon` 里 `defaultWidth = 28.dp`），
    塞进 24dp 槽位会明显偏小；列表统一 **28dp**（`IconSizes.Row`，与官方 example 的行首图标同值）。
    尺寸只有 `IconSizes` 一个出处：`Row` 28 / `Action` 28 / `Inline` 19（行内小动作）/ `Empty` 44 / `Media` 28（媒体区占位）。
    注意新版权重/命名变化：`Glass.Folder` / `Glass.Settings` 已改名成 `*Outline` / `*Fill`。
    顶栏返回键玻璃档用 `Glass.ChevronBackward`（28dp），非玻璃档才用 extended 的 `Back`（24dp），
    官方 `component/BackNavigationIcon.kt` 就是这么分的。
11. **`GlassDialog` / `GlassBottomSheet` 靠 Scaffold 宿主绘制，`GifApp` 最外层就是那个宿主。**
    它们通过 `MiuixPopupUtils.DialogLayout` 找到**最外层** Scaffold 的 `LocalRootDialogStates`，
    由那个 Scaffold 的 `MiuixPopupHost` 绘制。`GifApp` 因此套了一层
    `Scaffold(containerColor = Transparent, contentWindowInsets = 0)`——只当宿主、不管布局，
    与官方 `GlassPageHost`（`GlassPage.kt:652`）同一个用意。
    **去掉这层壳会同时坏三件事**：弹层退回到页面自己的 Scaffold（被页面录制层包住 → 有采样到自己的风险），
    坐标差一整页宽（第 1 条），以及底栏会压住弹层。
    `GifApp` 自己那一层真要弹系统级窗口，仍然用 `WindowDialog`；页面级弹层走 `pageOverlay`（第 14 条）。
12. **底栏属于一级页，Rail 属于 App 层，两者的在场条件不是一回事。**
    - **底栏**（`GlassNavigationBar`）画在 `entry<Route.Root>` 里面、Pager 录制层的兄弟位置，
      和官方 `CompactScreenLayout`（`AppContent.kt:581-627`）一样是"一级页自己的浮层"。
      二级页的组合里根本没有它 —— **不要**再用 `stack.size` 去挂载/卸载它：
      那样进二级页会在新页盖上来之前就"闪没"，返回时又会浮在退出中的二级页之上。
      官方实测的行为是"被新页盖住 / 被揭开"，没有自己的进退场动画。
    - **Rail**（`GlassNavigationRail`）在一级页外面，仍由 `wide && stack.size <= 1` 控制。
      官方宽屏二级页是保留 Rail 的，我们故意不保留（见 `docs/os4-round7-audit.md` §2.3）。
    - **不要用 `visible = false` 关导航**：库里是 `showScale` 缩到 `NAV_HIDE_SCALE` + 淡出 +
      自身模糊一起收（`GlassNavigationBar.kt:200`、`:256-266`），那就是"缩一下"的来源。
    - 一级页的 `BackHandler` 只在 `tab != 0` 时启用：tab 0 交还系统直接退出，
      官方 `MainScreenBackHandler`（`AppContent.kt:910`）就是这么写的，不要加退出确认框。
13. **横屏导航形态照官方两档阈值走，不要自己定规则。**
    宽屏判定 `shouldShowSplitPane` = `≥840dp` 或（`≥600dp` 且高宽比 < 1.2）→ 用 `GlassNavigationRail`；
    是否展开看 `shouldExpandNavigationRail` = **`≥1200dp`**。手机横屏约 900dp，落在"有 Rail 但折叠"，
    折叠态就是**顶部居中胶囊、只显示文字**（`GlassNavigationRail.kt:276`）——
    那不是顶部导航栏，是 Rail 的另一种形态。不要写死 `initialExpanded = true`，
    也不要用 `showToggle = false` 把折叠入口藏掉（1.5 之前就是这么写的）。
    内容让位由 `LocalRailInset`（**函数**，`() -> Dp`）下发：
    `{ (ExpandedWidth + Margin) * railState.progress }`。
    `progress` 是逐帧动画值，**不许在组合期解包**：`pageContentPadding` 在
    `PaddingValues.calculate*Padding` 里读、`PrimaryActionBar` 与顶栏走 `Modifier.railStartInset()`
    在量测期读。写成 `val inset = LocalRailInset.current()` 再 `padding(start = ...)`，
    侧栏展开的每一帧都会重组整个页面并重排列表（迁移指南第 2 条红线）。
    `railStartInset(base)` 的 `base` 是**起始边基准**（常规边距 + 安全区），它和 `railInset` 相加；
    这个参数曾经被忽略过，后果是窄屏下底部主操作条左端贴屏幕边缘（R9 §2.2 #5）。
    顶栏也要让位：玻璃档传 `contentModifier`（只挪前景，背景带仍满宽），非玻璃档挂 `modifier`。
    **录制层不许跟着收窄** —— 收窄会让 Rail 左半段没有像素可采，玻璃只剩一块平色。
    官方 `AppContent.kt:300-350` 也是"满宽录制 + 页面内边距让位"。
   **`derivedStateOf` 换成 `computedStateOf`（Compose 1.13 新增）。**
   官方 0.9.5-beta02 已把 `GlassTopAppBar`、`AboutPage`、`MainPage` 等处的
   `derivedStateOf` 全部换掉，本项目跟随（`ui/PageChrome.kt` 两处）。
   两者语义差别只有一条：`computedStateOf` **每次读都重新计算、不缓存**，
   但**仍然只在计算结果变化时才让组合失效**；`derivedStateOf` 会缓存结果，
   适合"计算本身很贵、同一趟组合里被读很多次"的场景。
   所以像 `canScrollBackward`、`progress == 1f` 这种廉价判据用 `computedStateOf`，
   省掉缓存簿记与失效写回；昂贵的多重读取仍用 `derivedStateOf`。
   无论哪个，**逐帧变化的值（`railState.progress`）都不能在组合期解包** ——
   这条和下面的让位规则是同一个原因。

14. **页面级弹层（Dialog / BottomSheet）走 `Os4ListPage` 的 `pageOverlay` 槽位，不要挂在页面外面。**
    槽位在页面 Scaffold **内部**、`ContentBackdropBox` **外部**：拿得到宿主，又不会被录进页面录制层。
    槽位参数是**弹层采样源**，`Os4ListPage` 已经按 `LocalRootBackdrop ?: 页面录制层` 选好了（第 1 条），
    调用方直接把它传给 `backdrop` 即可，不要再自己挑录制层。
    **不再有 `modalOverlayOpen`**：弹层由最外层 Scaffold 绘制，永远在底栏之上，
    面板开着时底栏只是被遮罩压暗（与官方一致）。之前那套"开面板就把一层导航让出去"的联动已删除，
    不要加回来 —— 它会在面板异常关闭时把导航留在隐藏状态。

15. **关于页走 `Os4ListPage`，它是唯一"自己铺背景"的页面；不要退回自建 Scaffold。**
    整屏渐变 + 固定大标题 + 可滚动内容，现在由骨架的三个参数表达：
    - `background = { progress -> ... }`：渐变（`ui/bg/BgEffectBackground`）铺在**页面根部、Scaffold 之外**，
      骨架负责把 Scaffold 设成 `containerColor = Transparent` + `contentWindowInsets = 0`。
      渐变要一直延伸到状态栏下面，放进 Scaffold 内容区会在顶栏下方切出一条硬边。
      传了 `background` 的页面，顶栏在收起前**完全透明**，`progress == 1f` 后才上 `surface` 实色。
    - `largeHeader = { progress -> ... }`：固定在列表**下面**的图标 / 名称 / 版本。骨架自动在列表首项
      插一块与头部等高的透明占位（初值 300dp，`onSizeChanged` 回填），头部因此不会被卡片压住；
      `progress` = 首项滚动偏移 / 头部高度，`progress == 1f` 之后顶栏才浮起。
      关于页 `AboutHeader` 的淡出阈值：版本 0.05、标题 0.20、图标 0.35（各 0.15 宽），
      图标 / 标题另缩到 0.95。
    - `topBarBackdrop`：顶栏采样源覆盖。关于页的正文层不垫底色（`opaqueBase = false`），
      顶部 40dp 又是图标留白 → 正文层在顶栏区域没有像素；渐变层滚到底又会淡出。
      传 `rememberCombinedBackdrop(bgBackdrop, contentBackdrop)`（`ui/CombinedBackdrop.kt`），
      任何滚动位置至少有一层有像素。玻璃关时传 `null`（走库的渐变带）。
      **不要**为了"让顶栏采到渐变"在页面根部再套一层 `layerBackdrop`：
      那样顶栏就成了录制层的子节点，采样到自己 → libhwui 栈溢出。
    - 短内容也要能滚：内容 item 外先放 `Spacer(Modifier.fillParentMaxHeight())` 撑满一屏，
      否则列表不可滚，头部永远收不回去（1.10 的 `fillParentMaxHeight`）。
    - 卡片用 **`GlassCard(backdrop = bgBackdrop)`**：官方 `Card` + 官方 `textureBlur`，
      卡片本体透明、圆角 `CardDefaults.CornerRadius`、半径 60f、混合色 `surface` 0.6，
      与图标 / 标题同采 `bgBackdrop`，三者材质同源（InstallerX `MiuixAboutPage.kt:612-638` 的写法）。
      `backdrop = null` 时退回 `cardColor()` 不透明卡 —— 玻璃关闭时入口不能消失。
      **不要**自造卡片渐变或第二套卡片色。
    - 图标与软件名称**必须走参考项目的原生 `textureBlur`**：一份背景录制层
      （`rememberLayerBackdrop()` 挂在 `BgEffectBackground` 的 `bgModifier` 上）、
      `BlurColors(blendColors = logoBlend)`、`contentBlendMode = DstIn`、`blurRadius = 150f`。
      三档混合色与 KSU `AboutMiuix.kt:189-203` / InstallerX `MiuixAboutPage.kt:420-431` 逐字相同。
      图标容器 100dp（`clip = true`）、主体 `requiredSize(132dp)`、`ColorFilter.tint(onBackground)`；
      标题 35sp Bold、同 backdrop 同混合色。
      **不要**改成 `gradientFilled` / `SrcIn` / ShaderBrush / 任何自制渐变（第九轮做过，第十轮已删除）。
    - 本机模拟器**必须用 `-gpu host` 启动**（`emulator -avd android16 -gpu host`）：
      此时 `textureBlur` / AGSL 渐进模糊与真机一致，图标、标题会真的跟随背景渐变。
      早先用 `-gpu swiftshader_indirect` 时 `textureBlur` 不产出像素，一度在代码里加了
      "软件渲染器就跳过模糊"的判据 —— 那条判据把 `-gpu host` 也误判掉，导致图标/标题退化成纯色、
      顶栏关掉渐进模糊，**已删除**。能力判断一律交给库（`isRuntimeShaderSupported()`），
      不要在业务代码里猜渲染器。
    - 图标**不带白底/底板**，只有单色剪影。第九轮那块 `cardColor(alpha = 0.72f)` 底板已删除。
    - 入口行**不带前置图标**，描述写在 `ArrowPreference` 的 `summary`（InstallerX 同款）；
      许可只有一个入口，本项目 GPL-3.0 / 第三方 Apache-2.0 / NOTICE 都在同一页分组里，
      不要再拆成两个 Route。

## 常见改动怎么下手

| 要做的事 | 改哪里 |
| --- | --- |
| 加一个工具 | `ToolCatalog` 加枚举项和图标 → `TaskRunner` 加分支 → `ToolScreen.ParamPanel` 加参数行 |
| 加一项设置 | `SettingsStore` 加字段 → `AppState` 加代理属性（读写联动） → `SettingsScreen` / `ThemeScreen` 加行 |
| 加一个页面 | `Route` 加 object → `App.kt` 的 NavDisplay 加 `entry<Route.X>` → `screens/` 新建文件（列表页直接套 `Os4ListPage`） |
| 加整屏背景 / 固定大标题 | 用 `Os4ListPage` 的 `background` / `largeHeader` / `topBarBackdrop`，不要自建 Scaffold（见第 15 条） |
| 加输入框 | `basic.TextField` **独立一条**，`padding(horizontal = 12.dp).padding(bottom = 12.dp)`；它自己就是一张卡（16dp 圆角 / `secondaryContainer` 底色 / 16dp 内边距），**不要塞进 `GroupCard`**（两层圆角会套在一起、底部会被卡片圆角裁掉） |
| 改配色层次 | 只动 `pageBaseColor()` / `pageColor()` / `cardColor()`，不要在各处散写 `colorScheme.*`（分工见硬约束 2） |
| 加离散选项（2~4 个） | `DropdownRow`（玻璃浮层下拉），不要做"点一下轮换到下一个" |
| 加弹层 | 页面级弹层传给 `Os4ListPage(pageOverlay = ...)`（Scaffold 内、录制层外，玻璃版可用，见第 14 条）；`GifApp` 自己那层只能用 `WindowDialog` |
| 加一个第三方依赖 / 改许可 | `ui/LicenseCatalog.kt` 的 `thirdPartyLibraries` 补一行；正文放 `assets/licenses/`。不要新开 Route，`licenseEntries` 的下标就是 `Route.License(index)` |

## 文案与对外内容

界面上和对外发布的内容都用普通开发者的写法，不要写成产品宣传稿。具体：

1. **副标题只放标题没说的事。** 「水平镜像 / 左右翻转」这种同义复述直接不传 `summary`
   （`SwitchRow` 的 summary 可空）。没有增量信息就不要为了"显得丰富"加一行。
2. **错误提示要说清"发生了什么 + 下一步"。** 「无法读取该视频，换一个文件试试」是合格写法，
   「读取失败」不合格。涉及删除、覆盖、权限的提示必须保留完整说明。
3. **同一功能在不同页面叫法一致。** 例如"保存到相册"「生成」页与作品面板同名；
   Monet 取色统一叫「动态取色」（不叫"自定义颜色"，那不是自定义）。
4. **不用空泛宣传词**：智能、高效、便捷、一站式、全方位、轻松实现。
5. **CHANGELOG / Release / README 只写实际改动**：新增什么、调整什么、修复什么。
   不写背景介绍、不写口号、不写"进一步提升稳定性"这种没有具体内容的话。
   技术细节进 `docs/`，对外内容一句话能说清就一句话。
6. **README 里的数字与清单要跟代码同步**（硬约束条数、依赖列表、已删功能）。过期的介绍比没有更糟。

## 判定基准（IMPORTANT）

**"像不像 OS4 / 是不是 MIUIX 原生写法"只有一个基准：MIUIX 官方源码 + 官方 example 的编译产物。**

- 需要视觉真值时，直接编译官方 example：`./gradlew :example:android:assembleDebug`（在 `../miuix` 里），
  装机对照；源码依据用 `miuix/example/shared` 与 `miuix/docs/demo`。
- **不要拿第三方 App 当标准**（包括上一轮参考过的 Glacier 等下游项目）：它们只是"别人怎么组合"，
  可能本身就是错的。之前从 Glacier 抄来的脚注行、24dp 行图标都已按 MIUIX 改回/删除。
- 组件默认值、尺寸、动画一律从 MIUIX 源码取；库里没有明确证据的，按官方 example 的调用点取。

## 产物与版本约定

- 版本号在 `build.gradle.kts` 的 `versionCode` / `versionName`
- release 使用 **debug 签名**，只为方便装机试用；正式分发必须换 keystore
- debug 包约 45MB（未混淆），release 约 3.3MB
- 改完 UI 要跑一遍「静态 / Monet × 明 / 暗」四种组合，只看一种会漏掉配色塌陷

## 构建环境的两个坑

1. 基线是 Miuix **`f3829d3d`**（2026-11-20，`perf: improve sidebar performance (#489)`），
   对应发布版 **`v0.9.5-beta02`**（tag `66248bac`，已推 Maven Central）+ 其后 3 个 main 提交；
   源码基线取后者，理由见 `docs/os4-round12-audit.md` §0.1。
   **`v0.9.5-beta02` 强制要求 Compose Multiplatform `1.13.0-rc01`**，不能只改 Miuix 版本。
   上上次基线是 `3177a684`（逐项差异见 `docs/miuix-baseline-3177a684-to-5ff41ba9-diff.md`），
   上一轮基线 `5ff41ba9`（见 `docs/os4-round11-audit.md`），升级前定制面清单见
   `docs/upgrade-prep-customization-inventory.md`。
   升级前先看上游 `GlassTopAppBar` / `GlassNavigationBar` 的默认值有没有变 ——
   这两处我们**故意**不传参数、吃默认值，上游一改观感就跟着变。
   已经踩过的一次：`GlassNavigationBar` 的 `floatingMargin` 在 ≤16dp 档从 28dp 变成 20dp，
   `ui/PageChrome.kt` 的 `rememberBottomBarClearance()` 复刻了这条公式（必须同步，见该函数注释）。
   这条公式的输入是 `maxOf(navigationBars, captionBar)`（上游 `GlassNavigationBar.kt:309-324`），
   只取 `navigationBars` 会在自由窗口 / 桌面模式下少让一截。
   **从 0.9.5-beta01 起上游把 `compileSdk` 提到 37.2**，下游必须同步：
   `compileSdk { version = release(37) { minorApiLevel = 2 } }`，否则 AGP 配置期直接拒绝
   （`requires ... compile against version 37.2 or later`）。`targetSdk` 可以继续停在 36。
   每轮升级的差异与结论见 `docs/os4-round*-audit.md`。
2. Miuix 的 `miuix-glass` / `miuix-glass-icons` 没有发布到 Maven Central，只能源码引入；
   且它的 `build-logic` 在配置阶段调 git，composite build 下必须补 `workingDir = rootDir`。
   `scripts/setup-miuix.sh` 会把这两件事都做掉。
3. **带输入框的页面不要再自己加 `imePadding()`**：`Os4ListPage` 的 Scaffold 已经挂了
   （官方 `ComponentPage.kt:148` 同款），edge-to-edge 下 `adjustResize` 不会缩窗口。
   有底栏的页面，底栏高度要做成**真实布局内边距**（`LazyColumn(modifier = Modifier.padding(bottom = barHeight))`
   + `pageContentPadding(barReserved = barHeight)`），否则列表视口一直延伸到屏幕底，
   输入框获得焦点时框架会把它滚到**底栏背后**。
   再叠加两件官方能力：`LocalBringIntoViewSpec` 告诉框架"可视下边界要扣掉底栏"、
   `Modifier.bringIntoViewRequester` 把请求打在**整个输入框外层**（`TextField` 自己只请求文本行）。
   **不要自己写 `scrollBy` 循环补差量**（写过，已删除）：那是重复实现框架已有的能力，上游一升级就失真。
   `TextFieldState` 必须建在**页面级**，不要建在 LazyColumn 的 item 里：item 被回收后
   `remember` 会重建，刚输入还没同步回表单的内容会丢。
4. 在受限网络下 `git push` 可能报 `CONNECT tunnel failed` / `Empty reply from server`，
   而 `api.github.com` 仍然可用（`gh api` 能通）。必要时走 REST API 推送：
   对每个待推提交，用 `git show <sha>:<path>` 取内容 → `POST /git/blobs`（base64）→
   `POST /git/trees`（`base_tree` = 远端当前 tree）→ `POST /git/commits`（parent = 远端当前 head）→
   最后 `PATCH /git/refs/heads/main`。**远端与本地 commit SHA 会不同源**（tree 相同、SHA 不同），
   网络恢复后本地要先 `git fetch` 再 `git reset --hard origin/main` 才不会撞 non-fast-forward。
   1.6 的发布就是这么推的：本地 `b055db7` ↔ 远端 `a43e6029`。
   **走 API 上传 Release 资产时必须二进制直传**（`Content-Type: application/vnd.android.package-archive`，
   body 就是 APK 字节）。手搓 `multipart/form-data` 会被 GitHub 原样存成资产（开头是边界串 `----wb…`
   而不是 `PK`），下载下来根本装不上。上传完**必须下载回来比一次 SHA**。
