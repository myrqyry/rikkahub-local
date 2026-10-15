plugins { id("com.android.application") }
android {
  namespace = "org.meristem.rvcprobe"
  compileSdk = 37
  defaultConfig {
    applicationId = "org.meristem.rvcprobe"
    minSdk = 28
    targetSdk = 35
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ndk { abiFilters += listOf("arm64-v8a") }
  }
  buildTypes { getByName("debug") { isMinifyEnabled = false } }
  testOptions { animationsDisabled = true }
}
dependencies {
  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
}
