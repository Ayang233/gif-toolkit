# 14 · 新项目审计模板（阶段 0，只读）

> 复制本篇到 `docs/adaptation/00-baseline.md` 后填写。
> **规则**：每条事实必须有 `路径:行号`；没有源码依据的写"未确认"。
> 本阶段**不修改任何代码**。

---

## A. 元信息

| 项 | 值 |
|---|---|
| 项目名 / 包名 | |
| 审计日期 | |
| 审计者 | |
| 代码版本（commit） | |
| 目标 MIUIX 版本（阶段 1 再定） | |
| 本报告对应的源码快照 | |

---

## B. 技术架构

| 项 | 值 | 依据 |
|---|---|---|
| 语言 | Kotlin / Java / 其它 | |
| UI 框架 | Compose / CMP / XML / Flutter / RN | |
| Compose 类型 | Jetpack / Compose Multiplatform | |
| Kotlin 版本 | | |
| AGP 版本 | | |
| Gradle 版本 | | |
| Compose / CMP 版本 | | |
| compose compiler 插件版本 | | |
| compileSdk（含 minor） | | |
| minSdk | | |
| targetSdk | | |
| JDK / JVM target | | |
| 模块结构 | 单模块 / 多模块（列出） | |
| 依赖管理 | Version Catalog / 直接写版本 | |
| 导航框架 | `miuix-nav` / `androidx.navigation` / 自建 / Fragment | |
| UI 状态管理 | ViewModel / Compose State / 其它 | |
| Theme 管理 | `MaterialTheme` / 自定义 / 无 | |
| 页面组织 | 单 Activity / 多 Activity / Fragment | |
| Insets 处理 | `enableEdgeToEdge` / 手动 / 无 | |
| 第三方 UI 依赖 | 列出 | |

**哪些是项目自己的结构（不能照抄 GIF 助手）**：

```text
（填写：例如 "本项目用单 Activity + androidx.navigation，与 GIF 助手的 miuix-nav 不同"）
```

---

## C. 依赖与冲突

| 依赖 | 版本 | 是否 UI 相关 | 处理建议 |
|---|---|---|---|
| | | | |

**冲突检查**：

- [ ] `grep -rn "androidx.compose.material3\." --include=*.kt .` 命中数：____
- [ ] 是否存在第二套设计体系（Material2 / 自绘 design system / Cupertino）：____
- [ ] 是否存在 Compose 版本冲突告警：____

---

## D. 页面清单

| # | 路由/入口 | 文件:行 | 容器 | 顶栏 | 底栏/侧栏 | 列表类型 | 弹层 | 背景 | 一级页？ | 适合 Glass？ |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | | | | | | | | | | |

> "适合 Glass"按 [07](07-glass-component-guide.md) §8.5 的流程图判，写"是/否 + 一句理由"。

---

## E. 组件清单（grep 结果）

| 类别 | grep 模式 | 命中数 | 代表文件 | 是否有自绘替代 |
|---|---|---|---|---|
| 页面容器 | `Scaffold(` | | | |
| 顶栏 | `TopAppBar(` | | | |
| 导航 | `NavigationBar(\|NavigationRail(\|NavHost` | | | |
| 按钮 | `Button(\|IconButton(` | | | |
| 卡片 | `Card(\|Surface(` | | | |
| 设置行 | `Switch(\|Checkbox(\|RadioButton(\|Slider(` | | | |
| 下拉/菜单 | `DropdownMenu\|ExposedDropdown` | | | |
| 弹层 | `Dialog(\|ModalBottomSheet\|AlertDialog` | | | |
| 输入 | `TextField(` | | | |
| 进度 | `ProgressIndicator` | | | |
| 主题 | `MaterialTheme\|colorScheme` | | | |

---

## F. 导航与返回语义

| 项 | 现状 | 是否需改 |
|---|---|---|
| 导航容器 | | |
| 一级导航形态 | 底栏 / 抽屉 / Rail / 无 | |
| 返回键行为（栈底） | 退出 / 二次确认 / 无处理 | |
| 预测性返回 | 支持 / 不支持 | |
| 转场动画 | 默认 / 自定义 | |
| 状态恢复 | `rememberSaveable` / ViewModel / 无 | |

---

## G. 主题与颜色现状（六问）

1. 颜色来源：____
2. 深浅色策略：____
3. 动态取色：____
4. 字体/字号语义化：____
5. 圆角是否集中：____
6. 品牌色映射建议：____

**硬编码颜色统计**：

```bash
grep -rn "Color(0x" --include=*.kt . | wc -l      # ____ 处
grep -rn "Color\.\(White\|Black\|Gray\)" --include=*.kt . | wc -l   # ____ 处
```

---

## H. 自定义组件清单

| 组件 | 文件:行 | 作用 | 保留理由 | 是否继承 Theme | 是否破坏 Glass 层级 |
|---|---|---|---|---|---|
| | | | | | |

---

## I. 兼容性结论

| 检查项 | 结论 | 说明 |
|---|---|---|
| Kotlin | ✅ / ❌ | |
| Compose | ✅ / ❌ | |
| CMP（若用） | ✅ / ❌ / N/A | |
| minSdk ≥ 24 | ✅ / ❌ | |
| minSdk ≥ 33（Glass 全功能） | ✅ / ❌ | 若 ❌ → 只能做到 L3 |
| compileSdk ≥ MIUIX 要求 | ✅ / ❌ | |
| Kotlin/AGP/CMP 版本 ≥ MIUIX | ✅ / ❌ | |
| 无 material3 冲突 | ✅ / ❌ | |
| 构建可跑 | ✅ / ❌ | |
| 导航可共存/替换 | ✅ / ❌ | |

**最终结论**（三选一，写理由）：

```text
[ ] 能直接接入 MIUIX
[ ] 需先完成 ____（Compose 迁移 / 版本升级 / 依赖清理），再接入
[ ] 不能接入（原因：____）
```

**阻断条件**（若有，逐条）：

1. ____
2.

---

## J. 预估

| 项 | 估计 | 依据 |
|---|---|---|
| 目标等级 | L__ | |
| 需要的阶段 | 2→__ | |
| 最大风险点 | | |
| 玻璃可行性 | 可用 / 仅 OS4 / 不可用 | minSdk + GPU |
| 预计工作量 | 页面数 × 组件数 | |

---

## K. 未确认项

| 项 | 为什么没确认 | 下一步 |
|---|---|---|
| | | |

---

## L. 审计自检

- [ ] 每类事实都有 `路径:行号`
- [ ] 没有把 README / 旧文档当源码证据
- [ ] 区分了"项目自有结构"与"MIUIX 要求"
- [ ] 页面清单的分母是全部导航目的地
- [ ] 组件清单的分母是 grep 命中
- [ ] 没有修改任何文件
