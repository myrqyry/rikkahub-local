cmake_minimum_required(VERSION 3.22)
project(litert_native_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Bootstrapped by :app:bootstrapLiteRtNativeSdk from the pinned LiteRT Maven AAR
# plus the matching upstream v2.1.5 C header tree.
set(LITERT_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/litert_cc_sdk/${ANDROID_ABI})

add_library(litert_native_bridge SHARED
    bridge.cpp
)

target_include_directories(litert_native_bridge PRIVATE
    litert_cc_sdk
)

target_link_libraries(litert_native_bridge
    ${LITERT_LIB_DIR}/libLiteRt.so
    android
    log
)