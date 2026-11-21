
package app.gyrolet.mpvrx.ui.player

import android.view.Surface

internal object NativeDataspace {

  const val DISPLAY_P3_DATASPACE = 143261696

  init {
    System.loadLibrary("mpvrx_dataspace")
  }

  @JvmStatic
  external fun setSurfaceDataspace(
    surface: Surface,
    dataspace: Int,
  ): Int
}
