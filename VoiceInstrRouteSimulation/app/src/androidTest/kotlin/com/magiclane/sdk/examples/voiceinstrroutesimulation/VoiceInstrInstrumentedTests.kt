/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voiceinstrroutesimulation

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SoundPlayingListener
import com.magiclane.sdk.core.SoundPlayingPreferences
import com.magiclane.sdk.core.SoundPlayingService
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.examples.voiceinstrroutesimulation.VoiceInstrInstrumentedTests.Companion.voiceHasBeenDownloaded
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class VoiceInstrInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()
        private var contentStore: ContentStore? = null
        val type = EContentType.HumanVoice
        const val COUNTRY_CODE = "DEU"
        var voiceHasBeenDownloaded = false

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.READ_PHONE_STATE,
    )

    @Before
    fun beforeTests() = runBlocking {
        val channel = Channel<Unit>()
        SdkCall.execute {
            contentStore = ContentStore()
            // check if already exists locally
            contentStore?.getLocalContentList(type)?.let { localList ->
                for (item in localList) {
                    if (item.countryCodes?.contains(COUNTRY_CODE) == true) {
                        voiceHasBeenDownloaded = true
                        onVoiceReady(item.fileName!!)
                        launch { channel.send(Unit) }
                        return@execute // already exists
                    }
                }
                launch { channel.send(Unit) }
            }
        }
        withTimeoutOrNull(
            30000,
        ) { channel.receive() } ?: Assert.fail("Failed to get local content list")

        if (!voiceHasBeenDownloaded) {
            SdkCall.execute {
                contentStore?.asyncGetStoreContentList(
                    type,
                    onCompleted = { result, _, _ ->
                        SdkCall.execute {
                            for (item in result) {
                                if (item.countryCodes?.contains(COUNTRY_CODE) == true) {
                                    item.asyncDownload(onCompleted = { _, _ ->
                                        SdkCall.execute {
                                            onVoiceReady(item.fileName!!)
                                            voiceHasBeenDownloaded = true
                                            launch { channel.send(Unit) }
                                        }
                                    })
                                    break
                                }
                            }
                        }
                    },
                )
            }
        }
        withTimeoutOrNull(30000) { channel.receive() } ?: Assert.fail("Voice failed to download")
    }

    @Test
    fun simulationShouldEndSuccessfullyAndPlaySound() = runBlocking {
        val channel = Channel<Unit>() // acts like a lock}
        var completed = false
        var soundReceived = false
        var error = GemError.NoError
        val navigationService = NavigationService()

        assert(voiceHasBeenDownloaded) { "Voice has not been downloaded" }

        // Skip if audio/TTS subsystem is not available
        var ttsAvailable = false
        repeat(10) {
            val languages = SdkCall.execute { SoundPlayingService.getTTSLanguages() }
            if (languages?.isNotEmpty() == true) {
                ttsAvailable = true
                return@repeat
            }
            delay(2000)
        }
        Assume.assumeTrue("TTS/audio not available on this device", ttsAvailable)

        SdkCall.execute {
            val waypoints = arrayListOf(
                /**
                 */
                Landmark("Start", 45.657188, 25.612740),
                Landmark("Destination", 45.652875, 25.605771),
            )

            error = navigationService.startSimulation(
                waypoints,
                NavigationListener.create(
                    onNavigationSound = { sound ->
                        SdkCall.execute {
                            soundReceived = sound.isValid()
                            SoundPlayingService.play(
                                sound,
                                SoundPlayingListener(),
                                SoundPlayingPreferences(),
                            )
                        }
                    },
                    onDestinationReached = {
                        completed = true
                        launch {
                            channel.send(Unit)
                        }
                    },
                    onNavigationError = { error = it },
                    canPlayNavigationSound = true,
                    postOnMain = true,
                ),
                ProgressListener.create(
                    onCompleted = { _, _ ->
                    },
                    postOnMain = true,
                ),
                speedMultiplier = 3f,
            )
        }

        withTimeout(300000) {
            channel.receive()
            assert(error == GemError.NoError) { GemError.getMessage(error) }
            assert(completed) { "Destination not reached or failed callback invoke" }
            assert(soundReceived) { "Sound is invalid" }
        }
    }

    private fun onVoiceReady(voiceFilePath: String) = SdkSettings.setVoiceByPath(voiceFilePath)
}
