/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.basicshapedrawer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.RectF
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.BasicShapeDrawer
import com.magiclane.sdk.d3scene.Canvas
import com.magiclane.sdk.d3scene.CanvasListener
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ETextAlignment
import com.magiclane.sdk.d3scene.ETextStyle
import com.magiclane.sdk.d3scene.TextState
import com.magiclane.sdk.examples.basicshapedrawer.Utils.showDialog
import com.magiclane.sdk.examples.basicshapedrawer.databinding.FragmentPopulationBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.routesandnavigation.EImageFileFormat
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

class Population : Fragment() {

    companion object {
        const val LONDON_LAT = 51.507322
        const val LONDON_LON = -0.127647
        const val PARIS_LAT = 48.856697
        const val PARIS_LON = 2.351462
        const val LONDON_POPULATION = " population: ~ 9,748,000 "
        const val PARIS_POPULATION = " population: ~ 11,276,701"
    }

    private var mBinding: FragmentPopulationBinding? = null
    private val binding
        get() = mBinding!!

    private var canvas: Canvas? = null
    private var shapeDrawer: BasicShapeDrawer? = null
    private var canvasListener = CanvasListener()
    private var animationEnded = false
    private lateinit var xyLondon: Xy
    private lateinit var xyParis: Xy

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mBinding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_population, container, false)

        binding.apply {
            progressBar.isVisible = true

            gemSurfaceView.onScreenCreated = { screen ->
                val rectF = RectF(0.0f, 0.0f, 1.0f, 1.0f) // 0%, 0%, 100%, 100%
                canvas = Canvas.produce(screen, rectF, canvasListener)
                shapeDrawer = BasicShapeDrawer.produce(canvas)
                setupShapeDrawer()
            }
            gemSurfaceView.onDefaultMapViewCreated = {
                Util.postOnMain { progressBar.isVisible = false }
                xyLondon = Xy(0, 0)
                xyParis = Xy(0, 0)
                gemSurfaceView.mapView!!.apply {
                    centerOnCoordinates(
                        Coordinates(PARIS_LAT, PARIS_LON),
                        zoomLevel = 20,
                        animation = Animation(
                            EAnimation.Linear,
                            duration = 0,
                            onCompleted = { err, msg ->
                                if (err != GemError.NoError) {
                                    showDialog(msg, requireActivity())
                                    return@Animation
                                }
                                animationEnded = true
                                SdkCall.execute {
                                    xyLondon = gemSurfaceView.mapView!!.transformWgsToScreen(
                                        Coordinates(LONDON_LAT, LONDON_LON),
                                    )!!
                                    xyParis = gemSurfaceView.mapView!!.transformWgsToScreen(
                                        Coordinates(PARIS_LAT, PARIS_LON),
                                    )!!
                                }
                            },
                        ),
                    )
                }
                gemSurfaceView.mapView?.onMove = { _, _ ->
                    if (animationEnded) {
                        SdkCall.execute {
                            xyLondon = gemSurfaceView.mapView!!.transformWgsToScreen(
                                Coordinates(LONDON_LAT, LONDON_LON),
                            )!!
                            xyParis = gemSurfaceView.mapView!!.transformWgsToScreen(
                                Coordinates(PARIS_LAT, PARIS_LON),
                            )!!
                        }
                    }
                }
                gemSurfaceView.mapView?.onPinch = { _, _, _, _, _ ->
                    if (animationEnded) {
                        SdkCall.execute {
                            xyLondon = gemSurfaceView.mapView!!.transformWgsToScreen(
                                Coordinates(LONDON_LAT, LONDON_LON),
                            )!!
                            xyParis = gemSurfaceView.mapView!!.transformWgsToScreen(
                                Coordinates(PARIS_LAT, PARIS_LON),
                            )!!
                        }
                    }
                }
            }
        }
        return binding.root
    }

    private fun setupShapeDrawer() {
        val w = 125f
        val h = 50f
        val idUKFlag = createFlagTexture("uk_flag.jpeg", w.toInt(), h.toInt())
        val idFranceFlag = createFlagTexture("france_flag.jpeg", w.toInt(), h.toInt())
        val textState = SdkCall.execute {
            TextState().apply {
                style = ETextStyle.BoldStyle
                innerColor = Rgba(0, 0, 255, 255)
                outerColor = Rgba(255, 255, 255, 255)
                outerWidth = 2f
                fontSize = 40
                alignment = ETextAlignment.LeftCenter
            }
        }!!
        mBinding?.gemSurfaceView?.onDrawFrameCustom = { _ ->
            shapeDrawer?.run {
                if (idUKFlag != -1) {
                    val left = xyLondon.x - w
                    val top = xyLondon.y - h
                    val bottom = top + h
                    val right = left + w
                    drawTexturedRectangle(
                        idUKFlag,
                        left,
                        top,
                        right,
                        bottom,
                        Rgba(255, 255, 255, 255).value,
                        true,
                    )
                    drawText(LONDON_POPULATION, right, top + (bottom - top) / 2, textState)
                }
                if (idFranceFlag != -1) {
                    val left = xyParis.x - w
                    val top = xyParis.y - h
                    val bottom = top + h
                    val right = left + w
                    drawTexturedRectangle(
                        idFranceFlag,
                        left,
                        top,
                        right,
                        bottom,
                        Rgba(255, 255, 255, 255).value,
                        true,
                    )
                    drawText(PARIS_POPULATION, right, top + (bottom - top) / 2, textState)
                }
                renderShapes()
            }
        }
    }

    private fun createFlagTexture(fileName: String, width: Int, height: Int): Int {
        val imgDataBuffer = getImageDataBuffer(fileName)
        var id: Int = -1
        SdkCall.execute {
            Image.produceWithDataBuffer(
                imgDataBuffer,
                EImageFileFormat.Jpeg,
            )?.apply {
                size?.width = 120
                size?.height = 40
            }?.let { texture ->
                shapeDrawer?.createTexture(texture, width, height)?.let { id = it }
            }
        }
        return id
    }

    private fun getImageDataBuffer(fileName: String): DataBuffer {
        try {
            val stream = ByteArrayOutputStream()
            val bitmap = requireActivity().assets.open(
                fileName,
            ).use { BitmapFactory.decodeStream(it) }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            val imageArray = stream.toByteArray()
            return SdkCall.execute { DataBuffer(byteArray = imageArray) }!!
        } catch (e: FileNotFoundException) {
            showDialog(e.message.toString(), requireActivity())
            return SdkCall.execute { DataBuffer() }!!
        }
    }
}
