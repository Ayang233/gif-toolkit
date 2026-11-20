# MIUIX 基线升级交付报告（2026-11-20）

> 本报告记录一次完整的基线升级：`3177a684` → `5ff41ba9`，以及升级后按"不许自造轮子、全部对齐最新基线"
> 要求做的彻查与修正。配套文档：
> - `docs/miuix-baseline-3177a684-to-5ff41ba9-diff.md` — 34 个提交的逐项差异（任务 A）
> - `docs/upgrade-prep-customization-inventory.md` — 升级前定制面/耦合点清单（任务 B）
> - `docs/audit-self-built-wheels.md` — 自造轮子彻查（任务 C）
> - `docs/audit-baseline-alignment.md` — 样式/默认值对齐审计（任务 D）
> - `docs/miuix-baseline-maintenance.md` — 长期维护机制（第四阶段交付）

---

## 一、第一阶段要求的 10 项交付

| # | 项目 | 结果 |
| --- | --- | --- |
| 1 | 官方最新 main 的 commit | **`5ff41ba9`**（`fix: Align GlassTopAppBar progressive blur insets with systemBars`，拉取时间 2026-11-20） |
| 2 | 原项目基线 commit | **`3177a684`**（`library: Refine glass floating action button`，2026-11-18）；注意 `scripts/setup-miuix.sh` 当时实际 pin 的是更旧的 `d45ca181`，三处已在本轮统一 |
| 3 | 升级后的项目 commit | 见文末「交付状态」——工作区改动已完成并验证，提交动作待确认后执行 |
| 4 | 官方 main 更新了什么 | 34 个提交 / 198 个文件。核心：blur 管线重写（7 个提交：`BackdropEffects`/`DrawBackdropModifier`/`Shaders`/`TextureEffect`）、`GlassTopAppBar` 渐进模糊曲线 0.5→0.25 + 静止时跳过采样 + insets `statusBars→systemBars`、`GlassNavigationBar` ≤16dp 档外边距 28→20dp + 横向安全区 padding、`GlassNavigationRail` 形变重写、`GlassTabRow` 动画、`GlassSearchOverlay` 移入 `miuix-glass`、`dynamicGlassMaterial`（Monet 玻璃 tint）、`ScrollBar` 状态化重构；`miuix-material`/`miuix-icons`/`miuix-nav` 三个模块**零改动** |
| 5 | 哪些更新已成功合并 | **全部 34 个提交**（以 pin 前移 + composite build 的方式吸收，不涉及代码合并） |
| 6 | 哪些文件发生冲突 | 本项目与上游没有同文件冲突（定制不在上游源码内）。**语义冲突 1 处**：`ui/PageChrome.kt` 复刻了 `GlassNavigationBar` 的未公开外边距公式（28dp→20dp） |
| 7 | 冲突如何解决 | `rememberBottomBarClearance()` 的 `28.dp` 改为 `20.dp`，并在注释里写明触发条件（仅导航栏 ≤16dp 的真机会暴露，本机 420dpi 手势导航走另一档，模拟器看不出来） |
| 8 | OS4 与 Glass 化成果是否保留 | **全部保留**：录制层兄弟节点结构、三类颜色分工、GlassSupport 单点决策、弹层采样源、导航/底栏/Rail 结构、关于页骨架、图标规范均未改动结构；仅按上游默认值变化做了参数级适配 |
| 9 | 原有功能测试结果 | 编译：debug + release（R8/lintVital）均通过；运行：安装启动、Tab 切换、工具页参数、下拉弹层、作品操作面板、关于页滚动、release 包装机均正常；logcat 无本应用 `FATAL`/`SIGSEGV` |
| 10 | 尚未解决的问题及风险 | 见文末「需要人工确认 / 后续建议」：真机玻璃观感、API 31/32 降级路径、真机大字体下的底栏预留高度 |

### 视觉回归证据（模拟器 `-gpu host`）

- 升级前后（`3177a684` vs `5ff41ba9`），静态 / Monet × 明暗 / 玻璃关 共 4 配置 × 5 页面 + 横屏截图：
  **除状态栏时钟外逐像素一致**。
- 修正阶段（见下）之后的差异都在预期内：Monet 下玻璃材质 tint 生效（约 3.3% 像素）、
  关于页 `textureBlur` 生效（图标/标题跟随渐变），其余页面仍与升级前逐像素一致。

---

## 二、升级后追加的要求与处理

### 要求 1：「我的作品」点击弹出的操作面板还是老样式

**原因（两层）**

1. **材质层（真因）**：`5ff41ba9` 把所有玻璃组件的默认材质换成 `dynamicGlassMaterial(...)`，
   它只在 `MiuixTheme.isDynamicColor` 为真时加 tint；而本项目用 `MiuixTheme(colors=…)` 装配主题，
   不提供 `LocalColorSchemeMode` → `isDynamicColor` 恒 false → 新材质整体失效。
2. **写法漂移**：面板没有官方示例的头部动作，行覆盖了库默认 `insideMargin`，内容缺滚动容器。

**改法**

- `ui/AnimatedTheme.kt`：外层补 `MiuixTheme(controller = controller)`（提供 `LocalColorSchemeMode`），
  内层继续 `MiuixTheme(colors = animated)` 做逐色过渡。Monet 下弹层 / 顶栏按钮 / 底栏 / Rail 的材质 tint 恢复。
- `screens/LibraryScreen.kt`：按官方 `5ff41ba9` 示例重写 `FileActionSheet`——
  头部 `endAction = GlassIconButton { Icon(MiuixIcons.Glass.Close) }`、
  内容用 `Column(fillMaxWidth + heightIn(max) + verticalScroll + navigationBarsPadding + padding(bottom=24))`、
  行不再覆盖 `insideMargin`。

### 要求 2：关于页图标与软件名称没有跟随背景渐变

**原因**：项目自造的 `isSoftwareRenderer` 黑名单包含 `ranchu/goldfish`，
把所有模拟器（**包括 `-gpu host`**）判成软件渲染 → `textureBlur` 整段被跳过 →
图标退成主题色剪影、标题是实色字。（这不是 MIUIX 的限制。）

**改法**

- 删除 `isSoftwareRenderer`，能力判断交给库（`GlassTopAppBar` 用 `isRuntimeShaderSupported()` 决定 AGSL 路径）。
- `isGlassSupported()` 改为 `isRenderEffectSupported() && isRuntimeShaderSupported()`：
  顺带修掉 API 31/32 上"`bgModifier` 录制层根本不存在 → `textureBlur` 采空 → 图标/标题整块消失"的隐患。
- 关于页 `textureBlur` 参数按官方对齐：标题 `shape = RoundedCornerShape(16.dp)`、`blurRadius = 100f`
  （= `BlurDefaults.MaxBlurRadius`）、显式 `noiseCoefficient`；图标保留"也跟随渐变"，`shape = RoundedCornerShape(24.dp)`。

**验证**：`-gpu host` 模拟器上关于页图标与标题已随渐变色（紫/粉）变化；顶栏渐进模糊带也真实生效。

### 要求 3：其他所有内容是否还有自造轮子 / 是否都按最新基线

彻查结论（详见两份审计报告）：**不是"到处造轮子"**，列表行、Card、开关、滑块、下拉、弹层、
底栏、Rail、顶栏、转场、Pager 手势、滚动过冲全部走官方组件。

真正算"自造轮子"并已修掉的 6 处：

| # | 位置 | 之前 | 现在 |
| --- | --- | --- | --- |
| W1 | `ui/SystemInsets.kt` | 自己读 `RoundedCorner` + framework dimen 算屏幕圆角（20 行） | 库的 `rememberNavSystemCornerRadius()`（官方 example 同款） |
| W2 | `ui/Widgets.kt` 预览舞台、`screens/ToolScreen.kt` GIF 预览 | `clip(RoundedCornerShape(12.dp)) + background(...)` | 库的 `squircleSurface(...)`（连续曲率，官方 Card/Button 同源） |
| W3 | `screens/ToolScreen.kt` 抠图颜色 | 手抄 `BasicComponent` 行规格（56dp/16dp/headline1）+ 自拼 Row | `BasicComponent(title, endActions = { 色块... })` |
| W4 | `App.kt` 转场 | 显式写 `enableCornerClip=true`、`dimAmount=0.5f`（与库默认相同） | 删掉，吃库默认值 |
| W5 | `ui/ProgressButton.kt` | 圆角/内边距字面量 16dp/(16,12) | `ButtonDefaults.CornerRadius` / `ButtonDefaults.InsideMargin` |
| W6 | `ui/Widgets.kt` 主操作条进度指示器 | 16/2/1.5dp | `ProgressIndicatorDefaults` 的 20/2/2dp |

保留但**不算轮子**（官方没有公开 API，只能保留）：

- `CombinedBackdrop`、`rememberPageBackdrop`、`pageScrollModifiers`、`pageContentPadding`、
  宽屏阈值（840/600/1.2/1200）、关于页渐变背景（`ui/bg/*`）——都是官方 **example** 里的实现，
  库没有发布成公共 API，只能按 example 复刻。已在长期维护文档 §4.2 的 C 表登记。
- `PrimaryActionBar` + `safeBottomGap`、`pageColor()` / AMOLED 卡片色、`ProgressButton` 的进度填充、
  `AnimatedTheme` 的逐色过渡 —— 官方没有对应能力或属于本项目业务（GIF 编解码、任务进度等）。

默认值/尺寸对齐：`GlassTopAppBar` / `GlassNavigationBar` / `GlassNavigationRail` / Card / BasicComponent
的默认值逐项核对过，除本报告提到的 20dp 与 `dynamicGlassMaterial` 外，本项目**未发现**与 `5ff41ba9` 冲突的硬编码常量。

---

## 二·补、「以 MIUIX 自身为基准」的对齐（第三轮）

用户的要求：作品操作面板要按 **MIUIX OS4 / OS4 Glass 的原生设计**来，不要拿别的 App 当标准。
做法：把 MIUIX 官方 example 在基线 `5ff41ba9` 上编译成 APK（`miuix/example/android`，
产物 `Miuix-v1.1.0(85)-debug.apk`），装到模拟器上逐屏取真值，再对照改 GifHelper。

### 官方 example 真值下看到的差异

| 项 | MIUIX 官方 example | GifHelper 原状 | 处理 |
| --- | --- | --- | --- |
| 列表滚动条 | 每个列表页都挂 `VerticalScrollBar`（`MainPage.kt:341-346`、`SettingsPage.kt:506-511`、`AboutPage.kt:447-452`） | **全项目 0 处** | **已补**：`Os4ListPage` 统一加（轨道 padding = `contentPadding`，`topAppBarScrollBehavior` 联动） |
| 行首图标 | Glass 图标统一 `Modifier.size(28.dp)`（`GlassPage.kt:325/359/372`、`IconPage.kt:266`、`MainPage.kt:224/254`、`PullToRefreshPage.kt:121`） | `IconSizes.Row = 26dp` | **已改 28dp**（`AGENTS.md` 第 10 条同步） |
| 折叠 Rail 与顶栏标题 | `centerObstructionStart`（`AppContent.kt:226-232` → `PageUtils.kt:250/270/291`） | 未接，横屏折叠态胶囊可能压住居中标题 | **已接**：新增 `LocalTopAppBarCenterObstructionStart`，两档顶栏都传 |
| 操作面板内容结构 | 玻璃弹层示例：行直接落在面板上；非玻璃 `OverlayBottomSheet` 示例才 `SmallTitle + Card + Preference` 分组 | 行直接落在面板上（对） | 保留；收尾按官方 `BottomSheetSection.kt:258-266` 补「导航条 + captionBar」留白 |
| 操作面板头部动作 | `startAction` / `endAction` 是可选参数（官方 Action Sheet 示例用 Cancel/Confirm 两个 TextButton） | 无 | 保留无头部按钮：三个动作都在行内，靠拖拽/外部点击/返回键关闭 |
| 行内边距 / 行高 / 图文间距 | 全部由 `BasicComponentDefaults` 决定 | 一致 | 保留（不覆盖 `insideMargin`） |

### 一条重要的口径修正：Glacier 不是标准

中间曾参考用户提供的 Glacier 截图（`GlassBottomSheet` + `BasicComponent` 行 + **包名脚注**）实现过一版。
按用户要求复核后确认：**Glacier 只是下游应用的一种组合，不是 MIUIX 的设计规范**。
因此把它带进来的两处**非 MIUIX 元素**删掉了：

1. **脚注行**（文件大小 / 导出时间）——MIUIX 官方弹层示例没有这个结构，已删除；
2. **头部玻璃 X**——官方参数可选，且本项目动作都在行内，已删除。

行图标最终取 **28dp**（MIUIX example 的 Glass 图标统一尺寸），不是 Glacier 的 24dp。
后续所有"像不像 OS4"的判断，一律以**编译出来的 MIUIX 官方 example APK** 为准。

### 保留的差异（有明确理由，不是"没对齐"）

1. **底栏不进 `Scaffold.bottomBar` 槽**：官方 `GlassPage` 把导航放 `bottomBar`，GifHelper 用
   "一级页浮层 + `LocalBottomBarClearance`"，因为底栏属于一级页、要跟着 Pager 一起被二级页盖住
   （`AGENTS.md` 第 12 条）。改槽位会破坏进入/退出二级页时的动画语义。
2. **玻璃关闭时顶栏用实色**：官方 `BlurredBar` 在非玻璃档仍铺 `textureBlur` 带；
   GifHelper 的"玻璃开关"语义是"彻底关掉模糊"（低端机降级手段，`AGENTS.md` 第 3 条）。
3. **关于页图标也做 `textureBlur`**：官方图标不做（88dp 白底 + 74dp launcher）；
   本项目按用户要求"图标跟随渐变"保留模糊，参数已与官方对齐（`RoundedCornerShape(24.dp)` / 100f）。
4. **操作面板不做 `Card` 分组**：官方玻璃弹层示例本身就不分组；只有非玻璃示例才分组。

---

## 三、本轮改动的文件清单

**代码（9 个）**

| 文件 | 改动 |
| --- | --- |
| `ui/PageChrome.kt` | 底栏预留外边距 28dp→20dp（对齐上游 `9413f690`）；列表页挂官方 `VerticalScrollBar` |
| `ui/Adaptive.kt` | 新增 `LocalTopAppBarCenterObstructionStart`（折叠 Rail 绕行居中标题） |
| `ui/GlassSupport.kt` | 删除 `isSoftwareRenderer`；`isGlassSupported()` = RenderEffect ∧ RuntimeShader |
| `ui/GlassScope.kt` | 顶栏 `progressiveBlur = true`（能力交库判断） |
| `ui/AnimatedTheme.kt` | 外层补 `MiuixTheme(controller)`，恢复 `isDynamicColor` |
| `ui/SystemInsets.kt` | 删自带屏幕圆角实现（W1） |
| `ui/Widgets.kt` | squircleSurface（W2）、`rememberNavSystemCornerRadius`（W1）、进度指示器默认值（W6） |
| `screens/AboutScreen.kt` | 不再按渲染器降级；`textureBlur` 参数对齐官方 |
| `screens/LibraryScreen.kt` | 操作面板按官方示例重写（头部玻璃按钮 + 滚动容器 + 默认行内边距） |
| `screens/ToolScreen.kt` | 抠图颜色行改 `BasicComponent`（W3）、GIF 预览改 `squircleSurface`（W2） |

**配置/脚本（4 个）**：`scripts/setup-miuix.sh`（pin → `5ff41ba9`）、`build.gradle.kts`（注释）、
`AGENTS.md`（基线、去耦说明、模拟器要求）、`README.md`（已知限制与截图说明）。

**文档（8 个）**：新增 5 份（差异分析、定制面清单、自造轮子审计、基线对齐审计、长期维护机制），
修改 3 处（迁移知识库 README、05 章底栏公式、CHANGELOG 待定）。

---

## 四、交付状态

### 已完成

- 第一阶段审计（本地/GitHub/上游三边状态、基线 commit、OS4/Glass 定制面清单、升级前保护点 tag
  `pre-miuix-upgrade-3177a684` + 旧 APK 备份）。
- 基线升级到 `5ff41ba9`，debug/release 双构建通过，模拟器 4 配置 × 5 页面 + 横屏回归对照通过。
- 两个用户可见问题的修复（作品面板样式、关于页跟随渐变）。
- 自造轮子彻查 + 6 处替换，样式/默认值对齐审计，长期维护机制文档。

### 已验证

- `./gradlew :assembleDebug` / `:assembleRelease`（含 R8、lintVital）通过。
- 装机启动、Tab 切换、工具页参数与下拉、颜色选择行、作品面板、关于页（含滚动）、release 包运行。
- logcat 无本应用崩溃/异常；`textureBlur` 与 AGSL 渐进模糊在 `-gpu host` 上真实生效。
- 升级前后像素对照：非预期差异只有 Monet 新材质 tint 与关于页 blur 生效。

### 尚未完成（明确不做，或需独立任务）

- **真机玻璃观感**：本轮只在 `-gpu host` 模拟器上验证；真机上的模糊半径观感、帧率、不同 GPU 的
  AGSL 行为仍需实机确认。
- **API 31/32 降级路径**：`isGlassSupported()` 已收紧（这类设备会关闭玻璃），但没有 31/32 设备可实测。
- **CHANGELOG 与版本号**：按你的要求**先不升 1.11**；提交信息、tag 与发版节奏待你确认后再定。
- 历史审计文档（`docs/os4-round*-audit.md`）里的基线描述是**当时的证据快照**，没有回改。

### 需要人工确认

1. **版本号/发布**：是否把本轮定为 `1.11`（versionCode 13）+ CHANGELOG + 打 tag 发 Release。
2. **是否提交并推送**到 `github.com/Ayang233/gif-toolkit`（当前工作区改动都在本地，未提交、未推送）。
3. **真机观感**：如果真机与 `-gpu host` 模拟器不一致，请把差异页截图发我，我按真机对齐。
4. **作品面板的关闭按钮**：现在头部右侧多了一颗官方样式的玻璃「X」，如果产品上不想要，可只保留滚动容器与行内边距两项改动。

### 后续建议（按优先级）

1. 落地 `scripts/check-upstream.sh`（基线更新监测）与 `scripts/visual-smoke.*`（截图回归），
   成本各约 10 分钟 / 2 小时，见维护文档 §7。
2. 真机跑一遍「十个工具 + 四配色 + 玻璃开关」，作为发版前门禁。
3. 视需要加 GitHub Actions 构建验证（顺带验证"新 clone 能否恢复"）。
4. 若上游把 example 里的 `rememberBlurBackdrop` / `pageContentPadding` 发布成公共 API，
   把 C 表的复制件删掉改为调用。
