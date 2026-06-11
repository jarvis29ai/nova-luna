# Architecture

## Overview

Nova / Luna is a phone-first assistant system designed to run locally on device as much as possible. Nova is the male assistant. Luna is the female assistant.

The default architecture should stay offline-first, with zero backend cost unless a future backend is explicitly requested.

## Main Pieces

- Flutter app for the primary front end
- Local voice input layer for speech capture and command intake
- Local voice output layer for spoken replies using Android TextToSpeech
- Nova and Luna are local TTS profiles that tune pitch and speech rate only; exact voice availability still depends on the installed Android TTS engine
- Local structured BrainService layer that turns user text into a JSON-shaped BrainAction before anything is executed
- Phone-only multi-model brain routing with `BrainRouter` selecting the ready local role first (`GemmaBrainModel`, `ActionJsonModel`, `LiteCommandModel`, `ScreenUnderstandingModel`) and using the guaranteed `LocalMockBrainProvider` fallback only when no local model is ready
- `BrainRouterTrace` records routing honesty for each decision, including the selected model role, whether mock fallback was used, why fallback happened, and whether real native inference and strict JSON parsing were attempted and succeeded
- Optional online AI helper abstraction for consent-gated research and drafting flows using ChatGPT/Gemini/Claude-style providers, with read-only or draft-only results only
- `PhoneLocalLlmRuntime` is the shared phone-local reasoning bridge. It resolves the configured model stack, checks asset readiness, builds the strict `BrainAction` prompt, parses strict JSON output, and reports structured readiness diagnostics
- `LiteLocalModelRuntime` and the native GGUF bridge now perform real local load/generate calls through llama.cpp. They still surface `tokenizer_loaded`, `vocab_size`, `tokenization_ok`, and `token_ids_preview` only when the GGUF metadata and vocabulary mapping were actually parsed and verified, but successful native runs now also report `load_ms`, `generation_ms`, model load/generation counts, prompt/generated token samples, parsed intent/risk, and finish reason instead of pretending generation never exists
- The debug-only diagnostic broadcast receiver now bypasses `LocalMockBrainProvider`, prefers the `command` extra, and drives the native GGUF load/generate path directly so phone logs can show the exact model path, enablement state, reuse counts, and tokenizer/generation proof status
- `PhoneGemmaRuntime` is the production phone reasoning scaffold for Gemma-style local models, and `GemmaBrainModel` delegates to it when runtime readiness passes
- Signed model pack sources are kept outside the APK and are injected from local `local.properties` or Gradle project properties through `ModelSourceManifest` and `ModelDownloadSourceProvider`; the app stays `NOT_CONFIGURED` until a real URL, SHA-256, and byte size are supplied locally, and imported/downloaded packs live in app-private `model_install` storage instead of `assets` or `res/raw`
- Debug builds can import already-downloaded model files through `ModelImportReceiver`, which copies the file into app-private storage, verifies SHA-256, and only then promotes the pack to `READY`
- Universal local memory and session management with `BrainMemoryStore`, `BrainSessionManager`, `BrainMemorySnapshot`, `PendingConfirmation`, `RecoveryState`, and `MemoryRedactor` so active sessions, confirmations, recovery hints, and preferences stay on-device
- The deterministic `AgentLoop` / `TaskLoopCoordinator` is the bounded read-decide-safety-execute-verify-recover loop for safe multi-step tasks, with retry limits, stuck detection, recovery policies, completion detection, and memory updates
- `ActionJsonModel` creates strict safe BrainAction JSON for cab, food, and task planning and stays validator-bound even if Gemma reasoning is supplied later
- `LiteCommandModel` handles fast offline commands like stop, cancel, go home, and open app
- Accessibility-first screen understanding built on `NovaAccessibilityService`, `AccessibilityNodeUtils`, `ScreenStateReader`, `ScreenStateAnalyzer`, `ScreenRecoveryAdvisor`, and `ScreenStateVerifier`
- `ScreenUnderstandingModel` is a deterministic read-only screen analysis model that summarizes the current UI from local accessibility snapshots and keeps execution gated by `BrainActionValidator`, `SafetyGate`, and `ActionExecutor`
- No backend, cloud server, or paid API is required for production
- Local task engine for routing commands and assistant actions
- Local cab-booking orchestration for pickup/drop collection, current-location resolution, provider launch, provider-specific destination handling, fare comparison, and explicit user-confirmed handoff
- Local food-ordering orchestration for item parsing, provider launch, quote comparison, coupon probing, and explicit user-confirmed handoff
- Local grocery-booking orchestration for item parsing, basket changes, provider launch, provider comparison, coupon handling, cart total comparison, and explicit user-confirmed handoff
- First-class control command path for stop/cancel style commands that safely shut down listening
- Local memory and preferences for persona, settings, and lightweight state
- Memory state is universal but still local-first: active sessions, pending confirmations, screen snapshots, recovery state, and preferences all stay on-device and must be redacted before storage
- Optional smartwatch companion later for quick commands and watch-first conveniences

## Phase 22 Update (Multi-model brain roles)

- The brain is now multi-model capable, supporting three distinct active roles: `CORE_BRAIN`, `LITE_FALLBACK`, and `MULTILINGUAL_BACKUP`.
- `ModelRuntimeManager` handles the lifecycle of loaded models, ensuring only one large model is active at a time to prevent OOM errors on RAM-constrained devices.
- `ModelRamGuard` performs pre-load checks using `RamInfoProvider` (Android MemoryInfo) to decide if a requested model role is safe to load or if a smaller fallback is required.
- `BrainRouter` uses refined heuristics for role selection:
    - `MULTILINGUAL_BACKUP` preferred for Devanagari script or Hindi/Hinglish keywords.
    - `LITE_FALLBACK` preferred for simple, short, or control-like commands.
    - `CORE_BRAIN` preferred for complex reasoning and planning.
- Diagnostics now include comprehensive `ModelRuntimeSessionTrace` information: switching counts, unload/load history, RAM guard decisions, and honest fallback reasons.
- `LocalBrainModelClient` is now wired to the runtime manager, allowing it to capture and report real-time loading/unloading traces for every inference attempt.

## Phase 21 Update (Model Install / Path System)

- A production-style model install and path management system is now implemented to ensure model file readiness before reasoning.
- `ModelInstallService` acts as the central coordinator for detecting, verifying, and repairing local model installations.
- `ModelPathResolver` implements a prioritized path resolution strategy: (A) Verified stored path, (B) Debug override path, (C) Default internal app storage, (D) External files directory.
- `ModelInstallVerifier` performs physical verification of model files, including existence, readability, extension checks (.gguf, .bin), minimum size requirements, and streaming SHA-256 hash validation.
- The system maintains strict honesty: a model is only marked as `READY` if it physically passes all verification steps. Missing or corrupted files trigger a `NOT_READY` state with clear diagnostic reasons.
- `ModelInstallDiagnostics` provides full transparency into the model's status, including exact path, physical existence, size in bytes, and SHA verification results.
- `ModelRuntimeLoader` now depends on verified paths from the install service, ensuring that native reasoning runtimes never attempt to load missing or invalid assets.
- Support for "repair" flows is included, allowing the system to automatically re-verify and update saved paths if a valid model is found in default storage locations.
- Model downloading remains marked as `NOT_IMPLEMENTED` to preserve architectural honesty until a full HTTP download + persistence layer is added.

## Phase 20 Update (BrainRouter Real Model Flow)

- When a real local model is ready, `BrainRouter` now selects that real model role for command handling instead of silently dropping to the mock fallback path.
- Mock fallback is reserved for not-ready states only, and the routed result now explains why fallback was used.
- Local runtime responses keep strict JSON honesty: JSON parsing is attempted, parse success or failure is recorded, and invalid model text is preserved as decoded output instead of being treated as a valid action.
- `SafetyGate` remains the final execution gate after BrainRouter output, so a ready model still cannot bypass safety checks.
- Phase 20 is implemented and verified in the current codebase; later model download/path, RAM switching, and UI/voice phases are still intentionally out of scope.

## Phase 1: Hands / Real Phone Control

Phase 1 enables Nova / Luna to perform real actions on the Android device using `AccessibilityService`. This system is designed with a safety-first approach where direct execution is decoupled from reasoning.

### Execution Chain
1. **Command Intake**: User provides a command via text or voice.
2. **Brain Routing**: `BrainService` and `BrainRouter` map the command to a candidate `BrainAction`.
3. **Safety Evaluation**: `SafetyGate` inspects the candidate action for risks (payments, data deletion, etc.).
4. **Action Formalization**: The approved `BrainAction` is converted to a canonical `CommandIntent`.
5. **Centralized Execution**: `ActionExecutor` receives the `CommandIntent`, verifies `NovaAccessibilityService` readiness, and performs the action.
6. **Result Reporting**: Every action returns a structured `CommandResult` with a specific `ActionResultStatus`.

### Key Components
- **Action Contract (`CommandIntent`)**: A structured object containing action ID, type, targets (app, label), risk levels, and retry policies.
- **Result Contract (`CommandResult`)**: Includes status (`SUCCESS`, `FAILED`, `BLOCKED`, `NEEDS_CONFIRMATION`, `NOT_FOUND`, `TIMEOUT`, `PERMISSION_REQUIRED`), technical reasons, and screen snapshot summaries.
- **Hands Bridge (`NovaAccessibilityService`)**: Performs tap, type, scroll, and navigation actions. It includes fallback logic to find clickable ancestors if a target element is not directly clickable.
- **Eyes Bridge (`ScreenStateReader`)**: Captures real-time screen snapshots to inform the brain and verify action success.
- Retry Logic: `ActionExecutor` implements a 2-retry policy for UI interactions, including automatic scrolling to find elements that are not immediately visible.

## Phase 2: Ears / Voice Input

Phase 2 gives Nova / Luna the ability to hear and understand voice commands using a tap-to-speak flow.

### Voice Input Chain
1. **Trigger**: User taps the "Tap to Speak" button in the app.
2. **Permission Check**: `VoiceInputController` verifies `RECORD_AUDIO` permission.
3. **Capture**: `SpeechRecognizer` (via `AndroidSpeechRecognizerWrapper`) captures audio and provides real-time partial results.
4. **Normalization**: `VoiceCommandNormalizer` cleans the final transcript by stripping wake words ("Luna", "Nova", etc.) and normalizing whitespace.
5. **Handoff**: The cleaned command is sent to `AssistantSession`, which tracks the source as `VOICE`.
6. **Execution**: The command is processed by `CommandBrain` and routed to Phase 1 "Hands" if an action is approved.

### Key Components
- **Voice Input Contract (`VoiceInputModels.kt`)**: Defines `VoiceInputState`, `VoiceInputResult`, and `VoiceInputError` for structured communication between UI and the voice layer.
- **Voice Controller (`VoiceInputController.kt`)**: Manages the `SpeechRecognizer` lifecycle, handles state transitions, and provides a mockable interface for testing.
- **Command Normalizer (`VoiceCommandNormalizer.kt`)**: Logic for wake-word removal and transcript cleaning (supports English and Hindi wake-word variants).
- **Assistant Session (`AssistantSession.kt`)**: A unified entry point for all commands (TEXT or VOICE), ensuring consistent handoff to the brain.

### Safety & Privacy
- **No Always-On**: Voice capture is one-shot and user-triggered only (no background listening in Phase 2).
- **Privacy First**: Raw audio is never saved. Transcripts are handled locally and redacted where possible before logging.
- **Consent-Gated**: Online AI helpers are only used if the user has given explicit consent.

## Phase 3: Mouth / Voice Response

Phase 3 gives Nova / Luna the ability to speak clearly and safely during the assistant lifecycle using Android Text-to-Speech (TTS).

### Voice Response Chain
1. **Trigger**: `AssistantSession`, `ActionExecutor`, or `VoiceInputController` emits a state change or result.
2. **Template Mapping**: `VoiceResponseTemplates` maps the state/status to a short, useful voice line.
3. **Sanitization**: `VoiceResponseSanitizer` masks sensitive data (OTPs, card numbers, emails) before speech.
4. **Coordination**: `VoiceResponseManager` handles speech priority, duplicate suppression, and interruption rules.
5. **Execution**: `TextToSpeechManager` (via `android.speech.tts.TextToSpeech`) performs the actual speech synthesis.

### Key Components
- **Voice Response Contract (`VoiceResponseModels.kt`)**: Defines structured models for states (`SPEAKING`, `COMPLETED`, etc.), types, requests, and results.
- **Response Manager (`VoiceResponseManager.kt`)**: Central coordinator that enforces muting settings, priorities, and interruption logic.
- **Sanitizer (`VoiceResponseSanitizer.kt`)**: Implements privacy rules to ensure sensitive information is not spoken aloud by default.
- **Template System (`VoiceResponseTemplates.kt`)**: Maps technical statuses (e.g., `NOT_FOUND`, `NEEDS_CONFIRMATION`) to user-friendly spoken feedback.

## Phase 4: Face / Futuristic Popup UI

Phase 4 gives Nova / Luna a "face"—a futuristic popup UI that visualizes the assistant's internal state and provides clear controls for user interaction and safety.

### Popup Interaction Flow
1. **Trigger**: User opens the app or taps the mic.
2. **State Observation**: `AssistantPopupController` subscribes to granular lifecycle events from `AssistantSession`.
3. **State Mapping**: `AssistantPopupStateMapper` converts session, voice, and action results into a unified `AssistantPopupUiModel`.
4. **Visual Update**: The UI transitions between modes (Orb for idle, Panel for active) and displays transcripts, thinking indicators, or action labels.
5. **Safety Confirmation**: For risky actions, the popup displays a high-visibility confirmation box with "Continue" and "Cancel" buttons.
6. **User Action**: Interactions like mic taps or button clicks are routed back to `AssistantSession` or `VoiceInputController`.

### Key Components
- **Popup Contract (`AssistantPopupModels.kt`)**: Defines canonical UI states (`LISTENING`, `THINKING`, `NEED_CONFIRMATION`, etc.) and event models.
- **Popup Controller (`AssistantPopupController.kt`)**: Manages the UI lifecycle, view binding, and visibility transitions.
- **State Mapper (`AssistantPopupStateMapper.kt`)**: Translates various back-end states into user-friendly UI configurations.
- **Multi-Listener Session**: `AssistantSession` is enhanced to support multiple concurrent listeners, ensuring synchronization between the main UI and the popup.

## Phase 5: Unified Model Integration

Phase 5 consolidates all specialized task models (Food, Cab, Shopping, etc.) into a single, cohesive assistant experience. User commands are automatically routed to the correct domain without explicit model selection.

### Unified Routing Chain
1. **Intake**: `AssistantSession` receives the command from `VoiceInputController` or a text field.
2. **Domain Detection**: `UnifiedDomainRouter` queries all registered `DomainHandlers` (Food, Cab, Media, etc.) for match confidence.
3. **Selection**: The router selects the domain with the highest confidence score (using thresholds like 0.9 for direct matches).
4. **Contextual Continuity**: The `AssistantContext` maintains the active domain and session state, allowing for natural follow-up commands (e.g., "proceed" in a food order).
5. **Action Generation**: The selected handler produces a candidate `CommandIntent`.
6. **Unified Flow**: The command proceeds through the established validation, safety gating, and execution path.

### Key Components
- **Unified Domain Router (`UnifiedDomainRouter.kt`)**: The central hub for automatic domain selection and disambiguation.
- **Domain Handler Interface (`DomainHandler.kt`)**: A common contract for all task models, enabling modular registration and scoring.
- **Assistant Context (`AssistantContext.kt`)**: Tracks short-lived session state, including active domains and last route decisions.
- **Integrated Brain (`CommandBrain.kt`)**: Refactored to act as the primary orchestrator for the unified routing and execution flow.

### Safety & Continuity
- **Automated Disambiguation**: If confidence is low, the system automatically asks for clarification rather than executing a wrong action.
- **Session-Aware Routing**: Active domain sessions (e.g., an ongoing cab booking) are prioritized for follow-up commands.
- **Single Authority**: `ActionExecutor` remains the only component capable of triggering device actions, and all actions must still pass `SafetyGate`.

## Phase 6: Real Phone Testing + Stabilization

Phase 6 hardens the assistant prototype for real-world use on Android devices, focusing on the stabilization of 10 core demo flows and rigorous safety validation.

### Stabilization Strategy
1. **Flow Hardening**: Each of the 10 core flows (Open App, Play Music, YouTube Search, etc.) is tested and refined for reliability and repeatability.
2. **Safety Pattern Refinement**: `SafetyGate` patterns are expanded to handle natural language variations, ensuring that risky final actions (e.g., "order now", "send it") always trigger a confirmation request.
3. **Robust Domain Routing**: Match confidence scores and session prioritize logic in `UnifiedDomainRouter` are tuned to prevent domain collisions and improve accuracy.
4. **Fallback Integrity**: `CommandBrain` is verified to correctly fall back to the rule-based parser or agent loop when unified routing confidence is low.

### Verified Demo Flows
1. **Open App**: Fast, safe opening of any installed app.
2. **Play Music**: Search and playback on Spotify, YouTube Music, etc.
3. **YouTube Search**: Multi-step search and play flow.
4. **Scroll/Select Media**: Voice-controlled UI interaction on media apps.
5. **Read/Summarize Message**: Safe reading and summarization of communication content.
6. **Content Prompt**: Intelligent prompt generation for PPTs, documents, and images.
7. **Food Order**: Complete search-to-confirmation flow for food delivery.
8. **Grocery Compare**: Cross-app price comparison and cart management.
9. **Cab Booking**: Reliable fare finding and booking confirmation.
10. **Shopping Compare**: Budget-aware product search and deal comparison.

### Safety & Reliability
- **Mandatory Gating**: Every command—regardless of source—is evaluated by the `SafetyGate` before execution.
- **Visual Transparency**: The futuristic popup UI provides real-time feedback on every step of the execution chain.
- **Zero Auto-Confirmation**: Risky operations are impossible to hide or execute without explicit user authorization.

## Architecture Rules

- **Non-Execution**: The UI cannot directly call `ActionExecutor` or `AccessibilityService`.
- **Mandatory Confirmation**: Risky actions (e.g., ordering, sending messages) must trigger a `NEED_CONFIRMATION` UI state.
- **Decoupled Resolution**: Confirmation buttons route through the `AssistantSession` confirmation handler, ensuring full brain/safety logic is applied.

## Architecture Rules

- **Privacy First**: Sensitive patterns like OTPs and card numbers are masked as "a code" or "a card number" before being spoken.
- **Local TTS**: Speech synthesis is performed entirely on-device using the installed Android TTS engine.
- **Gated Speech**: Voice responses can be enabled/disabled via user settings. High-priority safety blocks and confirmations can interrupt ongoing non-critical speech.

## Architecture Rules



- Prefer on-device logic before any remote service.
- Do not add a backend by default.
- Do not introduce paid APIs by default.
- Do not require a backend, server, or cloud API for the current architecture.
- Keep voice output on local Android TextToSpeech rather than cloud TTS services.
- Keep app structure clean and testable.
- Keep Nova/Luna separate from Jarvis language or workflows.
- Route every executable action through SafetyGate before it reaches ActionExecutor.
- Let BrainService decide the intent shape, but never let it bypass command routing or safety checks.
- Keep local LLM output opt-in, local-only, and strictly structured; reject invalid JSON or dangerous final actions before routing.
- Keep optional online AI helper output consent-gated, privacy-filtered, read-only or draft-only, and never let it control the phone directly.
- Keep Ollama-compatible desktop LLMs dev-only and outside the production brain path.
- Keep the phone-only model roles local-first and keep `LocalMockBrainProvider` as the guaranteed fallback only when a phone model is unavailable or not ready.
- If a phone-local model is ready, route to the real native runtime path instead of silently pretending the mock fallback was chosen.
- Keep phone-local model assets readiness-checked before the brain path can emit structured actions.
- Keep `SafetyGate` as the final authority before any action reaches the executor.
- Keep screen understanding local, accessibility-based, and read-only; do not replace it with OCR, cloud vision, or another backend dependency by default.
- Keep the agent loop deterministic, bounded, and recovery-first; stop or hand off on repeated screens, sensitive prompts, or completion states rather than spinning forever.
- Keep phone-only runtime modes explicit so offline-first behavior remains the default and optional online behavior stays lookup-only, consent-gated, and helper-only.
- Treat stop and cancel as safe control actions that can end listening cleanly.
- Do not finalize a cab booking without explicit user confirmation.
- Do not finalize a food order without explicit user confirmation.
- Do not finalize a grocery booking without explicit user confirmation.
- Do not bypass OTP, login, payment, or CAPTCHA screens.
- Preserve active food session continuity across follow-up replies until the session is completed or cancelled.
- Preserve active cab session continuity across follow-up replies until the session is completed or cancelled.
- Preserve active grocery session continuity across follow-up replies until the session is completed or cancelled.
- Keep assistant personality, voice, and automation logic aligned with the product mission.

## Data And State

- Use local storage for preferences and lightweight memory.
- Keep cab-booking session state transient and local to the device.
- Keep food-ordering session state transient and local to the device.
- Keep grocery-booking session state transient and local to the device.
- Keep BrainAction structured data transient and local to the device unless a future memory feature explicitly stores it; if a future memory feature exists, it must stay local-first and redacted.
- Keep LLM provider configuration local and explicit (`brain_provider`, `llm_enabled`, `ollama_base_url`, `ollama_model`).
- Keep capability mode configuration local and explicit (`brain_capability_mode`).
- Resolve current-location pickup locally when location permission and a last-known location are available.
- Keep sensitive data handling explicit and minimal.
- Avoid hidden synchronization paths.

## Roadmap Shape

1. Stabilize the phone-first assistant core.
2. Strengthen voice command, voice reply, and task automation flows.
3. Add tests and documentation for every meaningful behavior change.
4. Add smartwatch support only after the phone-first flow is stable.
5. Keep the system zero-cost and local-first unless requirements change.
6. Keep phone-only runtime prep documented in [`docs/PHONE_ONLY_RUNTIME.md`](PHONE_ONLY_RUNTIME.md).
