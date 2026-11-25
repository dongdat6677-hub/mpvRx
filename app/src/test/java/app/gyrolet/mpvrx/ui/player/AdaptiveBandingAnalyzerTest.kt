
package app.gyrolet.mpvrx.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBandingAnalyzerTest {

  @Test
  fun `returns null for invalid samples`() {
    assertNull(AdaptiveBandingAnalyzer.analyze(null))
    assertNull(AdaptiveBandingAnalyzer.analyze(byteArrayOf(1, 2, 3)))

    // Width is below the supported minimum.
    assertNull(
      AdaptiveBandingAnalyzer.analyze(
        createSample(8, 16) { _, _ -> 100 },
      ),
    )

    // Header declares a larger payload than the actual data.
    val malformed = createSample(16, 16) { _, _ -> 100 }
      .copyOf(20)

    assertNull(AdaptiveBandingAnalyzer.analyze(malformed))
  }

  @Test
  fun `returns zero for a uniform frame`() {
    val sample = createSample(32, 32) { _, _ -> 100 }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects stepped gradient banding`() {
    val sample = createSample(32, 16) { x, _ ->
      40 + (x / 3) * 2
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected a positive banding score, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `does not classify a smooth gradient as banding`() {
    val sample = createSample(32, 16) { x, _ ->
      40 + x
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `does not classify a strong object edge as banding`() {
    val sample = createSample(32, 16) { x, _ ->
      if (x < 16) 30 else 180
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  private fun createSample(
    width: Int,
    height: Int,
    pixel: (x: Int, y: Int) -> Int,
  ): ByteArray {
    val sample = ByteArray(4 + width * height * 3)

    // Width: unsigned 16-bit little-endian.
    sample[0] = (width and 0xFF).toByte()
    sample[1] = ((width shr 8) and 0xFF).toByte()

    // Height: unsigned 16-bit little-endian.
    sample[2] = (height and 0xFF).toByte()
    sample[3] = ((height shr 8) and 0xFF).toByte()

    var offset = 4

    for (y in 0 until height) {
      for (x in 0 until width) {
        val value = pixel(x, y).coerceIn(0, 255).toByte()

        // RGB24: red, green, blue.
        sample[offset++] = value
        sample[offset++] = value
        sample[offset++] = value
      }
    }

    return sample
  }
}
