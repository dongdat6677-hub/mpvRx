package app.gyrolet.mpvrx.ui.player

import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal class GpuPerformanceController(context: Context) {

  private val preferences =
    context.applicationContext.getSharedPreferences(
      PREFS_NAME,
      Context.MODE_PRIVATE,
    )

  private val lock = Any()

  suspend fun enableMaxPerformance(): Boolean =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        runCatching {
          val saved = preferences.getBoolean(KEY_ACTIVE, false)
          val savedPid = preferences.getInt(KEY_PID, -1)

          // Restore a stale session left by a previous app process.
          if (saved && savedPid != Process.myPid()) {
            if (!restoreLocked()) return@synchronized false
          }

          if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            val result = runRoot(
              """
              cat $MIN_PATH
              cat $MAX_PATH
              """.trimIndent(),
            )

            if (result.first != 0) {
              Log.e(TAG, "Cannot read original GPU limits: ${result.second}")
              return@synchronized false
            }

            val values = result.second.lines()
              .map { it.trim() }
              .filter { it.matches(Regex("[0-9]+")) }

            if (values.size < 2) {
              Log.e(TAG, "Invalid original GPU frequency values")
              return@synchronized false
            }

            val originalMin = values[0].toLongOrNull()
              ?: return@synchronized false
            val originalMax = values[1].toLongOrNull()
              ?: return@synchronized false

            if (originalMin <= 0L || originalMax < originalMin) {
              Log.e(TAG, "Invalid original GPU limits")
              return@synchronized false
            }

            // Persist before changing kernel values.
            val savedSuccessfully = preferences.edit()
              .putLong(KEY_ORIGINAL_MIN, originalMin)
              .putLong(KEY_ORIGINAL_MAX, originalMax)
              .putInt(KEY_PID, Process.myPid())
              .putBoolean(KEY_ACTIVE, true)
              .commit()

            if (!savedSuccessfully) {
              Log.e(TAG, "Cannot persist original GPU limits")
              return@synchronized false
            }
          }

          val result = runRoot(
            """
            printf '%s\n' $TARGET_FREQ > $MAX_PATH &&
            printf '%s\n' $TARGET_FREQ > $MIN_PATH &&
            cat $MIN_PATH &&
            cat $MAX_PATH
            """.trimIndent(),
          )

          val values = result.second.lines()
            .map { it.trim() }
            .filter { it.matches(Regex("[0-9]+")) }

          val success =
            result.first == 0 &&
              values.size >= 2 &&
              values[0] == TARGET_FREQ.toString() &&
              values[1] == TARGET_FREQ.toString()

          if (!success) {
            Log.e(TAG, "GPU limit request failed: ${result.second}")
            restoreLocked()
            return@synchronized false
          }

          Log.i(TAG, "GPU frequency limits requested: $TARGET_FREQ")
          true
        }.getOrElse {
          Log.e(TAG, "GPU performance activation failed", it)
          false
        }
      }
    }

  suspend fun restoreOriginalLimits(): Boolean =
    withContext(Dispatchers.IO) {
      synchronized(lock) {
        runCatching { restoreLocked() }
          .getOrElse {
            Log.e(TAG, "GPU restoration failed", it)
            false
          }
      }
    }

  private fun restoreLocked(): Boolean {
    if (!preferences.getBoolean(KEY_ACTIVE, false)) return true

    val originalMin = preferences.getLong(KEY_ORIGINAL_MIN, -1L)
    val originalMax = preferences.getLong(KEY_ORIGINAL_MAX, -1L)

    if (originalMin <= 0L || originalMax < originalMin) {
      Log.e(TAG, "Saved GPU limits are invalid; refusing restoration")
      return false
    }

    val result = runRoot(
      """
      printf '%s\n' $originalMin > $MIN_PATH &&
      printf '%s\n' $originalMax > $MAX_PATH &&
      cat $MIN_PATH &&
      cat $MAX_PATH
      """.trimIndent(),
    )

    val values = result.second.lines()
      .map { it.trim() }
      .filter { it.matches(Regex("[0-9]+")) }

    val success =
      result.first == 0 &&
        values.size >= 2 &&
        values[0] == originalMin.toString() &&
        values[1] == originalMax.toString()

    if (!success) {
      Log.e(TAG, "GPU limits could not be restored: ${result.second}")
      return false
    }

    preferences.edit().clear().commit()
    Log.i(TAG, "Original GPU limits restored")
    return true
  }

  private fun runRoot(script: String): Pair<Int, String> {
    val process = ProcessBuilder("su", "-c", script)
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream.bufferedReader().use { it.readText() }

    if (!process.waitFor(15, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      return -1 to "Root command timed out"
    }

    return process.exitValue() to output
  }

  private companion object {
    const val TAG = "GpuPerformanceController"
    const val PREFS_NAME = "gpu_performance_controller"

    const val KEY_ACTIVE = "active"
    const val KEY_PID = "pid"
    const val KEY_ORIGINAL_MIN = "original_min"
    const val KEY_ORIGINAL_MAX = "original_max"

    const val TARGET_FREQ = 1260000000L

    const val MIN_PATH =
      "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"

    const val MAX_PATH =
      "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
  }
}

