#include <jni.h>
#include <android/log.h>
#include <stable-diffusion.h>
#include <ctime>

#define LOG_TAG "SD-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static sd_ctx_t* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeInit(
    JNIEnv* env, jclass, jstring modelPath, jint backend) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("nativeInit: %s backend=%d", path, (int)backend);

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path    = path;
    params.n_threads     = 4;
    params.wtype         = SD_TYPE_F32;
    params.rng_type      = STD_DEFAULT_RNG;
    params.enable_mmap   = true;
    params.backend       = (backend == 1) ? "vulkan" : "cpu";
    params.params_backend = "cpu";

    sd_ctx_t* ctx = new_sd_ctx(&params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        LOGE("new_sd_ctx failed");
        return JNI_FALSE;
    }
    g_ctx = ctx;
    LOGI("nativeInit OK");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeGenerate(
    JNIEnv* env, jclass, jstring prompt, jstring negativePrompt,
    jint width, jint height, jint steps, jfloat cfg, jint seed) {

    if (!g_ctx) {
        LOGE("nativeGenerate: not initialized");
        return nullptr;
    }

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    const char* n = negativePrompt ? env->GetStringUTFChars(negativePrompt, nullptr) : "";

    sd_img_gen_params_t gen;
    sd_img_gen_params_init(&gen);
    gen.prompt          = p;
    gen.negative_prompt = n;
    gen.width           = (uint32_t)width;
    gen.height          = (uint32_t)height;
    gen.clip_skip       = -1;
    gen.seed            = (seed < 0) ? (int64_t)time(nullptr) : seed;
    gen.batch_count     = 1;
    gen.sample_params.sample_steps   = steps;
    gen.sample_params.guidance.txt_cfg = cfg;
    gen.sample_params.sample_method  = EULER_A_SAMPLE_METHOD;
    gen.sample_params.scheduler      = DISCRETE_SCHEDULER;

    sd_image_t* images = nullptr;
    int num_images = 0;
    bool ok = generate_image(g_ctx, &gen, &images, &num_images);

    env->ReleaseStringUTFChars(prompt, p);
    if (negativePrompt) env->ReleaseStringUTFChars(negativePrompt, n);

    if (!ok || num_images < 1 || !images || !images->data) {
        if (images) free_sd_images(images, num_images);
        LOGE("generate_image failed");
        return nullptr;
    }

    uint32_t len = images->width * images->height * images->channel;
    jbyteArray arr = env->NewByteArray((jsize)len);
    env->SetByteArrayRegion(arr, 0, (jsize)len, reinterpret_cast<jbyte*>(images->data));
    free_sd_images(images, num_images);
    return arr;
}

extern "C" JNIEXPORT void JNICALL
Java_me_rerere_rikkahub_data_ai_StableDiffusionBridge_nativeRelease(JNIEnv*, jclass) {
    if (g_ctx) {
        free_sd_ctx(g_ctx);
        g_ctx = nullptr;
        LOGI("nativeRelease OK");
    }
}
