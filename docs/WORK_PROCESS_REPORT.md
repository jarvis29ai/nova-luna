# Work Process Report

## Progress Snapshot

- Current app readiness: green for the audited scope on 2026-06-06
- `:app:testDebugUnitTest` and `:app:assembleDebug` both passed in this audit pass
- `docs/NOVA_LUNA_FULL_PROJECT_MODEL_FLOW_AUDIT_REPORT.md` records the current full-model verification

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
- The brain runtime now tracks phone-only capability modes, role-based routing, offline behavior, Gemma phone runtime readiness, and online-assisted lookup-only preparation without adding a backend.
- `flutter_app/` remains untouched and must not be added yet.
- Usage-access settings remains explicit and safety-aware.
- Sectioned command smoke reruns are used when a single full pass would otherwise stall on a later section, so each family can be verified independently without changing production behavior. In the latest run, the cab flow reported missing location permission cleanly, the food flow still stopped because supported search/cart controls were not available, and the grocery flow now dismisses the final-confirmation state cleanly on cancel.

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
- **Architecture**: Integrated cleanly into `CommandBrain` and `ActionExecutor` without rewriting core systems.

