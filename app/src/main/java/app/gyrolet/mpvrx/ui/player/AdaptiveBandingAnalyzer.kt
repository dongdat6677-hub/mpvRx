
package app.gyrolet.mpvrx.ui.player

/**
 * Estimates visible gradient banding from a compact RGB24 frame sample.
 *
 * Input format:
 * - Bytes 0-1: width, unsigned 16-bit little-endian.
 * - Bytes 2-3: height, unsigned 16-bit little-endian.
 * - Remaining bytes: packed RGB24 pixels.
 *
 * Returns a heuristic score from 0 to 100, or null for invalid samples.
 */
internal object AdaptiveBandingAnalyzer {

  private const val HEADER_SIZE = 4
  private const val MIN_DIMENSION = 16
  private const val MAX_DIMENSION = 256
  private const val WINDOW_SIZE = 12
  private const val WINDOW_STRIDE = 4

  private data class Pixel(
    val red: Int,
    val green: Int,
    val blue: Int,
    val luma: Int,
  )

  private data class SegmentResult(
    val candidate: Boolean,
    val direction: Int,
    val range: Int,
  )

  fun analyze(sample: ByteArray?): Int? {
    if (sample == null || sample.size < HEADER_SIZE) return null

    val width =
      (sample[0].toInt() and 0xFF) or
        ((sample[1].toInt() and 0xFF) shl 8)

    val height =
      (sample[2].toInt() and 0xFF) or
        ((sample[3].toInt() and 0xFF) shl 8)

    if (
      width !in MIN_DIMENSION..MAX_DIMENSION ||
      height !in MIN_DIMENSION..MAX_DIMENSION
    ) {
      return null
    }

    val expectedSize = HEADER_SIZE.toLong() + width.toLong() * height * 3L
    if (expectedSize != sample.size.toLong()) return null

    val pixels = Array(width * height) {
      val offset = HEADER_SIZE + it * 3

      val red = sample[offset].toInt() and 0xFF
      val green = sample[offset + 1].toInt() and 0xFF
      val blue = sample[offset + 2].toInt() and 0xFF

      Pixel(
        red = red,
        green = green,
        blue = blue,
        luma = (77 * red + 150 * green + 29 * blue) shr 8,
      )
    }

    var horizontalCandidates = 0
    var horizontalTotal = 0

    // Horizontal gradient analysis.
    for (y in 0 until height) {
      val rowStart = y * width
      var x = 0

      while (x + WINDOW_SIZE <= width) {
        horizontalTotal++

        if (isBandingLikeSegment(pixels, rowStart + x, 1)) {
          horizontalCandidates++
        }

        x += WINDOW_STRIDE
      }
    }

    var verticalCandidates = 0
    var verticalTotal = 0

    // Vertical gradient analysis.
    for (x in 0 until width) {
      var y = 0

      while (y + WINDOW_SIZE <= height) {
        verticalTotal++

        if (isBandingLikeSegment(pixels, y * width + x, width)) {
          verticalCandidates++
        }

        y += WINDOW_STRIDE
      }
    }

    val total = horizontalTotal + verticalTotal
    if (total == 0) return 0

    val candidates = horizontalCandidates + verticalCandidates

    return (candidates * 100 / total).coerceIn(0, 100)
  }

  private fun isBandingLikeSegment(
    pixels: Array<Pixel>,
    start: Int,
    stride: Int,
  ): Boolean {
    val channels = Array(4) { IntArray(WINDOW_SIZE) }

    for (i in 0 until WINDOW_SIZE) {
      val pixel = pixels[start + i * stride]

      channels[0][i] = pixel.luma
      channels[1][i] = pixel.red
      channels[2][i] = pixel.green
      channels[3][i] = pixel.blue
    }

    // A candidate is detected when at least one color representation
    // contains a quantized, approximately monotonic gradient.
    for (channel in channels) {
      if (isQuantizedGradient(channel)) return true
    }

    return false
  }

  private fun isQuantizedGradient(values: IntArray): Boolean {
    var minimum = 255
    var maximum = 0
    var flatSteps = 0
    var changingSteps = 0
    var direction = 0
    var directionChanges = 0

    for (i in values.indices) {
      val value = values[i]
      minimum = minOf(minimum, value)
      maximum = maxOf(maximum, value)

      if (i == 0) continue

      val difference = value - values[i - 1]
      val magnitude = kotlin.math.abs(difference)

      if (magnitude <= 1) {
        flatSteps++
        continue
      }

      // Reject strong edges and abrupt transitions.
      if (magnitude > 8) return false

      changingSteps++

      val currentDirection = if (difference > 0) 1 else -1

      if (direction != 0 && currentDirection != direction) {
        directionChanges++
      }

      direction = currentDirection
    }

    val range = maximum - minimum

    return range in 4..40 &&
      flatSteps >= 5 &&
      changingSteps in 2..7 &&
      directionChanges <= 1
  }
}
