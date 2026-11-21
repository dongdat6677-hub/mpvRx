
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

extern "C"
JNIEXPORT jint JNICALL
Java_app_gyrolet_mpvrx_ui_player_NativeDataspace_setSurfaceDataspace(
    JNIEnv* env,
    jclass,
    jobject surface,
    jint dataspace
) {
    if (surface == nullptr) {
        return -EINVAL;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        return -EINVAL;
    }

    const int result =
        ANativeWindow_setBuffersDataSpace(window, dataspace);

    ANativeWindow_release(window);
    return result;
}
