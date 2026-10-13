cmake_minimum_required(VERSION 3.20)
project(rikkahub_scyllasband LANGUAGES CXX)

# Use the exact same LiteRT 2.3.0 Maven-native binary and matching headers as
# RikkaHub's CompiledModel bridge: never package an independent duplicate.
get_filename_component(RIKKAHUB_ROOT
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
set(LITERT_SHARED_SDK
    "${RIKKAHUB_ROOT}/app/src/main/jni/litert/litert_cc_sdk")
set(SCYLLASBAND_LITERT_LIBRARY
    "${LITERT_SHARED_SDK}/${ANDROID_ABI}/libLiteRt.so"
    CACHE FILEPATH "Pinned app-shared LiteRT 2.3.0" FORCE)
set(SCYLLASBAND_LITERT_INCLUDE_DIR
    "${LITERT_SHARED_SDK}" CACHE PATH "Pinned LiteRT 2.3.0 headers" FORCE)
if(NOT EXISTS "${SCYLLASBAND_LITERT_LIBRARY}")
    message(FATAL_ERROR "Missing shared LiteRT 2.3.0 binary: ${SCYLLASBAND_LITERT_LIBRARY}")
endif()
set(SCYLLASBAND_BACKEND litert CACHE STRING "" FORCE)
set(SCYLLASBAND_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(SCYLLASBAND_BUILD_TESTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../scyllasband"
    "${CMAKE_CURRENT_BINARY_DIR}/scyllasband-core")
add_library(scyllasband_android_jni SHARED scyllasband_android_jni.cpp)
target_compile_features(scyllasband_android_jni PRIVATE cxx_std_17)
target_link_libraries(scyllasband_android_jni PRIVATE scyllasband::native android log)
