# 第十一轮：关于页顶栏玻璃修复、全项目 OS4 / Glass 覆盖性复核

本轮由真机截图触发：关于页顶栏出现一条边缘生硬的深色带，返回键左侧浮着一小块错位的玻璃色斑；
同一张截图里「查看源代码 / 开源许可」卡片压在图标与软件名称之上。

修复对象：`screens/AboutScreen.kt`、`ui/GlassScope.kt`，新增 `ui/CombinedBackdrop.kt`（移植自 Miuix 示例，Apache-2.0）。

版本：1.9（11）。**真机验证结论待补**（本机模拟器是软件渲染，`textureBlur` / `progressiveTextureBlur`
不产出像素，顶栏这两条路径在上面的表现无法在这里复现）。

---

## 0. 现象与判定

真机截图里的三处异常：

| 现象 | 判定 |
|---|---|
| 顶栏下方一条硬边深色带 | **缺陷**：顶栏玻璃采到了空像素 |
| 返回键左侧一小块错位玻璃色斑 | **同一缺陷**的另一种表现（按钮胶囊也采同一层） |
| 卡片压住图标/标题，标题区不淡出 | **缺陷**：正文顶部那块透明占位首帧高度为 0 |

两者都只在真机出现，模拟器上分别表现为"什么都不画"和"只在打开瞬间闪一下"，所以前几轮都没抓到。

---

## 1. 根因一：顶栏采到了空像素

### 1.1 机制

`GlassTopAppBar` 内部是二选一（`GlassTopAppBar.kt:389-460`）：

- `progressiveBlur && backdrop != null` → 用 `progressiveTextureBlur` 采样传入的 backdrop；
- 否则 → 画 `bandBrush` 渐变带。

同时它会建一层 `progressiveBackdrop`（把传入 backdrop 的像素按顶栏坐标拷一份），
并通过 `LocalGlassTopAppBarContext` 把 `buttonBackdrop = progressiveBackdrop ?: rawBackdrop`
给顶栏里的 `GlassIconButton`（返回键）。

也就是说：**顶栏的模糊层与返回键胶囊，采的是同一份像素**。
这份像素为空时，两者一起花屏 —— 正好对应截图里的"深色带 + 错位色斑"。

### 1.2 为什么是空的

关于页的正文录制层是 `rememberGlassScope(opaqueBase = false)`：

- 不垫页面底色（背后是整屏渐变，垫了会盖住）；
- 正文顶部还有 40dp 的图标留白，列表首项是一块**透明**占位。

于是顶栏所在的那块区域，在正文录制层里**什么都没有画**。
`progressiveTextureBlur` 采到的是一张全透明贴图，画出来就是深色带 + 色斑，
而不是"没有模糊"。

同时它也不能改成只采渐变层：渐变层（`BgEffectBackground`）由
`alpha = { 1f - scrollProgress() }` 驱动，**滚到底时会整层淡出**，那时顶栏又没像素可采了。

### 1.3 修复

1. 新增 `ui/CombinedBackdrop.kt`：把两层 `Backdrop` 按顺序叠成一个采样源
   （与官方 `component/liquid/CombinedBackdrop.kt` 同一份逻辑，本项目不复用示例模块，所以移植一份）。
2. `PageTopBar` 增加 `backdrop: Backdrop? = null` 参数，默认仍是 `glass.contentBackdrop`
   （其余页面不受影响）。
3. 关于页传 `rememberCombinedBackdrop(渐变层, 正文层)`：

```kotlin
val contentBackdrop = glass.contentBackdrop
val topBarBackdrop: Backdrop? = when {
    !blurEnabled -> null
    contentBackdrop != null -> rememberCombinedBackdrop(bgBackdrop, contentBackdrop)
    else -> bgBackdrop
}

PageTopBar(..., backdrop = topBarBackdrop)
```

两层都是顶栏的**兄弟节点**（不在顶栏的子树里），因此不会采样到自己（AGENTS 第 1 条）。
任何时刻至少有一层有真实像素：首屏靠渐变层，滚到底靠正文层（卡片）。

这条也写进了 AGENTS 硬约束 1，作为新的**覆盖性约束**：
"采样源必须在组件的整个绘制区域里都有像素"。

---

## 2. 根因二：正文顶部占位首帧高度为 0

### 2.1 机制

关于页的版式是「固定的标题区（Column） + 盖在上面的 LazyColumn」，
LazyColumn 首项是一块透明 Spacer，高度按标题区实测高度算（`headerHeight + ...`）。

`headerHeightPx` 原初值是 `0`，靠 `onSizeChanged` 在第一帧布局后回填。于是：

- 第一帧（以及真机上可能持续到下一帧）Spacer 高度 ≈ 0 → 卡片直接压到图标/标题上；
- 同时 `scrollProgress` 的判据是 `headerHeightPx > 0`，为 0 时恒返回 `0f` →
  标题区**永远不淡出** —— 这就是截图里"卡片盖住标题，但标题还透出来"的原因。

### 2.2 修复

首帧兜底 300dp（与官方 `AboutPage.kt` 的 `logoHeightDp = mutableStateOf(300.dp)` 同值）：

```kotlin
var headerHeightPx by remember { mutableIntStateOf(0) }
val headerHeight = with(density) {
    (if (headerHeightPx > 0) headerHeightPx else 300.dp.roundToPx()).toDp()
}
```

兜底值只影响第一帧：实测回调之后立刻换成真实高度（模拟器上卡片位置在 2 秒截图里已经归位）。

---

## 3. 全项目 OS4 / Glass 覆盖性复核

这一轮换了个查法：不再只看"组件用对没"，而是逐个 Glass 消费点问三件事 ——
**采的是哪一层？那一层在它的绘制区域里有像素吗？在它所有状态下都有像素吗？**

### 3.1 Glass 消费点逐项

| 页面 / 组件 | 采样源 | 该区域有像素吗 | 所有状态下都有吗 | 判定 |
|---|---|---|---|---|
| 一级页顶栏（工具/作品/设置） | 页面正文层（`opaqueBase = true`） | 有（垫了页面底色） | 有 | 通过 |
| 二级页顶栏（工具/主题/许可） | 页面正文层 | 有 | 有 | 通过 |
| **关于页顶栏** | 渐变层 + 正文层（本轮） | 有 | 有（两层互补） | **本轮修复** |
| 返回键胶囊 `GlassIconButton` | 顶栏的 `progressiveBackdrop`（拷贝自上项） | 随上项 | 随上项 | **随上项修复** |
| 底栏 `GlassNavigationBar` | 一级页根录制层（满屏） | 有 | 有 | 通过 |
| 侧栏 `GlassNavigationRail` | 同上 | 有 | 有 | 通过（与官方示例逐像素一致，第十轮 §1.4） |
| 下拉 `GlassDropdownPreference` | `LocalRootBackdrop ?: 页面层` | 有 | 弹层伸到内容外时边缘会退化成页面色 | 通过（第十轮已改） |
| 弹层 `GlassBottomSheet` / `GlassDialog` | `pageOverlay` 传入的 root 层 | 有（满屏） | 有 | 通过 |
| 关于页图标 / 标题 `textureBlur` | 渐变层 `bgBackdrop` | 首屏有 | **滚到底后渐变淡出 → 无** | **已知边界**：图标/标题此时也已淡出（alpha 0），不会被看到 |
| 关于页卡片 | 不用 glass（`cardColor` 0.82） | — | — | 有意保留（模糊失效时入口不能消失） |

### 3.2 OS4 组件使用（本轮复查结论）

沿第十轮覆盖矩阵逐页对了一遍，本轮没有发现新的组件误用。差异只在两条，且都是有意的：

1. **关于页骨架是自建 Scaffold**（不走 `Os4ListPage`）。
   这正是本次顶栏问题的土壤：自建骨架里"顶栏采样源"与"正文录制层"的关系要手写，
   而 `Os4ListPage` 已经把这层关系封装好了。
   **建议（未在本轮实施）**：给 `Os4ListPage` 增加 `opaqueBase` / `rootBackground` / `header` 变体，
   把关于页并进去。本轮只把关系写成参数 + 文档，避免再犯。
2. **关于页卡片不用 `textureBlur`**：参考项目整张卡由模糊层绘制，模糊失效时卡片会连同两个入口一起消失。
   本项目优先保证入口可用，保留 0.82 不透明的 `cardColor`。

### 3.3 本轮没有改动、但值得记住的边界

- 关于页图标/标题用的 `textureBlur` 采渐变层，滚动到底后渐变淡出、采样为空 ——
  但此时它们的 `alpha` 也已经被 `scrollProgress` 推到 0，不可见，所以不构成现象。
  如果以后把它们的淡出阈值改晚，这里会立刻暴露成"花屏"，要一起改。
- 顶栏的渐进模糊在**软件光栅化**环境仍走 `bandBrush`（`isSoftwareRenderer`），
  真机才走 AGSL。

---

## 4. 验证

### 4.1 模拟器（`swiftshader_indirect`）

| 项 | 结果 |
|---|---|
| 编译 / 打包（debug + release） | 通过 |
| 打开关于页无崩溃（`FATAL EXCEPTION` / `StackOverflowError` / `prepareTreeImpl` 均无） | 通过 |
| 卡片不再压住标题、版本号在卡片上方 | 通过（`_archive/round11/v2_about_glasson.png`） |
| 顶栏玻璃的实际观感 | **无法验证**（软件渲染下这条路不产出像素） |

### 4.2 真机待验证（请按这个顺序看）

1. 关于页首屏：顶栏应当**完全透明**，只有返回键的玻璃胶囊；
   不应再有深色横带、也不应有错位色斑。
2. 关于页往下滚：顶栏应长出模糊带，模糊的是卡片内容，边缘是渐隐的（不是硬边）。
3. 关于页首屏：图标与软件名称应是液态玻璃质感（采样背景），二者与渐变同源。
4. 其余页面回归：主页/工具/设置顶栏、底栏、侧栏、下拉、作品操作面板、删除确认。

如果第 1 条仍有色斑，说明还存在第三层采样问题，需要现场抓 `adb logcat` +
`adb shell dumpsys gfxinfo` 与一张原图。

---

## 5. 改动文件

| 文件 | 目的 |
|---|---|
| `ui/CombinedBackdrop.kt`（新增） | 两层录制层叠成一个采样源（移植自 Miuix 示例，Apache-2.0） |
| `ui/GlassScope.kt` | `PageTopBar` 增加 `backdrop` 参数（默认仍是正文层），用于"正文层覆盖不到顶栏"的页面 |
| `screens/AboutScreen.kt` | 顶栏改采「渐变层 + 正文层」；标题区高度首帧兜底 300dp |
| `AGENTS.md` | 硬约束 1 增补"覆盖性约束"；硬约束 15 更新关于页顶栏的采样源说明 |
| `CHANGELOG.md` / `docs/release-notes-1.9.md` | 1.9 条目 |

## 6. 回退

- 只回退顶栏：把 `AboutScreen` 的 `backdrop = topBarBackdrop` 一行删掉，即回到"采正文层"（旧行为）。
- 只回退首帧兜底：把 `headerHeight` 的 `if` 分支去掉。
- 整体回退：`git revert` 本轮提交即可，不涉及资源与依赖变化。
