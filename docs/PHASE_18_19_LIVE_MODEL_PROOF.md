# Phase 18 And 19 Live Model Proof

This checklist reproduces the live GGUF tokenizer and inference proof on a connected Android device.

## Prerequisites

- A USB-connected Android phone with developer options and USB debugging enabled
- `adb` available at:
  - `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`
- Local GGUF file:
  - `C:\Users\cricv\Desktop\nova-luna-models\fallback-qwen-0.5b\qwen2.5-0.5b-instruct-q4_k_m.gguf`

## Repeatable Checklist

1. Verify device connection and device properties.
2. Verify the PC-side SHA256 for the GGUF file.
3. Push the GGUF to `/data/local/tmp` on the phone.
4. Import the model into the app-private model directory using the debug receiver.
5. Confirm the internal runtime state is `READY`.
6. Run the Phase 18 tokenizer proof.
7. Run the Phase 19 native inference proof.
8. Save the proof logs.
9. Run the crash check and regression tests.

## Commands

```powershell
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
```

### 1. Device check

```powershell
& $ADB devices
& $ADB shell getprop ro.product.manufacturer
& $ADB shell getprop ro.product.model
& $ADB shell getprop ro.build.version.release
& $ADB shell getprop ro.product.cpu.abi
```

Expected:
- device status is `device`
- manufacturer, model, Android version, and ABI are printed

### 2. Verify GGUF integrity on PC

```powershell
Get-FileHash "C:\Users\cricv\Desktop\nova-luna-models\fallback-qwen-0.5b\qwen2.5-0.5b-instruct-q4_k_m.gguf" -Algorithm SHA256
```

Expected SHA256:
- `74A4DA8C9FDBCD15BD1F6D01D621410D31C6FC00986F5EB687824E7B93D7A9DB`

### 3. Push the GGUF to staging on the phone

```powershell
& $ADB push "C:\Users\cricv\Desktop\nova-luna-models\fallback-qwen-0.5b\qwen2.5-0.5b-instruct-q4_k_m.gguf" "/data/local/tmp/qwen2.5-0.5b-instruct-q4_k_m.gguf"
& $ADB shell sha256sum /data/local/tmp/qwen2.5-0.5b-instruct-q4_k_m.gguf
```

Expected:
- `sha256sum` matches the PC hash
- byte size is `491400032`

### 4. Import into app-private storage

The debug receiver is:
- `com.nova.luna.debug/com.nova.luna.modelinstall.ModelImportReceiver`
- action: `com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK`

```powershell
& $ADB shell am broadcast -n com.nova.luna.debug/com.nova.luna.modelinstall.ModelImportReceiver -a com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK --es com.nova.luna.debug.extra.PACK_ID lite --es com.nova.luna.debug.extra.SOURCE_DIR /data/local/tmp
```

Expected log output:
- `import_success=true`
- `model_found=true`
- `source_path="/data/local/tmp/qwen2.5-0.5b-instruct-q4_k_m.gguf"`
- `internal_path="/data/user/0/com.nova.luna.debug/files/model_install/models/lite"`
- `sha256="74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"`
- `model_role="LITE_FALLBACK"`
- `model_status="READY"`
- `error=null`

Optional verification:

```powershell
& $ADB shell run-as com.nova.luna.debug cat files/model_install/runtime_state.json
& $ADB shell run-as com.nova.luna.debug ls -R files/model_install
```

Expected:
- `packId: lite`
- `runtimeStatus: READY`
- `modelRootPath: /data/user/0/com.nova.luna.debug/files/model_install/models/lite`

### 5. Phase 18 tokenizer proof

Clear logs, then run the tokenizer proof:

```powershell
& $ADB logcat -c
& $ADB shell am broadcast -n com.nova.luna.debug/com.nova.luna.brain.DiagnosticBroadcastReceiver -a com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME --es mode tokenizer
& $ADB logcat -d -v brief | findstr /c:"NovaLunaDiagnostic"
```

Expected proof fields:
- `phase=18`
- `status=PASS`
- `model_found=true`
- `model_path=/data/user/0/com.nova.luna.debug/files/model_install/models/lite/qwen2.5-0.5b-instruct-q4_k_m.gguf`
- `model_sha256=74A4DA8C9FDBCD15BD1F6D01D621410D31C6FC00986F5EB687824E7B93D7A9DB`
- `tokenizer_loaded=true`
- `vocab_size=151936`
- `sample_text="open camera"`
- `sample_token_ids=[151643, 2508, 6249]`
- `tokenizer_error=null`
- `proof_source="REAL_DEVICE_GGUF"`
- `simulation=false`

Save the log file:

```powershell
& $ADB logcat -d -v brief | Select-String -Pattern "NovaLunaDiagnostic" | Out-File -Encoding utf8 docs\phase18_tokenizer_device_proof_log.txt
```

### 6. Phase 19 inference proof

Clear logs, then run the inference proof:

```powershell
& $ADB logcat -c
& $ADB shell am broadcast -n com.nova.luna.debug/com.nova.luna.brain.DiagnosticBroadcastReceiver -a com.nova.luna.debug.ACTION_DIAGNOSE_RUNTIME --es mode inference
& $ADB logcat -d -v brief | findstr /c:"NovaLunaDiagnostic"
```

Expected proof fields:
- `phase=19`
- `status=PASS`
- `model_found=true`
- `model_loaded=true`
- `real_inference=true`
- `generated_token_count=16`
- `decoded_text="!!!!!!!!!!!!!!!!"`
- `output_source="NATIVE_GGUF"`
- `simulation=false`
- `inference_error=null`

Save the log file:

```powershell
& $ADB logcat -d -v brief | Select-String -Pattern "NovaLunaDiagnostic" | Out-File -Encoding utf8 docs\phase19_inference_device_proof_log.txt
```

## Crash Check

```powershell
& $ADB shell am force-stop com.nova.luna.debug
& $ADB shell monkey -p com.nova.luna.debug -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 3
& $ADB logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime SIGSEGV Fatal signal UnsatisfiedLinkError"
```

Expected:
- no crash signatures in logcat

## Regression Tests

```powershell
$env:FLUTTER_ROOT='C:\src\flutter'
$env:GRADLE_USER_HOME="$PWD\.gradle-user"
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Expected:
- all commands exit `0`

## Troubleshooting

- If `adb` cannot talk to the phone, re-enable USB debugging and accept the RSA prompt.
- If import from `/sdcard/Download` fails with `AccessDeniedException`, use `/data/local/tmp` staging instead.
- If the install state reports `model_sha256=null`, update `ModelInstallService` so the lite spec carries the BuildConfig SHA.
- If Phase 19 returns generated text but still reports partial, make sure the proof wrapper treats real native output as success even when JSON parsing fails.
- If Gradle fails with a Flutter SDK lock error, rerun the command with escalation so `C:\src\flutter\packages\flutter_tools\gradle\.gradle` can be updated.

## Reference Paths

- App package: `com.nova.luna.debug`
- Tokenizer receiver: `com.nova.luna.debug/com.nova.luna.brain.DiagnosticBroadcastReceiver`
- Import receiver: `com.nova.luna.debug/com.nova.luna.modelinstall.ModelImportReceiver`
- Internal GGUF path:
  - `/data/user/0/com.nova.luna.debug/files/model_install/models/lite/qwen2.5-0.5b-instruct-q4_k_m.gguf`
- Log files:
  - `docs/phase18_tokenizer_device_proof_log.txt`
  - `docs/phase19_inference_device_proof_log.txt`
