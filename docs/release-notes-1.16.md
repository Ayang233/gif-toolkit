1.16 主要改动

改名
- 应用名从「GIF 助手」改为 GifToolkit：启动器名称、首页大标题、关于页、许可清单、导出目录（Pictures/GifToolkit、Movies/GifToolkit）都跟着改。工程名与 APK 文件名也改成 GifToolkit。
- 已安装的旧版设置不受影响，本地配置仍然沿用原来的存储文件。

升级
- Miuix 基线升到 e7c108ee（v0.9.5-rc01 + 10 个提交，共 22 个提交）。AGP 同步到 9.5.3；其余工具链（Kotlin 2.4.21、Compose Multiplatform 1.13.0-rc01、compileSdk 37.2）没动。
- 上游这轮的观感变化：顶栏玻璃带变薄（BandOverhang 36dp → 10dp、ProgressiveBlurRadius 80dp → 32dp，渐进曲线改线性）、GlassBottomSheet 的 progressiveBlur 默认关闭。调用方不用改参数。

调整（自造实现换成官方结构）
- 底栏从「一级页自绘浮层 + 复刻 GlassNavigationBar 内部 floatingMargin 公式」改成官方 CompactScreenLayout 的结构：底栏进 Scaffold 的 bottomBar 槽位，高度由 Scaffold 量测后下发，那段抄库内部的公式删掉了。
- 底部主操作条贴底留白改用官方公式 max(24dp, 安全区 + 8dp)，删掉按屏幕圆角解方程的自造算法。
- 页面转场只映射官方 NavTransitions 预设（Miuix 默认 / 底部弹出）。原先自造的三套转场（AOSP 缩放、经典平移、跟随手势缩放）和配套的「退出方向」设置一并删除。
- 「无动画」档改名「关闭手势预览」：只关掉系统预测性返回的手势预览，页面切换仍走 Miuix 默认转场，不会变成硬切。
- 主按钮配色改用 ButtonDefaults.buttonColorsPrimary()；列表补上 overscrollEffect = null；顶栏折叠胶囊的绕行偏移改成函数型 CompositionLocal，避免逐帧重组。

修复
- 底栏高度不再依赖上游私有公式，导航栏较矮的机型（安全区 ≤16dp）不会再多留 8dp 空带。
- 重复实现的 formatSize / formatTime 合并成一处；许可页的手写脚注改用统一的说明段落。

说明
- 平移动画等版式没变，玻璃质感仍建议真机确认。基线的逐项差异见 docs/miuix-baseline-3c560868-to-e7c108ee-diff.md。
