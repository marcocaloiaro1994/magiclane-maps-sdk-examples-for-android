/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.basicshapedrawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.magiclane.sdk.core.RectF
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.d3scene.BasicShapeDrawer
import com.magiclane.sdk.d3scene.Canvas
import com.magiclane.sdk.d3scene.CanvasListener
import com.magiclane.sdk.d3scene.ETextAlignment
import com.magiclane.sdk.d3scene.ETextStyle
import com.magiclane.sdk.d3scene.TextState
import com.magiclane.sdk.examples.basicshapedrawer.databinding.SpeedsLimitsFragmentBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util

class SpeedLimitsPanel : Fragment() {

    private var mBinding: SpeedsLimitsFragmentBinding? = null
    private val binding
        get() = mBinding!!

    private var canvas: Canvas? = null
    private var shapeDrawer: BasicShapeDrawer? = null
    private var canvasListener = CanvasListener()

    private data class LegendRectangle(
        var legendLeft: Float,
        var legendTop: Float,
        var legendRight: Float,
        var legendBottom: Float,
    )

    private var legendRectangle: LegendRectangle = LegendRectangle(0f, 0f, 0f, 0f)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBinding = DataBindingUtil.inflate(layoutInflater, R.layout.speeds_limits_fragment, container, false)

        binding.apply {
            progressBarView.isVisible = true
            gemSurfaceView.onScreenCreated = { screen ->
                val rectF = RectF(0.0f, 0.0f, 1.0f, 1.0f) // 0%, 0%, 100%, 100%
                canvas = Canvas.produce(screen, rectF, canvasListener)
                shapeDrawer = BasicShapeDrawer.produce(canvas)
            }

            gemSurfaceView.onDefaultMapViewCreated = {
                gemSurfaceView.mapView?.onReady = {
                    Util.postOnMain { progressBarView.isVisible = false }
                    SdkCall.execute {
                        gemSurfaceView.mapView?.viewport?.run {
                            legendRectangle.apply {
                                legendLeft = right - 400f
                                legendTop = bottom - 520f
                                legendRight = right.toFloat()
                                legendBottom = bottom.toFloat()
                            }
                        }
                        gemSurfaceView.onDrawFrameCustom = { _ ->
                            shapeDrawer?.apply {
                                drawLimitsPanel()
                                renderShapes()
                            }
                        }
                    }
                }
            }
        }
        return binding.root
    }

    private fun drawLimitsPanel() {
        shapeDrawer?.apply {
            legendRectangle.apply {
                drawRectangle(
                    legendLeft,
                    legendTop,
                    legendRight,
                    legendBottom,
                    Rgba(255, 255, 255, 155).value,
                    true,
                    2f,
                )
                drawText(
                    "Germany speed limits",
                    legendLeft + 20,
                    legendTop + 30,
                    TextState(
                        Rgba(0, 0, 0, 255),
                        fontSize = 35,
                        alignment = ETextAlignment.LeftCenter,
                        style = ETextStyle.BoldStyle,
                    ),
                )
                val left = legendRight - 150
                var top = legendBottom - 120
                val right = legendRight - 20
                var bottom = legendBottom - 20
                // 130
                drawRectangle(left, top, right, bottom, Rgba(30, 50, 200, 225).value, true, 2f)
                drawText(
                    "130",
                    (left + right) / 2,
                    (bottom + top) / 2,
                    TextState(
                        Rgba(255, 255, 255, 255),
                        fontSize = 60,
                        alignment = ETextAlignment.Center,
                        style = ETextStyle.BoldStyle,
                    ),
                )
                drawText(
                    "Autobahn:",
                    legendLeft + 20,
                    (bottom + top) / 2,
                    TextState(
                        Rgba(0, 0, 0, 255),
                        fontSize = 30,
                        alignment = ETextAlignment.LeftCenter,
                        style = ETextStyle.BoldStyle,
                    ),
                )
                // 100
                bottom = top - 40
                top = bottom - 120
                drawCircle(
                    (right + left) / 2,
                    (bottom + top) / 2,
                    70f,
                    Rgba(255, 0, 0, 255).value,
                    true,
                )
                drawCircle(
                    (right + left) / 2,
                    (bottom + top) / 2,
                    55f,
                    Rgba(255, 255, 255, 255).value,
                    true,
                )
                drawText(
                    "100",
                    (left + right) / 2,
                    (bottom + top) / 2,
                    TextState(
                        Rgba(0, 0, 0, 255),
                        fontSize = 60,
                        alignment = ETextAlignment.Center,
                        style = ETextStyle.BoldStyle,
                    ),
                )
                drawText(
                    "Outside cities:",
                    legendLeft + 20,
                    (bottom + top) / 2,
                    TextState(
                        Rgba(0, 0, 0, 255),
                        fontSize = 30,
                        alignment = ETextAlignment.LeftCenter,
                        style = ETextStyle.BoldStyle,
                    ),
                )
                // 50
                bottom = top - 40
                top = bottom - 120
                drawCircle(
                    (right + left) / 2,
                    (bottom + top) / 2,
                    70f,
                    Rgba(255, 0, 0, 255).value,
                    true,
                )
                drawCircle(
                    (right + left) / 2,
                    (bottom + top) / 2,
                    55f,
                    Rgba(255, 255, 255, 255).value,
                    true,
                )
                drawText(
                    "50",
                    (left + right) / 2,
                    (bottom + top) / 2,
                    TextState(
                        Rgba(0, 0, 0, 255),
                        fontSize = 70,
                        alignment = ETextAlignment.Center,
                        style = ETextStyle.BoldStyle,
                    ),
                )
                drawText(
                    "In cities:",
                    legendLeft + 20,
                    (bottom + top) / 2,
                    TextState(
                        Rgba(0, 0, 0, 255),
                        fontSize = 30,
                        alignment = ETextAlignment.LeftCenter,
                        style = ETextStyle.BoldStyle,
                    ),
                )
            }
        }
    }
}
