# AGENTS.md

## Project Mission

Nova / Luna is a phone-first, on-device assistant project with Nova as the male assistant and Luna as the female assistant. The project should stay local-first, offline-first, and zero-backend-cost by default.

This repository is for Nova / Luna only. Do not confuse it with Jarvis trading or any unrelated assistant/project direction.

## Contractor Workflow

- The owner gives high-level tasks to Codex.
- Codex inspects the project, plans the work, and decides whether helpers are needed.
- Gemini is a reviewer and research helper only.
- OpenCode is a local edit and file worker only.
- Cursor is optional visual IDE and rules support.
- Codex makes the final implementation decisions.
- Codex reports changed files, validation, and the next step.

## Core Architecture Rules

- Prefer local, on-device behavior for voice input, voice reply, memory, preferences, and task automation.
- Keep the app phone-first, with smartwatch support treated as an eventual companion layer.
- Keep Flutter frontend/backend-free unless a future backend is explicitly requested.
- Prefer free and open-source tools and libraries by default.
- Avoid hardcoded secrets, API keys, or paid services unless the user explicitly requests them.
- Do not invent placeholder-only architecture unless it is clearly marked as `TODO` and tracked.
- Keep folder structure clean and purposeful.
- Maintain reports and design notes in `docs/`.

## Coding Rules

- Make the smallest safe change that solves the task.
- Preserve existing Nova/Luna naming, voice roles, and product direction.
- Do not introduce backend assumptions, cloud dependencies, or paid APIs without explicit approval.
- Keep changes readable, testable, and consistent with the existing codebase.
- Prefer practical implementations over speculative abstractions.

## Testing Rules

- Add or update tests when behavior changes.
- Verify critical flows after edits, especially voice commands, voice replies, automation, and startup behavior.
- When adding architecture or workflow changes, update documentation in `docs/` at the same time.
- Prefer local verification and deterministic checks.

## Documentation Rules

- Update `docs/ARCHITECTURE.md` when the system architecture changes.
- Update `docs/WORK_PROCESS_REPORT.md` when the workflow, build process, or agent coordination changes.
- Keep docs honest about what exists now versus what is planned.
- Do not let docs drift away from the actual code.

## Safety Rules

- Preserve user control over automation.
- Do not bypass platform protections or security boundaries.
- Do not add hidden network behavior.
- Do not add paid service dependencies by default.
- Keep Nova/Luna separated from unrelated project language or branding.

## Agent Guidance

- Codex should act as the main contractor and final decision maker.
- Gemini CLI should read this file first if available and make careful, scoped edits.
- OpenCode should focus on local edits, small bugs, repetitive refactors, and file creation.
- Cursor should be treated as optional visual IDE and rules support, not a replacement for Codex.
- If instructions conflict, follow the most specific applicable file and preserve the project mission above.
