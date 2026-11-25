
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

    assertNull(
      AdaptiveBandingAnalyzer.analyze(
        createRgbSample(8, 16) { _, _ -> Triple(100, 100, 100) },
      ),
    )

    val malformed = createRgbSample(16, 16) { _, _ ->
      Triple(100, 100, 100)
    }.copyOf(20)

    assertNull(AdaptiveBandingAnalyzer.analyze(malformed))
  }

  @Test
  fun `returns zero for a uniform frame`() {
    val sample = createRgbSample(32, 32) { _, _ ->
      Triple(100, 100, 100)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects stepped grayscale gradient`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      val value = 40 + (x / 3) * 2
      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected positive grayscale banding score, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects banding isolated in red channel`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      Triple(40 + (x / 3) * 2, 100, 120)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected red-channel banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects banding isolated in green channel`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      Triple(100, 40 + (x / 3) * 2, 120)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected green-channel banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects banding isolated in blue channel`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      Triple(100, 120, 40 + (x / 3) * 2)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected blue-channel banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `does not classify smooth grayscale gradient as banding`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      val value = 40 + x
      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `does not classify smooth color gradient as banding`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      Triple(30 + x, 60 + x, 90 + x)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `does not classify strong object edge as banding`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      if (x < 16) {
        Triple(30, 40, 50)
      } else {
        Triple(180, 190, 200)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects low contrast object edge isolated to one row`() {
    val sample = createRgbSample(32, 32) { x, y ->
      if (y == 16) {
        val value = 80 + (x / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

@Test
fun `rejects diagonal low contrast object edge`() {
  val sample = createRgbSample(48, 48) { x, y ->
    val distance = x - y

    val value = when {
      distance <= -3 -> 80
      distance >= 3 -> 92
      else -> 80 + (distance + 3) * 2
    }

    Triple(value, value, value)
  }

  assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
}

  @Test
  fun `detects low contrast stepped gradient across parallel rows`() {
    val sample = createRgbSample(32, 32) { x, _ ->
      val value = 80 + (x / 3) * 2
      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected low-contrast banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects localized stepped gradient surrounded by flat regions`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val value = when {
        x < 12 -> 80
        x < 36 -> 80 + ((x - 12) / 3) * 2
        else -> 96
      }

      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected localized banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects vertical stepped gradient`() {
    val sample = createRgbSample(16, 32) { _, y ->
      val value = 40 + (y / 3) * 2
      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected vertical banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects horizontal gradient isolated to one row`() {
    val sample = createRgbSample(32, 32) { x, y ->
      if (y == 16) {
        val value = 40 + (x / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(100, 100, 100)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects vertical gradient isolated to one column`() {
    val sample = createRgbSample(32, 32) { x, y ->
      if (x == 16) {
        val value = 40 + (y / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(100, 100, 100)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  private fun createRgbSample(
    width: Int,
    height: Int,
    pixel: (x: Int, y: Int) -> Triple<Int, Int, Int>,
  ): ByteArray {
    val sample = ByteArray(4 + width * height * 3)

    sample[0] = (width and 0xFF).toByte()
    sample[1] = ((width shr 8) and 0xFF).toByte()
    sample[2] = (height and 0xFF).toByte()
    sample[3] = ((height shr 8) and 0xFF).toByte()

    var offset = 4

    for (y in 0 until height) {
      for (x in 0 until width) {
        val (red, green, blue) = pixel(x, y)

        sample[offset++] = red.coerceIn(0, 255).toByte()
        sample[offset++] = green.coerceIn(0, 255).toByte()
        sample[offset++] = blue.coerceIn(0, 255).toByte()
      }
    }

    return sample
  }
}
