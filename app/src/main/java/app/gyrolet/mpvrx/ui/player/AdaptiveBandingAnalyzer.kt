
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

    val luminance = IntArray(width * height)
    var offset = HEADER_SIZE

    for (i in luminance.indices) {
      val red = sample[offset++].toInt() and 0xFF
      val green = sample[offset++].toInt() and 0xFF
      val blue = sample[offset++].toInt() and 0xFF

      luminance[i] = (77 * red + 150 * green + 29 * blue) shr 8
    }

    var candidates = 0
    var totalWindows = 0

    // Analyze horizontal gradients.
    for (y in 0 until height) {
      val rowStart = y * width

      var x = 0
      while (x + WINDOW_SIZE <= width) {
        totalWindows++

        if (isBandingLikeSegment(luminance, rowStart + x, 1)) {
          candidates++
        }

        x += WINDOW_STRIDE
      }
    }

    // Analyze vertical gradients.
    for (x in 0 until width) {
      var y = 0
      while (y + WINDOW_SIZE <= height) {
        totalWindows++

        if (isBandingLikeSegment(luminance, y * width + x, width)) {
          candidates++
        }

        y += WINDOW_STRIDE
      }
    }

    if (totalWindows == 0) return 0

    return (candidates * 100 / totalWindows).coerceIn(0, 100)
  }

  private fun isBandingLikeSegment(
    luminance: IntArray,
    start: Int,
    stride: Int,
  ): Boolean {
    var minimum = 255
    var maximum = 0
    var flatSteps = 0
    var changingSteps = 0
    var direction = 0
    var directionChanges = 0

    for (i in 0 until WINDOW_SIZE) {
      val value = luminance[start + i * stride]
      minimum = minOf(minimum, value)
      maximum = maxOf(maximum, value)

      if (i == 0) continue

      val previous = luminance[start + (i - 1) * stride]
      val difference = value - previous
      val magnitude = kotlin.math.abs(difference)

      if (magnitude <= 1) {
        flatSteps++
        continue
      }

      // Large transitions are more likely to be object edges than banding.
      if (magnitude > 8) return false

      changingSteps++

      val currentDirection = if (difference > 0) 1 else -1

      if (direction != 0 && currentDirection != direction) {
        directionChanges++
      }

      direction = currentDirection
    }

    val range = maximum - minimum

    return range in 4..32 &&
      flatSteps >= 5 &&
      changingSteps in 2..7 &&
      directionChanges <= 1
  }
}
