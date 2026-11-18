/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.WebBrowserPreferences
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject

@Serializable
object BrowserSettingsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val preferences by inject<WebBrowserPreferences>(WebBrowserPreferences::class.java)

    val autoClearOnExit by preferences.autoClearOnExit.collectAsState()
    val blockAds by preferences.blockAds.collectAsState()
    val blockTrackers by preferences.blockTrackers.collectAsState()
    val blockPopups by preferences.blockPopups.collectAsState()
    val blockRedirects by preferences.blockRedirects.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
      TopAppBar(
        title = { Text(stringResource(R.string.ui_web_browser_settings)) },
        navigationIcon = {
          IconButton(onClick = { backStack.popSafely() }) {
            Icon(Icons.RoundedFilled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
      )

      ListItem(
        headlineContent = { Text(stringResource(R.string.ui_web_browser_auto_clear)) },
        trailingContent = { Switch(checked = autoClearOnExit, onCheckedChange = preferences.autoClearOnExit::set) },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.ui_web_browser_block_ads)) },
        trailingContent = { Switch(checked = blockAds, onCheckedChange = preferences.blockAds::set) },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.ui_web_browser_block_trackers)) },
        trailingContent = { Switch(checked = blockTrackers, onCheckedChange = preferences.blockTrackers::set) },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.ui_web_browser_block_popups)) },
        trailingContent = { Switch(checked = blockPopups, onCheckedChange = preferences.blockPopups::set) },
      )
      ListItem(
        headlineContent = { Text(stringResource(R.string.ui_web_browser_block_redirects)) },
        trailingContent = { Switch(checked = blockRedirects, onCheckedChange = preferences.blockRedirects::set) },
      )
    }
  }
}
