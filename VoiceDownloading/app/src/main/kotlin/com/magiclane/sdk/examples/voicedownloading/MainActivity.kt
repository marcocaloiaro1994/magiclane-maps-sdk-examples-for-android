/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.voicedownloading

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.content.ContentStore
import com.magiclane.sdk.content.ContentStoreItem
import com.magiclane.sdk.content.EContentStoreItemStatus
import com.magiclane.sdk.content.EContentType
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.MapDetails
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.examples.voicedownloading.databinding.ActivityMainBinding
import com.magiclane.sdk.examples.voicedownloading.databinding.DialogLayoutBinding
import com.magiclane.sdk.examples.voicedownloading.databinding.ListItemBinding
import com.magiclane.sdk.util.GemUtil
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.Util
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val contentStore = ContentStore()

    private var voicesCatalogRequested = false

    private val kDefaultToken =
        "YOUR_TOKEN"

    private val flagBitmapsMap = HashMap<String, Bitmap?>()

    private val progressListener = ProgressListener.create(
        onStarted = {
            binding.progressBar.visibility = View.VISIBLE
            showStatusMessage("Started content store service.")
        },

        onCompleted = { errorCode, _ ->
            binding.progressBar.visibility = View.GONE
            showStatusMessage("Content store service completed with error code: $errorCode")

            when (errorCode) {
                GemError.NoError -> {
                    SdkCall.execute {
                        // No error encountered, we can handle the results.
                        // Get the list of voices that was retrieved in the content store.

                        val models =
                            contentStore.getStoreContentList(EContentType.HumanVoice)?.first
                        if (!models.isNullOrEmpty()) {
                            // The voice items list is not empty or null.
                            val voiceItem = models[0]
                            val itemName = voiceItem.name

                            // Start downloading the first voice item.
                            SdkCall.execute {
                                voiceItem.asyncDownload(
                                    GemSdk.EDataSavePolicy.UseDefault,
                                    true,
                                    onStarted = {
                                        showStatusMessage("Started downloading $itemName.")
                                    },
                                    onCompleted = { _, _ ->
                                        binding.listView.adapter?.notifyItemChanged(0)
                                        showStatusMessage("$itemName was downloaded.")
                                    },
                                    onProgress = {
                                        binding.listView.adapter?.notifyItemChanged(0)
                                    },
                                )
                            }
                        }

                        displayList(models)
                    }
                }

                GemError.Cancel -> {
                    // The action was cancelled.
                }

                else -> {
                    // There was a problem at retrieving the content store items.
                    showDialog("Content store service error: ${GemError.getMessage(errorCode)}")
                }
            }
        },
        postOnMain = true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.listView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)

            val separator = DividerItemDecoration(
                applicationContext,
                (layoutManager as LinearLayoutManager).orientation,
            )
            addItemDecoration(separator)

            val lateralPadding = resources.getDimension(R.dimen.big_padding).toInt()
            setPadding(lateralPadding, 0, lateralPadding, 0)

            itemAnimator = null
        }

        SdkSettings.onConnectionStatusUpdated = { connected ->
            if (connected && !voicesCatalogRequested) {
                voicesCatalogRequested = true

                val loadVoicesCatalog = {
                    SdkCall.execute {
                        // Defines an action that should be done after the network is connected.
                        // Call to the content store to asynchronously retrieve the list of voices.
                        contentStore.asyncGetStoreContentList(
                            EContentType.HumanVoice,
                            progressListener,
                        )
                    }
                }

                val token = GemSdk.getTokenFromManifest(this)

                if (!token.isNullOrEmpty() && (token != kDefaultToken)) {
                    loadVoicesCatalog()
                } else {
                    binding.progressBar.visibility = View.VISIBLE

                    // If token is not present try to avoid content server
                    // requests limitation by delaying the voices catalog request
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            loadVoicesCatalog()
                        },
                        3000,
                    )
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

        // This step of initialization is mandatory if you want to use the SDK without a map.
        if (GemSdk.initSdkWithDefaults(this) != GemError.NoError) {
            // The SDK initialization was not completed.
            finish()
        }

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

        // Deinitialize the SDK.
        GemSdk.release()
    }

    private fun displayList(models: ArrayList<ContentStoreItem>?) {
        if (models != null) {
            val adapter = CustomAdapter(models)
            binding.listView.adapter = adapter
        }
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

    private fun showStatusMessage(text: String) {
        binding.statusText.text = text
    }

    /**
     * This custom adapter is made to facilitate the displaying of the data from the model
     * and to decide how it is displayed.
     */
    inner class CustomAdapter(private val dataSet: ArrayList<ContentStoreItem>) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = ListItemBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.binding.apply {
                text.text = SdkCall.execute {
                    dataSet[position].name + " (" + GemUtil.formatSizeAsText(
                        dataSet[position].totalSize,
                    ) + ")"
                }
                description.text = SdkCall.execute {
                    "${getCountryName(dataSet[position])} - ${
                        getParameter(
                            dataSet[position],
                            "native_language",
                        )
                    } - ${getParameter(dataSet[position], "gender")}"
                }
                icon.setImageBitmap(SdkCall.execute { getFlagBitmap(dataSet[position]) })

                statusIcon.visibility = View.GONE
                itemProgressBar.visibility = View.INVISIBLE
                when (SdkCall.execute { dataSet[position].status }) {
                    EContentStoreItemStatus.Completed -> {
                        statusIcon.visibility = View.VISIBLE
                        itemProgressBar.visibility = View.INVISIBLE
                    }

                    EContentStoreItemStatus.DownloadRunning -> {
                        itemProgressBar.visibility = View.VISIBLE
                        itemProgressBar.progress = SdkCall.execute { dataSet[position].downloadProgress } ?: 0
                    }

                    else -> return
                }
            }
        }

        override fun getItemCount() = dataSet.size

        private fun getFlagBitmap(item: ContentStoreItem): Bitmap? {
            item.countryCodes?.let { codes ->
                val isoCode = codes[0]
                if (!flagBitmapsMap.containsKey(isoCode)) {
                    val size = resources.getDimension(R.dimen.icon_size).toInt()
                    flagBitmapsMap[isoCode] =
                        MapDetails().getCountryFlag(codes[0])?.asBitmap(size, size)
                }

                return flagBitmapsMap[isoCode]
            }
            return null
        }

        private fun getCountryName(item: ContentStoreItem): String {
            item.countryCodes?.let { codes ->
                return MapDetails().getCountryName(codes[0]) ?: ""
            }
            return ""
        }

        private fun getParameter(item: ContentStoreItem, parameter: String): String {
            item.contentParameters?.let { parameters ->
                for (param in parameters) {
                    if (param.name?.lowercase()?.compareTo(parameter) == 0) {
                        return param.valueString
                    }
                }
            }
            return ""
        }
    }
}
