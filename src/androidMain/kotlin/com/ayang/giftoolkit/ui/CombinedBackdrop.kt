// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
//
// 移植自 Miuix 示例组件 `component/liquid/CombinedBackdrop.kt`（Apache-2.0，
// 见 NOTICE 的 Miuix 条目；上游注释说明它参考自 Kyant0/AndroidLiquidGlass）。
// 本项目只用到「两层录制层按顺序叠成一个采样源」这一件事。

package com.ayang.giftoolkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 把两层 [Backdrop] 叠成**一个**采样源：先画 [first]，再画 [second]。
 *
 * 为什么需要它：关于页的顶栏同时要采两处像素，而这两处各自都有一段时间是空的 —
 *
 * - 渐变层（`BgEffectBackground`）：首屏有像素；列表滚到底后整体淡出成透明。
 * - 正文层（`ContentBackdropBox`）：首屏在顶栏那块区域是透明的（顶部是图标留白）；
 *   滚起来之后才有卡片经过。
 *
 * 只取其中一层，另一状态下顶栏就采到空像素：贴图全透明 → 顶栏画成一条生硬深色带、
 * 返回键的玻璃胶囊变成一小块错位色斑（真机现象见 `_archive/round11/`）。
 * 叠起来以后，任何一个时刻至少有一层有真实像素。
 *
 * 实现只依赖 `Backdrop.drawBackdrop` 的公有契约，和官方示例的
 * `rememberCombinedBackdrop` 是同一份逻辑（本项目不复用示例模块，故抄一份）。
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean =
        first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

/** 记住一个 [CombinedBackdrop]。两层身份不变时复用同一个实例。 */
@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
