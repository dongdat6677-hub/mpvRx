/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import android.webkit.WebView
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.util.UUID

/** One browser tab's state. [webView] is created lazily the first time the tab is shown. */
class BrowserTabState(val id: String = UUID.randomUUID().toString()) {
  var webView: WebView? = null
  val addressBarText: MutableState<String> = mutableStateOf("")
  val pageUrl: MutableState<String?> = mutableStateOf(null)
  val pageTitle: MutableState<String?> = mutableStateOf(null)
  val isLoading: MutableState<Boolean> = mutableStateOf(false)
  val canGoBack: MutableState<Boolean> = mutableStateOf(false)
  val canGoForward: MutableState<Boolean> = mutableStateOf(false)
  val loadError: MutableState<String?> = mutableStateOf(null)
  val pendingHttpFallbackUrl: MutableState<String?> = mutableStateOf(null)
}
