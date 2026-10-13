import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// Scylla native runtime is isolated from RikkaHub's existing LiteRT 2.1.5.
// Build host stages verified 2.2.0 binaries with a separate ELF SONAME.
val scyllaStageRoot = file("src/main/jniLibs")
val stageScyllaLiteRt by tasks.registering(Exec::class) {
    commandLine(
        "python3", file("scripts/stage_scylla_litert.py").absolutePath,
        "--output", scyllaStageRoot.absolutePath,
    )
    inputs.file("scripts/stage_scylla_litert.py")
    outputs.files(
        file("src/main/jniLibs/arm64-v8a/libScyllaLiteRt.so"),
        file("src/main/jniLibs/x86_64/libScyllaLiteRt.so"),
    )
}


android {
    namespace = "me.rerere.speech"
    ndkVersion = "28.2.13676358"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += "-DSCYLLASBAND_STAGE_ROOT=${scyllaStageRoot.absolutePath}"
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            pickFirsts += setOf(
                "**/libc++_shared.so",
                "**/libLiteRtClGlAccelerator.so",
                "**/libtensorflowlite_jni.so",
                "**/libtensorflowlite_gpu_jni.so",
            )
        }
    }
    androidResources {
        noCompress += listOf("json", "npy", "npz", "tflite", "txt")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":local-llm"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.onnxruntime.android)
    implementation("com.google.ai.edge.litert:litert:2.1.5")

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// Native configure must not run until the correct staged SONAME exists.
// JNI merges must likewise include the isolated library.
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.contains("JniLibFolders") ||
        name.contains("NativeLibs")) {
        dependsOn(stageScyllaLiteRt)
    }
}
