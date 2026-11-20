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

enum class StreamVerification { PENDING, VERIFIED, FAILED }

data class SniffedStream(
  val url: String,
  val type: StreamType,
  val qualityLabel: String?,
  val isLikelyAd: Boolean,
  val headers: Map<String, String> = emptyMap(),
  val verification: StreamVerification = StreamVerification.PENDING,
  val durationMs: Long? = null,
  val bitrate: Long? = null,
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

  private val SUBTITLE_EXTENSIONS = listOf(".vtt", ".srt", ".ass", ".ssa")
  private val VIETNAMESE_MARKER_REGEX =
    Regex("(?i)(^|[/_\\-.?&=])(vi|vie|viet|vietnamese|việt)([/_\\-.?&=]|$)")

  /** Heuristic: a subtitle file whose URL marks itself as Vietnamese (e.g. "sub_vi.vtt", "?lang=vi"). */
  fun isVietnameseSubtitle(requestUrl: String): Boolean {
    val lower = requestUrl.lowercase()
    if (SUBTITLE_EXTENSIONS.none { lower.contains(it) }) return false
    return VIETNAMESE_MARKER_REGEX.containsMatchIn(requestUrl)
  }
  private val DEEP_QUALITY_REGEX = Regex("(?i)(2160|1440|1080|720|480|360|240)p?")
  private val DEEP_AD_KEYWORDS = listOf("/ads/", "adbreak", "vast", "doubleclick", "/ad/", "advert")
  private val DEEP_MANIFEST_KEYWORDS = listOf("manifest", "playlist", "chunklist", "/hls/", "/dash/")
  private val KNOWN_EXTENSION_REGEX = Regex("""\.(m3u8|mpd|mp4|webm|mkv)(\?|$)""")

  /** Falls back to keyword detection for manifest-style URLs with no file extension (covers most iframe-embedded players). */
  private val SEGMENT_FILENAME_REGEX = Regex("(?i)(^|/)(seg|segment|chunk|part|frag)[-_]?\\d+\\.")

  fun classifyDeep(requestUrl: String): SniffedStream? {
    if (SEGMENT_FILENAME_REGEX.containsMatchIn(requestUrl)) return null
    classify(requestUrl)?.let { return it }
    val lower = requestUrl.lowercase()
    if (KNOWN_EXTENSION_REGEX.containsMatchIn(lower)) return null
    if (DEEP_MANIFEST_KEYWORDS.none { lower.contains(it) }) return null
    return SniffedStream(
      url = requestUrl,
      type = StreamType.HLS,
      qualityLabel = DEEP_QUALITY_REGEX.find(requestUrl)?.value,
      isLikelyAd = DEEP_AD_KEYWORDS.any { lower.contains(it) },
    )
  }
}
