/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.mapselection

import android.Manifest
import android.R.attr.description
import android.R.attr.name
import android.R.attr.text
import android.R.attr.visibility
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Size
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.d3scene.MapSceneObject
import com.magiclane.sdk.examples.mapselection.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.routesandnavigation.RoutingService
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var gemSurfaceView: GemSurfaceView

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var routesList = ArrayList<Route>()

    private var imageSize = 0

    private val routingService = RoutingService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { routes, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError -> {
                    routesList = routes

                    SdkCall.execute {
                        gemSurfaceView.mapView?.presentRoutes(routes, displayBubble = true)
                        gemSurfaceView.mapView?.preferences?.routes?.mainRoute?.let {
                            selectRoute(
                                it,
                            )
                        }
                    }
                    binding.flyToRoutesButton.visibility = View.VISIBLE
                }

                GemError.Cancel -> {
                    // The routing action was cancelled.
                }

                else -> {
                    // There was a problem at computing the routing operation.
                    showDialog("Routing service error: ${GemError.getMessage(errorCode)}")
                }
            }
            EspressoIdlingResource.decrement()
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gemSurfaceView = binding.gemSurface
        binding.flyToRoutesButton.also {
            it.setOnClickListener {
                SdkCall.execute {
                    gemSurfaceView.mapView?.let { mapView ->
                        mapView.deactivateAllHighlights()
                        mapView.preferences?.routes?.mainRoute?.let { mainRoute ->
                            selectRoute(mainRoute)
                        }
                    }
                }
            }
        }

        imageSize = resources.getDimension(R.dimen.image_size).toInt()

        EspressoIdlingResource.increment()
        val onReady = {
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            calculateRoute()

            // Set GPS button if location permission is granted, otherwise request permission
            SdkCall.execute {
                val hasLocationPermission =
                    PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                if (hasLocationPermission) {
                    Util.postOnMain { enableGPSButton() }
                } else {
                    requestPermissions(this)
                }
            }

            // onTouch event callback
            binding.gemSurface.mapView?.onTouch = { xy ->
                // xy are the coordinates of the touch event
                SdkCall.execute {
                    // tell the map view where the touch event happened
                    gemSurfaceView.mapView?.cursorScreenPosition = xy
                    gemSurfaceView.mapView?.deactivateAllHighlights()

                    val centerXy =
                        Xy(gemSurfaceView.measuredWidth / 2, gemSurfaceView.measuredHeight / 2)

                    val myPosition = gemSurfaceView.mapView?.cursorSelectionSceneObject
                    if (myPosition != null && isSameMapScene(
                            myPosition,
                            MapSceneObject.getDefPositionTracker().first!!,
                        )
                    ) {
                        showOverlayContainer(
                            getString(R.string.my_position),
                            "",
                            ContextCompat.getDrawable(this, R.drawable.ic_current_location_arrow)
                                ?.toBitmap(imageSize, imageSize),
                        )

                        myPosition.coordinates?.let {
                            gemSurfaceView.mapView?.centerOnCoordinates(
                                it,
                                -1,
                                centerXy,
                                Animation(EAnimation.Linear),
                                Double.MAX_VALUE,
                                0.0,
                            )
                        }

                        return@execute
                    }

                    val landmarks = gemSurfaceView.mapView?.cursorSelectionLandmarks
                    if (!landmarks.isNullOrEmpty()) {
                        val landmark = landmarks[0]
                        landmark.run {
                            showOverlayContainer(
                                name.toString(),
                                description.toString(),
                                image?.asBitmap(imageSize, imageSize),
                            )
                        }

                        val contour = landmark.getContourGeographicArea()
                        if (contour != null && !contour.isEmpty()) {
                            contour.let {
                                gemSurfaceView.mapView?.centerOnArea(
                                    it,
                                    -1,
                                    centerXy,
                                    Animation(EAnimation.Linear),
                                )

                                val displaySettings = HighlightRenderSettings(
                                    EHighlightOptions.ShowContour,
                                    Rgba(255, 98, 0, 255),
                                    Rgba(255, 98, 0, 255),
                                    0.75,
                                )

                                gemSurfaceView.mapView?.activateHighlightLandmarks(
                                    landmark,
                                    displaySettings,
                                )
                            }
                        } else {
                            landmark.coordinates?.let {
                                gemSurfaceView.mapView?.centerOnCoordinates(
                                    it,
                                    -1,
                                    centerXy,
                                    Animation(EAnimation.Linear),
                                    Double.MAX_VALUE,
                                    0.0,
                                )
                            }
                        }

                        return@execute
                    }

                    val trafficEvents = gemSurfaceView.mapView?.cursorSelectionTrafficEvents
                    if (!trafficEvents.isNullOrEmpty()) {
                        hideOverlayContainer()
                        openWebActivity(trafficEvents[0].previewUrl.toString())

                        return@execute
                    }

                    val overlays = gemSurfaceView.mapView?.cursorSelectionOverlayItems
                    if (!overlays.isNullOrEmpty()) {
                        val overlay = overlays[0]
                        if (overlay.overlayInfo?.uid == ECommonOverlayId.Safety.value) {
                            hideOverlayContainer()
                            openWebActivity(overlay.getPreviewUrl(Size()).toString())
                        } else {
                            overlay.run {
                                showOverlayContainer(
                                    name.toString(),
                                    overlayInfo?.name.toString(),
                                    image?.asBitmap(imageSize, imageSize),
                                )
                            }

                            overlay.coordinates?.let {
                                gemSurfaceView.mapView?.centerOnCoordinates(
                                    it,
                                    -1,
                                    centerXy,
                                    Animation(EAnimation.Linear),
                                    Double.MAX_VALUE,
                                    0.0,
                                )
                            }
                        }

                        return@execute
                    }

                    // get the visible routes at the touch event point
                    val routes = gemSurfaceView.mapView?.cursorSelectionRoutes
                    // check if there is any route
                    if (!routes.isNullOrEmpty()) {
                        // set the touched route as the main route and center on it
                        val route = routes[0]
                        selectRoute(route)

                        return@execute
                    }
                }
            }
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

        // Deinitialize the SDK.
        GemSdk.release()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                showDialog(
                    "Location permission required for current position",
                )
                return
            }
        }

        SdkCall.execute {
            // Notify permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

            lateinit var positionListener: PositionListener
            if (PositionService.position?.isValid() == true) {
                Util.postOnMain { enableGPSButton() }
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    Util.postOnMain { enableGPSButton() }
                }
                PositionService.addListener(positionListener, EDataType.Position)
            }
        }
    }

    private fun calculateRoute() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("London", 51.5073204, -0.1276475),
            Landmark("Paris", 48.8566932, 2.3514616),
        )

        routingService.calculateRoute(waypoints)
    }

    private fun isSameMapScene(first: MapSceneObject, second: MapSceneObject): Boolean =
        first.maxScaleFactor == second.maxScaleFactor &&
            first.scaleFactor == second.scaleFactor &&
            first.visibility == second.visibility &&
            first.coordinates?.latitude == second.coordinates?.latitude &&
            first.coordinates?.longitude == second.coordinates?.longitude &&
            first.coordinates?.altitude == second.coordinates?.altitude &&
            first.orientation?.x == second.orientation?.x &&
            first.orientation?.y == second.orientation?.y &&
            first.orientation?.z == second.orientation?.z &&
            first.orientation?.w == second.orientation?.w

    private fun showOverlayContainer(name: String, description: String, image: Bitmap?) = Util.postOnMain {
        binding.apply {
            if (!overlayContainer.isVisible) {
                overlayContainer.visibility = View.VISIBLE
            }

            nameView.text = name
            if (description.isNotEmpty()) {
                descriptionView.apply {
                    text = description
                    visibility = View.VISIBLE
                }
            } else {
                this.descriptionView.visibility = View.GONE
            }

            this.overlayImage.setImageBitmap(image)
        }
    }

    private fun hideOverlayContainer() = Util.postOnMain { binding.overlayContainer.visibility = View.GONE }

    private fun openWebActivity(url: String) {
        val intent = Intent(this, WebActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        gemSurfaceView.mapView?.apply {
            val isFollowingPosition = SdkCall.execute { isFollowingPosition() }
            binding.followCursorButton.visibility = if (isFollowingPosition == true) {
                View.GONE
            } else {
                View.VISIBLE
            }

            onExitFollowingPosition = {
                binding.followCursorButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                binding.followCursorButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            binding.followCursorButton.setOnClickListener {
                SdkCall.execute {
                    deactivateAllHighlights()
                    followPosition()
                }
            }
        }
    }

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray(),
        )
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

    private fun selectRoute(route: Route) {
        gemSurfaceView.mapView?.apply {
            route.apply {
                showOverlayContainer(
                    summary.toString(),
                    "",
                    ContextCompat.getDrawable(
                        this@MainActivity,
                        if (isDarkThemeOn()) {
                            R.drawable.ic_baseline_route_24_night
                        } else {
                            R.drawable.ic_baseline_route_24
                        },
                    )?.toBitmap(imageSize, imageSize),
                )
            }
            preferences?.routes?.mainRoute = route
        }

        gemSurfaceView.mapView?.centerOnRoutes(routesList)
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}

//region TESTING
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("MapSelectionIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
