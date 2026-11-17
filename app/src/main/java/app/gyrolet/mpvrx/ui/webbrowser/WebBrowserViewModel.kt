/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.gyrolet.mpvrx.domain.adblock.AdBlockRuleSet

/**
 * Holds the in-app web browser's UI state and the ad-block rule set it
 * consults for every request. Created once per [WebBrowserScreen] visit
 * via `remember`.
 */
internal class WebBrowserViewModel(context: Context) {
  val url: MutableState<String> = mutableStateOf("")
  val pageUrl: MutableState<String?> = mutableStateOf(null)
  val isLoading: MutableState<Boolean> = mutableStateOf(false)
  val canGoBack: MutableState<Boolean> = mutableStateOf(false)
  val canGoForward: MutableState<Boolean> = mutableStateOf(false)
  val loadError: MutableState<String?> = mutableStateOf(null)

  /** Loaded once, lazily, from the bundled starter list (see easylist-lite.txt). */
  val adBlockRuleSet: AdBlockRuleSet by lazy {
    context.assets.open(AD_BLOCK_ASSET_PATH).bufferedReader().use { reader ->
      AdBlockRuleSet.parse(reader.lineSequence())
    }
  }

  private companion object {
    const val AD_BLOCK_ASSET_PATH = "adblock/easylist-lite.txt"
  }
}
