
# NDK build for the Android port. Compiles the same engine/src sources
# as the Apple build, minus the .mm files (we only glob .cpp/.c, so
# apple_platform.mm / ore_api.mm are automatically excluded).
cmake_minimum_required(VERSION 3.22.1)
project(oretsuba)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)

# android/app/src/main/cpp -> repo root is 5 levels up.
set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../engine)

file(GLOB_RECURSE ENGINE_SRCS
    ${ENGINE_DIR}/src/*.cpp
    ${ENGINE_DIR}/src/*.c
)

add_library(oretsuba SHARED
    jni_bridge.cpp
    ${ENGINE_SRCS}
)

target_include_directories(oretsuba PRIVATE
    ${ENGINE_DIR}/src
    ${ENGINE_DIR}/third_party
)

target_compile_options(oretsuba PRIVATE -O2 -w)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)
find_library(GLES_LIB GLESv2)

target_link_libraries(oretsuba
    ${LOG_LIB}
    ${ANDROID_LIB}
    ${GLES_LIB}
)
