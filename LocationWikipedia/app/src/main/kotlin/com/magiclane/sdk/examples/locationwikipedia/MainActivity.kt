/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.locationwikipedia

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.core.EExternalImageQuality
import com.magiclane.sdk.core.ErrorCode
import com.magiclane.sdk.core.ExternalInfo
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.Image
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.Rect
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.Xy
import com.magiclane.sdk.examples.locationwikipedia.databinding.ActivityMainBinding
import com.magiclane.sdk.places.Coordinates
import com.magiclane.sdk.places.Landmark
import com.magiclane.sdk.places.SearchService
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var wikipediaImagesList = mutableListOf<WikipediaImageModel>()

    private val externalInfoService = ExternalInfo()

    private var standardHeight = 0
    private val imageQuality = EExternalImageQuality.Medium

    private var wikipediaListAdapter: WikipediaListAdapter? = null

    private val searchService = SearchService(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { results, errorCode, _ ->
            binding.progressBar.visibility = View.GONE

            if (errorCode == GemError.NoError) {
                if (results.isNotEmpty()) {
                    val landmark = results[0]
                    flyTo(landmark)
                    val name = SdkCall.execute { landmark.name }
                    binding.locationName.text = name
                    requestWiki(results[0])
                } else {
                    // The search completed without errors, but there were no results found.
                    showDialog("No results!")
                }
            }
        },
    )

    private val wikipediaProgressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
        },

        onCompleted = { _, _ ->
            displayWikipediaInfo()
            binding.progressBar.visibility = View.GONE
            EspressoIdlingResource.decrement()
        },

        postOnMain = true,
    )

    private val wikipediaImagesProgressListener = WikipediaImagesProgressListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        EspressoIdlingResource.increment()

        binding.wikipediaContainer.layoutParams.height =
            (Resources.getSystem().displayMetrics.heightPixels * 0.5).toInt()

        standardHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            30f,
            resources.displayMetrics,
        ).toInt()

        val onReady = {
            search()
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

        // Release the SDK.
        GemSdk.release()
    }

    private fun search() = SdkCall.execute {
        val name = "Statue Of Liberty"
        val coordinates = Coordinates(40.68917616239407, -74.04452185917404)
        searchService.searchByFilter(name, coordinates)
    }

    private fun requestWiki(value: Landmark) = SdkCall.execute {
        externalInfoService.requestWikiInfo(value, wikipediaProgressListener)
    }

    private fun fetchItemAtIndex(index: Int) = SdkCall.execute {
        if (index < 0 || index >= wikipediaImagesList.size) return@execute

        wikipediaImagesProgressListener.apply {
            retryCount = 3
            this.index = index

            image?.let {
                externalInfoService.requestWikiImage(
                    wikipediaImagesProgressListener,
                    it,
                    index,
                    imageQuality,
                )
            }
        }
    }

    private fun flyTo(landmark: Landmark) = SdkCall.execute {
        landmark.geographicArea?.let { area ->
            binding.gemSurface.mapView?.let { mainMapView ->
                val rect =
                    Rect(
                        Xy(0, binding.wikipediaContainer.layoutParams.height),
                        Xy(binding.gemSurface.measuredWidth, 0),
                    )
                // Center the map on a specific area using the provided animation.
                mainMapView.centerOnRectArea(area, -1, rect)

                // Highlights a specific area on the map using the provided settings.
                mainMapView.activateHighlightLandmarks(landmark)
            }
        }
    }

    private fun displayWikipediaInfo() {
        var wikipediaTitleString = ""
        var wikipediaDescriptionString = ""
        var wikipediaUrl = ""
        var wikipediaImagesCount = 0

        SdkCall.execute {
            wikipediaTitleString = getString(R.string.wikipedia)
            wikipediaDescriptionString = externalInfoService.wikiPageDescription.toString()
            wikipediaUrl = externalInfoService.wikiPageURL.toString()
            wikipediaImagesCount = externalInfoService.wikiImagesCount
        }

        binding.wikipediaTitle.apply {
            text = wikipediaTitleString
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wikipediaUrl)))
            }
        }
        binding.wikipediaDescription.text = wikipediaDescriptionString

        if (wikipediaImagesCount > 0) {
            wikipediaImagesList = MutableList(wikipediaImagesCount) { WikipediaImageModel() }

            binding.wikipediaImageListView.apply {
                itemAnimator = null
                layoutManager = LinearLayoutManager(
                    this@MainActivity,
                    LinearLayoutManager.HORIZONTAL,
                    false,
                )
                adapter = WikipediaListAdapter(
                    wikipediaImagesList,
                ).also { wikipediaListAdapter = it }
            }

            fetchItemAtIndex(0)
        }

        binding.wikipediaContainer.visibility = View.VISIBLE
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

    enum class TLoadState {
        ENotRequested,
        ELoading,
        EPendingReloading,
        EFailed,
        ELoaded,
    }

    data class WikipediaImageModel(
        var status: TLoadState = TLoadState.ENotRequested,
        var bitmap: Bitmap? = null,
    )

    inner class WikipediaListAdapter(private val dataSet: MutableList<WikipediaImageModel>) :
        RecyclerView.Adapter<WikipediaListAdapter.ImageViewHolder>() {
        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(
                viewGroup.context,
            ).inflate(R.layout.wiki_image_list_item, viewGroup, false)
            return ImageViewHolder(view)
        }

        override fun getItemCount(): Int = dataSet.size

        inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val imageView: ImageView = view.findViewById(R.id.wiki_image)
            private val progressBar: ProgressBar = view.findViewById(R.id.wiki_image_progress)

            fun bind(position: Int) {
                dataSet[position].apply {
                    if (status == TLoadState.ELoaded) {
                        imageView.apply {
                            layoutParams.apply {
                                width = ConstraintLayout.LayoutParams.WRAP_CONTENT
                                height = ConstraintLayout.LayoutParams.WRAP_CONTENT
                            }

                            maxHeight = standardHeight
                            setImageBitmap(bitmap)
                        }

                        progressBar.visibility = View.GONE
                    } else {
                        imageView.apply {
                            layoutParams.apply {
                                height = standardHeight
                                width = standardHeight
                            }

                            setImageBitmap(null)
                        }

                        progressBar.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    inner class WikipediaImagesProgressListener : ProgressListener() {
        var index = 0
        var retryCount = 0
        var image: Image? = SdkCall.execute { Image() }

        override fun notifyComplete(errorCode: ErrorCode, hint: String) {
            if (errorCode != GemError.NoError) {
                if (retryCount > 0) {
                    // retry
                    retryCount--
                    wikipediaImagesList[index].status = TLoadState.EPendingReloading
                    image?.let {
                        externalInfoService.requestWikiImage(
                            this,
                            it,
                            index,
                            imageQuality,
                        )
                    }
                } else {
                    // fail
                    wikipediaImagesList[index].status = TLoadState.EFailed

                    // start fetching next item
                    if (index + 1 < wikipediaImagesList.size) {
                        fetchItemAtIndex(index + 1)
                    }
                }

                return
            }

            // success
            wikipediaImagesList[index].status = TLoadState.ELoaded
            SdkCall.execute {
                val imageWidth =
                    image?.size?.let { (it.width.toFloat() / it.height * standardHeight).toInt() }
                        ?: 0
                wikipediaImagesList[index].bitmap = image?.asBitmap(imageWidth, standardHeight)
            }

            Util.postOnMain { wikipediaListAdapter?.notifyItemRangeChanged(0, index + 1) }

            // start fetching next item
            if (index + 1 < wikipediaImagesList.size) {
                fetchItemAtIndex(index + 1)
            }
        }

        override fun notifyStart(hasProgress: Boolean) {
            wikipediaImagesList[index].status = TLoadState.ELoading
        }
    }
}

object EspressoIdlingResource {
    val espressoIdlingResource = CountingIdlingResource("LocationWikiResource")
    fun increment() = espressoIdlingResource.increment()
    fun decrement() = if (!espressoIdlingResource.isIdleNow) espressoIdlingResource.decrement() else Unit
}
