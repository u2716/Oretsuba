
#pragma once
// Android implementation of ore::Platform (see engine/src/platform/platform.h).
// Font rasterization and Shift-JIS / GBK conversion are delegated to
// Kotlin (PlatformHelper) via JNI, because Android has no CoreText.
// Everything else is POSIX / stdlib, same as the Apple version.
#include "platform/platform.h"

#include <jni.h>

#include <functional>
#include <map>
#include <mutex>
#include <string>

namespace ore {

class AndroidPlatform : public Platform {
public:
    // vm + helper are borrowed from the JNI layer. helper must be an
    // instance of org.local.oretsuba.PlatformHelper. We hold global refs.
    AndroidPlatform(std::string save_dir, JavaVM *vm, jobject helper);
    ~AndroidPlatform() override;

    uint32_t decode_char(uint16_t code, TextEncoding enc) override;
    std::string to_utf8(const std::string &text, TextEncoding enc) override;
    std::string from_utf8(const std::string &utf8, TextEncoding enc) override;
    bool render_glyph(uint32_t codepoint, const std::string &face, int size, int weight,
                      GlyphBitmap &out) override;
    int64_t now_ms() override;
    std::string save_dir() override { return save_dir_; }
    LocalTime local_time() override;

    using DialogHandler = std::function<void(const std::string &utf8, int kind)>;
    using TextInputHandler = std::function<void(const std::string &utf8_initial)>;
    void set_dialog_handler(DialogHandler h) { dialog_handler_ = std::move(h); }
    void set_text_input_handler(TextInputHandler h) { text_handler_ = std::move(h); }
    void answer_dialog(int result) { dialog_result_ = result; }
    void answer_text_input(bool ok, const std::string &utf8) {
        text_result_ = ok ? 1 : 0;
        text_value_ = utf8;
    }
    void begin_dialog(const std::string &utf8_text, int kind) override;
    int poll_dialog() override { return dialog_result_; }
    void begin_text_input(const std::string &utf8_initial) override;
    int poll_text_input(std::string &utf8_result) override;

    void set_chinese_fonts(bool on) { chinese_fonts_ = on; }

private:
    JNIEnv *env_for_thread(bool *did_attach);
    bool ensure_method_ids(JNIEnv *env);

    std::string save_dir_;
    JavaVM *vm_ = nullptr;
    jobject helper_global_ = nullptr;
    jclass helper_class_global_ = nullptr;
    jmethodID decode_mid_ = nullptr;
    jmethodID to_utf8_mid_ = nullptr;
    jmethodID from_utf8_mid_ = nullptr;
    jmethodID render_mid_ = nullptr;

    DialogHandler dialog_handler_;
    TextInputHandler text_handler_;
    int dialog_result_ = 1;
    int text_result_ = 0;
    std::string text_value_;
    bool chinese_fonts_ = false;
    std::mutex mu_;
    std::map<uint32_t, uint32_t> char_cache_[2];
};

}  // namespace ore
