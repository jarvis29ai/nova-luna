# Security and Limitations

## What the app can do

- Listen for speech locally on Android
- Parse simple commands offline
- Launch installed apps
- Trigger safe navigation actions
- Tap text or content descriptions when accessibility nodes are available
- Scroll and type into editable fields when the user has enabled accessibility
- Speak replies with a local TTS engine
- Record a local command history and audit trail

## What the app cannot do

- It cannot bypass Android security controls.
- It cannot bypass banking, payment, password, OTP, or CAPTCHA protections.
- It cannot complete shopping checkout automatically.
- It cannot extract passwords.
- It cannot safely read `FLAG_SECURE` protected content.
- It cannot replace the user for sensitive financial actions.

## FLAG_SECURE Limitation

Android intentionally prevents normal UI automation from reading or interacting with `FLAG_SECURE` windows.
This starter does not attempt to bypass that protection.
If an app marks its screen as secure, the assistant must respect that boundary.

## Banking and Payment Limitation

Any command related to:

- pay
- send money
- order
- buy
- checkout
- bank
- upi
- password
- otp
- captcha

is blocked in the parser and safety gate.

## AccessibilityService Policy Warning

The AccessibilityService is only for user-controlled accessibility automation.
It must not be used to:

- exfiltrate passwords
- auto-complete payments
- bypass confirmations
- bypass CAPTCHA
- silently control sensitive UI flows

If you plan to publish this app, review Google Play Accessibility and device policy requirements carefully.

## Privacy Policy Notes

- No cloud backend exists in this starter.
- No OpenAI, Gemini, or other remote API path exists in the code.
- Command history is stored locally only.
- Voice and automation data stay on device unless the user explicitly exports it later.

## No Backend / No Cloud Data Path

- No server calls
- No remote inference calls
- No paid API integration
- No cloud transcription API integration

The current design is intentionally local-first.

