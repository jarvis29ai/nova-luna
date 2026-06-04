# Work Process Report

## Progress Snapshot

- Current app readiness: 50%
- `docs/NOVA_LUNA_PROGRESS_CHECKPOINT.md` records the 40% verified command-brain checkpoint.
- This process-report sync raises tracked project readiness to 50% because the phone-only multi-model brain routing layer now includes the Gemma phone runtime scaffold and readiness reporting.

## Current Setup

- Nova / Luna is a phone-first assistant project.
- Nova is the male assistant and Luna is the female assistant.
- The codebase should stay local-first and offline-first by default.
- Flutter is the primary front end.
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
- Debug command smoke can be triggered in the debug build with `com.nova.luna.debug.ACTION_RUN_COMMAND_SMOKE` and records exact command handling for basic, cab, food, grocery, and negative safety phrases. The debug receiver also writes a cached report in app storage so the phone-side results can be reviewed deterministically.
- The brain runtime now tracks phone-only capability modes, role-based routing, offline behavior, Gemma phone runtime readiness, and online-assisted lookup-only preparation without adding a backend.
- `flutter_app/` remains untouched and must not be added yet.
- Usage-access settings remains explicit and safety-aware.
- Sectioned command smoke reruns are used when a single full pass would otherwise stall on a later section, so each family can be verified independently without changing production behavior.

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

1. Confirm the current app structure and entry points.
2. Validate the local voice command and voice reply flow.
3. Tighten the task engine and automation boundaries.
4. Add or improve tests around the most important assistant behaviors.
5. Expand documentation as new capabilities land.

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
