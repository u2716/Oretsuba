
// JNI bridge: Kotlin org.local.oretsuba.Engine <-> C engine API.
// One engine at a time. g_engine_obj is the Engine instance that receives
// dialog / text-input callbacks on the engine thread.
//
// Kotlin side (Script 2) must declare these as member `external fun`s of
// Engine with exactly these names and signatures:
//   nativeCreate(dataDir: String, patchDir: String?, saveDir: String,
//                chinese: Boolean, helper: PlatformHelper): Long
//   nativeCreateError(): String
//   nativeDestroy(handle: Long)
//   nativeFrame(handle: Long, nowMs: Long): Int
//   nativeLastError(handle: Long): String
//   nativeFrameSerial(handle: Long): Long
//   nativeFramebufferSize(handle: Long, out: IntArray): Boolean
//   nativeCopyFramebuffer(handle: Long, dst: java.nio.ByteBuffer): Boolean
//   nativeAudioRate(): Int
//   nativeAudioMix(handle: Long, out: FloatArray, frames: Int)
//   nativeMouseMove(handle: Long, x: Int, y: Int)
//   nativeMouseButton(handle: Long, button: Int, down: Boolean)
//   nativeMouseWheel(handle: Long, delta: Int)
//   nativeKey(handle: Long, vk: Int, down: Boolean)
//   nativeGameCommand(handle: Long, command: Int)
//   nativeSetPaused(handle: Long, paused: Boolean)
//   nativeAnswerDialog(handle: Long, result: Int)
//   nativeAnswerTextInput(handle: Long, ok: Boolean, text: String?)
//   nativeSetUiCallbacks(handle: Long)
#include <jni.h>

#include <cstring>
#include <string>

#include "api/ore_api.h"
#include "api/ore_api_android.h"

namespace {
JavaVM *g_vm = nullptr;
jobject g_engine_obj = nullptr;  // global ref to Engine
std::string g_create_error;

JNIEnv *get_env(bool *did_attach) {
    if (did_attach) *did_attach = false;
    if (!g_vm) return nullptr;
    JNIEnv *env = nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    if (did_attach) *did_attach = true;
    return env;
}

std::string jstring_to_std(JNIEnv *env, jstring s) {
    if (!s) return std::string();
    const char *c = env->GetStringUTFChars(s, nullptr);
    if (!c) return std::string();
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

OreEngine *to_engine(jlong h) { return reinterpret_cast<OreEngine *>(static_cast<intptr_t>(h)); }

void dialog_cb(void * /*ctx*/, const char *text, int kind) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env || !g_engine_obj) return;
    jclass cls = env->GetObjectClass(g_engine_obj);
    if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onDialogRequested", "(Ljava/lang/String;I)V");
    if (mid) {
        jstring jt = env->NewStringUTF(text ? text : "");
        env->CallVoidMethod(g_engine_obj, mid, jt, static_cast<jint>(kind));
        if (jt) env->DeleteLocalRef(jt);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
}

void text_cb(void * /*ctx*/, const char *initial) {
    bool attached = false;
    JNIEnv *env = get_env(&attached);
    if (!env || !g_engine_obj) return;
    jclass cls = env->GetObjectClass(g_engine_obj);
    if (!cls) return;
    jmethodID mid = env->GetMethodID(cls, "onTextInputRequested", "(Ljava/lang/String;)V");
    if (mid) {
        jstring jt = env->NewStringUTF(initial ? initial : "");
        env->CallVoidMethod(g_engine_obj, mid, jt);
        if (jt) env->DeleteLocalRef(jt);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_org_local_oretsuba_Engine_nativeCreate(
    JNIEnv *env, jobject thiz, jstring data_dir, jstring patch_dir, jstring save_dir,
    jboolean chinese, jobject helper) {
    env->GetJavaVM(&g_vm);
    std::string data = jstring_to_std(env, data_dir);
    std::string patch = jstring_to_std(env, patch_dir);
    std::string save = jstring_to_std(env, save_dir);
    OreConfig cfg;
    cfg.data_dir = data.c_str();
    cfg.patch_dir = patch_dir ? patch.c_str() : nullptr;
    cfg.save_dir = save.c_str();
    cfg.chinese = chinese ? 1 : 0;
    char err[512] = {0};
    OreEngine *e = ore_create_android(&cfg, err, sizeof(err), g_vm, helper);
    if (!e) {
        g_create_error = err;
        return 0;
    }
    g_create_error.clear();
    if (g_engine_obj) env->DeleteGlobalRef(g_engine_obj);
    g_engine_obj = env->NewGlobalRef(thiz);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(e));
}

JNIEXPORT jstring JNICALL Java_org_local_oretsuba_Engine_nativeCreateError(JNIEnv *env,
                                                                          jobject /*thiz*/) {
    return env->NewStringUTF(g_create_error.c_str());
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeDestroy(JNIEnv *env, jobject /*thiz*/,
                                                                    jlong handle) {
    if (OreEngine *e = to_engine(handle)) ore_destroy(e);
    if (g_engine_obj) {
        env->DeleteGlobalRef(g_engine_obj);
        g_engine_obj = nullptr;
    }
}

JNIEXPORT jint JNICALL Java_org_local_oretsuba_Engine_nativeFrame(JNIEnv * /*env*/, jobject /*thiz*/,
                                                                   jlong handle, jlong now_ms) {
    OreEngine *e = to_engine(handle);
    if (!e) return -1;
    return static_cast<jint>(ore_frame(e, static_cast<int64_t>(now_ms)));
}

JNIEXPORT jstring JNICALL Java_org_local_oretsuba_Engine_nativeLastError(JNIEnv *env,
                                                                         jobject /*thiz*/,
                                                                         jlong handle) {
    OreEngine *e = to_engine(handle);
    if (!e) return env->NewStringUTF("");
    return env->NewStringUTF(ore_last_error(e));
}

JNIEXPORT jlong JNICALL Java_org_local_oretsuba_Engine_nativeFrameSerial(JNIEnv * /*env*/,
                                                                        jobject /*thiz*/,
                                                                        jlong handle) {
    OreEngine *e = to_engine(handle);
    if (!e) return 0;
    return static_cast<jlong>(ore_frame_serial(e));
}

JNIEXPORT jboolean JNICALL Java_org_local_oretsuba_Engine_nativeFramebufferSize(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jintArray out) {
    OreEngine *e = to_engine(handle);
    if (!e || !out) return JNI_FALSE;
    int w = 0, h = 0;
    ore_framebuffer(e, &w, &h);
    jint v[2] = {w, h};
    env->SetIntArrayRegion(out, 0, 2, v);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_org_local_oretsuba_Engine_nativeCopyFramebuffer(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jobject dst) {
    OreEngine *e = to_engine(handle);
    if (!e || !dst) return JNI_FALSE;
    int w = 0, h = 0;
    const uint8_t *src = ore_framebuffer(e, &w, &h);
    if (!src || w <= 0 || h <= 0) return JNI_FALSE;
    void *d = env->GetDirectBufferAddress(dst);
    jlong cap = env->GetDirectBufferCapacity(dst);
    if (!d || cap < static_cast<jlong>(w) * h * 4) return JNI_FALSE;
    std::memcpy(d, src, static_cast<size_t>(w) * h * 4);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_org_local_oretsuba_Engine_nativeAudioRate(JNIEnv * /*env*/,
                                                                     jobject /*thiz*/) {
    return static_cast<jint>(ore_audio_rate());
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeAudioMix(JNIEnv *env, jobject /*thiz*/,
                                                                      jlong handle, jfloatArray out,
                                                                      jint frames) {
    OreEngine *e = to_engine(handle);
    if (!e || !out || frames <= 0) return;
    jfloat *p = env->GetFloatArrayElements(out, nullptr);
    if (!p) return;
    ore_audio_mix(e, reinterpret_cast<float *>(p), static_cast<int>(frames));
    env->ReleaseFloatArrayElements(out, p, 0);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeMouseMove(JNIEnv * /*env*/,
                                                                      jobject /*thiz*/, jlong handle,
                                                                      jint x, jint y) {
    if (OreEngine *e = to_engine(handle)) ore_mouse_move(e, x, y);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeMouseButton(
    JNIEnv * /*env*/, jobject /*thiz*/, jlong handle, jint button, jboolean down) {
    if (OreEngine *e = to_engine(handle)) ore_mouse_button(e, button, down ? 1 : 0);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeMouseWheel(JNIEnv * /*env*/,
                                                                       jobject /*thiz*/, jlong handle,
                                                                       jint delta) {
    if (OreEngine *e = to_engine(handle)) ore_mouse_wheel(e, delta);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeKey(JNIEnv * /*env*/, jobject /*thiz*/,
                                                                 jlong handle, jint vk,
                                                                 jboolean down) {
    if (OreEngine *e = to_engine(handle)) ore_key(e, vk, down ? 1 : 0);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeGameCommand(JNIEnv * /*env*/,
                                                                         jobject /*thiz*/,
                                                                         jlong handle, jint cmd) {
    if (OreEngine *e = to_engine(handle)) ore_game_command(e, cmd);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeSetPaused(JNIEnv * /*env*/,
                                                                       jobject /*thiz*/, jlong handle,
                                                                       jboolean paused) {
    if (OreEngine *e = to_engine(handle)) ore_set_paused(e, paused ? 1 : 0);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeAnswerDialog(JNIEnv * /*env*/,
                                                                         jobject /*thiz*/,
                                                                         jlong handle, jint result) {
    if (OreEngine *e = to_engine(handle)) ore_answer_dialog(e, result);
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeAnswerTextInput(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jboolean ok, jstring text) {
    OreEngine *e = to_engine(handle);
    if (!e) return;
    std::string s = jstring_to_std(env, text);
    ore_answer_text_input(e, ok ? 1 : 0, s.c_str());
}

JNIEXPORT void JNICALL Java_org_local_oretsuba_Engine_nativeSetUiCallbacks(JNIEnv * /*env*/,
                                                                           jobject /*thiz*/,
                                                                           jlong handle) {
    if (OreEngine *e = to_engine(handle)) ore_set_ui_callbacks(e, nullptr, dialog_cb, text_cb);
}

}  // extern "C"
