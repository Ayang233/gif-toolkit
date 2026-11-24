# Miuix 基线 `b00169ba` → `3c560868`（v0.9.5-beta04）差异记录

- **日期**：2026-11-24
- **上游**：`3c560868758f80cd97bbdab2f8e53612ad1c56be`，tag `v0.9.5-beta04`
  （`fix(ui): release overlay touch blocking before exit tail and refine motion`）
- **提交数**：60（`b00169ba..3c560868`）
- **改动规模**：579 文件 / +35221 / −31832；其中 `miuix-*` 库源码 498 文件 / +17232 / −9811，
  其余主要是官方 example 的图标表（`utils/GlassIcons.kt`、`utils/AllIcons.kt`）与文档。
- **工具链**：`gradle/libs.versions.toml` 零变化 —— AGP 9.5.2 / Kotlin 2.4.21 / CMP 1.13.0-rc01 /
  Gradle 9.9.2 / compileSdk 37.2 全部与 `b00169ba` 相同。宿主 `build.gradle.kts` 无需改版本。
- **结论**：`src/` 侧只需跟随两处"官方补齐了同名能力"的替换（`GlassCard`、库默认选中色），
  其余为首屏观感 / 性能变化，编译期零破坏。

## 1. 破坏性变更

| 项 | 内容 | 本项目影响 |
| --- | --- | --- |
| 玻璃图标改名 | `MiuixIcons.Glass.XiaominListen` → `XiaomiListen`；`CeditSelection` → `CheckSelection` | 未使用，零改动 |
| `GlassTopAppBar` band 画笔内部重构 | `TopAppBarBandBrush` 的 `createBandShader` 私有方法删除，改为缓存 `colors` / `topColor`；带高固定为 `bandOverhang + 12dp` | 只吃公开 API（`GlassTopAppBarDefaults.bandBrush`），编译期零影响 |
| `GlassBottomSheet` | 去掉底边描边；无标题/无操作时不再占头部空间（`b00169ba` 已生效，本轮继续收敛） | 用到了标题，行为不变 |

## 2. 本轮新增、且本项目直接受益的官方能力

### 2.1 `miuix-glass` 的 `GlassCard` 被官方用于"页面内容卡"

`GlassCard` 本身是 `b00169ba` 引入的（`#495`），但 beta04 里官方 `example/AboutPage.kt:360/386`
把它用在了**关于页的内容卡**上：

```kotlin
GlassCard(modifier = Modifier.padding(horizontal = 12.dp)) {
    ArrowPreference(title = "View Source", …)
}
```

这与本项目关于页的版式逐字同构（12dp 外边距 + 一组 `ArrowPreference`）。
本项目此前是自造的 `ui/GlassCard`：`Card` + `textureBlur` 采样渐变层，作为
`b00169ba` 那一轮"上游 GlassCard 语义不同、不能替换"的结论，**在 beta04 已不成立**。

本轮处置：删除 `ui/Widgets.kt` 的自造 `GlassCard`，关于页改用
`top.yukonga.miuix.kmp.glass.GlassCard`。官方组件不采样、不模糊，只画一层半透明填充
（浅色 `onSurfaceContainer` α=0x8C/255，深色 α=0x14/255），在整屏渐变上就是官方要的"浮起一组"。

### 2.2 导航选中色：库默认值已经能读到正确的配色模式

`GlassNavigationBar` / `GlassNavigationRail` 的 `selectedColor` 默认值是

```kotlin
if (MiuixTheme.isDynamicColor) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground
```

`isDynamicColor` 读 `LocalColorSchemeMode`。本项目外层 `AnimatedMiuixTheme` 用的是
`MiuixTheme(controller)`，它**提供** `LocalColorSchemeMode`；内层 `MiuixTheme(colors = …)` 只覆盖颜色，
不会覆盖这个 local。所以之前 `App.kt` 按 `app.monetEnabled` 手算的 `selectedColor`
（理由写的是"重载提供不了 LocalColorSchemeMode"）是重复实现，本轮删除，两处都吃默认值。

### 2.3 其它官方行为变化（观感，不需要改代码）

| 位置 | 变化 | 本项目的观感影响 |
| --- | --- | --- |
| `miuix-ui` Dialog / BottomSheet | `DialogMotion`（`Default` / `Glass`）、`SheetMotion` 与 HyperOS 对齐；退出尾段先释放触摸拦截 | 删除确认框、文件操作面板的进出场更接近系统；不传 `motion` 即官方默认 |
| `miuix-ui` `Switch` | 滑块拖拽改为弹簧位置追踪 + 方向手势仲裁 | 设置页开关可拖动，手感变化 |
| `miuix-ui` `Slider` | 新增 Tap-to-Seek、仅滑块按压缩放、归一化弹簧 | 工具页参数滑块可点轨道定位 |
| `miuix-blur` | 1D 模糊着色器特化、仅在最终 pass 反预乘、收紧采样核、跳过零模糊层 | 玻璃质感不变，GPU 开销下降；真机需复核观感 |
| `miuix-squircle` | `0.dp` 快速路径、超大超椭圆改用缓存 `clipPath`、缓存描边路径 | 预览画布与卡片圆角不变 |
| `miuix-preference` | 缓存 preference 条目、跳过空 `endActions` 行 | 长列表滚动更稳 |
| `miuix-nav` | 路由绑定式展开导航（`NavTransitionSource` / `NavExpansionPreset`） | 未使用，保持现有 `backTransition` |

## 3. 官方 example 的同步核对点

升级时必须 diff 的三处（本项目复刻自 example，库不提供）：

```bash
git -C miuix diff b00169ba..3c560868 -- \
  example/shared/src/commonMain/kotlin/utils \
  example/shared/src/commonMain/kotlin/component/liquid \
  example/shared/src/commonMain/kotlin/component/effect
```

- `utils/AdaptiveUtils.kt` 仍然只有一个 `LocalContainerSize` + 840/600/1.2 + 1200，阈值未变。
- `utils/PageUtils.kt` 的 `pageScrollModifiers` / `rememberBlurBackdrop` 未变。
- `component/liquid/CombinedBackdrop.kt` 未变。
- `component/effect/*`（OS3 渐变）未变。

## 4. 验证

- `:assembleDebug` 通过（`ANDROID_HOME` 必需，否则 `../miuix` 子构建报 SDK location not found）。
- 切换基线后 `../miuix/*/build` 已清空，未出现 `class located outside the root directory` 的旧残留问题。
- release 包与装机冒烟见 `docs/audit-self-built-wheels.md` §14。
