package com.ayang.giftoolkit.ui.bg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.runtime.withFrameNanos
import com.ayang.giftoolkit.ui.LocalWideLayout
import com.ayang.giftoolkit.ui.pageColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.material.RuntimeShader
import top.yukonga.miuix.kmp.material.asBrush
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * OS4「关于页」的动态背景。
 *
 * 整段移植自 compose-miuix-ui/miuix 官方示例 `component/effect/`（Apache-2.0，见 NOTICE）：
 * `BgEffectBackground.kt` / `BgEffectPainter.kt` / `BgEffectModifier.kt` / `BgEffectConfig.kt`。
 *
 * 只保留关于页实际要用的那一支：OS3 效果、PHONE/PAD 两套预设、明暗两档。
 * 示例里 OS2 分支和"背景效果"设置面板是演示用的可切换项，GIF 助手没有对应设置，一并删掉。
 *
 * 三处与示例的差异（都是取本项目自己的约定，不是随手改）：
 * 1. 底色铺 `pageColor()` 而不是 `surface` —— 项目里页面底色统一由它推导，见 Widgets.kt。
 * 2. 明暗判定用 `background.luminance()`（项目里全部这么判），不用示例的 `isInDarkTheme()`。
 * 3. 宽/窄屏判定用项目的 `LocalWideLayout`（阈值与官方 AdaptiveUtils 一致）。
 */

internal enum class BgDeviceType { PHONE, PAD }

/** 一组背景配色与其动画参数。字段含义与示例一致，不要据此改数值。 */
internal class BgPreset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
)

private val OS3_PHONE_LIGHT = BgPreset(
    points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
    colors1 = floatArrayOf(1.0f, 0.9f, 0.94f, 1.0f, 1.0f, 0.84f, 0.89f, 1.0f, 0.97f, 0.73f, 0.82f, 1.0f, 0.64f, 0.65f, 0.98f, 1.0f),
    colors2 = floatArrayOf(0.58f, 0.74f, 1.0f, 1.0f, 1.0f, 0.9f, 0.93f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
    colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1.0f, 0.6f, 0.73f, 0.98f, 1.0f, 0.92f, 0.93f, 1.0f, 1.0f, 0.56f, 0.69f, 1.0f, 1.0f),
    colorInterpPeriod = 5.0f,
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.2f,
)

private val OS3_PHONE_DARK = BgPreset(
    points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
    colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
    colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
    colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
    colorInterpPeriod = 8.0f,
    lightOffset = 0.0f,
    saturateOffset = 0.17f,
    pointOffset = 0.4f,
)

private val OS3_PAD_LIGHT = BgPreset(
    points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
    colors1 = floatArrayOf(0.99f, 0.77f, 0.86f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.72f, 0.74f, 1.0f, 1.0f, 0.98f, 0.76f, 0.8f, 1.0f),
    colors2 = floatArrayOf(0.66f, 0.75f, 1.0f, 1.0f, 1.0f, 0.86f, 0.91f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
    colors3 = floatArrayOf(0.97f, 0.79f, 0.85f, 1.0f, 0.65f, 0.68f, 0.98f, 1.0f, 0.66f, 0.77f, 1.0f, 1.0f, 0.72f, 0.73f, 0.98f, 1.0f),
    colorInterpPeriod = 7.0f,
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.2f,
)

private val OS3_PAD_DARK = BgPreset(
    points = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f),
    colors1 = floatArrayOf(0.66f, 0.26f, 0.62f, 0.4f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.14f, 0.18f, 0.55f, 0.5f),
    colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.11f, 0.16f, 0.83f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f),
    colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.5f, 0.11f, 0.16f, 0.83f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.27f, 0.18f, 0.6f, 0.6f),
    colorInterpPeriod = 7.0f,
    lightOffset = 0.0f,
    saturateOffset = 0.0f,
    pointOffset = 0.2f,
)

private fun presetOf(deviceType: BgDeviceType, isDark: Boolean): BgPreset = when (deviceType) {
    BgDeviceType.PHONE -> if (isDark) OS3_PHONE_DARK else OS3_PHONE_LIGHT
    BgDeviceType.PAD -> if (isDark) OS3_PAD_DARK else OS3_PAD_LIGHT
}

/**
 * 关于页的「背景配色状态」：预设 + 逐段推进的色段 + 明暗/设备档位。
 *
 * 抽出来的原因：图标与软件名称要**跟着同一套渐变**变颜色（参考 KernelSU / InstallerX Revived）。
 * 如果各自再建一份配色和动画，就会有两套相位不同的渐变 —— 背景在变、字不变，
 * 或者字变色比背景慢半拍。这里让背景和前景共用一个实例、一个 `colorStage`。
 *
 * 只暴露读取，不暴露修改：色段由 [rememberBgPalette] 内部推进。
 */
@Stable
class BgPalette internal constructor(
    internal val preset: BgPreset,
    internal val deviceType: BgDeviceType,
    internal val isDark: Boolean,
    internal val surface: Color,
    internal val colorStage: Animatable<Float, AnimationVector1D>,
    /** 设备是否支持 RuntimeShader；不支持时背景是纯色，前景也不该再上渐变。 */
    val gradientAvailable: Boolean,
) {
    /**
     * 当前色段的 4 个色标，与背景着色器用的是同一组数（`uColors` 的 4 × RGBA）。
     *
     * 不能直接拿来画字：背景是低饱和的粉/蓝（HSV 饱和度只有 0.1 上下），
     * 同色描上去等于没有字。这里按 HSV 统一压到一档亮度、并给饱和度设下限 ——
     * 色相/色相漂移保留（这才是"跟随背景"），亮度和对比度由固定值兜住：
     * 浅色主题 V=0.42（与页面底色的对比度约 4.5:1）、深色主题 V=0.92（约 8:1），
     * 都高于 WCAG 对大号粗体文本的 3:1 要求。
     */
    internal fun accentColors(): List<Color> {
        val stage = colorStage.value
        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsForCycle(base)
        val end = colorsForCycle(base + 1)
        val targetValue = if (isDark) 0.92f else 0.42f
        val hsv = FloatArray(3)
        return (0 until 4).map { i ->
            val o = i * 4
            val c = Color(
                red = start[o] + (end[o] - start[o]) * fraction,
                green = start[o + 1] + (end[o + 1] - start[o + 1]) * fraction,
                blue = start[o + 2] + (end[o + 2] - start[o + 2]) * fraction,
                alpha = 1f,
            )
            android.graphics.Color.RGBToHSV(
                (c.red * 255f).roundToInt(),
                (c.green * 255f).roundToInt(),
                (c.blue * 255f).roundToInt(),
                hsv,
            )
            hsv[1] = maxOf(hsv[1], 0.55f)
            hsv[2] = targetValue
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }

    private fun colorsForCycle(index: Int): FloatArray = when (index.mod(4)) {
        1 -> preset.colors1
        3 -> preset.colors3
        else -> preset.colors2
    }
}

/**
 * 建立（并推进）关于页背景的配色状态。
 *
 * 背景与"跟随背景"的图标/标题必须共用同一个实例，所以由页面创建后传给 [BgEffectBackground]。
 */
@Composable
fun rememberBgPalette(dynamic: Boolean = true): BgPalette {
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val deviceType = if (LocalWideLayout.current) BgDeviceType.PAD else BgDeviceType.PHONE
    val preset = remember(deviceType, isDark) { presetOf(deviceType, isDark) }
    val surfaceColor = pageColor()
    // 颜色循环：每 period*500ms 推进一个"色段"，段与段之间用弹簧插值。
    val colorStage = remember { Animatable(0f) }
    LaunchedEffect(dynamic, preset, shaderSupported) {
        if (!dynamic || !shaderSupported) return@LaunchedEffect
        val animated = preset.colors1 !== preset.colors2 || preset.colors2 !== preset.colors3
        if (!animated) return@LaunchedEffect
        var target = floor(colorStage.value) + 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500).toLong())
            colorStage.animateTo(target, spring(dampingRatio = 0.9f, stiffness = 35f))
            target += 1f
        }
    }
    return remember(preset, deviceType, isDark, surfaceColor, colorStage, shaderSupported) {
        BgPalette(preset, deviceType, isDark, surfaceColor, colorStage, shaderSupported)
    }
}

/**
 * 关于页的 OS3 动态渐变背景。
 *
 * 背景本身是**静态铺满 + 颜色缓慢轮换**（官方也是这套：底色不动，只有色带在渐变），
 * 内容滚动时靠 [alpha] 整体淡出，让位给列表。
 *
 * 设备不支持 RuntimeShader 时直接退化成普通 Box —— 此时页面就是原来的纯色底，
 * 不抛异常、不留空白（与官方一致）。
 */
@Composable
fun BgEffectBackground(
    modifier: Modifier = Modifier,
    /** 背景之外还要叠的修饰符（本项目用不上，保留以对齐官方签名）。 */
    bgModifier: Modifier = Modifier,
    /**
     * 配色状态。**必须由页面创建后传入**（[rememberBgPalette]），
     * 这样图标 / 软件名称的渐变才能和背景共用同一个色段，不会各跑一套相位。
     */
    palette: BgPalette = rememberBgPalette(),
    /** 整体透明度，随滚动进度递减。 */
    alpha: () -> Float = { 1f },
    content: @Composable (BoxScope.() -> Unit),
) {
    if (!palette.gradientAvailable) {
        Box(modifier = modifier, content = content)
        return
    }
    val painter = remember { BgEffectPainter() }
    val preset = palette.preset
    val surfaceColor = palette.surface
    val colorStage = palette.colorStage

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .bgEffectDraw(
                    painter = painter,
                    preset = preset,
                    deviceType = palette.deviceType,
                    isDark = palette.isDark,
                    surface = surfaceColor,
                    playing = true,
                    colorStage = { colorStage.value },
                    alpha = alpha,
                ),
        )
        content()
    }
}

/**
 * 「跟随背景渐变」的前景填充：把内容（文字字形 / 图标剪影）整体换成当前色段的渐变。
 *
 * 参考实现（KernelSU `AboutMiuix.kt:302`、InstallerX Revived `MiuixAboutPage.kt:493`）
 * 用的是 `textureBlur(backdrop = 背景录制层, contentBlendMode = DstIn)` ——
 * 把**已渲染的背景**采样、与混合色叠一层，再用 `DstIn` 把结果裁进字形。
 * 本机 GPU 上 `textureBlur` 静默不产出像素（官方示例 APK 的关于页因此没有标题、没有卡片，
 * 见 `_archive/round8/off_about3.png`），所以这里不用那条路，改用**同一套颜色**的线性渐变：
 *
 * ```
 * drawContent()                       // 先画字形/剪影，作为 alpha 掩膜
 * drawRect(brush, blendMode = SrcIn)  // 只在有像素的地方铺渐变
 * ```
 *
 * `SrcIn` 与 `DstIn` 在这里等价（都是"保留内容 alpha、替换颜色"），
 * 但不需要 RGBA16F 图层、也不需要 AGSL，任何 GPU 都有像素。
 * 代价是不再逐像素采样背景，渐变位置与背景不完全同步 —— 颜色和相位仍是同一套。
 */
fun Modifier.gradientFilled(brush: () -> Brush?): Modifier =
    this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithCache {
            // 在绘制期求值：色段是每帧插值出来的，放到组合期会整页重组。
            val current = brush()
            onDrawWithContent {
                drawContent()
                if (current != null) {
                    drawRect(brush = current, blendMode = BlendMode.SrcIn)
                }
            }
        }

/** 供 [gradientFilled] 使用的渐变：沿对角线铺开当前色段的 4 个色标。 */
internal fun BgPalette.accentBrush(): Brush =
    Brush.linearGradient(accentColors())

private fun Modifier.bgEffectDraw(
    painter: BgEffectPainter,
    preset: BgPreset,
    deviceType: BgDeviceType,
    isDark: Boolean,
    surface: Color,
    playing: Boolean,
    colorStage: () -> Float,
    alpha: () -> Float,
): Modifier = this then BgEffectElement(painter, preset, deviceType, isDark, surface, playing, colorStage, alpha)

private data class BgEffectElement(
    val painter: BgEffectPainter,
    val preset: BgPreset,
    val deviceType: BgDeviceType,
    val isDark: Boolean,
    val surface: Color,
    val playing: Boolean,
    val colorStage: () -> Float,
    val alpha: () -> Float,
) : ModifierNodeElement<BgEffectNode>() {

    override fun create() = BgEffectNode(painter, preset, deviceType, isDark, surface, playing, colorStage, alpha)

    override fun update(node: BgEffectNode) {
        node.update(painter, preset, deviceType, isDark, surface, playing, colorStage, alpha)
    }
}

private class BgEffectNode(
    private var painter: BgEffectPainter,
    private var preset: BgPreset,
    private var deviceType: BgDeviceType,
    private var isDark: Boolean,
    private var surface: Color,
    private var playing: Boolean,
    private var colorStage: () -> Float,
    private var alpha: () -> Float,
) : Modifier.Node(),
    DrawModifierNode {

    private var animationJob: Job? = null
    private var animTime = 0f
    private var startOffset = 0f

    override fun onAttach() {
        if (playing) startAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    fun update(
        painter: BgEffectPainter,
        preset: BgPreset,
        deviceType: BgDeviceType,
        isDark: Boolean,
        surface: Color,
        playing: Boolean,
        colorStage: () -> Float,
        alpha: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.deviceType = deviceType
        this.isDark = isDark
        this.surface = surface
        this.colorStage = colorStage
        this.alpha = alpha
        if (this.playing != playing) {
            this.playing = playing
            if (playing) startAnimation() else {
                animationJob?.cancel()
                animationJob = null
            }
        }
        invalidateDraw()
    }

    /** 逐帧推进只走 invalidateDraw，不碰组合 —— 与官方一致，避免整页每秒 60 次重组。 */
    private fun startAnimation() {
        animationJob?.cancel()
        startOffset = animTime
        animationJob = coroutineScope.launch {
            val minDeltaNanos = 1_000_000_000L / 60L
            val origin = withFrameNanos { it }
            var lastEmit = origin
            while (isActive) {
                val now = withFrameNanos { it }
                if (now - lastEmit < minDeltaNanos) continue
                lastEmit = now
                animTime = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        val alphaValue = alpha()
        if (alphaValue <= 0f) {
            // 完全透明时停掉逐帧动画：滚动到底后不再空转 GPU。
            animationJob?.cancel()
            animationJob = null
        } else {
            if (playing && animationJob == null) startAnimation()
            val drawHeight = size.height * 0.8f
            painter.updateResolution(size.width, size.height)
            painter.updateBoundIfNeeded(drawHeight, size.height, size.width)
            painter.updatePresetIfNeeded(deviceType, isDark)
            painter.updateColors(preset, colorStage())
            painter.updateAnimTime(animTime)
            painter.updatePointsAnim(animTime, preset)
            drawRect(painter.brush, alpha = alphaValue)
        }
        drawContent()
    }
}

/** 着色器 uniform 的持有者。缓存逻辑与官方一致：值没变就不重复 setUniform。 */
private class BgEffectPainter {

    val runtimeShader by lazy {
        RuntimeShader(OS3_BG_FRAG).also {
            it.setFloatUniform("uTranslateY", 0f)
            it.setFloatUniform("uNoiseScale", 1.5f)
            it.setFloatUniform("uPointRadiusMulti", 1f)
            it.setFloatUniform("uAlphaMulti", 1f)
        }
    }

    val brush: Brush get() = runtimeShader.asBrush()

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val colorsBuffer = FloatArray(16)
    private val pointsAnimBuffer = FloatArray(8)

    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var deviceTypeCached: BgDeviceType? = null
    private var presetApplied = false

    private var cachedLogoHeight = Float.NaN
    private var cachedTotalHeight = Float.NaN
    private var cachedTotalWidth = Float.NaN

    private var cachedColorStage = Float.NaN
    private var cachedColorsPreset: BgPreset? = null

    private var cachedPointsAnimTime = Float.NaN
    private var cachedPointsAnimPreset: BgPreset? = null

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    fun updatePointsAnim(time: Float, preset: BgPreset) {
        if (cachedPointsAnimTime == time && cachedPointsAnimPreset === preset) return
        val offset = preset.pointOffset
        for (i in 0 until 4) {
            val srcX = preset.points[i * 3]
            val srcY = preset.points[i * 3 + 1]
            val animX = srcX + sin(time + srcY) * offset
            val animY = srcY + cos(time + animX) * offset
            pointsAnimBuffer[i * 2] = animX
            pointsAnimBuffer[i * 2 + 1] = animY
        }
        runtimeShader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
        cachedPointsAnimTime = time
        cachedPointsAnimPreset = preset
    }

    fun updateColors(preset: BgPreset, stage: Float) {
        if (cachedColorsPreset === preset && cachedColorStage == stage) return
        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsForCycleIndex(preset, base)
        val end = colorsForCycleIndex(preset, base + 1)
        for (i in 0 until 16) {
            colorsBuffer[i] = start[i] + (end[i] - start[i]) * fraction
        }
        runtimeShader.setFloatUniform("uColors", colorsBuffer)
        cachedColorsPreset = preset
        cachedColorStage = stage
    }

    private fun colorsForCycleIndex(preset: BgPreset, index: Int): FloatArray = when (index.mod(4)) {
        1 -> preset.colors1
        3 -> preset.colors3
        else -> preset.colors2
    }

    fun updateBoundIfNeeded(logoHeight: Float, totalHeight: Float, totalWidth: Float) {
        if (cachedLogoHeight == logoHeight && cachedTotalHeight == totalHeight && cachedTotalWidth == totalWidth) return
        val heightRatio = logoHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
        runtimeShader.setFloatUniform("uBound", bound)
        cachedLogoHeight = logoHeight
        cachedTotalHeight = totalHeight
        cachedTotalWidth = totalWidth
    }

    fun updatePresetIfNeeded(deviceType: BgDeviceType, isDark: Boolean) {
        if (presetApplied && isDarkCached == isDark && deviceTypeCached == deviceType) return
        val preset = presetOf(deviceType, isDark)
        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        isDarkCached = isDark
        deviceTypeCached = deviceType
        presetApplied = true
    }
}
