
package app.gyrolet.mpvrx.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDebandProfileTest {

  @Test
  fun `classifies banding scores correctly`() {
    assertEquals(BandingLevel.NONE, BandingLevel.fromScore(0))
    assertEquals(BandingLevel.NONE, BandingLevel.fromScore(14))
    assertEquals(BandingLevel.LIGHT, BandingLevel.fromScore(15))
    assertEquals(BandingLevel.MEDIUM, BandingLevel.fromScore(35))
    assertEquals(BandingLevel.HEAVY, BandingLevel.fromScore(60))
    assertEquals(BandingLevel.VERY_HEAVY, BandingLevel.fromScore(80))
    assertEquals(BandingLevel.VERY_HEAVY, BandingLevel.fromScore(100))
  }

  @Test
  fun `classifies scores immediately below at and above every threshold`() {
    // LIGHT threshold: 15
    assertEquals(BandingLevel.NONE, BandingLevel.fromScore(14))
    assertEquals(BandingLevel.LIGHT, BandingLevel.fromScore(15))
    assertEquals(BandingLevel.LIGHT, BandingLevel.fromScore(16))

    // MEDIUM threshold: 35
    assertEquals(BandingLevel.LIGHT, BandingLevel.fromScore(34))
    assertEquals(BandingLevel.MEDIUM, BandingLevel.fromScore(35))
    assertEquals(BandingLevel.MEDIUM, BandingLevel.fromScore(36))

    // HEAVY threshold: 60
    assertEquals(BandingLevel.MEDIUM, BandingLevel.fromScore(59))
    assertEquals(BandingLevel.HEAVY, BandingLevel.fromScore(60))
    assertEquals(BandingLevel.HEAVY, BandingLevel.fromScore(61))

    // VERY_HEAVY threshold: 80
    assertEquals(BandingLevel.HEAVY, BandingLevel.fromScore(79))
    assertEquals(BandingLevel.VERY_HEAVY, BandingLevel.fromScore(80))
    assertEquals(BandingLevel.VERY_HEAVY, BandingLevel.fromScore(81))
  }

  @Test
  fun `clamps scores outside valid range`() {
    assertEquals(BandingLevel.NONE, BandingLevel.fromScore(-10))
    assertEquals(BandingLevel.VERY_HEAVY, BandingLevel.fromScore(120))
  }

  @Test
  fun `disabled profile is selected for negligible banding`() {
    val profile = AdaptiveDebandProfiles.fromScore(10)

    assertFalse(profile.enabled)
  }

  @Test
  fun `stronger banding selects stronger deband configuration`() {
    val light = AdaptiveDebandProfiles.fromScore(20)
    val heavy = AdaptiveDebandProfiles.fromScore(70)
    val veryHeavy = AdaptiveDebandProfiles.fromScore(90)

    assertTrue(heavy.enabled)
    assertTrue(veryHeavy.enabled)

    assertTrue(heavy.iterations > light.iterations)
    assertTrue(veryHeavy.iterations > heavy.iterations)

    assertTrue(heavy.threshold > light.threshold)
    assertTrue(veryHeavy.threshold > heavy.threshold)
  }
}
