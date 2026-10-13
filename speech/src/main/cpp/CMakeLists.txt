cmake_minimum_required(VERSION 3.20)
project(rikkahub_scyllasband LANGUAGES CXX)

# This is the separate LiteRT 2.2.0 library, with a distinct ELF SONAME.
# Do not use RikkaHub's existing libLiteRt.so (2.1.5): Scylla warmup fails.
if(NOT SCYLLASBAND_STAGE_ROOT)
    message(FATAL_ERROR "SCYLLASBAND_STAGE_ROOT must be supplied by :speech")
endif()
set(SCYLLASBAND_LITERT_LIBRARY
    "${SCYLLASBAND_STAGE_ROOT}/${ANDROID_ABI}/libScyllaLiteRt.so"
    CACHE FILEPATH "Scylla-isolated LiteRT" FORCE)
if(NOT EXISTS "${SCYLLASBAND_LITERT_LIBRARY}")
    message(FATAL_ERROR "Missing isolated Scylla LiteRT: ${SCYLLASBAND_LITERT_LIBRARY}")
endif()
set(SCYLLASBAND_BACKEND litert CACHE STRING "" FORCE)
set(SCYLLASBAND_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(SCYLLASBAND_BUILD_TESTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../scyllasband"
    "${CMAKE_CURRENT_BINARY_DIR}/scyllasband-core")
add_library(scyllasband_android_jni SHARED scyllasband_android_jni.cpp)
target_compile_features(scyllasband_android_jni PRIVATE cxx_std_17)
target_link_libraries(scyllasband_android_jni PRIVATE scyllasband::native android log)
