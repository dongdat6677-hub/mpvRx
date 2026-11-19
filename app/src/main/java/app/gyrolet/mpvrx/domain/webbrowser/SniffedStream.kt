/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.gyrolet.mpvrx.domain.webbrowser

enum class StreamType { HLS, DASH, PROGRESSIVE }

data class SniffedStream(
  val url: String,
  val type: StreamType,
  val qualityLabel: String?,
  val isLikelyAd: Boolean,
)

object StreamSniffer {
  private val EXTENSION_TYPE =
    mapOf(
      ".m3u8" to StreamType.HLS,
      ".mpd" to StreamType.DASH,
    )
  private val PROGRESSIVE_EXTENSIONS = listOf(".mp4", ".webm", ".mkv")
  private val QUALITY_REGEX = Regex("(?i)(2160|1440|1080|720|480|360|240)p?")
  private val AD_KEYWORDS =
    listOf("/ads/", "/ad/", "adbreak", "adserver", "vmap", "vast", "doubleclick", "/preroll", "/midroll")

  /** Returns null when [requestUrl] doesn't look like a playable video stream at all. */
  fun classify(requestUrl: String): SniffedStream? {
    val lower = requestUrl.lowercase()
    val type =
      EXTENSION_TYPE.entries.firstOrNull { lower.contains(it.key) }?.value
        ?: if (PROGRESSIVE_EXTENSIONS.any { lower.contains(it) }) StreamType.PROGRESSIVE else null
    type ?: return null
    val quality = QUALITY_REGEX.find(requestUrl)?.groupValues?.get(1)?.let { "${it}p" }
    val isLikelyAd = AD_KEYWORDS.any { lower.contains(it) }
    return SniffedStream(requestUrl, type, quality, isLikelyAd)
  }

  /** Sorts so a resolution near 1080p comes first; unknown resolution goes last. */
  fun sortedByPreferredQuality(streams: List<SniffedStream>): List<SniffedStream> =
    streams.sortedBy { stream ->
      val height = stream.qualityLabel?.removeSuffix("p")?.toIntOrNull()
      if (height == null) Int.MAX_VALUE else kotlin.math.abs(height - 1080)
    }
}
