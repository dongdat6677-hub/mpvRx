package app.gyrolet.mpvrx.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDebandControllerTest {

  @Test
  fun `requires consecutive samples before changing profile`() {
    val controller = AdaptiveDebandController()

    assertNull(controller.observe(70))
    assertNull(controller.observe(70))

    val profile = controller.observe(70)

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)
    assertTrue(profile!!.enabled)
    assertEquals(2, profile.iterations)
  }

  @Test
  fun `does not change profile when samples are inconsistent`() {
    val controller = AdaptiveDebandController()

    assertNull(controller.observe(70))
    assertNull(controller.observe(20))
    assertNull(controller.observe(70))
    assertNull(controller.observe(20))

    assertEquals(BandingLevel.NONE, controller.currentLevel)
  }

  @Test
  fun `hysteresis prevents upward transition near threshold`() {
    val controller = AdaptiveDebandController()

    // Score 63 should reach MEDIUM, but not HEAVY.
    repeat(3) {
      controller.observe(63)
    }

    assertEquals(BandingLevel.MEDIUM, controller.currentLevel)

    // Score 64 is below the HEAVY upward threshold of 65.
    repeat(3) {
      assertNull(controller.observe(64))
    }

    assertEquals(BandingLevel.MEDIUM, controller.currentLevel)

    // Score 65 reaches the HEAVY upward threshold.
    repeat(3) {
      controller.observe(65)
    }

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)
  }

  @Test
  fun `hysteresis prevents downward transition near threshold`() {
    val controller = AdaptiveDebandController()

    repeat(3) {
      controller.observe(70)
    }

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)

    repeat(3) {
      assertNull(controller.observe(57))
    }

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)

    repeat(3) {
      controller.observe(54)
    }

    assertEquals(BandingLevel.MEDIUM, controller.currentLevel)
  }

  @Test
  fun `returns null when profile does not change`() {
    val controller = AdaptiveDebandController()

    repeat(5) {
      assertNull(controller.observe(0))
    }

    assertEquals(BandingLevel.NONE, controller.currentLevel)
  }

  @Test
  fun `clamps out of range scores`() {
    val controller = AdaptiveDebandController()

    repeat(3) {
      controller.observe(150)
    }

    assertEquals(BandingLevel.VERY_HEAVY, controller.currentLevel)

    controller.reset()

    repeat(3) {
      controller.observe(-50)
    }

    assertEquals(BandingLevel.NONE, controller.currentLevel)
  }

  @Test
  fun `reset clears current and pending states`() {
    val controller = AdaptiveDebandController()

    controller.observe(70)
    controller.observe(70)

    controller.reset()

    assertEquals(BandingLevel.NONE, controller.currentLevel)
    assertNull(controller.observe(70))
    assertNull(controller.observe(70))
  }

  @Test
  fun `strong score transition can skip intermediate levels`() {
    val controller = AdaptiveDebandController()

    repeat(3) {
      controller.observe(95)
    }

    assertEquals(BandingLevel.VERY_HEAVY, controller.currentLevel)
  }

  @Test
  fun `profile remains disabled at none level`() {
    val controller = AdaptiveDebandController()

    assertFalse(AdaptiveDebandProfiles.forLevel(controller.currentLevel).enabled)
  }
  
  @Test
  fun `resets confirmation count when pending target level changes`() {
    val controller = AdaptiveDebandController()

    // Two samples targeting HEAVY.
    assertNull(controller.observe(70))
    assertNull(controller.observe(70))

    // This sample targets LIGHT and must reset the HEAVY confirmation count.
    assertNull(controller.observe(20))

    // Two new HEAVY samples must not be enough.
    assertNull(controller.observe(70))
    assertNull(controller.observe(70))

    assertEquals(BandingLevel.NONE, controller.currentLevel)

    // The third consecutive HEAVY sample confirms the transition.
    val profile = controller.observe(70)

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)
    assertEquals(2, profile!!.iterations)
  }
  
  @Test
  fun `clears pending transition when score returns to current level`() {
    val controller = AdaptiveDebandController()

    // Two samples begin a pending transition to HEAVY.
    assertNull(controller.observe(70))
    assertNull(controller.observe(70))

    // Returning to NONE, the current level, must clear the pending state.
    assertNull(controller.observe(0))

    // Two new HEAVY samples must not confirm the transition.
    assertNull(controller.observe(70))
    assertNull(controller.observe(70))

    assertEquals(BandingLevel.NONE, controller.currentLevel)

    // The third consecutive HEAVY sample confirms the transition.
    val profile = controller.observe(70)

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)
    assertEquals(2, profile!!.iterations)
  }
  
  @Test
  fun `reset after active profile restores none and clears transition state`() {
    val controller = AdaptiveDebandController()

    // Confirm transition to HEAVY.
    repeat(3) {
      controller.observe(70)
    }

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)

    // Reset must restore NONE.
    controller.reset()

    assertEquals(BandingLevel.NONE, controller.currentLevel)

    // Two HEAVY samples after reset must not be enough.
    assertNull(controller.observe(70))
    assertNull(controller.observe(70))

    assertEquals(BandingLevel.NONE, controller.currentLevel)

    // The third consecutive sample confirms HEAVY.
    val profile = controller.observe(70)

    assertEquals(BandingLevel.HEAVY, controller.currentLevel)
    assertEquals(2, profile!!.iterations)
  }
}

