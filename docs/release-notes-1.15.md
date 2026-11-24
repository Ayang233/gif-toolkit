1.15 主要改动

升级
- Miuix 基线升到 3c560868（v0.9.5-beta04），从 b00169ba 起 60 个提交。工具链没动：AGP 9.5.2、Kotlin 2.4.21、Compose Multiplatform 1.13.0-rc01、compileSdk 37.2。
- 上游这轮的观感变化：弹层进出场换成 HyperOS 那套，Switch 可以拖动，Slider 能点轨道定位，模糊换成特化 1D 着色器。调用方不用改参数。

调整
- 关于页的内容卡、作品页的文件操作面板改用 miuix-glass 的 GlassCard，删掉项目里自造的「Card + textureBlur 采背景」。
- 底栏和侧栏的选中色吃库的默认值；顶栏不再传与默认相同的 bandOverhang 和 backgroundAlpha。
- 上游撤回了 LocalContainerSize，宽屏判定和预览高度上限改回 LocalWindowInfo.current.containerSize + computedStateOf。
- 预览画布、调色板、结果卡的 16dp 内边距走 Card(insideMargin)，圆角用 CardDefaults.CornerRadius。关于页版本号改用 body2。

修复
- Monet 动态取色下切换主题模式不刷新：AnimatedTheme 把每次组合新建的 Colors 当动画 key，动画反复重启，颜色停在起点。改成按颜色值做 key。

说明
- 本轮不加功能，版式没动。基线的逐项差异和核对过程在 docs/miuix-baseline-b00169ba-to-3c560868-diff.md。
- 玻璃质感仍需真机确认；模拟器验证的是布局、启动和交互。
