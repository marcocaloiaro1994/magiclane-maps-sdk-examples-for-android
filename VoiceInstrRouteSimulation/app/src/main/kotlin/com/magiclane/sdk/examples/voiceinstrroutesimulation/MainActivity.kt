/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voiceinstrroutesimulation

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.voiceinstrroutesimulation.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.voiceinstrroutesimulation.databinding.DialogLayoutBinding
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val soundPreference = SoundPlayingPreferences()
    private val kDefaultToken = "YOUR_TOKEN"
    private val playingListener = object : SoundPlayingListener() {
        override fun notifyStart(hasProgress: Boolean) {
        }
    }

    // Define a navigation service from which we will start the simulation.
    private val navigationService = NavigationService()

    private var contentStore: ContentStore? = null

    /**
     * Define a navigation listener that will receive notifications from the
     * navigation service.
     * We will use just the onNavigationStarted method, but for more available
     * methods you should check the documentation.
     */
    private val navigationListener: NavigationListener = NavigationListener.create(
        onNavigationStarted = { onNavigationStarted() },
        onNavigationSound = { sound ->
            SdkCall.execute {
                SoundPlayingService.play(sound, playingListener, soundPreference)
            }
        },
        canPlayNavigationSound = true,
        postOnMain = true,
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

    private fun onNavigationStarted() = SdkCall.execute {
        binding.gemSurfaceView.mapView?.let { mapView ->
            mapView.preferences?.enableCursor = false
            navigationService.getNavigationRoute(navigationListener)?.let { route ->
                mapView.presentRoute(route)
            }

            enableGPSButton()
            mapView.followPosition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.progressBar.visibility = View.VISIBLE

        SdkSettings.onMapDataReady = { mapReady ->
            if (mapReady) {
                binding.progressBar.visibility = View.VISIBLE

                val type = EContentType.HumanVoice
                val countryCode = "DEU"
                var voiceHasBeenDownloaded = false

                val onVoiceReady = { voiceFilePath: String ->
                    SdkSettings.setVoiceByPath(voiceFilePath)
                    startSimulation()
                }

                SdkCall.execute {
                    contentStore = ContentStore()

                    // check if already exists locally
                    contentStore?.getLocalContentList(type)?.let { localList ->
                        for (item in localList) {
                            if (item.countryCodes?.contains(countryCode) == true) {
                                voiceHasBeenDownloaded = true
                                onVoiceReady(item.fileName!!)
                                return@execute // already exists
                            }
                        }
                    }
                }

                if (!voiceHasBeenDownloaded) {
                    val downloadVoice = {
                        SdkCall.execute {
                            contentStore?.asyncGetStoreContentList(
                                type,
                                onCompleted = { result, _, _ ->
                                    SdkCall.execute {
                                        for (item in result) {
                                            if (item.countryCodes?.contains(countryCode) == true) {
                                                item.asyncDownload(
                                                    onCompleted = { _, _ ->
                                                        SdkCall.execute {
                                                            onVoiceReady(item.fileName!!)
                                                        }
                                                    },
                                                )
                                                break
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }

                    val token = GemSdk.getTokenFromManifest(this)

                    if (!token.isNullOrEmpty() && (token != kDefaultToken)) {
                        downloadVoice()
                    } else {
                        // If token is not present try to avoid content server
                        // requests limitation by delaying the voices catalog request
                        Handler(Looper.getMainLooper()).postDelayed(
                            {
                                downloadVoice()
                            },
                            3000,
                        )
                    }
                }
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
            binding.progressBar.visibility = View.GONE
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                    exitProcess(0)
                }
            },
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.gemSurfaceView.mapView?.apply {
            onExitFollowingPosition = {
                binding.followCursorButton.visibility = View.VISIBLE
            }

            onEnterFollowingPosition = {
                binding.followCursorButton.visibility = View.GONE
            }

            // Set on click action for the GPS button.
            binding.followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
            }
        }
    }

    private fun startSimulation() = SdkCall.execute {
        val waypoints = arrayListOf(
            Landmark("Start", 51.50338075949678, -0.11946124784612752),
            Landmark("Destination", 51.500996060519896, -0.12461566914005363),
        )

        navigationService.startSimulation(waypoints, navigationListener, routingProgressListener)
    }

    @SuppressLint("InflateParams")
    private fun showDialog(text: String) {
        val dialog = BottomSheetDialog(this)
        val binding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(binding.root)
            show()
        }
    }
}
