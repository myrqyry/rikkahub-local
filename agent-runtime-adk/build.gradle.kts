import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // Required so @Composable function types in :ai's ProviderSetting ABIs match at the JVM
    // bytecode level (the compose compiler injects a synthetic composer int parameter).
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "me.rerere.agentruntime"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // Return default values for Android framework calls in JVM unit tests instead of
        // throwing "not mocked". Same reason and same setting as :ai and :local-llm.
        unitTests.isReturnDefaultValues = true
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // Rikkahub's provider/UI abstraction that ADK models must adapt.
    api(project(":ai"))

    // Google ADK Kotlin (Pre-GA, Apache-2.0). Wrapped behind the AgentRuntime boundary;
    // pinned to 0.8.0 (Kotlin 2.1 metadata / JDK 17 / minSdk 26, consumable by this project).
    api("com.google.adk:google-adk-kotlin-core:0.8.0")

    // Compose runtime on the classpath is required by the compose compiler plugin (see plugins block).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)

    // tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}
