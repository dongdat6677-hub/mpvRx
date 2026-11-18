/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

@Composable
fun BrowserTabBar(
  tabs: List<BrowserTabState>,
  activeTabId: String,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onNewTab: () -> Unit,
) {
  LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp(), vertical = 4.dp())) {
    items(tabs, key = { it.id }) { tab ->
      val pageTitle by tab.pageTitle
      val pageUrl by tab.pageUrl
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier
            .padding(end = 8.dp())
            .background(
              if (tab.id == activeTabId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              shape = MaterialTheme.shapes.small,
            )
            .clickable { onSelectTab(tab.id) }
            .padding(horizontal = 8.dp(), vertical = 4.dp()),
      ) {
        Text(
          text = pageTitle?.takeIf { it.isNotBlank() } ?: pageUrl ?: stringResource(R.string.ui_web_browser_new_tab),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 120.dp()),
        )
        IconButton(onClick = { onCloseTab(tab.id) }, modifier = Modifier.size(20.dp())) {
          Icon(Icons.RoundedFilled.Close, contentDescription = stringResource(R.string.ui_web_browser_close_tab))
        }
      }
    }
    item {
      IconButton(onClick = onNewTab) {
        Icon(Icons.RoundedFilled.Add, contentDescription = stringResource(R.string.ui_web_browser_new_tab))
      }
    }
  }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
