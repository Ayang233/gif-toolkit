# AGENTS.md

给接手这个仓库的人（或 AI 助手）看的上下文。README 讲「是什么、怎么构建」，这里讲「改的时候要注意什么」。

## 一句话

单模块 Kotlin Multiplatform 工程，**全部实现放在 `androidMain`**，`commonMain` 只用来声明依赖；
UI 全部基于 [Miuix](https://github.com/compose-miuix-ui/miuix)，**不引入 Material 组件**。

## 目录地图

```
src/androidMain/kotlin/com/ayang/giftoolkit/
├── App.kt                主题装配、NavDisplay 路由、玻璃底栏/宽屏侧栏、退出确认
├── Route.kt              路由定义（Root / Tool / About / Licenses / License）
├── AppState.kt           全局状态 + 每个工具一份 ToolForm（表单状态）
├── data/SettingsStore.kt 外观设置持久层（SharedPreferences）
├── ui/
│   ├── ToolCatalog.kt    十个工具的元数据（id / 标题 / 说明 / 分组 / 图标 / 按钮文案）
│   ├── Widgets.kt        分组卡片、行、下拉、预览舞台、主操作条、pageColor/cardColor、图标尺寸
│   ├── PageChrome.kt     页面骨架 Os4ListPage + pageScrollModifiers + pageContentPadding
│   ├── Adaptive.kt       宽屏判定（阈值与官方 AdaptiveUtils 一致）
│   ├── GlassScope.kt     页面玻璃作用域 + PageTopBar（玻璃/普通两态）
│   ├── GlassSupport.kt   玻璃开关与能力检测的**唯一决策点**
│   ├── AppInfo.kt        版本号串（关于页与设置页副标题共用）
│   ├── ProgressButton.kt 自带进度填充的按钮（移植自 miuix 生态，Apache-2.0）
│   └── LicenseCatalog.kt 第三方组件清单
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
   这是项目历史上真实发生过的崩溃。
   **同理：录制层不能上移到 `Scaffold` 自身。** 官方 `GlassPage.kt:242` 敢那么写
   （`Scaffold(modifier = Modifier.layerBackdrop(pageBackdrop))`），是因为它顶栏采的是
   `LocalTopAppBarBackdrop`（另一个 backdrop），与挂在 Scaffold 上的不是同一个。
   我们顶栏与内容**共用** `GlassScope.contentBackdrop`，录制层一旦包含顶栏，顶栏采样就会
   把正在采样的自己录进去 —— 1.4 试过，`RenderNode::prepareTreeImpl` 无限递归，SIGSEGV。
   **录制层必须排除顶栏，落在内容区（`ContentBackdropBox`）。**
2. **颜色一律走 `ui/Widgets.kt` 的 `pageColor()` / `cardColor()`。**
   Miuix 在 Monet 下会把 `surface` 与 `surfaceContainer` 压成同一个值，
   直接用主题角色会让页面和卡片糊成一片。
3. **玻璃只有一个决策点：`ui/GlassSupport.kt` 的 `LocalGlassEnabled`。**
   页面里不许再出现 `isRenderEffectSupported()` 之类的判断 —— 一度 `App.kt`、`GlassScope`、
   `Widgets.kt` 各判各的，就出过「顶栏玻璃、底栏实心」的割裂。
   **另外：Miuix 的 `isRenderEffectSupported()` 只是 `SDK_INT >= 31`**，
   它查不出某台 GPU 上模糊会不会翻车（真机上出现过「内容不糊开、只被压暗」），
   所以"关掉玻璃"这个开关必须一直留给用户。
4. **弹层的 `backdrop` 用「卡片局部录制层」，不要传整页录制层，也不要用 `null` 一躲了事。**
   `GroupCard` 已经建好局部层并通过 `LocalCardBackdrop` 下发，`DropdownRow` 直接消费它
   （官方 `BlurSection.kt:125` 同款：卡片容器 `layerBackdrop(preferenceBackdrop)`）。
   局部层的原点与弹层锚点是同一个容器，`localPositionOf` 反算天然吻合。
   传**整页**录制层才会出问题：弹层挂在录制层外面，跨容器反算一旦偏移就把背景抹成一块脏斑。
   1.4 之前一律传 `null`，那是躲开脏斑的补丁 —— 但脏斑的真实成因是当时的录制层**没垫底色**
   （裸 `rememberLayerBackdrop()` 只录内容，卡片空隙是透明的），1.3 补了 `drawRect(pageColor())`
   之后前提就不成立了。现在弹层真的会采样并模糊卡片，实机复测无脏斑。
5. **分段控件用自绘的 `SegmentedControl`，不要用 `TabRow` 去做"分段"。**
   库里其实有三个页签组件（`TabRow` / `TabRowWithContour` / `GlassTabRow`），
   它们都是**可横向滚动的页签**，不是「一整条凹槽 + 一个滑块」的分段控件；
   `TabRowWithContour` 还会额外画 5dp 轮廓 padding，看起来像一排小按钮。
   离散选项（2~4 个）优先用 `DropdownRow`（玻璃浮层下拉）。
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
   二级页（工具页 / 主题 / 关于 / 许可）要传 `reserveNavBar = false` —— 那时底栏已淡出，
   再预留 100dp 会在列表末尾留一块空带。
10. **图标只用官方 Glass 族，不自绘、不混 `extended`。**
    `MiuixIcons.Glass.X`（定色/Regular）给主入口、底栏、核心卡片；
    `MiuixIcons.Glass.Light.X`（同族细线档）给设置、关于、作品等次级入口。
    Glass 族是 **28dp 画布**（`buildHyperOSIcon` 里 `defaultWidth = 28.dp`），
    塞进 24dp 槽位会明显偏小；列表统一 26dp（`IconSizes.Row`）。
    注意新版权重/命名变化：`Glass.Folder` / `Glass.Settings` 已改名成 `*Outline` / `*Fill`。
    顶栏返回键玻璃档用 `Glass.ChevronBackward`（28dp），非玻璃档才用 extended 的 `Back`（24dp），
    官方 `component/BackNavigationIcon.kt` 就是这么分的。
11. **`GlassDialog` / `GlassBottomSheet` 需要 Scaffold 宿主，`GifApp` 这一层没有。**
    它们靠 `MiuixPopupUtils.DialogLayout` 从 `LocalDialogStates` 取宿主列表来绘制，
    而这个 local 由 Scaffold 提供**并负责渲染**。GifApp 只有 `NavDisplay` + 浮层底栏，
    没有 Scaffold —— 组件会被加进一个没人绘制的列表，**静默不显示**（不报错、不崩溃，
    点了没反应，极难排查）。`renderInRootScaffold` 的 true/false 都要宿主，都不管用。
    这类**页面级**浮层要用系统窗口版：`WindowDialog` / `WindowBottomSheet`。
    页面**内部**的弹层（如 `GroupCard` 里的 `GlassDropdownPreference`）有 Scaffold 祖先，可以正常用玻璃版。

## 常见改动怎么下手

| 要做的事 | 改哪里 |
| --- | --- |
| 加一个工具 | `ToolCatalog` 加枚举项和图标 → `TaskRunner` 加分支 → `ToolScreen.ParamPanel` 加参数行 |
| 加一项设置 | `SettingsStore` 加字段 → `AppState` 加代理属性（读写联动） → `SettingsScreen` / `ThemeScreen` 加行 |
| 加一个页面 | `Route` 加 object → `App.kt` 的 NavDisplay 加 `entry<Route.X>` → `screens/` 新建文件（列表页直接套 `Os4ListPage`） |
| 改配色层次 | 只动 `pageColor()` / `cardColor()`，不要在各处散写 `colorScheme.*` |
| 加离散选项（2~4 个） | `DropdownRow`（玻璃浮层下拉），不要做"点一下轮换到下一个" |
| 加弹层 | 页面级（全屏浮层）用 `WindowBottomSheet` / `WindowDialog`：系统窗口，天然在录制层外，且**不依赖 Scaffold 宿主**（见硬约束 11）。页面内部的弹层才用玻璃版 |

## 产物与版本约定

- 版本号在 `build.gradle.kts` 的 `versionCode` / `versionName`
- release 使用 **debug 签名**，只为方便装机试用；正式分发必须换 keystore
- debug 包约 38MB（未混淆），release 约 3MB
- 改完 UI 要跑一遍「静态 / Monet × 明 / 暗」四种组合，只看一种会漏掉配色塌陷

## 构建环境的两个坑

1. 基线是 Miuix `ac0af467`（2026-11-16）。升级前先看上游 `GlassTopAppBar` / `GlassNavigationBar`
   的默认值有没有变 —— 这两处我们**故意**不传参数、吃默认值，上游一改观感就跟着变。
2. Miuix 的 `miuix-glass` / `miuix-glass-icons` 没有发布到 Maven Central，只能源码引入；
   且它的 `build-logic` 在配置阶段调 git，composite build 下必须补 `workingDir = rootDir`。
   `scripts/setup-miuix.sh` 会把这两件事都做掉。
3. 在受限网络下 `git push` 可能报 `CONNECT tunnel failed`，而 `api.github.com` 仍然可用；
   必要时可走 REST API（blobs → tree → commit → refs）推送，但远端与本地 commit SHA 会不同源。
