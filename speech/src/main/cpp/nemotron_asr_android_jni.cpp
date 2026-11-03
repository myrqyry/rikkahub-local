#include <jni.h>
#include "transcribe.h"
#include "transcribe/parakeet.h"

#include <cstdint>
#include <new>
#include <string>
#include <vector>

namespace {
struct NativeSession {
    transcribe_session* session = nullptr;
};

NativeSession* from_handle(jlong handle) {
    return reinterpret_cast<NativeSession*>(static_cast<intptr_t>(handle));
}

void fail(JNIEnv* env, const std::string& message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type != nullptr) env->ThrowNew(type, message.c_str());
}

bool check(JNIEnv* env, transcribe_status status, const char* step) {
    if (status == TRANSCRIBE_OK) return true;
    fail(env, std::string(step) + ": " + transcribe_status_string(status));
    return false;
}

jstring text(JNIEnv* env, NativeSession* native) {
    transcribe_stream_text view{};
    transcribe_stream_text_init(&view);
    if (!check(env, transcribe_stream_get_text(native->session, &view), "Read transcript")) return nullptr;
    return env->NewStringUTF(view.full_text ? view.full_text : "");
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_me_rerere_asr_providers_NemotronNative_create(JNIEnv* env, jobject, jstring model_path,
                                                     jstring language, jint threads, jint right_context) {
    if (!model_path || !language || threads < 1 || threads > 12 ||
        (right_context != 0 && right_context != 3 && right_context != 6 && right_context != 13)) {
        fail(env, "Invalid Nemotron model or streaming parameters");
        return 0;
    }
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) return 0;
    transcribe_model_load_params load{};
    transcribe_model_load_params_init(&load);
    load.backend = TRANSCRIBE_BACKEND_CPU;
    transcribe_session_params session_params{};
    transcribe_session_params_init(&session_params);
    session_params.n_threads = threads;
    transcribe_session* session = nullptr;
    const auto status = transcribe_open(path, &load, &session_params, &session);
    env->ReleaseStringUTFChars(model_path, path);
    if (!check(env, status, "Load Nemotron") || !session) return 0;

    const auto* model = transcribe_get_model(session);
    if (!transcribe_model_accepts_ext_kind(model, TRANSCRIBE_EXT_SLOT_STREAM,
                                            TRANSCRIBE_EXT_KIND_PARAKEET_STREAM)) {
        transcribe_close(session);
        fail(env, "This GGUF is not a supported cache-aware Parakeet/Nemotron stream");
        return 0;
    }
    const char* lang = env->GetStringUTFChars(language, nullptr);
    if (!lang) { transcribe_close(session); return 0; }
    transcribe_run_params run{};
    transcribe_run_params_init(&run);
    run.language = lang;
    run.timestamps = TRANSCRIBE_TIMESTAMPS_NONE;
    transcribe_stream_params stream{};
    transcribe_stream_params_init(&stream);
    transcribe_parakeet_stream_ext ext{};
    transcribe_parakeet_stream_ext_init(&ext);
    ext.att_context_right = right_context;
    stream.family = &ext.ext;
    const auto begin_status = transcribe_stream_begin(session, &run, &stream);
    env->ReleaseStringUTFChars(language, lang);
    if (!check(env, begin_status, "Start Nemotron stream")) {
        transcribe_close(session);
        return 0;
    }
    auto* native = new (std::nothrow) NativeSession{session};
    if (!native) { transcribe_close(session); fail(env, "Out of memory"); return 0; }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(native));
}

extern "C" JNIEXPORT jstring JNICALL
Java_me_rerere_asr_providers_NemotronNative_feed(JNIEnv* env, jobject, jlong handle, jfloatArray pcm) {
    auto* native = from_handle(handle);
    if (!native || !native->session || !pcm) { fail(env, "Nemotron stream is closed"); return nullptr; }
    const jsize count = env->GetArrayLength(pcm);
    if (count <= 0) { fail(env, "Empty audio chunk"); return nullptr; }
    std::vector<float> samples(static_cast<size_t>(count));
    env->GetFloatArrayRegion(pcm, 0, count, samples.data());
    if (env->ExceptionCheck()) return nullptr;
    if (!check(env, transcribe_stream_feed(native->session, samples.data(), count, nullptr),
               "Nemotron streaming inference")) return nullptr;
    return text(env, native);
}

extern "C" JNIEXPORT jstring JNICALL
Java_me_rerere_asr_providers_NemotronNative_finish(JNIEnv* env, jobject, jlong handle) {
    auto* native = from_handle(handle);
    if (!native || !native->session) { fail(env, "Nemotron stream is closed"); return nullptr; }
    if (!check(env, transcribe_stream_finalize(native->session, nullptr), "Finalize Nemotron")) return nullptr;
    return text(env, native);
}

extern "C" JNIEXPORT jstring JNICALL
Java_me_rerere_asr_providers_NemotronNative_backend(JNIEnv* env, jobject, jlong handle) {
    auto* native = from_handle(handle);
    if (!native || !native->session) { fail(env, "Nemotron stream is closed"); return nullptr; }
    const char* result = transcribe_model_backend(transcribe_get_model(native->session));
    return env->NewStringUTF(result ? result : "");
}

extern "C" JNIEXPORT void JNICALL
Java_me_rerere_asr_providers_NemotronNative_destroy(JNIEnv*, jobject, jlong handle) {
    auto* native = from_handle(handle);
    if (native) { transcribe_close(native->session); delete native; }
}
