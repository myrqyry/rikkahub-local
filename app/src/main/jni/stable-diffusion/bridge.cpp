#include <jni.h>
#include <android/log.h>
#include <stable-diffusion.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "SD-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef RIKKAHUB_SD_VULKAN
#define RIKKAHUB_SD_VULKAN 0
#endif

namespace {
constexpr jint BACKEND_CPU = 0;
constexpr jint BACKEND_VULKAN = 1;
constexpr jint MIN_IMAGE_DIMENSION = 64;
constexpr jint MAX_IMAGE_DIMENSION = 2048;
constexpr jint MAX_STEPS = 200;
constexpr float MAX_CFG = 50.0f;

long long trace_ms() {
    static const auto start = std::chrono::steady_clock::now();
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - start
    ).count();
}

// stable-diffusion.cpp contexts are process-global in this bridge. Generation, init, and release
// are serialized because sd_ctx_t is not safe to mutate concurrently. Cancellation is deliberately
// protected by a separate context mutex so it can reach sd_cancel_generation() while generation is
// blocked inside native code.
std::mutex g_generation_mutex;
std::mutex g_context_mutex;
sd_ctx_t* g_ctx = nullptr;

// JNI relay targets for the global sd_progress_cb_t callback. Set once from nativeInit after the
// first successful context load; the callback itself fires on the generating thread, so the
// (jclass, jmethodID) pair is published before any generation can start.
JavaVM* g_vm = nullptr;
jclass g_bridge_class = nullptr;
jmethodID g_on_progress = nullptr;

const char* sd_log_level_name(sd_log_level_t level) {
    switch (level) {
        case SD_LOG_DEBUG: return "DEBUG";
        case SD_LOG_INFO: return "INFO";
        case SD_LOG_WARN: return "WARN";
        case SD_LOG_ERROR: return "ERROR";
        default: return "LOG";
    }
}

void android_sd_log(sd_log_level_t level, const char* text, void*) {
    __android_log_print(
        level == SD_LOG_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO,
        LOG_TAG,
        "sd.cpp[%s] %s",
        sd_log_level_name(level),
        text ? text : ""
    );
}

static constexpr const char* BRIDGE_CLASS = "me/rerere/rikkahub/data/ai/StableDiffusionBridge";
static constexpr const char* ON_PROGRESS_SIGNATURE = "(IIF)V";

bool backend_supported(jint backend) {
    switch (backend) {
        case BACKEND_CPU:
            return true;
        case BACKEND_VULKAN:
            return RIKKAHUB_SD_VULKAN != 0;
        default:
            return false;
    }
}

const char* backend_name(jint backend) {
    switch (backend) {
        case BACKEND_CPU:
            return "cpu";
        case BACKEND_VULKAN:
            return "vulkan";
        default:
            return nullptr;
    }
}

bool copy_jstring(JNIEnv* env, jstring value, std::string& out) {
    if (value == nullptr) {
        out.clear();
        return true;
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return false;
    }
    out.assign(chars);
    env->ReleaseStringUTFChars(value, chars);
    return true;
}

bool valid_generation_request(jint width, jint height, jint steps, jfloat cfg) {
    if (width < MIN_IMAGE_DIMENSION || width > MAX_IMAGE_DIMENSION ||
        height < MIN_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
        LOGE("invalid dimensions %dx%d (allowed %d..%d)",
             width, height, MIN_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION);
        return false;
    }
    if ((width % 8) != 0 || (height % 8) != 0) {
        LOGE("dimensions must be multiples of 8: %dx%d", width, height);
        return false;
    }
    if (steps < 1 || steps > MAX_STEPS) {
        LOGE("invalid step count %d (allowed 1..%d)", steps, MAX_STEPS);
        return false;
    }
    if (!std::isfinite(cfg) || cfg < 0.0f || cfg > MAX_CFG) {
        LOGE("invalid cfg scale %.3f (allowed 0..%.1f)", cfg, MAX_CFG);
        return false;
    }
    return true;
}

void release_context_locked() {
    std::lock_guard<std::mutex> context_lock(g_context_mutex);
    if (g_ctx != nullptr) {
        free_sd_ctx(g_ctx);
        g_ctx = nullptr;
    }
}

jbyteArray image_to_rgba(JNIEnv* env, const sd_image_t& image, jint requested_width, jint requested_height) {
    if (image.data == nullptr || image.width == 0 || image.height == 0) {
        LOGE("generated image has no data");
        return nullptr;
    }
    if (image.width != static_cast<uint32_t>(requested_width) ||
        image.height != static_cast<uint32_t>(requested_height)) {
        LOGE("generated image dimensions changed unexpectedly: got %ux%u requested %dx%d",
             image.width, image.height, requested_width, requested_height);
        return nullptr;
    }
    if (image.channel != 1 && image.channel != 3 && image.channel != 4) {
        LOGE("unsupported generated image channel count: %u", image.channel);
        return nullptr;
    }

    const size_t pixel_count = static_cast<size_t>(image.width) * static_cast<size_t>(image.height);
    if (pixel_count > static_cast<size_t>(std::numeric_limits<jsize>::max()) / 4u) {
        LOGE("generated image is too large for a Java byte array");
        return nullptr;
    }
    const size_t rgba_size = pixel_count * 4u;
    std::vector<uint8_t> rgba(rgba_size);

    if (image.channel == 4) {
        std::memcpy(rgba.data(), image.data, rgba_size);
    } else if (image.channel == 3) {
        for (size_t i = 0; i < pixel_count; ++i) {
            const size_t src = i * 3u;
            const size_t dst = i * 4u;
            rgba[dst] = image.data[src];
            rgba[dst + 1] = image.data[src + 1];
            rgba[dst + 2] = image.data[src + 2];
            rgba[dst + 3] = 0xff;
        }
    } else {
        for (size_t i = 0; i < pixel_count; ++i) {
            const uint8_t gray = image.data[i];
            const size_t dst = i * 4u;
            rgba[dst] = gray;
            rgba[dst + 1] = gray;
            rgba[dst + 2] = gray;
            rgba[dst + 3] = 0xff;
        }
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(rgba_size));
    if (result == nullptr) {
        LOGE("NewByteArray failed for %zu bytes", rgba_size);
        return nullptr;
    }
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(rgba_size),
        reinterpret_cast<const jbyte*>(rgba.data())
    );
    return env->ExceptionCheck() ? nullptr : result;
}

// sd.cpp calls this on the generating thread with elapsed seconds. Relay it to the Kotlin bridge
// as (step, steps, seconds); the JVM method writes into a StateFlow that the image-gen screen
// observes. Attach the thread only when sd.cpp's sampler thread is not already attached.
void progress_callback(int step, int steps, float time, void*) {
    if (g_vm == nullptr || g_bridge_class == nullptr || g_on_progress == nullptr) {
        return;
    }
    if (step == 1) {
        LOGI("trace t=%lldms first inference step callback steps=%d", trace_ms(), steps);
    }
    JNIEnv* env = nullptr;
    jint attached = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    bool detach = false;
    if (attached == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        detach = true;
    } else if (attached != JNI_OK || env == nullptr) {
        return;
    }
    env->CallStaticVoidMethod(
        g_bridge_class,
        g_on_progress,
        static_cast<jint>(step),
        static_cast<jint>(steps),
        static_cast<jfloat>(time)
    );
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (detach) {
        g_vm->DetachCurrentThread();
    }
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeSupportsBackend(
    JNIEnv*, jclass, jint backend) {
    return backend_supported(backend) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeInit(
    JNIEnv* env, jclass, jstring modelPath, jint backend) {
    if (!backend_supported(backend)) {
        LOGE("nativeInit: backend %d is not compiled into this build", static_cast<int>(backend));
        return JNI_FALSE;
    }

    std::string path;
    if (!copy_jstring(env, modelPath, path) || path.empty()) {
        LOGE("nativeInit: model path is empty or unavailable");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> generation_lock(g_generation_mutex);
    release_context_locked();

    LOGI("trace t=%lldms native init begin model=%s backend=%s", trace_ms(), path.c_str(), backend_name(backend));
    const auto init_start = std::chrono::steady_clock::now();
    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = path.c_str();
    params.n_threads = std::max(1, std::min(4, sd_get_num_physical_cores()));
    // Do not override wtype. sd_ctx_params_init() uses SD_TYPE_COUNT, which tells
    // stable-diffusion.cpp to preserve the model's actual F16/quantized tensor types.
    params.rng_type = STD_DEFAULT_RNG;
    params.enable_mmap = true;
    params.backend = backend_name(backend);
    params.params_backend = "cpu";

    sd_set_log_callback(android_sd_log, nullptr);
    LOGI("trace t=%lldms entering new_sd_ctx (device/pipeline/model load)", trace_ms());
    sd_ctx_t* new_ctx = new_sd_ctx(&params);
    const auto init_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - init_start
    ).count();
    LOGI("trace t=%lldms exited new_sd_ctx after %lld ms", trace_ms(), static_cast<long long>(init_ms));
    if (new_ctx == nullptr) {
        LOGE("new_sd_ctx failed");
        return JNI_FALSE;
    }
    if (!sd_ctx_supports_image_generation(new_ctx)) {
        LOGE("loaded model does not support image generation");
        free_sd_ctx(new_ctx);
        return JNI_FALSE;
    }

    {
        std::lock_guard<std::mutex> context_lock(g_context_mutex);
        g_ctx = new_ctx;
    }

    // Publish the JNI relay targets and register the global progress callback once per init.
    // Registering every init is idempotent: there is a single process-global context and the
    // callback target never changes.
    if (g_vm == nullptr) {
        env->GetJavaVM(&g_vm);
    }
    if (g_bridge_class == nullptr) {
        jclass local = env->FindClass(BRIDGE_CLASS);
        if (local != nullptr) {
            g_bridge_class = static_cast<jclass>(env->NewGlobalRef(local));
            env->DeleteLocalRef(local);
        }
    }
    if (g_bridge_class != nullptr && g_on_progress == nullptr) {
        g_on_progress = env->GetStaticMethodID(g_bridge_class, "nativeOnProgress", ON_PROGRESS_SIGNATURE);
    }
    if (g_on_progress != nullptr) {
        sd_set_progress_callback(progress_callback, nullptr);
        LOGI("progress callback registered");
    }

    LOGI("trace t=%lldms native init complete", trace_ms());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeGenerate(
    JNIEnv* env, jclass, jstring prompt, jstring negativePrompt,
    jint width, jint height, jint steps, jfloat cfg, jint seed) {
    if (!valid_generation_request(width, height, steps, cfg)) {
        return nullptr;
    }

    std::string prompt_text;
    std::string negative_text;
    if (!copy_jstring(env, prompt, prompt_text) ||
        !copy_jstring(env, negativePrompt, negative_text)) {
        LOGE("nativeGenerate: failed to read prompt strings");
        return nullptr;
    }

    std::lock_guard<std::mutex> generation_lock(g_generation_mutex);
    sd_ctx_t* ctx = nullptr;
    {
        std::lock_guard<std::mutex> context_lock(g_context_mutex);
        ctx = g_ctx;
    }
    if (ctx == nullptr) {
        LOGE("nativeGenerate: not initialized");
        return nullptr;
    }

    // A previous timed-out/cancelled run may have left the context cancellation flag set.
    sd_cancel_generation(ctx, SD_CANCEL_RESET);

    sd_img_gen_params_t gen;
    sd_img_gen_params_init(&gen);
    gen.prompt = prompt_text.c_str();
    gen.negative_prompt = negative_text.c_str();
    gen.width = width;
    gen.height = height;
    gen.clip_skip = -1;
    gen.seed = static_cast<int64_t>(seed);
    gen.batch_count = 1;
    gen.sample_params.sample_steps = steps;
    gen.sample_params.guidance.txt_cfg = cfg;

    // Let stable-diffusion.cpp choose sampler/scheduler defaults appropriate to the loaded
    // architecture instead of forcing Euler A + Discrete on SDXL, Flux, Qwen Image, etc.
    const sample_method_t sample_method = sd_get_default_sample_method(ctx);
    gen.sample_params.sample_method = sample_method;
    gen.sample_params.scheduler = sd_get_default_scheduler(ctx, sample_method);

    sd_image_t* images = nullptr;
    int num_images = 0;
    LOGI("trace t=%lldms entering generate_image dimensions=%dx%d steps=%d", trace_ms(), width, height, steps);
    const bool ok = generate_image(ctx, &gen, &images, &num_images);
    LOGI("trace t=%lldms generate_image returned ok=%d images=%d", trace_ms(), ok ? 1 : 0, num_images);

    if (!ok || num_images < 1 || images == nullptr || images[0].data == nullptr) {
        if (images != nullptr) {
            free_sd_images(images, num_images);
        }
        LOGE("generate_image failed or was cancelled");
        return nullptr;
    }

    jbyteArray result = image_to_rgba(env, images[0], width, height);
    LOGI("trace t=%lldms first output converted to RGBA", trace_ms());
    free_sd_images(images, num_images);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeCancel(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> context_lock(g_context_mutex);
    if (g_ctx != nullptr) {
        sd_cancel_generation(g_ctx, SD_CANCEL_ALL);
        LOGI("nativeCancel requested");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeRelease(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> generation_lock(g_generation_mutex);
    release_context_locked();
    LOGI("nativeRelease OK");
}
