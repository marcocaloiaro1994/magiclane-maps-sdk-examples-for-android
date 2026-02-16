/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.multisurfinfragrecycler

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.magiclane.sdk.examples.multisurfinfragrecycler.data.MapItem
import com.magiclane.sdk.examples.multisurfinfragrecycler.databinding.FragmentSecondBinding
import java.util.Date

class SecondFragment : Fragment() {

    private val maxSurfacesCount = 20
    private lateinit var mapAdapter: CustomAdapter

    val viewModel by activityViewModels<MainActivityViewModel>()

    private var binding: FragmentSecondBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_second, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.buttonSecond?.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        binding?.mapList?.apply {
            itemAnimator = null
            layoutManager = LinearLayoutManager(requireContext())
            mapAdapter = CustomAdapter().also { it.submitList(viewModel.list) }
            adapter = mapAdapter
            recycledViewPool.setMaxRecycledViews(1, 0)
            setItemViewCacheSize(5)
        }

        binding?.bottomLeftButton?.let {
            it.visibility = View.VISIBLE
            buttonAsDelete(requireContext(), it) {
                deleteLastSurface()
            }
        }

        binding?.bottomRightButton?.let {
            it.visibility = View.VISIBLE
            buttonAsAdd(requireContext(), it) {
                addSurface()
            }
        }
    }

    private fun addSurface() {
        viewModel.list.apply {
            if (viewModel.list.size >= maxSurfacesCount) return
            add(MapItem(lastIndex + 1, Date()))
            mapAdapter.submitList(this.toMutableList())
        }
    }

    private fun deleteLastSurface() {
        viewModel.list.apply {
            if (size == 0) return
            removeAt(lastIndex)
            mapAdapter.submitList(this.toMutableList())
        }
    }

    private fun buttonAsAdd(context: Context, button: FloatingActionButton, action: () -> Unit) {
        val tag = "add"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.primary)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }

    private fun buttonAsDelete(context: Context, button: FloatingActionButton, action: () -> Unit) {
        val tag = "delete"
        val backgroundTintList =
            AppCompatResources.getColorStateList(context, R.color.surface)
        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_delete)

        button.tag = tag
        button.setOnClickListener { action() }
        button.setImageDrawable(drawable)
        button.backgroundTintList = backgroundTintList
    }
}
