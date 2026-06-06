# Communication Model Report

## A. Communication Model Status
- **Phase 2.0 Finalized**: Gmail Authenticated Local Integration completed and verified.
- **Phase 1.2 Validated**: Yes
- **Build validated**: Yes
- **Device installed**: Yes (7675208c / KB2001 Android 14)
- **Package confirmed**: `com.nova.luna.debug`
- **Smoke tested**: Yes (Real device Gmail auth/reading/drafting handoff verified)
- **Auto-send path**: None (Explicit confirmation and handoff required)
- **Sensitive redaction**: Active (OTP/Passwords hidden across all platforms)
- **Reading**: Permission-gated real local readers active for SMS, Notifications, and Gmail.

## B. Gmail Integration (Phase 2.0)
- **Auth Method**: Android `AccountManager` + Google OAuth2 Token.
- **Scope**: Exactly `https://www.googleapis.com/auth/gmail.readonly`.
- **User Consent**: Token requests are system-driven and require explicit user approval.
- **Security**: 
  - No client secrets or tokens stored in plain text or logged.
  - No backend/cloud storage used for credentials or message data.
  - Token invalidation handled on HTTP 401.
- **Reader**: Fetches latest 25 messages via local Gmail REST API calls.
- **Search**: Fully supported using Gmail API's native query engine (`q` parameter).
- **Drafting**: Intent-based handoff to Gmail app via `ACTION_SENDTO` (user-visible only).
- **Redaction**: sensitive Gmail content is explicitly redacted before summarization.

## C. Supported Flows
- **Summarize all messages**: Today's SMS and captured WhatsApp/Telegram notifications.
- **Summarize platform messages**: Platform-specific grouping.
- **Search messages**: Keyword-based search across local SMS and notification snapshots.
- **Draft/reply**: Internal draft creation with tone support.
- **Modify draft**: Conversational draft editing.
- **Send-it continuation**: Safe handoff to app composer via Intents (only works with active draft session).

## C. Safety Boundaries
- **SMS**: Real local reader via `ContentResolver`. Requires `READ_SMS`. Limits read to recent messages.
- **WhatsApp/Telegram**: Notification snapshot reading via `NovaAccessibilityService`. Safe Android surfaces only. No private database reading. Historic reading clearance clears snapshots (volatile memory-only storage).
- **Gmail**: Remains scaffolded until authenticated local integration exists.
- **Sensitive Data**: OTP/password/payment/banking data explicitly redacted (`[Sensitive content hidden]`).
- **Composer Handoff**: Uses `ACTION_SEND` and `ACTION_SENDTO` intents. Final send is user-initiated.
- **No-Crash Guarantee**: Missing Accessibility, missing target apps, or missing permissions return clean `BLOCKED` or `FAILED` states without throwing uncaught exceptions.

## D. Validation Record
- **Device**: 7675208c / KB2001 Android 14
- **Install**: PASS
- **Targeted Unit tests**: PASS (`com.nova.luna.communication.*`)
- **Full Unit suite**: Blocked by unrelated legacy failures (brain/navigation/grocery).
- **Assemble**: PASS
- **git diff --check**: PASS

## F. Communication Model Release Lock
- **Frozen Phase**: 2.0 (Gmail Authenticated Local Integration)
- **Allowed Future Changes**: 
  - Advanced Gmail filtering (labels, importance).
  - Advanced notification parsing for sender names.
  - Bug fixes for Intent handoffs.
- **Forbidden Changes**:
  - Auto-sending without user confirmation.
  - Reading private databases (WhatsApp/Telegram).
  - Removing sensitive content redaction.
  - Bypassing Android permission checks.
- **Required Validation Commands**:
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.nova.luna.communication.*" --no-daemon
  .\gradlew.bat :app:assembleDebug --no-daemon
  git diff --check
  ```

## G. Architecture Note
- **Communication model location**: `app/src/main/java/com/nova/luna/communication/`
- **Build System**: Added Foojay resolver to `settings.gradle` for JDK 17 toolchain acquisition. Necessary for compiling the `shared` module without manual JDK management.
- **Brain wiring is minimal**:
  - `ActionType.kt`
  - `IntentType.kt`
  - `RuleBasedCommandParser.kt`
  - `CommandRouter.kt`
  - `CommandBrain.kt`
  - `ActionExecutor.kt`
- **Brain was not rewritten.** Isolation of communication domain is strictly maintained.
