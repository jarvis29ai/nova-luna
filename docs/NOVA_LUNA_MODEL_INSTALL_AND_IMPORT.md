# Nova/Luna Model Install And Import

## Small APK Rule

Nova/Luna keeps the APK small by never bundling model binaries in `app/src/main/assets` or `app/src/main/res/raw`.

The model files are downloaded or imported after install and then stored in app-private storage only.

## Model Roles

The current model catalog is role-based and supports up to three downloaded brain roles at one time:

- `CORE_BRAIN` for main reasoning, planning, and complex commands.
- `MULTILINGUAL_BACKUP` for Hindi, Hinglish, and regional-language fallback.
- `LITE_FALLBACK` for simple commands and low-RAM fallback.

Each role is verified independently before it becomes `READY`.

## Where Models Live

Downloaded or imported packs are stored inside app-private storage under:

- `context.filesDir/model_install/models/<packId>/...`
- `context.filesDir/model_install/model_downloads/<packId>/...` while staging

The runtime state and registry metadata also stay private inside the same `model_install` root.

## Release Download Flow

The release flow is:

1. User installs the small APK.
2. The app opens the model setup screen.
3. The app checks install state for all brain roles.
4. If no role is ready, the UI shows `Download Nova/Luna AI Brain`.
5. The app downloads the configured source URL into app-private storage.
6. The app verifies SHA-256 before marking the pack ready.
7. `BrainRouter` can use the role only after the install state becomes `READY`.

If a source URL, SHA-256, or size is missing, the UI and report should show a not-configured state instead of `READY`.

## Debug Import Flow

For local development, the repo includes a debug-only import receiver:

- Action: `com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK`
- Optional extras:
  - `com.nova.luna.debug.extra.PACK_ID`
  - `com.nova.luna.debug.extra.ROLE`
  - `com.nova.luna.debug.extra.SOURCE_DIR`

The receiver copies a local file into app-private storage, verifies SHA-256, and then registers the pack.

### Windows To Android Example

The user can push model files from the repo-local model tree into a shared debug folder on the phone:

```powershell
adb shell mkdir -p /sdcard/Download/nova-luna-model-import

adb push "C:\nova-luna\models\core\gemma-3n-E2B-it-int4.litertlm" /sdcard/Download/nova-luna-model-import/
adb push "C:\nova-luna\models\multilingual\qwen2.5-1.5b-instruct-q4_k_m.gguf" /sdcard/Download/nova-luna-model-import/
adb push "C:\nova-luna\models\lite\qwen2.5-0.5b-instruct-q4_k_m.gguf" /sdcard/Download/nova-luna-model-import/
```

Then import the pack from the debug build:

```powershell
adb shell am broadcast -a com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK --es com.nova.luna.debug.extra.ROLE core_brain --es com.nova.luna.debug.extra.SOURCE_DIR /sdcard/Download/nova-luna-model-import
adb shell am broadcast -a com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK --es com.nova.luna.debug.extra.ROLE multilingual_backup --es com.nova.luna.debug.extra.SOURCE_DIR /sdcard/Download/nova-luna-model-import
adb shell am broadcast -a com.nova.luna.debug.ACTION_IMPORT_MODEL_PACK --es com.nova.luna.debug.extra.ROLE lite_fallback --es com.nova.luna.debug.extra.SOURCE_DIR /sdcard/Download/nova-luna-model-import
```

If you omit the pack or role extra, the debug receiver tries to import every available pack from the source directory.

## How To Verify Status

Open the app diagnostics screen and review the model report. The report shows:

- each model role
- installed or missing
- ready or not ready
- private storage path
- detected size
- hash verification status
- whether the source is configured
- the currently selected active brain role

You can also check the debug import report written to the app cache directory after an import run.

## Practical Notes

- Release builds should use configured download URLs and hashes from local properties or Gradle properties.
- Debug builds can use the import receiver for already-downloaded files.
- A model is never marked ready until SHA-256 verification passes.
- The repo-local `models/` tree is the canonical Windows staging location for local development; the Android runtime still copies verified packs into app-private storage.
- No model binary should be committed to git.
