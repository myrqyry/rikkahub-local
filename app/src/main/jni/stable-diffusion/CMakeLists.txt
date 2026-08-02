cmake_minimum_required(VERSION 3.22)
project(stablediffusion C CXX)

set(SD_VULKAN OFF CACHE BOOL "")
set(SD_BUILD_SHARED_LIBS ON CACHE BOOL "")
set(SD_STATIC OFF CACHE BOOL "")
set(SD_CUDA OFF CACHE BOOL "")
set(SD_METAL OFF CACHE BOOL "")
set(SD_FLASH_ATTN OFF CACHE BOOL "")
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "")

add_subdirectory(../../../../../third_party/stable-diffusion.cpp stable-diffusion-src)

add_library(stablediffusion_jni SHARED bridge.cpp)
target_include_directories(stablediffusion_jni PRIVATE ../../../../../third_party/stable-diffusion.cpp/include)
target_link_libraries(stablediffusion_jni
    stable-diffusion
    log
)
