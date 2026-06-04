# Architecture

## Overview

Nova / Luna is a phone-first assistant system designed to run locally on device as much as possible. Nova is the male assistant. Luna is the female assistant.

The default architecture should stay offline-first, with zero backend cost unless a future backend is explicitly requested.

## Main Pieces

- Flutter app for the primary front end
- Local voice input layer for speech capture and command intake
- Local voice output layer for spoken replies using Android TextToSpeech
- Nova and Luna are local TTS profiles that tune pitch and speech rate only; exact voice availability still depends on the installed Android TTS engine
- Local task engine for routing commands and assistant actions
- Local cab-booking orchestration for provider launch, fare comparison, and explicit user-confirmed handoff
- Local food-ordering orchestration for provider launch, quote comparison, coupon probing, and explicit user-confirmed handoff
- First-class control command path for stop/cancel style commands that safely shut down listening
- Local memory and preferences for persona, settings, and lightweight state
- Optional smartwatch companion later for quick commands and watch-first conveniences

## Architecture Rules

- Prefer on-device logic before any remote service.
- Do not add a backend by default.
- Do not introduce paid APIs by default.
- Keep voice output on local Android TextToSpeech rather than cloud TTS services.
- Keep app structure clean and testable.
- Keep Nova/Luna separate from Jarvis language or workflows.
- Treat stop and cancel as safe control actions that can end listening cleanly.
- Do not finalize a cab booking without explicit user confirmation.
- Do not finalize a food order without explicit user confirmation.
- Do not bypass OTP, login, payment, or CAPTCHA screens.
- Keep assistant personality, voice, and automation logic aligned with the product mission.

## Data And State

- Use local storage for preferences and lightweight memory.
- Keep cab-booking session state transient and local to the device.
- Keep sensitive data handling explicit and minimal.
- Avoid hidden synchronization paths.

## Roadmap Shape

1. Stabilize the phone-first assistant core.
2. Strengthen voice command, voice reply, and task automation flows.
3. Add tests and documentation for every meaningful behavior change.
4. Add smartwatch support only after the phone-first flow is stable.
5. Keep the system zero-cost and local-first unless requirements change.
