/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.socialeventvoting

import android.R.attr.text
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.utilities.Score.score
import com.magiclane.sdk.core.GemError
import com.magiclane.sdk.core.GemSdk
import com.magiclane.sdk.core.ImageDatabase
import com.magiclane.sdk.core.ProgressListener
import com.magiclane.sdk.core.SdkSettings
import com.magiclane.sdk.core.SocialOverlay
import com.magiclane.sdk.d3scene.ECommonOverlayId
import com.magiclane.sdk.d3scene.OverlayItem
import com.magiclane.sdk.examples.socialeventvoting.databinding.ActivityMainBinding
import com.magiclane.sdk.util.GemUtilImages
import com.magiclane.sdk.util.SdkCall
import com.magiclane.sdk.util.SdkImages
import com.magiclane.sdk.util.Util
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SdkSettings.onMapDataReady = onMapDataReady@{ isReady ->
            if (!isReady) return@onMapDataReady

            // Defines an action that should be done when the world map is ready (Updated/ loaded).
            binding.gemSurfaceView.mapView?.onTouch = { xy ->
                SdkCall.execute {
                    binding.gemSurfaceView.mapView?.cursorScreenPosition = xy

                    val overlays = binding.gemSurfaceView.mapView?.cursorSelectionOverlayItems
                    if (!overlays.isNullOrEmpty()) {
                        val overlay = overlays[0]
                        if (overlay.overlayInfo?.uid == ECommonOverlayId.SocialReports.value) {
                            Util.postOnMain { showVotingPanel(overlay) }
                            return@execute
                        }
                    }

                    Util.postOnMain { hideVotingPanel() }
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
            showDialog("You must be connected to the internet!")
        }

        onBackPressedDispatcher.addCallback {
            finish()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Deinitialize the SDK.
        GemSdk.release()
    }

    private fun showVotingPanel(overlay: OverlayItem) {
        binding.apply {
            if (!eventVotingContainer.isVisible) {
                eventVotingContainer.visibility = View.VISIBLE
                pleaseSelectText.visibility = View.GONE
            }
        }

        var bitmap: Bitmap? = null
        var textStr = ""
        var timeStr = ""
        var scoreStr = ""
        var thumbUpBitmap: Bitmap? = null
        val eventImageSize = resources.getDimension(R.dimen.event_image_size).toInt()
        val imageSize = resources.getDimension(R.dimen.image_size).toInt()

        SdkCall.execute {
            bitmap = overlay.image?.asBitmap(eventImageSize, eventImageSize)
            textStr = overlay.name.toString()

            scoreStr = overlay.getPreviewData()?.find { it.key == "score" }?.valueString.toString()

            val stamp = overlay.getPreviewData()?.find { it.key == "create_stamp_utc" }?.valueLong ?: 0

            val eventTime = Calendar.getInstance(
                Locale.getDefault(),
            ).also { it.timeInMillis = stamp * 1000 }
            val now = Calendar.getInstance(Locale.getDefault()).also {
                it.timeInMillis = System.currentTimeMillis()
            }

            val dateFormat = if (eventTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                eventTime.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                eventTime.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
            ) {
                "HH:mm"
            } else {
                "dd/MM/yyyy"
            }

            timeStr = SimpleDateFormat(
                dateFormat,
                Locale.getDefault(),
            ).format(Date(eventTime.timeInMillis))

            if (overlay.getPreviewData()?.find { it.key == "allow_thumb" }?.valueBoolean == true) {
                thumbUpBitmap = GemUtilImages.asBitmap(
                    ImageDatabase().getImageById(SdkImages.SocialReports.Social_Thumbs_Up.value),
                    imageSize,
                    imageSize,
                )
            }
        }

        binding.apply {
            icon.setImageBitmap(bitmap)
            text.text = textStr
            time.text = timeStr
            score.text = scoreStr
        }

        if (thumbUpBitmap != null) {
            binding.thumbUpButton.apply {
                visibility = View.VISIBLE
                setImageBitmap(thumbUpBitmap)
                setOnClickListener {
                    val errorCode = SdkCall.execute {
                        SocialOverlay.confirmReport(
                            overlay,
                            ProgressListener(),
                        )
                    } ?: -1
                    if (errorCode < 0) {
                        showDialog(
                            "Confirm report failed with error: ${GemError.getMessage(errorCode)}",
                        )
                    }

                    binding.eventVotingContainer.visibility = View.GONE
                }
            }
        } else {
            binding.thumbUpButton.visibility = View.GONE
        }
    }

    private fun hideVotingPanel() {
        binding.eventVotingContainer.visibility = View.GONE
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
