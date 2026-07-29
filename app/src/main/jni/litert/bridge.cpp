#include <jni.h>
#include <string>

#include "litert/c/litert_common.h"
#include "litert/c/litert_compiled_model.h"
#include "litert/c/litert_environment.h"
#include "litert/c/litert_model.h"
#include "litert/c/litert_tensor_buffer.h"
#include "litert/c/litert_tensor_buffer_requirements.h"
#include "litert/c/litert_tensor_buffer_types.h"

extern "C" {

struct BridgeContext {
  LiteRtCompiledModel compiled;
  LiteRtEnvironment env;
  LiteRtModel model;
};

JNIEXPORT jlong JNICALL
Java_me_rerere_locallm_LiteRtNativeBridge_nativeInit(
    JNIEnv* env, jobject thiz, jstring model_path, jstring delegate) {
  const char* path = env->GetStringUTFChars(model_path, nullptr);

  LiteRtEnvironment env_handle;
  if (LiteRtCreateEnvironment(0, nullptr, &env_handle) != kLiteRtStatusOk) {
    env->ReleaseStringUTFChars(model_path, path);
    return 0;
  }

  LiteRtModel model;
  if (LiteRtCreateModelFromFile(env_handle, path, &model) != kLiteRtStatusOk) {
    LiteRtDestroyEnvironment(env_handle);
    env->ReleaseStringUTFChars(model_path, path);
    return 0;
  }

  LiteRtCompiledModel compiled;
  if (LiteRtCreateCompiledModel(env_handle, model, nullptr, &compiled) !=
      kLiteRtStatusOk) {
    LiteRtDestroyModel(model);
    LiteRtDestroyEnvironment(env_handle);
    env->ReleaseStringUTFChars(model_path, path);
    return 0;
  }

  auto* ctx = new BridgeContext{compiled, env_handle, model};
  env->ReleaseStringUTFChars(model_path, path);
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jobject JNICALL
Java_me_rerere_locallm_LiteRtNativeBridge_nativeGetInputOutputDetails(
    JNIEnv* env, jobject thiz, jlong handle) {
  auto* ctx = reinterpret_cast<BridgeContext*>(handle);
  if (!ctx) return nullptr;

  jclass cls = env->FindClass(
      "me/rerere/locallm/LiteRtCompiledModelEngine$ModelIODetails");
  if (!cls) return nullptr;

  jmethodID ctor = env->GetMethodID(cls, "<init>", "(II)V");
  return env->NewObject(cls, ctor, 1, 1);
}

JNIEXPORT jobject JNICALL
Java_me_rerere_locallm_LiteRtNativeBridge_nativeRun(
    JNIEnv* env, jobject thiz, jlong handle, jobject inputs) {
  auto* ctx = reinterpret_cast<BridgeContext*>(handle);
  if (!ctx) return nullptr;

  LiteRtTensorBufferRequirements input_req, output_req;
  LiteRtGetCompiledModelInputBufferRequirements(ctx->compiled, 0, 0,
                                                &input_req);
  LiteRtGetCompiledModelOutputBufferRequirements(ctx->compiled, 0, 0,
                                                 &output_req);

  LiteRtTensorBuffer input_buf, output_buf;
  LiteRtCreateManagedTensorBufferFromRequirements(ctx->env, nullptr, input_req,
                                                   &input_buf);
  LiteRtCreateManagedTensorBufferFromRequirements(
      ctx->env, nullptr, output_req, &output_buf);

  LiteRtRunCompiledModel(ctx->compiled, 0, 1, &input_buf, 1, &output_buf);

  size_t size;
  LiteRtGetTensorBufferSize(output_buf, &size);

  jclass bb_cls = env->FindClass("java/nio/ByteBuffer");
  jmethodID allocate = env->GetStaticMethodID(
      bb_cls, "allocateDirect", "(I)Ljava/nio/ByteBuffer;");
  jobject bb = env->CallStaticObjectMethod(bb_cls, allocate,
                                           static_cast<jint>(size));

  jclass hashmap_cls = env->FindClass("java/util/HashMap");
  jmethodID hashmap_ctor = env->GetMethodID(hashmap_cls, "<init>", "()V");
  jmethodID put = env->GetMethodID(
      hashmap_cls, "put",
      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

  jobject out_map = env->NewObject(hashmap_cls, hashmap_ctor);
  auto name = env->NewStringUTF("output_0");
  env->CallObjectMethod(out_map, put, name, bb);

  LiteRtDestroyTensorBuffer(input_buf);
  LiteRtDestroyTensorBuffer(output_buf);

  return out_map;
}

JNIEXPORT void JNICALL
Java_me_rerere_locallm_LiteRtNativeBridge_nativeRunAsync(
    JNIEnv* env, jobject thiz, jlong handle, jobject inputs, jobject callback) {
  jobject result =
      Java_me_rerere_locallm_LiteRtNativeBridge_nativeRun(env, thiz, handle,
                                                          inputs);
  if (callback) {
    jclass cb_cls = env->GetObjectClass(callback);
    jmethodID on_result =
        env->GetMethodID(cb_cls, "onResult", "(Ljava/util/Map;)V");
    if (on_result) env->CallVoidMethod(callback, on_result, result);
  }
}

JNIEXPORT void JNICALL
Java_me_rerere_locallm_LiteRtNativeBridge_nativeDispose(
    JNIEnv* env, jobject thiz, jlong handle) {
  auto* ctx = reinterpret_cast<BridgeContext*>(handle);
  if (!ctx) return;
  LiteRtDestroyCompiledModel(ctx->compiled);
  LiteRtDestroyModel(ctx->model);
  LiteRtDestroyEnvironment(ctx->env);
  delete ctx;
}

}  // extern "C"