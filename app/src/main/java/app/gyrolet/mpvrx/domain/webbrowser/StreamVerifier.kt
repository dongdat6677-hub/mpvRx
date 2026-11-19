package app.gyrolet.mpvrx.domain.webbrowser

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Probes a sniffed stream with MediaMetadataRetriever to confirm it is real video and read duration/bitrate. */
object StreamVerifier {
  suspend fun verify(stream: SniffedStream): SniffedStream = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
      val result = withTimeoutOrNull(6000L) {
        retriever.setDataSource(stream.url, stream.headers)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        Triple(durationMs, bitrate, hasVideo)
      }
      if (result != null && result.third) {
        stream.copy(
          verification = StreamVerification.VERIFIED,
          durationMs = result.first,
          bitrate = result.second,
        )
      } else {
        stream.copy(verification = StreamVerification.FAILED)
      }
    } catch (e: Exception) {
      stream.copy(verification = StreamVerification.FAILED)
    } finally {
      retriever.release()
    }
  }
}
