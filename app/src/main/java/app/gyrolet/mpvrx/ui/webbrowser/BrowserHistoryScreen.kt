/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.webbrowser.repository.BrowserHistoryRepository
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.inject

@Serializable
object BrowserHistoryScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val repository by inject<BrowserHistoryRepository>(BrowserHistoryRepository::class.java)
    val scope = remember { CoroutineScope(Dispatchers.IO + SupervisorJob()) }
    val history by repository.observeHistory().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
      TopAppBar(
        title = { Text(stringResource(R.string.ui_web_browser_history)) },
        navigationIcon = {
          IconButton(onClick = { backStack.popSafely() }) {
            Icon(Icons.RoundedFilled.ArrowBack, contentDescription = stringResource(R.string.back))
          }
        },
        actions = {
          IconButton(onClick = { scope.launch { repository.clearAll() } }) {
            Icon(Icons.RoundedFilled.Close, contentDescription = null)
          }
        },
      )

      if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(R.string.ui_web_browser_history_empty),
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      } else {
        LazyColumn {
          items(history) { entry ->
            ListItem(
              headlineContent = { Text(entry.title?.takeIf { it.isNotBlank() } ?: entry.url) },
              supportingContent = { Text(entry.url) },
              modifier =
                Modifier.clickable {
                  backStack.popSafely()
                  WebBrowserScreen.pendingLoadUrl.value = entry.url
                },
            )
          }
        }
      }
    }
  }
}
