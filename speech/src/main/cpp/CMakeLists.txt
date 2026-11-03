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

# Pin the ASR engine independently from its downloadable model weights.
# The third_party/transcribe.cpp Git submodule supplies the matching ggml ABI.
set(TRANSCRIBE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_SYSTEM_BLAS OFF CACHE BOOL "" FORCE)
# Link statically into this JNI .so: the app already includes a separate ggml ABI
# from another dependency. Bundling a second libggml*.so is unsafe.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(TRANSCRIBE_BUILD_SHARED OFF CACHE BOOL "" FORCE)
# Upstream uses CMAKE_SOURCE_DIR/include when embedded as a subproject.
include_directories("${RIKKAHUB_ROOT}/third_party/transcribe.cpp/include")
add_subdirectory("${RIKKAHUB_ROOT}/third_party/transcribe.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/transcribe-core")
# Android Gradle Debug defaults to no native optimization; without this the
# 10-second Pixel speech sample took over 90 seconds through the JNI bridge.
# Optimize only this inference engine; preserve debugger settings elsewhere.
foreach(_asr_target transcribe ggml ggml-base ggml-cpu)
    if(TARGET ${_asr_target})
        target_compile_options(${_asr_target} PRIVATE "$<$<CONFIG:Debug>:-O2>")
    endif()
endforeach()
add_library(nemotron_asr_android_jni SHARED nemotron_asr_android_jni.cpp)
target_compile_features(nemotron_asr_android_jni PRIVATE cxx_std_17)
target_include_directories(nemotron_asr_android_jni PRIVATE
    "${RIKKAHUB_ROOT}/third_party/transcribe.cpp/include")
target_link_libraries(nemotron_asr_android_jni PRIVATE transcribe log)
target_link_options(nemotron_asr_android_jni PRIVATE "-Wl,--exclude-libs,ALL")
