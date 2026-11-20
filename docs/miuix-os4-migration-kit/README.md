# MIUIX OS4 + Glass 跨项目适配知识库

> 本目录是**从 GIF 助手这一个真实工程提炼出来的迁移方法**，目标不是"总结 GIF 助手改了什么"，
> 而是让一个**完全没有 MIUIX 的项目**能照着走到完整的 OS4 + Glass 适配。

- 参考工程：GifHelper 1.10（`versionCode = 12`），Kotlin Multiplatform 单模块，全部实现在 `androidMain`
- MIUIX **证据基线**：`compose-miuix-ui/miuix` @ `3177a684`（2026-11-18，version `0.9.5` 开发线）。
  本知识库的组件行号、默认值、实测截图都取自这个提交，属于**历史证据**，不要当成"当前版本"。
  GifHelper 的**当前 pin** 已跟进到 `5ff41ba9`（2026-11-20），升级过程与差异见
  [`docs/miuix-baseline-3177a684-to-5ff41ba9-diff.md`](../miuix-baseline-3177a684-to-5ff41ba9-diff.md)；
  长期维护流程见 [`docs/miuix-baseline-maintenance.md`](../miuix-baseline-maintenance.md)。
- 本文档集**只描述方法与已验证事实**；GIF 助手的具体业务代码一律标注为「本项目实现」，不作为通用规范

---

## 1. 这套知识库解决什么问题

新项目常见的情况是：能加依赖、能跑起来，但一上 Glass 就出现**闪退、玻璃不模糊、弹层脏斑、
侧栏压住内容、横屏错位、模拟器上看不出来**这几类问题，而每个问题的根因都藏在"录制层与消费者的节点关系"里。
本知识库把这些根因整理成**可判定的检查项**，让代码助手在动手之前就能排除掉大部分坑。

它覆盖 7 件事：

1. 扫描新项目，判断它能不能接 MIUIX（含 Flutter / RN / XML 的可行性判定）。
2. 选对模块与版本，避免把 GIF 助手当前的版本固化成默认值。
3. 把现有颜色、字号、圆角、背景迁到 MIUIX Theme 体系。
4. 把原生 UI 组件逐项映射到 MIUIX 组件（并判断哪些**不该**换）。
5. 建立 Glass 的 backdrop 采样架构（本知识库的重点）。
6. 做导航、自适应、Insets、触觉、About 特效等专项适配。
7. 测试、验收、分级（L0～L6）与回滚。

---

## 2. 阅读顺序

第一次接触本知识库，按这个顺序读：

| 顺序 | 文件 | 读完能回答 |
|---|---|---|
| 1 | [README.md](README.md)（本篇） | 怎么用这套东西、证据等级怎么读 |
| 2 | [01-project-assessment.md](01-project-assessment.md) | 我这个项目能不能接、要先改什么 |
| 3 | [02-version-and-dependencies.md](02-version-and-dependencies.md) | 装哪几个模块、什么版本、有什么坑 |
| 4 | [03-os4-theme-system.md](03-os4-theme-system.md) | 颜色/字号/圆角怎么接、怎么不写死 |
| 5 | [04-component-mapping.md](04-component-mapping.md) | 每个旧控件换成什么 |
| 6 | [05-navigation-and-adaptive.md](05-navigation-and-adaptive.md) | 导航形态、断点、安全区、让位 |
| 7 | [06-glass-architecture.md](06-glass-architecture.md) | backdrop 怎么接、为什么不能自采样 |
| 8 | [07-glass-component-guide.md](07-glass-component-guide.md) | 每个 Glass 组件用在哪、要不要 backdrop |
| 9 | [08-custom-component-migration.md](08-custom-component-migration.md) | 自绘组件怎么改才不破层级 |
| 10 | [09-interaction-and-haptics.md](09-interaction-and-haptics.md) | 哪些反馈是库自带、哪些要自己加 |
| 11 | [10-about-and-special-effects.md](10-about-and-special-effects.md) | About 页、动态渐变、textureBlur |
| 12 | [11-testing-and-acceptance.md](11-testing-and-acceptance.md) | 怎么验、哪些必须真机 |
| 13 | [12-known-issues-and-solutions.md](12-known-issues-and-solutions.md) | 45 个真实案例与红线 |
| 14 | [13-project-adaptation-workflow.md](13-project-adaptation-workflow.md) | 阶段 0～8 与 L0～L6 成熟度 |
| 15 | [18-gif-helper-baseline-audit.md](18-gif-helper-baseline-audit.md) | 参考工程的真实架构基线（对照用） |
| 16 | [19-l0-to-l6-walkthrough.md](19-l0-to-l6-walkthrough.md) | 虚拟新项目的完整推演（自检：手册够不够用） |

要直接开工的代码助手，读 [16-new-project-code-assistant-prompt.md](16-new-project-code-assistant-prompt.md)，
它把上面这些步骤压缩成一份可执行的任务模板（含"只审计"与"实施"两个阶段）。

---

## 3. 文件清单

| 文件 | 内容 | 状态 |
|---|---|---|
| [README.md](README.md) | 使用说明、证据等级、维护规则 | — |
| [01-project-assessment.md](01-project-assessment.md) | 新项目扫描、兼容性判定、技术栈可行性矩阵 | 完成 |
| [02-version-and-dependencies.md](02-version-and-dependencies.md) | 模块职责、依赖决策表、版本策略、构建坑 | 完成 |
| [03-os4-theme-system.md](03-os4-theme-system.md) | ThemeController、ColorScheme、Typography、三层配色 | 完成 |
| [04-component-mapping.md](04-component-mapping.md) | 原生 UI → MIUIX 组件映射与四类处置 | 完成 |
| [05-navigation-and-adaptive.md](05-navigation-and-adaptive.md) | NavigationBar/Rail、断点、Insets、RTL、让位 | 完成 |
| [06-glass-architecture.md](06-glass-architecture.md) | backdrop 录制/采样/消费，接线 9 步，层级红线 | 完成 |
| [07-glass-component-guide.md](07-glass-component-guide.md) | 全部官方 Glass 组件逐项说明 | 完成 |
| [08-custom-component-migration.md](08-custom-component-migration.md) | ProgressButton、主操作条、预览区等迁移流程 | 完成 |
| [09-interaction-and-haptics.md](09-interaction-and-haptics.md) | 触觉反馈归属表、状态语义、无障碍 | 完成 |
| [10-about-and-special-effects.md](10-about-and-special-effects.md) | About 页、OS3 渐变、textureBlur、DstIn | 完成 |
| [11-testing-and-acceptance.md](11-testing-and-acceptance.md) | 三级证据、验收矩阵、回归清单 | 完成 |
| [12-known-issues-and-solutions.md](12-known-issues-and-solutions.md) | 真实案例库（按症状可检索） | 完成 |
| [13-project-adaptation-workflow.md](13-project-adaptation-workflow.md) | 阶段 0～8、L0～L6 定义与评分 | 完成 |
| [14-new-project-audit-template.md](14-new-project-audit-template.md) | 可直接填写的审计模板 | 完成 |
| [15-new-project-migration-template.md](15-new-project-migration-template.md) | 可直接填写的实施计划模板 | 完成 |
| [16-new-project-code-assistant-prompt.md](16-new-project-code-assistant-prompt.md) | 交给新项目代码助手的两阶段提示词 | 完成 |
| [17-source-reference-index.md](17-source-reference-index.md) | 官方源码/文档/提交/参考项目索引 | 完成 |
| [18-gif-helper-baseline-audit.md](18-gif-helper-baseline-audit.md) | GIF 助手 1.10 真实基线（本次审计） | 完成 |
| [19-l0-to-l6-walkthrough.md](19-l0-to-l6-walkthrough.md) | 虚拟项目 L0→L6 推演 + 缺漏修补记录 | 完成 |

---

## 4. 证据等级：先看标记，再信结论

本知识库中每条结论后面尽量跟一个标记。**未标记的视为"源码可读出的结构事实"**。

| 标记 | 含义 | 可信度 |
|---|---|---|
| ✅ | GIF 助手中**构建通过且经过模拟器截图/像素测量/探针实测** | 高（但局限在模拟器） |
| 🟡 | 仅**模拟器**验证（多为 `swiftshader_indirect` 软件渲染，玻璃观感不可信） | 中 |
| 🔴 | **未验证**：只有分析或推断，没有运行证据 | 低，实施时必须补验 |
| 📌 | **官方源码事实**（有 `路径:行号`） | 高 |
| 🏠 | **GIF 助手的本地实现**，不是库 API，不能照抄到别的项目 | 参考 |

三个必须区分的层次（[11-testing-and-acceptance.md](11-testing-and-acceptance.md) §1 详述）：

1. **代码接线正确**：源码审计可判定。
2. **运行时能力具备**：`isRuntimeShaderSupported()` / `isRenderEffectSupported()` 可判定（粒度很粗）。
3. **像素表现正确**：只能真机（硬件 GPU）验证。

> GIF 助手当前状态（1.10）：构建 ✅；模拟器交互 🟡；**真机玻璃观感、`textureBlur`、触觉手感仍是 🔴**。
> 本知识库不把"编译通过"当成"Glass 适配完成"，也不把"模拟器上发灰"当成"代码写错"。

---

## 5. 三条贯穿全库的判断句

遇到任何 Glass 相关问题，先问这三句，80% 的现象能当场定位：

1. **"这个玻璃组件，采样的是哪一层？它在那一层里是谁画的？画了没有？"**
   —— 定位"不模糊 / 深色带 / 错位色斑"。
2. **"这个玻璃组件，是录制层的兄弟还是子节点？"**
   —— 定位"闪退"（`libhwui` 栈溢出）。
3. **"这个尺寸/坐标是在组合期读的，还是量测/绘制期读的？"**
   —— 定位"动画期间卡顿、整页重组、首帧错位"。

---

## 6. 标记约定（正文里怎么区分官方与本地）

- 官方库 API 一律写全限定包名或 `顶层包`，例如 `top.yukonga.miuix.kmp.glass.GlassTopAppBar`。
- GIF 助手自建的东西会写明「🏠 本项目实现」，例如 `Os4ListPage`、`LocalRootBackdrop`、`LocalRailInset`、`rememberPageBackdrop`。
  这些**在库里不存在**，新项目必须按 [06-glass-architecture.md](06-glass-architecture.md) 的步骤**重新设计**，
  不要整段复制。
- 只在官方 `example/` 里存在、`miuix-*` 库里没有的东西，标「📌 example-only」。已知的有：
  `rememberBlurBackdrop`、`rememberCombinedBackdrop`、`LocalTopAppBarBackdrop`、`LocalTopAppBarTitleAlpha`、
  `LocalTopLayer` / `TopLayerHost`、`GlassPageHost`、`AdaptiveTopAppBar`、`shouldShowSplitPane` / `shouldExpandNavigationRail`
  （自适应阈值函数在 example 里，不在库里）。

---

## 7. 维护规则（版本迭代时怎么更新本库）

MIUIX 明确声明自己是 experimental（`miuix/README.md:5`），API 会变。每次新项目适配前必须重新确认版本，流程：

1. 拉取 MIUIX 最新代码，记录 `git rev-parse HEAD` 与 `build-logic/src/main/kotlin/BuildConfig.kt` 里的 `LIBRARY_VERSION`。
2. 对照 [02-version-and-dependencies.md](02-version-and-dependencies.md) §6 的「版本变更检查表」逐项核对：
   compileSdk 要求、`GlassDefaults.Style`、各组件默认值、图标改名。
3. 若发现 API 变化，**先改本库对应章节**，再改项目代码。
4. 在 [17-source-reference-index.md](17-source-reference-index.md) 里追加一条"本次核对的基线"。

三条硬性禁止：

- 不要把 GIF 助手用的 `3177a684` 当成"推荐版本"传播；它只是**本库的证据基线**。
- 不要写本库没有的 API（例如 `GlassSurface` 是工厂函数 `glassSurface`，`GlassPanel` 是 `Modifier.glassPanel`）。
- 不要把只在 `example/` 里的符号写成库 API。

---

## 8. 本库不做什么

- 不改 GIF 助手的业务代码、不升级它的依赖（本轮任务边界）。
- 不提供"一键迁移脚本"：各项目结构差异太大，脚本会把错误结构复制过去。
- 不把 GIF 助手的业务版式（十个工具、作品库、许可页）当成通用设计规范。
- 不承诺"全部页面都要 Glass"：判断依据见 [07-glass-component-guide.md](07-glass-component-guide.md) §8 与
  [13-project-adaptation-workflow.md](13-project-adaptation-workflow.md) §6。
