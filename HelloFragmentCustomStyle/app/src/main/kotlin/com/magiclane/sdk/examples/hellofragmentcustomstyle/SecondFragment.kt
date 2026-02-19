/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.hellofragmentcustomstyle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.magiclane.sdk.core.DataBuffer
import com.magiclane.sdk.d3scene.MapView
import com.magiclane.sdk.examples.hellofragmentcustomstyle.databinding.FragmentSecondBinding
import com.magiclane.sdk.util.SdkCall

class SecondFragment : Fragment() {

    private var binding: FragmentSecondBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DataBindingUtil.inflate<FragmentSecondBinding>(inflater, R.layout.fragment_second, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.buttonSecond?.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
        binding?.gemSurfaceView?.onDefaultMapViewCreated = {
            applyCustomAssetStyle(it)
        }
    }

    private fun applyCustomAssetStyle(mapView: MapView?) {
        val filename = "(Desktop) Monochrome Deep Blue (5a1da93a-dbf2-4a36-9b5c-1370386c1496).style"

        // Opens GPX input stream.
        val inputStream = resources.assets.open(filename)

        // Take bytes.
        val data = inputStream.readBytes()
        if (data.isEmpty()) return@execute

        // Apply style.
        mapView?.preferences?.setMapStyleByDataBuffer(DataBuffer(data))
    }
}
