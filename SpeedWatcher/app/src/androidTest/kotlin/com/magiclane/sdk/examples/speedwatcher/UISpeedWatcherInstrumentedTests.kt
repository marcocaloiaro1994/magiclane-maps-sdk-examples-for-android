/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.speedwatcher

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.slowSwipeLeft
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.magiclane.sdk.core.GemSurfaceView
import com.magiclane.sdk.examples.testing.GemSdkTestRule
import com.magiclane.sdk.util.SdkCall
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class UISpeedWatcherInstrumentedTests {
    companion object {
        @get:ClassRule
        @JvmStatic
        val sdkRule = GemSdkTestRule()
    }

    @Rule
    @JvmField
    val activityScenarioRule: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    private lateinit var activityRes: Activity

    @Before
    fun setUp() {
        activityScenarioRule.scenario.moveToState(Lifecycle.State.RESUMED)
        activityScenarioRule.scenario.onActivity { activityRes = it }
        IdlingRegistry.getInstance().register(EspressoIdlingResource.espressoIdlingResource)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.espressoIdlingResource)
        activityScenarioRule.scenario.close()
    }

    @Test
    fun mapIsInNavModeWhenOpeningApp(): Unit = runBlocking {
        // Wait for the camera to start following position (animation is async)
        var isFollowing = false
        repeat(20) {
            delay(1000)
            isFollowing = async {
                SdkCall.execute {
                    activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view)
                        ?.mapView?.isFollowingPosition() == true
                } ?: false
            }.await()
            if (isFollowing) return@repeat
        }
        assert(isFollowing) { "Map is not in following position mode" }
    }

    @Test
    fun testPositionButton(): Unit = runBlocking {
        delay(5000)
        onView(allOf(withId(R.id.gem_surface_view), isDisplayed())).perform(slowSwipeLeft())
        delay(2000)
        onView(allOf(withId(R.id.follow_cursor_button), isDisplayed())).perform(click())
        // Wait for the camera to start following position again
        var isFollowing = false
        repeat(20) {
            delay(1000)
            isFollowing = async {
                SdkCall.execute {
                    activityRes.findViewById<GemSurfaceView>(R.id.gem_surface_view)
                        ?.mapView?.isFollowingPosition() == true
                } ?: false
            }.await()
            if (isFollowing) return@repeat
        }
        assert(isFollowing) { "Map is not in following position mode after clicking follow button" }
    }
}
