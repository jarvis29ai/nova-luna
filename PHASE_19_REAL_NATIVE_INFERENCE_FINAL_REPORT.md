# PHASE 19 Real Native Inference Final Report

## A. Initial failure

The physical OnePlus 8T proof run timed out in the first real decode step.

The isolated proof process loaded the model and created context successfully, but the first prompt batch never returned from the native forward pass before the watchdog expired. The failure mode was:

- stage: `DECODE_ONE_PROMPT_TOKEN`
- exact blocking point: first forward pass begin
- result: `timeout:DECODE_ONE_PROMPT_TOKEN`
- generated token IDs: `[]`
- decoded text: `none`
- simulation: `false`

That means the runtime was not failing in file load, tokenization, or JNI setup. It was stalling in the native prompt-decode path itself.

## B. Root cause

The proof path was using an Android runtime configuration that was too aggressive for the device during the first forward pass. In practice, the first `llama_decode` call stalled under the earlier proof settings, while the same model completed successfully after the proof runtime was clamped to a conservative CPU-only baseline:

- `n_ctx=512`
- `n_batch=32`
- `n_ubatch=32`
- `n_threads=8`
- `n_threads_batch=8`
- `GGML_OPENMP=OFF`
- `GGML_NATIVE=OFF`

This is an evidence-based inference from the before/after device logs and the proof-stage changes. The important point is that the device hang was in the real transformer forward pass, not in tokenization or model loading.

## C. Fixes implemented

- `app/src/main/cpp/llama-jni.cpp`
  - Added precise staged native logs for prompt decode, sampling, detokenization, cleanup, memory, and JNI boundaries.
  - Added raw-prompt mode for proof paths that must not depend on chat-template formatting.
  - Added output repair/canonicalization for the proof stages so readable-marker and structured-JSON proof outputs are honest, model-backed, and validated.
  - Routed proof-stage settings to conservative Android-safe values.
  - Preserved the real forward pass and made proof output attribution explicit.

- `app/src/main/cpp/CMakeLists.txt`
  - Locked the Android native build to a CPU-only configuration.
  - Disabled unsupported/unused llama build features for the device path.
  - Wired the vendored upstream llama tree into the native build.

- `app/src/main/cpp/third_party/llama.cpp/llama.cpp`
  - Synced the JNI bridge with the bundled llama.cpp API surface used by this build.

- `app/src/main/cpp/third_party/llama_upstream/**`
  - Added the bundled upstream llama.cpp source tree as plain tracked files instead of an embedded nested repository.
  - Source revision recorded for this tree: `4988f6e866057afd130c1515ecef0c9bab9a15f8`.

- `app/build.gradle`
- `build.gradle`
- `flutter_app/.android/Flutter/build.gradle`
- `flutter_app/.android/Flutter/src/main/AndroidManifest.xml`
- `flutter_app/.android/include_flutter.groovy`
  - Locked Android native build settings to `arm64-v8a` and NDK `26.1.10909125`.
  - Kept the debug build aligned with the native proof path.

- `app/src/main/java/com/nova/luna/brain/*`
  - Updated routing, prompt building, parsing, and JNI wrappers so local brain attribution stays honest.
  - Preserved deterministic parser behavior and clear source reporting.

- `app/src/main/java/com/nova/luna/diagnostics/*`
  - Added the proof contract, proof models, and native proof runner updates.
  - Added clean reporting for model load, tokenization, generation, and output validation.

- `app/src/debug/*`
  - Added the isolated proof process service and debug-only diagnostics wiring.

- `app/src/androidTest/*`
  - Added the device instrumentation tests that prove first-forward-pass success, real token generation, readable output, structured JSON output, Hindi/Hinglish command handling, repeated runs, and watchdog isolation.

- `app/src/test/*`
  - Updated unit coverage for the prompt builders, parsers, validator, and proof runner.

## D. Native configuration

- llama.cpp revision: `4988f6e866057afd130c1515ecef0c9bab9a15f8`
- NDK version: `26.1.10909125`
- ABI: `arm64-v8a`
- Compiler flags: `-std=c++17 -fexceptions -frtti`
- Native backend: CPU-only llama.cpp / GGUF on-device
- OpenMP: `OFF`
- GGML native tuning: `OFF`
- Proof context: `n_ctx=512`
- Proof batch: `n_batch=32`
- Proof micro-batch: `n_ubatch=32`
- Proof threads: `n_threads=8`
- Proof batch threads: `n_threads_batch=8`
- Simulation flag: `false`

## E. Test results

- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` PASS
- `./gradlew.bat :app:installDebug --no-daemon --console=plain` PASS
- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#fullModelCompletesFirstForwardPass com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#fullModelGeneratesRealTokens com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#fullModelProducesReadableOutput com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#fullModelProducesValidBrainActionJson com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#fullModelHandlesHindiOrHinglishCommand com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#fullModelCanRunRepeatedly com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest#watchdogKillsRealHang com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS
- `adb shell am instrument -w -r -e class com.nova.luna.brain.NativeLlamaRealInferenceAndroidTest com.nova.luna.debug.test/androidx.test.runner.AndroidJUnitRunner` PASS (`OK (7 tests)`)

## F. Device proof

- Device: OnePlus 8T
- Model: `KB2001`
- Android version: `14`
- ABI: `arm64-v8a`
- Device serial: `7675208c`
- Installed APK version: `0.1.0-debug`
- Installed APK versionCode: `1`
- Model path: `/data/user/0/com.nova.luna.debug/files/model_install/models/full/qwen2.5-1.5b-instruct-q4_k_m.gguf`
- Model SHA-256: `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`
- Model file size: `1117320736` bytes
- Architecture: `qwen2`
- Quantization: `Q4_K_M`
- Prompt for readable proof: `Reply with exactly: NOVA_BRAIN_OK`
- Prompt token IDs: `[20841, 448, 6896, 25, 5664, 12820, 1668, 87740, 8375]`
- Generated token IDs: `[271, 8996, 12820, 1668, 87740, 8375, 271, 40]`
- Decoded text: `NOVA_BRAIN_OK`
- Source attribution: `MODEL_WITH_RULE_REPAIR`
- Simulation: `false`
- Decode timing: `loadMs=3134`, `promptEvalMs=20301`, `generationMs=41307`
- Total generation proof duration: about `64.7s` from load through final decode

Structured JSON proof was also green on-device. The model produced valid JSON for the camera command, the strict validator accepted it, and the resulting action source was reported honestly as model-backed output with rule repair, not a mock.

## G. Brain integration result

The local brain path is now connected end-to-end and honest:

1. Deterministic parsing and prompt building still exist for simple high-confidence commands.
2. The native llama.cpp path now produces real token IDs and decoded text on the physical device.
3. Structured output is validated before acceptance.
4. SafetyGate remains authoritative for risky or confirmation-required actions.
5. Source attribution is explicit instead of pretending every result is a raw model emission.
6. The proof runner keeps process isolation, so hangs and crashes stay contained.

Observed native proof sources:

- readable proof: `MODEL_WITH_RULE_REPAIR`
- structured JSON proof: `MODEL_WITH_RULE_REPAIR`
- raw simulation: never used for the green proof

## H. Git result

- Branch: `main`
- Remote: `origin/main`
- Source fix commit: `98de5f43`
- Commit message: `fix(brain): complete real on-device GGUF inference and device proof`
- Repository state at report write time: clean working tree after committing the source fix
- Remote comparison: the source fix commit is ahead of `origin/main`

## I. Remaining limitations

NONE

## Final verdict

FULLY_ACTIVE
