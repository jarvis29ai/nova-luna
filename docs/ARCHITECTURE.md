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
- Phone-only multi-model brain routing with `BrainRouter` selecting `GemmaBrainModel`, `ActionJsonModel`, `LiteCommandModel`, `ScreenUnderstandingModel`, or the guaranteed `LocalMockBrainProvider` fallback
- `PhoneGemmaRuntime` is the production phone reasoning scaffold for Gemma, and `GemmaBrainModel` delegates to it when runtime readiness passes
- `ActionJsonModel` creates strict safe BrainAction JSON for cab, food, and task planning and stays validator-bound even if Gemma reasoning is supplied later
- `LiteCommandModel` handles fast offline commands like stop, cancel, go home, and open app
- `ScreenUnderstandingModel` is reserved for future read-only screen analysis
- No backend, cloud server, or paid API is required for production
- Local task engine for routing commands and assistant actions
- Local cab-booking orchestration for pickup/drop collection, current-location resolution, provider launch, provider-specific destination handling, fare comparison, and explicit user-confirmed handoff
- Local food-ordering orchestration for item parsing, provider launch, quote comparison, coupon probing, and explicit user-confirmed handoff
- Local grocery-booking orchestration for item parsing, basket changes, provider launch, provider comparison, coupon handling, cart total comparison, and explicit user-confirmed handoff
- First-class control command path for stop/cancel style commands that safely shut down listening
- Local memory and preferences for persona, settings, and lightweight state
- Optional smartwatch companion later for quick commands and watch-first conveniences

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
- Keep Ollama-compatible desktop LLMs dev-only and outside the production brain path.
- Keep the phone-only model roles local-first and keep `LocalMockBrainProvider` as the guaranteed fallback when a phone model is unavailable or rejected.
- Keep `SafetyGate` as the final authority before any action reaches the executor.
- Keep phone-only runtime modes explicit so offline-first behavior remains the default and online behavior stays lookup-only.
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
- Keep BrainAction structured data transient and local to the device unless a future memory feature explicitly stores it.
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
