
package app.gyrolet.mpvrx.ui.player

/**
 * Estimated banding severity, represented by a normalized score from 0 to 100.
 */
enum class BandingLevel(
  val minimumScore: Int,
) {
  NONE(0),
  LIGHT(15),
  MEDIUM(35),
  HEAVY(60),
  VERY_HEAVY(80);

  companion object {
    fun fromScore(score: Int): BandingLevel {
      val normalizedScore = score.coerceIn(0, 100)
      return entries.last { normalizedScore >= it.minimumScore }
    }
  }
}

/**
 * Runtime deband configuration selected for a detected banding level.
 */
data class AdaptiveDebandProfile(
  val enabled: Boolean,
  val iterations: Int,
  val threshold: Int,
  val range: Int,
  val grain: Int,
)

/**
 * Maps detected banding severity to an mpv deband configuration.
 *
 * These are initial tuning profiles and will be validated on the target device.
 */
object AdaptiveDebandProfiles {

  val NONE = AdaptiveDebandProfile(
    enabled = false,
    iterations = 1,
    threshold = 48,
    range = 16,
    grain = 32,
  )

  val LIGHT = AdaptiveDebandProfile(
    enabled = true,
    iterations = 1,
    threshold = 32,
    range = 16,
    grain = 16,
  )

  val MEDIUM = AdaptiveDebandProfile(
    enabled = true,
    iterations = 1,
    threshold = 48,
    range = 16,
    grain = 32,
  )

  val HEAVY = AdaptiveDebandProfile(
    enabled = true,
    iterations = 2,
    threshold = 64,
    range = 16,
    grain = 48,
  )

  val VERY_HEAVY = AdaptiveDebandProfile(
    enabled = true,
    iterations = 3,
    threshold = 80,
    range = 16,
    grain = 64,
  )

  fun forLevel(level: BandingLevel): AdaptiveDebandProfile =
    when (level) {
      BandingLevel.NONE -> NONE
      BandingLevel.LIGHT -> LIGHT
      BandingLevel.MEDIUM -> MEDIUM
      BandingLevel.HEAVY -> HEAVY
      BandingLevel.VERY_HEAVY -> VERY_HEAVY
    }

  fun fromScore(score: Int): AdaptiveDebandProfile =
    forLevel(BandingLevel.fromScore(score))
}
