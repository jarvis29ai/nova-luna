# Nova / Luna Starter

Nova / Luna is an Android-first, fully on-device voice automation assistant starter project.

Nova is the male voice profile.
Luna is the female voice profile.

Core loop:
Voice Input -> Parse Command -> Safety Gate -> Execute Android UI/App Action -> Speak Reply

This starter is offline-first and does not use any backend, cloud API, or paid API.

## What is included

- Kotlin Android app module for the phone
- Foreground voice service with SpeechRecognizer and TextToSpeech
- AccessibilityService scaffold for user-controlled UI automation
- Room database scaffold for command history and audit logs
- DataStore preferences for voice profile and basic settings
- BiometricPrompt scaffold for sensitive commands
- Wear OS companion scaffold
- Shared constants module for command channel names
- Architecture, work process, safety, and roadmap docs

## Safety rules built into the starter

- Banking, payment, checkout, OTP, CAPTCHA, and password extraction flows are blocked.
- The assistant does not bypass Android security.
- The assistant does not bypass `FLAG_SECURE`.
- The AccessibilityService is only for legitimate, user-controlled automation.
- A visible foreground notification stays active while the mic/service is running.
- Stop and cancel commands are always handled first.

## Open in Android Studio

1. Open the `nova-luna/` folder in Android Studio.
2. Let Gradle sync.
3. If Android Studio asks for missing SDK components, install the requested Android SDK and build tools.

## Build a debug APK

From Android Studio:

1. Use `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
2. Or use `assembleDebug` after adding a working Gradle wrapper jar on a machine with Gradle installed.

Important:

- This ZIP includes the wrapper scripts and wrapper properties.
- The binary `gradle-wrapper.jar` is not generated in this environment.
- If you want command-line builds, generate the wrapper jar on a machine that has Gradle installed, then rerun `./gradlew assembleDebug`.

## Enable AccessibilityService

1. Install the app on a phone.
2. Open the app.
3. Tap `Open Accessibility Settings`.
4. Enable `NovaAccessibilityService`.
5. Return to the app and confirm the permission status section shows Accessibility as granted.

## Enable Usage Access

1. Tap `Open Usage Access Settings`.
2. Allow Nova Luna usage access.

Usage access is scaffolded for future automation features and status checks.

## Test commands

Try these commands after the service is running:

- `open WhatsApp`
- `open YouTube`
- `go home`
- `go back`
- `show recent apps`
- `open notifications`
- `scroll down`
- `scroll up`
- `tap settings`
- `click search`
- `type hello world`
- `read notifications`
- `stop listening`
- `cancel`

Blocked examples:

- `pay the bill`
- `send money`
- `buy shoes`
- `order pizza`
- `checkout now`
- `bank transfer`
- `UPI payment`

## Current status

- The project is a starter scaffold, not a finished consumer app.
- Phone voice input, parsing, safety gating, TTS, and accessibility scaffolding are in place.
- Wear OS relay is a basic scaffold.
- Screenshot, call automation, and advanced routines are intentionally not implemented.

## Next steps

1. Add a real Gradle wrapper jar or sync/build from Android Studio.
2. Install on a physical Android phone.
3. Enable microphone permission, accessibility, and usage access.
4. Validate the command loop with a small safe command set.
5. Expand custom rules and notification parsing after the base flow is stable.

