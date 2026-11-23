package app.gyrolet.mpvrx.ui.player

/**
 * Converts banding analysis scores into stable deband profile changes.
 *
 * This class is intentionally independent of libmpv and Android APIs.
 * It can therefore be tested without starting playback.
 */
class AdaptiveDebandController(
  private val confirmationSamples: Int = 3,
  private val hysteresisMargin: Int = 5,
) {

  init {
    require(confirmationSamples >= 1) {
      "confirmationSamples must be at least 1"
    }
    require(hysteresisMargin >= 0) {
      "hysteresisMargin cannot be negative"
    }
  }

  var currentLevel: BandingLevel = BandingLevel.NONE
    private set

  private var pendingLevel: BandingLevel? = null
  private var pendingCount = 0

  /**
   * Submit a new banding score.
   *
   * Returns a profile only when a confirmed level transition occurs.
   * Returns null when the current profile should remain unchanged.
   */
  fun observe(score: Int): AdaptiveDebandProfile? {
    val targetLevel = classifyWithHysteresis(score)

    if (targetLevel == currentLevel) {
      clearPending()
      return null
    }

    if (pendingLevel == targetLevel) {
      pendingCount++
    } else {
      pendingLevel = targetLevel
      pendingCount = 1
    }

    if (pendingCount < confirmationSamples) {
      return null
    }

    currentLevel = targetLevel
    clearPending()

    return AdaptiveDebandProfiles.forLevel(currentLevel)
  }

  /**
   * Reset the controller when a new video starts or playback is reinitialized.
   */
  fun reset() {
    currentLevel = BandingLevel.NONE
    clearPending()
  }

  private fun classifyWithHysteresis(score: Int): BandingLevel {
    val normalizedScore = score.coerceIn(0, 100)

    return BandingLevel.entries.lastOrNull { level ->
      val threshold = if (level.ordinal > currentLevel.ordinal) {
        level.minimumScore + hysteresisMargin
      } else {
        (level.minimumScore - hysteresisMargin).coerceAtLeast(0)
      }

      normalizedScore >= threshold
    } ?: BandingLevel.NONE
  }

  private fun clearPending() {
    pendingLevel = null
    pendingCount = 0
  }
}

