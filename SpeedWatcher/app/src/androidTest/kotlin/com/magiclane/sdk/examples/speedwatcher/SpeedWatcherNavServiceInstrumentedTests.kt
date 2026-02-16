/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.speedwatcher

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.rule.GrantPermissionRule
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.routesandnavigation.NavigationListener
import com.magiclane.sdk.routesandnavigation.NavigationService
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class SpeedWatcherNavServiceInstrumentedTests {
    companion object {
        private val appContext: Context = ApplicationProvider.getApplicationContext()

        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    @Test
    fun navigationServiceCallbacksAreCalled() = runBlocking {
        val channel = Channel<Unit>() // acts like a lock
        var navStarted = false
        var navEnded = false
        var hasHadError = false
        var error = GemError.NoError
        var updateCount = 0
        val navigationListener: NavigationListener = NavigationListener.create(
            onNavigationStarted = {
                navStarted = true
            },
            onNavigationInstructionUpdated = { instr ->
                updateCount++
                runBlocking {
                    val positionIsValid = async {
                        SdkCall.execute {
                            instr.currentPosition?.isValid() == true
                        } ?: false
                    }.await()
                    val speed = async {
                        SdkCall.execute { instr.currentPosition?.speed } ?: 0.0
                    }.await()
                    assert(positionIsValid)
                    assert(speed > 0)
                }
            },
            onDestinationReached = {
                // DON'T FORGET to remove the position listener after the navigation is done.
                navEnded = true
                launch { channel.send(Unit) }
            },
            onNavigationError = {
                error = it
                hasHadError = true
                launch { channel.send(Unit) }
            },
        )

        val navigationService = NavigationService()

        SdkCall.execute {
            val waypoints = arrayListOf(
                Landmark("START", 45.654200, 25.605294),
                Landmark("FINISH", 45.648774, 25.619747),
            )
            navigationService.startSimulation(
                waypoints,
                navigationListener,
                ProgressListener.create(),
                speedMultiplier = 5f,
            )
        }

        withTimeout(300000) {
            channel.receive()
            assert(navStarted)
            assert(!hasHadError) {
                "Passed through onNavigationError callBack: $hasHadError ${
                    GemError.getMessage(
                        error,
                    )
                }"
            }
            assert(updateCount > 0)
            assert(navEnded)
        }
    }
}
