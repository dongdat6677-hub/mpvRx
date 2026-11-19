/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.adblock.AdBlockRuleSet
import app.gyrolet.mpvrx.domain.webbrowser.StreamSniffer
import app.gyrolet.mpvrx.preferences.WebBrowserPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.navigateTo
import app.gyrolet.mpvrx.ui.utils.popSafely
import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject

/**
 * The in-app web browser. See docs/superpowers/specs/2026-11-17-integrated-browser-design.md
 * and docs/superpowers/specs/2026-11-18-integrated-browser-phase1b-design.md.
 *
 * Every request the active tab's WebView makes passes through the shared ad-block rule set
 * in `shouldInterceptRequest` inside [createWebView]; a blocked request never reaches the
 * network. A request that is *not* blocked is also classified by [StreamSniffer] and, if it
 * looks like a video stream, added to that tab's [BrowserTabState.sniffedStreams].
 *
 * The address bar + nav buttons live in a compact bottom toolbar that auto-hides on scroll-down
 * and reappears on scroll-up, so the WebView gets nearly the full screen while browsing.
 */
@Serializable
object WebBrowserScreen : Screen {
  val pendingLoadUrl: MutableState<String?> = mutableStateOf(null)
  private var viewModelInstance: WebBrowserViewModel? = null

  @SuppressLint("SetJavaScriptEnabled")
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val viewModel =
      remember {
        viewModelInstance ?: WebBrowserViewModel(context.applicationContext).also { viewModelInstance = it }
      }
    val tabs = viewModel.tabs
    val activeTabId by viewModel.activeTabId
    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    var showStreamsSheet by remember { mutableStateOf(false) }
    val toolbarVisible = remember { mutableStateOf(true) }

    LaunchedEffect(activeTab.id) { toolbarVisible.value = true }

    val addressBarText by activeTab.addressBarText
    val isLoading by activeTab.isLoading
    val canGoBack by activeTab.canGoBack
    val canGoForward by activeTab.canGoForward
    val loadError by activeTab.loadError
    val currentPageUrl by activeTab.pageUrl
    val isBookmarked by remember(currentPageUrl) { viewModel.isBookmarked(currentPageUrl ?: "") }
      .collectAsState(initial = false)
    val pendingNavigation by viewModel.pendingNavigationConfirmation
    val sniffedStreams = activeTab.sniffedStreams

    val preferences by inject<WebBrowserPreferences>(WebBrowserPreferences::class.java)
    val blockAds by preferences.blockAds.collectAsState()
    val blockTrackers by preferences.blockTrackers.collectAsState()
    val blockPopups by preferences.blockPopups.collectAsState()
    val blockRedirects by preferences.blockRedirects.collectAsState()

    val adBlockRuleSetState =
      remember { mutableStateOf(viewModel.buildAdBlockRuleSet(blockAds, blockTrackers)) }
    val blockPopupsState = remember { mutableStateOf(blockPopups) }
    val blockRedirectsState = remember { mutableStateOf(blockRedirects) }
    LaunchedEffect(blockAds, blockTrackers) {
      adBlockRuleSetState.value = viewModel.buildAdBlockRuleSet(blockAds, blockTrackers)
    }
    LaunchedEffect(blockPopups) { blockPopupsState.value = blockPopups }
    LaunchedEffect(blockRedirects) { blockRedirectsState.value = blockRedirects }

    LaunchedEffect(pendingLoadUrl.value) {
      pendingLoadUrl.value?.let { url ->
        activeTab.webView?.loadUrl(url)
        pendingLoadUrl.value = null
      }
    }

    DisposableEffect(Unit) {
      onDispose {
        if (preferences.autoClearOnExit.get()) {
          tabs.forEach {
            it.webView?.clearHistory()
            it.webView?.clearCache(true)
          }
          viewModel.clearHistoryRepository()
        }
      }
    }

    fun loadUrl(input: String) {
      val trimmed = input.trim()
      if (trimmed.isEmpty()) return
      val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
      if (normalized.startsWith("http://")) {
        activeTab.pendingHttpFallbackUrl.value = normalized
        activeTab.webView?.loadUrl(normalized.replaceFirst("http://", "https://"))
      } else {
        activeTab.pendingHttpFallbackUrl.value = null
        activeTab.webView?.loadUrl(normalized)
      }
    }

    BackHandler {
      val webView = activeTab.webView
      if (webView != null && canGoBack) webView.goBack() else backStack.popSafely()
    }

    Column(modifier = Modifier.fillMaxSize()) {
      TopAppBar(
        title = { Text(stringResource(R.string.ui_web_browser)) },
        navigationIcon = {
          IconButton(onClick = { backStack.popSafely() }) {
            Icon(
              Icons.RoundedFilled.ArrowBack,
              contentDescription = stringResource(R.string.back),
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        },
        actions = {
          IconButton(onClick = { showStreamsSheet = true }) {
            BadgedBox(
              badge = {
                if (sniffedStreams.isNotEmpty()) Badge { Text(sniffedStreams.size.toString()) }
              },
            ) {
              Icon(Icons.RoundedFilled.VideoLibrary, contentDescription = stringResource(R.string.ui_web_browser_streams))
            }
          }
          IconButton(onClick = { backStack.navigateTo(BrowserHistoryScreen) }) {
            Icon(Icons.RoundedFilled.History, contentDescription = stringResource(R.string.ui_web_browser_history))
          }
          IconButton(onClick = { backStack.navigateTo(BrowserBookmarksScreen) }) {
            Icon(Icons.RoundedFilled.Bookmarks, contentDescription = stringResource(R.string.ui_web_browser_bookmarks))
          }
          IconButton(onClick = { backStack.navigateTo(BrowserSettingsScreen) }) {
            Icon(Icons.RoundedFilled.Settings, contentDescription = stringResource(R.string.ui_web_browser_settings))
          }
          IconButton(onClick = { activeTab.webView?.reload() }) {
            Icon(Icons.RoundedFilled.Refresh, contentDescription = null)
          }
        },
      )

      BrowserTabBar(
        tabs = tabs,
        activeTabId = activeTabId,
        onSelectTab = { viewModel.activeTabId.value = it },
        onCloseTab = { viewModel.closeTab(it) },
        onNewTab = { viewModel.addTab() },
      )

      if (isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }

      if (loadError != null) {
        Text(
          text = loadError.orEmpty(),
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
      }

      key(activeTab.id) {
        AndroidView(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          factory = { ctx ->
            val existing = activeTab.webView
            if (existing != null) {
              (existing.parent as? ViewGroup)?.removeView(existing)
              existing
            } else {
              createWebView(
                ctx,
                activeTab,
                viewModel,
                adBlockRuleSetState,
                blockPopupsState,
                blockRedirectsState,
                toolbarVisible,
              ).also { activeTab.webView = it }
            }
          },
        )
      }

      AnimatedVisibility(visible = toolbarVisible.value) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          IconButton(
            onClick = { activeTab.webView?.goBack() },
            enabled = canGoBack,
            modifier = Modifier.size(32.dp),
          ) {
            Icon(Icons.RoundedFilled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
          }
          IconButton(
            onClick = { activeTab.webView?.goForward() },
            enabled = canGoForward,
            modifier = Modifier.size(32.dp),
          ) {
            Icon(Icons.RoundedFilled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
          }

          Box(
            modifier =
              Modifier
                .weight(1f)
                .height(32.dp)
                .padding(horizontal = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (addressBarText.isEmpty()) {
              Text(
                text = stringResource(R.string.ui_web_browser_url_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
            BasicTextField(
              value = addressBarText,
              onValueChange = { activeTab.addressBarText.value = it },
              singleLine = true,
              textStyle = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
              keyboardActions = KeyboardActions(onGo = { loadUrl(addressBarText) }),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              modifier = Modifier.fillMaxWidth(),
            )
          }

          IconButton(
            onClick = { currentPageUrl?.let { viewModel.toggleBookmark(it, activeTab.pageTitle.value) } },
            enabled = currentPageUrl != null,
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              Icons.RoundedFilled.Bookmark,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
          }
        }
      }

      pendingNavigation?.let { pending ->
        BrowserConfirmNavigationDialog(
          pending = pending,
          onAllow = {
            activeTab.webView?.loadUrl(pending.url)
            viewModel.confirmNavigation()
          },
          onDeny = { viewModel.confirmNavigation() },
        )
      }

      BrowserStreamsSheet(
        isOpen = showStreamsSheet,
        pageUrl = currentPageUrl,
        tab = activeTab,
        onDismiss = { showStreamsSheet = false },
      )
    }
  }
}

private const val SCROLL_HIDE_THRESHOLD_PX = 12
/**
 * Best-effort header set matching what the WebView itself would send for this request, so
 * mpvRx can be handed the same User-Agent/Referer/Origin/Cookie/Authorization the site expects.
 * Safe to call off the main thread: only reads [WebResourceRequest] (thread-safe by contract),
 * [CookieManager] (explicitly thread-safe), and [tab]'s cached, non-WebView state.
 */
private fun buildStreamHeaders(
  request: WebResourceRequest,
  tab: BrowserTabState,
): Map<String, String> {
  val headers = LinkedHashMap<String, String>()
  headers.putAll(request.requestHeaders)
  tab.userAgent?.let { headers.putIfAbsent("User-Agent", it) }
  val pageUrl = tab.pageUrl.value
  if (pageUrl != null) {
    headers.putIfAbsent("Referer", pageUrl)
    runCatching { URI(pageUrl) }.getOrNull()?.let { uri ->
      val portSuffix = if (uri.port != -1) ":${uri.port}" else ""
      headers.putIfAbsent("Origin", "${uri.scheme}://${uri.host}$portSuffix")
    }
  }
  CookieManager.getInstance().getCookie(request.url.toString())?.let { cookie ->
    if (cookie.isNotBlank()) headers.putIfAbsent("Cookie", cookie)
  }
  return headers
}

private fun createWebView(
  ctx: Context,
  tab: BrowserTabState,
  viewModel: WebBrowserViewModel,
  adBlockRuleSetState: MutableState<AdBlockRuleSet>,
  blockPopupsState: MutableState<Boolean>,
  blockRedirectsState: MutableState<Boolean>,
  toolbarVisibleState: MutableState<Boolean>,
): WebView =
  WebView(ctx).apply {
    layoutParams =
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    setBackgroundColor(android.graphics.Color.BLACK)
    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
    loadUrl("https://www.google.com/webhp?safe=off&pws=0&filter=0&nfpr=1&hl=en")
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(true)
    tab.userAgent = settings.userAgentString
    // Requires API 23+ (minSdk for this app is well above that).
    setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
      if (scrollY > oldScrollY + SCROLL_HIDE_THRESHOLD_PX) {
        toolbarVisibleState.value = false
      } else if (scrollY < oldScrollY - SCROLL_HIDE_THRESHOLD_PX) {
        toolbarVisibleState.value = true
      }
    }
    webViewClient =
      object : WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView,
          request: WebResourceRequest,
        ): WebResourceResponse? {
          val requestUrl = request.url.toString()
          if (adBlockRuleSetState.value.shouldBlock(requestUrl, null)) {
            return WebResourceResponse(
              "text/plain",
              "utf-8",
              403,
              "Blocked",
              emptyMap(),
              ByteArrayInputStream(ByteArray(0)),
            )
          }
          if (StreamSniffer.isVietnameseSubtitle(requestUrl)) {
            view.post { tab.setVietnameseSubtitleIfAbsent(requestUrl) }
          }
          StreamSniffer.classifyDeep(requestUrl)?.let { base ->
          val headers = buildStreamHeaders(request, tab)
            view.post { tab.addSniffedStream(base.copy(headers = headers)) }
          }
          return null
        }

        override fun shouldOverrideUrlLoading(
          view: WebView,
          request: WebResourceRequest,
        ): Boolean {
          if (!blockRedirectsState.value || request.hasGesture()) return false
          val currentHost = runCatching { URI(tab.pageUrl.value).host }.getOrNull()
          val targetHost = runCatching { URI(request.url.toString()).host }.getOrNull()
          if (targetHost != null && targetHost != currentHost) {
            viewModel.requestNavigationConfirmation(request.url.toString(), view.title)
            return true
          }
          return false
        }

        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
          tab.isLoading.value = true
          tab.loadError.value = null
          tab.addressBarText.value = pageUrl.orEmpty()
          tab.clearSniffedStreams()
        }

        override fun onPageFinished(view: WebView, pageUrl: String?) {
          tab.isLoading.value = false
          tab.pendingHttpFallbackUrl.value = null
          tab.pageUrl.value = pageUrl
          tab.pageTitle.value = view.title
          tab.canGoBack.value = view.canGoBack()
          tab.canGoForward.value = view.canGoForward()
          if (!pageUrl.isNullOrBlank()) viewModel.recordVisit(pageUrl, view.title)
        }

        override fun onReceivedError(
          view: WebView,
          request: WebResourceRequest,
          error: WebResourceError,
        ) {
          if (request.isForMainFrame) {
            val fallbackUrl = tab.pendingHttpFallbackUrl.value
            if (fallbackUrl != null) {
              tab.pendingHttpFallbackUrl.value = null
              view.loadUrl(fallbackUrl)
            } else {
              tab.isLoading.value = false
              tab.loadError.value = error.description?.toString()
            }
          }
        }
      }
    webChromeClient =
      object : WebChromeClient() {
        override fun onCreateWindow(
          view: WebView,
          isDialog: Boolean,
          isUserGesture: Boolean,
          resultMsg: Message,
        ): Boolean {
          if (!blockPopupsState.value) return false
          val tempWebView = WebView(ctx)
          tempWebView.webViewClient =
            object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                v: WebView,
                request: WebResourceRequest,
              ): Boolean {
                viewModel.requestNavigationConfirmation(request.url.toString(), view.title)
                tempWebView.destroy()
                return true
              }
            }
          val transport = resultMsg.obj as WebView.WebViewTransport
          transport.webView = tempWebView
          resultMsg.sendToTarget()
          return true
        }
      }
    post { setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null) }
  }
