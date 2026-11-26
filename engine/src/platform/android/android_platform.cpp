
#include "platform/android/android_platform.h"

#include <ctime>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace ore {
namespace {

bool face_is_serif(const std::string &face) {
    // Same test as apple_platform.mm: "MS Mincho" / Mingcho / Songti.
    return face.find("\x96\xbe\x92\xa9") != std::string::npos ||  // Mingcho (cp932)
           face.find("Mincho") != std::string::npos || face.find("MINCHO") != std::string::npos ||
           face.find("\xcb\xce\xcc\xe5") != std::string::npos;    // Songti (gbk)
}

void make_dirs(const std::string &path) {
    if (path.empty()) return;
    std::string cur;
    for (size_t i = 0; i < path.size(); ++i) {
        cur += path[i];
        if (path[i] == '/' || i + 1 == path.size()) {
            if (cur.size() > 1) mkdir(cur.c_str(), 0755);
        }
    }
}

int32_t read_le32(const uint8_t *p) {
    return int32_t(p[0]) | (int32_t(p[1]) << 8) | (int32_t(p[2]) << 16) | (int32_t(p[3]) << 24);
}

}  // namespace

AndroidPlatform::AndroidPlatform(std::string save_dir, JavaVM *vm, jobject helper)
    : save_dir_(std::move(save_dir)), vm_(vm) {
    make_dirs(save_dir_);
    if (!vm_ || !helper) return;
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env) return;
    helper_global_ = env->NewGlobalRef(helper);
    if (!helper_global_) return;
    jclass local = env->GetObjectClass(helper_global_);
    if (local) {
        helper_class_global_ = static_cast<jclass>(env->NewGlobalRef(local));
        env->DeleteLocalRef(local);
    }
    ensure_method_ids(env);
}

AndroidPlatform::~AndroidPlatform() {
    if (!vm_) return;
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env) return;
    if (helper_global_) env->DeleteGlobalRef(helper_global_);
    if (helper_class_global_) env->DeleteGlobalRef(helper_class_global_);
}

JNIEnv *AndroidPlatform::env_for_thread(bool *did_attach) {
    if (did_attach) *did_attach = false;
    if (!vm_) return nullptr;
    JNIEnv *env = nullptr;
    if (vm_->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    // Not attached (e.g. engine frame thread). Attach once and keep attached
    // for the life of the process; detaching per frame would be wasteful.
    if (vm_->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    if (did_attach) *did_attach = true;
    return env;
}

bool AndroidPlatform::ensure_method_ids(JNIEnv *env) {
    if (decode_mid_ && to_utf8_mid_ && from_utf8_mid_ && render_mid_) return true;
    if (!helper_class_global_) return false;
    decode_mid_ = env->GetMethodID(helper_class_global_, "decodeChar", "(IZ)I");
    to_utf8_mid_ = env->GetMethodID(helper_class_global_, "toUtf8", "([BZ)[B");
    from_utf8_mid_ = env->GetMethodID(helper_class_global_, "fromUtf8", "(Ljava/lang/String;Z)[B");
    render_mid_ = env->GetMethodID(helper_class_global_, "renderGlyph", "(IZZIZ)[B");
    if (env->ExceptionCheck()) env->ExceptionClear();
    return decode_mid_ && to_utf8_mid_ && from_utf8_mid_ && render_mid_;
}

uint32_t AndroidPlatform::decode_char(uint16_t code, TextEncoding enc) {
    {
        std::lock_guard<std::mutex> lock(mu_);
        auto &cache = char_cache_[enc == TextEncoding::Gbk];
        auto it = cache.find(code);
        if (it != cache.end()) return it->second;
    }
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    uint32_t cp = '?';
    if (env && ensure_method_ids(env)) {
        cp = static_cast<uint32_t>(
            env->CallIntMethod(helper_global_, decode_mid_, static_cast<jint>(code),
                               static_cast<jboolean>(enc == TextEncoding::Gbk)));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cp = '?';
        }
    }
    std::lock_guard<std::mutex> lock(mu_);
    char_cache_[enc == TextEncoding::Gbk][code] = cp;
    return cp;
}

std::string AndroidPlatform::to_utf8(const std::string &text, TextEncoding enc) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env || !ensure_method_ids(env) || text.empty()) return text;
    jbyteArray in = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!in) return text;
    env->SetByteArrayRegion(in, 0, static_cast<jsize>(text.size()),
                            reinterpret_cast<const jbyte *>(text.data()));
    auto out = static_cast<jbyteArray>(
        env->CallObjectMethod(helper_global_, to_utf8_mid_, in,
                              static_cast<jboolean>(enc == TextEncoding::Gbk)));
    env->DeleteLocalRef(in);
    if (env->ExceptionCheck() || !out) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (out) env->DeleteLocalRef(out);
        return text;
    }
    jsize n = env->GetArrayLength(out);
    std::string result(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(out, 0, n, reinterpret_cast<jbyte *>(&result[0]));
    env->DeleteLocalRef(out);
    return result;
}

std::string AndroidPlatform::from_utf8(const std::string &utf8, TextEncoding enc) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env || !ensure_method_ids(env)) return std::string();
    jstring in = env->NewStringUTF(utf8.c_str());
    if (!in) return std::string();
    auto out = static_cast<jbyteArray>(
        env->CallObjectMethod(helper_global_, from_utf8_mid_, in,
                              static_cast<jboolean>(enc == TextEncoding::Gbk)));
    env->DeleteLocalRef(in);
    if (env->ExceptionCheck() || !out) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (out) env->DeleteLocalRef(out);
        return std::string();
    }
    jsize n = env->GetArrayLength(out);
    std::string result(static_cast<size_t>(n), '\0');
    if (n > 0) env->GetByteArrayRegion(out, 0, n, reinterpret_cast<jbyte *>(&result[0]));
    env->DeleteLocalRef(out);
    return result;
}

bool AndroidPlatform::render_glyph(uint32_t codepoint, const std::string &face, int size, int weight,
                                   GlyphBitmap &out) {
    bool attached = false;
    JNIEnv *env = env_for_thread(&attached);
    if (!env || !ensure_method_ids(env)) return false;
    bool serif = face_is_serif(face);
    bool bold = weight >= 600;
    auto raw = static_cast<jbyteArray>(env->CallObjectMethod(
        helper_global_, render_mid_, static_cast<jint>(codepoint), static_cast<jboolean>(serif),
        static_cast<jboolean>(bold), static_cast<jint>(size),
        static_cast<jboolean>(chinese_fonts_)));
    if (env->ExceptionCheck() || !raw) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (raw) env->DeleteLocalRef(raw);
        return false;
    }
    jsize n = env->GetArrayLength(raw);
    if (n < 20) {
        env->DeleteLocalRef(raw);
        return false;
    }
    std::vector<uint8_t> buf(static_cast<size_t>(n));
    env->GetByteArrayRegion(raw, 0, n, reinterpret_cast<jbyte *>(buf.data()));
    env->DeleteLocalRef(raw);
    // Layout from PlatformHelper.renderGlyph: 5 x int32 LE header, then alpha.
    int32_t w = read_le32(&buf[0]);
    int32_t h = read_le32(&buf[4]);
    int32_t left = read_le32(&buf[8]);
    int32_t top = read_le32(&buf[12]);
    int32_t advance = read_le32(&buf[16]);
    if (w < 0 || h < 0 || n != 20 + w * h) return false;
    out.width = w;
    out.height = h;
    out.left = left;
    out.top = top;
    out.advance = advance;
    out.alpha.assign(buf.begin() + 20, buf.end());
    return true;
}

int64_t AndroidPlatform::now_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return int64_t(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000;
}

Platform::LocalTime AndroidPlatform::local_time() {
    time_t t = time(nullptr);
    struct tm lt;
    localtime_r(&t, &lt);
    return LocalTime{lt.tm_year + 1900, lt.tm_mon + 1, lt.tm_mday,
                     lt.tm_hour, lt.tm_min, lt.tm_sec, lt.tm_wday};
}

void AndroidPlatform::begin_dialog(const std::string &utf8_text, int kind) {
    if (!dialog_handler_) {
        dialog_result_ = 1;
        return;
    }
    dialog_result_ = -1;
    dialog_handler_(utf8_text, kind);
}

void AndroidPlatform::begin_text_input(const std::string &utf8_initial) {
    if (!text_handler_) {
        text_result_ = 0;
        return;
    }
    text_result_ = -1;
    text_handler_(utf8_initial);
}

int AndroidPlatform::poll_text_input(std::string &utf8_result) {
    if (text_result_ == 1) utf8_result = text_value_;
    return text_result_;
}

}  // namespace ore
