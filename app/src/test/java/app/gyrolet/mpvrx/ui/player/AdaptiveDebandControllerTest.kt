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

    repeat(3) {
      assertNull(controller.observe(63))
    }

    assertEquals(BandingLevel.NONE, controller.currentLevel)

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
}

