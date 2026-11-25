
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

  private const val RED_OFFSET = 0
  private const val GREEN_OFFSET = 1
  private const val BLUE_OFFSET = 2

 
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

    // Analyze horizontal gradients.
    val horizontalWindows =
      ((width - WINDOW_SIZE) / WINDOW_STRIDE) + 1

    val horizontalCandidates =
      BooleanArray(height * horizontalWindows)

    var horizontalTotal = 0

    for (y in 0 until height) {
      val rowStart = HEADER_SIZE + y * width * 3
      var x = 0
      var windowIndex = 0

      while (x + WINDOW_SIZE <= width) {
        horizontalTotal++

        val start = rowStart + x * 3

        horizontalCandidates[y * horizontalWindows + windowIndex] =
          isBandingLikeSegment(sample, start, 3)

        x += WINDOW_STRIDE
        windowIndex++
      }
    }

    // Analyze vertical gradients.
    val verticalWindows =
      ((height - WINDOW_SIZE) / WINDOW_STRIDE) + 1

    val verticalCandidates =
      BooleanArray(width * verticalWindows)

    var verticalTotal = 0

    for (x in 0 until width) {
      var y = 0
      var windowIndex = 0

      while (y + WINDOW_SIZE <= height) {
        verticalTotal++

        val start = HEADER_SIZE + (y * width + x) * 3

        verticalCandidates[x * verticalWindows + windowIndex] =
          isBandingLikeSegment(sample, start, width * 3)

        y += WINDOW_STRIDE
        windowIndex++
      }
    }

    val horizontalSupported = countSpatiallySupported(
      horizontalCandidates,
      height,
      horizontalWindows,
    )

    val verticalSupported = countSpatiallySupported(
      verticalCandidates,
      width,
      verticalWindows,
    )

    val total = horizontalTotal + verticalTotal

    if (total == 0) return 0

    val candidates = horizontalSupported + verticalSupported

    return (candidates * 100 / total).coerceIn(0, 100)
  }

  private fun countSpatiallySupported(
    candidates: BooleanArray,
    lineCount: Int,
    windowsPerLine: Int,
  ): Int {
    var supported = 0

    for (line in 0 until lineCount) {
      for (window in 0 until windowsPerLine) {
        if (!candidates[line * windowsPerLine + window]) {
          continue
        }

        var hasNeighbor = false

        val firstLine = maxOf(0, line - 1)
        val lastLine = minOf(lineCount - 1, line + 1)

        val firstWindow = maxOf(0, window - 1)
        val lastWindow = minOf(windowsPerLine - 1, window + 1)

        for (neighborLine in firstLine..lastLine) {
          if (neighborLine == line) continue

          for (neighborWindow in firstWindow..lastWindow) {
            if (
              candidates[
                neighborLine * windowsPerLine + neighborWindow
              ]
            ) {
              hasNeighbor = true
              break
            }
          }

          if (hasNeighbor) break
        }

        if (hasNeighbor) {
          supported++
        }
      }
    }

    return supported
  }

  private fun isBandingLikeSegment(
    sample: ByteArray,
    start: Int,
    stride: Int,
  ): Boolean {
    return isQuantizedGradient(
      sample,
      start,
      stride,
      0,
    ) ||
      isQuantizedGradient(
        sample,
        start,
        stride,
        1,
      ) ||
      isQuantizedGradient(
        sample,
        start,
        stride,
        2,
      ) ||
      isQuantizedGradient(
        sample,
        start,
        stride,
        3,
      )
  }

  /**
   * Channel:
   * 0 = luminance
   * 1 = red
   * 2 = green
   * 3 = blue
   */
  private fun isQuantizedGradient(
    sample: ByteArray,
    start: Int,
    stride: Int,
    channel: Int,
  ): Boolean {
    var minimum = 255
    var maximum = 0
    var flatSteps = 0
    var changingSteps = 0
    var direction = 0
    var directionChanges = 0

    var previous = -1

    for (i in 0 until WINDOW_SIZE) {
      val offset = start + i * stride

      val red = sample[offset + RED_OFFSET].toInt() and 0xFF
      val green = sample[offset + GREEN_OFFSET].toInt() and 0xFF
      val blue = sample[offset + BLUE_OFFSET].toInt() and 0xFF

      val value = when (channel) {
        0 -> (77 * red + 150 * green + 29 * blue) shr 8
        1 -> red
        2 -> green
        else -> blue
      }

      minimum = minOf(minimum, value)
      maximum = maxOf(maximum, value)

      if (i == 0) {
        previous = value
        continue
      }

      val difference = value - previous
      val magnitude = kotlin.math.abs(difference)

      if (magnitude <= 1) {
        flatSteps++
        previous = value
        continue
      }

      // Reject strong transitions that are more likely to be object edges.
      if (magnitude > 8) return false

      changingSteps++

      val currentDirection = if (difference > 0) 1 else -1

      if (direction != 0 && currentDirection != direction) {
        directionChanges++
      }

      direction = currentDirection
      previous = value
    }

    val range = maximum - minimum

    return range in 4..40 &&
      flatSteps >= 5 &&
      changingSteps in 2..7 &&
      directionChanges <= 1
  }
}
