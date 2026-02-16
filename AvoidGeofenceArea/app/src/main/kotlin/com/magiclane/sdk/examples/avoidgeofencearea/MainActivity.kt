/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.avoidgeofencearea

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.CircleGeographicArea
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.Geofence
import com.magiclane.sdk.core.GeofenceArea
import com.magiclane.sdk.core.GeofenceAreaList
import com.magiclane.sdk.core.Login
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.d3scene.EMarkerType
import com.magiclane.sdk.d3scene.Marker
import com.magiclane.sdk.d3scene.MarkerCollection
import com.magiclane.sdk.d3scene.MarkerCollectionRenderSettings
import com.magiclane.sdk.examples.avoidgeofencearea.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.avoidgeofencearea.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var inset = 0

    private val routingService = RoutingService(
        onStarted = {
            showStatusMessage("Calculating route...", withProgress = true)
        },
        onCompleted = onCompleted@{ routes, errorCode, _ ->
            showStatusMessage("Route calculation completed")

            if (errorCode != GemError.NoError) {
                showDialog("Route calculation failed with error ${GemError.getMessage(errorCode, this)}") {
                    finish()
                    exitProcess(0)
                }
                return@onCompleted
            } else {
                SdkCall.execute {
                    if (routes.isNotEmpty()) {
                        binding.gemSurface.mapView?.presentRoute(
                            routes[0],
                            edgeAreaInsets = Rect(inset, inset, inset, inset),
                        )
                    }
                }
            }
        },
    )

    private val geofence = Geofence()

    private var geofenceAreas: GeofenceAreaList = arrayListOf()

    private val addAreasProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            showDialog("Add area to geofence failed with error ${GemError.getMessage(error, this)}") {
                finish()
                exitProcess(0)
            }
        } else {
            SdkCall.execute {
                binding.gemSurface.mapView?.let { mapView ->
                    val polygonSettings =
                        MarkerCollectionRenderSettings(
                            polylineInnerColor = Rgba.magenta(),
                            polygonFillColor = Rgba(255, 0, 0, 128),
                        )
                    polygonSettings.polylineInnerSize = 1.0 // mm
                    val polygonCollection = MarkerCollection(EMarkerType.Polygon, "Polygon")

                    for (geofenceArea in geofenceAreas) {
                        if (geofenceArea.area is CircleGeographicArea) {
                            geofenceArea.area?.centerPoint?.let {
                                val marker = Marker(it, 1000)
                                polygonCollection.add(marker)
                            }
                        }
                    }

                    mapView.preferences?.markers?.add(polygonCollection, polygonSettings)

                    calculateRoute()
                }
            }
        }
    })

    private val loginProgressListener = ProgressListener.create(onCompleted = { error, _ ->
        if (error != GemError.NoError) {
            showDialog("Login failed with error = ${GemError.getMessage(error, this)}") {
                finish()
                exitProcess(0)
            }
        } else {
            SdkCall.execute {
                geofenceAreas = arrayListOf(
                    GeofenceArea(
                        CircleGeographicArea(Coordinates(45.5950875, 25.6359825), 1000),
                        "Area to avoid",
                    ),
                )
                val error = geofence.addAreas(geofenceAreas, addAreasProgressListener)
                if (error != GemError.NoError) {
                    Util.postOnMain {
                        showDialog("Can't add area to geofence. The error is ${GemError.getMessage(error, this)}") {
                            finish()
                            exitProcess(0)
                        }
                    }
                }
            }
        }
    })

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inset = getSizeInPixels(85)

        binding.gemSurface.onSdkInitFailed = { error ->
            val errorMessage = "SDK initialization failed: ${GemError.getMessage(error, this)}"
            Util.postOnMain {
                showDialog(errorMessage) {
                    finish()
                    exitProcess(0)
                }
            }
        }

        binding.gemSurface.onDefaultMapViewCreated = { _ ->
            if (!Util.isInternetConnected(this)) {
                Util.postOnMainDelayed({
                    showStatusMessage("You must be connected to the internet!")
                }, 0)
            }
        }

        SdkSettings.onConnectionStatusUpdated = { isConnected ->
            if (isConnected) {
                SdkCall.execute {
                    val error = Login.registerExternalLogin(
                        "__my_spceial_login_id__",
                        loginProgressListener,
                    )
                    if (error != GemError.NoError) {
                        Util.postOnMain {
                            showDialog("Error registering external login: ${GemError.getMessage(error, this)}") {
                                finish()
                                exitProcess(0)
                            }
                        }
                    }
                }

                SdkSettings.onConnectionStatusUpdated = {}
            }
        }

        SdkSettings.onApiTokenRejected = {
            showDialog(
                "The token you provided was rejected. " +
                    "Make sure you provide the correct value, or if you don't have a token, " +
                    "check the magiclane.com website, sign up / in and generate one. Then input it in the AndroidManifest.xml file.",
            )
        }

        onBackPressedDispatcher.addCallback(this) {
            finish()
            exitProcess(0)
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Brasov", 45.65094531, 25.60403406),
            Landmark("Predeal", 45.5052, 25.5742),
        )

        routingService.preferences.avoidGeofenceAreas = arrayListOf("Area to avoid")

        val error = routingService.calculateRoute(waypoints)
        if (error != GemError.NoError) {
            Util.postOnMain {
                showDialog("Route calculation failed with error ${GemError.getMessage(error, this)}") {
                    finish()
                    exitProcess(0)
                }
            }
        }
    }

    private fun showDialog(text: String, onDismiss: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                onDismiss?.invoke()
                dialog.dismiss()
            }
        }
        dialog.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.isDraggable = false
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    private fun getSizeInPixels(dpi: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpi.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun showStatusMessage(text: String, withProgress: Boolean = false) {
        Util.postOnMain {
            binding.apply {
                if (!statusText.isVisible) {
                    statusText.visibility = View.VISIBLE
                }
                statusText.text = text

                if (withProgress) {
                    statusProgressBar.visibility = View.VISIBLE
                } else {
                    statusProgressBar.visibility = View.GONE
                }
            }
        }
    }
}
