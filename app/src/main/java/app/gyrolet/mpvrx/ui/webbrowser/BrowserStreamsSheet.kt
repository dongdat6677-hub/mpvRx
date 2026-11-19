/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.ui.webbrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.webbrowser.SniffedStream
import app.gyrolet.mpvrx.domain.webbrowser.StreamSniffer
import app.gyrolet.mpvrx.utils.media.MediaUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserStreamsSheet(
  isOpen: Boolean,
  streams: List<SniffedStream>,
  onDismiss: () -> Unit,
) {
  if (!isOpen) return

  val context = LocalContext.current
  val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
  val sorted = StreamSniffer.sortedByPreferredQuality(streams)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    if (sorted.isEmpty()) {
      Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
          text = stringResource(R.string.ui_web_browser_streams_empty),
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    } else {
      LazyColumn {
        items(sorted) { stream ->
          val qualityText = stream.qualityLabel ?: stream.type.name
          val subtitle =
            if (stream.isLikelyAd) {
              "$qualityText · ${stringResource(R.string.ui_web_browser_stream_ad_tag)}"
            } else {
              qualityText
            }
          ListItem(
            headlineContent = { Text(stream.url, maxLines = 1) },
            supportingContent = { Text(subtitle) },
            modifier =
              Modifier.clickable {
                MediaUtils.playFile(stream.url, context, "webview_sniffer")
                onDismiss()
              },
          )
        }
      }
    }
  }
}
