# 03 · OS4 主题系统：颜色、文字、圆角、背景

> 目标：把新项目现有的颜色/字号/圆角/背景**收敛到 MIUIX 的语义角色**，
> 并且保证"深浅色 × 静态/Monet"四种组合都不塌。
> 一句话原则：**页面里不要再出现 `Color(0xFF...)`。**

---

## 1. 主题装配的正确姿势

### 1.1 两种 `MiuixTheme` 重载，差别很关键

📌 事实（`miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/MiuixTheme.kt`）：

| 重载 | 提供什么 | 位置 |
|---|---|---|
| `MiuixTheme(controller = …)` | `LocalColors`、`LocalTextStyles`、`LocalIndication`、`LocalOverscrollFactory`、**`LocalContentColor`**、**`LocalColorSchemeMode`** | `:24-44` |
| `MiuixTheme(colors = …)` | 只有前四项 | `:54-72` |

**这决定了能不能做"逐色过渡"**：想做主题切换动画就必须用 `colors =` 重载自己插值，
但那样会丢 `LocalContentColor` 与 `LocalColorSchemeMode`。

- `LocalContentColor` 丢了 → 没显式指定颜色的 `Text` 会回落 `Color.Black`。
- `LocalColorSchemeMode` 丢了 → `MiuixTheme.isDynamicColor` 恒 false → 依赖它做默认色的组件
  （底栏/侧栏选中色、Switch 未选中拇指色）会走错分支。**而且它是 `internal`，调用方补不了。**

🏠 GIF 助手的处置（`.../ui/AnimatedTheme.kt:63-68`）：

```kotlin
MiuixTheme(colors = animated) {
    CompositionLocalProvider(
        LocalContentColor provides animated.onBackground,   // 补回丢掉的 local
        content = content,
    )
}
```

并在**调用点**显式传 `selectedColor`（`App.kt:333-337`、`:405-409`）：

```kotlin
selectedColor = if (app.monetEnabled) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onBackground
```

> **可迁移性**：如果新项目**不需要**主题切换动画，就用 `MiuixTheme(controller = …)`，
> 一行都不用补。只有当你要"颜色逐色过渡"时才需要上面这套补丁。
> 这是"为了体验付出的额外复杂度"，不是必须。

### 1.2 最小装配（推荐给新项目）

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    monet: Boolean = true,
    content: @Composable () -> Unit,
) {
    val mode = when {
        monet && darkTheme -> ColorSchemeMode.MonetDark
        monet -> ColorSchemeMode.MonetLight
        darkTheme -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.Light
    }
    val controller = remember(mode) { ThemeController(colorSchemeMode = mode) }
    MiuixTheme(controller = controller, content = content)   // 📌 MiuixTheme.kt:24
}
```

`ThemeMode.System` 的语义由 `ColorSchemeMode.System` / `MonetSystem` 承担
（内部 `isSystemInDarkTheme()`，`ThemeController.kt:248`、`:257`）。

### 1.3 `ColorSchemeMode` 映射表

| 用户看到的选项 | 无动态取色 | 有动态取色（Monet） |
|---|---|---|
| 跟随系统 | `ColorSchemeMode.System` | `ColorSchemeMode.MonetSystem` |
| 浅色 | `ColorSchemeMode.Light` | `ColorSchemeMode.MonetLight` |
| 深色 | `ColorSchemeMode.Dark` | `ColorSchemeMode.MonetDark` |

Monet 取色走 `platformDynamicColors(dark = …)`（`ThemeController.kt:260`/`:266`/`:272`）。
GIF 助手没有传 `keyColor` / `colorSpec` / `paletteStyle`，即"完全跟随壁纸"。
如果新项目有品牌色，可以传 `keyColor`（需查当前版本签名）。

---

## 2. 颜色体系：53 个语义角色怎么用

📌 `Colors` 的字段就是权威清单；`copy()` 的形参顺序与它一致。
GIF 助手的逐色插值把 53 个角色全部列了一遍（`AnimatedTheme.kt:72-129`），可以当作**字段清单**来查。

按用途分组（只列迁移时最常碰到的）：

| 组 | 角色 | 用途 |
|---|---|---|
| 主色 | `primary` / `onPrimary` / `primaryVariant` / `onPrimaryVariant` / `primaryContainer` / `onPrimaryContainer` | 主操作、选中态 |
| 次色 | `secondary` / `onSecondary` / `secondaryVariant` / `onSecondaryVariant` / `secondaryContainer` / `onSecondaryContainer` / `secondaryContainerVariant` / `onSecondaryContainerVariant` | 次要操作、轨道色 |
| 第三 | `tertiaryContainer` / `onTertiaryContainer` / `tertiaryContainerVariant` / `onTertiaryContainerVariant` | 强调块 |
| 错误 | `error` / `onError` / `errorContainer` / `onErrorContainer` | 危险操作 |
| 背景 | `background` / `onBackground` / `onBackgroundVariant` | **窗口级**背景（不是页面） |
| 表面 | `surface` / `onSurface` / `surfaceVariant` / `onSurfaceSecondary` / `onSurfaceVariantSummary` / `onSurfaceVariantActions` | 页面底色、正文、说明、动作 |
| 卡片 | `surfaceContainer` / `onSurfaceContainer` / `onSurfaceContainerVariant` / `surfaceContainerHigh` / `surfaceContainerHighest`（各带 `on*`） | 卡片与浮起层次 |
| 禁用 | `disabledPrimary` / `disabledOnPrimary` / `disabledPrimaryButton` / `disabledOnPrimaryButton` / `disabledPrimarySlider` / `disabledSecondary*` / `disabledOnSurface` | **必须成对使用**（见 §5） |
| 其它 | `outline` / `dividerLine` / `windowDimming` / `sliderKeyPoint` / `sliderKeyPointForeground` / `sliderBackground` | 描边、分割线、遮罩、滑块 |

### 2.1 迁移映射表（旧颜色 → MIUIX 角色）

| 旧代码 | 映射到 | 备注 |
|---|---|---|
| `MaterialTheme.colorScheme.primary` | `MiuixTheme.colorScheme.primary` | 一一对应 |
| `MaterialTheme.colorScheme.surface` | `surface` | 页面底色 |
| `MaterialTheme.colorScheme.surfaceVariant` | `surfaceContainer` | Material 的 surfaceVariant ≈ MIUIX 的卡片色（**不是** MIUIX 的 `surfaceVariant`） |
| `MaterialTheme.colorScheme.onSurface` | `onSurface` | |
| `MaterialTheme.colorScheme.onSurfaceVariant` | `onSurfaceVariantSummary`（说明文字）/ `onSurfaceVariantActions`（动作） | MIUIX 把它拆成了两个语义 |
| `MaterialTheme.colorScheme.error` | `error` | |
| `Color.White` / `Color.Black` 当背景 | ❌ 禁止 | 用 `surface` / `onBackground`；AMOLED 由 `LocalAmoled` 处理 |
| `Color(0xFF3482FF)`（品牌蓝） | `primary` | 除非必须保留品牌色，否则不要写死 |
| `Color.Gray.copy(alpha = .5f)`（分割线） | `dividerLine` | |
| `Color.Black.copy(alpha = .32f)`（遮罩） | `windowDimming` | |

> ⚠️ 最容易错的一格：Material 的 `surfaceVariant` 不是 MIUIX 的 `surfaceVariant`。
> 前者在 Material 里是"卡片/容器色"，后者在 MIUIX 里是"表面上的次级表面"。
> 机械替换会得到"卡片变灰底"。

---

## 3. 三层配色：页面 / 次级表面 / 卡片

> 这是 GIF 助手最有价值的经验之一，但**它不是库 API**，是"三层派生的模式"。
> 新项目可以照抄**思路**，不要照抄函数名。

### 3.1 为什么需要三层

| 层 | 语义 | 用在哪 |
|---|---|---|
| `pageBaseColor()` | 页面**真正铺在屏幕上**的那层（= `surface`） | `Scaffold.containerColor`、录制层垫色、底部遮挡条、导航转场 `backdropColor` |
| `pageColor()` | **由卡片色反推的次级表面**（比页面深/浅一档） | 预览舞台、需要"跟页面拉开一档"的画布 |
| `cardColor()` | 卡片（= `surfaceContainer`，AMOLED 下压成近黑） | `Card` 背景 |

### 3.2 真实数字（🔴 模拟器实测，值得记住）

| 组合 | 页面（`surface`） | 卡片（`surfaceContainer`） | `pageColor()`（反推） |
|---|---|---|---|
| 静态浅色 | `#F7F7F7` | `#FFFFFF` | `#F6F6F6` |
| 静态深色 | `#000000` | `#242424` | `#1E1E1E` |
| Monet 浅色 | `#F9F9FF` | `#EDEDF4` | `#E5E5EB` |
| Monet 深色 | `#131316` | `#1F1F22` | 未记录 |

**关键差值**：深色下 `pageColor()` 与 `surface` 差 **30 个灰阶**（`#1E1E1E` vs `#000000`），
浅色只差 1（`#F6F6F6` vs `#F7F7F7`）。所以在浅色下写错看不出来，深色下一眼就是一条亮色带。

🏠 真实事故：录制层垫色与底部操作条遮挡色都用了 `pageColor()`，
深色静态下页面上出现一条 `#1E1E1E` 的横向色带（[12](12-known-issues-and-solutions.md) C14）。

### 3.3 实现（GIF 助手，供参考）

```kotlin
@Composable fun pageBaseColor(): Color {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    if (LocalAmoled.current && dark) return Color.Black
    return MiuixTheme.colorScheme.surface
}

@Composable fun cardColor(): Color {
    val card = MiuixTheme.colorScheme.surfaceContainer
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    return if (LocalAmoled.current && dark) Color(0xFF0D0D0D) else card
}

@Composable fun pageColor(): Color {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    if (LocalAmoled.current && dark) return Color.Black
    val card = cardColor()
    val shade = if (card.luminance() < 0.5f) Color.Black.copy(alpha = 0.18f)
                else Color.Black.copy(alpha = 0.035f)
    return shade.compositeOver(card)
}
```

📌 来源：`.../ui/Widgets.kt:84-138`。

### 3.4 会不会"页面和卡片糊成一片"？

旧结论说"Monet 会把 `surface` / `surfaceContainer` 压成同一个值" ——
🔴 在 `3177a684` 上**已不成立**：四套配色实测都是不同值（上表）。
所以新项目**可以直接用 `surface` 当页面底色**，不需要反推。
反推只用于"要跟页面拉开一档"的次级表面。

### 3.5 新项目怎么用（最小版）

```kotlin
object AppColors {
    @Composable fun page(): Color = MiuixTheme.colorScheme.surface
    @Composable fun card(): Color = MiuixTheme.colorScheme.surfaceContainer
    @Composable fun stage(): Color =                     // 需要"一档深"的画布
        Color.Black.copy(alpha = if (isDark()) 0.18f else 0.035f)
            .compositeOver(card())
}

@Composable fun isDark(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f
```

三条纪律：

1. **`containerColor`、录制层垫色、遮挡条底色必须是同一个函数**（否则出现色差方块）。
2. 判断明暗统一用 `background.luminance() < 0.5f`，不要用 `isSystemInDarkTheme()`
   （手动切深色时后者不跟随）。
3. 不要在调用点写 `surface.copy(alpha = …)` 当页面色 —— 半透明页面会露出下面的东西。

---

## 4. AMOLED 纯黑

只在深色模式生效（HyperOS 的 AMOLED 也只作用于深色）：

```kotlin
val LocalAmoled = compositionLocalOf { false }

@Composable fun pageBaseColor(): Color =
    if (LocalAmoled.current && isDark()) Color.Black else MiuixTheme.colorScheme.surface

@Composable fun cardColor(): Color =
    if (LocalAmoled.current && isDark()) Color(0xFF0D0D0D) else MiuixTheme.colorScheme.surfaceContainer
```

要点：

- 页面纯黑 `#000000`、卡片近黑 `#0D0D0D`（保留"卡片浮起"的一档层次）。
- 录制层垫色也要走同一个函数，否则玻璃面板在纯黑页面上会浮出一块 `#242424`。
- **不要**给浅色模式也做纯黑。

---

## 5. 禁用态：必须成对使用

📌 库按"档"成对配色，跨档拼装必然塌：

| 档 | 背景角色 | 文字角色 | 实测对比度 |
|---|---|---|---|
| 主按钮档 | `disabledPrimaryButton` | `disabledOnPrimaryButton` | 浅色 **1.43:1**、深色 **2.40:1**、Monet 浅 **1.47:1** |
| 次按钮档 | `disabledSecondaryVariant` | `disabledOnSecondaryVariant` | 约 **1.89:1** |
| 通用（非按钮） | `disabledPrimary` / `disabledSecondary` | `disabledOnPrimary` / `disabledOnSecondary` | — |

🔴 真实事故：禁用态"开始生成"用了 `disabledSecondaryVariant`（次档底）+ `disabledOnPrimaryButton`
（主档字）→ 浅色下 `#F2F2F2` 底 + `#FFFFFF` 字，对比度 **1.12:1**，几乎读不出字
（[12](12-known-issues-and-solutions.md) C37）。修法是**同一档配对**。

> 注意：对比度数字来自 GIF 助手的模拟器像素采样，两种轮次的记录有 1.04 / 1.12 的差异
> （见 [12](12-known-issues-and-solutions.md) 附录 B2）。**以"同一档配对"这条规则为准**，
> 不要纠结具体小数。

---

## 6. 文字体系

📌 `MiuixTheme.textStyles` 是唯一来源；GIF 助手**没有**自定义字体/字号表
（grep `Typography|fontFamily|textStyles =` 零命中），全部吃库默认。

常用角色（按 GIF 助手的实际用法）：

| 角色 | 用在哪 |
|---|---|
| `headline1` | 设置项大标题、色板行标题 |
| `body1` | 正文 |
| `footnote1` | 说明文字、版本号、空态提示 |
| `subtitle` | `SmallTitle`（分组标题）用的字号 |
| `button` | 按钮文字 |

规则：

1. **不要在页面里写 `fontSize = 14.sp`**，除非是"设计上固定"的（GIF 助手的 About 标题 35sp Bold 是刻意的）。
2. 需要弱化的文字用 `onSurfaceVariantSummary`，不要用 `onSurface.copy(alpha = 0.6f)`
   （alpha 叠加在不同背景上结果不可控）。
3. 字号缩放（系统字体大小）不要自己乘系数；Compose 的 `sp` 已经跟随。

---

## 7. 圆角

📌 `GlassDefaults.CornerRadius = 24.dp`（`miuix-material/.../GlassDefaults.kt:14`）；
`CardDefaults.CornerRadius` 是卡片圆角；`squircleClip` / `squircleBackground` 是连续曲率圆角
（`miuix-squircle`，已由 `miuix-ui` 传递）。

规则：

- **卡片**用 `CardDefaults.CornerRadius`，不要写 `RoundedCornerShape(16.dp)`。
- **玻璃卡**的 `textureBlur(shape = RoundedCornerShape(CardDefaults.CornerRadius))` 要与卡片一致，
  否则材质边缘和卡片边缘错位。
- **应用图标**用 `squircleClip(20.dp)` 而不是 `clip(RoundedCornerShape(20.dp))`：
  后者四角不是连续曲率，在 OS4 观感下会"方"。
  ⚠️ GIF 助手在 1.10 的实际代码里**没有**用 `squircleClip`（grep 零命中），
  `build.gradle.kts:32-35` 的注释与代码不一致 —— 这属于**文档漂移**，
  见 [12](12-known-issues-and-solutions.md) 附录 B。
- **按钮圆角**：库默认（`ProgressButton` 移植件是 16dp，官方 `Button` 是 16dp）。

---

## 8. 反模式清单（逐条都能对应真实事故）

| 反模式 | 后果 | 正确做法 |
|---|---|---|
| 页面里写 `Color(0xFF...)` | 深浅色/Monet 下塌 | 用语义角色 |
| `surface` 当卡片色 | Monet 下卡片消失（旧结论）；现在四套都不同值，但仍不建议 | 卡片用 `surfaceContainer` |
| `pageColor()` 当页面底色 | 深色下留一条亮 30 灰阶的色带 | 页面用 `pageBaseColor()` = `surface` |
| 用 `MaterialTheme` 的 `surfaceVariant` 映射 MIUIX `surfaceVariant` | 卡片变灰 | 映射到 `surfaceContainer` |
| 禁用态跨档拼装 | 对比度 1.12:1 | 同档配对 |
| 滑块取色用 `colorScheme.background` | Monet 下不是白色，滑块看不见 | 用 `cardColor()` |
| 自己按明暗传 `GlassDefaults.style(isDark)` | 做出第二套玻璃材质 | 吃默认 `GlassDefaults.Style` |
| 组件里 `isSystemInDarkTheme()` 判明暗 | 手动切深色时不跟随 | `background.luminance() < 0.5f` |
| 分割线用 `HorizontalDivider` 填满卡片 | 与 OS4 设置页不一致 | 卡片内不放分割线，靠 56dp 行高 + 16dp 内边距 |

> 最后一条的完整表述：GIF 助手硬约束 6「卡片内不加 `HorizontalDivider`」，
> 与 KernelSU / HyperOS 设置页一致。

---

## 9. 迁移步骤（可执行）

1. **列清单**：`grep -rn "Color(0x\|Color\.\(White\|Black\|Gray\)\|MaterialTheme.colorScheme" --include=*.kt .`
   把每一处映射到 §2.1 的角色，形成一张表。
2. **建三个函数**（§3.5）：`page()` / `card()` / `stage()`。页面里的背景色只允许调这三个。
3. **替换 `Scaffold(containerColor = …)`**：统一成 `page()`。
4. **替换卡片**：`cardColor()`；如果原来用 Material 的 `surfaceVariant`，注意 §2.1 的陷阱。
5. **禁用态**：全部改成同档配对（§5）。
6. **深色/浅色**：所有 `isSystemInDarkTheme()` 换成 `isDark()`（§3.5）。
7. **验证四种组合**：静态/ Monet × 明/暗，各截一张，检查
   ①页面与卡片有层次 ②说明文字可读 ③禁用按钮可读 ④玻璃垫色无方块。
8. **AMOLED** 单独验（深色 + 纯黑）。

---

## 10. 验证清单

- [ ] 全项目 grep `Color(0x` 只剩主题文件与资源文件
- [ ] 全项目 grep `MaterialTheme` 零命中
- [ ] `containerColor` / 录制层垫色 / 遮挡条底色是同一个函数
- [ ] 四套配色都截图，页面与卡片能区分
- [ ] 禁用态对比度 ≥ 1.4:1（GIF 助手方案 A 的水平）或按 WCAG 要求更高
- [ ] AMOLED 下卡片不是纯黑（否则没有层次）
- [ ] 系统字体放大 1.3× 后没有截断/重叠

---

## 11. Material3 退场策略（新项目几乎都会遇到）

大多数新项目一开始就是 Material3。**不能一天删完**，但也不能长期共存。建议三阶段：

### 阶段 1：共存（只改"外观层"，不改交互层）

- `MiuixTheme` 包在**外层**，`MaterialTheme` 若必须保留则包在内层（只给尚未迁移的页面用）。
- **禁止**在同一屏里混用两套 `Scaffold` / `TopAppBar` —— 两套的 Insets、字体、圆角都不同，会错位。
- 用 import 别名避免同名冲突：

```kotlin
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import androidx.compose.material3.Scaffold as MaterialScaffold
```

### 阶段 2：逐屏替换

按 [04](04-component-mapping.md) §7 的顺序（容器 → 顶栏 → 导航 → 卡片/行 → 弹层 → 输入）。
每替换一屏，就把该屏文件里的 `androidx.compose.material3.*` import 删干净。

### 阶段 3：清理

```bash
# 必须为 0
grep -rn "androidx.compose.material3" --include=*.kt .
# 依赖里也要删掉
grep -rn "compose.material3\|material3" --include=*.kts .
```

注意两个"隐形残留"：

1. **`material-icons-extended`**：Material 图标库，体积大且与 Glass 族线宽冲突。删。
2. **`MaterialTheme` 的 `Typography` / `Shapes`**：如果在自定义组件里读过，替换时会静默回落默认值。
   grep `MaterialTheme.typography` / `MaterialTheme.shapes` 确认零命中。

> 为什么必须删而不是共存：两套设计的**最小行高、字号阶梯、圆角、阴影**都不同。
> 共存的结果是"同一屏里一半像 OS4、一半像 Material"，比全 Material 更糟。
> GIF 助手的对应硬约束：全项目 grep `androidx.compose.material` **零命中**。

---

## 12. 参考

- 官方主题文档：`miuix/docs/guide/theme.md`、`miuix/docs/guide/colors.md`、`miuix/docs/guide/textstyles.md`
- `Colors` 字段清单：`miuix-ui/.../theme/Colors.kt`（GIF 助手逐色插值列全了 53 个，见 `AnimatedTheme.kt:72-129`）
- `ThemeController`：`miuix-ui/.../theme/ThemeController.kt:246-274`
- 三层配色：`.../ui/Widgets.kt:84-138`
- 逐色插值：`.../ui/AnimatedTheme.kt`
- 配色事故：`12-known-issues-and-solutions.md` C14、C32、C37
