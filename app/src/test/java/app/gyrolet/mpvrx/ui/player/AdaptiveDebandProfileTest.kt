
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
  
  @Test
  fun `uses optimized deband parameters for each profile`() {
    assertEquals(
      AdaptiveDebandProfile(false, 1, 48, 16, 32),
      AdaptiveDebandProfiles.NONE,
    )

    assertEquals(
      AdaptiveDebandProfile(true, 1, 32, 16, 16),
      AdaptiveDebandProfiles.LIGHT,
    )

    assertEquals(
      AdaptiveDebandProfile(true, 1, 48, 16, 24),
      AdaptiveDebandProfiles.MEDIUM,
    )

    assertEquals(
      AdaptiveDebandProfile(true, 2, 64, 12, 32),
      AdaptiveDebandProfiles.HEAVY,
    )

    assertEquals(
      AdaptiveDebandProfile(true, 3, 80, 8, 40),
      AdaptiveDebandProfiles.VERY_HEAVY,
    )
  }
  
  @Test
  fun `selects correct profile at every score threshold`() {
    // NONE: score 0–14
    assertEquals(AdaptiveDebandProfiles.NONE, AdaptiveDebandProfiles.fromScore(0))
    assertEquals(AdaptiveDebandProfiles.NONE, AdaptiveDebandProfiles.fromScore(14))

    // LIGHT: score 15–34
    assertEquals(AdaptiveDebandProfiles.LIGHT, AdaptiveDebandProfiles.fromScore(15))
    assertEquals(AdaptiveDebandProfiles.LIGHT, AdaptiveDebandProfiles.fromScore(34))

    // MEDIUM: score 35–59
    assertEquals(AdaptiveDebandProfiles.MEDIUM, AdaptiveDebandProfiles.fromScore(35))
    assertEquals(AdaptiveDebandProfiles.MEDIUM, AdaptiveDebandProfiles.fromScore(59))

    // HEAVY: score 60–79
    assertEquals(AdaptiveDebandProfiles.HEAVY, AdaptiveDebandProfiles.fromScore(60))
    assertEquals(AdaptiveDebandProfiles.HEAVY, AdaptiveDebandProfiles.fromScore(79))

    // VERY_HEAVY: score 80–100
    assertEquals(AdaptiveDebandProfiles.VERY_HEAVY, AdaptiveDebandProfiles.fromScore(80))
    assertEquals(AdaptiveDebandProfiles.VERY_HEAVY, AdaptiveDebandProfiles.fromScore(100))
  }
  
  @Test
  fun `maintains progressive deband profile strength`() {
    val light = AdaptiveDebandProfiles.LIGHT
    val medium = AdaptiveDebandProfiles.MEDIUM
    val heavy = AdaptiveDebandProfiles.HEAVY
    val veryHeavy = AdaptiveDebandProfiles.VERY_HEAVY

    // Deband remains enabled for every non-NONE profile.
    assertTrue(light.enabled)
    assertTrue(medium.enabled)
    assertTrue(heavy.enabled)
    assertTrue(veryHeavy.enabled)

    // Threshold increases with each severity level.
    assertTrue(light.threshold < medium.threshold)
    assertTrue(medium.threshold < heavy.threshold)
    assertTrue(heavy.threshold < veryHeavy.threshold)

    // Iterations increase for stronger banding.
    assertTrue(light.iterations <= medium.iterations)
    assertTrue(medium.iterations < heavy.iterations)
    assertTrue(heavy.iterations < veryHeavy.iterations)

    // Grain increases progressively without excessive jumps.
    assertTrue(light.grain < medium.grain)
    assertTrue(medium.grain < heavy.grain)
    assertTrue(heavy.grain < veryHeavy.grain)

    // Range is reduced as iteration count increases.
    assertEquals(light.range, medium.range)
    assertTrue(medium.range > heavy.range)
    assertTrue(heavy.range > veryHeavy.range)
  }
}
