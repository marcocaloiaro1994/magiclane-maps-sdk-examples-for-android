/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikesimulation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.replace
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rgba
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.TAG
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.d3scene.Animation
import com.magiclane.sdk.d3scene.EAnimation
import com.magiclane.sdk.d3scene.EHighlightOptions
import com.magiclane.sdk.d3scene.HighlightRenderSettings
import com.magiclane.sdk.examples.bikesimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.routesandnavigation.EBikeProfile
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.routesandnavigation.Route
import com.magiclane.sdk.sensordatasource.PositionListener
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val REQUEST_PERMISSIONS = 110
        private const val IMAGE_SIZE_PIXELS = 220
        val searchAdapter = SearchAdapter()
    }

    /*    private lateinit var gemSurfaceView: GemSurfaceView
        private lateinit var progressBar: ProgressBar
        private lateinit var searchProgressBar: LinearProgressIndicator
        private lateinit var followCursorButton: FloatingActionButton
        private lateinit var bikeSettingsButton: FloatingActionButton
        private lateinit var searchView: SearchView
        private lateinit var mapSearchBar: SearchBar
        private lateinit var searchList: RecyclerView
        private lateinit var fragment: FragmentContainerView
        private lateinit var cancelSimulationButton: MaterialButton
        private lateinit var noResultsText: MaterialTextView*/

    private val viewModel: MainActivityViewModel by viewModels()

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private val navRoute: Route?
        get() = navigationService.getNavigationRoute(navigationListener)
    private var job: Job? = null

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We will use just the onNavigationStarted method, but for more available
     * methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = {
            SdkCall.execute {
                binding.gemSurfaceView.mapView?.let { mapView ->
                    mapView.preferences?.enableCursor = false
                    navRoute?.let { route ->
                        mapView.presentRoute(route)
                        val remainingDistance = route.getTimeDistance(true)?.totalDistance ?: 0
                        Toast.makeText(
                            this@MainActivity,
                            "Distance to destination $remainingDistance m",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    mapView.followPosition()
                }
            }
            binding.apply {
                mapSearchBar.isVisible = false
                bikeSettingsButton.isVisible = false
                cancelSimulationButton.isVisible = true
            }
        },
        onDestinationReached = {
            binding.apply {
                mapSearchBar.isVisible = true
                bikeSettingsButton.isVisible = true
                cancelSimulationButton.isVisible = false
            }
        },
        onNavigationError = { err ->
            if (err != GemError.Cancel) {
                showDialog(GemError.getMessage(err))
            }
            binding.apply {
                mapSearchBar.isVisible = true
                bikeSettingsButton.isVisible = true
                cancelSimulationButton.isVisible = false
            }
        },
    )

    // Define a listener that will let us know the progress of the routing process.
    private val routingProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            binding.progressBar.visibility = View.GONE
        },

        postOnMain = true,
    )

    private val searchService = SearchService()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspressoIdlingResource.increment()
        val onReady = {
            viewModel.initPreferences()
            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            searchAdapter.setOnViewHolderClickListener { item ->
                if (item.lat == null || item.lon == null) return@setOnViewHolderClickListener
                binding.mapSearchView.hide()
                viewModel.destination = item
                SdkCall.execute {
                    binding.gemSurfaceView.mapView?.centerOnCoordinates(
                        Coordinates(item.lat, item.lon),
                    )
                }
                binding.mapSearchBar.hint = "To: " + item.text
            }

            binding.apply {
                gemSurfaceView.mapView?.onTouch = { xy ->
                    centerOnLocation(xy)
                    mapSearchBar.hint = "To: " + viewModel.destination?.text
                }

                bikeSettingsButton.isVisible = true
                followCursorButton.isVisible = true
            }
            enableGPSButton()
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

        requestPermissions(this)

        if (!Util.isInternetConnected(this)) {
            showDialog("You must be connected to the internet!")
        }
        binding.mapSearchView.editText.addTextChangedListener {
            binding.searchProgressBar.isInvisible = it.isNullOrBlank()
            binding.noResultsTextView.isVisible = !it.isNullOrEmpty()
            job?.run { if (isActive) cancel() }
            job = CoroutineScope(Dispatchers.IO).launch {
                delay(500)
                SdkCall.execute {
                    searchService.cancelSearch()
                }
                SdkCall.postAsync(
                    {
                        searchService.searchByFilter(
                            textFilter = binding.mapSearchView.text.trim().toString(),
                            onCompleted = { results, errorCode, hint ->
                                if (GemError.isError(errorCode)) {
                                    showDialog(hint)
                                    return@searchByFilter
                                }
                                SdkCall.execute {
                                    val list = results.map { landmark ->
                                        SearchResultItem(
                                            landmark.image?.asBitmap(
                                                IMAGE_SIZE_PIXELS,
                                                IMAGE_SIZE_PIXELS,
                                            ),
                                            GemUtil.formatName(landmark),
                                            landmark.coordinates?.latitude,
                                            landmark.coordinates?.longitude,
                                        )
                                    }.toMutableList()
                                    viewModel.searchResultListLivedata.postValue(list)
                                }
                            },
                        )
                    },
                    200,
                )
            }
        }
        viewModel.isElectricBikeProfile.observe(this) {
            invalidateOptionsMenu()
        }
        setSupportActionBar(binding.mapSearchBar)

        binding.searchResultsList.apply {
            adapter = searchAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        viewModel.searchResultListLivedata.observe(this) {
            searchAdapter.submitList(it)
            binding.searchProgressBar.isInvisible = true
            binding.noResultsTextView.isVisible = it.isEmpty()
        }

        binding.bikeSettingsButton.setOnClickListener {
            supportFragmentManager.beginTransaction().replace<BikeSettingsFragment>(
                R.id.fragment_container,
            ).commit()
        }
        binding.cancelSimulationButton.setOnClickListener {
            SdkCall.execute {
                navigationService.cancelNavigation(navigationListener)
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (binding.fragmentContainer.getFragment<BikeSettingsFragment?>() != null) {
                supportFragmentManager.beginTransaction().remove(binding.fragmentContainer.getFragment()).commit()
                return@addCallback
            }
            finish()
            exitProcess(0)
        }
    }

    private fun centerOnLocation(xy: Xy) = SdkCall.execute {
        val centerXy = binding.gemSurfaceView.mapView!!.viewport!!.center
        binding.gemSurfaceView.mapView?.cursorScreenPosition = xy
        binding.gemSurfaceView.mapView?.deactivateAllHighlights()
        val landmarks = binding.gemSurfaceView.mapView?.cursorSelectionLandmarks
        if (!landmarks.isNullOrEmpty()) {
            val landmark = landmarks[0]
            landmark.coordinates?.let {
                viewModel.destination = SearchResultItem(
                    landmark.image?.asBitmap(IMAGE_SIZE_PIXELS, IMAGE_SIZE_PIXELS),
                    GemUtil.formatName(landmark),
                    it.latitude,
                    it.longitude,
                )
            }
            val contour = landmark.getContourGeographicArea()
            if (contour != null && !contour.isEmpty()) {
                contour.let {
                    binding.gemSurfaceView.mapView?.centerOnArea(
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

                    binding.gemSurfaceView.mapView?.activateHighlightLandmarks(
                        landmark,
                        displaySettings,
                    )
                }
            } else {
                landmark.coordinates?.let {
                    binding.gemSurfaceView.mapView?.centerOnCoordinates(
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
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.run {
            val electric = findItem(R.id.e_bike_type)
            electric.isVisible = viewModel.isElectric
            val notElectric = findItem(R.id.bike_type)
            notElectric.isVisible = !viewModel.isElectric
            val icon = if (viewModel.isElectric) electric else notElectric
            icon.icon = ContextCompat.getDrawable(
                this@MainActivity,
                when (viewModel.bikeProfile) {
                    EBikeProfile.City -> if (viewModel.isElectric) R.drawable.ebikecity else R.drawable.bikecity
                    EBikeProfile.Cross -> if (viewModel.isElectric) R.drawable.ebikecross else R.drawable.bikecross
                    EBikeProfile.Mountain -> if (viewModel.isElectric) R.drawable.ebikemountain else R.drawable.bikemountain
                    EBikeProfile.Road -> if (viewModel.isElectric) R.drawable.ebikeroad else R.drawable.bikeroad
                },
            )
        }
        binding.mapSearchBar.setOnMenuItemClickListener { menuItem: MenuItem? ->
            val bikeTypeButton = binding.mapSearchBar.menu.findItem(
                if (viewModel.isElectric) R.id.e_bike_type else R.id.bike_type,
            )
            when (menuItem?.itemId) {
                R.id.bike_city, R.id.e_bike_city -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikecity else R.drawable.bikecity,
                    )
                    viewModel.setBikeProfile(EBikeProfile.City)
                }

                R.id.bike_cross, R.id.e_bike_cross -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikecross else R.drawable.bikecross,
                    )
                    viewModel.setBikeProfile(EBikeProfile.Cross)
                }

                R.id.bike_mountain, R.id.e_bike_mountain -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikemountain else R.drawable.bikemountain,
                    )
                    viewModel.setBikeProfile(EBikeProfile.Mountain)
                }

                R.id.bike_road, R.id.e_bike_road -> {
                    bikeTypeButton.icon = ContextCompat.getDrawable(
                        this,
                        if (viewModel.isElectric) R.drawable.ebikeroad else R.drawable.bikeroad,
                    )
                    viewModel.setBikeProfile(EBikeProfile.Road)
                }

                R.id.start_simulation -> {
                    viewModel.destination?.let {
                        startSimulation()
                    } ?: Toast.makeText(
                        this,
                        R.string.choose_destination,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                else -> {}
            }
            true
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the SDK.
        GemSdk.release()
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.apply {
            gemSurfaceView.mapView?.apply {
                onExitFollowingPosition = {
                    followCursorButton.visibility = View.VISIBLE
                }

                onEnterFollowingPosition = {
                    followCursorButton.visibility = View.GONE
                }

                // Set on click action for the GPS button.
                followCursorButton.setOnClickListener {
                    SdkCall.execute { followPosition() }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        for (item in grantResults) {
            if (item != PackageManager.PERMISSION_GRANTED) {
                finish()
                exitProcess(0)
            }
        }

        SdkCall.execute {
            // Notice permission status had changed
            PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)
            enableGPSButton()
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

    private fun startSimulation() {
        val startNavTask = {
            val hasPermissions =
                PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

            if (hasPermissions && PositionService.position != null) {
                val startLat = PositionService.position?.latitude
                val startLon = PositionService.position?.longitude
                val waypoints = viewModel.destination!!.run {
                    arrayListOf(
                        Landmark("Here", startLat!!, startLon!!),
                        Landmark(text!!, lat!!, lon!!),
                    )
                }
                // Cancel any navigation in progress.
                navigationService.cancelNavigation(navigationListener)
                // Start the new navigation.
                val error = navigationService.startSimulation(
                    waypoints,
                    navigationListener,
                    routingProgressListener,
                    viewModel.routePreferences,
                    5f,
                )
                Log.i(TAG, "MainActivity.startNavigation: after = $error")
            }
        }

        SdkCall.execute {
            lateinit var positionListener: PositionListener
            if (PositionService.position?.isValid() == true) {
                startNavTask()
            } else {
                positionListener = PositionListener {
                    if (!it.isValid()) return@PositionListener

                    PositionService.removeListener(positionListener)
                    startNavTask()
                }

                // listen for first valid position to start the nav
                PositionService.addListener(positionListener)
            }
        }
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
    val espressoIdlingResource = CountingIdlingResource("BikeSimulationTestsIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
