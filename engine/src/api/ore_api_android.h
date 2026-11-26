
#pragma once
// Android entry point for the C engine API (see engine/src/api/ore_api.h).
// ore_create() alone cannot work on Android because the Platform needs
// a JavaVM + PlatformHelper instance, so JNI calls ore_create_android().
// ore_android_set_vm() is an optional convenience for code that only
// has access to ore_create().
#include "api/ore_api.h"

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

OreEngine *ore_create_android(const OreConfig *config, char *err, int err_len, JavaVM *vm,
                              jobject helper);
void ore_android_set_vm(JavaVM *vm, jobject helper);

#ifdef __cplusplus
}
#endif
