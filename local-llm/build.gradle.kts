import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.locallm"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // Return default values (null / 0 / false) for Android framework calls in JVM unit
        // tests instead of throwing "not mocked" exceptions. Required because production
        // code (e.g. LiteRtToolPrefix) calls android.util.Log which isn't available on JVM.
        unitTests.isReturnDefaultValues = true
    }
    sourceSets {
        getByName("main").jniLibs.setSrcDirs(listOf("src/main/jniLibs"))
    }
    packaging {
        jniLibs {
            // Avoid extracting native libs at install time so System.loadLibrary path stays cheap.
            useLegacyPackaging = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    api(project(":ai"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    // LiteRT-LM runtime: loads .litertlm model files produced by the LiteRT-LM toolchain.
    //
    // Initial 0.11.0 -> 0.17.0 bump (2026-09-09) supported newer models that
    // native-SIGSEGV inside liblitertlm_jni.so (nativeCreateEngine) on 0.11.0. History: a
    // prior 0.12.0 bump SIGSEGV'd during vision-encoder init on Snapdragon 8 Gen 1
    // (Adreno), so 0.11.0 was pinned to match Google AI Edge Gallery's reference build.
    // Re-verify Gemma 4 vision on Adreno-class devices before relying on it there.
    // Updated to 0.18.0 (2026-10-08): Capabilities was removed in favor of
    // ModelInfo.Llm; app and local-llm must resolve exactly the same revision.
    // Compilation qualified; runtime/Pixel verification still pending.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    // CompiledModel / Accelerator.NPU Kotlin API for on-device JIT task inference.
    // LiteRT 2.3.0 fixes the AGP 9 namespace conflict from 2.2.0.
    // LiteRT-LM 0.18.0 is an independent engine and remains pinned separately.
    implementation("com.google.ai.edge.litert:litert:2.3.0")
    // llama.cpp runtime via Llamatik: loads .gguf chat models.
    // 1.10.1 fixes repeated embedding calls failing on stale KV cache (PR #171).
    // Upstream release verified 2026-10-07; Android GGUF inference still requires
    // an actual installed model and on-device runtime qualification.
    implementation("com.llamatik:library:1.10.1")
    // Raw org.tensorflow.lite.Interpreter for the PP-OCRv5 det/rec graphs (the 
    // task-vision/-audio AARs were dropped: their jni libs are 4KB-aligned, which
    // Android 15+ 16KB-page devices reject at install; task inference now runs via
    // litert CompiledModel (NPU) or this raw Interpreter).
    // tensorflow-lite pinned to 2.16.1: 2.14.0's tensorflow-lite-api AAR declared the same
    // org.tensorflow.lite package as tensorflow-lite (AGP manifest-merge conflict); 2.16.1
    // renamed the api package to org.tensorflow.lite.api (TFLite#63043).
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
}
