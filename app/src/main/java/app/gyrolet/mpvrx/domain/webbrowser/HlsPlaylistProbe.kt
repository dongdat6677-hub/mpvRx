/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx.domain.webbrowser

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class HlsPlaylistInfo(
  val isMaster: Boolean,
  val hasSegments: Boolean,
  val bestResolution: String?,
  val bestBandwidthBps: Long?,
)

/**
 * Reads an HLS playlist's own text to find its real resolution/bitrate. Far more reliable than
 * probing a .m3u8 URL with MediaMetadataRetriever, which cannot parse HLS manifest syntax.
 */
object HlsPlaylistProbe {
  private const val MAX_CHARS = 128 * 1024
  private val STREAM_INF_REGEX = Regex("#EXT-X-STREAM-INF:(.*)")
  private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""")
  private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+x\d+)""")

  suspend fun probe(url: String, headers: Map<String, String>): HlsPlaylistInfo? =
    withContext(Dispatchers.IO) {
      withTimeoutOrNull(5000L) {
        runCatching {
          val connection = URL(url).openConnection() as HttpURLConnection
          connection.connectTimeout = 4000
          connection.readTimeout = 4000
          headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
          connection.inputStream.bufferedReader().use { reader ->
            val text = StringBuilder()
            val buffer = CharArray(4096)
            var total = 0
            while (true) {
              val read = reader.read(buffer)
              if (read == -1) break
              text.append(buffer, 0, read)
              total += read
              if (total >= MAX_CHARS) break
            }
            parse(text.toString())
          }
        }.getOrNull()
      }
    }

  private fun parse(text: String): HlsPlaylistInfo {
    var bestBandwidth: Long? = null
    var bestResolution: String? = null
    var isMaster = false
    var hasSegments = false
    text.lineSequence().forEach { line ->
      val match = STREAM_INF_REGEX.find(line)
      if (match != null) {
        isMaster = true
        val attrs = match.groupValues[1]
        val bandwidth = BANDWIDTH_REGEX.find(attrs)?.groupValues?.get(1)?.toLongOrNull()
        if (bandwidth != null && (bestBandwidth == null || bandwidth > bestBandwidth!!)) {
          bestBandwidth = bandwidth
          bestResolution = RESOLUTION_REGEX.find(attrs)?.groupValues?.get(1)
        }
      } else if (line.startsWith("#EXTINF:")) {
        hasSegments = true
      }
    }
    return HlsPlaylistInfo(isMaster, hasSegments, bestResolution, bestBandwidth)
  }
}
