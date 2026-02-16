/*
 * SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.androidautoroutenavigation.services

import com.magiclane.sdk.core.SettingsService

object SettingsInstance {
    lateinit var service: SettingsService
        private set

    fun isInitialized(): Boolean = this::service.isInitialized

    fun init() {
        if (isInitialized()) {
            return
        }

        SettingsService.produce("Settings.ini")?.let { service = it }
    }
}
