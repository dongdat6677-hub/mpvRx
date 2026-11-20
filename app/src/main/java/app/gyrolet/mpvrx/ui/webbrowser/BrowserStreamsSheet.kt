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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import android.net.Uri
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.domain.webbrowser.StreamVerification
import app.gyrolet.mpvrx.domain.webbrowser.StreamVerifier
import app.gyrolet.mpvrx.domain.webbrowser.HlsPlaylistProbe
import app.gyrolet.mpvrx.domain.webbrowser.SniffedStream
import app.gyrolet.mpvrx.domain.webbrowser.StreamSniffer
import app.gyrolet.mpvrx.ui.player.ytdlp.YtdlpManager
import app.gyrolet.mpvrx.utils.media.MediaUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserStreamsSheet(
  isOpen: Boolean,
  pageUrl: String?,
  tab: BrowserTabState,
  onDismiss: () -> Unit,
) {
  if (!isOpen) return

  val streams = tab.sniffedStreams
  val vietnameseSubtitleUrl = tab.vietnameseSubtitleUrl.value
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  LaunchedEffect(isOpen) {
    streams
      .filter { it.verification == StreamVerification.PENDING && !it.isLikelyAd }
      .forEach { stream ->
        scope.launch {
          val updated =
            if (stream.type == StreamType.HLS) {
              when (val info = HlsPlaylistProbe.probe(stream.url, stream.headers)) {
                null -> stream.copy(verification = StreamVerification.FAILED)
                else ->
                  if (info.isMaster || info.hasSegments) {
                    stream.copy(
                      verification = StreamVerification.VERIFIED,
                      qualityLabel = info.bestResolution?.let(::resolutionToQualityLabel) ?: stream.qualityLabel,
                      bitrate = info.bestBandwidthBps ?: stream.bitrate,
                    )
                  } else {
                    stream.copy(verification = StreamVerification.FAILED)
                  }
              }
            } else {
              StreamVerifier.verify(stream)
            }
          tab.updateStreamVerification(updated)
        }
      }
  }
  val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
  val sorted = StreamSniffer.sortedByPreferredQuality(streams)
  val isYoutube = pageUrl != null && YtdlpManager.requiresYtdlp(pageUrl)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
  ) {
    Column {
      if (isYoutube && pageUrl != null) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.ui_web_browser_play_via_ytdlp)) },
          modifier =
            Modifier.clickable {
              MediaUtils.playFile(pageUrl, context, "webview_sniffer")
              onDismiss()
            },
        )
      }

      if (!pageUrl.isNullOrBlank()) {
        Text(
          text = tab.pageTitle.value ?: pageUrl,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }

      if (vietnameseSubtitleUrl != null) {
        Text(
          text = stringResource(R.string.ui_web_browser_vi_subtitle_found),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }

      if (sorted.isEmpty() && !isYoutube) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(R.string.ui_web_browser_streams_empty),
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      } else if (sorted.isNotEmpty()) {
        LazyColumn {
          items(sorted) { stream ->
            val streamHost = runCatching { Uri.parse(stream.url).host }.getOrNull()
            val pageHost = runCatching { pageUrl?.let { Uri.parse(it).host } }.getOrNull()
            val crossDomain = streamHost != null && pageHost != null &&
              streamHost != pageHost && !streamHost.endsWith(".$pageHost")
            val details = buildList {
              add(stream.qualityLabel ?: stream.type.name)
              stream.durationMs?.let { add(formatStreamDuration(it)) }
              stream.bitrate?.let { add(formatStreamBitrate(it)) }
              if (crossDomain) add(stringResource(R.string.ui_web_browser_stream_cross_domain))
              if (stream.isLikelyAd) add(stringResource(R.string.ui_web_browser_stream_ad_tag))
              add(
                when (stream.verification) {
                  StreamVerification.VERIFIED -> stringResource(R.string.ui_web_browser_stream_verified)
                  StreamVerification.FAILED -> stringResource(R.string.ui_web_browser_stream_unverified)
                  StreamVerification.PENDING -> stringResource(R.string.ui_web_browser_stream_checking)
                },
              )
            }
            ListItem(
              headlineContent = { Text(streamDisplayName(stream.url), maxLines = 1) },
              supportingContent = { Text(details.joinToString(" · ")) },
              modifier =
                Modifier.clickable {
                  MediaUtils.playFile(
                    stream.url,
                    context,
                    "webview_sniffer",
                    headers = stream.headers,
                    subtitles = vietnameseSubtitleUrl?.let { listOf(Uri.parse(it)) } ?: emptyList(),
                  )
                  onDismiss()
              },
            )
          }
        }
      }
    }
  }
}

private fun streamDisplayName(url: String): String {
  val uri = Uri.parse(url)
  val last = uri.lastPathSegment?.takeIf { it.isNotBlank() }
  val host = uri.host
  return when {
    host != null && last != null -> "$host/$last"
    last != null -> last
    else -> host ?: url
  }
}

private fun resolutionToQualityLabel(resolution: String): String {
  val height = resolution.substringAfter('x').toIntOrNull()
  return if (height != null) "${height}p" else resolution
}

private fun formatStreamDuration(durationMs: Long): String {
  val totalSeconds = durationMs / 1000
  return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatStreamBitrate(bitrate: Long): String {
  return "${bitrate / 1000} kbps"
}
