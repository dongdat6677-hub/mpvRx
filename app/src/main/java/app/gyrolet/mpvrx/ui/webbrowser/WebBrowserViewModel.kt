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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.gyrolet.mpvrx.domain.adblock.AdBlockRuleSet
import app.gyrolet.mpvrx.domain.webbrowser.repository.BrowserBookmarkRepository
import app.gyrolet.mpvrx.domain.webbrowser.repository.BrowserHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

data class PendingNavigation(val url: String, val pageTitle: String?)

internal class WebBrowserViewModel(private val context: Context) {
  val tabs: SnapshotStateList<BrowserTabState> = mutableStateListOf(BrowserTabState())
  val activeTabId: MutableState<String> = mutableStateOf(tabs.first().id)

  val pendingNavigationConfirmation: MutableState<PendingNavigation?> = mutableStateOf(null)

  private val historyRepository by inject<BrowserHistoryRepository>(BrowserHistoryRepository::class.java)
  private val bookmarkRepository by inject<BrowserBookmarkRepository>(BrowserBookmarkRepository::class.java)
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val alreadyPromptedUrls = mutableSetOf<String>()

  fun addTab() {
    val tab = BrowserTabState()
    tabs.add(tab)
    activeTabId.value = tab.id
  }

  /** Never leaves [tabs] empty: closing the last tab opens a fresh default one. */
  fun closeTab(id: String) {
    val index = tabs.indexOfFirst { it.id == id }
    if (index == -1) return
    tabs[index].webView?.destroy()
    tabs.removeAt(index)
    if (tabs.isEmpty()) tabs.add(BrowserTabState())
    if (activeTabId.value == id) {
      val newIndex = (index - 1).coerceIn(0, tabs.lastIndex)
      activeTabId.value = tabs[newIndex].id
    }
  }

  fun recordVisit(url: String, title: String?) {
    scope.launch { historyRepository.recordVisit(url, title) }
  }

  fun isBookmarked(url: String): Flow<Boolean> = bookmarkRepository.isBookmarked(url)

  fun toggleBookmark(url: String, title: String?) {
    scope.launch { bookmarkRepository.toggle(url, title) }
  }

  fun clearHistoryRepository() {
    scope.launch { historyRepository.clearAll() }
  }

  /** Returns false (no dialog shown) when this exact destination was already asked about. */
  fun requestNavigationConfirmation(url: String, pageTitle: String?): Boolean {
    if (url in alreadyPromptedUrls) return false
    alreadyPromptedUrls += url
    pendingNavigationConfirmation.value = PendingNavigation(url, pageTitle)
    return true
  }

  fun confirmNavigation() {
    pendingNavigationConfirmation.value = null
  }

  fun buildAdBlockRuleSet(blockAds: Boolean, blockTrackers: Boolean): AdBlockRuleSet {
    val lines = mutableListOf<String>()
    if (blockAds) {
      context.assets.open(AD_BLOCK_ASSET_PATH).bufferedReader().use { lines += it.readLines() }
    }
    if (blockTrackers) {
      context.assets.open(TRACKER_BLOCK_ASSET_PATH).bufferedReader().use { lines += it.readLines() }
    }
    return AdBlockRuleSet.parse(lines.asSequence())
  }

  private companion object {
    const val AD_BLOCK_ASSET_PATH = "adblock/easylist-lite.txt"
    const val TRACKER_BLOCK_ASSET_PATH = "adblock/easyprivacy-lite.txt"
  }
}
