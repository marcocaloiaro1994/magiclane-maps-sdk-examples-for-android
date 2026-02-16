/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package com.magiclane.sdk.examples.androidautoroutenavigation.services

import com.magiclane.sdk.places.SearchService

object SearchInstance {
    val service: SearchService = SearchService()

    fun init() {
        service.preferences.searchMapPOIsEnabled = true
        service.preferences.searchMapPOIsEnabled = true
    }
}
