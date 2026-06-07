# Local LLM Setup

This project keeps the brain layer local-first. The phone-local model stack is now the production direction when model assets are present. The Ollama-compatible provider is still optional, dev-only, and only runs when explicitly enabled by build flags.

## Phone-Local Model Stack

- `PhoneGemmaRuntime` delegates through `PhoneLocalLlmRuntime`, which is the shared bridge for phone-resident model files.
- The runtime resolves the configured model stack from `gemma_*` build flags, checks whether the model asset exists, and refuses to guess when the prompt or output is unsafe.
- The default stack is ordered by priority as `gemma-3n`, `qwen-3-small`, `gemma-3-270m`, and `phi-4-mini`, with only `gemma-3n` enabled by default.
- The runtime only accepts strict `BrainAction` JSON and still passes through `BrainActionValidator` and `SafetyGate` before anything can execute.
- If the configured model asset is missing, disabled, or oversized for the prompt, `BrainService` falls back to deterministic behavior instead of forcing a partial answer.

## 1. Install Ollama

- Install Ollama from [https://ollama.com](https://ollama.com)
- Start the Ollama service locally
- Confirm the local endpoint is available at `http://127.0.0.1:11434`

## 2. Pull a Suggested Model

Recommended first model:

- `qwen2.5:3b`

Example:

```powershell
ollama pull qwen2.5:3b
```

## 3. Enable the Local Brain Provider

Use these Gradle flags when building the Android app:

```powershell
./gradlew.bat :app:testDebugUnitTest --no-daemon -Pbrain_provider=ollama -Pllm_enabled=true -Pbrain_capability_mode=local_llm_dev -Pollama_base_url=http://127.0.0.1:11434 -Pollama_model=qwen2.5:3b
./gradlew.bat :app:assembleDebug --no-daemon -Pbrain_provider=ollama -Pllm_enabled=true -Pbrain_capability_mode=local_llm_dev -Pollama_base_url=http://127.0.0.1:11434 -Pollama_model=qwen2.5:3b
```

Build flag meanings:

- `brain_provider=mock | ollama`
- `llm_enabled=true | false`
- `brain_capability_mode=offline_only | online_assisted | local_llm_dev`
- `ollama_base_url=http://127.0.0.1:11434`
- `ollama_model=qwen2.5:3b`

Gemma / phone-local runtime flags:

- `gemma_enabled=true | false`
- `gemma_model_asset_path=<path or asset path>`
- `gemma_max_tokens=512`
- `gemma_temperature=0.2`
- `gemma_top_k=40`
- `gemma_context_window=8192`
- `gemma_role_enabled=true | false`

## 4. Run the Brain Smoke Test

The debug build includes a broadcast receiver that runs a curated phrase list through `BrainService` and logs:

- user input
- provider used
- raw model response
- parsed BrainAction
- validator result
- fallback usage
- final safety decision

Example broadcast:

```powershell
adb shell am broadcast -a com.nova.luna.debug.ACTION_RUN_BRAIN_SMOKE
```

Watch the output with:

```powershell
adb logcat -s NovaLunaBrainSmoke
```

## 5. Switch Back to Mock

To return to the deterministic local mock provider, rebuild with:

```powershell
./gradlew.bat :app:assembleDebug --no-daemon -Pbrain_provider=mock -Pllm_enabled=false -Pbrain_capability_mode=offline_only
```

You can also keep the default `gradle.properties` values, which already point the project at the mock provider.

## Safety Reminder

- The local LLM only produces structured `BrainAction` JSON.
- `SafetyGate` still decides whether any action can proceed.
- The executor still blocks irreversible final actions such as payment, OTP, login, and final booking.
- The Ollama path is a development aid only and is not the production brain.
- The phone Gemma runtime must still pass `BrainActionValidator` and `SafetyGate` before any action reaches execution.
