/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.weather

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.magiclane.sdk.examples.weather.databinding.DialogLayoutBinding

object Utils {

    fun showDialog(text: String, activityRef: AppCompatActivity) {
        activityRef.run {
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
}
