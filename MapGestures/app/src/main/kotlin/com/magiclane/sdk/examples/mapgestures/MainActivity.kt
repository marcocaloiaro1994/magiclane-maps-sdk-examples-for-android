/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapgestures

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.examples.mapgestures.databinding.ActivityMainBinding
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    @VisibleForTesting
    lateinit var gemSurfaceView: GemSurfaceView
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()
        gemSurfaceView = binding.gemSurface
        val onReady = {
            binding.gemSurface.mapView?.let { mapView ->
                mapView.onDoubleTouch = {
                    SdkCall.execute {
                        Log.i("Gesture", "onDoubleTouch at (${it.x}, ${it.y}).")
                    }
                }

                mapView.onLongDown = {
                    SdkCall.execute {
                        Log.i("Gesture", "onLongDown at (${it.x}, ${it.y}).")
                    }
                }

                mapView.onMove = { start: Xy, end: Xy ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture",
                            "onMove from (${start.x}, ${start.y}) to (${end.x}, ${end.y}).",
                        )
                    }
                }

                mapView.onPinch = { start1: Xy, start2: Xy, end1: Xy, end2: Xy, center: Xy ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture",
                            "onPinch from " +
                                "(${start1.x}, ${start1.y}) and (${start2.x}, ${start2.y}) " +
                                "to " +
                                "(${end1.x}, ${end1.y}) and (${end2.x}, ${end2.y})" +
                                "center " +
                                "(${center.x}, ${center.y}).",
                        )
                    }
                }

                mapView.onSwipe = { distX: Int, distY: Int, speedMMPerSec: Double ->
                    SdkCall.execute {
                        Log.i(
                            "Gesture",
                            "onSwipe with " +
                                "$distX pixels on X and " +
                                "$distY pixels on Y and " +
                                "the speed of $speedMMPerSec mm/s.",
                        )
                    }
                }

                mapView.onTouch = {
                    SdkCall.execute {
                        Log.i("Gesture", "onTouch at (${it.x}, ${it.y}).")
                    }
                }

                mapView.onTwoTouches = {
                    SdkCall.execute {
                        Log.i("Gesture", "onTwoTouches with middle point (${it.x}, ${it.y}).")
                    }
                }
            }
            EspressoIdlingResource.decrement()
        }
        if (SdkSettings.isMapDataReady) {
            onReady()
        } else {
            SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
                if (!isReady) return@onMapDataReady
                onReady()
            }
        }

        SdkSettings.onApiTokenRejected = {
            /**
             * The TOKEN you provided in the AndroidManifest.xml file was rejected.
             * Make sure you provide the correct value, or if you don't have a TOKEN,
             * check the magiclane.com website, sign up/sign in and generate one.
             */
            showDialog("TOKEN REJECTED")
        }

        if (!Util.isInternetConnected(this)) {
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    @SuppressLint("InflateParams")
    private fun showDialog(text: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(R.id.message).text = text
            findViewById<Button>(R.id.button).setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(view)
            show()
        }
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("MapGesturesIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
