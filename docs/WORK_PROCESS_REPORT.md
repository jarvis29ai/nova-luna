# Work Process Report

## Progress Snapshot

- Current app readiness: green for the audited scope on 2026-06-11, including the Phase 20 BrainRouter real model flow, the Phase 19 native GGUF generation bridge, the Phase 7 safe agent loop, and the Phase 6 universal memory/session brain layer
- `:app:testDebugUnitTest --tests com.nova.luna.brain.*` and `:app:assembleDebug` passed in this audit pass
- `docs/NOVA_LUNA_FULL_PROJECT_MODEL_FLOW_AUDIT_REPORT.md` records the current full-model verification
- The model-pack workflow keeps binaries out of APK resources, stores packs in app-private `model_install` storage, and exposes a debug-only import receiver for local file imports
- The Phase 19 native GGUF generation path now keeps backend honesty strict: `tokenizer_loaded`, `vocab_size`, `tokenization_ok`, and `token_ids_preview` only surface when the GGUF metadata and vocabulary mapping were actually parsed, and successful runs now also report `load_ms`, `generation_ms`, model load/generation counts, prompt/generated token samples, parsed intent/risk, and finish reason instead of pretending generation never happened
- The debug diagnostic broadcast now goes straight to the native GGUF load/generate path, prefers the `command` extra over the older `request` extra, and logs the exact lite model path plus the missing/disabled/reuse reason before it decides whether a native load is possible
- The Android native build now forces CMake try-compile onto the static-library path to work around an arm64 `cmTC_9ff68` configure stall during external native build setup
- The Android native build also pre-seeds CMake compiler-work cache values on Windows so the bundled Ninja/CMake configure step can skip the stalled ABI probe after manual compiler verification

## Phase 22 Update (Multi-model brain roles)

- The brain is now capable of multi-model orchestration, supporting `CORE_BRAIN` (reasoning), `LITE_FALLBACK` (fast commands/low-RAM), and `MULTILINGUAL_BACKUP` (Hindi/Hinglish).
- `ModelRuntimeManager` and `ModelRamGuard` now provide deterministic model switching and lifecycle management. The system unloads previous models when switching to maintain a low RAM footprint on Android.
- Heuristic role selection logic is integrated into `BrainRouter`:
    - Language detection handles Devanagari script and multilingual keywords.
    - Command complexity classification separates simple control tasks from reasoning/planning.
- Diagnostics now show real-time session traces, including RAM guard decisions, switching counts, and honest fallback reasons.
- Successfully verified 187/187 unit tests, including new Phase 22 integration tests for role selection, fallback scenarios, and RAM management.

## Phase 21 Update (Model Install / Path System)

- A production-style model install and path management system is now implemented, ensuring model file readiness and architectural honesty.
- `ModelInstallService` now manages model metadata via a new `ModelInstallSpecRegistry`, supporting "core", "lite", and "full" model roles with specific size and extension requirements.
- `ModelPathResolver` handles prioritized path discovery, while `ModelInstallVerifier` performs physical file checks (existence, readability, size, extension) and streaming SHA-256 verification.
- `BrainService` and `ModelRuntimeLoader` are now wired to the new install service. Native runtimes only load from verified paths, and diagnostics now include Phase 21 honesty fields (exact path, physical status, size, SHA verification reason).
- The system correctly handles "repair" scenarios, automatically updating broken stored paths if a valid model exists in default internal storage.
- All 187 project unit tests (including Phase 7-20 regressions and new Phase 21 cases) passed successfully.
- Model download remains marked as `NOT_IMPLEMENTED_PHASE_21` to maintain honesty until a full download layer is added.

## Phase 20 Update (BrainRouter Real Model Flow)

- `BrainRouter` now selects a real local model role for ready commands instead of silently falling back to the mock path when a native model is available.
- Mock fallback is now reserved for not-ready states only, and the fallback reason is surfaced explicitly when it is used.
- Router diagnostics are now carried through `BrainDiagnostics` as `BrainRouterTrace`, including the selected model role, mock fallback usage, fallback reason, real model invocation, and JSON parse honesty fields.
- Strict JSON honesty remains intact: real native generation still records parse attempts and success/failure separately, and invalid text is preserved instead of being treated as a valid action.
- `SafetyGate` still runs after router output, so a ready model never bypasses the final safety check.
- Validation for this phase passed with `:app:testDebugUnitTest --tests com.nova.luna.brain.*` and `:app:assembleDebug`.

## Current Setup

- Nova / Luna is a phone-first assistant project.
- Nova is the male assistant and Luna is the female assistant.
- The codebase should stay local-first and offline-first by default.
- This repository snapshot is native Android plus a Wear OS scaffold; Flutter is not present in the checked-in source tree.
- Smartwatch support is a later companion goal, not the default starting point.

## How Agents Should Work

- The owner gives high-level tasks to Codex.
- Codex inspects the project, plans the work, and decides whether helpers are needed.
- Codex acts as the main contractor and final decision maker.
- Gemini CLI is a reviewer and research helper only.
- OpenCode handles local edits, file creation, small bugs, and repetitive refactors.
- Cursor is optional visual IDE and rules support.
- Agents should preserve existing project direction and avoid unrelated changes.
- Codex reports changed files, validation, and the next step.

## Verified Command Families

- Open app.
- Tap / click / press.
- Scroll / swipe / move up-down.
- Open settings.
- Open accessibility settings.
- Open usage access settings.
- Type / write / enter / input text.

## Safety Notes

- Deterministic JVM tests only.
- Mocked `NovaAccessibilityService` and mocked launchers are used in tests.
- No real screen taps, typing, settings launches, or permission grants happen in tests.
- Debug cab smoke now resets the foreground UI to Home before the preflight snapshot and between scenarios so stale provider screens do not leak across smoke cases.
- Debug brain smoke can be triggered in the debug build with `com.nova.luna.debug.ACTION_RUN_BRAIN_SMOKE` and logs the selected provider, raw response, parsed BrainAction, fallback usage, and final safety decision.
- Debug command smoke can be triggered in the debug build with `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` and records exact command handling for basic, cab, food, grocery, and negative safety phrases. The debug receiver also writes a cached report in app storage so the phone-side results can be reviewed deterministically. The latest sectioned rerun landed as basic PASS, cab BLOCKED_BY_LOCATION_PERMISSION, food BLOCKED_BY_PROVIDER_UI, grocery PASS, and negative PASS.
- The brain runtime now tracks phone-only capability modes, role-based routing, the shared phone-local LLM stack, model readiness, strict JSON parsing, and online-assisted lookup-only preparation without adding a backend.
- The screen understanding brain now uses deterministic accessibility snapshots, local screen-state analysis, and post-action screen verification instead of OCR, cloud vision, or any backend dependency.
- `flutter_app/` remains untouched and must not be added yet.
- Usage-access settings remains explicit and safety-aware.
- Sectioned command smoke reruns are used when a single full pass would otherwise stall on a later section, so each family can be verified independently without changing production behavior. In the latest run, the cab flow reported missing location permission cleanly, the food flow still stopped because supported search/cart controls were not available, and the grocery flow now dismisses the final-confirmation state cleanly on cancel.

## Phase 4 Update (Phone-Local LLM)

- The phone-local model bridge is now implemented with `PhoneLocalLlmConfig`, `ModelAssetLocator`, `ModelReadinessChecker`, `PhoneLocalLlmPromptBuilder`, `PhoneLocalLlmOutputParser`, `PhoneLocalLlmRuntime`, and `PhoneLocalLlmProvider`.
- Local reasoning requests stay strict: the runtime only accepts `BrainAction` JSON, rejects malformed or oversized prompts, and still routes every result through `BrainActionValidator` and `SafetyGate`.
- `BrainService` now records local model readiness, prompt build status, JSON parse success, and model latency in diagnostics so the local path is visible instead of hidden.
- `LocalMockBrainProvider` remains the guaranteed fallback, which keeps the app usable offline when a configured model is missing, disabled, or unsafe.
- `PhoneGemmaRuntime` now delegates through the shared phone-local runtime bridge rather than acting as a separate parallel path.

## Phase 5 Update (Optional Online AI Helper)

- An optional online AI helper layer is now wired through `BrainService`, `BrainRouter`, and `CommandBrain` for consent-gated research and drafting requests.
- Supported online helper providers are abstracted so ChatGPT, Gemini, and Claude-style integrations can be added later without changing the command pipeline.
- Online helper output stays read-only or draft-only, privacy-filtered, and never controls the phone directly.
- Offline-first remains the default because the helper is disabled by config unless explicitly enabled.
- Pending online requests now wait for explicit user consent before replaying through the helper path.
- Phase 1 through Phase 4 routing stays intact after regression testing, including cab, grocery, navigation, content, and local LLM fallback flows.
- New tests cover the helper policy, privacy filter, prompt builder, provider factory, helper fallback behavior, router selection, and consent replay.
- Validation for this phase passed with `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug`.

## Phase 6 Update (Universal Memory and Session Brain)

- A universal local memory/session layer is now wired through `BrainMemoryStore`, `BrainSessionManager`, `BrainMemorySnapshot`, `PendingConfirmation`, `RecoveryState`, and `MemoryRedactor` so active sessions, confirmations, recovery hints, screen snapshots, and preferences remain on-device.
- `BrainService`, `BrainRouter`, `CommandBrain`, `SafetyGate`, and `ActionExecutor` now carry memory context through the live brain path so follow-up prompts, confirmed actions, and session continuity stay consistent across phases.
- Confirmed online-helper requests now replay safely through the helper path, while stale confirmations are rejected instead of being reused against a different request.
- New regression tests cover redaction, session state, confirmation resolution, follow-up resolution, router memory routing, command replay, and diagnostics memory surfaces.
- Validation for this phase passed with `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `:app:assembleDebug`.

## Phase 7 Update (Local LLM Runtime)

- **Local LLM Stack**: Integrated a hierarchical local LLM runtime supporting Gemma 3n (4-bit), Qwen 3 Small (Hinglish/Multilingual), and lightweight fallbacks like Phi-4 mini for low-memory devices.
- **Rule-First Intelligence**: Implemented a "Rule-first" routing policy where local LLMs are invoked only when rule-based confidence falls below 0.75, ensuring maximum speed for deterministic tasks.
- **JSON Candidate Bridge**: Built a strict prompt-to-JSON bridge that extracts actionable `BrainAction` candidates from LLM output, preventing the model from outputting prose or uncontrolled text.
- **Hierarchical Safety**: Hardened the `SafetyGate` to treat LLM candidate actions as high-risk by default, subjecting them to rigorous pattern matching, biometric requirements, and user confirmations.
- **Contextual Session Bridging**: Refactored `CommandBrain` to bridge active executor orchestrator states with memory session types, ensuring seamless continuity for playback control, drafting, and complex task planning.
- **Real-time Feedback**: Enhanced the `AssistantPopup` and `AssistantSession` with "Thinking" and "Routing" states, providing immediate visual and audio feedback during LLM reasoning.
- **Validation**: Achieved a 100% pass rate across the full unit test suite (465/465 tests), resolving regressions in stability, memory, and cross-brain session persistence.

## Verified Test Command

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat :app:installDebug --no-daemon
```

## Coordination Rules

- Read `AGENTS.md` first when available.
- Keep Nova/Luna separate from Jarvis or any unrelated workflow.
- Prefer small, practical changes over broad rewrites.
- Keep token use and scope disciplined.
- Update docs when architecture or process changes.

## Next Build Steps

1. Improve food provider screen discovery so the comparison step can advance when supported apps are installed.
2. Keep cab current-location handling honest when location permission is missing and continue provider-screen continuation work once permission is granted.
3. Keep the sectioned smoke docs synchronized with the actual phone results.
4. Add or improve tests around the most important assistant behaviors as new fixes land.
5. Extend the screen-understanding recovery prompts only if new accessibility states show up in real device testing.
6. Continue device-level tuning for the phone-local LLM stack, especially model asset packaging, prompt limits, and latency on target hardware.

## Verification Checklist

- Project still reads as Nova/Luna, not Jarvis.
- No backend is introduced by default.
- No paid service is required by default.
- Voice command flow remains local-first.
- Voice reply flow remains local-first.
- Tests are added or updated for behavior changes.
- Docs stay aligned with the codebase.
- Folder structure stays clean and understandable.
- `flutter_app/` stays untracked until explicitly added later.
- Local LLM setup instructions live in `docs/LOCAL_LLM_SETUP.md`.
- Phone-only runtime notes live in `docs/PHONE_ONLY_RUNTIME.md`.

## Communication Model Update (Phase 2.0)

- **Gmail Integration**: Implementing safe reading and search using Android `AccountManager` and OAuth2 (`gmail.readonly`).
- **Local-First**: No backend used; tokens and data stay on-device.
- **Safety**: Draft handoff remains user-visible; no auto-send.
- **Redaction**: Gmail content is subject to OTP/password redaction.

## Communication Model Update (Phase 1.2)

- The Communication Model has been fully implemented, hardened with real local integrations, and frozen.
- **Real SMS Reader**: Implemented using `ContentResolver` and gated by `READ_SMS`.
- **WhatsApp/Telegram Reader**: Implemented using `NovaAccessibilityService` notification snapshots (safe surfaces only).
- **Draft Handoff**: Implemented via Android `Intents` (`ACTION_SEND`/`ACTION_SENDTO`) for safe user-confirmed dispatch.
- **Crash-Free Safety**: Verified that missing permissions, services, or target apps return clean `BLOCKED` or `FAILED` states instead of crashing.
- **Build System**: Added Foojay JDK resolver to `settings.gradle` to ensure JDK 17 availability for the `shared` module.
- **Validation**: Smoke tested on device (KB2001 Android 14) and passed targeted unit tests.
- See `docs/COMMUNICATION_MODEL_REPORT.md` for full details.

## Content Creation Model Update (Phase 1.0)

- The Content Creation Model has been implemented from scratch and achieved **FULL PASS** on real-device smoke tests.
- **7-Path Flow**: Supports PPT, Image, Video, Document, Excel, PDF, and "Other" formats with specialized drafting and requirement gathering.
- **State Machine**: Orchestrator manages the full lifecycle from raw idea expansion to final export and sharing.
- **Intelligent Detail Gathering**: Automatically asks for missing topics, slide counts, audience, or styles; maps direct user replies back to active requirements.
- **App Registry & Selection**: Intelligently selects best-fit apps (Canva, Gemini, Google Sheets, etc.) based on `<queries>` detection.
- **Safety & Privacy**: Sharing on WhatsApp/Gmail requires explicit user confirmation. Final sends are handled via manual handoff. Blocked keywords (OTP, Pay, etc.) are correctly intercepted.
- **Validation**: Smoke tested on OnePlus KB2001 (Android 14). Unit tests (13/13 green) verify parsing and state transitions.
- Architecture: Integrated cleanly into `CommandBrain` and `ActionExecutor` without rewriting core systems.

## Phase 1 Update (Hands: Real Phone Control)

- **Action Contract**: Enhanced `CommandIntent` to serve as a canonical action contract with ID, targets (app, label), risk levels, and retry policies.
- **Result Contract**: Implemented structured `ActionResultStatus` (SUCCESS, FAILED, BLOCKED, NEEDS_CONFIRMATION, NOT_FOUND, TIMEOUT, PERMISSION_REQUIRED) and added technical reasoning and screen snapshot summaries to `CommandResult`.
- **Hands Bridge**: Enhanced `NovaAccessibilityService` with better node finding (`clickByContentDescription`), improved typing reliability, and new capabilities like `waitForText` and `waitForApp`.
- **Centralized Execution**: Consolidated all phone control into `ActionExecutor.execute()`, ensuring all actions pass through `SafetyGate`.
- **Retry & Recovery**: Implemented a 2-retry mechanism for UI interactions with automatic scroll-fallback when elements are not found.
- **Service Decoupling**: Refactored the execution path to allow service-independent actions (launching apps, stop service) to execute even if Accessibility permission is not yet granted.
- Validation: Added `ActionExecutorPhase1Test` and fixed 412 unit tests across the project to maintain full system integrity after model signature changes.

## Phase 2 Update (Ears: Voice Input)

- **Voice Input Contract**: Defined canonical models (`VoiceInputState`, `VoiceInputResult`, `VoiceInputError`) for reliable communication between the voice layer and consumers.
- **Transcript Cleaning**: Implemented `VoiceCommandNormalizer` to strip wake words ("Luna", "Nova", etc.) and normalize speech results for the brain.
- **Voice Controller**: Built `VoiceInputController` as a lifecycle-aware wrapper for `SpeechRecognizer` with a mockable interface for testing.
- **Tap-to-Speak Flow**: Integrated a one-shot, user-triggered voice input flow into `MainActivity` with real-time transcript feedback.
- **Brain Integration**: Established `AssistantSession` as a unified entry point for commands from both voice and text sources.
- Privacy Gating: Ensured no always-on background listening; all audio is processed locally and never persisted.
- Validation: Added unit tests for normalization and controller logic; verified that all Phase 1 Hands tests still pass.

## Phase 3 Update (Mouth: Voice Response)

- **Voice Response Contract**: Defined canonical models (`VoiceResponseState`, `VoiceResponseType`, `VoiceResponseRequest`, `VoiceResponseResult`, `VoiceResponseError`) for observable and structured voice output.
- **Response Manager**: Implemented `VoiceResponseManager` to coordinate speech priority, duplicate suppression, and interruption rules.
- **Sanitizer**: Built `VoiceResponseSanitizer` to enforce privacy by masking sensitive information (OTPs, card numbers, emails) in spoken responses.
- **Template System**: Created `VoiceResponseTemplates` to map assistant states and action results to concise, user-friendly spoken feedback.
- **TTS Adapter**: Enhanced `TextToSpeechManager` with a `stop()` method and made it `open` for better testability and control.
- **Assistant Session Integration**: Integrated the response manager into `AssistantSession`, enabling automatic spoken feedback for command lifecycle events (listening, thinking, success, etc.).
- Service Consistency: Refactored `VoiceCommandService` to use `VoiceResponseManager`, ensuring a unified voice persona across the app and background service.
- Validation: Added comprehensive unit tests for templates, sanitization, and the response manager; verified that all Phase 1 and Phase 2 tests still pass.

## Phase 4 Update (Face: Futuristic Popup UI)

- **Popup Contract**: Defined canonical models (`AssistantPopupState`, `AssistantPopupUiModel`, `AssistantPopupEvent`) for a structured and reactive UI.
- **Visual Mapping**: Implemented `AssistantPopupStateMapper` to translate complex assistant lifecycle states into user-friendly UI configurations (Listening, Thinking, Action, etc.).
- **Interactive Controller**: Built `AssistantPopupController` to manage the UI lifecycle, including smooth transitions between the idle "Orb" and active "Panel" modes.
- **Multi-Listener Architecture**: Refactored `AssistantSession` to support multiple concurrent listeners, enabling synchronization between the main activity and the popup UI.
- **Safety Confirmation UI**: Implemented a mandatory, high-visibility confirmation box for risky actions, ensuring that user authorization is never bypassed or obscured.
- Event Bridging: Established a clean event flow from the popup (mic taps, cancellations) back to the session and voice controllers.
- Validation: Added unit tests for state mapping and verified that all previous phase tests (Hands, Ears, Mouth) continue to pass.

## Phase 5 Update (Unified Model Integration)

- **Unified Domain Routing**: Implemented `UnifiedDomainRouter` to automatically detect user intent and route to the correct model (Food, Cab, Media, etc.) without manual selection.
- **Domain Handlers**: Created a modular registry of `DomainHandlers` for 9 key areas: System, Food, Cab, Grocery, Shopping, Communication, Content, Media, and Music.
- **Contextual Continuity**: Introduced `AssistantContext` to maintain session awareness, allowing for natural follow-up commands like "proceed" or "cancel" within an active domain flow.
- **Integrated Brain**: Refactored `CommandBrain` to use the unified router as its primary reasoning entry point while maintaining robust fallbacks to specialized model roles and agent loops.
- **Automated Disambiguation**: Implemented confidence-based routing rules that automatically trigger clarification questions for ambiguous commands.
- Visual & Audio Feedback: Integrated the routed domain information into the `AssistantPopupController` and `VoiceResponseManager` for transparent user feedback.
- Validation: Verified that all 445 project tests (including Phase 1-4) pass with the new unified architecture.

## Phase 6 Update (Real Phone Testing & Stabilization)

- **Official Demo Flows**: Successfully stabilized 10 core demo flows: Open App, Play Music, YouTube Search, Scroll/Select Media, Read/Summarize Message, Content Prompt, Food Order, Grocery Compare, Cab Booking, and Shopping Compare.
- **Safety Hardening**: Expanded `SafetyGate` patterns to reliably catch final risky actions like "order now" and "send it," ensuring mandatory user confirmation in all cases.
- **Unified Routing Re-integration**: Refactored `CommandBrain` to correctly orchestrate the `UnifiedDomainRouter` while maintaining the existing parser and agent loop as robust fallbacks.
- **Match Confidence Tuning**: Refined domain match confidence and implemented session-aware prioritizing in the router to resolve domain collisions (e.g., between Music and Communication).
- **Test Framework**: Created a dedicated `Phase6StabilizationTest` suite and a `DemoFlowSmokeReceiver` to automate and record real phone execution results.
- **Validation**: Achieved a 100% pass rate (10/10) for the official demo flows in a simulated environment, with safety confirmations verified for all risky domains.







