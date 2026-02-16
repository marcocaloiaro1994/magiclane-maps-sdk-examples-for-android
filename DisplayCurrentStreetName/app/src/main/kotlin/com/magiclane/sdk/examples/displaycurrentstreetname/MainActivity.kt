/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.displaycurrentstreetname

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.displaycurrentstreetname.databinding.ActivityMainBinding
import com.magiclane.sdk.sensordatasource.DataSource
import com.magiclane.sdk.sensordatasource.DataSourceFactory
import com.magiclane.sdk.sensordatasource.DataSourceListener
import com.magiclane.sdk.sensordatasource.ImprovedPositionData
import com.magiclane.sdk.sensordatasource.SenseData
import com.magiclane.sdk.sensordatasource.enums.EDataType
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var dataSource: DataSource? = null
    private val dataSourceListener = object : DataSourceListener() {
        override fun onNewData(data: SenseData) {
            var txt = ""
            SdkCall.execute execute@{
                val improvedPositionData = ImprovedPositionData(data)
                val roadAddress = improvedPositionData.roadAddress ?: return@execute

                roadAddress.format()?.let let@{
                    if (it.isEmpty()) {
                        txt = "Current street name not available."
                        return@let
                    }

                    txt = "Current street name: $it"

                    val speedLimit = (improvedPositionData.roadSpeedLimit * 3.6).toInt()
                    if (speedLimit != 0) {
                        txt += "\nRoad speed limit: $speedLimit km/h"
                    }
                }
            }
            Util.postOnMain {
                binding.currentStreetName.apply {
                    isVisible = true
                    text = txt
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.gemSurface.onDefaultMapViewCreated = {
            enableGPSButton()
        }
        val onReady = {
            val hasPermissions = PermissionsHelper.hasPermissions(this, permissions)

            if (hasPermissions) {
                SdkCall.execute {
                    startImprovedPositionListener()
                }
            } else {
                requestPermissions(this)
            }
        }
        if (SdkSettings.isMapDataReady) {
            onReady()
        } else {
            SdkSettings.onMapDataReady = { onReady() }
        }

        if (!Util.isInternetConnected(this)) {
            showDialog()
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
    private fun showDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_layout, null).apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.error)
            findViewById<TextView>(
                R.id.message,
            ).text = ContextCompat.getString(context, R.string.not_connected)
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

    private fun startImprovedPositionListener() {
        if (binding.gemSurface.mapView?.isFollowingPosition() != true) {
            binding.gemSurface.mapView?.followPosition()
        }

        if (dataSource == null) {
            dataSource = DataSourceFactory.produceLive()
        }

        dataSource?.addListener(dataSourceListener, EDataType.ImprovedPosition)
    }

    private fun enableGPSButton() {
        // Set actions for entering/ exiting following position mode.
        binding.gemSurface.mapView?.apply {
            onExitFollowingPosition = {
                binding.followCursorButton.isVisible = true
            }

            onEnterFollowingPosition = {
                binding.followCursorButton.isVisible = false
            }

            // Set on click action for the GPS button.
            binding.followCursorButton.setOnClickListener {
                SdkCall.execute { followPosition() }
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

            startImprovedPositionListener()
        }
    }

    private val permissions = arrayOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private fun requestPermissions(activity: Activity): Boolean {
        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions,
        )
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}

//region TESTING
@VisibleForTesting
object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("DisplayCurrentStreetNameIdlingResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
//endregion
