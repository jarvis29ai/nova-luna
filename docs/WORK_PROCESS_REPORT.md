# Work Process Report

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
