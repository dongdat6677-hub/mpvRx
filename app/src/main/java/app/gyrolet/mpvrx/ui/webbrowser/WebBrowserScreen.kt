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
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.AppTextField as OutlinedTextField
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import java.io.ByteArrayInputStream
import kotlinx.serialization.Serializable

/**
 * The in-app web browser (Phase 1 of the integrated-browser feature; see
 * docs/superpowers/specs/2026-11-17-integrated-browser-design.md).
 *
 * Every request the WebView makes passes through [WebBrowserViewModel]'s
 * [app.gyrolet.mpvrx.domain.adblock.AdBlockRuleSet] in
 * `shouldInterceptRequest` below; a blocked request never reaches the
 * network. Phase 2 (stream sniffing) extends that same override to also
 * classify and collect the requests that were *not* blocked, instead of
 * adding a second interception path.
 */
@Serializable
object WebBrowserScreen : Screen {
  @SuppressLint("SetJavaScriptEnabled")
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val viewModel = remember { WebBrowserViewModel(context) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val addressBarText by viewModel.url
    val isLoading by viewModel.isLoading
    val canGoBack by viewModel.canGoBack
    val canGoForward by viewModel.canGoForward
    val loadError by viewModel.loadError

    fun loadUrl(input: String) {
      val trimmed = input.trim()
      if (trimmed.isEmpty()) return
      val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
      webViewRef?.loadUrl(normalized)
    }

    BackHandler {
      val webView = webViewRef
      if (webView != null && canGoBack) webView.goBack() else backStack.popSafely()
    }
    
    LaunchedEffect(webViewRef) {
      if (webViewRef != null && viewModel.pageUrl.value == null) {
        loadUrl("https://www.google.com")
      }
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
          IconButton(onClick = { webViewRef?.reload() }) {
            Icon(Icons.RoundedFilled.Refresh, contentDescription = null)
          }
        },
      )

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
          Icon(Icons.RoundedFilled.ArrowBack, contentDescription = null)
        }
        IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
          Icon(Icons.RoundedFilled.ArrowForward, contentDescription = null)
        }
        OutlinedTextField(
          value = addressBarText,
          onValueChange = { viewModel.url.value = it },
          modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
          singleLine = true,
          placeholder = { Text(stringResource(R.string.ui_web_browser_url_hint)) },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { loadUrl(addressBarText) }),
        )
      }

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

      AndroidView(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
            // Rules out a dark-themed empty WebView rendering as solid black before any page loads.
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient =
              object : WebViewClient() {
                override fun shouldInterceptRequest(
                  view: WebView,
                  request: WebResourceRequest,
                ): WebResourceResponse? {
                  val requestUrl = request.url.toString()
                  // view.url must not be read here: shouldInterceptRequest runs off the main
                  // thread, and WebView.getUrl() is main-thread-only (this is what crashed).
                  return if (viewModel.adBlockRuleSet.shouldBlock(requestUrl, null)) {
                    WebResourceResponse(
                      "text/plain",
                      "utf-8",
                      403,
                      "Blocked",
                      emptyMap(),
                      ByteArrayInputStream(ByteArray(0)),
                    )
                  } else {
                    // Default handling; Phase 2 will also record requestUrl/view.url here.
                    null
                  }
                }

                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                  viewModel.isLoading.value = true
                  viewModel.loadError.value = null
                  viewModel.url.value = pageUrl.orEmpty()
                }

                override fun onPageFinished(view: WebView, pageUrl: String?) {
                  viewModel.isLoading.value = false
                  viewModel.pageUrl.value = pageUrl
                  viewModel.canGoBack.value = view.canGoBack()
                  viewModel.canGoForward.value = view.canGoForward()
                }

                override fun onReceivedError(
                  view: WebView,
                  request: WebResourceRequest,
                  error: WebResourceError,
                ) {
                  if (request.isForMainFrame) {
                    viewModel.isLoading.value = false
                    viewModel.loadError.value = error.description?.toString()
                  }
                }
              }
            webViewRef = this
          }
        },
      )
    }
  }
}
