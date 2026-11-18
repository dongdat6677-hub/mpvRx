/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.preferences

import app.gyrolet.mpvrx.preferences.preference.PreferenceStore

class WebBrowserPreferences(
  preferenceStore: PreferenceStore,
) {
  val autoClearOnExit = preferenceStore.getBoolean("browser_auto_clear_on_exit", false)
  val blockAds = preferenceStore.getBoolean("browser_block_ads", true)
  val blockTrackers = preferenceStore.getBoolean("browser_block_trackers", true)
  val blockPopups = preferenceStore.getBoolean("browser_block_popups", true)
  val blockRedirects = preferenceStore.getBoolean("browser_block_redirects", true)
}
