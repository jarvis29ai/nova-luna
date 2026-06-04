# Phone-Only Runtime

Nova / Luna is being prepared as a phone-first assistant that can run without any backend, server, or paid cloud API.
Production does not require Ollama or any desktop LLM; the phone-only brain can stay local with the mock fallback until a real on-device model is ready. The phone Gemma runtime scaffold is now the production direction, while Ollama remains a dev-only path.

## Runtime Modes

- `OFFLINE_ONLY`
  - Uses on-device logic and the guaranteed local mock fallback
  - No internet dependency is required
  - Safe default for the phone build
- `ONLINE_ASSISTED`
  - Can treat internet as a helper for information lookup
  - Never bypasses `SafetyGate`
  - Never turns internet access into autonomous execution
- `LOCAL_LLM_DEV`
  - Used for laptop or desktop local testing with an Ollama-compatible endpoint
  - This is a legacy development path, not a production requirement
  - Still local-first and still routed through the same safety chain

## Offline vs Online Behavior

- Offline mode keeps command parsing, cab flows, and safety checks working locally.
- Online-assisted mode can only influence information lookup behavior in the future.
- The phone-only brain stack now routes through `BrainService -> BrainRouter -> selected model role -> BrainAction candidate -> BrainActionValidator -> fallback -> CommandRouter -> SafetyGate -> ActionExecutor`.
- Dangerous actions such as payment, OTP, login, final booking, send money, delete, and purchase confirmation remain human-only.
- `GemmaBrainModel` is the intended final on-device reasoning role.
- `PhoneGemmaRuntime` exposes readiness status for the on-device Gemma path, and `GemmaBrainModel` only uses it when the runtime is ready.
- `ActionJsonModel` creates strict safe BrainAction JSON for cab, food, and task planning.
- `LiteCommandModel` handles fast offline commands such as stop, cancel, go home, and open app.
- `ScreenUnderstandingModel` is reserved for future read-only screen analysis.

## Local Model Options for the Future

The codebase is being prepared so a real on-device or phone-adjacent model can be dropped in later without rewriting the safety chain. Possible local options include:

- TFLite
- MediaPipe
- `llama.cpp` on Android
- ONNX Runtime
- Small quantized Qwen or Gemma variants

## No Backend Requirement

- No server is required for the current architecture.
- No cloud API is required for the current architecture.
- No desktop LLM is required for production.
- The guaranteed fallback remains `LocalMockBrainProvider` so the phone can keep working offline.
- `SafetyGate` remains the final authority before any action reaches `ActionExecutor`.
- `ActionJsonModel` stays strict JSON-only and never lets any model bypass `BrainActionValidator`.
