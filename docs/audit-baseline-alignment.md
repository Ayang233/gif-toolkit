# GifHelper × MIUIX `5ff41ba9` 样式 / 默认值对齐审计

> 性质：**只读审计**。除本文件外没有改动任何源码。
> 工作区基线：`C:\Users\10487\WorkBuddy\gif\miuix` @ `5ff41ba9`（2026-11-20，`fix: Align GlassTopAppBar progressive blur insets with systemBars`）；
> GifHelper 工作区（HEAD `060c804` + 未提交的 5ff41ba9 升级改动）。
> 所有结论都带两侧 `文件:行号`；行号取自审计时的实际文件内容。

---

## 0. 结论摘要（先看这 10 条）

1. **本项目 1.10 实际编译用的基线比文档写的更老。** `git show HEAD:scripts/setup-miuix.sh` 里 `PINNED_REF="d45ca181"`（`d45ca181` 是 `3177a684` 的祖先），而 AGENTS.md / 迁移知识库写的是 `3177a684`。工作区已把它改成 `5ff41ba9`。→ 用户看到的"老样式"首先是**基线本身落后**（见 §1）。
2. **「作品」操作面板缺了 5ff41ba9 文档示例的头部长相**：官方现在用 `endAction = { GlassIconButton { Icon(MiuixIcons.Glass.Close/Ok) } }`，本项目 `FileActionSheet` 完全没有 `endAction`（`LibraryScreen.kt:244-250`）。官方写法见 §2.3，可直接照抄的整函数见 §2.4。
3. **弹层内容结构也没对齐**：官方把内容放进 `Column(fillMaxWidth().heightIn(max=…).verticalScroll(rememberScrollState()).navigationBarsPadding?/padding(bottom=24.dp))`（`GlassBottomSheetDemo.kt:88-110`），本项目是 3 个裸 `BasicComponent` + 末尾一个 `Spacer`（`LibraryScreen.kt:254-280`），并且用 `insideMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp)` 覆盖了库默认的 16dp（`BasicComponentDefaults.InsideMargin`，`Component.kt:287-292`）。
4. **⭐ 最关键的一条：5ff41ba9 的"动态取色玻璃材质"在本项目整体失效。** `GlassBottomSheetDefaults.material()` / `GlassDialogDefaults.material()` / `GlassTopAppBarDefaults.buttonMaterial()` / `GlassNavigationBarDefaults.material()` / `GlassNavigationRailDefaults.material()` 在 5ff41ba9 里都改成了 `dynamicGlassMaterial(...)`（`GlassBottomSheetSurface.kt:31-37`、`GlassDialogSurface.kt:32-37`、`Glass.kt:269-279`、`GlassNavigationBar.kt:162-166`、`GlassNavigationRail.kt:233-237`），只有当 `MiuixTheme.isDynamicColor == true` 时才加 tint 层；而 `isDynamicColor` 读的是 `LocalColorSchemeMode`（`MiuixTheme.kt:87-96`），该 local 是 `internal` 且**只由 `MiuixTheme(controller=…)` 重载提供**（`MiuixTheme.kt:34-43`）。本项目走的是 `MiuixTheme(colors = animated)`（`AnimatedTheme.kt:63`），所以 `isDynamicColor` 恒为 `false` → **Monet 下弹层/顶栏按钮/导航的新材质一律不生效**。修法只要 3 行（§2.6），既不丢本项目逐色过渡，也能让默认值真正生效。
5. **⭐ 关于页图标/软件名称"不跟随渐变"的根因是项目自己加的渲染器黑名单，不是 MIUIX 限制。** 旧代码 `val blurEnabled = glassEnabled && !isSoftwareRenderer`，而 `isSoftwareRenderer` 把 `Build.HARDWARE` 里含 `ranchu/goldfish` 的**所有模拟器（包括 `-gpu host`）**都判成软件渲染 → `textureBlur` 整段被跳过，图标/标题退回主题色实字。工作区已删除该判据（`GlassSupport.kt` / `GlassScope.kt` / `AboutScreen.kt` 的 diff），方向正确。详见 §3.2。
6. **关于页还有一条 P0 隐患没修**：`blurEnabled` 仍只跟 `isRenderEffectSupported()`（SDK≥31，`RenderEffect.android.kt:12`）走，但采样源 `BgEffectBackground` 只在 `isRuntimeShaderSupported()`（SDK≥33，`RuntimeShader.android.kt:18`）时才挂 `bgModifier` 录制层（`BgEffect.kt:159-182`）。API 31/32 设备上：录制层不存在 → `textureBlur(DstIn)` 采到空 → 图标与标题**整块不可见**。官方 `rememberBlurBackdrop()` 用 `isRuntimeShaderSupported()` 兜底返回 `null`（`PageUtils.kt:299-308`）。修法见 §3.4。
7. **关于页 `textureBlur` 参数与官方有细节差异**：官方标题 `shape = RoundedCornerShape(16.dp)`、`blurRadius = 100f`、`noiseCoefficient = BlurDefaults.NoiseCoefficient`（`AboutPage.kt:273-282`）；本项目 `shape = RectangleShape`、`blurRadius = 150f`（`AboutScreen.kt:244/246`、`:271-272`）。150f 会被 `BlurDefaults.MaxBlurRadius = 100f` 夹紧（`BlurDefaults.kt:155`），所以半径等价，**只有 shape 不一样**。
8. **图标策略与官方不同（不是 bug，但要知道）**：官方图标**不做** `textureBlur`，是 88dp 白色圆角底 + 74dp launcher 图（`AboutPage.kt:242-261`）；本项目图标做 `textureBlur` + `ColorFilter.tint(onBackground)`（`AboutScreen.kt:232-255`，参考 KSU / InstallerX）。用户明确要"图标跟随渐变"，所以**保留本项目的做法**，但样式参数按官方标题对齐（§3.4 给了两套代码：A=官方原样，B=本项目保留图标模糊）。
9. **已对齐、不要动的**：`rememberBottomBarClearance()` 的 ≤16dp 档已从 28dp 同步到 20dp（`PageChrome.kt:79` vs `GlassNavigationBar.kt:318-323`）；返回键 glass/extended 分档与官方 `BackNavigationIcon` 逐行一致（`GlassScope.kt:210-234` vs `component/BackNavigationIcon.kt:29-45`）；`GlassTopAppBar` 的 `bandBrush` / `bandOverhang` 显式传参正确（`GlassScope.kt:259,284`）。
10. **P2 遗留**：`GlassTopAppBar` 5ff41ba9 新增的 `centerObstructionStart`（`GlassTopAppBar.kt:293`）本项目没接，手机横屏折叠 Rail 时顶部胶囊可能压住居中标题（官方在 `AppContent.kt:226-232` 算、`PageUtils.kt:270` 传）；动效里仍有 `tween(320)` / `tween(300)` 这类硬编码（`AnimatedTheme.kt:49`、`Widgets.kt:547`）。

---

## 1. 基线事实核对（先定坐标）

### 1.1 实际 pin 与文档不一致

| 出处 | 记录 | 证据 |
| --- | --- | --- |
| 1.10 HEAD 的 setup 脚本 | `PINNED_REF="d45ca181"` | `git -C GifHelper show HEAD:scripts/setup-miuix.sh:24` |
| 工作区（升级中） | `PINNED_REF="5ff41ba9"` | 工作区 `scripts/setup-miuix.sh:23` |
| AGENTS.md（HEAD 版本） | 基线 `3177a684` | `git -C GifHelper show HEAD:AGENTS.md` |
| 迁移知识库 README（工作区） | 证据基线 `3177a684`，当前 pin `5ff41ba9` | `docs/miuix-os4-migration-kit/README.md:7-11` |

`d45ca181` 是 `3177a684` 的祖先（`git merge-base --is-ancestor d45ca181 3177a684` → 0），也就是说 **1.10 实机观感取的是比文档更老的默认值**。`d45ca181..5ff41ba9` 共 48 个提交。这解释了"文档说已经对齐、实机却不是"的落差。

### 1.2 5ff41ba9 上与本项目相关的默认值改动（逐项）

只列**本项目吃默认值**的组件（改默认值 = 本项目观感自动变）。旧值取 `d45ca181` / `3177a684`（这两版在这些点上相同，除特别标注）。

| 组件 | API 默认值 | 旧值 | 5ff41ba9 | 证据（miuix） | 本项目吃默认值的位置 |
| --- | --- | --- | --- | --- | --- |
| GlassBottomSheet | `GlassBottomSheetDefaults.material()` | `GlassMaterials.dialogGlass(isDark)` | `dynamicGlassMaterial(dialogGlass(isDark), tint = surfaceContainer, alpha = 0.72f)`（仅 `isDynamicColor` 时加 tint） | `GlassBottomSheetSurface.kt:31-37` | `LibraryScreen.kt:244-250`（不传 material） |
| GlassBottomSheet | `stroke()` | `MiddleLight/MiddleDark` | 未变 | `GlassBottomSheetSurface.kt:41-45` | 同上（吃默认） |
| GlassBottomSheet | `insideMargin` | `DpSize(24.dp, 0.dp)` | 未变 | `BottomSheetContentLayout.kt:934` | 吃默认；行内又叠了 `insideMargin`（`LibraryScreen.kt:254`） |
| GlassBottomSheet | 内容区几何 | — | `padding(horizontal = insideMargin.width).padding(bottom = insideMargin.height)` 包住 拖拽把手+标题+content | `BottomSheetContentLayout.kt:680-700` | 决定行左边界 = 24dp（见 §2.2） |
| GlassDialog | `GlassDialogDefaults.material()` | `dialogGlass(isDark)` | `dynamicGlassMaterial(dialogGlass(isDark), alpha = 0.72f)` | `GlassDialogSurface.kt:32-37` | `LibraryScreen.kt:114-124`（不传 material） |
| GlassDialog | `stroke()` / `ScrimColor` | `SmallLight/SmallDark` / `0x33000000` | 未变 | `GlassDialogSurface.kt:28,41-45` | 吃默认 |
| GlassDialogButton | `Primary` 禁用态 contentColor | 深色 `onPrimary α 77/255` | 动态取色下 `α 0.6f` | `GlassDialogButton.kt:125-137` | `LibraryScreen.kt:132-145`（Destructive） |
| GlassIconButton | 顶栏内按钮 backdrop 捕获 | 无 | 新增 `onButtonBackdropConsumerChange` 计数（仅置顶栏内生效） | `GlassIconButton.kt:81-88` | `GlassScope.kt:225-226` 顶栏返回键、`GlassIconButton` 在页面里用 |
| GlassIconButton | `fill` | static `#2C2C2C/#FFF` | 动态取色优先 `surfaceContainerHigh` | `GlassTopAppBar.kt:125-129` | 吃默认 |
| GlassIconButton | `material` | `puredThinGlass(isDark)` | `dynamicGlassMaterial(puredThinGlass(isDark))` | `GlassTopAppBar.kt:133` | 吃默认 |
| GlassTopAppBar | progressive band 曲线 | `ProgressiveBlur.Top`（curve 0.5） | `ProgressiveBlur.Top.copy(curve = 0.25f)` | `GlassTopAppBar.kt:460` | `GlassScope.kt:260-289`（本项目不传 curve） |
| GlassTopAppBar | 背景带 inset | `WindowInsets.statusBars` | `WindowInsets.systemBars`（即 5ff41ba9 的 headline commit） | `GlassTopAppBar.kt:556` | 吃默认 |
| GlassTopAppBar | `centerObstructionStart` | 无 | 新增参数，默认 `Dp.Unspecified` | `GlassTopAppBar.kt:293` | `GlassScope.kt:260-289`（**没传**，见 §6） |
| GlassNavigationBar | `material()` | `puredThinGlass(isDark)` | `dynamicGlassMaterial(puredThinGlass(isDark))` | `GlassNavigationBar.kt:162-166` | `App.kt:321-343`（吃默认） |
| GlassNavigationBar | `floatingMargin`（内部） | `≤16dp → 28dp` | `≤16dp → 20dp` | `GlassNavigationBar.kt:318-323` | `PageChrome.kt:73-86`（已同步 20dp ✅） |
| GlassNavigationBar | 隐藏动效 | — | 透明度 → 缩放换算 `NAV_HIDE_SCALE=0.6` / `NAV_HIDE_BLUR_DP=18` / `NAV_SHOW_DELAY_MS=100` | `GlassMotion.kt:360-367` | 吃默认（`App.kt` 不传） |
| GlassNavigationRail | `material()` / `stroke()` | `puredThinGlass` / theme | 动态材质 | `GlassNavigationRail.kt:217-231` | `App.kt:399-413`（吃默认） |
| Blur | `MaxBlurRadius` | `100f`（d45ca181 就是这个提交） | `100f` | `BlurDefaults.kt:155` | `AboutScreen.kt:246/272` 传 150f（被夹到 100f） |
| Blur | `NoiseCoefficient` | `0.0045f` | 未变 | `GlassDefaults`（`NoiseCoefficient = 0.0045f`），`BlurDefaults.kt:148-149` | 吃默认 |

**结论**：5ff41ba9 对"弹层观感"的默认值改动只有一处 —— **`dynamicGlassMaterial` 的动态 tint**。它在本项目被主题装配方式关掉了（§2.6）。其余"新写法"是**调用写法**层面的（头部 `GlassIconButton`、滚动容器），见 §2。

---

## 2. ★ 抱怨 1：作品页操作面板（GlassBottomSheet）还是老样式

### 2.1 本项目现状

`src/androidMain/kotlin/com/ayang/giftoolkit/screens/LibraryScreen.kt`

```text
233-282  private fun FileActionSheet(...)
244-250    GlassBottomSheet(
244            show = visible,
245            title = file.name,
246            backdrop = backdrop,
247-249        onDismissRequest / onDismissFinished
250          ) {
254-275      val rowMargin = PaddingValues(horizontal = 0.dp, vertical = 16.dp)
255-275      3 × BasicComponent(title, startAction, insideMargin = rowMargin, onClick)
280          Spacer(Modifier.navigationBarsPadding().height(8.dp))
```

问题点（按对观感的影响排序）：

| # | 现状 | 官方 5ff41ba9 | 影响 |
| --- | --- | --- | --- |
| 1 | 无 `endAction`（头部右侧空着） | `endAction = { GlassIconButton(onClick) { Icon(MiuixIcons.Glass.Ok, …) } }`（`GlassBottomSheetDemo.kt:78-86`） | 头部缺一颗 44dp 玻璃胶囊按钮，是最显眼的"老样式" |
| 2 | 内容直接铺在 sheet 里，不自带滚动 / 无底部呼吸 | `Column(fillMaxWidth().heightIn(max=260.dp).verticalScroll(rememberScrollState()).padding(bottom=24.dp))`（`GlassBottomSheetDemo.kt:88-110`；example 版再加 `.navigationBarsPadding().padding(bottom=16.dp)`，`component/GlassBottomSheetDemo.kt:46-51`） | 行数一多就顶到屏幕外；底部留白 0（sheet `insideMargin` 纵向是 0，`BottomSheetContentLayout.kt:934`） |
| 3 | 行 `insideMargin = PaddingValues(0.dp, 16.dp)` | 官方示例行不覆盖，吃 `BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)`（`Component.kt:287-292`；demo 用 `SwitchPreference`，其默认也是这个，`SwitchPreference.kt:56`） | 本项目行文字距面板边 24dp，官方是 24+16=40dp；本项目注释里"和标题 24dp 对齐"的说法不成立——标题是 `TextAlign.Center` 居中的（`BottomSheetContentLayout.kt:855-863`） |
| 4 | 自己补 `Spacer(navigationBarsPadding().height(8.dp))` | 官方把导航条留白交给滚动容器（example 版 `navigationBarsPadding()`）或 `Spacer(navigationBars height)`（`AboutPage.kt:494`） | 差不多，但 8dp 比官方的 16/24dp 小；且不在滚动容器里，行数多时位置不可控 |
| 5 | 不传 `material` / `stroke` | 官方也不传（吃默认），**但**本项目拿不到动态材质（§2.6） | Monet 下材质仍是无 tint 的旧 `dialogGlass` |

已对齐的部分（不要动）：`show` / `title` / `backdrop` / `onDismissRequest` / `onDismissFinished` 签名与官方一致（`GlassBottomSheet.kt:49-97`）；`backdrop` 来源走 `pageOverlay`（`PageChrome.kt:327-329`）符合硬约束 4；`GlassDialog` 结构与官方 demo 一致（Normal 取消 + Destructive 删除，官方 demo 是 Normal + Primary，角色语义都对，`GlassDialogDemo.kt:65-91`）。

### 2.2 官方 5ff41ba9 的完整调用示例（原样）

`miuix/docs/demo/src/commonMain/kotlin/GlassBottomSheetDemo.kt:73-111`

```kotlin
GlassBottomSheet(
    show = showSheet,
    backdrop = backdrop,
    title = "Notifications",
    onDismissRequest = { showSheet = false },
    endAction = {
        GlassIconButton(onClick = { showSheet = false }) {
            Icon(
                imageVector = MiuixIcons.Glass.Ok,
                contentDescription = "Confirm",
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
    },
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SwitchPreference(title = "Allow notifications", checked = notificationsEnabled, onCheckedChange = { … })
        SwitchPreference(title = "Play sound", checked = soundEnabled, onCheckedChange = { … })
        SwitchPreference(title = "Show previews", checked = previewsEnabled, onCheckedChange = { … })
    }
}
```

example 版的差异（`component/GlassBottomSheetDemo.kt:28-70`）：多了一层 `Column(verticalScroll().navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))`、内容透明（`insideMargin = DpSize(0.dp, 0.dp)` 的版本在 `AboutPage.kt:457-496`），底部用 `GlassDialogButton(role = Primary)` 收尾。

### 2.3 可直接照抄的改法（`LibraryScreen.kt`）

**新增 import**（相对当前 import 块）：

```kotlin
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.glass.Close
```

（`Column` / `fillMaxWidth` / `navigationBarsPadding` / `BasicComponent` / `MiuixIcons` / `MiuixTheme` / `Icon` 已存在。）

**替换 `LibraryScreen.kt:233-282` 的 `FileActionSheet`：**

```kotlin
/**
 * 文件操作面板。
 *
 * 结构对齐 Miuix 5ff41ba9 的官方示例：
 * - 头部右侧用 `GlassIconButton`（官方 `docs/demo/GlassBottomSheetDemo.kt:78-86`）。
 *   它自带 44dp 胶囊、动态取色 fill、Small bloom stroke 与按下动效，全部从
 *   `GlassTopAppBarDefaults` 继承（`GlassIconButton.kt:70-77`）——不要自己塞 TextButton / IconButton。
 * - 内容自带滚动容器 + 底部呼吸（官方 `GlassBottomSheetDemo.kt:88-110`）。
 * - 行不再覆盖 `insideMargin`：库默认的 `BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)`
 *   就是官方示例里 preference 行的内边距。
 */
@Composable
private fun FileActionSheet(
    file: File,
    backdrop: LayerBackdrop?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassBottomSheet(
        show = visible,
        title = file.name,
        backdrop = backdrop,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        // 官方 5ff41ba9 头部动作：玻璃图标按钮，走库的默认材质/描边/按压动效。
        endAction = {
            GlassIconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Glass.Close,
                    contentDescription = "关闭",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        // 官方写法：高度上限 + 滚动 + 底部留白；导航条留白交给滚动内容。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            BasicComponent(
                title = "保存到相册",
                startAction = { LeadingIcon(MiuixIcons.Glass.Light.Download) },
                onClick = onSave,
            )
            BasicComponent(
                title = "分享",
                startAction = { LeadingIcon(MiuixIcons.Glass.Light.Share) },
                onClick = onShare,
            )
            BasicComponent(
                title = "删除",
                titleColor = BasicComponentDefaults.titleColor(
                    color = MiuixTheme.colorScheme.error,
                ),
                startAction = { LeadingIcon(MiuixIcons.Glass.Light.Delete, MiuixTheme.colorScheme.error) },
                onClick = onDelete,
            )
        }
    }
}
```

> 说明：`endAction` 用官方示例的 `Ok` 还是语义更准确的 `Close` 都可以，两枚图标都在 glass 族里（`miuix-glass-icons/.../glass/Ok.kt`、`Close.kt`）。如果产品上不想多一颗按钮，至少把第 2、3 条（滚动容器 + 行内边距）改掉，那两条不需要产品确认。

### 2.4 GlassDialog 对照（基本已对齐，只补两处）

- 本项目 `LibraryScreen.kt:114-147` 与官方 `GlassDialogDemo.kt:65-91` 结构一致（title + summary + `Row(spacedBy(12.dp))` + 两个 `GlassDialogButton(weight(1f))`）。
- 官方取消按钮显式写 `role = GlassDialogButtonRole.Normal`（`GlassDialogDemo.kt:76-79`）；`GlassDialogButton` 的默认 role 就是 `Normal`（`GlassDialogButton.kt:71`），所以本项目不写**结果相同**，只是意图不显式。建议补上以便后续改默认值时不受影响。
- 本项目删除按钮用 `Destructive`（`LibraryScreen.kt:142`）——这是对官方角色集的正向使用，保留。

### 2.5 ⭐ `dynamicGlassMaterial` 在本项目整体失效（这是"看起来还是老样式"的真正材质原因）

链路：

1. 5ff41ba9 的 `GlassBottomSheetDefaults.material()` 是 `dynamicGlassMaterial(base, tint = surfaceContainer, alpha = 0.72f)`（`GlassBottomSheetSurface.kt:31-37`）；`GlassDialog` / 顶栏按钮 / 底栏 / Rail 同理（`GlassDialogSurface.kt:32-37`、`GlassTopAppBar.kt:125-133`、`GlassNavigationBar.kt:162-166`、`GlassNavigationRail.kt:233-237`）。
2. `dynamicGlassMaterial` 只有 `MiuixTheme.isDynamicColor` 为真才加第三层 tint（`Glass.kt:269-279`）。
3. `isDynamicColor` 读 `LocalColorSchemeMode`（`MiuixTheme.kt:87-96`），而 `LocalColorSchemeMode` 是 `internal staticCompositionLocalOf { null }`（`ThemeController.kt:298`），**只**由 `MiuixTheme(controller = …)` 提供（`MiuixTheme.kt:34-43`）。
4. 本项目用 `MiuixTheme(colors = animated)` 装配主题（`AnimatedTheme.kt:63`），该重载**不提供** `LocalColorSchemeMode`（`MiuixTheme.kt:64-70`）→ 值为 `null` → `isDynamicColor == false`。
5. 因此 Monet 静态/动态取色下，本项目弹层、顶栏按钮、底栏、Rail 全部拿不到 5ff41ba9 的新材质 tint。`App.kt:328-337` 的注释已经记录了这个限制（它是给导航 `selectedColor` 打补丁的），但材质这一侧一直没补。

### 2.6 修法（3 行，保住逐色过渡，同时让默认值生效）

`src/androidMain/kotlin/com/ayang/giftoolkit/ui/AnimatedTheme.kt:63`：把内层 `MiuixTheme(colors = animated)` **套在外层 `MiuixTheme(controller)` 里面**。CompositionLocal 是继承的：内层不提供的 `LocalColorSchemeMode` 会保留外层的值，于是 `MiuixTheme.isDynamicColor` 变正确，动态材质恢复；颜色仍然由内层的 `animated` 覆盖。

```kotlin
@Composable
fun AnimatedMiuixTheme(
    controller: ThemeController,
    content: @Composable () -> Unit,
) {
    val target = controller.currentColors()
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (to != target) {
            from = lerpColors(from, to, progress.value)
            to = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
        }
    }

    val animated = lerpColors(from, to, progress.value)
    // 外层 MiuixTheme(controller) 只为一件事：提供 LocalColorSchemeMode
    // （ThemeController.kt:298，internal，下游无法自己提供）。
    // 5ff41ba9 的 dynamicGlassMaterial（Glass.kt:269）与所有 *Defaults.material() 都靠
    // MiuixTheme.isDynamicColor 决定要不要加 surfaceContainer tint；
    // 少了这一层，Monet 下弹层/顶栏按钮/底栏/Rail 的新材质全部不生效。
    MiuixTheme(controller = controller) {
        MiuixTheme(colors = animated) {
            CompositionLocalProvider(
                LocalContentColor provides animated.onBackground,
                content = content,
            )
        }
    }
}
```

验收：Monet 模式下打开作品操作面板，sheet 材质应带 `surfaceContainer@0.72` 的 tint（与官方 docs demo 同档），顶栏返回键胶囊 fill 变成 `surfaceContainerHigh`；静态配色模式下观感应与改动前一致（`dynamicGlassMaterial` 在非动态取色时返回原材质，`Glass.kt:276-279`）。

> 备选（不改主题装配）：像 `App.kt:328-337` 给 `selectedColor` 打补丁那样，在 `FileActionSheet` / 删除确认里显式传 `material`。但 `dynamicGlassMaterial` 是 `internal`，只能自己用公开 API 复刻（`GlassMaterials.dialogGlass(isDark).copy(third = GlassColorLayer(surfaceContainer.copy(alpha = 0.72f), GlassColorBlendMode.SrcOver))`）。这是在业务代码里复制上游内部逻辑，**不推荐**；只在前者评估后确认不可行时再用。

---

## 3. ★ 抱怨 2：关于页图标与软件名称没有跟随背景渐变

### 3.1 本项目现状（工作区，已删掉渲染器黑名单）

`src/androidMain/kotlin/com/ayang/giftoolkit/screens/AboutScreen.kt`

```text
105  val glass = rememberGlassScope(opaqueBase = false)
106  val glassEnabled = LocalGlassEnabled.current
108  val blurEnabled = glassEnabled                 // ← 工作区已改成只跟玻璃开关
115  val bgBackdrop = rememberLayerBackdrop()        // 只录渐变层
119-138  logoBlend（三档 ColorDodge/LinearLight/Lab，浅色 ColorBurn…）
144-148  topBarBackdrop = rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
157-163  background = { BgEffectBackground(bgModifier = if (blurEnabled) Modifier.layerBackdrop(bgBackdrop) else Modifier, …) }
165  largeHeader = { progress -> AboutHeader(progress, blurEnabled, bgBackdrop, logoColors, version) }
240-250  图标：textureBlur(backdrop = bgBackdrop, shape = RectangleShape, blurRadius = 150f, colors = logoColors, contentBlendMode = DstIn)
268-276  标题：textureBlur(同参)
```

### 3.2 根因（为什么"不跟随渐变"）

1. **主因（已修）**：旧代码 `blurEnabled = glassEnabled && !isSoftwareRenderer`，而 `isSoftwareRenderer` 的黑名单包含 `ranchu/goldfish`（旧 `GlassSupport.kt`，见工作区 diff）→ 所有模拟器（含 `-gpu host`）都被判软件渲染 → `textureBlur` 整段跳过 → 图标 tint 成 `onBackground` 单色、标题是主题色实字，"不跟随渐变"。这正是 AGENTS.md 第 15 条新补的那段说明。**这不是 MIUIX 的限制**：库侧能力判断是 `isRenderEffectSupported()`（SDK≥31）与 `isRuntimeShaderSupported()`（SDK≥33），官方示例直接用它们（`PageUtils.kt:299-308`）。
2. **残留 P0**：见 §3.3。
3. **次要**：`shape = RectangleShape` 与官方 `RoundedCornerShape(16.dp)` 不一致（不影响"是否跟随"，影响边缘）。

### 3.3 残留 P0：API 31/32 上录制层不存在 → 图标/标题不可见

- 本项目 `blurEnabled = glassEnabled`（`AboutScreen.kt:108`），`glassEnabled = app.glassEnabled && isGlassSupported()`（`App.kt:110`），`isGlassSupported() = isRenderEffectSupported()`（`GlassSupport.kt:32`），即 **SDK≥31**（`RenderEffect.android.kt:12`）。
- 但录制层挂在 `BgEffectBackground` 的 `bgModifier` 上（`AboutScreen.kt:160`），而 `BgEffectBackground` 在 `!isRuntimeShaderSupported()` 时**提前 return 成普通 Box，连 `bgModifier` 都不挂**（`BgEffect.kt:159-162`）。`isRuntimeShaderSupported()` = **SDK≥33**（`RuntimeShader.android.kt:18`）。
- 结果：API 31/32 上 `blurEnabled = true`，但 `bgBackdrop` 从未录制到任何像素 → `textureBlur(contentBlendMode = DstIn)` 用空结果替换内容 → 图标和标题**整块消失**；同时页面渐变也不画（`BgEffectBackground` 直接返回空 Box），背景退化成 App 底色。
- 官方对照：`rememberBlurBackdrop()` 在 `!isRuntimeShaderSupported()` 时返回 `null`（`PageUtils.kt:299-308`），AboutPage 里 `backdrop != null` 才挂 `textureBlur`（`AboutPage.kt:271`），否则正常画实色内容。

**修法（二选一，建议两者都做）**

A. 主修：把运行时着色器能力并入唯一决策点（`GlassSupport.kt:32`）

```kotlin
import top.yukonga.miuix.kmp.material.isRenderEffectSupported
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/**
 * 设备能不能画玻璃。
 *
 * 5ff41ba9 的玻璃材质（glassPanel / dynamicGlassMaterial）与关于页渐变都依赖 AGSL 运行时着色器，
 * 而 `isRenderEffectSupported()` 只是 SDK≥31（RenderEffect.android.kt:12），
 * `isRuntimeShaderSupported()` 才是 SDK≥33（RuntimeShader.android.kt:18）。
 * 官方示例 `rememberBlurBackdrop()` 就是用后者兜底（PageUtils.kt:299-308），
 * 否则 SDK 31/32 上会出现"backdrop 录制层不存在、textureBlur 采空"的情况。
 */
fun isGlassSupported(): Boolean = isRenderEffectSupported() && isRuntimeShaderSupported()
```

B. 加固（可选）：`BgEffect.kt:159-162` 的不支持分支也画一层不透明底色，保证"没有渐变时页面仍有背景"：

```kotlin
if (!shaderSupported) {
    // 不支持 AGSL 时退化：铺页面色，不跑色带旋转（与官方 BgEffectBackground 的降级一致）。
    Box(modifier = modifier) {
        Spacer(Modifier.fillMaxSize().then(bgModifier).background(surfaceColor))
        content()
    }
    return
}
```

### 3.4 官方原样写法 + 可直接照抄的两套代码

**官方 5ff41ba9 原样（`miuix/example/shared/src/commonMain/kotlin/AboutPage.kt`）**

- 采样源：`val backdrop = rememberBlurBackdrop()`（`:111`）→ 挂在整页 `Box(... Modifier.layerBackdrop(backdrop))`（`:154`）；`rememberBlurBackdrop()` 内部先 `drawRect(surface)` 再 `drawContent()`，且 `!isRuntimeShaderSupported()` 时返回 null（`PageUtils.kt:299-308`）。
- 背景：`BgEffectBackground(..., bgModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier, alpha = { 1f - scrollProgress })`（`:225-232`）。
- **图标：不做 textureBlur**，88dp 白底圆角盒 + 74dp launcher（`:242-261`）；淡出阈值 0.35（`:247`）。
- **标题**：`textureBlur(backdrop = backdrop, shape = RoundedCornerShape(16.dp), blurRadius = 100f, noiseCoefficient = noiseCoefficient, colors = BlurDefaults.blurColors(blendColors = logoBlend), contentBlendMode = ComposeBlendMode.DstIn)`（`:270-286`），阈值 0.20（`:265`），35sp Bold。
- 版本号：`fontSize = 14.sp`、`onSurfaceVariantSummary`，阈值 0.05（`:292-304`），并缩到 0.95。
- `logoBlend` 三档与 `BlurDefaults.NoiseCoefficient`（0.0045f）：`AboutPage.kt:196,205-220`、`BlurDefaults.kt:148-149`。

**A. 官方原样版 `AboutHeader`（若产品接受"图标不带渐变"）**

```kotlin
// import 需要新增：
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode

@Composable
private fun AboutHeader(
    progress: () -> Float,
    backdrop: Backdrop?,           // = rememberBlurBackdrop() 的等价物（官方 AboutPage.kt:111）
    logoColors: BlurColors,        // BlurDefaults.blurColors(blendColors = logoBlend)
    version: String,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .graphicsLayer {
                val p = ((progress() - 0.35f) / 0.15f).coerceIn(0f, 1f)
                clip = true
                shape = RoundedCornerShape(24.dp)
                alpha = 1 - p
                scaleX = 1 - (p * 0.05f)
                scaleY = 1 - (p * 0.05f)
            }
            .background(Color.White),
    ) {
        Image(
            modifier = Modifier.size(74.dp),
            painter = painterResource(R.drawable.ic_app_mark),
            contentDescription = null,
        )
    }
    Text(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 5.dp)
            .graphicsLayer {
                val p = ((progress() - 0.20f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1 - p
                scaleX = 1 - (p * 0.05f)
                scaleY = 1 - (p * 0.05f)
            }
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(16.dp),
                        blurRadius = 100f,
                        noiseCoefficient = BlurDefaults.NoiseCoefficient,
                        colors = logoColors,
                        contentBlendMode = ComposeBlendMode.DstIn,
                    )
                } else {
                    Modifier
                },
            ),
        text = "GIF 助手",
        color = MiuixTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        fontSize = 35.sp,
    )
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = ((progress() - 0.05f) / 0.15f).coerceIn(0f, 1f)
                alpha = 1 - p
                scaleX = 1 - (p * 0.05f)
                scaleY = 1 - (p * 0.05f)
            },
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        text = version,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
    )
}
```

**B. 保留本项目"图标也跟随渐变"的做法（推荐，满足用户诉求），参数按官方对齐**

只改 `AboutScreen.kt` 两处 `textureBlur` + 一个能力判断，其余不动：

```kotlin
// AboutScreen.kt:108 —— 能力判断与采样源保持一致（官方 PageUtils.kt:299-308 同款）
//   blurEnabled 仍然只看 GlassSupport 的单一决策点（AGENTS.md 第 3 条），
//   前提是 §3.3 修法 A 已经把 isRuntimeShaderSupported() 并进 isGlassSupported()。
val blurEnabled = glassEnabled

// AboutScreen.kt:240-250 图标：保留 textureBlur；shape 换成与标题一致的圆角采样区，
//   blurRadius 用官方的 100f（= BlurDefaults.MaxBlurRadius，BlurDefaults.kt:155；150f 本来也会被夹紧）。
Modifier.textureBlur(
    backdrop = bgBackdrop,
    shape = RoundedCornerShape(24.dp),   // 与图标容器 MarkBoxSize(100.dp) 的圆角观感一致
    blurRadius = BlurDefaults.MaxBlurRadius, // = 100f
    noiseCoefficient = BlurDefaults.NoiseCoefficient,
    colors = logoColors,
    contentBlendMode = ComposeBlendMode.DstIn,
)

// AboutScreen.kt:268-276 标题：shape / blurRadius / noiseCoefficient 与官方 AboutPage.kt:273-282 逐参一致
Modifier.textureBlur(
    backdrop = bgBackdrop,
    shape = RoundedCornerShape(16.dp),   // 官方就是 16dp
    blurRadius = 100f,
    noiseCoefficient = BlurDefaults.NoiseCoefficient,
    colors = logoColors,
    contentBlendMode = ComposeBlendMode.DstIn,
)
```

需要新增的 import：

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
```

> `noiseCoefficient` 不写也等价（默认就是 `BlurDefaults.NoiseCoefficient`，`TextureEffect.kt:27-33`），显式写只是让"与官方同参"可核对。

### 3.5 关于页其余差异（按重要性）

| # | 项目 | 本项目 | 官方 5ff41ba9 | 判定 |
| --- | --- | --- | --- | --- |
| 1 | backdrop 来源 | `rememberLayerBackdrop()` 只录 `BgEffectBackground` 渐变层（`AboutScreen.kt:115`） | `rememberBlurBackdrop()` 录**整页**（先 `drawRect(surface)` 再 `drawContent()`，`PageUtils.kt:299-308`），挂在整页 Box（`AboutPage.kt:154`） | **可保留**：本项目硬约束 1 不允许把录制层套到内容外（会采样到自己），只采渐变层是正确取舍；但必须补 §3.3 的能力兜底 |
| 2 | bgModifier 挂载位置 | 挂在渐变层的 `Spacer` 上（`BgEffect.kt:164-179`） | 挂在 `BgEffectBackground` 的根（`AboutPage.kt:230`） | 等价，保留 |
| 3 | 图标是否 textureBlur | 是（`AboutScreen.kt:241-250`） | **否**（`AboutPage.kt:242-261`） | 产品选择：用户要求"跟随渐变"→ 保留本项目做法（§3.4-B） |
| 4 | 图标容器 | 100dp、`clip = true`、图形 `132dp`、tint `onBackground`（`MarkBoxSize`/`MarkSampleSize`，`AboutScreen.kt:60-63`，`:232-255`） | 88dp、白底、`RoundedCornerShape(24.dp)`、74dp 原图 | 保留（KSU/InstallerX 参考值）；若要更像官方可加白底——但会直接破坏"跟随渐变"，不建议 |
| 5 | 标题 shape | `RectangleShape` | `RoundedCornerShape(16.dp)` | **改**（§3.4-B） |
| 6 | 标题 blurRadius | `150f`（夹到 100f） | `100f` | 等价，建议显式 100f |
| 7 | 版本号缩放 | 只有 alpha | alpha + 缩到 0.95（`AboutPage.kt:295-299`） | 小差异，可选 |
| 8 | 头部顶部留白 | 40dp（`PageChrome.kt:277`） | 52dp + 列表占位多 126dp（`AboutPage.kt:157,182,323`） | 项目既有版式，保留（改会造成整页间距漂移） |
| 9 | 头部淡出阈值 | 0.35 / 0.20 / 0.05（`AboutScreen.kt:223,262,293`） | 0.35 / 0.20 / 0.05（`AboutPage.kt:247,265,295`） | **已对齐** ✅ |

---

## 4. 本项目硬编码值 → 官方常量/默认值 对照表

原则：**能从库里取到的，不要在自己代码里留常量**。`GlassSupport` / `pageContentPadding` 这类"唯一决策点"已经是正确形态，其余按表处理。

| 本项目位置 | 现值 | 官方 API / 值 | 判定 |
| --- | --- | --- | --- |
| `LibraryScreen.kt:254` 行内边距 | `PaddingValues(0.dp, 16.dp)` | `BasicComponentDefaults.InsideMargin = PaddingValues(16.dp)`（`Component.kt:287-292`） | 去掉覆盖（§2.3） |
| `LibraryScreen.kt:280` 底部留白 | `8.dp` | 官方滚动容器 `padding(bottom = 24.dp)`（`GlassBottomSheetDemo.kt:93`） | 改 24dp（随 §2.3） |
| `Widgets.kt:202-210` GlassCard 圆角 | `RoundedCornerShape(CardDefaults.CornerRadius)` (=16dp, `Card.kt:192`) | 同 | ✅ 已用常量 |
| `Widgets.kt:206` GlassCard 模糊半径 | `60f` | 官方 About 卡片默认 `blurRadius = 60f`（`AboutPage.kt:195`） | ✅ 对齐 |
| `Widgets.kt:207-209` GlassCard 混色 | `surface.copy(alpha = 0.6f)` | 官方卡片用 `ColorBlendToken.Overlay_Thin_Light`（`AboutPage.kt:205,358`）；本项目取自 InstallerX | 保留（参考项目写法），别再加第二套 |
| `Widgets.kt:251-274` IconSizes | Row 26 / Action 28 / Inline 19 / Empty 44 / Media 28 | 库只有 `GlassNavigationBarDefaults.IconSize = 25.dp`（导航栏内）；Glass 图标画布 28dp | 保留（项目自定，无官方对应） |
| `GlassScope.kt:217` 顶栏返回键 | 28dp（glass）/ 24dp（普通） | 官方 `component/BackNavigationIcon.kt:37`（28dp / 24dp） | ✅ 已对齐 |
| `GlassScope.kt:229` 普通档图标 | `MiuixIcons.Back`（extended） | 官方同（`BackNavigationIcon.kt:41`） | ✅ 已对齐 |
| `GlassScope.kt:259,284` 顶栏 band | `GlassTopAppBarDefaults.bandBrush(surface)` / `BandOverhang` | 官方 `PageUtils.kt:254,273` 同 | ✅ 已对齐 |
| `PageChrome.kt:79` 底栏浮动外边距 | `20.dp`（≤16dp 档） | `GlassNavigationBar.kt:321` | ✅ 已同步（工作区改的） |
| `PageChrome.kt:84` 底栏占位 | `GlassNavigationBarDefaults.Height + floatingMargin + 12.dp` | `Height = 54.dp`（`GlassNavigationBar.kt:122`） | ✅ 已用常量；`+12.dp` 是呼吸值（项目自定） |
| `PageChrome.kt:149-150` 内容底部 | `maxOf(innerPadding.bottom, clearance) + extraBottom(12.dp)` | — | 项目骨架，保留 |
| `PageChrome.kt:277-281` 头部留白 | `40.dp` / `2.dp` / `300.dp` 兜底 / `+20.dp` | 官方 `+52.dp` / `126.dp` / `logoHeightDp=300.dp`（`AboutPage.kt:157,182,223,323`） | 保留（改会牵动整页版式；`300.dp` 兜底与官方一致） |
| `AboutScreen.kt:60-63` 图标尺寸 | 采样 132dp / 容器 100dp | 官方 74dp / 88dp（`AboutPage.kt:245,257`） | 保留（KSU 参考值） |
| `AboutScreen.kt:246,272` 模糊半径 | `150f` | 官方 100f（`AboutPage.kt:276`），库上限 `BlurDefaults.MaxBlurRadius = 100f` | 改成 `BlurDefaults.MaxBlurRadius`（等价值，去掉隐式夹紧） |
| `AboutScreen.kt:244,271` shape | `RectangleShape` | 标题 `RoundedCornerShape(16.dp)`（`AboutPage.kt:275`） | 改（§3.4-B） |
| `AnimatedTheme.kt:49` 主题过渡 | `tween(320, FastOutSlowInEasing)` | 库无对应（`GlassMotion.*` 是组件动效，不是主题过渡） | 保留（项目特性） |
| `Widgets.kt:545-549` 进度动画 | `tween(durationMillis = 300)` | 无对应（ProgressButton 是移植件） | 保留 |
| `BgEffect.kt:155` 色带插值 | `spring(dampingRatio = 0.9f, stiffness = 35f)` | 官方 BgEffectConfig 同参（移植来源） | ✅ 对齐（移植件，别改） |
| `App.kt:188` tab 切换 | `pagerState.springAnimateToPage(index)` | 官方同（库提供，stiffness 322.2） | ✅ 已用库实现 |
| `App.kt:342` 底栏左右留白 | `24.dp` | 官方示例 `padding(horizontal = 24.dp)`（`AppContent.kt:791`） | ✅ 对齐 |

---

## 5. 5ff41ba9 新增能力 / 本项目该用未用

| 5ff41ba9 能力 | 证据 | 本项目状况 | 建议 |
| --- | --- | --- | --- |
| `GlassTopAppBar` 的 `centerObstructionStart` | 参数 `GlassTopAppBar.kt:293`；官方在 `AppContent.kt:226-232` 用 `GlassNavigationRailState.collapsedStart` 算，`PageUtils.kt:270,291` 传给 Glass/普通两档顶栏 | `PageTopBar` 没传（`GlassScope.kt:260-289`、`:291-303`） | **建议补**：手机横屏折叠 Rail 时，项目把标题透明度绑到 `railState.progress`（`App.kt:150`）而不挪中心，胶囊与居中标题可能重叠。把 `railState.collapsedStart` 通过 `CompositionLocal` 下发到 `PageTopBar`，两档都传 `centerObstructionStart = it` |
| `GlassIconButton` 作为 header/dialog 动作 | 官方 demo `GlassBottomSheetDemo.kt:78-86`、`GlassDialogDemo.kt`（无按钮）、`GlassSearchOverlay` | 页面里只用于顶栏返回键；弹层头部没用 | 按 §2.3 用 |
| `dynamicGlassMaterial`（动态取色材质） | `Glass.kt:269-279`；被 `GlassBottomSheetDefaults/ GlassDialogDefaults/ GlassTopAppBarDefaults/ GlassNavigationBarDefaults/ GlassNavigationRailDefaults` 采用 | 因主题装配失效（§2.5） | 按 §2.6 修 |
| `GlassSearchOverlay`（新增组件） | `miuix-glass/.../GlassSearchOverlay.kt`（+605 行，`416709f9`/`47d75e69`） | 项目无搜索，不需要 | 不引入 |
| `ScrollIndicatorState` / 新版 `ScrollBar` | `9155cd2a`（`ScrollBar.kt` 大改） | 项目列表没有滚动条 | 不需要 |
| `GlassSegmentedTabRow` | `GlassTabRow.kt` | 项目没有分段控件 | 不需要 |
| `GlassDialogButton` 动态取色禁用色 | `GlassDialogButton.kt:125-137` | 走默认 | 修 §2.6 后自动生效 |

---

## 6. 未对齐清单（按严重度）

### P0（用户可见错误 / 功能回退）

1. **关于页图标/标题不跟随渐变（主因：自造渲染器黑名单）**
   现状：`blurEnabled = glassEnabled && !isSoftwareRenderer`（HEAD 版 `AboutScreen.kt`；`isSoftwareRenderer` 在 HEAD 版 `GlassSupport.kt`，黑名单含 `ranchu/goldfish`）。
   目标：只走库能力判断 + `GlassSupport` 单一决策点。
   改法：删除 `isSoftwareRenderer`（**工作区已删** ✅）；再按 §3.3-A 把 `isRuntimeShaderSupported()` 并入 `isGlassSupported()`。
   证据：`AboutScreen.kt:108`、`GlassSupport.kt:32`、`GlassTopAppBar`/`isRuntimeShaderSupported` 定义 `RuntimeShader.android.kt:18`、官方 `PageUtils.kt:299-308`。

2. **弹层/顶栏/导航的 5ff41ba9 动态材质在本项目全灭**
   现状：`MiuixTheme(colors = animated)` 不提供 `LocalColorSchemeMode` → `MiuixTheme.isDynamicColor == false`（`AnimatedTheme.kt:63`、`MiuixTheme.kt:64-70,87-96`、`ThemeController.kt:298`）。
   改法：§2.6 的嵌套 `MiuixTheme(controller)`（3 行）。
   影响面：`GlassBottomSheet` / `GlassDialog` / `GlassIconButton`（fill + material）/ `GlassNavigationBar` / `GlassNavigationRail` 在 Monet 下的 tint 全部恢复。

3. **API 31/32 上关于页图标标题不可见**（§3.3）
   改法：`isGlassSupported()` 加 `isRuntimeShaderSupported()`；可选加固 `BgEffect.kt:159-162`。

### P1（明显样式漂移，影响"像不像 OS4 新基线"）

4. **作品操作面板缺官方 5ff41ba9 的头部 `endAction`（`GlassIconButton`）**：`LibraryScreen.kt:244-250` → §2.3。
5. **弹层内容没有滚动容器 / 底部呼吸 / 行内边距覆盖**：`LibraryScreen.kt:254-280` → §2.3。
6. **关于页标题 `textureBlur` 的 `shape` 与官方不一致（RectangleShape vs RoundedCornerShape(16.dp)）**：`AboutScreen.kt:271` → §3.4-B。

### P2（细节一致性）

7. `GlassTopAppBar` 未接 `centerObstructionStart`（§5）。
8. `GlassDialog` 取消按钮未显式 `role = Normal`（`LibraryScreen.kt:126-131`，默认即 Normal，补上防以后默认值变化）。
9. 关于页版本号缺 0.95 缩放（`AboutScreen.kt:292-295` vs `AboutPage.kt:295-299`）。
10. 文档/脚本基线记录不一致（AGENTS.md/迁移知识库写 `3177a684`，HEAD 脚本 pin `d45ca181`）；工作区已改成 `5ff41ba9`，但 AGENTS.md 第 15 条的"关于页"段落仍描述旧的模拟器降级理由，建议同步（工作区已部分更新）。
11. `GlassCard` 混色用 `surface@0.6` 而官方 About 卡片用 `ColorBlendToken.Overlay_Thin_Light`——属于参考项目（InstallerX）取舍，**不要**为了"对齐"改掉，会导致与本项目其它玻璃材质不同源。

---

## 7. 建议的回归验证（改完必须跑）

1. 设备矩阵：真机（SDK≥33）+ `emulator -avd <avd> -gpu host`（AGENTS.md 明确 `-gpu host` 才可信）+ 至少一台 API 31/32（或临时把 `isRuntimeShaderSupported()` 打桩为 false 验证降级不消失内容）。
2. 配色矩阵：静态 / Monet × 明 / 暗（4 组）。重点看：
   - 作品面板：头部是否出现玻璃胶囊按钮；Monet 下材质是否有 `surfaceContainer` tint（与官方 docs demo APK 对比）；行文字左边界是否从 24dp 变 40dp（预期变化）。
   - 关于页：图标与标题是否取到渐变颜色（真机截图色值应与背景同源），滚动时渐隐/缩放正常；API 31/32 下图标标题**可见**（走实色回退）。
   - 顶栏返回键胶囊 fill：Monet 下应为 `surfaceContainerHigh`（`GlassTopAppBar.kt:125-129`），静态下白/`#2C2C2C`。
   - 底栏 / Rail：Monet 下材质带 tint（`GlassNavigationBar.kt:162-166`、`GlassNavigationRail.kt:233-237`）。
3. 弹层几何：作品面板 3 行时底部留白 24dp；列表多于 ~6 行时可滚动；`navigationBarsPadding` 后"删除"不贴手势条。
4. 编译期：确认删除 `isSoftwareRenderer` 后无残留引用（`GlassScope.kt` / `GlassSupport.kt` / `AboutScreen.kt` 已清理，`grep -r isSoftwareRenderer src` 应为 0 命中）。

---

## 8. 附：本次审计用到的关键证据索引

**GifHelper**
- `screens/LibraryScreen.kt:114-147`（GlassDialog）、`:233-282`（FileActionSheet）、`:244-250`、`:254-280`
- `screens/AboutScreen.kt:105-115`（作用域/录制层）、`:143-148`（顶栏叠加层）、`:157-165`（背景/头部接线）、`:218-300`（AboutHeader）、`:240-250`（图标 textureBlur）、`:268-276`（标题 textureBlur）、`:287-299`（版本号）
- `ui/GlassSupport.kt:32`（`isGlassSupported`）
- `ui/GlassScope.kt:73-86`（底栏占位公式）、`:106-117`（rememberPageBackdrop）、`:149-305`（PageTopBar）
- `ui/PageChrome.kt:73-86`、`:178-354`（Os4ListPage）
- `ui/Widgets.kt:150-219`（GroupCard / GlassCard）、`:249-274`（IconSizes）
- `ui/AnimatedTheme.kt:34-69`（主题装配）
- `App.kt:105`（ThemeController）、`:110`（glassEnabled）、`:153`（AnimatedMiuixTheme）、`:321-343`（底栏）、`:399-413`（Rail）
- `ui/bg/BgEffect.kt:127-182`（bgModifier 与降级分支）

**miuix 5ff41ba9**
- `miuix-glass/.../GlassBottomSheet.kt:49-97`；`GlassBottomSheetSurface.kt:26-50`
- `miuix-glass/.../GlassDialog.kt:47-93`；`GlassDialogSurface.kt:24-50`；`GlassDialogButton.kt:66-149`
- `miuix-glass/.../GlassIconButton.kt:63-120`
- `miuix-glass/.../GlassTopAppBar.kt:76-166`（Defaults，buttonFill/buttonMaterial 在 :125-133）、`:280-331`、`:423-467`、`:556`
- `miuix-glass/.../GlassNavigationBar.kt:119-186`（Defaults，material 在 :162-166）、`:318-323`（floatingMargin）
- `miuix-glass/.../GlassNavigationRail.kt:205-238`
- `miuix-glass/.../GlassMotion.kt:18-368`；`Glass.kt:269-279`（dynamicGlassMaterial）
- `miuix-ui/.../layout/BottomSheetContentLayout.kt:653-700`、`:838-871`、`:902-935`
- `miuix-ui/.../layout/DialogContentLayout.kt:450-503`
- `miuix-ui/.../basic/Component.kt:287-321`；`Card.kt:187-212`；`TopAppBar.kt:429-453`
- `miuix-ui/.../theme/MiuixTheme.kt:24-97`；`ThemeController.kt:227-298`
- `miuix-material/.../RenderEffect.android.kt:12`；`RuntimeShader.android.kt:18`；`GlassMaterials.kt:11-102`；`GlassMaterial.kt:12-74`
- `miuix-blur/.../BlurDefaults.kt:143-178`；`TextureEffect.kt:27-43`；`LayerBackdrop.kt:38-110`
- `example/shared/.../AboutPage.kt:85-497`；`utils/PageUtils.kt:245-308`；`component/GlassBottomSheetDemo.kt:28-70`；`component/BackNavigationIcon.kt:22-46`；`AppContent.kt:210-269`、`:723-846`
- `docs/demo/src/commonMain/kotlin/GlassBottomSheetDemo.kt:39-114`；`GlassDialogDemo.kt:34-94`
- 旧写法取证：`git show d45ca181:docs/demo/src/commonMain/kotlin/GlassBottomSheetDemo.kt`（`endAction = { TextButton("Done") }`）→ 5ff41ba9 变为 `GlassIconButton + Glass.Ok`
