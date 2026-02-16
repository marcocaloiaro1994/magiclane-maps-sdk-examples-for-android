/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.whatsnearby

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EUnitSystem
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.whatsnearby.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.whatsnearby.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.whatsnearby.databinding.ListItemBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.sensordatasource.PositionService
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.GemUtil.getDistText
import com.magiclane.sdk.util.PermissionsHelper
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import com.magiclane.sdk.util.Util.postOnMain
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageSize: Int = 0

    private var reference: Coordinates? = null
    private val searchService = SearchService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = onCompleted@{ results, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            when (errorCode) {
                GemError.NoError ->
                    {
                        // No error encountered, we can handle the results.
                        if (results.isNotEmpty()) {
                            reference?.let {
                                binding.listView.adapter = CustomAdapter(
                                    it,
                                    results,
                                    imageSize,
                                )
                            }
                        } else {
                            // The search completed without errors, but there were no results found.
                            showDialog("No results!")
                        }
                    }

                GemError.Cancel ->
                    {
                        // The search action was cancelled.
                    }

                else ->
                    {
                        // There was a problem at computing the search operation.
                        showDialog("Search service error: ${GemError.getMessage(errorCode)}")
                    }
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageSize = resources.getDimension(R.dimen.landmark_image_size).toInt()
        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            addItemDecoration(
                DividerItemDecoration(
                    applicationContext,
                    (layoutManager as LinearLayoutManager).orientation,
                ),
            )

            setBackgroundResource(R.color.background)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)
        }

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done after the network is connected.
            search()
        }

        SdkSettings.onApiTokenRejected = {
            /**
             * The TOKEN you provided in the AndroidManifest.xml file was rejected.
             * Make sure you provide the correct value, or if you don't have a TOKEN,
             * check the magiclane.com website, sign up/sign in and generate one.
             */
            showDialog("TOKEN REJECTED")
        }

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (GemSdk.initSdkWithDefaults(this) != GemError.NoError) {
            // The SDK initialization was not completed.
            finish()
        }

        /**
         * The SDK initialization completed with success, but for the search action to be executed
         * properly the app needs permission to get your location.
         * Not requesting this permission or not granting it will make the search fail.
         */
        requestPermissions(this)

        if (!Util.isInternetConnected(this)) {
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

        // Release the SDK.
        GemSdk.release()
    }

    private fun search() = SdkCall.execute {
        // If one of the location permissions is granted, we can do the search around action.
        val hasPermissions =
            PermissionsHelper.hasPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (!hasPermissions) return@execute

        // Cancel any search that is in progress now.
        searchService.cancelSearch()

        PositionService.getCurrentPosition()?.let {
            reference = it

            // Search around position using the provided search preferences and/ or filter.
            searchService.searchAroundPosition(it)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsHelper.onRequestPermissionsResult(this, requestCode, grantResults)

        postOnMain { search() }
    }

    private fun requestPermissions(activity: Activity): Boolean {
        val permissions = arrayListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
        )

        return PermissionsHelper.requestPermissions(
            REQUEST_PERMISSIONS,
            activity,
            permissions.toTypedArray(),
        )
    }

    private fun showDialog(text: String) {
        val dialog = BottomSheetDialog(this)
        val dialogBinding = DialogLayoutBinding.inflate(layoutInflater).apply {
            title.text = getString(R.string.error)
            message.text = text
            button.setOnClickListener {
                dialog.dismiss()
            }
        }
        dialog.apply {
            setCancelable(false)
            setContentView(dialogBinding.root)
            show()
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 110
    }
}

/**
 * This custom adapter is made to facilitate the displaying of the data from the model
 * and to decide how it is displayed.
 */
class CustomAdapter(
    private val reference: Coordinates,
    private val dataSet: ArrayList<Landmark>,
    private val imageSize: Int,
) : RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

    class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) = SdkCall.execute {
        val meters = dataSet[position].coordinates?.getDistance(reference)?.toInt() ?: 0
        val dist = getDistText(meters, EUnitSystem.Metric, true)

        viewHolder.binding.run {
            image.setImageBitmap(dataSet[position].imageAsBitmap(imageSize))
            listItemText.text = dataSet[position].name
            listItemDescription.text = GemUtil.getLandmarkDescription(dataSet[position], true)
            statusText.text = dist.first
            statusDescription.text = dist.second
        }
    } ?: Unit

    override fun getItemCount() = dataSet.size
}
