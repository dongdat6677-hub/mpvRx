package app.gyrolet.mpvrx.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveDebandOptionTransactionTest {

  private val updates = listOf(
    "deband" to "yes",
    "deband-iterations" to "1",
    "deband-threshold" to "32",
    "deband-range" to "16",
    "deband-grain" to "16",
  )

  private fun initialOptions() = mutableMapOf(
    "deband" to "no",
    "deband-iterations" to "2",
    "deband-threshold" to "48",
    "deband-range" to "12",
    "deband-grain" to "32",
  )

  @Test
  fun `restores all previous options after partial application failure`() {
    val options = initialOptions()
    var failureTriggered = false

    val result = AdaptiveDebandOptionTransaction.apply(
      updates = updates,
      readOption = { name -> options[name] },
      writeOption = { name, value ->
        if (name == "deband-range" && value == "16" && !failureTriggered) {
          failureTriggered = true
          false
        } else {
          options[name] = value
          true
        }
      },
    )

    assertEquals(
      AdaptiveDebandTransactionResult.ROLLED_BACK,
      result,
    )
    assertEquals(initialOptions(), options)
  }

  @Test
  fun `reports rollback failure when a previous option cannot be restored`() {
    val options = initialOptions()
    var applicationFailureTriggered = false

    val result = AdaptiveDebandOptionTransaction.apply(
      updates = updates,
      readOption = { name -> options[name] },
      writeOption = { name, value ->
        if (
          name == "deband-range" &&
          value == "16" &&
          !applicationFailureTriggered
        ) {
          applicationFailureTriggered = true
          false
        } else if (
          name == "deband" &&
          value == "no"
        ) {
          false
        } else {
          options[name] = value
          true
        }
      },
    )

    assertEquals(
      AdaptiveDebandTransactionResult.ROLLBACK_FAILED,
      result,
    )

    // The failed restoration must not be mistaken for a successful rollback.
    assertEquals("yes", options["deband"])
  }
}

