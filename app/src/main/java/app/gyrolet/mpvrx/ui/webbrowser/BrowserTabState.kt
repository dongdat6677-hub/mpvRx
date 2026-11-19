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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.gyrolet.mpvrx.domain.webbrowser.SniffedStream
import java.util.UUID

/** One browser tab's state. [webView] is created lazily the first time the tab is shown. */
class BrowserTabState(val id: String = UUID.randomUUID().toString()) {
  var webView: WebView? = null

  /** Captured once from the WebView's own settings (main thread) so background callbacks
   *  like `shouldInterceptRequest` never have to call a WebView getter off-thread. */
  var userAgent: String? = null
  val addressBarText: MutableState<String> = mutableStateOf("")
  val pageUrl: MutableState<String?> = mutableStateOf(null)
  val pageTitle: MutableState<String?> = mutableStateOf(null)
  val isLoading: MutableState<Boolean> = mutableStateOf(false)
  val canGoBack: MutableState<Boolean> = mutableStateOf(false)
  val canGoForward: MutableState<Boolean> = mutableStateOf(false)
  val loadError: MutableState<String?> = mutableStateOf(null)
  val pendingHttpFallbackUrl: MutableState<String?> = mutableStateOf(null)

  val sniffedStreams: SnapshotStateList<SniffedStream> = mutableStateListOf()
  private val seenStreamUrls = mutableSetOf<String>()

  fun addSniffedStream(stream: SniffedStream) {
    if (seenStreamUrls.add(stream.url)) {
      sniffedStreams.add(stream)
    }
  }

  /** Called on every new page load — a stream sniffed on the previous page is no longer relevant. */
  fun clearSniffedStreams() {
    sniffedStreams.clear()
    seenStreamUrls.clear()
  }
}
