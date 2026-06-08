# Phase 2: Ears / Voice Input Report

## Status: COMPLETE
**Audit Pass: Green (Phase 2 and Phase 1 tests pass)**

## 1. What Phase 2 Ears Now Supports

Luna / Nova now has "ears" to receive and understand voice commands.

- **Tap-to-Speak**: A reliable, user-triggered voice input flow in the main app.
- **Speech-to-Text**: Real-time conversion of speech to text using Android's `SpeechRecognizer`.
- **Transcript Cleaning**: Automatic removal of wake words ("Luna", "Nova", "Hey Nova", etc.) and normalization of punctuation/whitespace.
- **Hindi Support**: Basic support for Hindi wake words like "नमस्ते लूना".
- **Structured Contracts**: Clear models for voice states (`LISTENING`, `PROCESSING`, `TRANSCRIPT_READY`, etc.) and results.
- **Brain Integration**: Unified handoff path via `AssistantSession` to the existing `CommandBrain`.
- **Privacy Gating**: No always-on background listening; audio is processed locally and never stored.

## 2. Key Files Changed/Added

- **Models**: `VoiceInputModels.kt` (Contracts).
- **Core Logic**: `VoiceInputController.kt` (Recognizer wrapper), `VoiceCommandNormalizer.kt` (Cleaning).
- **Orchestration**: `AssistantSession.kt` (Command handoff).
- **UI**: `MainActivity.kt` (Tap-to-speak integration), `activity_main.xml`.
- **Service**: `VoiceCommandService.kt` (Updated to use the new normalizer).
- **Tests**: `VoiceCommandNormalizerTest.kt`, `VoiceInputControllerTest.kt`.

## 3. Voice Input States added

- `IDLE`: No active voice input.
- `PERMISSION_REQUIRED`: Microphone permission not granted.
- `READY`: Recognizer is initialized and ready.
- `LISTENING`: User is currently speaking.
- `PROCESSING`: Speech ended, converting to text.
- `TRANSCRIPT_READY`: Final cleaned command is available.
- `NO_SPEECH`: Recognizer timed out or heard nothing.
- `ERROR`: A recognizer or system error occurred.
- `CANCELLED`: User cancelled the listening session.

## 4. How Transcript is Cleaned

Cleaning is handled by `VoiceCommandNormalizer`:
1.  **Trim**: Remove leading/trailing whitespace.
2.  **Lowercase**: Convert to lowercase for consistent matching.
3.  **Wake-Word Strip**: Check if the transcript starts with common wake words (Luna, Nova, Hey Luna, etc.) and remove them.
4.  **Punctuation Fix**: Remove leading commas or periods often added by the recognizer after the wake word.
5.  **Validation**: Ensure the resulting command is not empty and has a minimum length.

## 5. Safety & Privacy Rules Implemented

- **Local Processing**: Audio is processed using the on-device `SpeechRecognizer`.
- **No Persistence**: Raw audio data is never saved to disk or sent to a remote server.
- **User-Triggered**: All voice capture in Phase 2 is initiated by an explicit user action (mic tap).
- **Redaction**: Transcripts are handled locally and can be redacted before storage in the command history.
- **Non-Execution**: Voice input only produces a text command; actual device control remains gated by Phase 1's `SafetyGate` and `ActionExecutor`.

## 6. Real Phone Smoke Test Plan

- **Grant Mic Permission**: Open app, tap "Tap to Speak", grant permission when prompted.
- **Test Command**: Tap mic, say "Luna open settings".
- **Verification**:
    - "Listening..." state shown on button.
    - Live transcript appears in the text view.
    - "Final: luna open settings" appears.
    - Brain receives "open settings".
    - Toast shows "Result: Opening Settings." (or similar).
- **Test Wake Word Removal**: Tap mic, say "Hey Nova play music".
- **Verification**: Brain receives "play music".
- **Test No Speech**: Tap mic, stay silent.
- **Verification**: Text view shows "I didn't hear that. Please try again."

## 7. Build/Test Results

- **Unit Tests**: `.\gradlew.bat :app:testDebugUnitTest --tests "com.nova.luna.voice.*"` -> **PASS**.
- **Regressions**: All 412 existing tests (Phase 1 and core) -> **PASS**.
- **Build**: `.\gradlew.bat :app:assembleDebug` -> **SUCCESS**.

## 8. Confirmation

- [x] Voice input does not directly control the phone.
- [x] Voice layer does not call `AccessibilityService` directly.
- [x] Voice layer does not bypass `BrainService` / `BrainRouter`.
- [x] Empty/no-speech result does not call the brain.
- [x] Always-on background listening was **not** added.
- [x] Microphone permission is handled safely.
- [x] Phase 1 Hands tests still pass.
