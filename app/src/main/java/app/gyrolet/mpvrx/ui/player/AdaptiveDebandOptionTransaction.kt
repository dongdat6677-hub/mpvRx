
package app.gyrolet.mpvrx.ui.player

internal enum class AdaptiveDebandTransactionResult {
  APPLIED,
  ROLLED_BACK,
  ROLLBACK_FAILED,
  SNAPSHOT_FAILED,
}

/**
 * Applies a group of mpv options and restores their previous values
 * if any write or readback verification fails.
 */
internal object AdaptiveDebandOptionTransaction {

  fun apply(
    updates: List<Pair<String, String>>,
    readOption: (String) -> String?,
    writeOption: (String, String) -> Boolean,
  ): AdaptiveDebandTransactionResult {
    val previousValues = runCatching {
      updates.associate { (name, _) ->
        name to requireNotNull(readOption(name)) {
          "Unable to snapshot mpv option: $name"
        }
      }
    }.getOrNull() ?: return AdaptiveDebandTransactionResult.SNAPSHOT_FAILED

    val applied = runCatching {
      updates.forEach { (name, value) ->
        check(writeOption(name, value)) {
          "Failed to write mpv option: $name=$value"
        }
      }

      check(
        updates.all { (name, value) ->
          readOption(name) == value
        }
      ) {
        "mpv option readback verification failed"
      }
    }.isSuccess

    if (applied) {
      return AdaptiveDebandTransactionResult.APPLIED
    }

    val rollbackSucceeded = runCatching {
      previousValues.forEach { (name, value) ->
        check(writeOption(name, value)) {
          "Failed to restore mpv option: $name=$value"
        }
      }

      check(
        previousValues.all { (name, value) ->
          readOption(name) == value
        }
      ) {
        "mpv option rollback verification failed"
      }
    }.isSuccess

    return if (rollbackSucceeded) {
      AdaptiveDebandTransactionResult.ROLLED_BACK
    } else {
      AdaptiveDebandTransactionResult.ROLLBACK_FAILED
    }
  }
}
