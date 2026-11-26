
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
  fun `detects stepped gradient in dark regions`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      val value = 8 + (x / 3) * 2
      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected dark-region banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects low amplitude stepped gradient in dark regions`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      val value = 8 + (x / 3)
      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected low-amplitude dark-region banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects chromatic stepped gradient in dark regions`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      val step = x / 3

      Triple(
        8 + step,
        20 - step,
        12,
      )
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected dark-region chromatic banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects chromatic smooth gradient in dark regions`() {
    val sample = createRgbSample(16, 16) { x, _ ->
      Triple(
        32 - x,
        64 - 2 * x,
        8 + 13 * x,
      )
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects smooth gradient in dark regions`() {
    val sample = createRgbSample(32, 16) { x, _ ->
      val value = 8 + x
      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
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
  fun `rejects small localized stepped texture patch`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 15..16 && x in 12..35) {
        val value = 80 + ((x - 12) / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects checkerboard texture noise`() {
    val sample = createRgbSample(48, 32) { x, y ->
      val value = if ((x + y) % 2 == 0) 88 else 92
      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects deterministic low amplitude random noise`() {
    val sample = createRgbSample(48, 32) { x, y ->
      val noise = ((x * 17 + y * 31 + x * y * 7) % 5) - 2
      val value = 90 + noise

      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects periodic horizontal stripe texture`() {
    val sample = createRgbSample(48, 32) { _, y ->
      val value = when (y % 4) {
        0 -> 88
        1 -> 90
        2 -> 92
        else -> 90
      }

      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects periodic vertical stripe texture`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val value = when (x % 4) {
        0 -> 88
        1 -> 90
        2 -> 92
        else -> 90
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
  fun `detects localized stepped gradient isolated in red channel`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val red = when {
        x < 12 -> 80
        x < 36 -> 80 + ((x - 12) / 3) * 2
        else -> 96
      }

      Triple(red, 100, 120)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected localized red-channel banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects localized stepped gradient isolated in blue channel`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val blue = when {
        x < 12 -> 80
        x < 36 -> 80 + ((x - 12) / 3) * 2
        else -> 96
      }

      Triple(100, 120, blue)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected localized blue-channel banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects localized stepped gradient isolated in green channel`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val green = when {
        x < 12 -> 80
        x < 36 -> 80 + ((x - 12) / 3) * 2
        else -> 96
      }

      Triple(100, green, 120)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected localized green-channel banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects chromatic banding with nearly constant luminance`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val steps = x / 3
      val red = 80 + steps * 4
      val green = 176 - steps * 2

      Triple(red, green, 100)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected chromatic banding to be detected despite nearly constant luminance, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects chromatic banding across parallel rows with matching channels`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val steps = x / 3
      val red = 80 + steps * 4
      val green = 176 - steps * 2

      Triple(red, green, 100)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected matching chromatic gradients across rows to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects vertical chromatic banding with nearly constant luminance`() {
    val sample = createRgbSample(32, 48) { _, y ->
      val steps = y / 3
      val red = 80 + steps * 4
      val green = 176 - steps * 2

      Triple(red, green, 100)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected vertical chromatic banding to be detected despite nearly constant luminance, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects chromatic smooth gradient with nearly constant luminance`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val red = 80 + x * 2
      val green = 176 - x
     
      Triple(red, green, 100)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects localized smooth gradient isolated in green channel`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val green = when {
        x < 12 -> 80
        x < 36 -> 80 + (x - 12)
        else -> 104
      }

      Triple(100, green, 120)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects localized vertical stepped gradient surrounded by flat regions`() {
    val sample = createRgbSample(32, 48) { _, y ->
      val value = when {
        y < 12 -> 80
        y < 36 -> 80 + ((y - 12) / 3) * 2
        else -> 96
      }

      Triple(value, value, value)
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected localized vertical banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects localized smooth gradient surrounded by flat regions`() {
    val sample = createRgbSample(48, 32) { x, _ ->
      val value = when {
        x < 12 -> 80
        x < 36 -> 80 + (x - 12)
        else -> 104
      }

      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects localized vertical smooth gradient surrounded by flat regions`() {
    val sample = createRgbSample(32, 48) { _, y ->
      val value = when {
        y < 12 -> 80
        y < 36 -> 80 + (y - 12)
        else -> 104
      }

      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
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

  @Test
  fun `rejects alternating gradient directions across adjacent rows`() {
    val sample = createRgbSample(48, 32) { x, y ->
      val position = if (y % 2 == 0) x else 47 - x
      val value = 40 + (position / 3) * 2
      Triple(value, value, value)
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects stepped gradient in a narrow three-row region`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..16) {
        val value = 40 + (x / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected narrow-region banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects localized stepped gradient in a narrow four-row region`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..17 && x in 12..35) {
        val value = 40 + ((x - 12) / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected localized narrow-region banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects localized smooth gradient in a narrow four-row region`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..17 && x in 12..35) {
        val value = 40 + (x - 12)
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects localized stepped gradient touching the left frame edge`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..17 && x in 0..23) {
        val value = 40 + (x / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected left-edge stepped gradient to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects narrow three-row smooth gradient`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..16) {
        val value = 40 + x
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects stepped gradient in a narrow four-row region`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..17) {
        val value = 40 + (x / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected four-row banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects narrow four-row smooth gradient`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 14..17) {
        val value = 40 + x
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects narrow two-row horizontal smooth gradient`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y in 15..16) {
        val value = 40 + x
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `detects vertical stepped gradient in a narrow three-column region`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x in 14..16) {
        val value = 40 + (y / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected narrow vertical banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `detects vertical stepped gradient in a narrow four-column region`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x in 14..17) {
        val value = 40 + (y / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    val score = AdaptiveBandingAnalyzer.analyze(sample)

    assertTrue(
      "Expected four-column vertical banding to be detected, got $score",
      score != null && score > 0,
    )
  }

  @Test
  fun `rejects narrow four-column vertical smooth gradient`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x in 14..17) {
        val value = 40 + y
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects narrow three-column vertical smooth gradient`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x in 14..16) {
        val value = 40 + y
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects narrow two-column vertical smooth gradient`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x in 15..16) {
        val value = 40 + y
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects narrow two-column vertical stepped texture`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x in 15..16) {
        val value = 40 + (y / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects one-column vertical stepped texture`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x == 15) {
        val value = 40 + (y / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects one-column vertical smooth gradient`() {
    val sample = createRgbSample(32, 48) { x, y ->
      if (x == 15) {
        val value = 40 + y
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects one-row horizontal stepped texture`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y == 15) {
        val value = 40 + (x / 3) * 2
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
      }
    }

    assertEquals(0, AdaptiveBandingAnalyzer.analyze(sample))
  }

  @Test
  fun `rejects one-row horizontal smooth gradient`() {
    val sample = createRgbSample(48, 32) { x, y ->
      if (y == 15) {
        val value = 40 + x
        Triple(value, value, value)
      } else {
        Triple(90, 90, 90)
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
