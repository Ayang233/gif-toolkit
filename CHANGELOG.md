# Changelog

版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)；`versionCode` 随版本递增。

## 1.15 — 2026-11-24

**升级**

- Miuix 基线从 `b00169ba` 升到 `3c560868`（= `v0.9.5-beta04`），60 个提交。
  差异与核对过程见 `docs/miuix-baseline-b00169ba-to-3c560868-diff.md`。
  工具链零变化（AGP 9.5.2 / Kotlin 2.4.21 / CMP 1.13.0-rc01 / Gradle 9.9.2 / compileSdk 37.2）。
- 官方同轮变化带来的观感：弹层进出场对齐 HyperOS（`DialogMotion` / `SheetMotion`）、
  `Switch` 可拖动、`Slider` 支持点轨道定位、模糊管线换特化 1D 着色器（观感不变、开销下降）。

**调整（自造轮子换成官方组件）**

- 关于页的内容卡改用官方的 `miuix-glass` `GlassCard`（官方 `AboutPage` 同款写法），
  删掉本项目自造的「`Card` + `textureBlur` 采样背景」实现。官方组件不采样、不模糊，
  只画一层半透明填充，玻璃关掉时也不会消失。
- 作品页的文件操作面板：三个动作行改成包在官方 `GlassCard` 里（官方
  `GlassBottomSheetDemo` 的结构），底部留白从手算 `navigationBars + captionBar`
  改成 `navigationBarsPadding()`。
- 底栏与侧栏的选中色不再手算（`if (monetEnabled) primary else onBackground/onSurface`）：
  库的默认值就是这条规则，外层主题已经提供了 `LocalColorSchemeMode`，手算那份是重复实现。
- 删除 `LocalContainerSize` 自造注入：官方 `utils/AdaptiveUtils.kt` 本轮把它删回了
  `LocalWindowInfo.current.containerSize` + `computedStateOf`，本项目跟着改回，
  宽屏判定与预览舞台高度上限读同一来源。
- 顶栏不再显式传 `bandOverhang` / `backgroundAlpha`（值与库默认相同，抄进调用点只会脱节）；
  `bandBrush` 仍显式传，因为要用页面真实底色而不是 `surface`（AMOLED 档）。
- 卡片里的整块自定义内容（预览画布、抠图调色板、结果卡）改用 `Card(insideMargin = 16.dp)`，
  不再套 `Box(Modifier.padding(16.dp))`；预览画布圆角从写死的 12dp 改成 `CardDefaults.CornerRadius`。
- 关于页版本号改用 `body2` 排版档（14sp），不再写裸 `fontSize`。

**修复**

- 主题模式切换后界面不刷新（深色/浅色/跟随系统 都要再动一下别的开关才生效）。
  原因：`ui/AnimatedTheme.kt` 把 `controller.currentColors()` 的返回值直接当 `LaunchedEffect` 的 key，
  而 Monet 档没有 `keyColor` 时库内部走 `platformDynamicColors()`，**每次组合都新建一个 `Colors`**，
  key 每帧变化 → 动画反复 `snapTo(0f)` → 颜色停在起点。改为按颜色值（background / surface /
  surfaceContainer / primary / onBackground）做 key，只有真正换配色才重启动画。
  静态档因为返回控制器里的稳定实例，所以这个缺陷只在 Monet 下暴露。

## 1.14 — 2026-11-22

**升级**

- Miuix 基线从 `3526cfe2`（v0.9.5-beta03）升到 `b00169ba`，19 个提交。
  差异与核对过程见 `docs/miuix-baseline-3526cfe2-to-b00169ba-diff.md`。
  工具链零变化（AGP 9.5.2 / Kotlin 2.4.21 / CMP 1.13.0-rc01 / Gradle 9.9.2），`src/` 升级即编译通过。
- 上游本轮新增 `miuix-glass` 的 `GlassCard`、弹层 `DialogMotion`（含 `DialogMotion.Glass`）、
  渐进模糊带高度修正、`GlassBottomSheet` 去掉底边描边并在无标题时不再留头部空间。
  本项目没有同名实现被替换：`GlassCard` 是"玻璃面板**内部**的半透明卡"（不采样），
  与本项目"用 `textureBlur` 采页面背景画出的玻璃卡"不是同一件事。

**调整（按 `docs/miuix-glass-migration.md` 逐条复核后修掉的自造轮子）**

- 「抠图颜色」从自绘的 5 个圆形色块换成官方 `ColorPalette`。原先的选中外环、2dp/1dp 边框、
  触觉反馈、`semantics` 语义全是自己维护的一套；现在整块只剩一次 `form.keyColor` 读写，
  腾出约 60 行自绘 UI。摆法照官方 `component/ColorPickerSection.kt`（独立卡 + 16dp 内边距 + 全宽）。
- 列表页的 `isScrolled` 补上 `overscrollOffset < -1f`（官方 `GlassPage.kt:176` 同款）：
  页面不足一屏、继续上拉时顶栏的按钮材质与渐进模糊才会跟着浮起，且避开亚像素抖动误触发。
- 新增 `LocalContainerSize`，在 `App` 组合根用 `BoxWithConstraints` 注入一次；宽屏判定
  （840/600/1200）与预览舞台的高度上限改读它，读不到才回退 `LocalWindowInfo`
  （官方 `utils/AdaptiveUtils.kt` 的口径）。多窗口 / 自由窗口下不再拿"窗口尺寸"当"可用区域"。

## 1.13 — 2026-11-21

**升级**

- Miuix 基线从 `f3829d3d` 升到 `3526cfe2`（即 `v0.9.5-beta03`，tag 与 main head 同一个提交，9 个提交 / 439 个文件）。
  差异与核对过程见 `docs/miuix-baseline-f3829d3d-to-3526cfe2-diff.md`。
- AGP 9.5.1 → 9.5.2（上游同步抬的版本，composite build 两边的 AGP 必须一致）。
  Compose Multiplatform 仍是 1.13.0-rc01，Kotlin 2.4.21、Gradle 9.9.2、compileSdk 37.2 都没变。

**调整**

- 「字幕内容」输入框换成官方示例第一条的写法：标题走 `GroupHeader`，`TextField` 不再传 `label` /
  `useLabelAsPlaceholder`。原先提示文字由 label 层绘制、光标跟着输入层走，两层行盒不同：
  竖杠比中文字形高出一截且整体偏上，开始打字的瞬间文字还会往上跳约 4dp。
- 删掉「设置 → 主题」页底部的「调试（仅调试包可见）」分组与「开始按钮配色对比」页。配色方案已定，
  调试包也不再需要这个入口；`Route.ButtonColors`、`ButtonColorPreview.kt`、`DEBUG_UI` 一并移除。

**说明**

- 本轮不动设计、不动功能，只跟进基线 + 收掉一个调试入口。

## 1.12 — 2026-11-20

**升级**

- Miuix 基线从 `5ff41ba9` 升到 `f3829d3d`：`5ff41ba9` → `66248bac`（`v0.9.5-beta02`，已发布 Maven Central）
  为 11 个提交，再加其后 3 个 main 提交。差异与结论见 `docs/os4-round12-audit.md`。
- Compose Multiplatform 1.12.2 → 1.13.0-rc01（0.9.5-beta02 的硬性要求），Gradle wrapper 9.9.1 → 9.9.2。
- 渐进模糊按官方新版管线默认开启（顶栏与底栏都是）：库内部换成 2x bridge / 着色器特化 /
  `cascade[0]` 复用 / `NoiseDither` 折进最终 pass，调用方不需要改参数。

**调整**

- 玻璃能力判断只留库的 `isRuntimeShaderSupported()`：原先叠的 `isRenderEffectSupported()`
  在 `minSdk 33` 下恒为 true，属于多余的兼容代码。
- `derivedStateOf` → Compose 1.13 的 `computedStateOf`（滚动判据与关于页头部收起判据），
  与官方 `MainPage.kt` / `AboutPage.kt` 的写法一致。
- 主操作按钮的容器换成官方 `Button` 的那一套（`squircleSurface` + `clickable`），
  四角与其它 Miuix 按钮同为连续曲率，不再用 `Surface + RoundedCornerShape`。
- 底栏占位高度补上 `captionBar`：与上游 `GlassNavigationBar` 的取值顺序一致（手机恒 0，自由窗口下才生效）。

**调整**

- 「字幕内容」输入框从参数卡片里移出来，按 MIUIX 官方摆法独立成一条（外边距 12dp）：
  `TextField` 自己就是一张卡（16dp 圆角 / `secondaryContainer` 底色 / 16dp 内边距），
  塞进 `GroupCard` 会变成两层圆角套在一起、底部被卡片圆角截掉。
- 卡片内自定义内容的垂直内边距 12dp → 16dp（官方 `Card(insideMargin = PaddingValues(16.dp))`）。
- 说明段落上下留白 10dp → 8dp，与分组标题的 28/8 同一节奏；结果卡并排按钮间距 10dp → 12dp。

**修复**
- 「字幕内容」输入框弹出键盘时不跟随上移：edge-to-edge 下 IME insets 没人消费，页面骨架补
  `Modifier.imePadding()`（与 MIUIX 示例同一行）；再用官方的 `LocalBringIntoViewSpec` 把"底栏遮挡"
  告诉框架、`bringIntoViewRequester` 把请求落在整个输入框上（TextField 自己只请求文本行）。
  `TextFieldState` 提到页面级，避免列表项回收时把刚输入的文字丢掉。
- 关于页图标容器 100dp → **88dp**（官方 `AboutPage.kt:249` 同值）；主体 132dp → 88dp
  （资源自带 26% 透明留白，取 88 后可见主体约 65dp，与官方 74dp 图的可见主体同档）。
- 底部主操作条按官方尺寸对齐：左右留白 12dp → 24dp（官方底部条同值），按钮高度不再写死 50dp，
  改用 `ButtonDefaults.MinHeight` + `InsideMargin`。


- 「AMOLED 纯黑 + 动态取色 + 深色」下，关掉玻璃材质后顶栏是一条比页面亮 19/255 的实色带：
  非玻璃档的顶栏底色写的是 `colorScheme.surface`（#131316），而 AMOLED 会把页面压成纯黑。
  现在顶栏（含关于页收起后上实色的那一档）统一走 `pageBaseColor()`。
- 非玻璃档的返回键颜色没按官方给：官方 `BackNavigationIcon.kt` 是玻璃 `onSurface` / 非玻璃
  `onBackground`，我们非玻璃那档漏了 tint，吃成了标题色。

**已知**

- Compose 1.13.0-rc01 在本机 GPU 模拟器上把绘制侧帧耗时抬高约 1ms：janky 帧 4% → 9%，
  分位数（p50 / p99）不变。对照构建已确认与 Miuix 基线和本项目代码无关，属上游 RC 现象，
  见 `docs/os4-round12-audit.md` §5。

## 1.11 — 2026-11-20

**升级**

- Miuix 基线从 `3177a684` 升到 `5ff41ba9`（34 个提交），差异与结论见 `docs/miuix-baseline-3177a684-to-5ff41ba9-diff.md`。
- 作品操作面板按 MIUIX 官方弹层写法重做：`GlassBottomSheet` + 官方 `BasicComponent` 行（行高、内边距、图文间距全吃默认值）+ 官方底部留白；去掉自造的头部按钮与脚注。
- 各列表页补上官方 `VerticalScrollBar` 滚动条。
- 行首图标统一 28dp（与官方示例的 Glass 图标同尺寸）。
- 改用官方 `rememberNavSystemCornerRadius()`、`squircleSurface`、`NavDisplayEffects` 默认值、`ButtonDefaults` / `ProgressIndicatorDefaults` 常量。
- 接入官方 `centerObstructionStart`：横屏折叠态侧栏不再压住顶栏标题。

**修复**

- 关于页图标与软件名称不跟随背景渐变：删掉自造的"软件渲染器"降级判断（它把硬件加速模拟器也误判了），能力判断交给库。
- 动态取色下玻璃材质缺少着色：主题装配补上 `MiuixTheme(controller)`，让 `dynamicGlassMaterial` 生效。
## 1.10 — 2026-11-19

**调整**

- 关于页不再自建 Scaffold，改用页面共用的 `Os4ListPage` 骨架：
  骨架新增「整屏背景」「固定大标题区」「顶栏采样源」三个参数，
  关于页只声明内容，不再自己搭顶栏 / 录制层 / 懒列表 / 安全区。
- 关于页的「查看源代码 / 开源许可」卡片改成玻璃卡（`GlassCard`：卡片透明 + `textureBlur`
  采样页面背景），与参考项目 InstallerX Revived 关于页一致；玻璃关闭时退回不透明卡片。
- 关于页现在**可以滚动收起**顶部内容：列表末尾用 `fillParentMaxHeight` 撑满一屏保证可滚，
  上滑时图标 / 标题 / 版本按不同斜率淡出（InstallerX 同款阈值）。
- 软件光栅化环境（模拟器 SwiftShader）上跳过关于页的 `textureBlur`：
  该渲染器不产出像素，跳过后退化成单色剪影 + 实字，版式仍可核对。

## 1.9 — 2026-11-19

**修复**

- 关于页顶栏的玻璃：真机上出现过一条边缘生硬的深色带，返回键左侧还有一小块错位的玻璃色斑。
  原因是顶栏的采样源（页面正文录制层）在顶栏这块区域里没有像素可采 ——
  该层刻意不垫底色，顶部又是图标留白。现在顶栏改采「渐变层 + 正文层」两层叠加
  （`ui/CombinedBackdrop.kt`），任何滚动位置都有真实像素。
- 关于页首帧卡片压住图标与软件名称、标题区不淡出：正文顶部那块透明占位的高度在第一帧是 0。
  改为先按 300dp 兜底，量到真实高度后再切换。

**调整**

- 「关于页顶栏要采哪一层」从页面里猜，改成 `PageTopBar(backdrop = …)` 显式传；
  其余页面不传，行为不变。

## 1.8 — 2026-11-18

**修复**

- 无素材时「开始生成」按钮的文字几乎看不见：禁用态改用 MIUIX 主按钮的标准配对
  （`disabledPrimaryButton` + `disabledOnPrimaryButton`），保留主操作按钮的蓝底语义。
  修前是灰底白字，浅色下对比度只有 1.12:1。
- 深色主题下页面底部出现一条比页面亮的色带：底部主操作条与玻璃录制层垫的底色
  取自 `pageColor()`（深色 #1E1E1E），而页面实际是 `surface`（#000000）。
  新增 `pageBaseColor()` 统一"页面真实底色"，录制层垫色、操作条遮挡、页面底色都改用它。
- 顶栏在软件光栅化环境（模拟器 SwiftShader）下，渐进模糊产出的是错位副本、内容直接压到标题上：
  这类渲染器上回退到 `GlassTopAppBar` 默认的渐变背景带（仍是库的官方路径），真机不受影响。
- 「GIF 编辑」的旋转滑块档位错误：`0..270` 配 3 个中间档实际得到 0/67.5/135/202.5/270°，
  非直角会把画面斜切。改为 0/90/180/270 四档。
- 动态取色下底栏与侧栏的选中项不是主色（用了 `onBackground`）：主题包装用的是
  `MiuixTheme(colors = …)` 重载，拿不到 `LocalColorSchemeMode`。改为在调用点显式传 `selectedColor`，
  并补上该重载缺失的 `LocalContentColor`。
- 下拉弹层的玻璃材质深色下与顶栏不同档：去掉显式传入的 `visuals`，回到库默认。
- 下拉弹层在一级页会采错坐标（页面录制层上报槽位坐标）：采样源改为
  `LocalRootBackdrop ?: LocalPageBackdrop`，与 Dialog / BottomSheet 同一条规则。
- 作品操作面板的行标题与面板标题不对齐（24dp + 16dp 叠成 40dp），且最后一行会贴住手势导航条。

**调整**

- 关于页改用 KernelSU / InstallerX Revived 的原生 `textureBlur + BlurColors + DstIn` 实现图标与软件名称，
  删除第九轮自制的渐变填充路径（`BgPalette` / `gradientFilled` / `accentBrush`）。
- 关于页图标去掉白底底板，改为纯单色剪影，容器 100dp、主体采样 132dp（可见主体约 98 × 49dp）。
- 抠图颜色色块补无障碍语义（单选角色、选中状态、颜色名），选中时不再改变尺寸、整行不再跳动。
- 新增仅调试包可见的「开始按钮配色对比」页（设置 → 主题 → 调试），
  用真实 `ProgressButton` 并排展示无素材禁用态的四种候选配色。正式按钮已采用 A 方案。

## 1.7 — 2026-11-18

**修复**

- 1200dp 横屏（侧栏展开）时侧栏压住一级页面的列表行：内容让位只算了侧栏宽度，
  漏掉了起始边的系统安全区（横屏时挖孔落在左侧 64dp），而侧栏自己是吃这段安全区的。
  竖屏挖孔在顶部、横向安全区为 0，所以上一轮没露出来
- 底部主操作条左端贴屏幕边缘、右端留 12dp：`railStartInset(base)` 的 `base` 参数没被使用
- 横屏下底部主操作条压在挖孔/侧边系统栏上：这一层不在 Scaffold 内容槽位里，拿不到 `innerPadding`，
  现在自己取横向安全区
- 宽屏展开侧栏时顶栏标题没有跟着内容让位，标题偏在内容区左侧

**调整**

- 关于页按 InstallerX Revived 的排版重做：入口不再带前置小图标，描述写在副标题里
- 应用图标与软件名称改为跟随背景色段上色（与背景共用同一个配色状态，不另起一套渐变）
- 「开源许可」与「第三方许可」合并为一个入口：同一页按「本项目 / 第三方组件 / 归属」分组，
  正文页共用；`Route.OwnLicense` 随之移除，NOTICE 收进 assets
- 尺寸统一：说明段落 16→28dp、预览与结果卡内边距 12→16dp、字幕输入框去掉多余横向内边距
  （字段自带 16dp）、许可正文 16→28dp、预览占位图标走 `IconSizes.Media`

## 1.6 — 2026-11-18

**新增**

- 关于页按 Miuix 官方示例重做：整块 OS3 动态渐变背景、88dp 图标 + 35sp 大标题 + 版本号，
  滚动时逐层淡出；下面三行入口（源代码 / 开源许可 GPL-3.0 / 第三方许可）。
  渐变背景移植自官方 example，Apache-2.0，见 NOTICE
- 「开源许可」新增 GPL-3.0 正文页（原文来自本仓库 LICENSE）

**调整**

- Miuix 基线升到 `3177a684`；`compileSdk` 随上游提到 37.2
- 宽屏侧栏的让位宽度改成量测期读取：侧栏展开/收起时不再整页重组、不再重排列表
  （`LocalRailInset` 从 `Dp` 改成 `() -> Dp`）
- 色板选色补上 `ToggleOn` 触觉反馈（对齐官方 `RadioButtonPreference` 的选中反馈），
  点已选中的颜色不重复反馈

**修复**

- 关于页顶栏下方出现一条灰白硬边（渐变没铺到状态栏下面）

## 1.5 — 2026-11-17

**修复**

- 作品操作面板的背景始终不模糊：弹层的采样区间整块落在录制层之外（页面在 Pager 里，
  页面内录制层上报的是槽位坐标，和弹层的真实坐标差整整一页宽）。弹层改用录在 Pager
  外面的录制层，并由最外层 Scaffold 绘制
- 进入二级页时底栏「闪没」、返回时又浮在退出中的二级页之上：底栏改为一级页自己的浮层，
  随一级页被新页盖住、被揭开（与官方实测行为一致，没有任何自定义过渡动画）
- 横屏进入二级页后再旋转屏幕，左侧导航会重新出现、内容也被让出一条空白：一级导航现在只看页面栈
- 横屏侧栏玻璃有一块平色和一条竖直硬边：录制层不再跟着侧栏收窄，改由页面内容让位
- 展开式侧栏下内容右侧白空一条：内容让位只让起始边
- 横屏折叠态顶部胶囊压住页面标题：标题透明度跟着折叠进度走（官方同款机制）
- 横屏工具页预览区是一块空白：预览高度按窗口高度封顶，占位提示不再被底部按钮压住
- 主题设置里「预测性返回动画」的下拉菜单下半截发灰：弹层背景改采样整页录制层
- 删除作品没有二次确认：改为带确认的对话框，破坏性按钮用红色

**调整**

- 横屏导航改为官方形态：宽度不到 1200dp 时是顶部居中的折叠胶囊，达到 1200dp 才展开成左侧面板
  （之前是强制常驻展开，还关掉了折叠入口）
- 一级页按返回直接回桌面，去掉了「退出 GIF 助手？」确认框（手势返回与返回键行为一致；
  在非第一个标签页按返回仍先回到第一个标签页）
- 删掉 `modalOverlayOpen` 状态：弹层画在最外层，永远在底栏之上

**其它**

- Miuix 基线升到 `d45ca181`
- 顶层补了一层 `Scaffold` 作为弹层宿主（官方 `GlassPageHost` 同款）

## 1.4 — 2026-11-16

**修复**

- 首页继续左滑、末页继续右滑时页面还会跟着动，现在两端不再回弹
- 二级页底部按钮上方有一层渐变，看起来像多了一条底色，已去掉
- 顶栏滚动时的模糊偶尔出现生硬边界，补上了渐变降级参数
- 下拉菜单恢复背景模糊（之前为了躲开显示异常，退成了一整块不透明底）

**调整**

- 退出确认改用系统对话框，带进出场动画
- 顶栏返回图标换成 Glass 图标集里的细箭头，和同栏其它图标的线宽一致
- 顶栏玻璃不再按深浅色分档，跟随库默认
- 应用图标改为白底黑主体
- 关于页的应用图标改用 squircle 圆角（不支持着色器的设备自动退回普通圆角）

**优化**

- 列表图标与文字的间距回到 8dp（之前多补了 4dp，和别的行不一致）
- 底栏高度改为读取库的常量，不再本地写死
- 作品页文件行改用官方的箭头行组件，不再自己拼箭头
- 设置里的「自定义颜色」改名为「动态取色」，副标题说明它从壁纸取色
- 精简页面副标题：同义重复的（如「水平镜像 / 左右翻转」）去掉；
  错误提示补上「换一个文件试试」这类下一步说明
- 去掉零调用的旧组件与重复的录制层构造

**版本**

- 依赖基线 Miuix `ac0af467`（与 1.3 相同，上游无更新）
- 新增 `miuix-squircle` 依赖

排查与清理记录见 `docs/os4-deep-audit.md`、`docs/os4-reuse-audit.md`。

## 1.3 — 2026-11-16

对齐 Miuix `ac0af467`（OS4 Glass），整体而非局部：底栏、顶栏、图标、内边距、宽屏。

**修复**

- **「预测性返回动画」选「无」之后，二级页面的进出完全没有过渡动画**（1.2 已发布版本的实际问题）。
  「无」的本意只是关掉预测性返回的手势预览，页面切换该照常走默认转场；1.2 把它映射成了
  `NavTransitions.None` —— 库对这个值的注释就是 "identity transform, instant swap"，
  等于把整条转场一起关掉了。现在改为 `NavTransitions.MiuixDefault`
- 预测性返回选「无」后，窗口的系统级开关仍由 `GifApplication` 用 hidden API 关掉，
  与页面转场互不影响
- 玻璃录制层补齐底色：`rememberLayerBackdrop` 现在先 `drawRect(pageColor())` 再录内容
  （对齐官方 `rememberBlurBackdrop`）。此前只录内容，卡片之间的空隙是透明的，
  顶栏那条模糊带上会有一部分区域原样透出内容，看着像一块"糊影"而不是柔和的过渡

**基线**

- Miuix 依赖从 `d8efe18` 前进到 `ac0af467`（含 `GlassNavigationRail`、新底栏间距规则）；
  `scripts/setup-miuix.sh` 的 `PINNED_REF` 同步更新
- 从该提交自行编译官方 example 并实机运行，按实际观感取参数（不再靠截图推测）

**界面**

- 底栏参数交还库：`defaultWindowInsetsPadding = true`、高度回默认 54dp、指示器与选中色用
  库的默认值。此前手算 `maxOf(24, inset+8)` 并关掉库的内边距，同一台设备上比官方高出 6~8dp
- 一级页切换改用库的 `springAnimateToPage` + `PagerNavigationSpringSpec`，
  松手吸附与点击切页共用同一套弹簧参数（原来是手写 EaseInOut，两种手感不一致）
- 滚动统一走 `pageScrollModifiers()`：`overScrollVertical` 把下拉过冲喂给顶栏，
  band 才会跟手；补上 `scrollEndHaptic` 的边界触感
- 顶栏大标题：宽屏下 `expandable = false`（居中窄标题），与官方一致
- **图标体系统一到官方 Glass 族**：10 个工具入口用定色档（`ConvertToGif` / `MotionPhoto` /
  `Album` / `Edit` / `Compress` / `Merge` / `GridView` / `VerticalSplit` / `ConvertFile` / `Play`），
  设置、关于、作品等次级入口用同族的细线档（`Glass.Light.*`），底栏用 `All` / `FolderOutline` /
  `SettingsOutline`。尺寸统一到 26dp（Glass 是 28dp 画布，需按视觉大小换算）
- 卡片之间补 12dp 下间距（官方是 `padding(horizontal = 12).padding(bottom = 12)`），
  分组边界终于看得出来
- 设置页大标题下加版本号副标题，对齐官方设置页的第二行
- 六页统一到一个页面骨架（`Os4ListPage`）：把「Scaffold + 玻璃顶栏 + 录制层 + 懒列表」
  固定成唯一写法，内边距由 `pageContentPadding()` 单点算，不再每页手写 `+2dp` 与固定 128dp
- 去掉设置页与工具页里的说明段落（玻璃失效提示、预测性返回副作用说明、
  三个"只有说明没有参数"的工具文案）：实现细节留在界面上只是噪音

**新增**

- 宽屏（≥840dp，或横向 ≥600dp）改用玻璃侧栏 `GlassNavigationRail`，底栏让位；
  侧栏叠放在内容之上（它自身是 `fillMaxSize` 的，放进 Row 会把内容挤成 0 宽）
- 删掉 `SegmentedControl`（全项目零调用的死代码）

**工程**

- 记录三条新的硬约束到 `AGENTS.md`：截图判断不了玻璃要真机/实机、二级页不该为底栏留白、
  侧栏必须叠放

## 1.2 — 2026-11-15

对准「完整 Miuix OS4 化」的第一批：主操作区、表面层级、底栏。

**修复**

- 生成中的进度填充画在按钮中间：父级 `Box` 是 `contentAlignment = Center`，
  进度块被水平居中（25% 的填充出现在 37.5%~62.5%），且不受圆角约束
- 生成中按钮显示成"禁用"：`busy` 时 `enabled` 为 false，轨道走禁用色，文字对比度极低

**界面**

- 主按钮换成 `ProgressButton`（自制进度轨道，填充画在 shape 内部，两端跟随圆角），
  文字色随填充扫过 45% 从 `onSecondaryContainer` 切到 `onPrimary`
- 生成中点击按钮 = 取消任务，文案「取消生成 · 45%」；取消不留半成品文件
- 底栏：左右 24dp、高度 60dp、指示器改主色（15%/22%）、选中图标与文字用主色
- 一级页切换改用 EaseInOut + 时长随距离增长的手动滚动，指示器与页面同时起步
- 「玻璃材质」收敛成**一个开关**：顶栏与底栏采样背景做模糊，关掉后仍保留浮起胶囊与阴影
- 所有下拉换成玻璃浮层（`GlassDropdownPreference`，和 InstallerX 的主题下拉一致）；
  弹层**不采样背景**，走库里的不透明底色 + 描边 + 投影，边缘清晰不发灰
- 顶栏返回键统一走 `PageTopBar(onBack = …)`，玻璃档下包成 `GlassIconButton`，
  尺寸/材质/投影从顶栏上下文继承，不再是页面里手写的裸图标按钮
- 作品页文件操作从「手搓居中弹卡」换成 `WindowBottomSheet`（拖拽手柄、下拉关闭、返回手势联动）
- 作品页空态改为居中图标 + 两行说明
- 分组标题改用库里的 `SmallTitle`（`subtitle` 字号、28dp 缩进），与卡片内文字对齐

**工程**

- 新增 `ui/GlassSupport.kt`：玻璃开关与能力检测的唯一入口（`LocalGlassEnabled`），
  页面不再自己调 `isRenderEffectSupported()`（此前 `App.kt`、`GlassScope`、`Widgets.kt` 各判各的）
- 新增 `ui/ProgressButton.kt`，移植自 compose-miuix-ui 生态（Apache-2.0，见文件头）
- 主按钮禁用态改用库自带的 `disabledPrimaryButton` / `disabledOnPrimaryButton`
- `AGENTS.md` 补两条硬约束（玻璃单一决策点、弹层不采样背景），并修正「不要用 TabRow」那条
  过于笼统的结论（库里三个页签组件都不是分段控件）

## 1.1 — 2026-11-15

OS4 Miuix 化的一版：界面骨架重做、补齐主题设置与预测性返回、换图标、换许可证。

**修复**

- 竖屏视频导出的 GIF 变成横屏：改为按「解码帧宽高 vs 编码宽高」判断解码器是否已经转过，
  而不是无条件按旋转元数据再转一次；预览与导出共用同一条定向/缩放路径
- 「分辨率（短边）」现在真的按短边缩放，此前实际按宽度算，横屏视频会偏小
- 动态照片预览会残留临时视频文件
- 二级页面转场没有圆角裁剪（`NavDisplay` 未传 `effects`，默认半径是 0）

**界面**

- 所有页面顶栏换成 OS4 玻璃顶栏（含渐进模糊带），API 33 以下或不支持 RenderEffect 时整页回落普通 Miuix
- 页面结构改为「内容录制层 + 顶栏作为兄弟节点」，避免玻璃采样到自己导致原生栈溢出
- 一级页改为 Pager 承载：底栏点击与左右滑动都有连续位移，不再是纯淡入淡出
- 主题切换改为逐色插值（53 个颜色角色，320ms），不再硬切
- 主操作区重做：进度画进按钮内部、去掉实心底栏、改用渐变遮罩；按钮底部留白按屏幕圆角几何推导
- 设置页只保留「主题」「关于」两个入口，主题相关收进独立页

**新增**

- 独立主题设置页：主题模式（下拉）、AMOLED 纯黑背景、自定义颜色、玻璃材质
- 预测性返回：五档动画（Miuix / AOSP / 缩放 / 经典 / 无）+ 缩放档的退出方向；
  选「无」会通过 HiddenApiBypass 把系统级 `enableOnBackInvokedCallback` 置 false（API 34+）
- 自适应应用图标：前景 / 背景 / 单色三层 + `roundIcon`，白底蓝图，HyperOS 主题图标可用

**工程**

- 许可证由 Apache-2.0 改为 **GPL-3.0**（1.0 及更早版本仍按 Apache-2.0 发布）
- Miuix 跟进到 `d8efe18`（折叠态顶栏标题居中、band 渐变修正）
- 新增依赖：`miuix-material`、`org.lsposed.hiddenapibypass:hiddenapibypass:6.1`
- 删除无引用的 `ic_launcher_background.xml`

## 1.0 — 2026-11-14

首个公开发布版本。

**功能**

- 创建：视频转 GIF（截取片段、自定义分辨率/帧率/色数，防抖、色度抠图）、动态照片转 GIF、图片合成 GIF
- 处理：GIF 编辑（旋转/镜像/翻转/倒放/居中裁剪/字幕/去水印）、压缩、拼接、拼图（2~4 格，列数可选）
- 输出：GIF 分解为逐帧 PNG 打包、GIF 转 MP4、GIF 播放（调速与逐帧）

**界面**

- 基于 Miuix 重写：页面 #F7F7F7 + 卡片纯白、卡片内无分割线、单列行式列表、16dp 圆角主按钮
- 静态配色与 Monet 动态取色两套主题，明暗双模式
- OS4 玻璃浮动底栏，低端机可关闭
- 主题 / 动态取色 / 玻璃开关持久化
- 关于页、开源许可（Apache-2.0 全文随包内置）、锚定下拉弹窗（水印位置、宫格列数）
- 作品页条目改为点击弹操作面板（保存 / 分享 / 删除）

**工程**

- 包名 `com.ayang.giftoolkit`
- `scripts/setup-miuix.sh`：一键准备 Miuix 源码并打上游补丁
- 修复：拼图列数参数此前硬编码为 2，界面改了也不生效
