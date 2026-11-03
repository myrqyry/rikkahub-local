# Isolated Android RVC ONNX-stage benchmark

This is a **separate Android instrumentation app** (org.meristem.rvcprobe).
It does not link, launch, uninstall, or modify RikkaHub. It runs real model
weights with **synthetic stage inputs** to measure runtime compatibility,
load latency and two warm passes. It does NOT convert an actual TTS phrase,
test speaker similarity, validate pitch acoustics or prove fluid playback.

## Pinned test weights (not included)

- ContentVec Q8: TigreGotico/voiceclonnx-rvc, revision cbfbabdabe6a,
  contentvec_768l12_q8.onnx, 95,224,416 bytes,
  SHA-256 23d4914b29779cd68af19191405423e82ec978fafe9c12227df23d3ebe04d421
- RMVPE Q8: same repo/revision, rmvpe_q8.onnx, 98,719,204 bytes,
  SHA-256 9151c489d8c09a2c31c035e5eb24651c18e9f05cc04c4cc1afc5086c9bce7d1e
- Synth: Kady-x/Nova_HD_by_Kady, revision 1dd56337c517,
  onnx/Nova_HD.onnx, 115,127,097 bytes,
  SHA-256 c01d34d7c2c41e0b428c24921a820dd245f7b077c2b2be9dbbfdeb42995a31cc

Source URLs: https://huggingface.co/TigreGotico/voiceclonnx-rvc
and https://huggingface.co/Kady-x/Nova_HD_by_Kady

Base models declare MIT, with provenance in the repository.
Nova_HD declares CC-BY-4.0 and specifies attribution:
"Voice model created by Kady (Nova_HD)".
No third-party model weights are copied into this repository.

## Build

From the RikkaHub repository root, with the Android SDK configured:

    export ANDROID_HOME=/path/to/Android/Sdk
    ./gradlew -p tools/experiments/rvc-onnx-probe :app:assembleDebug :app:assembleDebugAndroidTest --offline

Install *only* this isolated test package and its instrumentation APK:

    BASE=tools/experiments/rvc-onnx-probe/app/build/outputs/apk
    adb install -r "$BASE/debug/app-debug.apk"
    adb install -r "$BASE/androidTest/debug/app-debug-androidTest.apk"

The test requires all three pinned models in the probe app's **private**
files/rvc-ort-probe directory. These can be staged by pushing each downloaded
file into its external app-specific directory, then piping the data into a
run-as process. On Pixel Android 17, files created directly by the ADB shell
in Android/data were not readable by the app, even though shell could see
them; the pipe-copy into private storage was needed:

    PACKAGE=org.meristem.rvcprobe
    EXTERNAL=/sdcard/Android/data/$PACKAGE/files/rvc-ort-probe
    PRIVATE=/data/user/0/$PACKAGE/files/rvc-ort-probe
    adb shell "mkdir -p $EXTERNAL"
    adb shell run-as "$PACKAGE" mkdir -p files/rvc-ort-probe
    adb push /path/to/contentvec_768l12_q8.onnx "$EXTERNAL/contentvec_768l12_q8.onnx"
    adb shell "cat $EXTERNAL/contentvec_768l12_q8.onnx | run-as $PACKAGE dd of=$PRIVATE/contentvec_768l12_q8.onnx bs=1M"

Repeat for rmvpe_q8.onnx and Nova_HD.onnx. Verify their SHA-256 inside the
run-as private directory against the manifest above **before executing**.

Run tests one by one using the real AndroidJUnitRunner:

    RUNNER=org.meristem.rvcprobe.test/androidx.test.runner.AndroidJUnitRunner
    CLASS=org.meristem.rvcprobe.OnnxStagesTest
    timeout -k 5s 90s adb shell am instrument -w -e class "$CLASS#synthRealWeights" "$RUNNER"

Available methods: contentVecRealWeights, rmvpeRealWeights, synthRealWeights,
synthXnnpack, synthNnapi, synthWebGpu.

Careful: the ADB instrumentation wrapper may return exit code zero even
when JUnit reports failures. **Check for JUnit "OK (1 test)"** before
recording a passing test.

The benchmark writes metrics to the probe package's private
files/rvc-ort-probe/metrics.txt. Export before cleanup:

    adb exec-out run-as org.meristem.rvcprobe cat /data/user/0/org.meristem.rvcprobe/files/rvc-ort-probe/metrics.txt

Clean up only the benchmark's known packages and test files:

    adb shell rm -rf /sdcard/Android/data/org.meristem.rvcprobe/files/rvc-ort-probe
    adb uninstall org.meristem.rvcprobe.test
    adb uninstall org.meristem.rvcprobe

These cleanup actions and verified model staging were executed successfully
on October 14, 2026, and RikkaHub remained running. See the checked-in
results/pixel-10-pro-20261014.txt for raw metrics and
docs/rvc-on-device.md for interpretation and the next acceptance gate.
