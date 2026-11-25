
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

    val horizontalSignatures =
      IntArray(height * horizontalWindows)

    var horizontalTotal = 0

    for (y in 0 until height) {
      val rowStart = HEADER_SIZE + y * width * 3
      var x = 0
      var windowIndex = 0

      while (x + WINDOW_SIZE <= width) {
        horizontalTotal++

        val start = rowStart + x * 3

        horizontalSignatures[y * horizontalWindows + windowIndex] =
          getBandingSignature(sample, start, 3)

        x += WINDOW_STRIDE
        windowIndex++
      }
    }

    // Analyze vertical gradients.
    val verticalWindows =
      ((height - WINDOW_SIZE) / WINDOW_STRIDE) + 1

    val verticalSignatures =
      IntArray(width * verticalWindows)

    var verticalTotal = 0

    for (x in 0 until width) {
      var y = 0
      var windowIndex = 0

      while (y + WINDOW_SIZE <= height) {
        verticalTotal++

        val start = HEADER_SIZE + (y * width + x) * 3

        verticalSignatures[x * verticalWindows + windowIndex] =
          getBandingSignature(sample, start, width * 3)

        y += WINDOW_STRIDE
        windowIndex++
      }
    }

    val horizontalSupported = countSpatiallySupported(
      horizontalSignatures,
      height,
      horizontalWindows,
    )

    val verticalSupported = countSpatiallySupported(
      verticalSignatures,
      width,
      verticalWindows,
    )

    val total = horizontalTotal + verticalTotal

    if (total == 0) return 0

    val candidates = horizontalSupported + verticalSupported

    return (candidates * 100 / total).coerceIn(0, 100)
  }

private fun countSpatiallySupported(
  signatures: IntArray,
  lineCount: Int,
  windowsPerLine: Int,
): Int {
  var supported = 0

  for (line in 0 until lineCount) {
    for (window in 0 until windowsPerLine) {
      val current = signatures[line * windowsPerLine + window]

      if (current == 0) continue

      var hasSimilarNeighbor = false

      val firstLine = maxOf(0, line - 1)
      val lastLine = minOf(lineCount - 1, line + 1)

      val firstWindow = maxOf(0, window - 1)
      val lastWindow = minOf(windowsPerLine - 1, window + 1)

      for (neighborLine in firstLine..lastLine) {
        if (neighborLine == line) continue

        for (neighborWindow in firstWindow..lastWindow) {
          val neighbor =
            signatures[neighborLine * windowsPerLine + neighborWindow]

          if (neighbor == 0) continue

          if (areSignaturesSimilar(current, neighbor)) {
            hasSimilarNeighbor = true
            break
          }
        }

        if (hasSimilarNeighbor) break
      }

      if (hasSimilarNeighbor) {
        supported++
      }
    }
  }

  return supported
}

private fun areSignaturesSimilar(
  first: Int,
  second: Int,
): Boolean {
  val firstDirection = first and 0x3
  val secondDirection = second and 0x3

  if (firstDirection != secondDirection) return false

  val firstAmplitude = (first ushr 2) and 0x3F
  val secondAmplitude = (second ushr 2) and 0x3F

  if (kotlin.math.abs(firstAmplitude - secondAmplitude) > 6) {
    return false
  }

  val firstFlatSteps = (first ushr 8) and 0xF
  val secondFlatSteps = (second ushr 8) and 0xF

  if (kotlin.math.abs(firstFlatSteps - secondFlatSteps) > 2) {
    return false
  }

  val firstChangingSteps = (first ushr 12) and 0xF
  val secondChangingSteps = (second ushr 12) and 0xF

  if (kotlin.math.abs(firstChangingSteps - secondChangingSteps) > 2) {
    return false
  }

  val firstTransitionStart = (first ushr 16) and 0xF
  val secondTransitionStart = (second ushr 16) and 0xF

  if (
    kotlin.math.abs(firstTransitionStart - secondTransitionStart) > 0
  ) {
    return false
  }

  val firstTransitionEnd = (first ushr 20) and 0xF
  val secondTransitionEnd = (second ushr 20) and 0xF

  if (
    kotlin.math.abs(firstTransitionEnd - secondTransitionEnd) > 0
  ) {
    return false
  }

  return true
}

/**
 * Returns a packed gradient signature.
 *
 * 0 means the segment does not match the current
 * quantized-gradient heuristic.
 *
 * Bit layout:
 *  0-1   : direction
 *  2-7   : amplitude
 *  8-11  : flat steps
 * 12-15  : changing steps
 * 16-19  : first transition position
 * 20-23  : last transition position
 */
private fun getBandingSignature(
  sample: ByteArray,
  start: Int,
  stride: Int,
): Int {
  for (channel in 0..3) {
    val signature = analyzeGradientChannel(
      sample,
      start,
      stride,
      channel,
    )

    if (signature != 0) {
      return signature
    }
  }

  return 0
}

/**
 * Channel:
 * 0 = luminance
 * 1 = red
 * 2 = green
 * 3 = blue
 */
private fun analyzeGradientChannel(
  sample: ByteArray,
  start: Int,
  stride: Int,
  channel: Int,
): Int {
  var minimum = 255
  var maximum = 0
  var flatSteps = 0
  var changingSteps = 0
  var direction = 0
  var directionChanges = 0
  var firstTransition = -1
  var lastTransition = -1

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

    if (magnitude > 8) return 0

    changingSteps++

    if (firstTransition == -1) {
      firstTransition = i
    }

    lastTransition = i

    val currentDirection = if (difference > 0) 1 else -1

    if (direction != 0 && currentDirection != direction) {
      directionChanges++
    }

    direction = currentDirection
    previous = value
  }

  val amplitude = maximum - minimum

  if (
    amplitude !in 4..40 ||
    flatSteps < 5 ||
    changingSteps !in 2..7 ||
    directionChanges > 0
  ) {
    return 0
  }

  val encodedDirection = if (direction > 0) 1 else 2

  return encodedDirection or
    (amplitude shl 2) or
    (flatSteps shl 8) or
    (changingSteps shl 12) or
    (firstTransition shl 16) or
    (lastTransition shl 20)
}

}
