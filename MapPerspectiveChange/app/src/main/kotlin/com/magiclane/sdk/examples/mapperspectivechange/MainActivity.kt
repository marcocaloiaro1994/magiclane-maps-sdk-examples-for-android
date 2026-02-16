/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapperspectivechange

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EMapViewPerspective
import com.magiclane.sdk.examples.mapperspectivechange.databinding.ActivityMainBinding
import com.magiclane.sdk.util.SdkCall
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var currentPerspective: EMapViewPerspective = EMapViewPerspective.TwoDimensional

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val twoDimensionalText = resources.getString(R.string.two_dimensional)
        val threeDimensionalText = resources.getString(R.string.three_dimensional)

        binding.button.setOnClickListener {
            // Get the map view.
            binding.surfaceView.mapView?.let { mapView ->
                // Establish the current map view perspective.
                currentPerspective = if (currentPerspective == EMapViewPerspective.TwoDimensional) {
                    binding.button.text = twoDimensionalText
                    EMapViewPerspective.ThreeDimensional
                } else {
                    binding.button.text = threeDimensionalText
                    EMapViewPerspective.TwoDimensional
                }

                SdkCall.execute {
                    // Change the map view perspective.
                    mapView.preferences?.setMapViewPerspective(
                        currentPerspective,
                        Animation(EAnimation.Linear, 300),
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }
}
